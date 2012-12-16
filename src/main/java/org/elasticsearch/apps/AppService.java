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
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.apps.support.ExceptionFormatter;
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
import org.jboss.shrinkwrap.resolver.api.maven.strategy.AcceptScopesStrategy;

public class AppService extends AbstractComponent {

    /**
     * The resource that identifies a plugin when it's found on the classpath
     */
    public final static String DEFAULT_RESOURCE = "es-plugin.properties";
    /**
     * The Maven settings that is loaded for dependency resolution
     */
    public final static String DEFAULT_SETTINGS = "config/apps.xml";
    /**
     * The artifacts that are excluded by default.
     */
    public final static String[] DEFAULT_EXCLUDE = new String[]{"org.elasticsearch:elasticsearch"};
    private final Environment environment;
    private ClassLoader classLoader;
    private Map<App, List<OnModuleReference>> onModuleReferences;
    /**
     * A map for all apps
     */
    private Map<String, App> apps = newHashMap();
    /**
     * A map for apps that are maven artifacts
     */
    private Map<String, ArtifactApp> artifactApps = newHashMap();
    /**
     * A map for apps that are sites
     */
    private Map<String, SiteApp> siteApps = newHashMap();
    /**
     * A map for apps that are legacy plugins
     */
    private Map<String, PluginApp> pluginApps = newHashMap();

    /**
     * Installing a SSL trust manager to accept HTTPS connections when
     * downloading
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
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Constructs a new AppService.
     *
     * When constructed, a new instance of an AppService refreshes all apps,
     * checks for mandatory apps, and prints loaded apps to log.
     *
     * @param settings the settings
     * @param environment the environment
     */
    public AppService(Settings settings, Environment environment) {
        super(settings);
        this.environment = environment;
        // refresh all apps
        refreshAllApps();
        // check if all mandatory apps are there
        checkMandatory();
        // log state to users
        logger.info("loaded artifact apps {}", artifactApps.keySet());
        logger.info("loaded plugin apps {}", pluginApps.keySet());
        logger.info("loaded site apps {}", siteApps.keySet());
    }

    /**
     * Return the apps
     *
     * @return the apps
     */
    public Collection<App> apps() {
        return apps.values();
    }

    /**
     * Return the artifact apps
     *
     * @return the artifact apps
     */
    public Collection<ArtifactApp> artifactApps() {
        return artifactApps.values();
    }

    /**
     * Return the site apps
     *
     * @return the site apps
     */
    public Collection<SiteApp> siteApps() {
        return siteApps.values();
    }

