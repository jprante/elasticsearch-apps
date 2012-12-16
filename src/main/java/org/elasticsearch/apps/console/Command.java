package org.elasticsearch.apps.console;

import java.io.Console;
import java.util.List;
import org.elasticsearch.ElasticSearchIllegalArgumentException;
import org.elasticsearch.apps.App;
import org.elasticsearch.apps.AppService;
import org.elasticsearch.apps.ArtifactApp;
import org.elasticsearch.apps.SiteApp;
import org.elasticsearch.common.settings.Settings;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;

public enum Command {

    EXIT(new ExitAction()),
    LS(new LsAction()),
    RM(new RmAction()),
    LIST(new LsAction()),
    RESOLVE(new ResolveAction());

    private interface Action {

        public void exec(Console c, Settings settings, AppService service, List<String> params) throws Exception;
    }

    public interface Listener {

        public void exception(Exception e);
    }
    private Action action;

    private Command(Action a) {
        this.action = a;
    }

    public void exec(final Console c, Settings settings, AppService service, List<String> params, final Listener l) {
        try {
            action.exec(c, settings, service, params);
        } catch (Exception e) {
            l.exception(e);
        }
    }

    static class ExitAction implements Action {

        @Override
        public void exec(Console c, Settings settings, AppService service, List<String> params) {
            c.printf("Bye%n");
            System.exit(0);
        }
    }

    static class LsAction implements Action {

        @Override
        public void exec(Console c, Settings settings, AppService service, List<String> params) throws Exception {
            for (App app : service.apps()) {
                boolean isArtifact = app instanceof ArtifactApp;
                boolean isSite = app instanceof SiteApp;
                System.out.println(app.getCanonicalForm() + " (" + (isArtifact ? "artifact" : isSite ? "site" : "") + ")");
            }
        }
    }

    static class RmAction implements Action {

        @Override
        public void exec(Console c, Settings settings, AppService service, List<String> params) throws Exception {
            if (params.size() < 1) {
                throw new ElasticSearchIllegalArgumentException("can't resolve");
            }
            service.removeArtifacts(params.get(0));
        }
    }

    static class ResolveAction implements Action {

        @Override
        public void exec(Console c, Settings settings, AppService service, List<String> params) throws Exception {
            // <canonical> <scope>
            if (params.size() < 1) {
                throw new ElasticSearchIllegalArgumentException("can't resolve");
            }
            for (MavenResolvedArtifact artifact : service.resolveArtifact(params.get(0),
                    params.size() > 1 ? params.get(1) : null,
                    AppService.DEFAULT_EXCLUDE)) {
                System.out.println(artifact.getCoordinate().toCanonicalForm());
            }
        }
    }
}