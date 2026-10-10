package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Admin: /set emeralds <player> <amount>  and  /set money <player> <amount> */
public final class SetCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final EmeraldManager emerald;
    private final Economy economy;

    public SetCommand(EmeraldManager emerald, Economy economy) {
        this.emerald = emerald;
        this.economy = economy;
    }

    private void send(CommandSender s, String mini) {
        s.sendMessage(MM.deserialize(mini));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CapybaraScoreboard.isAdmin(sender)) {
            send(sender, "<red>You don't have permission.");
            return true;
        }
        if (args.length < 3) {
            send(sender, "<red>Usage: /set <emeralds|money> <player> <amount>");
            return true;
        }
        String what = args[0].toLowerCase(Locale.ROOT);
        boolean isEmerald = what.equals("emeralds") || what.equals("emerald");
        boolean isMoney = what.equals("money") || what.equals("balance");
        if (!isEmerald && !isMoney) {
            send(sender, "<red>Usage: /set <emeralds|money> <player> <amount>");
            return true;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            send(sender, "<red>That player has never joined.");
            return true;
        }
        BigInteger amount;
        try {
            amount = MoneyUtil.parse(args[2]);
        } catch (NumberFormatException e) {
            send(sender, "<red>Invalid amount. Examples: 500, 1.5K, 2M");
            return true;
        }
        if (amount.signum() < 0) {
            send(sender, "<red>Amount can't be negative.");
            return true;
        }

        if (isEmerald) {
            long value = amount.min(BigInteger.valueOf(Long.MAX_VALUE / 2)).longValue();
            emerald.set(target.getUniqueId(), value);
            sender.sendMessage(MM.deserialize("<green>Set the <#17dd62><c> <green>of <white><p> <green>to <white><n><green>.",
                    Placeholder.unparsed("c", emerald.currency()),
                    Placeholder.unparsed("p", String.valueOf(target.getName())),
                    Placeholder.unparsed("n", MoneyUtil.format(BigInteger.valueOf(value)))));
            if (target.getPlayer() != null && !target.getPlayer().equals(sender)) {
                target.getPlayer().sendMessage(MM.deserialize("<green>Your <#17dd62><c> <green>is now <white><n><green>.",
                        Placeholder.unparsed("c", emerald.currency()),
                        Placeholder.unparsed("n", MoneyUtil.format(BigInteger.valueOf(value)))));
            }
        } else {
            economy.set(target.getUniqueId(), amount);
            sender.sendMessage(MM.deserialize("<green>Set the money of <white><p> <green>to <yellow>$<n><green>.",
                    Placeholder.unparsed("p", String.valueOf(target.getName())),
                    Placeholder.unparsed("n", MoneyUtil.format(amount))));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!CapybaraScoreboard.isAdmin(sender)) return out;
        if (args.length == 1) {
            out.add("emeralds");
            out.add("money");
        } else if (args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