    /**
     * return the plugin apps
     *
     * @return the plugin apps
     */
    public Collection<PluginApp> pluginApps() {
        return pluginApps.values();
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Refresh all apps
     *
     */
    public synchronized void refreshAllApps() {
        // reset class loader... TODO
        this.classLoader = settings.getClassLoader();

        this.artifactApps = refreshArtifactApps();
        this.pluginApps = refreshPluginApps();
        this.siteApps = refreshSiteApps();

        this.apps = newHashMap();
        apps.putAll(artifactApps);
        apps.putAll(pluginApps);
        apps.putAll(siteApps);

        checkMandatory();

        MapBuilder<App, List<OnModuleReference>> refs = MapBuilder.newMapBuilder();
        for (App app : apps.values()) {
            List<OnModuleReference> list = onModuleRefs(app);
            if (!list.isEmpty()) {
                refs.put(app, list);
            }
        }
        this.onModuleReferences = refs.map();
    }

    /**
     * Resolve artifact
     *
     * @param settings the settings
     * @param dependency the canonical form of the dependency
     * @param scope the scope, one of "compile", "runtime", "provided", "test",
     * "system", "import"
     * @param excludes canonical names of the dependencies to be excluded
     * @return the resolved Maven artifacts
     */
    public MavenResolvedArtifact[] resolveArtifact(String dependency, String scope, String[] excludes) {
        final String mavenSettingsFile = settings.get("apps.settings", DEFAULT_SETTINGS);
        final boolean useMavenCentral = settings.getAsBoolean("apps.usemavencentral", Boolean.TRUE);
        final String[] defaultExcludes = settings.getAsArray("apps.excludes", DEFAULT_EXCLUDE);
        final MavenDependencyExclusion[] exclusions = new MavenDependencyExclusion[defaultExcludes.length + excludes.length];
        for (int i = 0; i < defaultExcludes.length; i++) {
            exclusions[i] = MavenDependencies.createExclusion(defaultExcludes[i]);
        }
        for (int i = 0; i < excludes.length; i++) {
            exclusions[defaultExcludes.length + i] = MavenDependencies.createExclusion(excludes[i]);
        }
        ScopeType scopeType = scope != null ? ScopeType.fromScopeType(scope) : ScopeType.RUNTIME;
        // optional = false
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
     * Resolve and return artifact as app
     *
     * @param dependency
     * @param excludes
     * @return
     */
    public ArtifactApp toArtifactApp(String dependency, String[] excludes) {
        MavenResolvedArtifact[] artifacts = resolveArtifact(dependency, "runtime", excludes);
        Iterator<ArtifactApp> it = loadArtifacts(artifacts).values().iterator();
        // return first artifact, it's the parent artifact, the app
        return it.hasNext() ? it.next() : null;
    }

    /**
     * Remove artifact(s).
     *
     * @param settings
     * @param dependency
     */
    public void removeArtifacts(String dependency) {
        String mavenSettingsFile = settings.get("apps.settings", DEFAULT_SETTINGS);
        MavenDependency dep = MavenDependencies.createDependency(dependency,
                ScopeType.RUNTIME, false);
        File[] artifactFiles = Maven.configureResolver().fromFile(mavenSettingsFile)
                .offline()
                .addDependency(dep)
                .resolve()
                .withoutTransitivity()
                .asFile();
        if (artifactFiles != null) {
            for (File artifactFile : artifactFiles) {
                // parent is the version file, a directory
                logger.warn("deleting {}", artifactFile.getParent());
                deleteDirectory(artifactFile.getParentFile());
            }
        }
    }

    /**
     * Instantiate a single app. Returns old app if there was an old app.
     *
     * @param app the new app
     * @return the old app if any existed
     */
    public App installApp(App app) {
        if (app == null) {
            return null;
        }
        App oldApp = null;
        if (app instanceof ArtifactApp) {
            oldApp = artifactApps.put(app.getCanonicalForm(), (ArtifactApp) app);
        } else if (app instanceof PluginApp) {
            oldApp = pluginApps.put(app.getCanonicalForm(), (PluginApp) app);
        } else if (app instanceof SiteApp) {
            oldApp = siteApps.put(app.getCanonicalForm(), (SiteApp) app);
        }
        apps.put(app.getCanonicalForm(), app);
        List<OnModuleReference> list = onModuleRefs(app);
        if (!list.isEmpty()) {
            onModuleReferences.put(app, list);
        }
        return oldApp;
    }

    /**
     * Helper method for refreshing all declared artifact apps
     *
     * @return a map of artifact apps that are present after refreshing
     */
    private Map<String, ArtifactApp> refreshArtifactApps() {
        final String mavenSettingsFile = settings.get("apps.settings", DEFAULT_SETTINGS);
        final boolean useMavenCentral = settings.getAsBoolean("apps.usemavencentral", Boolean.TRUE);
        final Map<String, Settings> appSettings = settings.getGroups("apps.dependencies");
        final String[] defaultExcludes = settings.getAsArray("apps.excludes", DEFAULT_EXCLUDE);
        Set<MavenDependency> defaultDeps = Sets.newHashSet();
        for (Map.Entry<String, Settings> entry : appSettings.entrySet()) {
            String name = entry.getKey(); // not used yet
            String dependency = entry.getValue().get("dependency");
            String[] excludes = entry.getValue().getAsArray("exclude");
            MavenDependencyExclusion[] exclusions = new MavenDependencyExclusion[defaultExcludes.length + excludes.length];
            for (int i = 0; i < defaultExcludes.length; i++) {
                exclusions[i] = MavenDependencies.createExclusion(defaultExcludes[i]);
            }
            for (int i = 0; i < excludes.length; i++) {
                exclusions[defaultExcludes.length + i] = MavenDependencies.createExclusion(excludes[i]);
            }
            ScopeType scopeType = ScopeType.RUNTIME;
            defaultDeps.add(MavenDependencies.createDependency(dependency,
                    scopeType, false, exclusions));
        }
        MavenResolvedArtifact[] artifacts = defaultDeps.isEmpty() ? null : Maven.configureResolver()
                .fromFile(mavenSettingsFile)
                .addDependencies(defaultDeps)
                .resolve()
                .withMavenCentralRepo(useMavenCentral)
                .withTransitivity()
                .asResolvedArtifact();
        return loadArtifacts(artifacts);
    }

    /**
     * Helper method for refreshing all plugin apps
     *
     * @return a map of plugin apps that are present after refreshing
     */
    private Map<String, PluginApp> refreshPluginApps() {
        final Map<String, PluginApp> loadedApps = newHashMap();
        final HttpDownloadHelper downloadHelper = new HttpDownloadHelper();
        final String pluginGroupId = settings.get("apps.plugingroup", PluginApp.GROUP_ID);
        final Map<String, Settings> pluginSettings = settings.getGroups("apps.plugins");
        // download plugins, if not already present
        for (Map.Entry<String, Settings> entry : pluginSettings.entrySet()) {
            try {
                String name = entry.getKey();
                URL url = new URL(entry.getValue().get("url"));
                String version = entry.getValue().get("version", "0");
                PluginApp app = new PluginApp(pluginGroupId, name, version, url);
                File appFile = app.getInstallPath(environment);
                if (appFile.exists()) {
                    loadedApps.put(app.getCanonicalForm(), app);
                } else {
                    appFile.mkdirs();
                    logger.info("retrieving legacy plugin from URL {}", url);
                    // only zip supported
                    File zipFile = new File(environment.pluginsFile(), name + ".zip");
                    downloadHelper.download(url, zipFile, null);
                    // extract zip
                    unzip(environment, new ZipFile(zipFile), app.getInstallPath(environment), app.getPathName());
                    zipFile.delete();
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return loadPlugins(environment.pluginsFile());
    }

    /**
     * Helper method for refreshing all site apps
     *
     * @return a map of the loaded site apps
     */
    private Map<String, SiteApp> refreshSiteApps() {
        final Map<String, SiteApp> loadedApps = newHashMap();
        final HttpDownloadHelper downloadHelper = new HttpDownloadHelper();
        final String siteGroupId = settings.get("apps.sitegroup", SiteApp.GROUP_ID);
        final Map<String, Settings> siteSettings = settings.getGroups("apps.sites");
        // loop over all declared site apps
        for (Map.Entry<String, Settings> entry : siteSettings.entrySet()) {
            try {
                String name = entry.getKey();
                URL url = new URL(entry.getValue().get("url"));
                String version = entry.getValue().get("version", "0");
                SiteApp app = new SiteApp(siteGroupId, name, version, url);
                File appFile = app.getInstallPath(environment);
                if (appFile.exists()) {
                    // already downloaded and expanded
                    loadedApps.put(app.getCanonicalForm(), app);
                } else {
                    // download
                    appFile.mkdirs();
                    logger.info("retrieving site plugin from URL {}", url);
                    // only zip supported
                    File zipFile = new File(environment.pluginsFile(), name + ".zip");
                    downloadHelper.download(url, zipFile, null);
                    // extract zip
                    unzip(environment, new ZipFile(zipFile), app.getInstallPath(environment), app.getPathName());
                    if (new File(appFile, "_site").exists()) {
                        loadedApps.put(app.getCanonicalForm(), app);
                    }
                    zipFile.delete();
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return loadedApps;
    }

    /**
     * A helper method for checking if all mandatory apps are present.
     */
    private void checkMandatory() {
        String[] mandatoryApps = settings.getAsArray("apps.mandatory", null);
        if (mandatoryApps != null) {
            Set<String> missingApps = Sets.newHashSet();
            for (String mandatoryApp : mandatoryApps) {
                boolean found = false;
                // do not check versions
                for (App app : apps.values()) {
                    String appName = app.groupId() + ":" + app.artifactId();
                    if (mandatoryApp.startsWith(appName)) {
                        found = true;
                    }
                }
                if (!found && !missingApps.contains(mandatoryApp)) {
                    missingApps.add(mandatoryApp);
                }
            }
            if (!missingApps.isEmpty()) {
                throw new ElasticSearchException("Missing mandatory apps [" + Strings.collectionToDelimitedString(missingApps, ", ") + "]");
            }
        }
    }

    /**
     * Helper method for loading artifacts and building a map of the
     * artifact-based apps.
     *
     * @param artifacts the artifacts that will be checked for ES plugins
     * @return a map of artifacts
     */
    private Map<String, ArtifactApp> loadArtifacts(MavenResolvedArtifact[] artifacts) {
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
        Map<String, ArtifactApp> map = newHashMap();
        if (addURL == null) {
            logger.error("failed to find addURL method on classLoader [" + classLoader + "] to add methods");
            return map;
        }
        // no artifacts?
        if (artifacts == null) {
            logger.warn("no artifacts to load");
            return map;
        }
        // Now we want to know the relationship between class path and JAR.
        // build an URL map to assign found plugin on classpath to artifact
        Map<URL, MavenResolvedArtifact> jars = newHashMap();
        for (MavenResolvedArtifact artifact : artifacts) {
            if (artifact.getCoordinate().getType().equals(PackagingType.JAR)) {
                try {
                    URL url = artifact.asFile().toURI().toURL();
                    addURL.invoke(classLoader, url);
                    jars.put(url, artifact);
                } catch (Exception e) {
                    logger.warn("failed to add [{}]", artifact.getCoordinate(), e);
                }
            } else {
                logger.warn("not a jar artifact: [{}]", artifact.getCoordinate());
            }
        }
        // now, that everything is on the class path, build the artifact app map.
        Enumeration<URL> propUrls = null;
        try {
            propUrls = classLoader.getResources(DEFAULT_RESOURCE);
        } catch (IOException e1) {
            logger.warn("failed to find resources on classpath", e1);
            return map;
        }
        while (propUrls.hasMoreElements()) {
            URL propUrl = propUrls.nextElement();
            Properties appProps = new Properties();
            InputStream is = null;
            try {
                is = propUrl.openStream();
                appProps.load(is);
                String appClassName = appProps.getProperty("plugin");
                Plugin plugin = instantiatePluginClass(appClassName);
                // lookup for artifact in the jars map
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
                        logger.warn("can't find artifact jar for " + propUrl + ", skipping");
                    }
                }
            } catch (Exception e) {
                logger.warn("failed to load artifact from [" + propUrl + "]", e);
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

    /**
     * Helper for loading all plugins into the class path and building a plugin
     * app map.
     *
     * @param pluginsFile the base folder for the plugins
     * @return
     */
    private Map<String, PluginApp> loadPlugins(File pluginsFile) {
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
        Map<String, PluginApp> map = newHashMap();
        if (addURL == null) {
            logger.error("failed to find addURL method on classLoader [" + classLoader + "] to add methods");
            return map;
        }
        // traverse all legacy plugins in the plugins folder
        File[] pluginsFiles = pluginsFile.listFiles();
        if (pluginsFiles == null) {
            logger.warn("no files exist in {}", pluginsFile.getAbsolutePath());
            return map;
        }
        for (File pluginFile : pluginsFiles) {
            if (pluginFile.isDirectory()) {
                try {
                    // add the root
                    addURL.invoke(classLoader, pluginFile.toURI().toURL());
                    // gather files to add
                    List<File> libFiles = Lists.newArrayList();
                    if (pluginFile.listFiles() != null) {
                        libFiles.addAll(Arrays.asList(pluginFile.listFiles()));
                    }
                    File libLocation = new File(pluginFile, "lib");
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
                    logger.warn("failed to add plugin [{}]", pluginFile, e);
                }
            }
        }
        // now, everything is on the class path, build the plugin app map
        Enumeration<URL> propUrls = null;
        try {
            propUrls = classLoader.getResources(DEFAULT_RESOURCE);
        } catch (IOException e1) {
            logger.warn("failed to find resources on classpath", e1);
            return map;
        }
        while (propUrls.hasMoreElements()) {
            URL propUrl = propUrls.nextElement();
            Properties appProps = new Properties();
            InputStream is = null;
            try {
                // skip jar URLs, they are artifact apps
                //if (!propUrl.getProtocol().equals("jar")) {
                is = propUrl.openStream();
                appProps.load(is);
                String appClassName = appProps.getProperty("plugin");
                Plugin plugin = instantiatePluginClass(appClassName);
                if (isArtifactPlugin(plugin)) {
                    logger.warn("plugin at [{}] is already present as artifact app, skipping", propUrl);
                } else {
                    PluginApp app = new PluginApp(PluginApp.GROUP_ID, propUrl, plugin);
                    map.put(app.getCanonicalForm(), app);
                }
                //} else {
                //    logger.warn("skipped {}", propUrl);
                //}
            } catch (Exception e) {
                logger.warn("failed to load plugin from [{}], reason: {}", propUrl, ExceptionFormatter.format(e));
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

    /**
     * Helper method for checking if this plugin name is already in use by and
     * artifact app.
     *
     * @param plugin
     * @return
     */
    private boolean isArtifactPlugin(Plugin plugin) {
        if (artifactApps == null) {
            return false;
        }
        for (ArtifactApp app : artifactApps.values()) {
            if (plugin.name().equals(app.name())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper method for instantiating a Plugin class
     *
     * @param className the class name of the Plugin
     * @return a Plugin instance
     */
    private Plugin instantiatePluginClass(String className) {
        try {
            Class<? extends Plugin> pluginClass = (Class<? extends Plugin>) classLoader.loadClass(className);
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
     * Helper for unzipping downloaded zips
     *
     * @param environment
     * @throws IOException
     */
    private void unzip(Environment environment, ZipFile zipFile, File targetFile, String targetPath) throws IOException {
        String baseDirSuffix = null;
        try {
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
                String zipEntryName = zipEntry.getName();
                zipEntryName = zipEntryName.replace('\\', '/');
                if (baseDirSuffix != null && zipEntryName.startsWith(baseDirSuffix)) {
                    zipEntryName = zipEntryName.substring(baseDirSuffix.length());
                }
                File target = new File(targetFile, zipEntryName);
                FileSystemUtils.mkdirs(target.getParentFile());
                Streams.copy(zipFile.getInputStream(zipEntry), new FileOutputStream(target));
            }
        } catch (IOException e) {
            logger.error("failed to extract zip [" + zipFile.getName() + "]: " + ExceptionsHelper.detailedMessage(e));
            return;
        } finally {
            try {
                zipFile.close();
            } catch (IOException e) {
                // ignore
            }
        }
        File binFile = new File(targetFile, "bin");
        if (binFile.exists() && binFile.isDirectory()) {
            File toLocation = new File(new File(environment.homeFile(), "bin"), targetPath);
            logger.info("found bin, moving to " + toLocation.getAbsolutePath());
            FileSystemUtils.deleteRecursively(toLocation);
            binFile.renameTo(toLocation);
        }
        if (!new File(targetFile, "_site").exists()) {
            if (!FileSystemUtils.hasExtensions(targetFile, ".class", ".jar")) {
                logger.info("identified as a _site plugin, moving to _site structure ...");
                File site = new File(targetFile, "_site");
                File tmpLocation = new File(environment.pluginsFile(), targetPath + ".tmp");
                targetFile.renameTo(tmpLocation);
                FileSystemUtils.mkdirs(targetFile);
                tmpLocation.renameTo(site);
            }
        }
    }

    /**
     * Helper method for deleting a directory in a local artifact repo.
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
     * Helper method to invoke plugin module hooks.
     *
     * @param app the app to invoke
     * @return an immutable map of the on-module refs of the apps
     */
    private List<OnModuleReference> onModuleRefs(App app) {
        List<OnModuleReference> list = Lists.newArrayList();
        for (Method method : app.getClass().getDeclaredMethods()) {
            if (!method.getName().equals("onModule")) {
                continue;
            }
            if (method.getParameterTypes().length == 0 || method.getParameterTypes().length > 1) {
                logger.warn("plugin {} implementing onModule with no parameters or more than one parameter", app.name());
                continue;
            }
            Class moduleClass = method.getParameterTypes()[0];
            if (!Module.class.isAssignableFrom(moduleClass)) {
                logger.warn("plugin {} implementing onModule by the type is not of Module type {}", app.name(), moduleClass);
                continue;
            }
            method.setAccessible(true);
            list.add(new OnModuleReference(moduleClass, method));
        }
        return list;
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

    static class OnModuleReference {

        public final Class<? extends Module> moduleClass;
        public final Method onModuleMethod;

        OnModuleReference(Class<? extends Module> moduleClass, Method onModuleMethod) {
            this.moduleClass = moduleClass;
            this.onModuleMethod = onModuleMethod;
        }
    }
}
