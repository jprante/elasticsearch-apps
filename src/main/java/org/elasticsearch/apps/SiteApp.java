package org.elasticsearch.apps;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.CloseableIndexComponent;

public class SiteApp implements App {
    
    URL url;
    
    String groupId;
    
    String artifactId;
    
    String version;
    
    String type;
    
    SiteApp(URL url) {
        this.url = url;
        String path = url.getPath();
        int pos = path.lastIndexOf("/");
        String file = pos >= 0 ? path.substring(pos+1) : path;
        groupId = "org.elasticsearch.apps.site";
        pos = file.lastIndexOf(".");
        type = pos >= 0 ? file.substring(pos+1) : file;
        String basename = file.substring(0, pos);
        pos = basename.lastIndexOf("-");
        version = pos >= 0 ? basename.substring(pos+1) : "0";
        artifactId = basename.substring(0, pos);
    }
    
    public File getInstallPath(Environment environment) {
        return new File(environment.pluginsFile(), artifactId);
    }

    @Override    
    public String getCanonicalForm() {
        return groupId + ":" + artifactId + ":" + version + ":" + type;        
    }
    
    @Override
    public String groupId() {
        return groupId;
    }

    @Override
    public String artifactId() {
        return artifactId;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public String classifier() {
        return "site";
    }

    @Override
    public String type() {
        return "site";
    }

    @Override
    public String name() {
        return groupId + ":" + artifactId + ":" + version;
    }

    @Override
    public String description() {
        return "Site plugin from " + url;
    }

    @Override
    public Collection<Class<? extends Module>> modules() {
        return null;
    }

    @Override
    public Collection<? extends Module> modules(Settings stngs) {
        return null;
    }

    @Override
    public Collection<Class<? extends LifecycleComponent>> services() {
        return null;
    }

    @Override
    public Collection<Class<? extends Module>> indexModules() {
        return null;
    }

    @Override
    public Collection<? extends Module> indexModules(Settings stngs) {
        return null;
    }

    @Override
    public Collection<Class<? extends CloseableIndexComponent>> indexServices() {
        return null;
    }

    @Override
    public Collection<Class<? extends Module>> shardModules() {
        return null;
    }

    @Override
    public Collection<? extends Module> shardModules(Settings stngs) {
        return null;
    }

    @Override
    public Collection<Class<? extends CloseableIndexComponent>> shardServices() {
        return null;
    }

    @Override
    public void processModule(Module module) {
    }

    @Override
    public Settings additionalSettings() {
        return null;
    }
}
