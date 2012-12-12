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
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.common.collect.Sets;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.CloseableIndexComponent;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependencies;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependency;

public class AppResolverService extends AbstractComponent {

    private final Environment environment;
    private final ImmutableMap<String, App> apps;
    private final ImmutableMap<App, List<OnModuleReference>> onModuleReferences;

    //private final ConfigurableMavenResolverSystemImpl resolver = new ConfigurableMavenResolverSystemImpl();
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
        this.environment = environment;

        Map<String, App> apps = Maps.newHashMap();

        String mavenSettingsFile = settings.get("apps.settings", "config/settings-es.xml");
        String[] defaultAppClasses = settings.getAsArray("apps.dependencies");
        if (defaultAppClasses.length > 0) {
            MavenDependency[] defaultDeps = new MavenDependency[defaultAppClasses.length];
            for (int i = 0; i < defaultAppClasses.length; i++) {
                logger.info("adding dependency {}", defaultAppClasses[i]);
                defaultDeps[i] = MavenDependencies.createDependency(defaultAppClasses[i], ScopeType.COMPILE, false);
            }
            
            MavenResolvedArtifact[] artifacts = Maven.configureResolver()
                    .fromFile(mavenSettingsFile)
                    .addDependencies(defaultDeps)
                    .resolve()
                    .withTransitivity()
                    .asResolvedArtifact();

            for (MavenResolvedArtifact artifact : artifacts) {
                logger.info("found artifact {} {} {}",
                        artifact.getCoordinate().getGroupId(),
                        artifact.getCoordinate().getArtifactId(),
                        artifact.getCoordinate().getVersion());
            }
        }

        //first we load all the default apps from the settings
        /*String[] defaultPluginsClasses = settings.getAsArray("app.types");
         for (String pluginClass : defaultPluginsClasses) {
         Plugin app = loadApp(pluginClass, settings);
         apps.put(app.name(), app);
         }

         // now, find all the ones that are in the classpath
         loadPluginsIntoClassLoader();
         apps.putAll(loadPluginsFromClasspath(settings));
         Set<String> siteApps = siteApps();

         String[] mandatoryPlugins = settings.getAsArray("app.mandatory", null);
         if (mandatoryPlugins != null) {
         Set<String> missingPlugins = Sets.newHashSet();
         for (String mandatoryPlugin : mandatoryPlugins) {
         if (!apps.containsKey(mandatoryPlugin) && !siteApps.contains(mandatoryPlugin) && !missingPlugins.contains(mandatoryPlugin)) {
         missingPlugins.add(mandatoryPlugin);
         }
         }
         if (!missingPlugins.isEmpty()) {
         throw new ElasticSearchException("Missing mandatory apps [" + Strings.collectionToDelimitedString(missingPlugins, ", ") + "]");
         }
         }
         */
        Set<String> siteApps = siteApps();
        logger.info("TODO: apps {}, sites {}", apps.keySet(), siteApps);

        this.apps = ImmutableMap.copyOf(apps);

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

    private Set<String> siteApps() {
        File appsFile = environment.pluginsFile();
        Set<String> siteApps = Sets.newHashSet();
        if (!appsFile.exists()) {
            return siteApps;
        }
        if (!appsFile.isDirectory()) {
            return siteApps;
        }
        File[] appsFiles = appsFile.listFiles();
        for (File appFile : appsFiles) {
            if (new File(appFile, "_site").exists()) {
                siteApps.add(appFile.getName());
            }
        }
        return siteApps;
    }

    private void loadAppsIntoClassLoader() {
        File appsFile = environment.pluginsFile();
        if (!appsFile.exists()) {
            return;
        }
        if (!appsFile.isDirectory()) {
            return;
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
            logger.debug("failed to find addURL method on classLoader [" + classLoader + "] to add methods");
            return;
        }

        File[] appsFiles = appsFile.listFiles();
        for (File appFile : appsFiles) {
            if (appFile.isDirectory()) {
                logger.trace("--- adding app [" + appFile.getAbsolutePath() + "]");
                try {
                    // add the root
                    addURL.invoke(classLoader, appFile.toURI().toURL());
                    // gather files to add
                    List<File> libFiles = Lists.newArrayList();
                    if (appFile.listFiles() != null) {
                        libFiles.addAll(Arrays.asList(appFile.listFiles()));
                    }
                    File libLocation = new File(appFile, "lib");
                    if (libLocation.exists() && libLocation.isDirectory() && libLocation.listFiles() != null) {
                        libFiles.addAll(Arrays.asList(libLocation.listFiles()));
                    }

                    // if there are jars in it, add it as well
                    for (File libFile : libFiles) {
                        if (!(libFile.getName().endsWith(".jar") || libFile.getName().endsWith(".zip"))) {
                            continue;
                        }
                        addURL.invoke(classLoader, libFile.toURI().toURL());
                    }
                } catch (Exception e) {
                    logger.warn("failed to add app [" + appFile + "]", e);
                }
            }
        }
    }

    private Map<String, App> loadAppsFromClasspath(Settings settings) {
        Map<String, App> apps = newHashMap();
        Enumeration<URL> appUrls = null;
        try {
            appUrls = settings.getClassLoader().getResources("es-plugin.properties");
        } catch (IOException e1) {
            logger.warn("failed to find properties on classpath", e1);
            return ImmutableMap.of();
        }
        while (appUrls.hasMoreElements()) {
            URL appUrl = appUrls.nextElement();
            Properties appProps = new Properties();
            InputStream is = null;
            try {
                is = appUrl.openStream();
                appProps.load(is);
                String appClassName = appProps.getProperty("plugin");
                App app = loadApp(appClassName, settings);
                apps.put(app.name(), app);
            } catch (Exception e) {
                logger.warn("failed to load app from [" + appUrl + "]", e);
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
        return apps;
    }

    private App loadApp(String className, Settings settings) {
        try {
            Class<? extends App> appClass = (Class<? extends App>) settings.getClassLoader().loadClass(className);
            try {
                return appClass.getConstructor(Settings.class).newInstance(settings);
            } catch (NoSuchMethodException e) {
                try {
                    return appClass.getConstructor().newInstance();
                } catch (NoSuchMethodException e1) {
                    throw new ElasticSearchException("No constructor for [" + appClass + "]. An App class must "
                            + "have either an empty default constructor or a single argument constructor accepting a "
                            + "Settings instance");
                }
            }

        } catch (Exception e) {
            throw new ElasticSearchException("Failed to load app [" + className + "]", e);
        }

    }
}
