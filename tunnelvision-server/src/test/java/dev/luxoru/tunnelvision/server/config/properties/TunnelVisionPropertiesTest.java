package dev.luxoru.tunnelvision.server.config.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TunnelVisionPropertiesTest {
    // The records have no defaults of their own, so application.yaml has to set every value

    @Test
    void applicationYamlSetsEverything() throws Exception {
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"));
        TunnelVisionProperties properties = new Binder(ConfigurationPropertySources.from(yaml))
            .bind("tunnelvision", TunnelVisionProperties.class).get();

        assertNoNulls(properties, "tunnelvision");
        assertThat(properties.allowlist()).isEmpty();
        assertThat(properties.sources().custom()).isEmpty();
    }

    private static void assertNoNulls(Record record, String path) throws Exception {
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            Object value = component.getAccessor().invoke(record);
            String name = path + "." + component.getName();
            assertThat(value).as(name).isNotNull();
            if (value instanceof Record nested) {
                assertNoNulls(nested, name);
            }
        }
    }
}
