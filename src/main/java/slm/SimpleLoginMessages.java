/*
 * SimpleLoginMessages
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 * See NOTICE for author attribution and project provenance.
 */
package slm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.platform.PlayerAdapter;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

public final class SimpleLoginMessages extends JavaPlugin implements Listener {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private @Nullable YamlConfiguration activeConfig;
    private File dataFile;
    private YamlConfiguration data;
    private @Nullable LuckPerms luckPerms;
    private @Nullable PlayerAdapter<@NonNull Player> luckPermsPlayerAdapter;
    private final DateTimeFormatter lastLoginFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!loadValidatedConfig()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        loadData();
        LuckPerms provider = findLuckPerms();
        luckPerms = provider;
        if (provider == null) {
            getLogger().warning("LuckPerms was not found. Group, prefix and suffix placeholders will use fallbacks.");
        } else {
            luckPermsPlayerAdapter = provider.getPlayerAdapter(Player.class);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand command = getCommand("slm");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        Bukkit.getConsoleSender().sendMessage(Component.text(
                "[SimpleLoginMessages] SimpleLoginMessages enabled.",
                net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    @Override
    public org.bukkit.configuration.file.FileConfiguration getConfig() {
        YamlConfiguration current = activeConfig;
        return current == null ? super.getConfig() : current;
    }

    private boolean loadValidatedConfig() {
        YamlConfiguration candidate = new YamlConfiguration();
        try {
            candidate.load(new File(getDataFolder(), "config.yml"));
            ConfigValidation.validate(candidate);
            activeConfig = candidate;
            return true;
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException | IllegalArgumentException error) {
            getLogger().severe("Invalid config.yml: " + error.getMessage()
                    + " Configuration not applied; file left unchanged.");
            return false;
        }
    }

    // ServicesManager.load may return null; expose that contract to JSpecify-only tools.
    private @Nullable LuckPerms findLuckPerms() {
        return Bukkit.getServicesManager().load(LuckPerms.class);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        @NonNull Player player = Objects.requireNonNull(event.getPlayer());
        UUID uuid = player.getUniqueId();
        String base = "players." + uuid;
        boolean firstJoin = !data.contains(base + ".logins");
        int logins = data.getInt(base + ".logins", 0) + 1;
        int total = data.getInt("totallogins", 0) + 1;

        data.set(base + ".name", player.getName());
        data.set(base + ".logins", logins);
        data.set(base + ".last-login", System.currentTimeMillis());
        data.set("totallogins", total);
        updateSnapshot(player);
        saveData();

        String group = getGroup(player);
        if (getConfig().getBoolean("messages.join-enabled", true)) {
            String template = getGroupMessage(group, "join");
            event.joinMessage(render(template, player));
        }

        if (firstJoin && getConfig().getBoolean("messages.first-join-enabled", true)) {
            String template = getGroupMessage(group, "first-join");
            if (template != null && !template.isBlank()) {
                player.sendMessage(render(template, player));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        @NonNull Player player = Objects.requireNonNull(event.getPlayer());
        updateSnapshot(player);
        saveData();
        if (!getConfig().getBoolean("messages.quit-enabled", true)) {
            return;
        }
        String template = getGroupMessage(getGroup(player), "quit");
        event.quitMessage(render(template, player));
    }

    private void updateSnapshot(@NonNull Player player) {
        String base = "players." + player.getUniqueId();
        data.set(base + ".name", player.getName());
        data.set(base + ".group", getGroup(player));
        data.set(base + ".world", player.getWorld().getName());
        data.set(base + ".nickname", player.getName());
        data.set(base + ".prefix", getPrefix(player));
        data.set(base + ".suffix", getSuffix(player));
        data.set(base + ".levels", player.getLevel());
        data.set(base + ".health", player.getHealth());
        data.set(base + ".gamemode", player.getGameMode().name());
        data.set(base + ".food", player.getFoodLevel());
    }

    private String getGroupMessage(String group, String key) {
        String normalized = group == null ? "default" : group.toLowerCase(Locale.ROOT);
        String path = "messages.groups." + normalized + "." + key;
        String value = getConfig().getString(path);
        if (value == null) {
            value = getConfig().getString("messages.groups.default." + key, "");
        }
        return value;
    }

    private String getGroup(@NonNull Player player) {
        User user = getUser(player);
        if (user != null) {
            return user.getPrimaryGroup();
        }
        String storedGroup = data.getString("players." + player.getUniqueId() + ".group");
        return storedGroup == null || storedGroup.isBlank() ? "default" : storedGroup;
    }

    private @Nullable User getUser(@NonNull Player player) {
        LuckPerms provider = luckPerms;
        PlayerAdapter<@NonNull Player> adapter = luckPermsPlayerAdapter;
        if (provider == null) {
            return null;
        }
        try {
            if (adapter != null) {
                return adapter.getUser(player);
            }
        } catch (RuntimeException ignored) {
            // Fall back to the UserManager cache below.
        }
        return provider.getUserManager().getUser(player.getUniqueId());
    }

    private @Nullable CachedMetaData getMetaData(@NonNull Player player) {
        PlayerAdapter<@NonNull Player> adapter = luckPermsPlayerAdapter;
        if (adapter != null) {
            try {
                return adapter.getMetaData(player);
            } catch (RuntimeException ignored) {
                // Fall back to user cached data below.
            }
        }
        User user = getUser(player);
        return user == null ? null : user.getCachedData().getMetaData();
    }

    private String getPrefix(@NonNull Player player) {
        CachedMetaData metaData = getMetaData(player);
        if (metaData == null) {
            return data.getString("players." + player.getUniqueId() + ".prefix", "");
        }
        String value = metaData.getPrefix();
        return value == null ? "" : value;
    }

    private String getSuffix(@NonNull Player player) {
        CachedMetaData metaData = getMetaData(player);
        if (metaData == null) {
            return data.getString("players." + player.getUniqueId() + ".suffix", "");
        }
        String value = metaData.getSuffix();
        return value == null ? "" : value;
    }

    private @NonNull Component render(@Nullable String template, @NonNull Player player) {
        return miniMessage.deserialize(replacePlaceholders(template == null ? "" : template, player), miniMessage.tags());
    }

    private @NonNull Component renderStored(@Nullable String template, String uuid) {
        return miniMessage.deserialize(replaceStoredPlaceholders(template == null ? "" : template, uuid), miniMessage.tags());
    }

    private @NonNull String replacePlaceholders(@NonNull String input, @NonNull Player player) {
        String uuidPath = "players." + player.getUniqueId();
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        String playerList = online.stream().map(onlinePlayer -> Objects.requireNonNull(onlinePlayer).getName()).collect(Collectors.joining(", "));

        return Objects.requireNonNull(input
            .replace("%playername%", safe(player.getName()))
            .replace("%nickname%", safe(player.getName()))
            .replace("%group%", safe(getGroup(player)))
            .replace("%world%", safe(player.getWorld().getName()))
            .replace("%country%", safe(getConfig().getString("country-fallback", "Unknown")))
            .replace("%playerlist%", safe(playerList))
            .replace("%logins%", Integer.toString(data.getInt(uuidPath + ".logins", 0)))
            .replace("%totallogins%", Integer.toString(data.getInt("totallogins", 0)))
            .replace("%uniqueplayers%", Integer.toString(getUniquePlayerCount()))
            .replace("%onlineplayers%", Integer.toString(online.size()))
            .replace("%status%", "Online")
            .replace("%lastlogin%", formatLastLogin(getStoredLong(uuidPath + ".last-login")))
            .replace("%prefix%", safe(getPrefix(player)))
            .replace("%suffix%", safe(getSuffix(player)))
            .replace("%slots%", Integer.toString(Bukkit.getMaxPlayers()))
            .replace("%levels%", Integer.toString(player.getLevel()))
            .replace("%health%", trimNumber(player.getHealth()))
            .replace("%gamemode%", player.getGameMode().name())
            .replace("%food%", Integer.toString(player.getFoodLevel())));
    }

    private @NonNull String replaceStoredPlaceholders(@NonNull String input, String uuid) {
        String base = "players." + uuid;
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        String playerList = online.stream().map(onlinePlayer -> Objects.requireNonNull(onlinePlayer).getName()).collect(Collectors.joining(", "));

        return Objects.requireNonNull(input
            .replace("%playername%", safe(data.getString(base + ".name", "Unknown")))
            .replace("%nickname%", safe(data.getString(base + ".nickname", data.getString(base + ".name", "Unknown"))))
            .replace("%group%", safe(data.getString(base + ".group", "Unknown")))
            .replace("%world%", safe(data.getString(base + ".world", "Unknown")))
            .replace("%country%", safe(getConfig().getString("country-fallback", "Unknown")))
            .replace("%playerlist%", safe(playerList))
            .replace("%logins%", Integer.toString(data.getInt(base + ".logins", 0)))
            .replace("%totallogins%", Integer.toString(data.getInt("totallogins", 0)))
            .replace("%uniqueplayers%", Integer.toString(getUniquePlayerCount()))
            .replace("%onlineplayers%", Integer.toString(online.size()))
            .replace("%status%", isOnlineUuid(uuid) ? "Online" : "Offline")
            .replace("%lastlogin%", formatLastLogin(getStoredLong(base + ".last-login")))
            .replace("%prefix%", safe(data.getString(base + ".prefix", "")))
            .replace("%suffix%", safe(data.getString(base + ".suffix", "")))
            .replace("%slots%", Integer.toString(Bukkit.getMaxPlayers()))
            .replace("%levels%", stored(base + ".levels"))
            .replace("%health%", storedNumber(base + ".health"))
            .replace("%gamemode%", safe(data.getString(base + ".gamemode", "Unknown")))
            .replace("%food%", stored(base + ".food")));
    }

    private int getUniquePlayerCount() {
        ConfigurationSection players = data.getConfigurationSection("players");
        return players == null ? 0 : players.getKeys(false).size();
    }

    private String formatLastLogin(long timestamp) {
        if (timestamp <= 0L) {
            return "Unknown";
        }
        return lastLoginFormatter.format(Instant.ofEpochMilli(timestamp));
    }

    private long getStoredLong(@NonNull String path) {
        Object value = data.get(path);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private String stored(@NonNull String path) {
        return data.contains(path) ? String.valueOf(data.get(path)) : "Unknown";
    }

    private String storedNumber(@NonNull String path) {
        return data.contains(path) ? trimNumber(data.getDouble(path)) : "Unknown";
    }

    private @Nullable String findStoredUuidByName(String name) {
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return null;
        for (String uuid : players.getKeys(false)) {
            String storedName = data.getString("players." + uuid + ".name");
            if (storedName != null && storedName.equalsIgnoreCase(name)) {
                return uuid;
            }
        }
        return null;
    }

    private List<String> getPlayerStatLines() {
        List<String> configured = getConfig().getStringList("stats.player-lines");
        if (!configured.isEmpty()) return configured;
        return List.of(
            "<gray>Player:</gray> <white>%playername%</white>",
            "<gray>Status:</gray> <white>%status%</white>",
            "<gray>Last login:</gray> <white>%lastlogin%</white>",
            "<gray>Group:</gray> <white>%group%</white>",
            "<gray>World:</gray> <white>%world%</white>",
            "<gray>Logins:</gray> <white>%logins%</white>",
            "<gray>Level:</gray> <white>%levels%</white>",
            "<gray>Health:</gray> <white>%health%</white>",
            "<gray>Gamemode:</gray> <white>%gamemode%</white>",
            "<gray>Food:</gray> <white>%food%</white>"
        );
    }

    private List<String> getServerStatLines() {
        List<String> configured = getConfig().getStringList("stats.server-lines");
        if (!configured.isEmpty()) return configured;
        return List.of(
            "<gray>Total logins:</gray> <white>%totallogins%</white>",
            "<gray>Unique players:</gray> <white>%uniqueplayers%</white>",
            "<gray>Online:</gray> <white>%onlineplayers%/%slots%</white>"
        );
    }

    private void sendStats(CommandSender sender, @NonNull Player target) {
        sender.sendMessage(render(getConfig().getString("stats.player-header", "<gold>Player statistics</gold>"), target));
        for (String line : getPlayerStatLines()) {
            sender.sendMessage(render(line, target));
        }
        sender.sendMessage("");
        sender.sendMessage(render(getConfig().getString("stats.server-header", "<gold>Server statistics</gold>"), target));
        for (String line : getServerStatLines()) {
            sender.sendMessage(render(line, target));
        }
    }

    private void sendStoredStats(CommandSender sender, String uuid) {
        sender.sendMessage(renderStored(getConfig().getString("stats.player-header", "<gold>Player statistics</gold>"), uuid));
        for (String line : getPlayerStatLines()) {
            sender.sendMessage(renderStored(line, uuid));
        }
        sender.sendMessage("");
        sender.sendMessage(renderStored(getConfig().getString("stats.server-header", "<gold>Server statistics</gold>"), uuid));
        for (String line : getServerStatLines()) {
            sender.sendMessage(renderStored(line, uuid));
        }
    }

    private boolean isOnlineUuid(String uuid) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Objects.requireNonNull(player).getUniqueId().toString().equalsIgnoreCase(uuid)) {
                return true;
            }
        }
        return false;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(miniMessage.deserialize("<gold>SimpleLoginMessages</gold>", miniMessage.tags()));
        sender.sendMessage("/slm help - Show this help.");
        if (sender.hasPermission("slm.stats")) {
            sender.sendMessage("/slm stats - Show your own statistics.");
        }
        if (sender.hasPermission("slm.stats.others")) {
            sender.sendMessage("/slm stats <player> - Show another player's statistics.");
        }
        if (sender.hasPermission("slm.reload")) {
            sender.sendMessage("/slm reload - Reload config and data.");
        }
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("stats")) {
            if (args.length == 1) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Console usage: /slm stats <player>");
                    return true;
                }
                if (!sender.hasPermission("slm.stats")) {
                    sender.sendMessage("You do not have permission to view your statistics.");
                    return true;
                }
                sendStats(sender, player);
                return true;
            }

            if (!sender.hasPermission("slm.stats.others")) {
                sender.sendMessage("You do not have permission to view another player's statistics.");
                return true;
            }

            @NonNull String targetName = Objects.requireNonNull(args[1]);
            Player onlineTarget = Bukkit.getPlayerExact(targetName);
            if (onlineTarget != null) {
                sendStats(sender, onlineTarget);
                return true;
            }

            String storedUuid = findStoredUuidByName(targetName);
            if (storedUuid == null) {
                sender.sendMessage("No stored statistics found for player: " + targetName);
                return true;
            }
            sendStoredStats(sender, storedUuid);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("slm.reload")) {
                sender.sendMessage("You do not have permission to reload this plugin.");
                return true;
            }
            if (!loadValidatedConfig()) {
                sender.sendMessage("SimpleLoginMessages reload failed. Previous configuration retained; see server log.");
                return true;
            }
            loadData();
            sender.sendMessage("SimpleLoginMessages reloaded.");
            return true;
        }

