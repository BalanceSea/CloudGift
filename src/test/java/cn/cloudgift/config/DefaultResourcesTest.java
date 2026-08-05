package cn.cloudgift.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DefaultResourcesTest {

    private static final List<String> YAML_RESOURCES = List.of(
            "config.yml",
            "messages.yml",
            "items.yml",
            "gifts.yml",
            "gifts/monthly.yml",
            "gifts/novice.yml",
            "plugin.yml");

    @Test
    void bundledYamlResourcesAreValid() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        for (String resource : YAML_RESOURCES) {
            try (InputStream input = classLoader.getResourceAsStream(resource)) {
                assertNotNull(input, () -> "Missing YAML resource: " + resource);
                try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    YamlConfiguration yaml = new YamlConfiguration();
                    yaml.load(reader);
                }
            }
        }
    }
}
