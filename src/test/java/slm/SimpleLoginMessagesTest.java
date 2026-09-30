// SPDX-License-Identifier: GPL-2.0-or-later
package slm;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimpleLoginMessagesTest {
    @TempDir Path directory;
    private SimpleLoginMessages plugin;
    private YamlConfiguration config;
    private YamlConfiguration data;
    private Player player;
    private MockedStatic<Bukkit> bukkit;
    private final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private String base() { return "players." + uuid; }

    @BeforeEach void setup() throws Exception {
        // A real JavaPlugin constructor requires Paper's class loader. Only the
        // framework accessors are mocked; handlers, rendering and YAML I/O run for real.
        plugin = mock(SimpleLoginMessages.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        config = new YamlConfiguration();
        data = new YamlConfiguration();
        doReturn(config).when(plugin).getConfig();
        doReturn(directory.toFile()).when(plugin).getDataFolder();
        doReturn(Logger.getLogger("slm-test")).when(plugin).getLogger();
        set("data", data);
        set("dataFile", directory.resolve("data.yml").toFile());
        set("miniMessage", MiniMessage.miniMessage());
        set("lastLoginFormatter", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.of("UTC")));
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alex");
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getHealth()).thenReturn(20.0);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        bukkit.when(Bukkit::getMaxPlayers).thenReturn(20);
        config.set("messages.groups.default.join", "<green>%playername%</green> %logins%/%totallogins%/%uniqueplayers%");
        config.set("messages.groups.default.quit", "Bye %playername%");
        config.set("messages.groups.default.first-join", "Welcome %playername%");
    }

    @AfterEach void close() { if (bukkit != null) bukkit.close(); }

    private void set(String name, Object value) throws Exception {
        Field field = SimpleLoginMessages.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    private PlayerJoinEvent join() {
        PlayerJoinEvent event = new PlayerJoinEvent(player, Component.text("original"));
        plugin.onJoin(event);
        return event;
    }

    @Test void joinsPersistCountersAndWelcomeOnlyOnce() {
        assertEquals(MiniMessage.miniMessage().deserialize("<green>Alex</green> 1/1/1"), join().joinMessage());
        assertEquals(MiniMessage.miniMessage().deserialize("<green>Alex</green> 2/2/1"), join().joinMessage());
        verify(player, times(1)).sendMessage(Component.text("Welcome Alex"));
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(directory.resolve("data.yml").toFile());
        assertEquals(2, saved.getInt(base() + ".logins"));
        assertEquals(2, saved.getInt("totallogins"));
        assertTrue(saved.getLong(base() + ".last-login") > 0);
    }

    @Test void disabledReplacementPreservesOtherMessagesButStillCountsLogins() {
        config.set("messages.join-enabled", false);
        config.set("messages.quit-enabled", false);
        config.set("messages.first-join-enabled", false);
        assertEquals(Component.text("original"), join().joinMessage());
        PlayerQuitEvent quit = new PlayerQuitEvent(player, Component.text("other plugin"), PlayerQuitEvent.QuitReason.DISCONNECTED);
        plugin.onQuit(quit);
        assertEquals(Component.text("other plugin"), quit.quitMessage());
        assertEquals(1, data.getInt("totallogins"));
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test void luckPermsPrimaryGroupIsCaseNormalizedAndMissingKeysFallBack() throws Exception {
        LuckPerms luckPerms = mock(LuckPerms.class);
        UserManager manager = mock(UserManager.class);
        User user = mock(User.class, RETURNS_DEEP_STUBS);
        when(luckPerms.getUserManager()).thenReturn(manager);
        when(manager.getUser(uuid)).thenReturn(user);
        when(user.getPrimaryGroup()).thenReturn("VIP");
        set("luckPerms", luckPerms);
        config.set("messages.groups.vip.join", "VIP %playername%");
        config.set("messages.groups.vip.first-join", "");
        assertEquals(Component.text("VIP Alex"), join().joinMessage());
        verify(player, never()).sendMessage(any(Component.class));
        PlayerQuitEvent quit = new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED);
        plugin.onQuit(quit);
        assertEquals(Component.text("Bye Alex"), quit.quitMessage());
        assertEquals("VIP", data.getString(base() + ".group"));
    }

    @Test void quitSavesSnapshotWithoutIncreasingCounters() {
        join();
        when(player.getLevel()).thenReturn(42);
        plugin.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(directory.resolve("data.yml").toFile());
        assertEquals(42, saved.getInt(base() + ".levels"));
        assertEquals(1, saved.getInt("totallogins"));
    }

    @Test void ownStatsRequirePermission() {
        plugin.onCommand(player, mock(Command.class), "slm", new String[]{"stats"});
        verify(player).sendMessage("You do not have permission to view your statistics.");
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test void otherStatsRequireSeparatePermission() {
        when(player.hasPermission("slm.stats")).thenReturn(true);
        plugin.onCommand(player, mock(Command.class), "slm", new String[]{"stats", "Other"});
        verify(player).sendMessage("You do not have permission to view another player's statistics.");
        bukkit.verify(() -> Bukkit.getPlayerExact("Other"), never());
    }

    @Test void offlineStatsUseStoredNameAndDynamicStatusAndUniqueCount() {
        data.set(base() + ".name", "Alex");
        data.set(base() + ".logins", 7);
        data.set(base() + ".last-login", 0);
        data.set("uniqueplayers", 999);
        config.set("stats.player-lines", List.of("%playername% %status% %logins% %lastlogin%"));
        config.set("stats.server-lines", List.of("Unique %uniqueplayers%"));
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("slm.stats.others")).thenReturn(true);
        plugin.onCommand(sender, mock(Command.class), "slm", new String[]{"stats", "aLeX"});
        verify(sender).sendMessage(Component.text("Alex Offline 7 Unknown"));
        verify(sender).sendMessage(Component.text("Unique 1"));
    }

    @Test void consoleNeedsPlayerName() {
        CommandSender sender = mock(CommandSender.class);
        plugin.onCommand(sender, mock(Command.class), "slm", new String[]{"stats"});
        verify(sender).sendMessage("Console usage: /slm stats <player>");
    }

    @Test void deniedReloadDoesNotTouchConfigOrData() {
        plugin.onCommand(player, mock(Command.class), "slm", new String[]{"reload"});
        verify(plugin, never()).reloadConfig();
        verify(player).sendMessage("You do not have permission to reload this plugin.");
    }

    @Test void allowedReloadReadsDiskAndRemovesLegacyUniqueCounter() throws Exception {
        YamlConfiguration disk = new YamlConfiguration();
        disk.set("totallogins", 99);
        disk.set("uniqueplayers", 500);
        disk.save(directory.resolve("data.yml").toFile());
        when(player.hasPermission("slm.reload")).thenReturn(true);
        try (var input = getClass().getResourceAsStream("/config.yml")) {
            java.nio.file.Files.copy(java.util.Objects.requireNonNull(input), directory.resolve("config.yml"));
        }
        plugin.onCommand(player, mock(Command.class), "slm", new String[]{"reload"});

        join();
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(directory.resolve("data.yml").toFile());
        assertEquals(100, saved.getInt("totallogins"));
        assertFalse(saved.contains("uniqueplayers"));
    }

    @Test void tabCompletionFiltersPermissionsAndDeduplicatesKnownNames() {
        Command command = mock(Command.class);
        assertEquals(List.of("help"), plugin.onTabComplete(player, command, "slm", new String[]{""}));
        assertEquals(List.of(), plugin.onTabComplete(player, command, "slm", new String[]{"stats", ""}));
        when(player.hasPermission("slm.stats.others")).thenReturn(true);
        data.set(base() + ".name", "Alex");
        data.set("players.second.name", "Alice");
        data.set("players.third.name", "Bob");
        assertEquals(List.of("Alex", "Alice"), plugin.onTabComplete(player, command, "slm", new String[]{"stats", "AL"}));
    }
}
