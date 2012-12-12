package org.elasticsearch.apps;

import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;

import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.internal.InternalSettingsPerparer;
import org.testng.annotations.Test;

public class AppResolverServiceTests {

    @Test
    public void testAppResolver() {
        Settings pSettings = settingsBuilder()
                .put("apps.settings", "config/settings-es.xml")
                .putArray("apps.dependencies", 
                     "org.elasticsearch:elasticsearch-river-rabbitmq:0.17.10" 
                )
                .build();
        Tuple<Settings, Environment> tuple = InternalSettingsPerparer.prepareSettings(pSettings, false);
        Settings settings = settingsBuilder().put(tuple.v1()).build();
        Environment environment = tuple.v2();
        AppResolverService service = new AppResolverService(settings,environment);
    }
}
