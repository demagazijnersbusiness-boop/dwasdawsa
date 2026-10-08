package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** /scoreboard - every player chooses which lines (or the whole board) they want to see. */
public final class ScoreboardGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String[] NAMES = {"Kills", "Deaths", "Money", "Emerald", "Playtime", "Lives", "Team"};
    private static final Material[] ICONS = {
            Material.IRON_SWORD, Material.SKELETON_SKULL, Material.GOLD_INGOT,
            Material.EMERALD, Material.CLOCK, Material.TOTEM_OF_UNDYING, Material.WHITE_BANNER};
    private static final int ICON_START = 10;     // slots 10-16
    private static final int STATUS_START = 19;   // slots 19-25
    private static final int BOARD_TOGGLE = 31;
    private static final int RESET = 35;

    private final DataManager data;
    private final BoardManager boards;

    public ScoreboardGui(DataManager data, BoardManager boards) {
        this.data = data;
        this.boards = boards;
    }

    private static Component l(String s) {
        return MM.deserialize("<!italic>" + s);
    }

    public void open(Player p) {
        ScoreboardHolder h = new ScoreboardHolder();
        Inventory inv = Bukkit.createInventory(h, 36, MM.deserialize("<dark_gray>Edit your scoreboard"));
        h.setInventory(inv);
        render(inv, p);
        p.openInventory(inv);
    }

    private void render(Inventory inv, Player p) {
        inv.clear();
        PlayerData d = data.get(p.getUniqueId());
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.displayName(Component.empty()));
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        for (int i = 0; i < BoardManager.KEYS.length; i++) {
            boolean shown = !d.hiddenLines.contains(BoardManager.KEYS[i]);
            final String name = NAMES[i];

            ItemStack icon = new ItemStack(ICONS[i]);
            icon.editMeta(m -> {
                m.displayName(l((shown ? "<green>" : "<red>") + "<bold>" + name));
                m.lore(List.of(l("<gray>Click to " + (shown ? "hide" : "show") + " this line")));
            });
            inv.setItem(ICON_START + i, icon);

            ItemStack status = new ItemStack(shown ? Material.LIME_DYE : Material.GRAY_DYE);
            status.editMeta(m -> {
                m.displayName(l(shown ? "<green><bold>SHOWN" : "<red><bold>HIDDEN"));
                m.lore(List.of(l("<gray>Click to toggle <white>" + name)));
            });
            inv.setItem(STATUS_START + i, status);
        }

        ItemStack lever = new ItemStack(Material.LEVER);
        lever.editMeta(m -> {
            m.displayName(l(d.boardHidden ? "<red><bold>Scoreboard: OFF" : "<green><bold>Scoreboard: ON"));
            m.lore(List.of(l("<gray>Click to turn your whole scoreboard " + (d.boardHidden ? "on" : "off"))));
        });
        inv.setItem(BOARD_TOGGLE, lever);

        ItemStack reset = new ItemStack(Material.BARRIER);
        reset.editMeta(m -> {
            m.displayName(l("<red><bold>Reset"));
            m.lore(List.of(l("<gray>Show everything again")));
        });
        inv.setItem(RESET, reset);
    }

    public void handleClick(Player p, Inventory inv, int slot) {
        PlayerData d = data.get(p.getUniqueId());
        int line = -1;
        if (slot >= ICON_START && slot < ICON_START + BoardManager.KEYS.length) line = slot - ICON_START;
        else if (slot >= STATUS_START && slot < STATUS_START + BoardManager.KEYS.length) line = slot - STATUS_START;

        if (line >= 0) {
            String key = BoardManager.KEYS[line];
            if (!d.hiddenLines.remove(key)) d.hiddenLines.add(key);
        } else if (slot == BOARD_TOGGLE) {
            d.boardHidden = !d.boardHidden;
        } else if (slot == RESET) {
            d.hiddenLines.clear();
            d.boardHidden = false;
        } else {
            return;
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        boards.update(p);
        render(inv, p);
    }
}
