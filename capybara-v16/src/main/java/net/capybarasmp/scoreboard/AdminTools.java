package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import java.util.HashMap;
import java.util.Map;
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
 * /invis (be invisible for normal players), /fakeleft (pretend to leave, keep playing hidden),
 * /fly, /godmode, /spectator, /spectate <player>, /gamemode <mode> [player], /freebuild
 * (the toggles also work on another player: /fly Steve).
 */
public final class AdminTools implements CommandExecutor, TabCompleter, Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CapybaraScoreboard plugin;
    private final SpawnerManager spawners;
    private final Set<UUID> invisible = new HashSet<>();
    private final Set<UUID> fakeLeft = new HashSet<>();
    private final Set<UUID> god = new HashSet<>();
    private final Set<UUID> freeBuild = new HashSet<>();
    private final Map<UUID, GameMode> previousMode = new HashMap<>();

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
            case "fakeleft", "fly", "godmode", "spectator", "invis", "freebuild" -> {
                // every one of these can also be used on another player: /fly Steve
                Player t = p;
                if (args.length > 0) {
                    t = Bukkit.getPlayerExact(args[0]);
                    if (t == null) {
                        send(p, "<red>" + args[0] + " is not online.");
                        return true;
                    }
                }
                switch (cmd.getName().toLowerCase(Locale.ROOT)) {
                    case "fakeleft" -> toggleFakeLeft(t);
                    case "fly" -> toggleFly(t);
                    case "godmode" -> toggleGod(t);
                    case "spectator" -> toggleSpectator(t);
                    case "invis" -> toggleInvisible(t);
                    default -> toggleFreeBuild(t);
                }
                if (!t.equals(p)) send(p, "<gray>/" + cmd.getName().toLowerCase(Locale.ROOT) + " toggled for <white>" + t.getName() + "<gray>.");
            }
            case "spectate" -> spectate(p, args);
            case "gamemode" -> gamemode(p, args);
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

    // ------------------------------------------------------------ /fakeleft

    /**
     * /fakeleft: everyone sees "<name> left the game" and the admin disappears from the world and the player list
     * for normal players, but stays online and can keep playing. Run it again to "join" back.
     */
    private void toggleFakeLeft(Player p) {
        UUID id = p.getUniqueId();
        if (fakeLeft.remove(id)) {
            invisible.remove(id);
            for (Player other : Bukkit.getOnlinePlayers()) other.showPlayer(plugin, p);
            broadcastJoinLeave(p, "multiplayer.player.joined");
            send(p, "<yellow>You fake-joined. Everyone can see you again.");
        } else {
            fakeLeft.add(id);
            invisible.add(id);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(p) && !isAdminPlayer(other)) other.hidePlayer(plugin, p);
            }
            broadcastJoinLeave(p, "multiplayer.player.left");
            send(p, "<green>You fake-left. <gray>Normal players can't see you, but you can keep playing. "
                    + "Use /fakeleft again to join back. <red>Careful: your chat messages still show your name.");
        }
    }

    private void broadcastJoinLeave(Player p, String key) {
        net.kyori.adventure.text.Component msg = net.kyori.adventure.text.Component.translatable(key,
                net.kyori.adventure.text.format.NamedTextColor.YELLOW,
                net.kyori.adventure.text.Component.text(p.getName()));
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(p)) other.sendMessage(msg);
        }
    }

    // ------------------------------------------------------------ /fly /godmode /spectator /spectate /gamemode

    private void toggleFly(Player p) {
        boolean enable = !p.getAllowFlight();
        p.setAllowFlight(enable);
        if (enable) {
            if (!p.isOnGround()) p.setFlying(true);
            send(p, "<green>Fly <white>ON<green>. Double-tap space to fly.");
        } else {
            p.setFlying(false);
            send(p, "<yellow>Fly <white>OFF<yellow>.");
        }
    }

    private void toggleGod(Player p) {
        if (god.remove(p.getUniqueId())) {
            send(p, "<yellow>God mode <white>OFF<yellow>.");
        } else {
            god.add(p.getUniqueId());
            p.setFoodLevel(20);
            send(p, "<green>God mode <white>ON<green>. You take no damage and don't get hungry.");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && god.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && god.contains(p.getUniqueId())) e.setCancelled(true);
    }

    /** Switches to spectator and remembers the old game mode (only when not already spectating). */
    private void enterSpectator(Player p) {
        if (p.getGameMode() != GameMode.SPECTATOR) previousMode.put(p.getUniqueId(), p.getGameMode());
        p.setGameMode(GameMode.SPECTATOR);
    }

    private void leaveSpectator(Player p) {
        p.setSpectatorTarget(null);
        GameMode back = previousMode.remove(p.getUniqueId());
        p.setGameMode(back == null || back == GameMode.SPECTATOR ? GameMode.SURVIVAL : back);
    }

    private void toggleSpectator(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR) {
            leaveSpectator(p);
            send(p, "<yellow>Spectator <white>OFF<yellow>. Game mode: <white>" + p.getGameMode().name().toLowerCase(Locale.ROOT) + "<yellow>.");
        } else {
            enterSpectator(p);
            send(p, "<green>Spectator <white>ON<green>. Use /spectator again to go back.");
        }
    }

    /** /spectate <player> [who] follows a player in spectator mode. /spectate (no name) stops and goes back. */
    private void spectate(Player p, String[] args) {
        Player who = p;
        if (args.length >= 2) {
            who = Bukkit.getPlayerExact(args[1]);
            if (who == null) {
                send(p, "<red>" + args[1] + " is not online.");
                return;
            }
        }
        if (args.length == 0) {
            if (p.getGameMode() != GameMode.SPECTATOR) {
                send(p, "<red>Usage: /spectate <player> [who spectates]");
                return;
            }
            leaveSpectator(p);
            send(p, "<yellow>You stopped spectating.");
            return;
        }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null) {
            send(p, "<red>" + args[0] + " is not online.");
            return;
        }
        if (t.equals(who)) {
            send(p, "<red>A player can't spectate themselves.");
            return;
        }
        enterSpectator(who);
        who.teleport(t.getLocation());
        who.setSpectatorTarget(t);
        send(who, "<green>Spectating <white>" + t.getName() + "<green>. Sneak to stop, or use /spectator to go back to normal.");
        if (!who.equals(p)) send(p, "<gray>" + who.getName() + " now spectates <white>" + t.getName() + "<gray>.");
    }

    // ------------------------------------------------------------ /freebuild

    /**
     * /freebuild: placing blocks doesn't use them up. Handy with Litematica: the schematic goes down without
     * needing all the materials. You still need at least ONE of each block in your inventory (or use creative mode),
     * because the Litematica client mod can only place blocks it can pick from your inventory.
     */
    private void toggleFreeBuild(Player p) {
        if (freeBuild.remove(p.getUniqueId())) {
            send(p, "<yellow>Free build <white>OFF<yellow>. Blocks are used up again.");
        } else {
            freeBuild.add(p.getUniqueId());
            send(p, "<green>Free build <white>ON<green>. Placing blocks doesn't use them up (keep 1 of each block in your inventory).");
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceFree(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!freeBuild.contains(p.getUniqueId()) || p.getGameMode() == GameMode.CREATIVE) return;
        final ItemStack before = e.getItemInHand().clone();
        final EquipmentSlot hand = e.getHand();
        // the server uses up one item after the event: put the full stack back one tick later
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) p.getInventory().setItem(hand, before);
        });
    }

    private static GameMode parseMode(String in) {
        return switch (in.toLowerCase(Locale.ROOT)) {
            case "survival", "s", "0" -> GameMode.SURVIVAL;
            case "creative", "c", "1" -> GameMode.CREATIVE;
            case "adventure", "a", "2" -> GameMode.ADVENTURE;
            case "spectator", "sp", "3" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    /** /gamemode <survival|creative|adventure|spectator> [player] */
    private void gamemode(Player p, String[] args) {
        GameMode mode = args.length == 0 ? null : parseMode(args[0]);
        if (mode == null) {
            send(p, "<red>Usage: /gamemode <survival|creative|adventure|spectator> [player]");
            return;
        }
        Player t = p;
        if (args.length >= 2) {
            t = Bukkit.getPlayerExact(args[1]);
            if (t == null) {
                send(p, "<red>" + args[1] + " is not online.");
                return;
            }
        }
        previousMode.remove(t.getUniqueId());
        t.setGameMode(mode);
        String name = mode.name().toLowerCase(Locale.ROOT);
        if (t.equals(p)) {
            send(p, "<green>Game mode set to <white>" + name + "<green>.");
        } else {
            send(p, "<green>Set <white>" + t.getName() + "<green>'s game mode to <white>" + name + "<green>.");
            send(t, "<yellow>Your game mode was set to <white>" + name + "<yellow>.");
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
        god.remove(e.getPlayer().getUniqueId());
        freeBuild.remove(e.getPlayer().getUniqueId());
        // a fake-left admin who really disconnects: no second "left the game" message
        if (fakeLeft.remove(e.getPlayer().getUniqueId())) e.quitMessage(null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String cn = cmd.getName().toLowerCase(Locale.ROOT);
        if (!CapybaraScoreboard.isAdmin(sender)) return out;
        if (cn.equals("gamemode") && args.length == 1) {
            for (String m : List.of("survival", "creative", "adventure", "spectator")) {
                if (m.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(m);
            }
            return out;
        }
        boolean playerArg = (args.length == 1 && List.of("goto", "spectate", "fly", "godmode", "spectator", "invis", "fakeleft", "freebuild").contains(cn))
                || (args.length == 2 && (cn.equals("gamemode") || cn.equals("spectate")));
        if (playerArg) {
            String last = args[args.length - 1].toLowerCase(Locale.ROOT);
            Bukkit.getOnlinePlayers().forEach(pl -> {
                if (pl.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(pl.getName());
            });
        }
        return out;
    }
}
