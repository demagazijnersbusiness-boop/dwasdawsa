package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class CommandHandler implements CommandExecutor, TabCompleter {

    private final CapybaraScoreboard plugin;
    private final DataManager data;
    private final BoardManager boards;

    public CommandHandler(CapybaraScoreboard plugin, DataManager data, BoardManager boards) {
        this.plugin = plugin;
        this.data = data;
        this.boards = boards;
    }

    private void msg(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Component.text(text, color));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("capybarascoreboard")) {
            if (!CapybaraScoreboard.isAdmin(sender)) {
                msg(sender, "You don't have permission.", NamedTextColor.RED);
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadConfig();
                plugin.getShopManager().load();
                plugin.getSpawnerManager().start();
                plugin.getSpawnerManager().refreshHolograms();
                boards.reload();
                plugin.getRankManager().applyAll();
                msg(sender, "CapybaraScoreboard reloaded.", NamedTextColor.GREEN);
            } else {
                msg(sender, "Usage: /capybarascoreboard reload", NamedTextColor.RED);
            }
            return true;
        }

        // /lives
        if (args.length == 0) {
            if (sender instanceof Player p) {
                msg(sender, "You have " + data.get(p.getUniqueId()).lives + " lives.", NamedTextColor.RED);
            } else {
                msg(sender, "Usage: /lives <player> <set|add|remove> <amount>", NamedTextColor.RED);
            }
            return true;
        }

        if (!CapybaraScoreboard.isAdmin(sender)) {
            msg(sender, "You don't have permission.", NamedTextColor.RED);
            return true;
        }
        if (args.length < 3) {
            msg(sender, "Usage: /lives <player> <set|add|remove> <amount>", NamedTextColor.RED);
            return true;
        }

        OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null) {
            msg(sender, "That player has never joined.", NamedTextColor.RED);
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            msg(sender, "Amount must be a number.", NamedTextColor.RED);
            return true;
        }

        PlayerData d = data.get(target.getUniqueId());
        int max = plugin.getConfig().getInt("max-lives", 20);
        switch (args[1].toLowerCase()) {
            case "set" -> d.lives = amount;
            case "add" -> d.lives += amount;
            case "remove" -> d.lives -= amount;
            default -> {
                msg(sender, "Use set, add or remove.", NamedTextColor.RED);
                return true;
            }
        }
        d.lives = Math.max(0, Math.min(max, d.lives));

        Player online = target.getPlayer();
        if (online != null) boards.update(online);
        data.save();
        msg(sender, args[0] + " now has " + d.lives + " lives.", NamedTextColor.GREEN);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!CapybaraScoreboard.isAdmin(sender)) return out;
        if (cmd.getName().equalsIgnoreCase("capybarascoreboard")) {
            if (args.length == 1) out.add("reload");
        } else if (args.length == 1) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 2) {
            out.addAll(List.of("set", "add", "remove"));
        }
        return out;
    }
}
