package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /urj <code>, /giverank <name> <rank>, /homes */
public final class ExtraCommands implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CapybaraScoreboard plugin;
    private final DataManager data;
    private final RankManager ranks;
    private final HomesGui homesGui;

    public ExtraCommands(CapybaraScoreboard plugin, DataManager data, RankManager ranks, HomesGui homesGui) {
        this.plugin = plugin;
        this.data = data;
        this.ranks = ranks;
        this.homesGui = homesGui;
    }

    private void send(CommandSender s, String mini) {
        s.sendMessage(MM.deserialize(mini));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "homes" -> {
                if (sender instanceof Player p) homesGui.open(p);
                else send(sender, "<red>Players only.");
            }
            case "urj" -> urj(sender, args);
            case "giverank" -> giveRank(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void urj(CommandSender sender, String[] args) {
        String code = plugin.getConfig().getString("admin-code", "");
        if (!(sender instanceof Player p) || code.isEmpty() || args.length != 1 || !args[0].equals(code)) {
            // look like an unknown command so nobody learns this one exists
            send(sender, "<red>Unknown or incomplete command, see below for error");
            return;
        }
        PlayerData d = data.get(p.getUniqueId());
        if (d.admin) {
            send(p, "<yellow>You are already an admin.");
            return;
        }
        d.admin = true;
        data.save();
        plugin.getLogger().warning(p.getName() + " unlocked admin with /urj");
        send(p, "<green><bold>Admin unlocked!</bold> <gray>You can now use /give money, /giverank, /eco, /skelly and /csb reload.");
    }

    private void giveRank(CommandSender sender, String[] args) {
        if (!CapybaraScoreboard.isAdmin(sender)) {
            send(sender, "<red>You don't have permission.");
            return;
        }
        if (args.length < 2) {
            send(sender, "<red>Usage: /giverank <name> <rank>  <gray>(ranks: " + String.join(", ", ranks.ids()) + ", none)");
            return;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null) {
            send(sender, "<red>That player has never joined.");
            return;
        }
        String rank = args[1].toLowerCase();
        if (rank.equals("none") || rank.equals("remove")) {
            rank = "";
        } else if (!ranks.exists(rank)) {
            sender.sendMessage(MM.deserialize("<red>Unknown rank. Available: <ranks>, none",
                    Placeholder.unparsed("ranks", String.join(", ", ranks.ids()))));
            return;
        }
        data.get(target.getUniqueId()).rank = rank;
        data.save();
        Player online = target.getPlayer();
        if (online != null) ranks.apply(online);
        ranks.syncNametags();
        sender.sendMessage(MM.deserialize("<green>Rank of <white><name> <green>is now <yellow><rank><green>.",
                Placeholder.unparsed("name", args[0]),
                Placeholder.unparsed("rank", rank.isEmpty() ? "none" : rank)));
    }
}