        sendHelp(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String @NonNull [] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("help");
            if (sender.hasPermission("slm.stats")) {
                suggestions.add("stats");
            }
            if (sender.hasPermission("slm.reload")) {
                suggestions.add("reload");
            }
            return filterSuggestions(suggestions, args[0]);
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("stats")
                && sender.hasPermission("slm.stats.others")) {
            Set<String> names = new LinkedHashSet<>();

            ConfigurationSection players = data.getConfigurationSection("players");
            if (players != null) {
                for (String uuid : players.getKeys(false)) {
                    String name = data.getString("players." + uuid + ".name");
                    if (name != null && !name.isBlank()) {
                        names.add(name);
                    }
                }
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(Objects.requireNonNull(player).getName());
            }

            List<String> suggestions = new ArrayList<>(names);
            suggestions.sort(String.CASE_INSENSITIVE_ORDER);
            return filterSuggestions(suggestions, args[1]);
        }

        return List.of();
    }

    private List<String> filterSuggestions(List<String> values, @Nullable String input) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
            .collect(Collectors.toList());
    }

    private @NonNull String safe(@Nullable String value) {
        return value == null ? "" : value;
    }

    private String trimNumber(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void loadData() {
        File file = new File(getDataFolder(), "data.yml");
        dataFile = file;
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create plugin data directory.");
        }
        data = YamlConfiguration.loadConfiguration(file);
        if (!data.contains("totallogins")) data.set("totallogins", 0);
        if (data.contains("uniqueplayers")) {
            data.set("uniqueplayers", null);
            saveData();
        }
    }

    private void saveData() {
        File file = dataFile;
        if (data == null || file == null) return;
        try {
            data.save(file);
        } catch (IOException e) {
            getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }
}
