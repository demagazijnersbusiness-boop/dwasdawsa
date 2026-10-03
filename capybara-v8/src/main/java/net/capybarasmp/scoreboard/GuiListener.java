package net.capybarasmp.scoreboard;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class GuiListener implements Listener {

    private final ShopGui gui;
    private final SellService sell;
    private final HomesGui homes;
    private final SpawnerGui spawnerGui;
    private final EmeraldGui emeraldGui;
    private final ScoreboardGui scoreboardGui;

    public GuiListener(ShopGui gui, SellService sell, HomesGui homes, SpawnerGui spawnerGui,
                       EmeraldGui emeraldGui, ScoreboardGui scoreboardGui) {
        this.spawnerGui = spawnerGui;
        this.emeraldGui = emeraldGui;
        this.scoreboardGui = scoreboardGui;
        this.gui = gui;
        this.sell = sell;
        this.homes = homes;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (holder instanceof ShopHolder sh) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;
            if (sh.isMenu()) {
                gui.handleMenuClick(p, e.getSlot(), top.getSize());
            } else {
                gui.handleListClick(p, sh, e.getSlot(), e.isShiftClick(), e.isRightClick());
            }
        } else if (holder instanceof HomesHolder) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;
            homes.handleClick(p, e.getSlot(), e.isRightClick(), top);
        } else if (holder instanceof SpawnerHolder sph) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;
            spawnerGui.handleClick(p, top, sph, e.getSlot(), e.isRightClick());
        } else if (holder instanceof EmeraldHolder) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;
            emeraldGui.handleClick(p, top, e.getSlot());
        } else if (holder instanceof ScoreboardHolder) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;
            scoreboardGui.handleClick(p, top, e.getSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof ShopHolder || holder instanceof HomesHolder || holder instanceof SpawnerHolder
                || holder instanceof EmeraldHolder || holder instanceof ScoreboardHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (!(inv.getHolder() instanceof SellHolder)) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        ItemStack[] contents = inv.getContents();
        SellService.Result result = sell.sell(contents);
        inv.clear();

        // give back everything that could not be sold
        for (ItemStack s : contents) {
            if (s == null || s.getType().isAir()) continue;
            p.getInventory().addItem(s).values()
                    .forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        }
        sell.payout(p, result);
    }
}
