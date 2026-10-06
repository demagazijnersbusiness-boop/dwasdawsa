package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * /schem: admins drop .litematic / .schem files in plugins/CapybaraScoreboard/schematics/ and paste them with
 * no materials needed. By default the schematic's air blocks replace the terrain, so the area is dug out first.
 * Pasting is spread over many ticks so the server doesn't lag, and /schem undo restores the old blocks.
 */
public final class SchematicCommands implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CapybaraScoreboard plugin;
    private final File folder;
    private final Map<UUID, Undo> undo = new HashMap<>();
    private BatchJob running;
    private boolean loading;

    /** The blocks a paste replaced, so it can be undone. */
    private record Undo(World world, int[] x, int[] y, int[] z, BlockData[] old, int count) {
    }

    public SchematicCommands(CapybaraScoreboard plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "schematics");
        this.folder.mkdirs();
    }

    private void send(CommandSender s, String mini) {
        s.sendMessage(MM.deserialize(mini));
    }

    private long maxBlocks() {
        return Math.max(1000L, plugin.getConfig().getLong("schematics.max-blocks", 2_000_000L));
    }

    private int perTick() {
        return Math.max(100, plugin.getConfig().getInt("schematics.blocks-per-tick", 5000));
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
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(p);
            case "info" -> info(p, args);
            case "paste" -> paste(p, args);
            case "undo" -> undo(p);
            case "cancel" -> cancel(p);
            default -> {
                send(p, "<yellow>Schematics <gray>(drop .litematic / .schem files in <white>plugins/" + plugin.getName() + "/schematics/<gray>)");
                send(p, "<white>/schem list <gray>- the files you can place");
                send(p, "<white>/schem info <name> <gray>- size and block count");
                send(p, "<white>/schem paste <name> [0|90|180|270] [noair] <gray>- place it with its corner on your feet. "
                        + "Air in the schematic digs the terrain out; <white>noair<gray> keeps the terrain.");
                send(p, "<white>/schem undo <gray>- put back what your last paste replaced");
                send(p, "<white>/schem cancel <gray>- stop a paste that is running");
            }
        }
        return true;
    }

    // ------------------------------------------------------------ files

    private File find(String name) {
        for (String f : SchematicLoader.listFiles(folder)) {
            String base = f.substring(0, f.lastIndexOf('.'));
            if (f.equalsIgnoreCase(name) || base.equalsIgnoreCase(name)) return new File(folder, f);
        }
        return null;
    }

    private void list(Player p) {
        List<String> files = SchematicLoader.listFiles(folder);
        if (files.isEmpty()) {
            send(p, "<yellow>No schematics yet. Put .litematic or .schem files in <white>plugins/" + plugin.getName() + "/schematics/<yellow>.");
            return;
        }
        send(p, "<yellow>Schematics (" + files.size() + "):");
        for (int i = 0; i < files.size() && i < 50; i++) send(p, "<gray>- <white>" + MM.escapeTags(files.get(i)));
        if (files.size() > 50) send(p, "<gray>... and " + (files.size() - 50) + " more.");
    }

    private void info(Player p, String[] args) {
        if (args.length < 2) {
            send(p, "<red>Usage: /schem info <name>");
            return;
        }
        File f = find(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        if (f == null) {
            send(p, "<red>No such schematic. Use /schem list.");
            return;
        }
        if (loading || running != null) {
            send(p, "<red>A schematic is busy right now. Wait a moment.");
            return;
        }
        loading = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SchematicData d = SchematicLoader.load(f, maxBlocks());
                boolean[] air = new boolean[d.palette.length];
                for (int i = 0; i < air.length; i++) air[i] = SchematicData.isAir(d.palette[i]);
                long solid = 0;
                for (int idx : d.blocks) if (idx >= 0 && !air[idx]) solid++;
                final long solidCount = solid;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    loading = false;
                    send(p, "<yellow>" + MM.escapeTags(f.getName()) + "<gray>: <white>" + d.sizeX + " x " + d.sizeY + " x " + d.sizeZ
                            + " <gray>(x, y, z), <white>" + solidCount + " <gray>blocks that aren't air, <white>" + d.palette.length + " <gray>block types.");
                });
            } catch (IOException | RuntimeException e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    loading = false;
                    send(p, "<red>Couldn't read it: " + MM.escapeTags(String.valueOf(e.getMessage())));
                });
            }
        });
    }

    // ------------------------------------------------------------ paste

    private void paste(Player p, String[] args) {
        if (loading || running != null) {
            send(p, "<red>A schematic is busy right now. Use /schem cancel or wait.");
            return;
        }
        List<String> parts = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
        int rot = 0;
        boolean noAir = false;
        while (!parts.isEmpty()) {
            String last = parts.get(parts.size() - 1).toLowerCase(Locale.ROOT);
            if (last.equals("noair")) {
                noAir = true;
                parts.remove(parts.size() - 1);
            } else if (last.equals("0") || last.equals("90") || last.equals("180") || last.equals("270")) {
                rot = Integer.parseInt(last);
                parts.remove(parts.size() - 1);
            } else {
                break;
            }
        }
        if (parts.isEmpty()) {
            send(p, "<red>Usage: /schem paste <name> [0|90|180|270] [noair]");
            return;
        }
        File f = find(String.join(" ", parts));
        if (f == null) {
            send(p, "<red>No such schematic. Use /schem list.");
            return;
        }
        final int fRot = rot;
        final boolean fNoAir = noAir;
        final Location origin = p.getLocation().getBlock().getLocation();
        loading = true;
        send(p, "<gray>Reading <white>" + MM.escapeTags(f.getName()) + "<gray>...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SchematicData d = SchematicLoader.load(f, maxBlocks());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    loading = false;
                    start(p, f.getName(), d, origin, fRot, fNoAir);
                });
            } catch (IOException | RuntimeException e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    loading = false;
                    send(p, "<red>Couldn't read it: " + MM.escapeTags(String.valueOf(e.getMessage())));
                });
            }
        });
    }

    private void start(Player p, String fileName, SchematicData d, Location origin, int rot, boolean noAir) {
        if (!p.isOnline()) return;
        World w = origin.getWorld();
        StructureRotation sr = switch (rot) {
            case 90 -> StructureRotation.CLOCKWISE_90;
            case 180 -> StructureRotation.CLOCKWISE_180;
            case 270 -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
        BlockData[] pal = new BlockData[d.palette.length];
        boolean[] air = new boolean[d.palette.length];
        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < pal.length; i++) {
            air[i] = SchematicData.isAir(d.palette[i]);
            try {
                BlockData bd = Bukkit.createBlockData(d.palette[i]);
                if (sr != StructureRotation.NONE) bd.rotate(sr);
                pal[i] = bd;
            } catch (IllegalArgumentException e) {
                pal[i] = null;
                if (unknown.size() < 5) unknown.add(d.palette[i]);
            }
        }
        final int[] blocks = d.blocks;
        int count = 0;
        for (int idx : blocks) {
            if (idx < 0 || pal[idx] == null || (noAir && air[idx])) continue;
            count++;
        }
        if (count == 0) {
            send(p, "<red>There's nothing to place in this schematic.");
            return;
        }
        final int total = count;
        final int[] ux = new int[total];
        final int[] uy = new int[total];
        final int[] uz = new int[total];
        final BlockData[] old = new BlockData[total];
        final int[] used = {0};
        final int[] skippedHeight = {0};
        final int sx = d.sizeX;
        final int sz = d.sizeZ;
        final int ox = origin.getBlockX();
        final int oy = origin.getBlockY();
        final int oz = origin.getBlockZ();
        final int minH = w.getMinHeight();
        final int maxH = w.getMaxHeight();
        final int fRot = rot;

        IntConsumer step = i -> {
            int idx = blocks[i];
            if (idx < 0 || pal[idx] == null || (noAir && air[idx])) return;
            int x = i % sx;
            int z = (i / sx) % sz;
            int y = i / (sx * sz);
            int rx;
            int rz;
            switch (fRot) {
                case 90 -> {
                    rx = sz - 1 - z;
                    rz = x;
                }
                case 180 -> {
                    rx = sx - 1 - x;
                    rz = sz - 1 - z;
                }
                case 270 -> {
                    rx = z;
                    rz = sx - 1 - x;
                }
                default -> {
                    rx = x;
                    rz = z;
                }
            }
            int wx = ox + rx;
            int wy = oy + y;
            int wz = oz + rz;
            if (wy < minH || wy >= maxH) {
                skippedHeight[0]++;
                return;
            }
            Block b = w.getBlockAt(wx, wy, wz);
            int k = used[0]++;
            ux[k] = wx;
            uy[k] = wy;
            uz[k] = wz;
            old[k] = b.getBlockData();
            b.setBlockData(pal[idx], false);
        };
        Consumer<Boolean> done = cancelled -> {
            if (used[0] > 0) undo.put(p.getUniqueId(), new Undo(w, ux, uy, uz, old, used[0]));
            String msg = (cancelled ? "<yellow>Paste stopped. " : "<green>Done! ") + "Placed <white>" + used[0] + "<green> blocks"
                    + (skippedHeight[0] > 0 ? " <gray>(" + skippedHeight[0] + " were above or below the world)" : "")
                    + "<green>. <gray>/schem undo puts the old blocks back.";
            if (p.isOnline()) send(p, msg);
        };

        plugin.getLogger().warning(p.getName() + " pasted " + fileName + " at " + ox + " " + oy + " " + oz + " (" + w.getName()
                + ", rotation " + rot + (noAir ? ", no air" : "") + ").");
        send(p, "<green>Placing <white>" + MM.escapeTags(fileName) + "<green> (" + d.sizeX + " x " + d.sizeY + " x " + d.sizeZ + ", "
                + total + " blocks)" + (noAir ? "" : ", digging out the area") + "<green>. This takes a few seconds.");
        if (!unknown.isEmpty()) {
            send(p, "<yellow>Some blocks don't exist in this Minecraft version and are skipped, for example <white>"
                    + MM.escapeTags(String.join(", ", unknown)) + "<yellow>.");
        }
        send(p, "<gray>Chests, signs and other containers come without their contents.");
        runBatch(p, blocks.length, step, done);
    }

    // ------------------------------------------------------------ undo / cancel

    private void undo(Player p) {
        if (loading || running != null) {
            send(p, "<red>A schematic is busy right now. Wait a moment.");
            return;
        }
        Undo u = undo.remove(p.getUniqueId());
        if (u == null) {
            send(p, "<red>Nothing to undo.");
            return;
        }
        send(p, "<gray>Putting back <white>" + u.count() + "<gray> blocks...");
        IntConsumer step = j -> {
            int k = u.count() - 1 - j;
            u.world().getBlockAt(u.x()[k], u.y()[k], u.z()[k]).setBlockData(u.old()[k], false);
        };
        Consumer<Boolean> done = cancelled -> {
            if (p.isOnline()) send(p, cancelled ? "<yellow>Undo stopped halfway." : "<green>Undone.");
        };
        runBatch(p, u.count(), step, done);
    }

    private void cancel(Player p) {
        if (running == null) {
            send(p, "<yellow>Nothing is running.");
            return;
        }
        running.finish(true);
    }

    // ------------------------------------------------------------ batching

    private void runBatch(Player owner, int total, IntConsumer step, Consumer<Boolean> done) {
        BatchJob job = new BatchJob(owner, total, step, done);
        running = job;
        job.task = Bukkit.getScheduler().runTaskTimer(plugin, job, 1L, 1L);
    }

    /** Does a limited amount of work each tick so the server doesn't freeze. */
    private final class BatchJob implements Runnable {
        final Player owner;
        final int total;
        final IntConsumer step;
        final Consumer<Boolean> done;
        BukkitTask task;
        int pos;
        boolean finished;

        BatchJob(Player owner, int total, IntConsumer step, Consumer<Boolean> done) {
            this.owner = owner;
            this.total = total;
            this.step = step;
            this.done = done;
        }

        @Override
        public void run() {
            if (finished) return;
            try {
                int end = (int) Math.min((long) total, (long) pos + perTick());
                for (; pos < end; pos++) step.accept(pos);
                if (pos >= total) finish(false);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Schematic job failed: " + e);
                if (owner.isOnline()) send(owner, "<red>Something went wrong, the paste was stopped: " + MM.escapeTags(String.valueOf(e.getMessage())));
                finish(true);
            }
        }

        void finish(boolean cancelled) {
            if (finished) return;
            finished = true;
            if (task != null) task.cancel();
            if (running == this) running = null;
            done.accept(cancelled);
        }
    }

    // ------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!CapybaraScoreboard.isAdmin(sender)) return out;
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            out.addAll(List.of("list", "info", "paste", "undo", "cancel"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("paste"))) {
            for (String f : SchematicLoader.listFiles(folder)) {
                if (!f.contains(" ")) out.add(f.substring(0, f.lastIndexOf('.')));
            }
        } else if (args.length >= 3 && args[0].equalsIgnoreCase("paste")) {
            out.addAll(List.of("0", "90", "180", "270", "noair"));
        }
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
