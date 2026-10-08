package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /hardcore <player> <on|off|revive> (admins): players can't turn hardcore off themselves. */
public final class HardcoreCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final DataManager data;

    public HardcoreCommand(DataManager data) {
        this.data = data;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CapybaraScoreboard.isAdmin(sender)) {
            sender.sendMessage(MM.deserialize("<red>You don't have permission."));
            return true;
        }
        Player t = args.length >= 2 ? Bukkit.getPlayerExact(args[0]) : null;
        if (t == null) {
            sender.sendMessage(MM.deserialize("<red>Usage: /hardcore <online player> <on|off|revive>"));
            return true;
        }
        PlayerData d = data.get(t.getUniqueId());
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> d.hardcore = true;
            case "off" -> {
                d.hardcore = false;
                d.hardcoreDead = false;
            }
            case "revive" -> {
                d.hardcoreDead = false;
                d.lives = Math.max(d.lives, 1);
                t.setGameMode(GameMode.SURVIVAL);
            }
            default -> {
                sender.sendMessage(MM.deserialize("<red>Use on, off or revive."));
                return true;
            }
        }
        data.save();
        sender.sendMessage(MM.deserialize("<green>Done for <white>" + t.getName() + "<green>."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!CapybaraScoreboard.isAdmin(sender)) return out;
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        else if (args.length == 2) out.addAll(List.of("on", "off", "revive"));
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
