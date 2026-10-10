package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /give &lt;item&gt; &lt;player&gt; [amount]  (admins). Understands our own items and every normal Minecraft item.
 * Anything else falls through to the normal Minecraft /give, so /give &lt;player&gt; &lt;item&gt; still works too.
 *
 * Custom item: <b>respawnblock</b> - an INVISIBLE block (a light block with light level 0, you can walk through it).
 * Place it somewhere and the moment a player touches it, they are instantly sent to a player spawn point
 * (see /setplayerspawn). Nobody can see it, except in creative mode while holding the item.
 */
public final class CustomItems implements CommandExecutor, TabCompleter, Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> CUSTOM = List.of("respawnblock");

    private final CapybaraScoreboard plugin;
    private final SpawnPoints spawns;
    private final NamespacedKey respawnKey;
    private final File file;
    /** "world;x;y;z" of every placed Respawn Block. */
    private final Set<String> blocks = new HashSet<>();
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public CustomItems(CapybaraScoreboard plugin, SpawnPoints spawns) {
        this.plugin = plugin;
        this.spawns = spawns;
        this.respawnKey = new NamespacedKey(plugin, "respawn_block");
        this.file = new File(plugin.getDataFolder(), "respawnblocks.yml");
        load();
    }

    private void send(CommandSender s, String mini) {
        s.sendMessage(MM.deserialize(mini));
    }

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private void load() {
        if (!file.exists()) return;
        blocks.addAll(YamlConfiguration.loadConfiguration(file).getStringList("blocks"));
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("blocks", new ArrayList<>(blocks));
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save respawnblocks.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ the item

    public ItemStack respawnBlock(int amount) {
        ItemStack it = new ItemStack(Material.LIGHT, Math.max(1, Math.min(64, amount)));
        it.editMeta(m -> {
            m.displayName(MM.deserialize("<!italic><dark_purple><bold>Respawn Block"));
            m.lore(List.of(
                    MM.deserialize("<!italic><gray>An invisible block."),
                    MM.deserialize("<!italic><gray>Touch it and you instantly"),
                    MM.deserialize("<!italic><gray>respawn at spawn.")));
            m.getPersistentDataContainer().set(respawnKey, PersistentDataType.BYTE, (byte) 1);
        });
        return it;
    }

    private boolean isRespawnItem(ItemStack it) {
        return it != null && it.getType() == Material.LIGHT && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(respawnKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------ /give

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CapybaraScoreboard.isAdmin(sender)) {
            // normal players: whatever vanilla does (nothing, unless they are OP)
            vanilla(sender, args);
            return true;
        }
        if (args.length >= 2) {
            // /give <item> <player> [amount]
            Player target = Bukkit.getPlayerExact(args[1]);
            boolean firstIsPlayer = Bukkit.getPlayerExact(args[0]) != null;
            if (target != null && !firstIsPlayer) {
                String id = args[0].toLowerCase(Locale.ROOT);
                int amount = 1;
                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        send(sender, "<red>Usage: /give <item> <player> [amount]");
                        return true;
                    }
                    if (amount < 1 || amount > 6400) {
                        send(sender, "<red>Amount must be between 1 and 6400.");
                        return true;
                    }
                }
                if (id.equals("respawnblock") || id.equals("respawn_block") || id.equals("respawn")) {
                    give(sender, target, respawnBlock(1), Math.min(amount, 64), "Respawn Block");
                    return true;
                }
                Material m = Material.matchMaterial(args[0]);
                if (m != null && m.isItem() && !m.isAir()) {
                    int max = Math.max(1, m.getMaxStackSize());
                    ItemStack proto = new ItemStack(m, 1);
                    give(sender, target, proto, amount, m.name().toLowerCase(Locale.ROOT).replace('_', ' '));
                    return true;
                }
                send(sender, "<red>Unknown item <white>" + MM.escapeTags(args[0]) + "<red>. Try: <white>respawnblock<red> or any Minecraft item.");
                return true;
            }
        }
        if (args.length == 0) {
            send(sender, "<red>Usage: /give <item> <player> [amount] <gray>(custom items: respawnblock)");
            return true;
        }
        vanilla(sender, args); // /give <player> <item> [count] and selectors like @a
        return true;
    }

    private void vanilla(CommandSender sender, String[] args) {
        Bukkit.dispatchCommand(sender, "minecraft:give " + String.join(" ", args));
    }

    private void give(CommandSender from, Player to, ItemStack proto, int amount, String name) {
        int left = amount;
        int stack = Math.max(1, proto.getMaxStackSize());
        while (left > 0) {
            ItemStack part = proto.clone();
            part.setAmount(Math.min(stack, left));
            left -= part.getAmount();
            to.getInventory().addItem(part).values().forEach(r -> to.getWorld().dropItemNaturally(to.getLocation(), r));
        }
        send(from, "<green>Gave <white>" + amount + "x " + name + "<green> to <white>" + to.getName() + "<green>.");
        if (!from.equals(to)) send(to, "<green>You got <white>" + amount + "x " + name + "<green>.");
    }

    // ------------------------------------------------------------ the invisible block

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!isRespawnItem(e.getItemInHand())) return;
        Block b = e.getBlockPlaced();
        if (b.getBlockData() instanceof Light light) {
            light.setLevel(0); // no light at all: completely invisible
            b.setBlockData(light, false);
        }
        blocks.add(key(b));
        save();
        e.getPlayer().sendMessage(MM.deserialize("<dark_purple>Respawn Block placed. <gray>It's invisible, players who touch it go to spawn."));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (blocks.remove(key(e.getBlock()))) {
            save();
            e.getPlayer().sendMessage(MM.deserialize("<yellow>Respawn Block removed."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (blocks.isEmpty() || e.getTo() == null) return;
        Location to = e.getTo();
        Location from = e.getFrom();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;
        Player p = e.getPlayer();
        Block feet = to.getBlock();
        if (!(blocks.contains(key(feet)) || blocks.contains(key(feet.getRelative(0, 1, 0))))) return;
        long now = System.currentTimeMillis();
        Long last = lastUse.get(p.getUniqueId());
        if (last != null && now - last < 1000) return;
        lastUse.put(p.getUniqueId(), now);

        Location spawn = spawns.randomSpawn();
        p.setFallDistance(0f);
        p.setFireTicks(0);
        p.teleportAsync(spawn).thenAccept(ok -> {
            if (ok) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                p.sendMessage(MM.deserialize("<dark_purple>You touched a Respawn Block and went back to spawn."));
            }
        });
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        lastUse.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!CapybaraScoreboard.isAdmin(sender)) return out;
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            out.addAll(CUSTOM);
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            for (Material m : Material.values()) {
                if (m.isItem() && !m.isLegacy() && m.name().toLowerCase(Locale.ROOT).startsWith(last) && out.size() < 60) out.add(m.name().toLowerCase(Locale.ROOT));
            }
        } else if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            out.addAll(List.of("1", "16", "64"));
        } else if (args.length == 3) {
            out.addAll(List.of("1", "16", "64"));
        }
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
