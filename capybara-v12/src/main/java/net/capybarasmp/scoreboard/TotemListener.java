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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

/** Wayback Totem: if you die with one in your inventory, you keep your inventory and armor (the totem is used up). */
public final class TotemListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final EmeraldManager emerald;
    private final CapybaraScoreboard plugin = CapybaraScoreboard.getPlugin(CapybaraScoreboard.class);

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
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        plugin.getLogger().info(p.getName() + " used a Wayback Totem (kept inventory).");
        p.sendMessage(MM.deserialize("<blue><bold>Wayback Totem</bold> <gray>» <white>Your inventory and armor are back with you!"));
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
    }
}
