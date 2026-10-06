package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** /afk, /emeraldshop, /scoreboard, /emerald, /getstar, /upgradepickaxe */
public final class EmeraldCommands implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final EmeraldManager emerald;
    private final SpawnerManager spawners;
    private final EmeraldGui shopGui;
    private final ScoreboardGui boardGui;

    public EmeraldCommands(EmeraldManager emerald, SpawnerManager spawners, EmeraldGui shopGui, ScoreboardGui boardGui) {
        this.emerald = emerald;
        this.spawners = spawners;
        this.shopGui = shopGui;
        this.boardGui = boardGui;
    }

    private void send(CommandSender s, String mini) {
        s.sendMessage(MM.deserialize(mini));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "afk" -> {
                if (sender instanceof Player p) emerald.toggleAfk(p);
                else send(sender, "<red>Players only.");
            }
            case "emeraldshop" -> {
                if (sender instanceof Player p) shopGui.open(p);
                else send(sender, "<red>Players only.");
            }
            case "scoreboard" -> {
                if (sender instanceof Player p) boardGui.open(p);
                else send(sender, "<red>Players only.");
            }
            case "emerald" -> emerald(sender, args);
            case "getstar" -> {
                if (sender instanceof Player p) getStar(p, args);
                else send(sender, "<red>Players only.");
            }
            case "upgradepickaxe" -> {
                if (sender instanceof Player p) upgradePickaxe(p);
                else send(sender, "<red>Players only.");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ /getstar + /upgradepickaxe

    /** Counts the Upgrade Stars in the player's inventory. */
    private int countStars(PlayerInventory inv) {
        int n = 0;
        for (ItemStack it : inv.getStorageContents()) {
            if (emerald.isStar(it)) n += it.getAmount();
        }
        return n;
    }

    /** Removes up to 'amount' Upgrade Stars from the inventory. */
    private void takeStars(PlayerInventory inv, int amount) {
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack it = contents[i];
            if (!emerald.isStar(it)) continue;
            int take = Math.min(amount, it.getAmount());
            amount -= take;
            if (take >= it.getAmount()) contents[i] = null;
            else it.setAmount(it.getAmount() - take);
        }
        inv.setStorageContents(contents);
    }

    /** /getstar [amount]: trades Skelly Spawners from your inventory for Upgrade Stars (1 spawner = 1 star). */
    private void getStar(Player p, String[] args) {
        int want = 1;
        if (args.length > 0) {
            try {
                want = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                send(p, "<red>Usage: /getstar [amount]");
                return;
            }
        }
        if (want < 1) {
            send(p, "<red>Amount must be at least 1.");
            return;
        }
        int perSpawner = emerald.starsPerSpawner();
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int spawnersUsed = 0;
        for (int i = 0; i < contents.length && spawnersUsed < want; i++) {
            ItemStack it = contents[i];
            if (!spawners.isSkellyItem(it)) continue;
            int take = Math.min(want - spawnersUsed, it.getAmount());
            spawnersUsed += take;
            if (take >= it.getAmount()) contents[i] = null;
            else it.setAmount(it.getAmount() - take);
        }
        if (spawnersUsed == 0) {
            send(p, "<red>You need a <white>Skelly Spawner <red>in your inventory. <gray>1 Skelly Spawner = 1 Upgrade Star.");
            return;
        }
        inv.setStorageContents(contents);
        int stars = spawnersUsed * perSpawner;
        for (ItemStack left : inv.addItem(emerald.createStar(stars)).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        p.sendMessage(MM.deserialize("<green>Traded <white><s> Skelly Spawner(s) <green>for <white><n> <#17dd62>Upgrade Star(s)<green>.",
                Placeholder.unparsed("s", String.valueOf(spawnersUsed)),
                Placeholder.unparsed("n", String.valueOf(stars))));
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
    }

    /** /upgradepickaxe: hold your Emerald Pickaxe, pay Upgrade Stars, raise its level (max 3). */
    private void upgradePickaxe(Player p) {
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!emerald.isPickaxe(tool)) {
            send(p, "<red>Hold your <#17dd62>Emerald Pickaxe <red>in your main hand first.");
            return;
        }
        int level = emerald.pickLevel(tool);
        if (level >= EmeraldManager.MAX_LEVEL) {
            send(p, "<yellow>Your Emerald Pickaxe is already at the <white>max level <yellow>(" + EmeraldManager.MAX_LEVEL + "/" + EmeraldManager.MAX_LEVEL + ").");
            return;
        }
        int cost = emerald.starsPerUpgrade();
        int have = countStars(p.getInventory());
        if (have < cost) {
            p.sendMessage(MM.deserialize("<red>You need <white><c> <#17dd62>Upgrade Stars <red>(you have <white><h><red>). Trade spawners with <white>/getstar<red>.",
                    Placeholder.unparsed("c", String.valueOf(cost)),
                    Placeholder.unparsed("h", String.valueOf(have))));
            return;
        }
        takeStars(p.getInventory(), cost);
        // re-read the item: takeStars rewrote the storage contents
        ItemStack hand = p.getInventory().getItemInMainHand();
        emerald.setPickLevel(hand, level + 1);
        p.getInventory().setItemInMainHand(hand);
        String what = switch (level + 1) {
            case 1 -> "mines <white>9 blocks <green>(3x3)";
            case 2 -> "mines <white>2 layers <green>of 9 blocks";
            default -> "mines <white>3 layers <green>of 9 blocks";
        };
        p.sendMessage(MM.deserialize("<green>Emerald Pickaxe upgraded to level <white><l>/<m><green>! It now " + what + "<green>.",
                Placeholder.unparsed("l", String.valueOf(level + 1)),
                Placeholder.unparsed("m", String.valueOf(EmeraldManager.MAX_LEVEL))));
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
    }

    private void emerald(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                send(sender, "<red>Usage: /emerald give <player> <amount>");
                return;
            }
            send(sender, "<#17dd62><c>: <white><n>".replace("<c>", emerald.currency())
                    .replace("<n>", MoneyUtil.format(java.math.BigInteger.valueOf(emerald.balance(p.getUniqueId())))));
            return;
        }
        if (!CapybaraScoreboard.isAdmin(sender)) {
            send(sender, "<red>You don't have permission.");
            return;
        }
        if (args.length < 3 || !(args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
            send(sender, "<red>Usage: /emerald <give|take> <player> <amount>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            send(sender, "<red>Unknown player (they must have joined before).");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            send(sender, "<red>Invalid amount.");
            return;
        }
        if (amount <= 0) {
            send(sender, "<red>Amount must be positive.");
            return;
        }
        boolean give = args[0].equalsIgnoreCase("give");
        if (give) emerald.add(target.getUniqueId(), amount);
        else emerald.take(target.getUniqueId(), amount);
        sender.sendMessage(MM.deserialize("<green>Done. <white><p> <green>now has <white><n> <c><green>.",
                Placeholder.unparsed("p", String.valueOf(target.getName())),
                Placeholder.unparsed("n", String.valueOf(emerald.balance(target.getUniqueId()))),
                Placeholder.unparsed("c", emerald.currency())));
    }
}
