// SPDX-License-Identifier: GPL-2.0-or-later
package slm;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Validates explicit file contents before they become the active configuration. */
final class ConfigValidation {
    private ConfigValidation() { }

    static void validate(YamlConfiguration config) {
        if (config.contains("stats.header") || config.contains("stats.lines")) {
            throw new IllegalArgumentException("Legacy stats.header/stats.lines detected. Use stats.player-header, "
                    + "stats.player-lines, stats.server-header and stats.server-lines. No automatic migration is available; back up and update config.yml manually.");
        }
        keys(config, Set.of("messages", "stats", "country-fallback"));
        ConfigurationSection messages = section(config, "messages");
        keys(messages, Set.of("join-enabled", "quit-enabled", "first-join-enabled", "groups"));
        for (String key : List.of("join-enabled", "quit-enabled", "first-join-enabled")) {
            require(messages.get(key) instanceof Boolean, "messages." + key + " must be a YAML boolean (true/false).");
        }
        ConfigurationSection groups = section(messages, "groups");
        require(groups.isConfigurationSection("default"), "messages.groups.default is required.");
        for (String group : groups.getKeys(false)) {
            require(group.equals(group.toLowerCase(Locale.ROOT)), "messages.groups." + group + " must use a lowercase group name.");
            ConfigurationSection entry = section(groups, group);
            keys(entry, Set.of("join", "quit", "first-join"));
            for (String key : List.of("join", "quit", "first-join")) {
                if (group.equals("default") || entry.contains(key)) {
                    template(entry.get(key), "messages.groups." + group + "." + key);
                }
            }
        }
        ConfigurationSection stats = section(config, "stats");
        keys(stats, Set.of("player-header", "player-lines", "server-header", "server-lines"));
        for (String kind : List.of("player", "server")) {
            template(stats.get(kind + "-header"), "stats." + kind + "-header");
            Object value = stats.get(kind + "-lines");
            require(value instanceof List<?>, "stats." + kind + "-lines must be a list of strings.");
            List<?> lines = (List<?>) value;
            for (int i = 0; i < lines.size(); i++) template(lines.get(i), "stats." + kind + "-lines[" + i + "]");
        }
        require(config.get("country-fallback") instanceof String, "country-fallback must be a string.");
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key) {
        ConfigurationSection result = parent.getConfigurationSection(key);
        require(result != null, parent.getCurrentPath() + "." + key + " must be a section.");
        return java.util.Objects.requireNonNull(result);
    }

    private static void keys(ConfigurationSection section, Set<String> allowed) {
        for (String key : section.getKeys(false)) {
            require(allowed.contains(key), "Unknown configuration key: " + section.getCurrentPath() + "." + key);
        }
    }

    private static void template(Object value, String path) {
        require(value instanceof String, path + " must be a string.");
        try {
            MiniMessage.builder().strict(true).build().deserialize((String) value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(path + ": invalid MiniMessage: " + error.getMessage(), error);
        }
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
