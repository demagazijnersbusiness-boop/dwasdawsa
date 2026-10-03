package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Admin tools: /spawnfakestash (Skelly Spawner in a hole where you look), /goto <player> (teleport to a player),
 * /invis (be invisible for normal players).
 */
public final class AdminTools implements CommandExecutor, TabCompleter, Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CapybaraScoreboard plugin;
    private final SpawnerManager spawners;
    private final Set<UUID> invisible = new HashSet<>();

    public AdminTools(CapybaraScoreboard plugin, SpawnerManager spawners) {
        this.plugin = plugin;
        this.spawners = spawners;
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
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "spawnfakestash" -> fakeStash(p);
            case "goto" -> {
                if (args.length < 1) {
                    send(p, "<red>Usage: /goto <player>");
                } else {
                    Player t = Bukkit.getPlayerExact(args[0]);
                    if (t == null) send(p, "<red>" + args[0] + " is not online.");
                    else if (t.equals(p)) send(p, "<red>That's you.");
                    else {
                        p.teleport(t.getLocation());
                        send(p, "<green>Teleported to <white>" + t.getName() + "<green>.");
                    }
                }
            }
            case "invis" -> toggleInvisible(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ /spawnfakestash

    private void fakeStash(Player p) {
        RayTraceResult rt = p.rayTraceBlocks(80);
        if (rt == null || rt.getHitBlock() == null) {
            send(p, "<red>Look at a block (max 80 blocks away).");
            return;
        }
        Block top = rt.getHitBlock();
        World w = top.getWorld();
        int x = top.getX();
        int y = top.getY();
        int z = top.getZ();
        int depth = 3; // the hole is 3 blocks deep, the spawner sits on the bottom
        if (y - depth < w.getMinHeight() + 1) {
            send(p, "<red>That's too deep down, look at a higher block.");
            return;
        }
        Block spawnerBlock = w.getBlockAt(x, y - depth, z);
        if (spawnerBlock.getType().getHardness() < 0 || spawnerBlock.getState() instanceof Container) {
            send(p, "<red>You can't dig here (unbreakable block or a chest below).");
            return;
        }

        for (int dy = 0; dy < depth; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = w.getBlockAt(x + dx, y - dy, z + dz);
                    Material t = b.getType();
                    if (t.getHardness() < 0 || b.getState() instanceof Container || spawners.isSkelly(b)) continue;
                    b.setType(Material.AIR);
                }
            }
        }
        spawnerBlock.setType(Material.SPAWNER);
        spawners.registerDecoy(spawnerBlock); // no owner, no loot, no money
        send(p, "<green>Fake stash placed at <white>" + x + ", " + (y - depth) + ", " + z
                + "<green>: a Skelly Spawner at the bottom of a hole. <gray>(no owner, makes no money)");
    }

    // ------------------------------------------------------------ /invis

    private boolean isAdminPlayer(Player p) {
        return CapybaraScoreboard.isAdmin(p);
    }

    private void toggleInvisible(Player p) {
        if (invisible.remove(p.getUniqueId())) {
            for (Player other : Bukkit.getOnlinePlayers()) other.showPlayer(plugin, p);
            send(p, "<yellow>You are visible again.");
        } else {
            invisible.add(p.getUniqueId());
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(p) && !isAdminPlayer(other)) other.hidePlayer(plugin, p);
            }
            send(p, "<green>You are now invisible for normal players. <gray>(admins can still see you)");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player joined = e.getPlayer();
        if (isAdminPlayer(joined)) return;
        for (UUID id : invisible) {
            Player v = Bukkit.getPlayer(id);
            if (v != null) joined.hidePlayer(plugin, v);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        invisible.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("goto") && args.length == 1 && CapybaraScoreboard.isAdmin(sender)) {
            String last = args[0].toLowerCase(Locale.ROOT);
            Bukkit.getOnlinePlayers().forEach(pl -> {
                if (pl.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(pl.getName());
            });
        }
        return out;
    }
}
