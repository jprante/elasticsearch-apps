package org.elasticsearch.apps;

import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;

import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.internal.InternalSettingsPerparer;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.testng.annotations.Test;

public class AppResolverServiceTests {

    @Test
    public void testMavenArtifactApp() {
        Settings pSettings = settingsBuilder()
                .put("apps.settings", AppService.DEFAULT_SETTINGS)
                .put("apps.dependencies.river-rabbitmq.dependency", "org.elasticsearch:elasticsearch-river-rabbitmq:0.17.10")
                .build();
        Tuple<Settings, Environment> tuple = InternalSettingsPerparer.prepareSettings(pSettings, false);
        Settings settings = settingsBuilder().put(tuple.v1()).build();
        Environment environment = tuple.v2();
        AppService service = new AppService(settings, environment);
    }

    @Test
    public void testSiteApp() {
        Settings pSettings = settingsBuilder()
                .put("apps.settings", AppService.DEFAULT_SETTINGS)
                .put("apps.sites.elasticsearch-head.url", "https://github.com/mobz/elasticsearch-head/archive/master.zip")
                .build();
        Tuple<Settings, Environment> tuple = InternalSettingsPerparer.prepareSettings(pSettings, false);
        Settings settings = settingsBuilder().put(tuple.v1()).build();
        Environment environment = tuple.v2();
        AppService service = new AppService(settings, environment);
    }

    @Test
    public void testResolveArtifact() {
        Settings pSettings = settingsBuilder()
                .put("apps.settings", AppService.DEFAULT_SETTINGS)
                .build();
        Tuple<Settings, Environment> tuple = InternalSettingsPerparer.prepareSettings(pSettings, false);
        Settings settings = settingsBuilder().put(tuple.v1()).build();
        Environment environment = tuple.v2();
        AppService service = new AppService(settings, environment);
        MavenResolvedArtifact[] artifacts = service.resolveArtifact(settings, 
                "org.elasticsearch:elasticsearch-river-rabbitmq:0.17.10", 
                "runtime",
                AppService.DEFAULT_EXCLUDE);
        for (MavenResolvedArtifact artifact : artifacts) {
            System.err.println(" ==> " + artifact);
        }
    }
}
