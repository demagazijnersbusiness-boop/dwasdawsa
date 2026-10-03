package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emerald = the second currency. You earn it by standing still after typing /afk.
 * It is spent in /emeraldshop. Also handles the Emerald Pickaxe (expires after 24 hours).
 */
public final class EmeraldManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CapybaraScoreboard plugin;
    private final DataManager data;
    private final BoardManager boards;
    private final NamespacedKey pickKey;
    private final NamespacedKey expiresKey;
    private final NamespacedKey swordKey;

    private final Map<UUID, Location> afk = new HashMap<>();
    private final Map<UUID, Integer> afkSeconds = new HashMap<>();

    public EmeraldManager(CapybaraScoreboard plugin, DataManager data, BoardManager boards) {
        this.plugin = plugin;
        this.data = data;
        this.boards = boards;
        this.pickKey = new NamespacedKey(plugin, "emerald_pickaxe");
        this.expiresKey = new NamespacedKey(plugin, "emerald_expires");
        this.swordKey = new NamespacedKey(plugin, "emerald_sword");
    }

    private static Component l(String s) {
        return MM.deserialize("<!italic>" + s);
    }

    public String currency() {
        return plugin.getConfig().getString("emerald.currency-name", "Emerald");
    }

    // ------------------------------------------------------------ balance

    public long balance(UUID id) {
        return data.get(id).emerald;
    }

    public void add(UUID id, long amount) {
        PlayerData d = data.get(id);
        d.emerald = Math.max(0L, d.emerald + amount);
        refresh(id);
    }

    public boolean take(UUID id, long amount) {
        PlayerData d = data.get(id);
        if (d.emerald < amount) return false;
        d.emerald -= amount;
        refresh(id);
        return true;
    }

    private void refresh(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) boards.update(p);
    }

    public static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m";
        return s + "s";
    }

    // ------------------------------------------------------------ AFK

    public boolean isAfk(Player p) {
        return afk.containsKey(p.getUniqueId());
    }

    public void toggleAfk(Player p) {
        if (isAfk(p)) {
            stopAfk(p, "<yellow>You are no longer AFK.");
            return;
        }
        afk.put(p.getUniqueId(), p.getLocation().clone());
        afkSeconds.put(p.getUniqueId(), 0);
        p.sendMessage(MM.deserialize("<green>You are now AFK. <gray>Stand still to earn <#17dd62><c><gray>. "
                        + "Move or type <white>/afk <gray>again to stop.",
                Placeholder.unparsed("c", currency())));
    }

    public void stopAfk(Player p, String miniMessage) {
        afk.remove(p.getUniqueId());
        afkSeconds.remove(p.getUniqueId());
        p.sendActionBar(Component.empty());
        if (miniMessage != null) p.sendMessage(MM.deserialize(miniMessage));
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAfk, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::scanAll, 200L, 400L);
    }

    private void tickAfk() {
        int interval = Math.max(1, plugin.getConfig().getInt("emerald.afk.interval-seconds", 60));
        long amount = Math.max(1L, plugin.getConfig().getLong("emerald.afk.amount", 1L));

        for (UUID id : new ArrayList<>(afk.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                afk.remove(id);
                afkSeconds.remove(id);
                continue;
            }
            Location anchor = afk.get(id);
            Location now = p.getLocation();
            if (anchor == null || !now.getWorld().equals(anchor.getWorld()) || now.distanceSquared(anchor) > 0.09) {
                stopAfk(p, "<yellow>You moved, so you are no longer AFK.");
                continue;
            }
            int seconds = afkSeconds.merge(id, 1, Integer::sum);
            if (seconds >= interval) {
                afkSeconds.put(id, 0);
                add(id, amount);
                p.sendMessage(MM.deserialize("<green>+<n> <#17dd62><c> <gray>(AFK reward)",
                        Placeholder.unparsed("n", String.valueOf(amount)),
                        Placeholder.unparsed("c", currency())));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.6f);
            } else {
                p.sendActionBar(MM.deserialize("<green>AFK <gray>\u00bb next <#17dd62><c> <gray>in <white><s>s <dark_gray>(move to stop)",
                        Placeholder.unparsed("c", currency()),
                        Placeholder.unparsed("s", String.valueOf(interval - seconds))));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        afk.remove(e.getPlayer().getUniqueId());
        afkSeconds.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && isAfk(p)) {
            stopAfk(p, "<yellow>You took damage, so you are no longer AFK.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> scan(p), 20L);
    }

    // ------------------------------------------------------------ Emerald Pickaxe

    public ItemStack createPickaxe() {
        long hours = Math.max(1L, plugin.getConfig().getLong("emerald.shop.pickaxe-hours", 24L));
        long expires = System.currentTimeMillis() + hours * 3_600_000L;
        ItemStack it = new ItemStack(Material.NETHERITE_PICKAXE);
        it.editMeta(m -> {
            m.displayName(l("<#17dd62><bold>EMERALD PICKAXE"));
            m.setUnbreakable(true);
            m.addEnchant(Enchantment.EFFICIENCY, 5, true);
            m.getPersistentDataContainer().set(pickKey, PersistentDataType.BYTE, (byte) 1);
            m.getPersistentDataContainer().set(expiresKey, PersistentDataType.LONG, expires);
            m.lore(pickLore(expires));
        });
        return it;
    }

    private List<Component> pickLore(long expires) {
        long left = expires - System.currentTimeMillis();
        return List.of(
                l("<gray>Mines <white>6 blocks <gray>at once <dark_gray>(3x2)"),
                l("<gray>Unbreakable"),
                Component.empty(),
                l("<red>Expires in <white>" + duration(left)));
    }

    public boolean isPickaxe(ItemStack it) {
        if (it == null || it.getType() != Material.NETHERITE_PICKAXE || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(pickKey, PersistentDataType.BYTE);
    }

    public long expiresAt(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0L;
        Long v = it.getItemMeta().getPersistentDataContainer().get(expiresKey, PersistentDataType.LONG);
        return v == null ? 0L : v;
    }

    public boolean isExpired(ItemStack it) {
        return expiresAt(it) <= System.currentTimeMillis();
    }

    public void notifyExpired(Player p) {
        p.sendMessage(MM.deserialize("<red>Your <#17dd62>Emerald Pickaxe <red>expired and disappeared!"));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
    }


    // ------------------------------------------------------------ Emerald Sword

    public ItemStack createSword() {
        long hours = Math.max(1L, plugin.getConfig().getLong("emerald.shop.sword-hours", 5L));
        long expires = System.currentTimeMillis() + hours * 3_600_000L;
        ItemStack it = new ItemStack(Material.NETHERITE_SWORD);
        it.editMeta(m -> {
            m.displayName(l("<#17dd62><bold>EMERALD SWORD"));
            m.setUnbreakable(true);
            m.getPersistentDataContainer().set(swordKey, PersistentDataType.BYTE, (byte) 1);
            m.getPersistentDataContainer().set(expiresKey, PersistentDataType.LONG, expires);
            m.lore(swordLore(expires));
        });
        return it;
    }

    private List<Component> swordLore(long expires) {
        long left = expires - System.currentTimeMillis();
        return List.of(
                l("<gray>Kills <white>anything <gray>in one hit"),
                l("<gray>Unbreakable"),
                Component.empty(),
                l("<red>Self-destructs in <white>" + duration(left)));
    }

    public boolean isSword(ItemStack it) {
        if (it == null || it.getType() != Material.NETHERITE_SWORD || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(swordKey, PersistentDataType.BYTE);
    }

    public void notifyExpired(Player p, String itemName) {
        p.sendMessage(MM.deserialize("<red>Your <#17dd62><i> <red>expired and disappeared!",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("i", itemName)));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
    }

    private void scanAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            scan(p);
        }
    }

    /** Removes expired Emerald items (pickaxe + sword) from the inventory + ender chest and refreshes the "expires in" text. */
    public void scan(Player p) {
        long now = System.currentTimeMillis();
        boolean removedPick = false;
        boolean removedSword = false;
        Inventory[] places = {p.getInventory(), p.getEnderChest()};
        for (Inventory inv : places) {
            ItemStack[] contents = inv.getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack it = contents[i];
                boolean pick = isPickaxe(it);
                boolean sword = !pick && isSword(it);
                if (!pick && !sword) continue;
                long exp = expiresAt(it);
                if (exp <= now) {
                    inv.setItem(i, null);
                    if (pick) removedPick = true;
                    else removedSword = true;
                    continue;
                }
                List<Component> want = pick ? pickLore(exp) : swordLore(exp);
                ItemMeta meta = it.getItemMeta();
                if (!want.equals(meta.lore())) {
                    it.editMeta(m -> m.lore(want));
                    inv.setItem(i, it);
                }
            }
        }
        if (removedPick) notifyExpired(p);
        if (removedSword) notifyExpired(p, "Emerald Sword");
    }
}
