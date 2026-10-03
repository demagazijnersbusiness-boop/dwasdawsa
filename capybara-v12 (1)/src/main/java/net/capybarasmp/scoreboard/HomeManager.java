package net.capybarasmp.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HomeManager {

    public static final int NORMAL_MAX = 5;
    public static final int ADMIN_MAX = 100;

    /** How many homes this player may have: 5 for normal players, 100 for admins. */
    public static int limit(Player p) {
        return CapybaraScoreboard.isAdmin(p) ? ADMIN_MAX : NORMAL_MAX;
    }

    public record Home(String world, double x, double y, double z, float yaw, float pitch) {
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    private final CapybaraScoreboard plugin;
    private final File file;
    private final Map<UUID, Map<Integer, Home>> homes = new HashMap<>();

    public HomeManager(CapybaraScoreboard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
    }

    public Home get(UUID id, int index) {
        Map<Integer, Home> m = homes.get(id);
        return m == null ? null : m.get(index);
    }

    public void set(UUID id, int index, Location l) {
        homes.computeIfAbsent(id, k -> new HashMap<>())
                .put(index, new Home(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()));
        save();
    }

    public void delete(UUID id, int index) {
        Map<Integer, Home> m = homes.get(id);
        if (m != null && m.remove(index) != null) {
            save();
        }
    }

    public void load() {
        homes.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("homes");
        if (root == null) return;
        for (String uuid : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(uuid);
                ConfigurationSection s = root.getConfigurationSection(uuid);
                if (s == null) continue;
                Map<Integer, Home> map = new HashMap<>();
                for (String key : s.getKeys(false)) {
                    int index;
                    try {
                        index = Integer.parseInt(key);
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    ConfigurationSection h = s.getConfigurationSection(key);
                    if (h == null || index < 0 || index >= ADMIN_MAX) continue;
                    map.put(index, new Home(h.getString("world", "world"), h.getDouble("x"), h.getDouble("y"), h.getDouble("z"),
                            (float) h.getDouble("yaw"), (float) h.getDouble("pitch")));
                }
                homes.put(id, map);
            } catch (IllegalArgumentException ignored) {
                // skip invalid uuid
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Map<Integer, Home>> e : homes.entrySet()) {
            for (Map.Entry<Integer, Home> he : e.getValue().entrySet()) {
                Home h = he.getValue();
                String base = "homes." + e.getKey() + "." + he.getKey();
                y.set(base + ".world", h.world());
                y.set(base + ".x", h.x());
                y.set(base + ".y", h.y());
                y.set(base + ".z", h.z());
                y.set(base + ".yaw", h.yaw());
                y.set(base + ".pitch", h.pitch());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save homes.yml: " + ex.getMessage());
        }
    }
}
