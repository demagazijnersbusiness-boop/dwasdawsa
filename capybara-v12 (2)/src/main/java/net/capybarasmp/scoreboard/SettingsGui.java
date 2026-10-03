package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** /settings - turn mob spawning near you off, and get infinite night vision. */
public final class SettingsGui implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int NIGHT_VISION = 11;
    private static final int MOB_SPAWN = 15;
    private static final int NIGHT_VISION_STATUS = 20;
    private static final int MOB_SPAWN_STATUS = 24;
    private static final Set<CreatureSpawnEvent.SpawnReason> BLOCKED = Set.of(
            CreatureSpawnEvent.SpawnReason.NATURAL,
            CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
            CreatureSpawnEvent.SpawnReason.PATROL,
            CreatureSpawnEvent.SpawnReason.RAID,
            CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION,
            CreatureSpawnEvent.SpawnReason.TRAP);

    private final CapybaraScoreboard plugin;
    private final DataManager data;

    public SettingsGui(CapybaraScoreboard plugin, DataManager data) {
        this.plugin = plugin;
        this.data = data;
    }

    private static Component l(String s) {
        return MM.deserialize("<!italic>" + s);
    }

    public void start() {
        // keep night vision on for everybody who turned it on (milk, death, /effect clear...)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (data.get(p.getUniqueId()).nightVision && !hasInfiniteNv(p)) applyNightVision(p, true);
            }
        }, 100L, 100L);
    }

    private boolean hasInfiniteNv(Player p) {
        PotionEffect e = p.getPotionEffect(PotionEffectType.NIGHT_VISION);
        return e != null && e.getDuration() == PotionEffect.INFINITE_DURATION;
    }

    private void applyNightVision(Player p, boolean on) {
        if (on) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        } else {
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    // ------------------------------------------------------------ GUI

    public void open(Player p) {
        SettingsHolder h = new SettingsHolder();
        Inventory inv = Bukkit.createInventory(h, 27, MM.deserialize("<dark_gray>Settings"));
        h.setInventory(inv);
        render(inv, p);
        p.openInventory(inv);
    }

    private ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(l(name));
            meta.lore(java.util.Arrays.stream(lore).map(SettingsGui::l).toList());
        });
        return it;
    }

    private void render(Inventory inv, Player p) {
        inv.clear();
        PlayerData d = data.get(p.getUniqueId());
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.displayName(Component.empty()));
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        inv.setItem(NIGHT_VISION, named(Material.GOLDEN_CARROT, "<aqua><bold>Night Vision",
                "<gray>See perfectly in the dark, forever.", "<gray>Status: " + (d.nightVision ? "<green>ON" : "<red>OFF"),
                "", "<yellow>Click to toggle"));
        inv.setItem(MOB_SPAWN, named(Material.ZOMBIE_HEAD, "<red><bold>Mob Spawning",
                "<gray>Turn it off and no monsters spawn", "<gray>around you (when you are alone there).",
                "<gray>Status: " + (d.noMobSpawn ? "<green>MOBS OFF" : "<red>MOBS ON"),
                "", "<yellow>Click to toggle"));
        inv.setItem(NIGHT_VISION_STATUS, new ItemStack(d.nightVision ? Material.LIME_DYE : Material.GRAY_DYE));
        inv.setItem(MOB_SPAWN_STATUS, new ItemStack(d.noMobSpawn ? Material.LIME_DYE : Material.GRAY_DYE));
        inv.getItem(NIGHT_VISION_STATUS).editMeta(m -> m.displayName(l(d.nightVision ? "<green><bold>ON" : "<red><bold>OFF")));
        inv.getItem(MOB_SPAWN_STATUS).editMeta(m -> m.displayName(l(d.noMobSpawn ? "<green><bold>MOBS OFF" : "<red><bold>MOBS ON")));
    }

    public void handleClick(Player p, Inventory inv, int slot) {
        PlayerData d = data.get(p.getUniqueId());
        if (slot == NIGHT_VISION || slot == NIGHT_VISION_STATUS) {
            d.nightVision = !d.nightVision;
            applyNightVision(p, d.nightVision);
            p.sendMessage(MM.deserialize(d.nightVision ? "<green>Night vision is now <bold>ON</bold>." : "<yellow>Night vision is now OFF."));
        } else if (slot == MOB_SPAWN || slot == MOB_SPAWN_STATUS) {
            d.noMobSpawn = !d.noMobSpawn;
            p.sendMessage(MM.deserialize(d.noMobSpawn
                    ? "<green>Monsters will no longer spawn around you."
                    : "<yellow>Monsters can spawn around you again."));
        } else {
            return;
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        render(inv, p);
    }

    // ------------------------------------------------------------ effects

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (data.get(p.getUniqueId()).nightVision) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> applyNightVision(p, true), 10L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (data.get(p.getUniqueId()).nightVision) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> applyNightVision(p, true), 5L);
        }
    }

    /** No monster spawns when EVERY player within 96 blocks has mob spawning turned off. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Enemy)) return;
        if (!BLOCKED.contains(e.getSpawnReason())) return;
        Collection<Player> near = e.getLocation().getWorld().getNearbyPlayers(e.getLocation(), 96);
        if (near.isEmpty()) return;
        for (Player p : near) {
            if (!data.get(p.getUniqueId()).noMobSpawn) return;
        }
        e.setCancelled(true);
    }
}
