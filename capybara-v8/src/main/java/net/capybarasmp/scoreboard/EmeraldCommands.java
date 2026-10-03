package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /afk, /emeraldshop, /scoreboard, /emerald */
public final class EmeraldCommands implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final EmeraldManager emerald;
    private final EmeraldGui shopGui;
    private final ScoreboardGui boardGui;

    public EmeraldCommands(EmeraldManager emerald, EmeraldGui shopGui, ScoreboardGui boardGui) {
        this.emerald = emerald;
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
            default -> {
                return false;
            }
        }
        return true;
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
