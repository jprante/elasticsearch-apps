/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.apps;

import static org.elasticsearch.common.collect.Maps.newHashMap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.collect.Sets;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.http.client.HttpDownloadHelper;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.CloseableIndexComponent;
import org.elasticsearch.plugins.Plugin;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.PackagingType;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependencies;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependency;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependencyExclusion;

public class AppResolverService extends AbstractComponent {

    private final ImmutableMap<String, App> apps;
    private final ImmutableMap<App, List<OnModuleReference>> onModuleReferences;

    static {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
        };

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class OnModuleReference {

        public final Class<? extends Module> moduleClass;
        public final Method onModuleMethod;

        OnModuleReference(Class<? extends Module> moduleClass, Method onModuleMethod) {
            this.moduleClass = moduleClass;
            this.onModuleMethod = onModuleMethod;
        }
    }

    /**
     * Constructs a new AppResolverService
     *
     * @param settings The settings of the system
     * @param environment The environment of the system
     */
    public AppResolverService(Settings settings, Environment environment) {
        super(settings);
        HttpDownloadHelper downloadHelper = new HttpDownloadHelper();

        String mavenSettingsFile = settings.get("apps.settings", "config/settings-es.xml");
        final MavenDependencyExclusion exclusion = MavenDependencies
                .createExclusion("org.elasticsearch:elasticsearch");
        MavenResolvedArtifact[] artifacts = null;
        String[] defaultAppClasses = settings.getAsArray("apps.dependencies");
        if (defaultAppClasses.length > 0) {
            MavenDependency[] defaultDeps = new MavenDependency[defaultAppClasses.length];
            for (int i = 0; i < defaultAppClasses.length; i++) {
                logger.info("adding dependency {}", defaultAppClasses[i]);
                defaultDeps[i] = MavenDependencies.createDependency(defaultAppClasses[i],
                        ScopeType.COMPILE, false, exclusion);
            }
            artifacts = Maven.configureResolver()
                    .fromFile(mavenSettingsFile)
                    .addDependencies(defaultDeps)
                    .resolve()
                    .withMavenCentralRepo(true)
                    .withTransitivity()
                    .asResolvedArtifact();
        }

        Map<String, App> loadedApps = loadApps(settings, artifacts);

        String[] defaultSites = settings.getAsArray("apps.sites");
        if (defaultSites.length > 0) {
            for (int i = 0; i < defaultSites.length; i++) {
                try {
                    URL url = new URL(defaultSites[i]);
                    SiteApp app = new SiteApp(url);
                    File appFile = app.getInstallPath(environment);
                    if (appFile.exists()) {
                        loadedApps.put(app.getCanonicalForm(), app);
                    } else {
                        logger.info("retrieving site from URL {}", defaultSites[i]);
                        downloadHelper.download(url, appFile, null);
                        if (new File(appFile, "_site").exists()) {
                            loadedApps.put(app.getCanonicalForm(), app);
                        }
                    }
                } catch (MalformedURLException e) {
                    logger.error(e.getMessage(), e);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }

        this.apps = ImmutableMap.copyOf(loadedApps);

        String[] mandatoryApps = settings.getAsArray("apps.mandatory", null);
        if (mandatoryApps != null) {
            Set<String> missingApps = Sets.newHashSet();
            for (String mandatoryApp : mandatoryApps) {
                if (!apps.containsKey(mandatoryApp) && !missingApps.contains(mandatoryApp)) {
                    missingApps.add(mandatoryApp);
                }
            }
            if (!missingApps.isEmpty()) {
                throw new ElasticSearchException("Missing mandatory apps [" + Strings.collectionToDelimitedString(missingApps, ", ") + "]");
            }
        }

        logger.info("apps {}", apps.keySet());

        MapBuilder<App, List<OnModuleReference>> onModuleReferences = MapBuilder.newMapBuilder();
        for (App app : apps.values()) {
            List<OnModuleReference> list = Lists.newArrayList();
            for (Method method : app.getClass().getDeclaredMethods()) {
                if (!method.getName().equals("onModule")) {
                    continue;
                }
                if (method.getParameterTypes().length == 0 || method.getParameterTypes().length > 1) {
                    logger.warn("App: {} implementing onModule with no parameters or more than one parameter", app.name());
                    continue;
                }
                Class moduleClass = method.getParameterTypes()[0];
                if (!Module.class.isAssignableFrom(moduleClass)) {
                    logger.warn("App: {} implementing onModule by the type is not of Module type {}", app.name(), moduleClass);
                    continue;
                }
                method.setAccessible(true);
                list.add(new OnModuleReference(moduleClass, method));
            }
            if (!list.isEmpty()) {
                onModuleReferences.put(app, list);
            }
        }
        this.onModuleReferences = onModuleReferences.immutableMap();
    }

    public ImmutableMap<String, App> apps() {
        return apps;
    }

    public void processModules(Iterable<Module> modules) {
        for (Module module : modules) {
            processModule(module);
        }
    }

    public void processModule(Module module) {
        for (App app : apps().values()) {
            app.processModule(module);
            // see if there are onModule references
            List<OnModuleReference> references = onModuleReferences.get(app);
            if (references != null) {
                for (OnModuleReference reference : references) {
                    if (reference.moduleClass.isAssignableFrom(module.getClass())) {
                        try {
                            reference.onModuleMethod.invoke(app, module);
                        } catch (Exception e) {
                            logger.warn("app {}, failed to invoke custom onModule method", e, app.name());
                        }
                    }
                }
            }
        }
    }

    public Settings updatedSettings() {
        ImmutableSettings.Builder builder = ImmutableSettings.settingsBuilder()
                .put(this.settings);
        for (App app : apps.values()) {
            builder.put(app.additionalSettings());
        }
        return builder.build();
    }

    public Collection<Class<? extends Module>> modules() {
        List<Class<? extends Module>> modules = Lists.newArrayList();
        for (App app : apps.values()) {
            modules.addAll(app.modules());
        }
        return modules;
    }

    public Collection<Module> modules(Settings settings) {
        List<Module> modules = Lists.newArrayList();
        for (App app : apps.values()) {
            modules.addAll(app.modules(settings));
        }
        return modules;
    }

    public Collection<Class<? extends LifecycleComponent>> services() {
        List<Class<? extends LifecycleComponent>> services = Lists.newArrayList();
        for (App app : apps.values()) {
            services.addAll(app.services());
        }
        return services;
    }

    public Collection<Class<? extends Module>> indexModules() {
        List<Class<? extends Module>> modules = Lists.newArrayList();
        for (App app : apps.values()) {
            modules.addAll(app.indexModules());
        }
        return modules;
    }

    public Collection<Module> indexModules(Settings settings) {
        List<Module> modules = Lists.newArrayList();
        for (App app : apps.values()) {
            modules.addAll(app.indexModules(settings));
        }
        return modules;
    }

    public Collection<Class<? extends CloseableIndexComponent>> indexServices() {
        List<Class<? extends CloseableIndexComponent>> services = Lists.newArrayList();
        for (App app : apps.values()) {
            services.addAll(app.indexServices());
        }
        return services;
    }

    public Collection<Class<? extends Module>> shardModules() {
        List<Class<? extends Module>> modules = Lists.newArrayList();
        for (App app : apps.values()) {
            modules.addAll(app.shardModules());
        }
        return modules;
    }

    public Collection<Module> shardModules(Settings settings) {
        List<Module> modules = Lists.newArrayList();
        for (App app : apps.values()) {
            modules.addAll(app.shardModules(settings));
        }
        return modules;
    }

    public Collection<Class<? extends CloseableIndexComponent>> shardServices() {
        List<Class<? extends CloseableIndexComponent>> services = Lists.newArrayList();
        for (App app : apps.values()) {
            services.addAll(app.shardServices());
        }
        return services;
    }

    private Map<String, App> loadApps(Settings settings, MavenResolvedArtifact[] artifacts) {
        if (artifacts == null) {
            return newHashMap();
        }
        ClassLoader classLoader = settings.getClassLoader();
        Class classLoaderClass = classLoader.getClass();
        Method addURL = null;
        while (!classLoaderClass.equals(Object.class)) {
            try {
                addURL = classLoaderClass.getDeclaredMethod("addURL", URL.class);
                addURL.setAccessible(true);
                break;
            } catch (NoSuchMethodException e) {
                // no method, try the parent
                classLoaderClass = classLoaderClass.getSuperclass();
            }
        }
        if (addURL == null) {
            logger.error("failed to find addURL method on classLoader [" + classLoader + "] to add methods");
            return newHashMap();
        }
        Map<URL, MavenResolvedArtifact> urlMap = newHashMap();
        for (MavenResolvedArtifact artifact : artifacts) {
            if (artifact.getCoordinate().getType().equals(PackagingType.JAR)) {
                try {
                    URL url = artifact.asFile().toURI().toURL();
                    addURL.invoke(classLoader, url);
                    urlMap.put(url, artifact);
                } catch (Exception e) {
                    logger.warn("failed to add [{}]", artifact, e);
                }
            }
        }
        return loadAppsFromClasspath(settings, urlMap);
    }

    private Map<String, App> loadAppsFromClasspath(Settings settings, Map<URL, MavenResolvedArtifact> jars) {
        Map<String, App> map = newHashMap();
        Enumeration<URL> propUrls = null;
        try {
            propUrls = settings.getClassLoader().getResources("es-plugin.properties");
        } catch (IOException e1) {
            logger.warn("failed to find properties on classpath", e1);
            return ImmutableMap.of();
        }
        while (propUrls.hasMoreElements()) {
            URL propUrl = propUrls.nextElement();
            Properties appProps = new Properties();
            InputStream is = null;
            try {
                is = propUrl.openStream();
                appProps.load(is);
                String appClassName = appProps.getProperty("plugin");
                Plugin plugin = loadPlugin(appClassName, settings);
                // find URL of artifact
                boolean found = false;
                for (URL appUrl : jars.keySet()) {
                    if (propUrl.toExternalForm().startsWith("jar:" + appUrl.toExternalForm())) {
                        ArtifactApp app = new ArtifactApp(appUrl, jars.get(appUrl), plugin);
                        map.put(app.getCanonicalForm(), app);
                    }
                }
            } catch (Exception e) {
                logger.warn("failed to load app from [" + propUrl + "]", e);
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
        return map;
    }

    private Plugin loadPlugin(String className, Settings settings) {
        try {
            Class<? extends Plugin> pluginClass = (Class<? extends Plugin>) settings.getClassLoader().loadClass(className);
            try {
                return pluginClass.getConstructor(Settings.class).newInstance(settings);
            } catch (NoSuchMethodException e) {
                try {
                    return pluginClass.getConstructor().newInstance();
                } catch (NoSuchMethodException e1) {
                    throw new ElasticSearchException("No constructor for [" + pluginClass + "]. A Plugin class must "
                            + "have either an empty default constructor or a single argument constructor accepting a "
                            + "Settings instance");
                }
            }
        } catch (Exception e) {
            throw new ElasticSearchException("Failed to load plugin class [" + className + "]", e);
        }

    }
}
