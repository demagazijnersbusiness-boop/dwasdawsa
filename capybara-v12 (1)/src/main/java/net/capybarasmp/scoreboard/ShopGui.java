package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PER_PAGE = 45;

    private final ShopManager shop;
    private final Economy economy;
    private final SpawnerManager spawners;
    private final Set<UUID> awaitingSearch = ConcurrentHashMap.newKeySet();

    private final SkellyLimit limit;

    public ShopGui(ShopManager shop, Economy economy, SpawnerManager spawners, SkellyLimit limit) {
        this.limit = limit;
        this.shop = shop;
        this.economy = economy;
        this.spawners = spawners;
    }

    private static Component l(String s, TagResolver... r) {
        return MM.deserialize("<!italic>" + s, r);
    }

    private ItemStack named(Material m, String mini, String... loreLines) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(l(mini));
            if (loreLines.length > 0) {
                List<Component> lore = new ArrayList<>();
                for (String s : loreLines) lore.add(l(s));
                meta.lore(lore);
            }
        });
        return it;
    }

    // ---------------------------------------------------------------- search

    public boolean isAwaitingSearch(Player p) {
        return awaitingSearch.contains(p.getUniqueId());
    }

    public void stopAwaitingSearch(Player p) {
        awaitingSearch.remove(p.getUniqueId());
    }

    private SignSearch signSearch;

    public void setSignSearch(SignSearch signSearch) {
        this.signSearch = signSearch;
    }

    private void promptSearch(Player p) {
        Bukkit.getScheduler().runTask(CapybaraScoreboard.get(), () -> p.closeInventory());
        // a sign pops up on your screen: type the item name on it
        Bukkit.getScheduler().runTaskLater(CapybaraScoreboard.get(), () -> {
            boolean ok = signSearch != null && signSearch.open(p, text -> {
                if (text.isBlank()) openMenu(p);
                else openSearchResults(p, text.trim());
            });
            if (!ok) {
                // no room for a sign here: fall back to typing in chat
                awaitingSearch.add(p.getUniqueId());
                p.sendMessage(MM.deserialize("<yellow>Type the name of the item you want to find in chat. <gray>(type <white>cancel<gray> to stop)"));
            }
        }, 3L);
    }

    public void openSearchResults(Player p, String query) {
        List<ShopManager.ShopItem> results = shop.search(query);
        if (results.isEmpty()) {
            p.sendMessage(MM.deserialize("<red>No items found for '<q>'.", Placeholder.unparsed("q", query)));
            openMenu(p);
            return;
        }
        openList(p, "Search: " + query, null, query, results, 0);
    }

    // ---------------------------------------------------------------- main menu

    public void openMenu(Player p) {
        List<ShopManager.Category> list = new ArrayList<>(shop.categories());
        int rows = Math.max(2, Math.min(6, (list.size() + 8) / 9 + 1));
        int size = rows * 9;
        ShopHolder h = new ShopHolder(null, null, 0, 0);
        Inventory inv = Bukkit.createInventory(h, size, MM.deserialize("<dark_gray>Shop"));
        h.setInventory(inv);

        for (int i = 0; i < list.size() && i < size - 9; i++) {
            ShopManager.Category c = list.get(i);
            ItemStack icon = new ItemStack(c.icon());
            icon.editMeta(m -> {
                m.displayName(l("<yellow><bold>" + c.name()));
                m.lore(List.of(l("<gray>Click to open")));
            });
            inv.setItem(i, icon);
        }

        inv.setItem(size - 5, named(Material.COMPASS, "<aqua><bold>Search",
                "<gray>Click, then type an item name in chat"));
        ItemStack bal = named(Material.GOLD_INGOT, "<green><bold>Balance");
        String amount = MoneyUtil.format(economy.balance(p.getUniqueId()));
        bal.editMeta(m -> m.lore(List.of(l("<white>$<amt>", Placeholder.unparsed("amt", amount)))));
        inv.setItem(size - 1, bal);
        p.openInventory(inv);
    }

    public void handleMenuClick(Player p, int slot, int size) {
        if (slot == size - 5) {
            promptSearch(p);
            return;
        }
        List<ShopManager.Category> list = new ArrayList<>(shop.categories());
        if (slot < 0 || slot >= size - 9 || slot >= list.size()) return;
        openCategory(p, list.get(slot), 0);
    }

    // ---------------------------------------------------------------- item pages

    private int pagesOf(int total) {
        return Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
    }

    public void openCategory(Player p, ShopManager.Category c, int page) {
        openList(p, c.name(), c.id(), null, c.items(), page);
    }

    private void openList(Player p, String name, String categoryId, String query,
                          List<ShopManager.ShopItem> items, int page) {
        int pages = pagesOf(items.size());
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * PER_PAGE;
        int count = Math.max(0, Math.min(PER_PAGE, items.size() - from));

        int size;
        int offset;
        if (pages == 1 && items.size() <= 9) {
            size = 27;   // 3 rows: items in the middle row (like Donut SMP)
            offset = 9;
        } else if (pages > 1) {
            size = 54;
            offset = 0;
        } else {
            int rows = Math.max(2, Math.min(6, (count + 8) / 9 + 1));
            size = rows * 9;
            offset = 0;
        }

        Component title = MM.deserialize("<dark_gray>Shop - <n><pg>",
                Placeholder.unparsed("n", name),
                Placeholder.unparsed("pg", pages > 1 ? " (" + (page + 1) + "/" + pages + ")" : ""));
        ShopHolder h = new ShopHolder(categoryId, query, page, offset);
        Inventory inv = Bukkit.createInventory(h, size, title);
        h.setInventory(inv);

        for (int i = 0; i < count; i++) {
            inv.setItem(offset + i, display(items.get(from + i)));
        }

        int back = size - 9;
        if (page == 0) {
            inv.setItem(back, named(Material.RED_STAINED_GLASS_PANE, "<red><bold>Back", "<gray>Back to the shop menu"));
        } else {
            inv.setItem(back, named(Material.RED_DYE, "<red><bold>Back Page", "<gray>Go to the previous page"));
        }
        inv.setItem(back + 4, named(Material.COMPASS, "<aqua><bold>Search", "<gray>Click, then type an item name"));
        if (page < pages - 1) {
            inv.setItem(size - 1, named(Material.ARROW, "<yellow><bold>Next Page", "<gray>Go to page " + (page + 2)));
        } else {
            inv.setItem(size - 1, named(Material.ARROW, "<yellow><bold>Next Page", "<gray>This is the last page"));
        }
        p.openInventory(inv);
    }

    private ItemStack display(ShopManager.ShopItem si) {
        ItemStack it = si.skelly() ? spawners.createItem(1) : new ItemStack(si.material());
        List<Component> lore = new ArrayList<>();
        if (si.skelly()) {
            ItemMeta existing = it.getItemMeta();
            if (existing != null && existing.lore() != null) {
                lore.addAll(existing.lore());
                lore.add(Component.empty());
            }
        }
        String each = MoneyUtil.format(shop.priceOf(si));
        lore.add(l("<gray>Price: <green>$<p> <dark_gray>each", Placeholder.unparsed("p", each)));
        if (shop.multiplier() > 1) {
            lore.add(l("<red>Inflation: prices are x" + shop.multiplier() + " right now"));
        }
        lore.add(Component.empty());
        lore.add(l("<yellow>Left-click <gray>» Buy <white>1 <dark_gray>($<c>)", Placeholder.unparsed("c", each)));
        if (!si.skelly() && si.material().getMaxStackSize() > 1) {
            lore.add(l("<yellow>Right-click <gray>» Buy <white>16 <dark_gray>($<c>)",
                    Placeholder.unparsed("c", MoneyUtil.format(shop.priceOf(si).multiply(BigInteger.valueOf(16))))));
            lore.add(l("<yellow>Shift-click <gray>» Buy <white>64 <dark_gray>($<c>)",
                    Placeholder.unparsed("c", MoneyUtil.format(shop.priceOf(si).multiply(BigInteger.valueOf(64))))));
        }
        it.editMeta(m -> m.lore(lore));
        return it;
    }

    private List<ShopManager.ShopItem> itemsOf(ShopHolder h) {
        if (h.query() != null) return shop.search(h.query());
        ShopManager.Category c = shop.category(h.category());
        return c == null ? null : c.items();
    }

    private void reopen(Player p, ShopHolder h, int page) {
        if (h.query() != null) {
            openList(p, "Search: " + h.query(), null, h.query(), shop.search(h.query()), page);
        } else {
            ShopManager.Category c = shop.category(h.category());
            if (c != null) openCategory(p, c, page);
        }
    }

    public void handleListClick(Player p, ShopHolder h, int slot, boolean shift, boolean right) {
        List<ShopManager.ShopItem> items = itemsOf(h);
        if (items == null) return;
        int size = h.getInventory().getSize();
        int back = size - 9;
        int next = size - 1;
        int pages = pagesOf(items.size());

        if (slot == back) {
            if (h.page() > 0) reopen(p, h, h.page() - 1);
            else openMenu(p);
            return;
        }
        if (slot == back + 4) {
            promptSearch(p);
            return;
        }
        if (slot == next) {
            if (h.page() < pages - 1) reopen(p, h, h.page() + 1);
            else p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (slot >= back) return;

        int onPage = slot - h.offset();
        if (onPage < 0 || onPage >= PER_PAGE) return;
        int idx = h.page() * PER_PAGE + onPage;
        if (idx >= items.size()) return;

        ShopManager.ShopItem si = items.get(idx);
        int qty = shift ? 64 : right ? 16 : 1;
        if (si.skelly() || si.material().getMaxStackSize() == 1) qty = 1;
        buy(p, si, qty);
    }

    private void buy(Player p, ShopManager.ShopItem si, int qty) {
        if (si.skelly() && limit.remaining(p.getUniqueId()) <= 0) {
            p.sendMessage(MM.deserialize("<red>You can only buy <white><l> <red>Skelly Spawners per day. Try again in <white><t><red>.",
                    Placeholder.unparsed("l", String.valueOf(limit.limit())),
                    Placeholder.unparsed("t", EmeraldManager.duration(limit.waitMs(p.getUniqueId())))));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        BigInteger cost = shop.priceOf(si).multiply(BigInteger.valueOf(qty));
        if (!economy.take(p.getUniqueId(), cost)) {
            p.sendMessage(MM.deserialize("<red>Not enough money! You need <yellow>$<amt><red>.",
                    Placeholder.unparsed("amt", MoneyUtil.format(cost))));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        ItemStack give = si.skelly() ? spawners.createItem(qty) : new ItemStack(si.material(), qty);
        Map<Integer, ItemStack> left = p.getInventory().addItem(give);
        int leftover = 0;
        for (ItemStack s : left.values()) leftover += s.getAmount();
        int bought = qty - leftover;
        BigInteger paid = cost;
        if (leftover > 0) {
            BigInteger refund = shop.priceOf(si).multiply(BigInteger.valueOf(leftover));
            economy.add(p.getUniqueId(), refund);
            paid = cost.subtract(refund);
            p.sendMessage(MM.deserialize("<red>Your inventory is full!"));
        }
        if (bought > 0 && si.skelly()) limit.record(p.getUniqueId());
        if (bought > 0) {
            String name = si.skelly() ? "Skelly Spawner" : ShopManager.pretty(si.material());
            p.sendMessage(MM.deserialize("<green>Bought <white><n>x <item> <green>for <yellow>$<amt><green>.",
                    Placeholder.unparsed("n", String.valueOf(bought)),
                    Placeholder.unparsed("item", name),
                    Placeholder.unparsed("amt", MoneyUtil.format(paid))));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
    }

    // ---------------------------------------------------------------- sell window

    public void openSell(Player p) {
        SellHolder h = new SellHolder();
        Inventory inv = Bukkit.createInventory(h, 36, MM.deserialize("<dark_gray>Sell <gray>- close to sell"));
        h.setInventory(inv);
        p.openInventory(inv);
    }
}
