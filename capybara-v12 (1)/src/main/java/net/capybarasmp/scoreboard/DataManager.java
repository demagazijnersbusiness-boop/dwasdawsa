package net.capybarasmp.scoreboard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DataManager {

    private final CapybaraScoreboard plugin;
    private final File file;
    private final Map<UUID, PlayerData> cache = new HashMap<>();

    public DataManager(CapybaraScoreboard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    private BigInteger startMoney() {
        try {
            BigInteger b = MoneyUtil.parse(plugin.getConfig().getString("start-money", "0"));
            return b.signum() < 0 ? BigInteger.ZERO : b;
        } catch (NumberFormatException e) {
            return BigInteger.ZERO;
        }
    }

    public void load() {
        cache.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection s = players.getConfigurationSection(key);
                if (s == null) continue;
                BigInteger money;
                try {
                    money = new BigInteger(s.getString("money", startMoney().toString()));
                } catch (NumberFormatException e) {
                    money = BigInteger.ZERO;
                }
                PlayerData d = new PlayerData(s.getInt("lives", plugin.getConfig().getInt("start-lives", 3)), money);
                d.kills = s.getInt("kills", 0);
                d.deaths = s.getInt("deaths", 0);
                d.playtimeSeconds = s.getLong("playtime", 0L);
                d.rank = s.getString("rank", "");
                d.admin = s.getBoolean("admin", false);
                d.pendingWipeInv = s.getBoolean("pending-wipe-inv", false);
                d.pendingWipeEnder = s.getBoolean("pending-wipe-ender", false);
                d.emerald = Math.max(0L, s.getLong("emerald", s.getLong("amerald", 0L)));
                d.lastSpawnerBuy = s.getLong("last-spawner-buy", 0L);
                d.skellyBought = s.getInt("skelly-bought", 0);
                d.skellyWindowStart = s.getLong("skelly-window-start", 0L);
                d.noMobSpawn = s.getBoolean("no-mob-spawn", false);
                d.nightVision = s.getBoolean("night-vision", false);
                d.boardHidden = s.getBoolean("board-hidden", false);
                d.hiddenLines.addAll(s.getStringList("hidden-lines"));
                cache.put(id, d);
            } catch (IllegalArgumentException ignored) {
                // skip invalid UUID keys
            }
        }
    }

    public PlayerData get(UUID id) {
        return cache.computeIfAbsent(id, k -> new PlayerData(plugin.getConfig().getInt("start-lives", 3), startMoney()));
    }

    public Map<UUID, PlayerData> all() {
        return Collections.unmodifiableMap(cache);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> e : cache.entrySet()) {
            String base = "players." + e.getKey();
            PlayerData d = e.getValue();
            yaml.set(base + ".kills", d.kills);
            yaml.set(base + ".deaths", d.deaths);
            yaml.set(base + ".playtime", d.playtimeSeconds);
            yaml.set(base + ".lives", d.lives);
            yaml.set(base + ".money", d.money.toString());
            if (d.rank != null && !d.rank.isEmpty()) yaml.set(base + ".rank", d.rank);
            if (d.admin) yaml.set(base + ".admin", true);
            if (d.pendingWipeInv) yaml.set(base + ".pending-wipe-inv", true);
            if (d.pendingWipeEnder) yaml.set(base + ".pending-wipe-ender", true);
            if (d.emerald > 0) yaml.set(base + ".emerald", d.emerald);
            if (d.lastSpawnerBuy > 0) yaml.set(base + ".last-spawner-buy", d.lastSpawnerBuy);
            if (d.skellyBought > 0) yaml.set(base + ".skelly-bought", d.skellyBought);
            if (d.skellyWindowStart > 0) yaml.set(base + ".skelly-window-start", d.skellyWindowStart);
            if (d.noMobSpawn) yaml.set(base + ".no-mob-spawn", true);
            if (d.nightVision) yaml.set(base + ".night-vision", true);
            if (d.boardHidden) yaml.set(base + ".board-hidden", true);
            if (!d.hiddenLines.isEmpty()) yaml.set(base + ".hidden-lines", new java.util.ArrayList<>(d.hiddenLines));
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }
}
