package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Wayback Totem: if you die with one in your inventory, you keep your inventory and armor (the totem is used up). */
public final class TotemListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final EmeraldManager emerald;
    private final CapybaraScoreboard plugin = CapybaraScoreboard.getPlugin(CapybaraScoreboard.class);
    /** Safety copy of the inventory taken when a totem is used, restored on respawn if the items went missing. */
    private final Map<UUID, ItemStack[]> backup = new HashMap<>();

    public TotemListener(EmeraldManager emerald) {
        this.emerald = emerald;
    }

    /**
     * Real Wayback Totems carry our tag. As a fallback we also accept a totem whose lore is ours
     * (lore can't be edited by players, unlike names), so totems made by an older version still work.
     */
    private boolean isWayback(ItemStack it) {
        if (emerald.isTotem(it)) return true;
        if (it == null || it.getType() != Material.TOTEM_OF_UNDYING || !it.hasItemMeta()) return false;
        List<Component> lore = it.getItemMeta().lore();
        if (lore == null) return false;
        for (Component c : lore) {
            if (PlainTextComponentSerializer.plainText().serialize(c).contains("come back to you")) return true;
        }
        return false;
    }

    // HIGHEST: run after other plugins so nothing turns keepInventory back off
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        PlayerInventory inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < inv.getSize(); i++) {
            if (isWayback(inv.getItem(i))) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            // help find problems: a plain totem is in the inventory but was not recognised
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                if (it != null && it.getType() == Material.TOTEM_OF_UNDYING) {
                    plugin.getLogger().warning(p.getName() + " died with a Totem of Undying that is not a Wayback Totem (slot " + i + ").");
                    break;
                }
            }
            return;
        }

        ItemStack totem = inv.getItem(slot);
        if (totem.getAmount() > 1) {
            totem.setAmount(totem.getAmount() - 1);
            inv.setItem(slot, totem);
        } else {
            inv.setItem(slot, null);
        }
        // safety copy (after the totem was used up) in case another plugin or the server still clears the inventory
        ItemStack[] copy = inv.getContents();
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] != null) copy[i] = copy[i].clone();
        }
        backup.put(p.getUniqueId(), copy);
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        plugin.getLogger().info(p.getName() + " used a Wayback Totem (kept inventory).");
        p.sendMessage(MM.deserialize("<blue><bold>Wayback Totem</bold> <gray>» <white>Your inventory and armor are back with you!"));
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
    }

    private static boolean isEmpty(PlayerInventory inv) {
        for (ItemStack it : inv.getContents()) {
            if (it != null && !it.getType().isAir()) return false;
        }
        return true;
    }

    /** Gives the safety copy back, but only if the inventory really is empty (so nothing is ever duplicated). */
    private void restore(Player p) {
        ItemStack[] copy = backup.remove(p.getUniqueId());
        if (copy == null) return;
        if (!isEmpty(p.getInventory())) return;
        p.getInventory().setContents(copy);
        plugin.getLogger().warning(p.getName() + "'s inventory was empty after a Wayback Totem death, so it was restored from the backup. "
                + "Another plugin is probably clearing inventories on death.");
        p.sendMessage(MM.deserialize("<blue><bold>Wayback Totem</bold> <gray>» <white>Your inventory was restored."));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!backup.containsKey(p.getUniqueId())) return;
        // one tick later, when the player is fully respawned
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> restore(p), 2L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (backup.containsKey(p.getUniqueId()) && !p.isDead()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> restore(p), 2L);
        }
    }
}
