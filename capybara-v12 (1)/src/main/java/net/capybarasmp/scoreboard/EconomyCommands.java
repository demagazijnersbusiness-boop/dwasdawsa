package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EconomyCommands implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String BAD_AMOUNT = "<red>Invalid amount. Examples: 500, 1.5K, 2M, 3B, 4T, 5QA";

    private final DataManager data;
    private final Economy economy;
    private final ShopGui gui;
    private final SellService sell;

    public EconomyCommands(DataManager data, Economy economy, ShopGui gui, SellService sell) {
        this.data = data;
        this.economy = economy;
        this.gui = gui;
        this.sell = sell;
    }

    private void send(CommandSender s, String mini, TagResolver... r) {
        s.sendMessage(MM.deserialize(mini, r));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "money" -> money(sender, args);
            case "pay" -> pay(sender, args);
            case "baltop" -> baltop(sender);
            case "eco" -> eco(sender, args);
            case "sell" -> sell(sender, args);
            case "shop" -> {
                if (sender instanceof Player p) {
                    if (args.length > 0) gui.openSearchResults(p, String.join(" ", args));
                    else gui.openMenu(p);
                }
                else send(sender, "<red>Players only.");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void money(CommandSender sender, String[] args) {
        UUID id;
        String name;
        if (args.length >= 1) {
            OfflinePlayer t = Bukkit.getPlayerExact(args[0]);
            if (t == null) t = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (t == null) {
                send(sender, "<red>That player has never joined.");
                return;
            }
            id = t.getUniqueId();
            name = args[0];
        } else if (sender instanceof Player p) {
            id = p.getUniqueId();
            name = "You";
        } else {
            send(sender, "<red>Usage: /money <player>");
            return;
        }
        send(sender, "<yellow><name> <gray>- Balance: <green>$<amt>",
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("amt", MoneyUtil.format(economy.balance(id))));
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            send(sender, "<red>Players only.");
            return;
        }
        if (args.length < 2) {
            send(sender, "<red>Usage: /pay <player> <amount>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "<red>That player is not online.");
            return;
        }
        if (target.equals(p)) {
            send(sender, "<red>You can't pay yourself.");
            return;
        }
        BigInteger amt;
        try {
            amt = MoneyUtil.parse(args[1]);
        } catch (NumberFormatException e) {
            send(sender, BAD_AMOUNT);
            return;
        }
        if (amt.signum() <= 0) {
            send(sender, BAD_AMOUNT);
            return;
        }
        if (!economy.take(p.getUniqueId(), amt)) {
            send(sender, "<red>You don't have enough money.");
            return;
        }
        economy.add(target.getUniqueId(), amt);
        String f = MoneyUtil.format(amt);
        send(p, "<green>You paid <yellow>$<amt> <green>to <white><name><green>.",
                Placeholder.unparsed("amt", f), Placeholder.unparsed("name", target.getName()));
        send(target, "<green>You received <yellow>$<amt> <green>from <white><name><green>.",
                Placeholder.unparsed("amt", f), Placeholder.unparsed("name", p.getName()));
    }

    private void baltop(CommandSender sender) {
        List<Map.Entry<UUID, PlayerData>> list = new ArrayList<>(data.all().entrySet());
        list.sort((a, b) -> b.getValue().money.compareTo(a.getValue().money));
        send(sender, "<gradient:#1e90ff:#00e5ff><bold>Top Balances</bold></gradient>");
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            Map.Entry<UUID, PlayerData> e = list.get(i);
            String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            send(sender, "<gray>#<rank> <white><name> <dark_gray>- <green>$<amt>",
                    Placeholder.unparsed("rank", String.valueOf(i + 1)),
                    Placeholder.unparsed("name", name == null ? "Unknown" : name),
                    Placeholder.unparsed("amt", MoneyUtil.format(e.getValue().money)));
        }
    }

    private void eco(CommandSender sender, String[] args) {
        if (!CapybaraScoreboard.isAdmin(sender)) {
            send(sender, "<red>You don't have permission.");
            return;
        }
        if (args.length < 3) {
            send(sender, "<red>Usage: /eco <give|take|set> <player> <amount>");
            return;
        }
        OfflinePlayer t = Bukkit.getPlayerExact(args[1]);
        if (t == null) t = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (t == null) {
            send(sender, "<red>That player has never joined.");
            return;
        }
        BigInteger amt;
        try {
            amt = MoneyUtil.parse(args[2]);
        } catch (NumberFormatException e) {
            send(sender, BAD_AMOUNT);
            return;
        }
        if (amt.signum() < 0) {
            send(sender, BAD_AMOUNT);
            return;
        }
        UUID id = t.getUniqueId();
        switch (args[0].toLowerCase()) {
            case "give" -> economy.add(id, amt);
            case "take" -> economy.set(id, economy.balance(id).subtract(amt));
            case "set" -> economy.set(id, amt);
            default -> {
                send(sender, "<red>Use give, take or set.");
                return;
            }
        }
        data.save();
        send(sender, "<green><name> now has <yellow>$<amt><green>.",
                Placeholder.unparsed("name", args[1]),
                Placeholder.unparsed("amt", MoneyUtil.format(economy.balance(id))));
    }

    private void sell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            send(sender, "<red>Players only.");
            return;
        }
        if (args.length == 0) {
            gui.openSell(p);
            return;
        }
        PlayerInventory inv = p.getInventory();
        switch (args[0].toLowerCase()) {
            case "hand" -> {
                ItemStack[] arr = {inv.getItemInMainHand()};
                SellService.Result r = sell.sell(arr);
                if (arr[0] == null) inv.setItemInMainHand(new ItemStack(Material.AIR));
                sell.payout(p, r);
            }
            case "all" -> {
                ItemStack[] storage = inv.getStorageContents();
                SellService.Result r = sell.sell(storage);
                inv.setStorageContents(storage);
                sell.payout(p, r);
            }
            default -> send(sender, "<red>Usage: /sell [hand|all]");
        }
    }
}
