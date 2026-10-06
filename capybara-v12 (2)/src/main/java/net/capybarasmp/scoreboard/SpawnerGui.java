package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The storage window of a Skelly Spawner: all stored drops, click to sell. */
public final class SpawnerGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int STORAGE_SLOTS = 45;
    private static final int SELL_ALL = 48;
    private static final int INFO = 49;

    private final CapybaraScoreboard plugin;
    private final SpawnerManager spawners;

    public SpawnerGui(CapybaraScoreboard plugin, SpawnerManager spawners) {
        this.plugin = plugin;
        this.spawners = spawners;
        // keep open windows live while loot is generated
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpen, 20L, 20L);
    }

    private static Component l(String s, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... r) {
        return MM.deserialize("<!italic>" + s, r);
    }

    private ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(l(name));
            if (lore.length > 0) {
                List<Component> lines = new ArrayList<>();
                for (String s : lore) lines.add(l(s));
                meta.lore(lines);
            }
        });
        return it;
    }

    public void open(Player p, SpawnerManager.Spawner sp) {
        SpawnerHolder h = new SpawnerHolder(sp.key());
        Inventory inv = Bukkit.createInventory(h, 54, MM.deserialize("<dark_gray><n> Skeleton Spawners",
                Placeholder.unparsed("n", String.valueOf(sp.stack))));
        h.setInventory(inv);
        render(inv, sp);
        p.openInventory(inv);
    }

    private void render(Inventory inv, SpawnerManager.Spawner sp) {
        inv.clear();

        // storage: bones first, then arrows, one stack of 64 per slot
        int slot = 0;
        Material[] types = {Material.BONE, Material.ARROW};
        long[] counts = {sp.bones, sp.arrows};
        for (int t = 0; t < types.length; t++) {
            long left = counts[t];
            while (left > 0 && slot < STORAGE_SLOTS) {
                int a = (int) Math.min(64, left);
                ItemStack it = new ItemStack(types[t], a);
                it.editMeta(m -> m.lore(List.of(
                        l("<yellow>Left-click <gray>» sell this stack"),
                        l("<yellow>Right-click <gray>» take this stack"))));
                inv.setItem(slot++, it);
                left -= a;
            }
        }

        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = STORAGE_SLOTS; i < 54; i++) inv.setItem(i, filler);

        String worth = MoneyUtil.format(spawners.value(sp));
        inv.setItem(SELL_ALL, named(Material.GOLD_INGOT, "<green><bold>Sell All",
                "<gray>Sell everything stored here", "<gray>Worth: <green>$" + worth));

        long cap = spawners.capacity(sp);
        long pct = cap <= 0 ? 0 : Math.min(100, sp.total() * 100 / cap);
        ItemStack info = named(Material.SPAWNER, "<white><bold>" + sp.stack + " Skeleton Spawners",
                "<green>" + MoneyUtil.format(BigInteger.valueOf(sp.bones)) + " <white>Bones",
                "<green>" + MoneyUtil.format(BigInteger.valueOf(sp.arrows)) + " <white>Arrows",
                "<gray>(" + pct + "% full)",
                "<dark_gray>Right-click the spawner with another",
                "<dark_gray>Skelly Spawner to stack it.");
        inv.setItem(INFO, info);
    }

    private void refreshOpen() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof SpawnerHolder h)) continue;
            SpawnerManager.Spawner sp = spawners.getByKey(h.key());
            if (sp == null) {
                p.closeInventory();
                continue;
            }
            render(p.getOpenInventory().getTopInventory(), sp);
        }
    }

    public void handleClick(Player p, Inventory inv, SpawnerHolder h, int slot, boolean right) {
        SpawnerManager.Spawner sp = spawners.getByKey(h.key());
        if (sp == null) {
            p.closeInventory();
            return;
        }

        if (slot == SELL_ALL) {
            BigInteger got = spawners.sellAll(sp, p.getUniqueId());
            if (got.signum() > 0) {
                p.sendMessage(MM.deserialize("<green>Sold everything for <yellow>$<amt><green>.",
                        Placeholder.unparsed("amt", MoneyUtil.format(got))));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            } else {
                p.sendMessage(MM.deserialize("<red>Nothing stored yet."));
            }
            render(inv, sp);
            return;
        }

        if (slot < 0 || slot >= STORAGE_SLOTS) return;
        ItemStack clicked = inv.getItem(slot);
        if (clicked == null || clicked.getType().isAir()) return;
        Material type = clicked.getType();
        int amount = clicked.getAmount();

        if (right) {
            long took = spawners.take(sp, type, amount);
            if (took > 0) {
                Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(type, (int) took));
                long back = 0;
                for (ItemStack s : left.values()) back += s.getAmount();
                if (back > 0) {
                    // give back what did not fit
                    if (type == Material.BONE) sp.bones += back; else sp.arrows += back;
                    p.sendMessage(MM.deserialize("<red>Your inventory is full!"));
                }
            }
        } else {
            BigInteger got = spawners.sell(sp, type, amount, p.getUniqueId());
            if (got.signum() > 0) {
                p.sendMessage(MM.deserialize("<green>Sold <white><n>x <item> <green>for <yellow>$<amt><green>.",
                        Placeholder.unparsed("n", String.valueOf(amount)),
                        Placeholder.unparsed("item", ShopManager.pretty(type)),
                        Placeholder.unparsed("amt", MoneyUtil.format(got))));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
        }
        render(inv, sp);
    }
}
