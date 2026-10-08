package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /team create | invite | accept | deny | leave | kick | transfer | disband | info | list */
public final class TeamCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final TeamManager teams;
    private final Economy economy;

    public TeamCommand(TeamManager teams, Economy economy) {
        this.teams = teams;
        this.economy = economy;
    }

    private void send(CommandSender s, String mini, TagResolver... r) {
        s.sendMessage(MM.deserialize(mini, r));
    }

    private static TagResolver n(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    private void tell(TeamManager.Team t, String mini, TagResolver... r) {
        for (UUID id : t.members()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) send(p, mini, r);
        }
    }

    private String nameOf(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() == null ? "Unknown" : op.getName();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) {
            send(sender, "<red>Players only.");
            return true;
        }
        if (a.length == 0) {
            help(p);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(p, a);
            case "invite" -> invite(p, a);
            case "accept", "join" -> accept(p, a);
            case "deny", "decline" -> deny(p, a);
            case "leave" -> leave(p);
            case "kick" -> kick(p, a);
            case "transfer" -> transfer(p, a);
            case "disband" -> disband(p, a);
            case "info" -> info(p, a);
            case "list" -> list(p);
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        send(p, "<gold><bold>Team commands</bold>");
        send(p, "<yellow>/team create <name> <gray>- costs <green>$<c>", n("c", MoneyUtil.format(teams.createCost())));
        send(p, "<yellow>/team invite <player> <gray>- invite someone (leader)");
        send(p, "<yellow>/team accept [team] <gray>- join a team you were invited to");
        send(p, "<yellow>/team deny [team] <gray>- decline an invite");
        send(p, "<yellow>/team leave <gray>- leave your team");
        send(p, "<yellow>/team kick <player> <gray>- remove a member (leader)");
        send(p, "<yellow>/team transfer <player> <gray>- make someone else the leader");
        send(p, "<yellow>/team disband confirm <gray>- delete your team (leader)");
        send(p, "<yellow>/team info [team] <gray>and <yellow>/team list");
    }

    // ------------------------------------------------------------ create

    private void create(Player p, String[] a) {
        if (teams.teamOf(p.getUniqueId()) != null) {
            send(p, "<red>You are already in a team. Leave it first with /team leave.");
            return;
        }
        if (a.length < 2) {
            send(p, "<red>Usage: /team create <name>");
            return;
        }
        String name = a[1];
        if (!TeamManager.validName(name)) {
            send(p, "<red>A team name must be 3-16 characters: letters, numbers and _ only.");
            return;
        }
        if (teams.find(name) != null) {
            send(p, "<red>A team with that name already exists.");
            return;
        }
        BigInteger cost = teams.createCost();
        if (cost.signum() > 0 && !economy.take(p.getUniqueId(), cost)) {
            send(p, "<red>You need <green>$<c> <red>to create a team.", n("c", MoneyUtil.format(cost)));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        teams.create(name, p.getUniqueId());
        send(p, "<green>Team <white><t> <green>created for <yellow>$<c><green>! Invite players with <white>/team invite <player><green>.",
                n("t", name), n("c", MoneyUtil.format(cost)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    // ------------------------------------------------------------ invite / accept / deny

    private void invite(Player p, String[] a) {
        TeamManager.Team t = teams.teamOf(p.getUniqueId());
        if (t == null) {
            send(p, "<red>You are not in a team.");
            return;
        }
        if (!t.isLeader(p.getUniqueId())) {
            send(p, "<red>Only the team leader can invite players.");
            return;
        }
        if (a.length < 2) {
            send(p, "<red>Usage: /team invite <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) {
            send(p, "<red>That player is not online.");
            return;
        }
        if (target.equals(p)) {
            send(p, "<red>You can't invite yourself.");
            return;
        }
        if (teams.teamOf(target.getUniqueId()) != null) {
            send(p, "<red><n> is already in a team.", n("n", target.getName()));
            return;
        }
        if (t.members().size() >= teams.maxMembers()) {
            send(p, "<red>Your team is full (<m> members).", n("m", String.valueOf(teams.maxMembers())));
            return;
        }
        teams.addInvite(target.getUniqueId(), t);
        send(p, "<green>Invited <white><n> <green>to your team.", n("n", target.getName()));
        send(target, "<gold><bold>TEAM INVITE</bold> <white><from> <gray>invited you to the team <aqua><t><gray>. "
                        + "<click:run_command:'/team accept <t>'><green><bold>[ACCEPT]</bold></click> "
                        + "<click:run_command:'/team deny <t>'><red><bold>[DENY]</bold></click>",
                n("from", p.getName()), Placeholder.parsed("t", t.name()));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
    }

    private TeamManager.Team pickInvite(Player p, String[] a) {
        List<String> keys = teams.invitesFor(p.getUniqueId());
        if (keys.isEmpty()) {
            send(p, "<red>You have no team invites.");
            return null;
        }
        if (a.length >= 2) {
            TeamManager.Team t = teams.find(a[1]);
            if (t == null || !keys.contains(t.key())) {
                send(p, "<red>You have no invite from that team.");
                return null;
            }
            return t;
        }
        if (keys.size() > 1) {
            send(p, "<yellow>You have several invites. Use <white>/team accept <team><yellow>.");
            return null;
        }
        return teams.find(keys.get(0));
    }

    private void accept(Player p, String[] a) {
        if (teams.teamOf(p.getUniqueId()) != null) {
            send(p, "<red>You are already in a team. Leave it first with /team leave.");
            return;
        }
        TeamManager.Team t = pickInvite(p, a);
        if (t == null) return;
        if (t.members().size() >= teams.maxMembers()) {
            send(p, "<red>That team is full.");
            return;
        }
        teams.addMember(t, p.getUniqueId());
        send(p, "<green>You joined the team <white><t><green>! You can't hurt your teammates.", n("t", t.name()));
        tell(t, "<green><n> joined the team!", n("n", p.getName()));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private void deny(Player p, String[] a) {
        TeamManager.Team t = pickInvite(p, a);
        if (t == null) return;
        teams.removeInvite(p.getUniqueId(), t);
        send(p, "<yellow>You declined the invite from <white><t><yellow>.", n("t", t.name()));
        Player leader = Bukkit.getPlayer(t.leader());
        if (leader != null) send(leader, "<yellow><n> declined your team invite.", n("n", p.getName()));
    }

    // ------------------------------------------------------------ leave / kick / transfer / disband

    private void leave(Player p) {
        TeamManager.Team t = teams.teamOf(p.getUniqueId());
        if (t == null) {
            send(p, "<red>You are not in a team.");
            return;
        }
        if (t.isLeader(p.getUniqueId())) {
            if (t.members().size() == 1) {
                send(p, "<yellow>You are the only member. Use <white>/team disband confirm <yellow>to delete the team.");
            } else {
                send(p, "<red>You are the leader. Use <white>/team transfer <player> <red>first, or <white>/team disband confirm<red>.");
            }
            return;
        }
        teams.removeMember(t, p.getUniqueId());
        send(p, "<yellow>You left the team <white><t><yellow>.", n("t", t.name()));
        tell(t, "<yellow><n> left the team.", n("n", p.getName()));
    }

    private void kick(Player p, String[] a) {
        TeamManager.Team t = teams.teamOf(p.getUniqueId());
        if (t == null || !t.isLeader(p.getUniqueId())) {
            send(p, "<red>Only the team leader can kick players.");
            return;
        }
        if (a.length < 2) {
            send(p, "<red>Usage: /team kick <player>");
            return;
        }
        UUID target = null;
        for (UUID id : t.members()) {
            if (nameOf(id).equalsIgnoreCase(a[1])) target = id;
        }
        if (target == null) {
            send(p, "<red>That player is not in your team.");
            return;
        }
        if (target.equals(p.getUniqueId())) {
            send(p, "<red>You can't kick yourself.");
            return;
        }
        String name = nameOf(target);
        teams.removeMember(t, target);
        send(p, "<yellow>Kicked <white><n> <yellow>from the team.", n("n", name));
        Player kicked = Bukkit.getPlayer(target);
        if (kicked != null) send(kicked, "<red>You were removed from the team <white><t><red>.", n("t", t.name()));
        tell(t, "<yellow><n> was removed from the team.", n("n", name));
    }

    private void transfer(Player p, String[] a) {
        TeamManager.Team t = teams.teamOf(p.getUniqueId());
        if (t == null || !t.isLeader(p.getUniqueId())) {
            send(p, "<red>Only the team leader can do that.");
            return;
        }
        if (a.length < 2) {
            send(p, "<red>Usage: /team transfer <player>");
            return;
        }
        UUID target = null;
        for (UUID id : t.members()) {
            if (nameOf(id).equalsIgnoreCase(a[1])) target = id;
        }
        if (target == null || target.equals(p.getUniqueId())) {
            send(p, "<red>Pick another member of your team.");
            return;
        }
        teams.transfer(t, target);
        tell(t, "<gold><n> is now the team leader.", n("n", nameOf(target)));
    }

    private void disband(Player p, String[] a) {
        TeamManager.Team t = teams.teamOf(p.getUniqueId());
        if (t == null || !t.isLeader(p.getUniqueId())) {
            send(p, "<red>Only the team leader can disband the team.");
            return;
        }
        if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) {
            send(p, "<yellow>This deletes the team <white><t><yellow> (no refund). Type <white>/team disband confirm <yellow>to continue.",
                    n("t", t.name()));
            return;
        }
        tell(t, "<red>The team <white><t><red> was disbanded.", n("t", t.name()));
        teams.disband(t);
    }

    // ------------------------------------------------------------ info / list

    private void info(Player p, String[] a) {
        TeamManager.Team t = a.length >= 2 ? teams.find(a[1]) : teams.teamOf(p.getUniqueId());
        if (t == null) {
            send(p, a.length >= 2 ? "<red>That team does not exist." : "<red>You are not in a team. Use /team info <team>.");
            return;
        }
        send(p, "<gold><bold>Team <t></bold> <gray>(<c>/<m> members)", n("t", t.name()),
                n("c", String.valueOf(t.members().size())), n("m", String.valueOf(teams.maxMembers())));
        for (UUID id : t.members()) {
            boolean online = Bukkit.getPlayer(id) != null;
            send(p, (online ? "<green>" : "<gray>") + "- <n>" + (t.isLeader(id) ? " <gold>(leader)" : ""), n("n", nameOf(id)));
        }
    }

    private void list(Player p) {
        List<TeamManager.Team> all = new ArrayList<>(teams.all());
        if (all.isEmpty()) {
            send(p, "<gray>There are no teams yet. Create one with /team create <name>.");
            return;
        }
        all.sort(Comparator.comparingInt((TeamManager.Team t) -> t.members().size()).reversed());
        send(p, "<gold><bold>Teams</bold>");
        for (int i = 0; i < Math.min(15, all.size()); i++) {
            TeamManager.Team t = all.get(i);
            send(p, "<yellow><t> <gray>- <c> members (leader <l>)", n("t", t.name()),
                    n("c", String.valueOf(t.members().size())), n("l", nameOf(t.leader())));
        }
    }

    // ------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player p)) return out;
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        if (a.length == 1) {
            out.addAll(List.of("create", "invite", "accept", "deny", "leave", "kick", "transfer", "disband", "info", "list"));
        } else if (a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "invite" -> Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
                case "accept", "join", "deny", "decline" -> {
                    for (String key : teams.invitesFor(p.getUniqueId())) {
                        TeamManager.Team t = teams.find(key);
                        if (t != null) out.add(t.name());
                    }
                }
                case "kick", "transfer" -> {
                    TeamManager.Team t = teams.teamOf(p.getUniqueId());
                    if (t != null) for (UUID id : t.members()) out.add(nameOf(id));
                }
                case "info" -> teams.all().forEach(t -> out.add(t.name()));
                case "disband" -> out.add("confirm");
                default -> { }
            }
        }
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
