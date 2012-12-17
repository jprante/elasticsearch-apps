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

import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.inject.PreProcessModule;
import org.elasticsearch.common.inject.SpawnModules;
import org.elasticsearch.common.settings.Settings;

import java.util.Collection;
import java.util.List;

import static org.elasticsearch.common.inject.Modules.createModule;

/**
 *
 */
public class AppModule extends AbstractModule implements SpawnModules, PreProcessModule {

    private final Settings settings;

    private final AppService appService;

    public AppModule(Settings settings, AppService appService) {
        this.settings = settings;
        this.appService = appService;
    }

    @Override
    public Iterable<? extends Module> spawnModules() {
        List<Module> modules = Lists.newArrayList();
        Collection<Class<? extends Module>> modulesClasses = appService.modules();
        for (Class<? extends Module> moduleClass : modulesClasses) {
            modules.add(createModule(moduleClass, settings));
        }
        modules.addAll(appService.modules(settings));
        return modules;
    }

    @Override
    public void processModule(Module module) {
        appService.processModule(module);
    }

    @Override
    protected void configure() {
        bind(AppService.class).toInstance(appService);
    }
}
