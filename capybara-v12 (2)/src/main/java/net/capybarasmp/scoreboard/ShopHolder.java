package net.capybarasmp.scoreboard;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** category == null && query == null means the main shop menu. */
public final class ShopHolder implements InventoryHolder {
    private final String category;
    private final String query;
    private final int page;
    private final int offset;
    private Inventory inventory;

    public ShopHolder(String category, String query, int page, int offset) {
        this.category = category;
        this.query = query;
        this.page = page;
        this.offset = offset;
    }

    public String category() { return category; }
    public String query() { return query; }
    public int page() { return page; }
    public int offset() { return offset; }
    public boolean isMenu() { return category == null && query == null; }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
