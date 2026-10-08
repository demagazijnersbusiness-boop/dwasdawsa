package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * /ban /kick /unban (console + admins) and /rtp [overworld|nether|end] [player].
 * Bans use the normal Minecraft ban list, so they also show up in banned-players.json.
 */
public final class ModCommands implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.]{1,20}");
    private static final int RTP_PARALLEL = 16;
    private static final int POOL_PARALLEL = 4;
    private static final String[] DIMS = {"overworld", "nether", "end"};
    private static final int RTP_ROUNDS = 6;

    /** Blocks that are never a safe place to land on or stand in. */
    private static final Set<Material> UNSAFE = Set.of(
            Material.LAVA, Material.WATER, Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE,
            Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW,
            Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.POINTED_DRIPSTONE, Material.COBWEB,
            Material.BEDROCK, Material.BUBBLE_COLUMN, Material.SEAGRASS, Material.TALL_SEAGRASS, Material.KELP);

    private final CapybaraScoreboard plugin;
    private final Map<UUID, Long> rtpCooldown = new HashMap<>();
    /** Ready-made safe spots per dimension, so /rtp can teleport instantly. Their chunks stay loaded until used. */
    private final Map<String, Queue<Location>> pool = new ConcurrentHashMap<>();
    private final java.util.Set<String> filling = ConcurrentHashMap.newKeySet();

    public ModCommands(CapybaraScoreboard plugin) {
        this.plugin = plugin;
    }

    private void send(CommandSender s, String mini) {
        s.sendMessage(MM.deserialize(mini));
    }

    private static boolean isStaff(CommandSender s) {
        return s instanceof ConsoleCommandSender || CapybaraScoreboard.isAdmin(s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "ban" -> ban(sender, args);
            case "kick" -> kick(sender, args);
            case "unban" -> unban(sender, args);
            case "rtp" -> rtp(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ /ban /kick /unban

    private static String reasonFrom(String[] args, int from, String fallback) {
        if (args.length <= from) return fallback;
        String r = String.join(" ", Arrays.copyOfRange(args, from, args.length)).replaceAll("[\\r\\n]", " ").trim();
        return r.isEmpty() ? fallback : r;
    }

    /** Admins can't be banned or kicked, and you can't do it to yourself. Returns true when blocked. */
    private boolean protectedTarget(CommandSender by, Player online) {
        if (online == null) return false;
        if (by instanceof Player bp && bp.equals(online)) {
            send(by, "<red>You can't do that to yourself.");
            return true;
        }
        if (CapybaraScoreboard.isAdmin(online)) {
            send(by, "<red>" + online.getName() + " is an admin.");
            return true;
        }
        return false;
    }

    private void ban(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            send(sender, "<red>You don't have permission.");
            return;
        }
        if (args.length < 1 || !NAME.matcher(args[0]).matches()) {
            send(sender, "<red>Usage: /ban <player> [reason]");
            return;
        }
        Player online = Bukkit.getPlayerExact(args[0]);
        if (protectedTarget(sender, online)) return;
        OfflinePlayer off = online != null ? online : Bukkit.getOfflinePlayerIfCached(args[0]);
        if (off == null) {
            send(sender, "<red>That player has never joined.");
            return;
        }
        String name = off.getName() != null ? off.getName() : args[0];
        String reason = reasonFrom(args, 1, "Banned by an admin.");
        // the normal Minecraft ban list, so it also works with vanilla tools
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:ban " + name + " " + reason);
        if (online != null && online.isOnline()) {
            online.kick(MM.deserialize("<red><bold>You are banned.</bold><newline><gray>" + escape(reason)));
        }
        plugin.getLogger().warning(sender.getName() + " banned " + name + ": " + reason);
        send(sender, "<green>Banned <white>" + name + "<green>: <gray>" + escape(reason));
    }

    private void kick(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            send(sender, "<red>You don't have permission.");
            return;
        }
        if (args.length < 1) {
            send(sender, "<red>Usage: /kick <player> [reason]");
            return;
        }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null) {
            send(sender, "<red>" + args[0] + " is not online.");
            return;
        }
        if (protectedTarget(sender, t)) return;
        String reason = reasonFrom(args, 1, "Kicked by an admin.");
        t.kick(MM.deserialize("<red><bold>You were kicked.</bold><newline><gray>" + escape(reason)));
        plugin.getLogger().warning(sender.getName() + " kicked " + t.getName() + ": " + reason);
        send(sender, "<green>Kicked <white>" + t.getName() + "<green>: <gray>" + escape(reason));
    }

    private void unban(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            send(sender, "<red>You don't have permission.");
            return;
        }
        if (args.length != 1 || !NAME.matcher(args[0]).matches()) {
            send(sender, "<red>Usage: /unban <player>");
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:pardon " + args[0]);
        plugin.getLogger().warning(sender.getName() + " unbanned " + args[0]);
        send(sender, "<green>Unbanned <white>" + args[0] + "<green> (if they were banned).");
    }

    /** Stops text typed by a player from being read as MiniMessage tags. */
    private static String escape(String text) {
        return MM.escapeTags(text);
    }

    // ------------------------------------------------------------ /rtp

    private World worldFor(String dim) {
        World.Environment env = switch (dim) {
            case "nether" -> World.Environment.NETHER;
            case "end" -> World.Environment.THE_END;
            default -> World.Environment.NORMAL;
        };
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == env) return w;
        }
        return null;
    }

    private static String dimOf(World w) {
        return switch (w.getEnvironment()) {
            case NETHER -> "nether";
            case THE_END -> "end";
            default -> "overworld";
        };
    }

    /** /rtp [overworld|nether|end] [player]. Admins skip the cooldown and can send other players. */
    private void rtp(CommandSender sender, String[] args) {
        boolean staff = isStaff(sender);
        Player target;
        if (args.length >= 2) {
            if (!staff) {
                send(sender, "<red>Only admins can send another player.");
                return;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                send(sender, "<red>" + args[1] + " is not online.");
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            send(sender, "<red>Usage: /rtp <overworld|nether|end> <player>");
            return;
        }

        String dim = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : dimOf(target.getWorld());
        if (dim.equals("over") || dim.equals("normal") || dim.equals("world")) dim = "overworld";
        if (dim.equals("the_end")) dim = "end";
        if (!dim.equals("overworld") && !dim.equals("nether") && !dim.equals("end")) {
            send(sender, "<red>Usage: /rtp [overworld|nether|end]" + (staff ? " [player]" : ""));
            return;
        }
        if (!staff) {
            if (dim.equals("nether") && !plugin.getConfig().getBoolean("rtp.allow-nether", true)) {
                send(sender, "<red>Random teleport to the Nether is turned off.");
                return;
            }
            if (dim.equals("end") && !plugin.getConfig().getBoolean("rtp.allow-end", true)) {
                send(sender, "<red>Random teleport to the End is turned off.");
                return;
            }
            long cd = plugin.getConfig().getLong("rtp.cooldown-seconds", 60) * 1000L;
            long left = rtpCooldown.getOrDefault(target.getUniqueId(), 0L) + cd - System.currentTimeMillis();
            if (left > 0) {
                send(sender, "<red>Wait <white>" + ((left + 999) / 1000) + "s <red>before you use /rtp again.");
                return;
            }
        }
        World w = worldFor(dim);
        if (w == null) {
            send(sender, "<red>There is no " + dim + " world on this server.");
            return;
        }
        if (!staff) rtpCooldown.put(target.getUniqueId(), System.currentTimeMillis());

        // instant path: a safe spot was already found in the background
        Location ready = takeFromPool(dim, w);
        if (ready != null) {
            final String fdim = dim;
            target.teleportAsync(ready).thenAccept(ok -> {
                release(ready);
                if (ok) {
                    send(target, "<green>Teleported to <white>" + ready.getBlockX() + ", " + ready.getBlockY() + ", " + ready.getBlockZ()
                            + "<green> in the " + fdim + ".");
                } else if (!staff) {
                    rtpCooldown.remove(target.getUniqueId());
                }
            });
            if (!target.equals(sender)) send(sender, "<gray>Sent <white>" + target.getName() + "<gray> to a random spot in the " + dim + ".");
            refill(dim);
            return;
        }
        refill(dim); // pool was empty: the next /rtp will be instant
        send(target, "<gray>Searching for a safe spot in the <white>" + dim + "<gray>...");
        if (!target.equals(sender)) send(sender, "<gray>Sending <white>" + target.getName() + "<gray> to a random spot in the " + dim + "...");
        attempt(target, w, dim, 0, staff);
    }

    /**
     * Fast /rtp: instead of trying one random spot at a time, it checks RTP_PARALLEL spots at the same moment
     * (their chunks load side by side) and sends the player to the first safe one.
     */
    private void attempt(Player target, World w, String dim, int round, boolean staff) {
        if (!target.isOnline()) return;
        if (round >= RTP_ROUNDS) {
            send(target, "<red>Couldn't find a safe spot. Try again.");
            if (!staff) rtpCooldown.remove(target.getUniqueId());
            return;
        }
        int min = Math.max(0, plugin.getConfig().getInt("rtp." + dim + ".min", dim.equals("end") ? 1100 : 200));
        int max = Math.max(min + 1, plugin.getConfig().getInt("rtp." + dim + ".max", dim.equals("end") ? 4000 : 5000));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        AtomicBoolean won = new AtomicBoolean(false);
        AtomicInteger pending = new AtomicInteger(RTP_PARALLEL);
        for (int i = 0; i < RTP_PARALLEL; i++) {
            int x;
            int z;
            do {
                x = r.nextInt(-max, max + 1);
                z = r.nextInt(-max, max + 1);
            } while (Math.max(Math.abs(x), Math.abs(z)) < min);
            final int fx = x;
            final int fz = z;
            w.getChunkAtAsync(fx >> 4, fz >> 4).thenAccept(chunk -> {
                Location loc = won.get() ? null : findSafe(w, fx, fz, dim);
                if (loc != null && w.getWorldBorder().isInside(loc) && won.compareAndSet(false, true)) {
                    target.teleportAsync(loc).thenAccept(ok -> {
                        if (ok) {
                            send(target, "<green>Teleported to <white>" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                                    + "<green> in the " + dim + ".");
                        }
                    });
                    return;
                }
                // this spot was no good; if none of the parallel spots worked, start the next round
                if (pending.decrementAndGet() == 0 && !won.get()) attempt(target, w, dim, round + 1, staff);
            });
        }
    }

    // ------------------------------------------------------------ instant /rtp pool

    private int poolSize() {
        return Math.max(0, plugin.getConfig().getInt("rtp.pool-size", 4));
    }

    /** Called once when the plugin is enabled: fills the pool in the background after the worlds are ready. */
    public void startPool() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (String d : DIMS) refill(d);
        }, 100L);
    }

    /** Called when the plugin is disabled: lets go of the chunks the pool kept loaded. */
    public void stopPool() {
        for (Queue<Location> q : pool.values()) {
            for (Location l : q) release(l);
        }
        pool.clear();
        filling.clear();
    }

    private void hold(Location l) {
        l.getWorld().addPluginChunkTicket(l.getBlockX() >> 4, l.getBlockZ() >> 4, plugin);
    }

    private void release(Location l) {
        if (l.getWorld() != null) l.getWorld().removePluginChunkTicket(l.getBlockX() >> 4, l.getBlockZ() >> 4, plugin);
    }

    /** Takes a still-safe spot out of the pool, or null when the pool is empty. */
    private Location takeFromPool(String dim, World w) {
        Queue<Location> q = pool.get(dim);
        if (q == null) return null;
        Location l;
        while ((l = q.poll()) != null) {
            boolean ok = w.equals(l.getWorld()) && w.getWorldBorder().isInside(l)
                    && standable(w.getBlockAt(l.getBlockX(), l.getBlockY() - 1, l.getBlockZ()));
            if (ok) return l;
            release(l);
        }
        return null;
    }

    /** Tops the pool of a dimension up in the background (a few spots at a time, so it never lags the server). */
    private void refill(String dim) {
        if (poolSize() == 0 || !plugin.isEnabled()) return;
        World w = worldFor(dim);
        if (w == null) return;
        if (!filling.add(dim)) return;
        fill(dim, w, 0);
    }

    private void fill(String dim, World w, int round) {
        Queue<Location> q = pool.computeIfAbsent(dim, k -> new ConcurrentLinkedQueue<>());
        if (q.size() >= poolSize() || round >= 40 || !plugin.isEnabled()) {
            filling.remove(dim);
            return;
        }
        int min = Math.max(0, plugin.getConfig().getInt("rtp." + dim + ".min", dim.equals("end") ? 1100 : 200));
        int max = Math.max(min + 1, plugin.getConfig().getInt("rtp." + dim + ".max", dim.equals("end") ? 4000 : 5000));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        AtomicInteger pending = new AtomicInteger(POOL_PARALLEL);
        for (int i = 0; i < POOL_PARALLEL; i++) {
            int x;
            int z;
            do {
                x = r.nextInt(-max, max + 1);
                z = r.nextInt(-max, max + 1);
            } while (Math.max(Math.abs(x), Math.abs(z)) < min);
            final int fx = x;
            final int fz = z;
            w.getChunkAtAsync(fx >> 4, fz >> 4).whenComplete((chunk, err) -> {
                if (err == null && q.size() < poolSize()) {
                    Location loc = findSafe(w, fx, fz, dim);
                    if (loc != null && w.getWorldBorder().isInside(loc)) {
                        hold(loc);
                        q.add(loc);
                    }
                }
                if (pending.decrementAndGet() == 0) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> fill(dim, w, round + 1), 10L);
                }
            });
        }
    }

    private static boolean standable(Block floor) {
        Material t = floor.getType();
        if (UNSAFE.contains(t) || !t.isSolid()) return false;
        Block a = floor.getRelative(0, 1, 0);
        Block b = floor.getRelative(0, 2, 0);
        return a.isPassable() && b.isPassable() && !a.isLiquid() && !b.isLiquid()
                && !UNSAFE.contains(a.getType()) && !UNSAFE.contains(b.getType());
    }

    /** Finds a safe place to stand at x/z (chunk is already loaded), or null. */
    private Location findSafe(World w, int x, int z, String dim) {
        if (dim.equals("nether")) {
            // stay under the roof (y 120) and above the lava sea level
            for (int y = 120; y > 32; y--) {
                Block floor = w.getBlockAt(x, y, z);
                if (standable(floor)) return new Location(w, x + 0.5, y + 1, z + 0.5);
            }
            return null;
        }
        int y = w.getHighestBlockYAt(x, z);
        if (y <= w.getMinHeight() + 5) return null; // void
        Block floor = w.getBlockAt(x, y, z);
        if (!standable(floor)) return null;
        return new Location(w, x + 0.5, y + 1, z + 0.5);
    }

    // ------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (name.equals("rtp")) {
            if (args.length == 1) out.addAll(List.of("overworld", "nether", "end"));
            else if (args.length == 2 && isStaff(sender)) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (isStaff(sender) && args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
