package net.capybarasmp.scoreboard;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SettingsHolder implements InventoryHolder {
    private Inventory inventory;
    /** true = this is the "are you sure?" screen for hardcore mode. */
    private boolean confirm;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public boolean isConfirm() {
        return confirm;
    }

    public void setConfirm(boolean confirm) {
        this.confirm = confirm;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
