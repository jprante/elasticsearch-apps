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
package org.elasticsearch.apps.console;

import java.io.Console;
import java.util.List;
import java.util.Set;
import org.elasticsearch.ElasticSearchIllegalArgumentException;
import org.elasticsearch.apps.App;
import org.elasticsearch.apps.AppComparator;
import org.elasticsearch.apps.AppService;
import org.elasticsearch.apps.ArtifactApp;
import org.elasticsearch.apps.SiteApp;
import org.elasticsearch.common.collect.Sets;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;

public enum Command {

    EXIT(new ExitAction()),
    LS(new LsAction()),
    RM(new RmAction()),
    LIST(new LsAction()),
    RESOLVE(new ResolveAction()),
    INSTALL(new InstallAction());

    private interface Action {

        public void exec(Console c, AppService service, List<String> params) throws Exception;
    }

    public interface Listener {

        public void exception(Exception e);
    }
    private Action action;

    private Command(Action a) {
        this.action = a;
    }

    public void exec(final Console c, AppService service, List<String> params, final Listener l) {
        try {
            action.exec(c, service, params);
        } catch (Exception e) {
            l.exception(e);
        }
    }

    static class ExitAction implements Action {

        @Override
        public void exec(Console c, AppService service, List<String> params) {
            c.printf("Bye%n");
            System.exit(0);
        }
    }

    static class LsAction implements Action {

        @Override
        public void exec(Console c,  AppService service, List<String> params) throws Exception {
            Set<App> sortedApps = Sets.newTreeSet(new AppComparator());
            sortedApps.addAll(service.apps());
            for (App app : sortedApps) {
                boolean isArtifact = app instanceof ArtifactApp;
                boolean isSite = app instanceof SiteApp;
                System.out.println(app.getCanonicalForm() + " (" + (isArtifact ? "artifact" : isSite ? "site" : "plugin") + ")");
            }
        }
    }

    static class RmAction implements Action {

        @Override
        public void exec(Console c, AppService service, List<String> params) throws Exception {
            if (params.size() < 1) {
                throw new ElasticSearchIllegalArgumentException("can't remove with a given dendencendy");
            }
            String dependency = params.get(0);
            service.removeArtifacts(dependency);
        }
    }

    static class ResolveAction implements Action {

        @Override
        public void exec(Console c, AppService service, List<String> params) throws Exception {
            // <canonical> <scope>
            if (params.size() < 1) {
                throw new ElasticSearchIllegalArgumentException("can't resolve without a given dependency");
            }
            for (MavenResolvedArtifact artifact : service.resolveArtifact(params.get(0),
                    params.size() > 1 ? params.get(1) : null,
                    AppService.DEFAULT_EXCLUDE)) {
                System.out.println("resolved: " + artifact.getCoordinate().toCanonicalForm());
            }
        }
    }
    
    static class InstallAction implements Action {

        @Override
        public void exec(Console c, AppService service, List<String> params) throws Exception {
            // Note: in a stand alone app like bin/app, installing is useful for testing if an app
            // install works. For example, is es-plugin.properties correct. Or, will the plugin 
            // install on the current Elasticsearch version successfully.
            // It can't inject apps into a running node, since the node needs a restart.
            if (params.size() < 1) {
                throw new ElasticSearchIllegalArgumentException("can't install without a given dependency");
            }
            String dependency = params.get(0);
            // TODO excludes as param
            String[] excludes = AppService.DEFAULT_EXCLUDE;
            ArtifactApp app = service.toArtifactApp(dependency, excludes);
            if (app != null) {
                App oldApp = service.installApp(app);
                System.out.println("successfully installed: " + app.getCanonicalForm()
                        + (oldApp != null ? " (previous app was " + oldApp.getCanonicalForm() + ")" : ""));                
            } else {
                System.out.println("can't install " + dependency);
            }
        }
    }    
}