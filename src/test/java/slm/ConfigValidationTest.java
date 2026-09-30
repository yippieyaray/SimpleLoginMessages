// SPDX-License-Identifier: GPL-2.0-or-later
package slm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static slm.TestMocks.mock;
import static java.util.Objects.requireNonNull;

class ConfigValidationTest {
    @TempDir Path folder;

    private YamlConfiguration defaults() throws Exception {
        try (var in = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(in);
            YamlConfiguration result = new YamlConfiguration();
            result.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            return result;
        }
    }

    @Test void defaultsAndPartialGroupOverridesAreValid() throws Exception {
        YamlConfiguration config = defaults();
        config.set("messages.groups.vip.join", "<gold>VIP</gold>");
        config.set("messages.groups.vip.first-join", "");
        assertDoesNotThrow(() -> ConfigValidation.validate(config));
    }

    @ParameterizedTest
    @ValueSource(strings = {"stats.header", "stats.lines"})
    void legacyKeysAreRejectedEvenWithNewKeysPresent(String key) throws Exception {
        YamlConfiguration config = defaults();
        config.set(key, key.endsWith("lines") ? List.of("Old text") : "Old heading");
        var error = requireNonNull(assertThrows(IllegalArgumentException.class, () -> ConfigValidation.validate(config)));
        assertTrue(error.getMessage().contains("No automatic migration"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"messages.join-enabled", "messages.groups.default", "stats.player-lines", "stats.server-header", "country-fallback"})
    void wrongTypesAreRejected(@org.jspecify.annotations.NonNull String path) throws Exception {
        YamlConfiguration config = defaults();
        config.set(path, 42);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidation.validate(config));
    }

    @Test void invalidListElementsMissingKeysAndTyposAreRejected() throws Exception {
        YamlConfiguration config = defaults();
        config.set("stats.player-lines", List.of("text", 42));
        assertThrows(IllegalArgumentException.class, () -> ConfigValidation.validate(config));
        final var missing = defaults();
        missing.set("stats.server-lines", null);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidation.validate(missing));
        final var typo = defaults();
        typo.set("messages.jion-enabled", true);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidation.validate(typo));
    }

    @Test void malformedMiniMessageIsRejected() throws Exception {
        YamlConfiguration config = defaults();
        config.set("messages.groups.default.join", "<green>unclosed");
        assertThrows(IllegalArgumentException.class, () -> ConfigValidation.validate(config));
    }

    private SimpleLoginMessages plugin(Logger logger) {
        SimpleLoginMessages plugin = mock(SimpleLoginMessages.class, CALLS_REAL_METHODS);
        doReturn(folder.toFile()).when(plugin).getDataFolder();
        doReturn(logger).when(plugin).getLogger();
        doNothing().when(plugin).saveDefaultConfig();
        doReturn(null).when(plugin).getCommand("slm");
        return plugin;
    }

    @Test void startupLogsGreenSuccessAndFailedReloadRetainsPreviousConfigAndData() throws Exception {
        Path file = folder.resolve("config.yml");
        defaults().save(requireNonNull(file.toFile()));
        Logger logger = mock(Logger.class);
        SimpleLoginMessages plugin = plugin(logger);
        PluginManager manager = mock(PluginManager.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            bukkit.when(Bukkit::getServicesManager).thenReturn(mock(ServicesManager.class));
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            plugin.onEnable();
            verify(console).sendMessage(Component.text("[SimpleLoginMessages] SimpleLoginMessages enabled.", NamedTextColor.GREEN));
            var previous = plugin.getConfig();
            var field = SimpleLoginMessages.class.getDeclaredField("data");
            field.setAccessible(true);
            Object previousData = field.get(plugin);
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("slm.reload")).thenReturn(true);
            for (String invalid : List.of("stats: [broken", "stats:\n  header: old\n  lines: [text]\n")) {
                Files.writeString(file, invalid);
                plugin.onCommand(sender, mock(Command.class), "slm", new String[]{"reload"});
                assertSame(previous, plugin.getConfig());
                assertSame(previousData, field.get(plugin));
                assertEquals(invalid, Files.readString(file));
            }
            verify(logger, times(2)).severe(contains("Invalid config.yml"));
            verify(sender, never()).sendMessage("SimpleLoginMessages reloaded.");
            YamlConfiguration changed = defaults();
            changed.set("country-fallback", "New value");
            changed.save(requireNonNull(file.toFile()));
            plugin.onCommand(sender, mock(Command.class), "slm", new String[]{"reload"});
            assertEquals("New value", plugin.getConfig().getString("country-fallback"));
            verify(sender).sendMessage("SimpleLoginMessages reloaded.");
        }
    }

    @Test void invalidStartupDisablesPluginWithoutRegisteringOrLoggingSuccess() throws Exception {
        Files.writeString(folder.resolve("config.yml"), "messages: [broken");
        Logger logger = mock(Logger.class);
        SimpleLoginMessages plugin = plugin(logger);
        PluginManager manager = mock(PluginManager.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            plugin.onEnable();
            verify(manager).disablePlugin(plugin);
            verifyNoMoreInteractions(manager);
            verifyNoInteractions(console);
            verify(logger).severe(contains("Invalid config.yml"));
        }
    }
}
