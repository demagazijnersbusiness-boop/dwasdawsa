package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Homes Dashboard: white bed + red dye = not set. Click the dye to set it, the bed turns red and the dye becomes
 * an arrow (teleport). Right-click the arrow twice to delete (with an "Are you sure?" step).
 * Normal players have 5 homes, admins have 100 (5 per page).
 */
public final class HomesGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PER_PAGE = 5;
    private static final int BANNER = 10;
    private static final int INFO = 19;
    private static final int[] BEDS = {12, 13, 14, 15, 16};
    private static final int[] DYES = {21, 22, 23, 24, 25};
    private static final int PREV = 30;
    private static final int PAGE_INFO = 31;
    private static final int NEXT = 32;

    private final HomeManager homes;
    /** player -> home index that is waiting for "are you sure?" */
    private final Map<UUID, Integer> pendingDelete = new HashMap<>();

    public HomesGui(HomeManager homes) {
        this.homes = homes;
    }

    private static Component l(String s) {
        return MM.deserialize("<!italic>" + s);
    }

    private ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(l(name));
            List<Component> lines = new ArrayList<>();
            for (String s : lore) lines.add(l(s));
            meta.lore(lines);
        });
        return it;
    }

    private int pages(Player p) {
        return Math.max(1, (homes.limit(p) + PER_PAGE - 1) / PER_PAGE);
    }

    public void open(Player p) {
        openPage(p, 0);
    }

    public void openPage(Player p, int page) {
        page = Math.max(0, Math.min(page, pages(p) - 1));
        pendingDelete.remove(p.getUniqueId());
        HomesHolder h = new HomesHolder(page);
        Inventory inv = Bukkit.createInventory(h, 36, MM.deserialize("<dark_gray>Homes Dashboard"
                + (pages(p) > 1 ? " <gray>(" + (page + 1) + "/" + pages(p) + ")" : "")));
        h.setInventory(inv);
        render(p, inv, page);
        p.openInventory(inv);
    }

    private void render(Player p, Inventory inv, int page) {
        inv.clear();
        int limit = homes.limit(p);
        Integer pending = pendingDelete.get(p.getUniqueId());

        inv.setItem(BANNER, item(Material.BLUE_BANNER, "<aqua><bold>Homes",
                List.of("<gray>You can set up to <white>" + limit + " <gray>homes.")));
        inv.setItem(INFO, item(Material.GRAY_DYE, "<gray><bold>How it works",
                List.of("<gray>Red dye » <white>set a home here",
                        "<gray>Arrow » <white>teleport to the home",
                        "<gray>Right-click arrow » <white>delete the home")));

        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= limit) break;
            HomeManager.Home hm = homes.get(p.getUniqueId(), index);
            int n = index + 1;
            if (hm == null) {
                inv.setItem(BEDS[i], item(Material.WHITE_BED, "<white><bold>Home " + n, List.of("<gray>Not set yet")));
                inv.setItem(DYES[i], item(Material.RED_DYE, "<red><bold>Set Home " + n,
                        List.of("<gray>Click to set this home", "<gray>at your current location")));
            } else {
                String where = hm.world() + ": " + Math.round(hm.x()) + ", " + Math.round(hm.y()) + ", " + Math.round(hm.z());
                inv.setItem(BEDS[i], item(Material.RED_BED, "<red><bold>Home " + n, List.of("<gray>" + where)));
                if (pending != null && pending == index) {
                    inv.setItem(DYES[i], item(Material.TNT, "<red><bold>Are you sure?",
                            List.of("<gray>Home " + n + " will be deleted for good.",
                                    "<yellow>Right-click again <gray>» yes, delete",
                                    "<yellow>Left-click <gray>» cancel")));
                } else {
                    inv.setItem(DYES[i], item(Material.ARROW, "<green><bold>Teleport to Home " + n,
                            List.of("<yellow>Left-click <gray>» teleport", "<yellow>Right-click <gray>» delete home")));
                }
            }
        }

        int total = pages(p);
        if (total > 1) {
            if (page > 0) {
                inv.setItem(PREV, item(Material.RED_DYE, "<red><bold>Back Page", List.of("<gray>Go to page " + page)));
            }
            inv.setItem(PAGE_INFO, item(Material.PAPER, "<yellow><bold>Page " + (page + 1) + " / " + total,
                    List.of("<gray>Homes " + (page * PER_PAGE + 1) + " - " + Math.min(limit, (page + 1) * PER_PAGE))));
            if (page < total - 1) {
                inv.setItem(NEXT, item(Material.ARROW, "<yellow><bold>Next Page", List.of("<gray>Go to page " + (page + 2))));
            }
        }
    }

    public void handleClick(Player p, int slot, boolean right, Inventory inv) {
        if (!(inv.getHolder() instanceof HomesHolder holder)) return;
        int page = holder.page();
        Integer pending = pendingDelete.get(p.getUniqueId());

        if (pages(p) > 1) {
            if (slot == PREV && page > 0) {
                openPage(p, page - 1);
                return;
            }
            if (slot == NEXT && page < pages(p) - 1) {
                openPage(p, page + 1);
                return;
            }
        }

        for (int i = 0; i < PER_PAGE; i++) {
            if (slot != DYES[i]) continue;
            int index = page * PER_PAGE + i;
            if (index >= homes.limit(p)) return;
            int n = index + 1;
            HomeManager.Home hm = homes.get(p.getUniqueId(), index);

            if (hm == null) {
                pendingDelete.remove(p.getUniqueId());
                homes.set(p.getUniqueId(), index, p.getLocation());
                p.sendMessage(MM.deserialize("<green>Home <n> set!", Placeholder.unparsed("n", String.valueOf(n))));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                render(p, inv, page);
            } else if (right) {
                if (pending != null && pending == index) {
                    pendingDelete.remove(p.getUniqueId());
                    homes.delete(p.getUniqueId(), index);
                    p.sendMessage(MM.deserialize("<yellow>Home <n> deleted.", Placeholder.unparsed("n", String.valueOf(n))));
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                } else {
                    pendingDelete.put(p.getUniqueId(), index);
                    p.sendMessage(MM.deserialize("<red>Are you sure you want to delete Home <n>? <gray>Right-click the TNT again to confirm, left-click to cancel.",
                            Placeholder.unparsed("n", String.valueOf(n))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                }
                render(p, inv, page);
            } else if (pending != null && pending == index) {
                // left-click on the "are you sure?" button = cancel
                pendingDelete.remove(p.getUniqueId());
                p.sendMessage(MM.deserialize("<green>Cancelled, Home <n> was kept.", Placeholder.unparsed("n", String.valueOf(n))));
                render(p, inv, page);
            } else {
                pendingDelete.remove(p.getUniqueId());
                Location loc = hm.toLocation();
                if (loc == null) {
                    p.sendMessage(MM.deserialize("<red>That world is not loaded."));
                    return;
                }
                Bukkit.getScheduler().runTask(CapybaraScoreboard.get(), () -> {
                    p.closeInventory();
                    p.teleport(loc);
                    p.sendMessage(MM.deserialize("<green>Teleported to Home <n>.", Placeholder.unparsed("n", String.valueOf(n))));
                });
            }
            return;
        }

        // clicked something else: forget a half-finished delete
        if (pending != null) {
            pendingDelete.remove(p.getUniqueId());
            render(p, inv, page);
        }
    }
}
