package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /wipe <player> [inv|ender|all], /urj <code>, /giverank <name> <rank>, /givehomes <player> <amount>, /homes, /reset w.o.r. <user>, /reset w.o.p. <user> */
public final class ExtraCommands implements CommandExecutor, org.bukkit.command.TabCompleter, org.bukkit.event.Listener {

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
            case "givehomes" -> giveHomes(sender, args);
            case "reset" -> resetWor(sender, args);
            case "wipe" -> wipe(sender, args);
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

    /**
     * /wipe <player> [inv|ender|all]: clears a player's inventory and/or ender chest.
     * Online players are wiped right away. Offline players are wiped the next time they join
     * (the wipe is saved in data.yml, so it survives restarts). Console or admins only.
     */
    private void wipe(CommandSender sender, String[] args) {
        boolean allowed = sender instanceof org.bukkit.command.ConsoleCommandSender || CapybaraScoreboard.isAdmin(sender);
        if (!allowed) {
            send(sender, "<red>You don't have permission.");
            return;
        }
        if (args.length < 1 || args.length > 2) {
            send(sender, "<red>Usage: /wipe <player> [inv|ender|all] <gray>(default: all)");
            return;
        }
        String mode = args.length == 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "all";
        boolean inv = mode.equals("inv") || mode.equals("all");
        boolean ender = mode.equals("ender") || mode.equals("all");
        if (!inv && !ender) {
            send(sender, "<red>Usage: /wipe <player> [inv|ender|all]");
            return;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null) {
            send(sender, "<red>That player has never joined.");
            return;
        }
        String what = inv && ender ? "inventory and ender chest" : inv ? "inventory" : "ender chest";
        if (target instanceof Player online) {
            if (inv) online.getInventory().clear();
            if (ender) online.getEnderChest().clear();
            send(sender, "<green>Wiped the " + what + " of " + online.getName() + ".");
        } else {
            PlayerData d = data.get(target.getUniqueId());
            if (inv) d.pendingWipeInv = true;
            if (ender) d.pendingWipeEnder = true;
            data.save();
            send(sender, "<yellow>" + args[0] + " is offline. Their " + what + " will be wiped the next time they join.");
        }
        plugin.getLogger().warning(sender.getName() + " wiped the " + what + " of " + args[0] + " with /wipe.");
    }

    /** Applies a pending /wipe when the player joins. */
    @org.bukkit.event.EventHandler
    public void onJoinWipe(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = data.get(p.getUniqueId());
        if (!d.pendingWipeInv && !d.pendingWipeEnder) return;
        if (d.pendingWipeInv) p.getInventory().clear();
        if (d.pendingWipeEnder) p.getEnderChest().clear();
        d.pendingWipeInv = false;
        d.pendingWipeEnder = false;
        data.save();
    }

    /** /reset w.o.r. <user> makes a player admin, /reset w.o.p. <user> removes it. Only the console or an existing admin can use it. */
    private void resetWor(CommandSender sender, String[] args) {
        boolean allowed = sender instanceof org.bukkit.command.ConsoleCommandSender || CapybaraScoreboard.isAdmin(sender);
        if (!allowed) {
            send(sender, "<red>You don't have permission.");
            return;
        }
        boolean grant = args.length == 2 && args[0].equalsIgnoreCase("w.o.r.");
        boolean revoke = args.length == 2 && args[0].equalsIgnoreCase("w.o.p.");
        if (!grant && !revoke) {
            send(sender, "<red>Usage: /reset w.o.r. <user> <gray>(make admin)<red>, /reset w.o.p. <user> <gray>(remove admin)");
            return;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            send(sender, "<red>That player has never joined.");
            return;
        }
        PlayerData d = data.get(target.getUniqueId());
        if (revoke) {
            if (!d.admin) {
                send(sender, "<yellow>" + args[1] + " is not an admin.");
                return;
            }
            d.admin = false;
            data.save();
            plugin.getLogger().warning(sender.getName() + " removed admin from " + args[1] + " with /reset w.o.p.");
            send(sender, "<green>" + args[1] + " is no longer an admin.");
            if (target instanceof Player tp) send(tp, "<red>Your admin rights were removed.");
            return;
        }
        if (d.admin) {
            send(sender, "<yellow>" + args[1] + " is already an admin.");
            return;
        }
        d.admin = true;
        data.save();
        plugin.getLogger().warning(sender.getName() + " made " + args[1] + " an admin with /reset w.o.r.");
        send(sender, "<green>" + args[1] + " is now an admin.");
        if (target instanceof Player tp) send(tp, "<green><bold>You are now an admin.</bold>");
    }

    /** /givehomes <player> <amount>: gives extra home slots (negative amount takes them away). Console or admins only. */
    private void giveHomes(CommandSender sender, String[] args) {
        boolean allowed = sender instanceof org.bukkit.command.ConsoleCommandSender || CapybaraScoreboard.isAdmin(sender);
        if (!allowed) {
            send(sender, "<red>You don't have permission.");
            return;
        }
        if (args.length != 2) {
            send(sender, "<red>Usage: /givehomes <player> <amount> <gray>(negative amount removes homes)");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            send(sender, "<red>Amount must be a whole number.");
            return;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null) {
            send(sender, "<red>That player has never joined.");
            return;
        }
        HomeManager homes = plugin.getHomes();
        int extra = homes.addExtraHomes(target.getUniqueId(), amount);
        int total = Math.min(HomeManager.ADMIN_MAX, HomeManager.NORMAL_MAX + extra);
        plugin.getLogger().warning(sender.getName() + " changed the homes of " + args[0] + " by " + amount + " with /givehomes (now " + total + ").");
        sender.sendMessage(MM.deserialize("<green><name> <green>can now set up to <white><total> <green>homes.",
                Placeholder.unparsed("name", args[0]),
                Placeholder.unparsed("total", String.valueOf(total))));
        if (target instanceof Player online && !online.equals(sender)) {
            online.sendMessage(MM.deserialize("<green>You can now set up to <white><total> <green>homes. <gray>Open them with /homes.",
                    Placeholder.unparsed("total", String.valueOf(total))));
        }
    }

    private void giveRank(CommandSender sender, String[] args) {
        if (!CapybaraScoreboard.isAdmin(sender)) {
            send(sender, "<red>You don't have permission.");
            return;
        }
        if (args.length < 2) {
            send(sender, "<red>Usage: /giverank <name> <rank>  <gray>(ranks: " + String.join(", ", ranks.ids()) + " - use <white>none<gray> to remove)");
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
            sender.sendMessage(MM.deserialize("<red>Unknown rank. Available: <ranks> <gray>(use <white>none<gray> to remove)",
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

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("wipe") && CapybaraScoreboard.isAdmin(sender)) {
            String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
            if (args.length == 1) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (args.length == 2) {
                out.addAll(java.util.List.of("inv", "ender", "all"));
            }
            out.removeIf(x -> !x.toLowerCase().startsWith(last));
            return out;
        }
        if (cmd.getName().equalsIgnoreCase("givehomes") && (sender instanceof org.bukkit.command.ConsoleCommandSender || CapybaraScoreboard.isAdmin(sender))) {
            String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
            if (args.length == 1) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (args.length == 2) {
                out.addAll(java.util.List.of("1", "5", "10", "25"));
            }
            out.removeIf(x -> !x.toLowerCase().startsWith(last));
            return out;
        }
        if (!cmd.getName().equalsIgnoreCase("giverank") || !CapybaraScoreboard.isAdmin(sender)) return out;
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 2) {
            out.addAll(ranks.ids());
            out.add("none");
        }
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
