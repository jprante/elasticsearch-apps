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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.collect.Sets;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.http.client.HttpDownloadHelper;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.io.Streams;
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
import org.jboss.shrinkwrap.resolver.api.maven.strategy.AcceptAllStrategy;
import org.jboss.shrinkwrap.resolver.api.maven.strategy.AcceptScopesStrategy;

public class AppService extends AbstractComponent {

    public final static String DEFAULT_SETTINGS = "config/apps.xml";
    public final static String[] DEFAULT_EXCLUDE = new String[] { "org.elasticsearch:elasticsearch"};
    private final Environment environment;
    private Map<App, List<OnModuleReference>> onModuleReferences;
    private Map<String, App> artifactApps = newHashMap();
    private Map<String, App> siteApps = newHashMap();
    private Map<String, App> apps = newHashMap();

    /**
     * Installing a SSL trust manager to accept HTTPS connections.
     */
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
     * Constructs a new AppService. Refresh all apps, check for mandatory
     * settings, print loaded artifact and site apps to log.
     *
     * @param settings The settings
     * @param environment The environment
     */
    public AppService(Settings settings, Environment environment) {
        super(settings);
        this.environment = environment;
        refreshApps(settings);
        checkMandatory();
        logger.info("loaded apps {}", artifactApps.keySet());
        logger.info("loaded sites {}", siteApps.keySet());
    }

    public Collection<App> artifactApps() {
        return artifactApps.values();
    }

    public Collection<App> siteApps() {
        return siteApps.values();
    }
    
    public Collection<App> apps() {
        return apps.values();
    }

    public synchronized void refreshApps(Settings settings) {
        this.artifactApps = refreshArtifactApps(settings);
        this.siteApps = refreshSiteApps(settings);
        this.apps = newHashMap(artifactApps);
        apps.putAll(siteApps);
        checkMandatory();
        this.onModuleReferences = onModuleRefs(apps.values());
    }

    /**
     * Resolve artifact
     *
     * @param settings
     * @param dependency
     * @param scope
     * @param excludes
     * @return
     */
    public MavenResolvedArtifact[] resolveArtifact(Settings settings, String dependency, String scope, String[] excludes) {
        String mavenSettingsFile = settings.get("apps.settings", DEFAULT_SETTINGS);
        boolean useMavenCentral = settings.getAsBoolean("apps.usemavencentral", Boolean.TRUE);
        String[] defaultExcludes = settings.getAsArray("apps.excludes", DEFAULT_EXCLUDE);
        MavenDependencyExclusion[] exclusions = new MavenDependencyExclusion[defaultExcludes.length + excludes.length];
        for (int i = 0; i < defaultExcludes.length; i++) {
            exclusions[i] = MavenDependencies.createExclusion(defaultExcludes[i]);
        }
        for (int i = 0; i < excludes.length; i++) {
            exclusions[defaultExcludes.length + i] = MavenDependencies.createExclusion(excludes[i]);
        }
        ScopeType scopeType = scope != null ? ScopeType.fromScopeType(scope) : ScopeType.RUNTIME;
        MavenDependency dep = MavenDependencies.createDependency(dependency, scopeType, false, exclusions);
        return Maven
                .configureResolver()
                .fromFile(mavenSettingsFile)
                .addDependencies(dep)
                .resolve()
                .withMavenCentralRepo(useMavenCentral)
                .using(new AcceptScopesStrategy(scopeType))
                .asResolvedArtifact();
    }

    /**
     * Search artifacts by pattern
     *
     * @param settings
     * @param name
     * @param dependency
     * @param excludes
     * @return a set of canonical forms of the found Maven artifacts
     */
    public Set<String> searchArtifacts(Settings settings, String regex) {
        String mavenSettingsFile = settings.get("apps.settings", DEFAULT_SETTINGS);
        Pattern pattern = Pattern.compile(regex);
        Set<String> matchedArtifacts = Sets.newHashSet();
        MavenResolvedArtifact[] artifacts = Maven
                .configureResolver()
                .fromFile(mavenSettingsFile)
                .resolve()
                .using(AcceptAllStrategy.INSTANCE)
                .asResolvedArtifact();
        for (MavenResolvedArtifact artifact : artifacts) {
            String name = artifact.getCoordinate().toCanonicalForm();
            if (pattern.matcher(name).matches()) {
                matchedArtifacts.add(artifact.getCoordinate().toCanonicalForm());
            }
        }
        return matchedArtifacts;
    }

