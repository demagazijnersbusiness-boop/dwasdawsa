package net.capybarasmp.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HomeManager {

    public static final int MAX = 5;

    public record Home(String world, double x, double y, double z, float yaw, float pitch) {
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    private final CapybaraScoreboard plugin;
    private final File file;
    private final Map<UUID, Home[]> homes = new HashMap<>();

    public HomeManager(CapybaraScoreboard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
    }

    public Home get(UUID id, int index) {
        Home[] arr = homes.get(id);
        return arr == null ? null : arr[index];
    }

    public void set(UUID id, int index, Location l) {
        Home[] arr = homes.computeIfAbsent(id, k -> new Home[MAX]);
        arr[index] = new Home(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        save();
    }

    public void delete(UUID id, int index) {
        Home[] arr = homes.get(id);
        if (arr != null) {
            arr[index] = null;
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
                Home[] arr = new Home[MAX];
                for (int i = 0; i < MAX; i++) {
                    ConfigurationSection h = s.getConfigurationSection(String.valueOf(i));
                    if (h == null) continue;
                    arr[i] = new Home(h.getString("world", "world"), h.getDouble("x"), h.getDouble("y"), h.getDouble("z"),
                            (float) h.getDouble("yaw"), (float) h.getDouble("pitch"));
                }
                homes.put(id, arr);
            } catch (IllegalArgumentException ignored) {
                // skip invalid uuid
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Home[]> e : homes.entrySet()) {
            for (int i = 0; i < MAX; i++) {
                Home h = e.getValue()[i];
                if (h == null) continue;
                String base = "homes." + e.getKey() + "." + i;
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
