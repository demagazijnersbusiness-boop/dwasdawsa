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
import java.util.List;

/** Homes Dashboard: white bed + red dye = not set. Click the dye to set it, the bed turns red and the dye becomes an arrow (teleport). */
public final class HomesGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int BANNER = 10;
    private static final int INFO = 19;
    private static final int[] BEDS = {12, 13, 14, 15, 16};
    private static final int[] DYES = {21, 22, 23, 24, 25};

    private final HomeManager homes;

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

    public void open(Player p) {
        HomesHolder h = new HomesHolder();
        Inventory inv = Bukkit.createInventory(h, 36, MM.deserialize("<dark_gray>Homes Dashboard"));
        h.setInventory(inv);
        render(p, inv);
        p.openInventory(inv);
    }

    private void render(Player p, Inventory inv) {
        inv.clear();
        inv.setItem(BANNER, item(Material.BLUE_BANNER, "<aqua><bold>Homes",
                List.of("<gray>You can set up to <white>5 <gray>homes.")));
        inv.setItem(INFO, item(Material.GRAY_DYE, "<gray><bold>How it works",
                List.of("<gray>Red dye » <white>set a home here",
                        "<gray>Arrow » <white>teleport to the home",
                        "<gray>Right-click arrow » <white>delete the home")));

        for (int i = 0; i < HomeManager.MAX; i++) {
            HomeManager.Home hm = homes.get(p.getUniqueId(), i);
            int n = i + 1;
            if (hm == null) {
                inv.setItem(BEDS[i], item(Material.WHITE_BED, "<white><bold>Home " + n, List.of("<gray>Not set yet")));
                inv.setItem(DYES[i], item(Material.RED_DYE, "<red><bold>Set Home " + n,
                        List.of("<gray>Click to set this home", "<gray>at your current location")));
            } else {
                String where = hm.world() + ": " + Math.round(hm.x()) + ", " + Math.round(hm.y()) + ", " + Math.round(hm.z());
                inv.setItem(BEDS[i], item(Material.RED_BED, "<red><bold>Home " + n,
                        List.of("<gray>" + where)));
                inv.setItem(DYES[i], item(Material.ARROW, "<green><bold>Teleport to Home " + n,
                        List.of("<yellow>Left-click <gray>» teleport", "<yellow>Right-click <gray>» delete home")));
            }
        }
    }

    public void handleClick(Player p, int slot, boolean right, Inventory inv) {
        for (int i = 0; i < HomeManager.MAX; i++) {
            if (slot != DYES[i]) continue;
            int n = i + 1;
            HomeManager.Home hm = homes.get(p.getUniqueId(), i);

            if (hm == null) {
                homes.set(p.getUniqueId(), i, p.getLocation());
                p.sendMessage(MM.deserialize("<green>Home <n> set!", Placeholder.unparsed("n", String.valueOf(n))));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                render(p, inv);
            } else if (right) {
                homes.delete(p.getUniqueId(), i);
                p.sendMessage(MM.deserialize("<yellow>Home <n> deleted.", Placeholder.unparsed("n", String.valueOf(n))));
                render(p, inv);
            } else {
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
    }
}
