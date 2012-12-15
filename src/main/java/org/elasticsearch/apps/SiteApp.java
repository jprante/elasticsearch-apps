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

import static org.elasticsearch.common.settings.ImmutableSettings.Builder.EMPTY_SETTINGS;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.CloseableIndexComponent;

/**
 * A web site archive as an App
 * 
 * @author joerg
 */
public class SiteApp implements App {

    private final URL url;
    private final String name;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String type;

    public SiteApp(String groupId, String name, String version, URL url) {
        this.url = url;
        this.name = name;
        this.version = version;
        this.groupId = groupId;
        String path = url.getPath();
        int pos = path.lastIndexOf("/");
        String file = pos >= 0 ? path.substring(pos + 1) : path;
        pos = file.lastIndexOf(".");
        this.type = pos >= 0 ? file.substring(pos + 1) : file;
        this.artifactId = name;
    }

    public String getPathName() {
        String pathName = name;
        if (name.startsWith("elasticsearch-")) {
            pathName = name.substring("elasticsearch-".length());
        } else if (name.startsWith("es-")) {
            pathName = name.substring("es-".length());
        }
        return pathName;
    }   
    
    public File getInstallPath(Environment environment) {
        return new File(environment.pluginsFile(), getPathName());
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
        return type;
    }

    @Override
    public String name() {
        return name;
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
        return EMPTY_SETTINGS;
    }
}
