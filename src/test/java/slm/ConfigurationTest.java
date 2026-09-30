// SPDX-License-Identifier: GPL-2.0-or-later
package slm;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationTest {
    @TempDir Path directory;

    @Test void defaultConfigIsCreatedOnceAndExistingEditsArePreserved() throws Exception {
        SimpleLoginMessages plugin = mock(SimpleLoginMessages.class, CALLS_REAL_METHODS);
        for (String name : new String[]{"dataFolder", "configFile"}) {
            Field field = JavaPlugin.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(plugin, name.equals("dataFolder") ? directory.toFile() : directory.resolve("config.yml").toFile());
        }
        doAnswer(call -> getClass().getResourceAsStream("/config.yml")).when(plugin).getResource("config.yml");
        plugin.saveDefaultConfig();
        try (var resource = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(resource);
            assertArrayEquals(resource.readAllBytes(), Files.readAllBytes(directory.resolve("config.yml")));
        }
        String edited = "# My server settings\ncountry-fallback: 'Custom'\n";
        Files.writeString(directory.resolve("config.yml"), edited);
        plugin.saveDefaultConfig();
        assertEquals(edited, Files.readString(directory.resolve("config.yml")));
        verify(plugin, times(1)).saveResource("config.yml", false);
    }

    @Test void packagedResourcesContainUsableTemplatesAndRestrictedPermissions() throws Exception {
        YamlConfiguration config = resource("config.yml");
        assertTrue(config.getBoolean("messages.join-enabled"));
        assertTrue(config.getBoolean("messages.quit-enabled"));
        for (String key : new String[]{"join", "quit", "first-join"}) {
            String template = config.getString("messages.groups.default." + key);
            assertNotNull(template);
            assertFalse(template.isBlank());
            assertDoesNotThrow(() -> net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(template));
        }
        assertFalse(config.getStringList("stats.player-lines").isEmpty());
        assertFalse(config.getStringList("stats.server-lines").isEmpty());
        YamlConfiguration metadata = resource("plugin.yml");
        assertEquals(SimpleLoginMessages.class.getName(), metadata.getString("main"));
        assertFalse(metadata.getString("version").contains("${"));
        for (String permission : new String[]{"slm.stats", "slm.stats.others", "slm.reload", "slm.*"}) {
            // Permission names contain literal dots: inspect the section as a map.
            var permissions = metadata.getConfigurationSection("permissions");
            assertNotNull(permissions);
            var entry = (org.bukkit.configuration.ConfigurationSection) permissions.getValues(false).get(permission);
            assertNotNull(entry);
            assertEquals(Boolean.FALSE, entry.get("default"));
        }
    }

    private YamlConfiguration resource(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/" + name)) {
            assertNotNull(stream);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().pathSeparator('/');
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            // Read configuration paths normally; plugin permissions need literal keys.
            if (name.equals("config.yml")) yaml.options().pathSeparator('.');
            return yaml;
        }
    }
}
