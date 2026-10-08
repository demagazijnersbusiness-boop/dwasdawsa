package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /savestructure: gives an admin a Structure Axe. Left click = corner 1, right click = corner 2. Run /savestructure
 * again to save: every block between the two corners can no longer be broken (or blown up) by normal players.
 * Admins in creative mode can still edit. Structures are kept in structures.yml.
 */
public final class StructureProtection implements CommandExecutor, TabCompleter, Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private record Region(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        boolean contains(Block b) {
            return contains(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        }

        boolean contains(String w, int x, int y, int z) {
            return world.equals(w) && x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }
    }

    private final CapybaraScoreboard plugin;
    private final NamespacedKey axeKey;
    private final File file;
    private final List<Region> regions = new ArrayList<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public StructureProtection(CapybaraScoreboard plugin) {
        this.plugin = plugin;
        this.axeKey = new NamespacedKey(plugin, "structure_axe");
        this.file = new File(plugin.getDataFolder(), "structures.yml");
        load();
    }

    private void send(CommandSender s, String mini) {
        s.sendMessage(MM.deserialize(mini));
    }

    // ------------------------------------------------------------ storage

    private void load() {
        regions.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("structures");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            regions.add(new Region(key, s.getString("world", ""), s.getInt("x1"), s.getInt("y1"), s.getInt("z1"),
                    s.getInt("x2"), s.getInt("y2"), s.getInt("z2")));
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Region r : regions) {
            String b = "structures." + r.name();
            y.set(b + ".world", r.world());
            y.set(b + ".x1", r.x1());
            y.set(b + ".y1", r.y1());
            y.set(b + ".z1", r.z1());
            y.set(b + ".x2", r.x2());
            y.set(b + ".y2", r.y2());
            y.set(b + ".z2", r.z2());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save structures.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ the axe

    private ItemStack makeAxe() {
        ItemStack axe = new ItemStack(Material.GOLDEN_AXE);
        axe.editMeta(m -> {
            m.displayName(MM.deserialize("<!italic><gold><bold>Structure Axe"));
            m.lore(List.of(
                    MM.deserialize("<!italic><gray>Left click a block = <white>corner 1"),
                    MM.deserialize("<!italic><gray>Right click a block = <white>corner 2"),
                    MM.deserialize("<!italic><gray>Then type <white>/savestructure <gray>to save")));
            m.setUnbreakable(true);
            m.getPersistentDataContainer().set(axeKey, PersistentDataType.BYTE, (byte) 1);
        });
        return axe;
    }

    private boolean isAxe(ItemStack it) {
        return it != null && it.getType() == Material.GOLDEN_AXE && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(axeKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------ command

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
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                if (regions.isEmpty()) send(p, "<yellow>No saved structures.");
                for (Region r : regions) {
                    send(p, "<gray>- <white>" + MM.escapeTags(r.name()) + " <gray>(" + r.world() + " " + r.x1() + "," + r.y1() + "," + r.z1()
                            + " to " + r.x2() + "," + r.y2() + "," + r.z2() + ")");
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    send(p, "<red>Usage: /savestructure remove <name>");
                    return true;
                }
                boolean gone = regions.removeIf(r -> r.name().equalsIgnoreCase(args[1]));
                if (gone) save();
                send(p, gone ? "<yellow>Structure removed, people can break it again." : "<red>No structure with that name.");
            }
            case "clear" -> {
                pos1.remove(p.getUniqueId());
                pos2.remove(p.getUniqueId());
                send(p, "<yellow>Your selection was cleared.");
            }
            default -> {
                // /savestructure [name]: with a full selection it saves, otherwise it gives the axe
                Location a = pos1.get(p.getUniqueId());
                Location b = pos2.get(p.getUniqueId());
                if (a != null && b != null) {
                    saveSelection(p, a, b, sub.isEmpty() ? null : args[0]);
                    return true;
                }
                boolean has = false;
                for (ItemStack it : p.getInventory().getContents()) if (isAxe(it)) has = true;
                if (!has) p.getInventory().addItem(makeAxe()).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
                send(p, "<green>You got the <gold>Structure Axe<green>. <gray>Left click = corner 1, right click = corner 2, "
                        + "then run <white>/savestructure<gray> again to save.");
                send(p, "<gray>Also: /savestructure list, remove <name>, clear");
            }
        }
        return true;
    }

    private void saveSelection(Player p, Location a, Location b, String wanted) {
        if (!a.getWorld().equals(b.getWorld())) {
            send(p, "<red>Both corners must be in the same world. Use /savestructure clear and try again.");
            return;
        }
        String name = wanted;
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,32}")) {
            int n = regions.size() + 1;
            while (nameTaken("structure" + n)) n++;
            name = "structure" + n;
        } else if (nameTaken(name)) {
            send(p, "<red>That name is already used.");
            return;
        }
        Region r = new Region(name, a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
        regions.add(r);
        save();
        pos1.remove(p.getUniqueId());
        pos2.remove(p.getUniqueId());
        long vol = (long) (r.x2() - r.x1() + 1) * (r.y2() - r.y1() + 1) * (r.z2() - r.z1() + 1);
        send(p, "<green>Structure <white>" + name + "<green> saved (<white>" + vol + "<green> blocks). Players can't break anything in it now.");
        plugin.getLogger().warning(p.getName() + " saved protected structure " + name + " (" + vol + " blocks).");
    }

    private boolean nameTaken(String n) {
        for (Region r : regions) if (r.name().equalsIgnoreCase(n)) return true;
        return false;
    }

    // ------------------------------------------------------------ selecting

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSelect(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!isAxe(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        if (!CapybaraScoreboard.isAdmin(p)) return;
        Location l = e.getClickedBlock().getLocation();
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1.put(p.getUniqueId(), l);
            send(p, "<green>Corner 1 set <gray>(" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ")"
                    + (pos2.containsKey(p.getUniqueId()) ? " <yellow>Now run /savestructure to save." : ""));
        } else {
            pos2.put(p.getUniqueId(), l);
            send(p, "<green>Corner 2 set <gray>(" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ")"
                    + (pos1.containsKey(p.getUniqueId()) ? " <yellow>Now run /savestructure to save." : ""));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pos1.remove(e.getPlayer().getUniqueId());
        pos2.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ protection

    private boolean isProtected(Block b) {
        for (Region r : regions) if (r.contains(b)) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (isAxe(p.getInventory().getItemInMainHand())) {
            e.setCancelled(true); // the Structure Axe only selects, it never breaks blocks
            return;
        }
        if (!isProtected(e.getBlock())) return;
        if (CapybaraScoreboard.isAdmin(p) && p.getGameMode() == GameMode.CREATIVE) return;
        e.setCancelled(true);
        p.sendActionBar(Component.text("This structure is protected.", net.kyori.adventure.text.format.NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::isProtected);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::isProtected);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!CapybaraScoreboard.isAdmin(sender)) return out;
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) out.addAll(List.of("list", "remove", "clear"));
        else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) for (Region r : regions) out.add(r.name());
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
