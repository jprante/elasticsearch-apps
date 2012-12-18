package org.elasticsearch.apps;

import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;

import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.internal.InternalSettingsPerparer;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.testng.annotations.Test;

public class AppResolverServiceTests {

    private final ESLogger logger = ESLoggerFactory.getLogger(AppResolverServiceTests.class.getName());
    
    @Test
    public void testMavenArtifactApp() {
        logger.info("testMavenArtifactApp");
        Settings pSettings = settingsBuilder()
                .put("apps.settings", AppService.DEFAULT_SETTINGS)
                .put("apps.dependencies.river-rabbitmq.dependency", "org.elasticsearch:elasticsearch-river-rabbitmq:0.17.10")
                .build();
        Tuple<Settings, Environment> tuple = InternalSettingsPerparer.prepareSettings(pSettings, false);
        Settings settings = settingsBuilder().put(tuple.v1()).build();
        Environment environment = tuple.v2();
        AppService service = new AppService(settings, environment, true);
    }

    @Test
    public void testSiteApp() {
        logger.info("testSiteApp");
        Settings pSettings = settingsBuilder()
                .put("apps.settings", AppService.DEFAULT_SETTINGS)
                .put("apps.sites.elasticsearch-head.url", "https://github.com/mobz/elasticsearch-head/archive/master.zip")
                .build();
        Tuple<Settings, Environment> tuple = InternalSettingsPerparer.prepareSettings(pSettings, false);
        Settings settings = settingsBuilder().put(tuple.v1()).build();
        Environment environment = tuple.v2();
        AppService service = new AppService(settings, environment, true);
    }

    @Test
    public void testPluginApp() {
        logger.info("testPluginApp");
        Settings pSettings = settingsBuilder()
                .put("apps.settings", AppService.DEFAULT_SETTINGS)
                .put("apps.plugins.elasticsearch-analysis-phonetic.url", "https://github.com/downloads/elasticsearch/elasticsearch-analysis-phonetic/elasticsearch-analysis-phonetic-1.2.0.zip")
                .build();
        Tuple<Settings, Environment> tuple = InternalSettingsPerparer.prepareSettings(pSettings, false);
        Settings settings = settingsBuilder().put(tuple.v1()).build();
        Environment environment = tuple.v2();
        AppService service = new AppService(settings, environment, true);
    }
    
    
    @Test
    public void testResolveArtifact() {
        logger.info("testResolveArtifact");
        Settings pSettings = settingsBuilder()
                .put("apps.settings", AppService.DEFAULT_SETTINGS)
                .build();
        Tuple<Settings, Environment> tuple = InternalSettingsPerparer.prepareSettings(pSettings, false);
        Settings settings = settingsBuilder().put(tuple.v1()).build();
        Environment environment = tuple.v2();
        AppService service = new AppService(settings, environment, true);
        MavenResolvedArtifact[] artifacts = service.resolveArtifact( 
                "org.elasticsearch:elasticsearch-river-rabbitmq:0.17.10", 
                "runtime",
                AppService.DEFAULT_EXCLUDE);
    }
}
