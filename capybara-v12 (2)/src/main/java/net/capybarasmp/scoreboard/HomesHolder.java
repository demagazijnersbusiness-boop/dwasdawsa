package net.capybarasmp.scoreboard;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class HomesHolder implements InventoryHolder {
    private final int page;
    private Inventory inventory;

    public HomesHolder(int page) {
        this.page = page;
    }

    public int page() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
