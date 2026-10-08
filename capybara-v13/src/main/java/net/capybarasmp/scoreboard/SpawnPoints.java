package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /setplayerspawn [1-5]: admins set up to 5 player spawn points. Players who respawn (without a bed or anchor)
 * and players who touch a Respawn Block land on a random one of them. No spawn points set = the world spawn.
 */
public final class SpawnPoints implements CommandExecutor, TabCompleter, Listener {

    public static final int MAX = 5;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CapybaraScoreboard plugin;
    private final File file;
    private final Location[] spots = new Location[MAX];
    private int nextSlot = 0;

    public SpawnPoints(CapybaraScoreboard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawns.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextSlot = Math.floorMod(y.getInt("next", 0), MAX);
        for (int i = 0; i < MAX; i++) {
            String b = "spawns." + (i + 1);
            if (!y.contains(b + ".world")) continue;
            World w = Bukkit.getWorld(y.getString(b + ".world", ""));
            if (w == null) continue;
            spots[i] = new Location(w, y.getDouble(b + ".x"), y.getDouble(b + ".y"), y.getDouble(b + ".z"),
                    (float) y.getDouble(b + ".yaw"), (float) y.getDouble(b + ".pitch"));
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next", nextSlot);
        for (int i = 0; i < MAX; i++) {
            Location l = spots[i];
            if (l == null || l.getWorld() == null) continue;
            String b = "spawns." + (i + 1);
            y.set(b + ".world", l.getWorld().getName());
            y.set(b + ".x", l.getX());
            y.set(b + ".y", l.getY());
            y.set(b + ".z", l.getZ());
            y.set(b + ".yaw", l.getYaw());
            y.set(b + ".pitch", l.getPitch());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spawns.yml: " + e.getMessage());
        }
    }

    /** A random player spawn point, or the main world spawn when none are set. */
    public Location randomSpawn() {
        List<Location> list = new ArrayList<>();
        for (Location l : spots) if (l != null && l.getWorld() != null) list.add(l);
        if (!list.isEmpty()) return list.get(ThreadLocalRandom.current().nextInt(list.size())).clone();
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private void send(CommandSender s, String mini) {
        s.sendMessage(MM.deserialize(mini));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CapybaraScoreboard.isAdmin(sender)) {
            send(sender, "<red>You don't have permission.");
            return true;
        }
        if (!(sender instanceof Player p)) {
            send(sender, "<red>Players only.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            int n = 0;
            for (int i = 0; i < MAX; i++) {
                Location l = spots[i];
                if (l == null) continue;
                n++;
                send(p, "<gray>#" + (i + 1) + " <white>" + l.getWorld().getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
            }
            if (n == 0) send(p, "<yellow>No player spawns set (the world spawn is used). Use /setplayerspawn.");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("remove")) {
            int n = parseSlot(args[1]);
            if (n < 0 || spots[n] == null) {
                send(p, "<red>Usage: /setplayerspawn remove <1-" + MAX + ">");
                return true;
            }
            spots[n] = null;
            save();
            send(p, "<yellow>Spawn #" + (n + 1) + " removed.");
            return true;
        }
        int slot;
        if (args.length >= 1) {
            slot = parseSlot(args[0]);
            if (slot < 0) {
                send(p, "<red>Usage: /setplayerspawn [1-" + MAX + "|list|remove <n>]");
                return true;
            }
        } else {
            // no number: use the next free slot, and when all 5 are full start again from #1
            slot = -1;
            for (int i = 0; i < MAX; i++) {
                int c = (nextSlot + i) % MAX;
                if (spots[c] == null) {
                    slot = c;
                    break;
                }
            }
            if (slot < 0) slot = nextSlot;
        }
        spots[slot] = p.getLocation().clone();
        nextSlot = (slot + 1) % MAX;
        save();
        int count = 0;
        for (Location l : spots) if (l != null) count++;
        send(p, "<green>Player spawn <white>#" + (slot + 1) + "<green> set here. <gray>(" + count + "/" + MAX + " spawns, players get a random one)");
        return true;
    }

    private static int parseSlot(String s) {
        try {
            int n = Integer.parseInt(s);
            return n >= 1 && n <= MAX ? n - 1 : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // players who die land on one of the spawn points (a bed or respawn anchor still wins)
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        if (e.isBedSpawn() || e.isAnchorSpawn()) return;
        boolean any = false;
        for (Location l : spots) if (l != null) any = true;
        if (any) e.setRespawnLocation(randomSpawn());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!CapybaraScoreboard.isAdmin(sender)) return out;
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) out.addAll(List.of("1", "2", "3", "4", "5", "list", "remove"));
        else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) out.addAll(List.of("1", "2", "3", "4", "5"));
        out.removeIf(x -> !x.startsWith(last));
        return out;
    }
}
