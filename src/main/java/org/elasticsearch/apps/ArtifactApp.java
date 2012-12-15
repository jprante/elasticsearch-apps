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

import java.net.URL;
import java.util.Collection;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.CloseableIndexComponent;
import org.elasticsearch.plugins.Plugin;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;

/**
 * A Maven Artifact as an App
 * 
 * @author joerg
 */
public class ArtifactApp implements App {
    
    private final URL url;
    
    private final MavenResolvedArtifact artifact;
    
    private final Plugin plugin;
    
    ArtifactApp(URL url, MavenResolvedArtifact artifact, Plugin plugin) {
        this.url = url;
        this.artifact = artifact;
        this.plugin = plugin;
    }
    
    public URL getURL() {
        return url;
    }
    
    @Override    
    public String getCanonicalForm() {
        return artifact.getCoordinate().toCanonicalForm();        
    }
    
    @Override
    public String groupId() {
        return artifact.getCoordinate().getGroupId();
    }

    @Override
    public String artifactId() {
        return artifact.getCoordinate().getArtifactId();
    }

    @Override
    public String version() {
        return artifact.getCoordinate().getVersion();
    }

    @Override
    public String classifier() {
        return artifact.getCoordinate().getClassifier();
    }

    @Override
    public String type() {
        return artifact.getExtension();
    }

    @Override
    public String name() {
        return plugin.name();
    }

    @Override
    public String description() {
        return plugin.description();
    }

    @Override
    public Collection<Class<? extends Module>> modules() {
        return plugin.modules();
    }

    @Override
    public Collection<? extends Module> modules(Settings stngs) {
        return plugin.modules(stngs);
    }

    @Override
    public Collection<Class<? extends LifecycleComponent>> services() {
        return plugin.services();
    }

    @Override
    public Collection<Class<? extends Module>> indexModules() {
        return plugin.indexModules();
    }

    @Override
    public Collection<? extends Module> indexModules(Settings stngs) {
        return plugin.indexModules(stngs);
    }

    @Override
    public Collection<Class<? extends CloseableIndexComponent>> indexServices() {
        return plugin.indexServices();
    }

    @Override
    public Collection<Class<? extends Module>> shardModules() {
        return plugin.shardModules();
    }

    @Override
    public Collection<? extends Module> shardModules(Settings stngs) {
        return plugin.shardModules(stngs);
    }

    @Override
    public Collection<Class<? extends CloseableIndexComponent>> shardServices() {
        return plugin.shardServices();
    }

    @Override
    public void processModule(Module module) {
        plugin.processModule(module);
    }

    @Override
    public Settings additionalSettings() {
        return plugin.additionalSettings();
    }
    
    @Override
    public String toString() {
        return getCanonicalForm();
    }
    
}
