package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Wayback Totem: if you die with one in your inventory, you keep your inventory and armor (the totem is used up). */
public final class TotemListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final EmeraldManager emerald;

    public TotemListener(EmeraldManager emerald) {
        this.emerald = emerald;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        PlayerInventory inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < inv.getSize(); i++) {
            if (emerald.isTotem(inv.getItem(i))) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return;

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
        p.sendMessage(MM.deserialize("<blue><bold>Wayback Totem</bold> <gray>» <white>Your inventory and armor are back with you!"));
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
    }
}
