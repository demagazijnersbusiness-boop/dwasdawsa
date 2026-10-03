package net.capybarasmp.scoreboard;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SpawnerHolder implements InventoryHolder {
    private final String key;
    private Inventory inventory;

    public SpawnerHolder(String key) {
        this.key = key;
    }

    public String key() { return key; }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