    /**
     * Build  artifact app - do not install.
     *
     * @param settings
     * @param dependency
     * @param excludes
     * @return
     */
    public App buildArtifactApp(Settings settings, String dependency, String[] excludes) {
        MavenResolvedArtifact[] artifacts = resolveArtifact(settings, dependency, "runtime", excludes);
        Iterator<App> it = loadApps(settings, artifacts).values().iterator();
        return it.hasNext() ? it.next() : null;
    }

    /**
     * Remove artifact app.
     *
     * @param settings
     * @param name
     * @param dependency
     */
    public void removeArtifactApp(Settings settings, String name, String dependency) {
        String mavenSettingsFile = settings.get("apps.settings", DEFAULT_SETTINGS);
        MavenDependency dep = MavenDependencies.createDependency(dependency,
                ScopeType.RUNTIME, false);
        File[] artifactFile = Maven.configureResolver().fromFile(mavenSettingsFile)
                .offline()
                .addDependency(dep)
                .resolve()
                .withoutTransitivity()
                .asFile();
        if (artifactFile != null && artifactFile.length == 1) {
            logger.warn("deleting ", artifactFile[0]);
            deleteDirectory(artifactFile[0].getParentFile());
        }
    }

    /**
     * Delete directory
     *
     * @param path
     * @return
     */
    private boolean deleteDirectory(File path) {
        if (path == null) {
            return false;
        }
        if (path.exists()) {
            File[] files = path.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return path.delete();
    }

    /**
     * Install app. Returns old app if there was an old app.
     *
     * @param settings the settings
     * @param app the new app
     * @return the old app if any existed
     */
    public App installApp(Settings settings, App app) {
        if (app instanceof ArtifactApp) {
            App oldApp = artifactApps.put(app.getCanonicalForm(), app);
            apps.put(app.getCanonicalForm(), app);
            this.onModuleReferences = onModuleRefs(apps.values());
            return oldApp;
        }
        if (app instanceof SiteApp) {
            App oldApp = siteApps.put(app.getCanonicalForm(), app);
            apps.put(app.getCanonicalForm(), app);
            this.onModuleReferences = onModuleRefs(apps.values());
            return oldApp;
        }
        return null;
    }

    private Map<String, App> refreshArtifactApps(Settings settings) {
        String mavenSettingsFile = settings.get("apps.settings", DEFAULT_SETTINGS);
        boolean useMavenCentral = settings.getAsBoolean("apps.usemavencentral", Boolean.TRUE);
        Map<String, Settings> appSettings = settings.getGroups("apps.dependencies");
        Set<MavenDependency> defaultDeps = Sets.newHashSet();
        for (Map.Entry<String, Settings> entry : appSettings.entrySet()) {
            String name = entry.getKey(); // not used
            String dependency = entry.getValue().get("dependency");
            String[] excludes = entry.getValue().getAsArray("exclude");
            String[] defaultExcludes = settings.getAsArray("apps.excludes", DEFAULT_EXCLUDE);
            MavenDependencyExclusion[] exclusions = new MavenDependencyExclusion[defaultExcludes.length + excludes.length];
            for (int i = 0; i < defaultExcludes.length; i++) {
                exclusions[i] = MavenDependencies.createExclusion(defaultExcludes[i]);
            }
            for (int i = 0; i < excludes.length; i++) {
                exclusions[defaultExcludes.length + i] = MavenDependencies.createExclusion(excludes[i]);
            }
            logger.info("adding dependency {}", dependency);
            defaultDeps.add(MavenDependencies.createDependency(dependency,
                    ScopeType.RUNTIME, false, exclusions));
        }
        MavenResolvedArtifact[] artifacts = defaultDeps.isEmpty() ? null : Maven.configureResolver()
                .fromFile(mavenSettingsFile)
                .addDependencies(defaultDeps)
                .resolve()
                .withMavenCentralRepo(useMavenCentral)
                .withTransitivity()
                .asResolvedArtifact();
        return loadApps(settings, artifacts);
    }

    private Map<String, App> refreshSiteApps(Settings settings) {
        Map<String, App> loadedApps = newHashMap();
        HttpDownloadHelper downloadHelper = new HttpDownloadHelper();
        String siteGroupId = settings.get("apps.sitegroup", "org.elasticsearch.apps.site");
        Map<String, Settings> siteSettings = settings.getGroups("apps.sites");
        for (Map.Entry<String, Settings> entry : siteSettings.entrySet()) {
            try {
                String name = entry.getKey();
                URL url = new URL(entry.getValue().get("url"));
                String version = entry.getValue().get("version", "0");
                SiteApp app = new SiteApp(siteGroupId, name, version, url);
                File appFile = app.getInstallPath(environment);
                if (appFile.exists()) {
                    loadedApps.put(app.getCanonicalForm(), app);
                } else {
                    appFile.mkdirs();
                    logger.info("retrieving site plugin from URL {}", url);
                    // only zip supported
                    File zipFile = new File(environment.pluginsFile(), name + ".zip");
                    downloadHelper.download(url, zipFile, null);
                    // extract zip
                    unzip(environment, app, zipFile);
                    if (new File(appFile, "_site").exists()) {
                        loadedApps.put(app.getCanonicalForm(), app);
                    }
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return loadedApps;
    }

    private void checkMandatory() {
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
    }

    private ImmutableMap<App, List<OnModuleReference>> onModuleRefs(Collection<App> apps) {

        MapBuilder<App, List<OnModuleReference>> refs = MapBuilder.newMapBuilder();
        for (App app : apps) {
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
                refs.put(app, list);
            }
        }
        return refs.immutableMap();
    }

    public void processModules(Iterable<Module> modules) {
        for (Module module : modules) {
            processModule(module);
        }
    }

    public void processModule(Module module) {
        for (App app : apps.values()) {
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
                    logger.warn("failed to add [{}]", artifact.getCoordinate(), e);
                }
            } else {
                logger.warn("not a jar artifact: [{}]", artifact.getCoordinate());
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
                if (jars != null) {
                    // find URL of artifact
                    boolean found = false;
                    for (URL appUrl : jars.keySet()) {
                        if (propUrl.toExternalForm().startsWith("jar:" + appUrl.toExternalForm())) {
                            ArtifactApp app = new ArtifactApp(appUrl, jars.get(appUrl), plugin);
                            map.put(app.getCanonicalForm(), app);
                            found = true;
                        }
                    }
                    if (!found) {
                        logger.warn("can't find jar for plugin " + propUrl);
                    }
                } else {
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

    /**
     * Helper for unzipping downloaded site apps
     *
     * @param environment
     * @param app
     * @param pluginFile
     * @throws IOException
     */
    private void unzip(Environment environment, SiteApp app, File pluginFile) throws IOException {
        File appFile = app.getInstallPath(environment);
        ZipFile zipFile = null;
        String baseDirSuffix = null;
        try {
            zipFile = new ZipFile(pluginFile);
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            if (!zipEntries.hasMoreElements()) {
                logger.error("the zip archive has no entries");
            }

            ZipEntry firstEntry = zipEntries.nextElement();
            if (firstEntry.isDirectory()) {
                baseDirSuffix = firstEntry.getName();
            } else {
                zipEntries = zipFile.entries();
            }
            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();
                if (zipEntry.isDirectory()) {
                    continue;
                }
                String zipName = zipEntry.getName();
                if (zipName.startsWith(baseDirSuffix)) {
                    zipName = zipName.replace('\\', '/').substring(baseDirSuffix.length());
                }
                File target = new File(appFile, zipName);
                FileSystemUtils.mkdirs(target.getParentFile());
                Streams.copy(zipFile.getInputStream(zipEntry), new FileOutputStream(target));
            }
        } catch (IOException e) {
            logger.error("failed to extract plugin [" + pluginFile + "]: " + ExceptionsHelper.detailedMessage(e));
            return;
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            pluginFile.delete();
        }
        File binFile = new File(appFile, "bin");
        if (binFile.exists() && binFile.isDirectory()) {
            File toLocation = new File(new File(environment.homeFile(), "bin"), app.getPathName());
            logger.info("found bin, moving to " + toLocation.getAbsolutePath());
            FileSystemUtils.deleteRecursively(toLocation);
            binFile.renameTo(toLocation);
        }
        if (!new File(appFile, "_site").exists()) {
            if (!FileSystemUtils.hasExtensions(appFile, ".class", ".jar")) {
                logger.info("identified as a _site plugin, moving to _site structure ...");
                File site = new File(appFile, "_site");
                File tmpLocation = new File(environment.pluginsFile(), app.getPathName() + ".tmp");
                appFile.renameTo(tmpLocation);
                FileSystemUtils.mkdirs(appFile);
                tmpLocation.renameTo(site);
            }
        }

    }
}
