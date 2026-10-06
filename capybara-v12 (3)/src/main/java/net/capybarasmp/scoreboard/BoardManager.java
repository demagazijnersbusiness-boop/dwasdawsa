package net.capybarasmp.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BoardManager {

    /** All lines in display order (top to bottom). Players can hide lines with /scoreboard. */
    public static final String[] KEYS = {"kills", "deaths", "money", "emerald", "playtime", "lives", "team"};
    private static final String[] DEFAULTS = {
            "<red>\u2694 <white>Kills <red><value>",
            "<gold>\u2620 <white>Deaths <gold><value>",
            "<green>$ <white>Money <green><value>",
            "<#17dd62>\u25c6 <white>Emerald <#17dd62><value>",
            "<yellow>\u23f1 <white>Playtime <yellow><value>",
            "<red>\u2764 <white>Lives <red><value>",
            "<aqua>\u2605 <white>Team <aqua><value>"
    };
    // Invisible, unique entry names (color codes render as nothing)
    private static final String[] ENTRIES = {"\u00a70", "\u00a71", "\u00a72", "\u00a73", "\u00a74", "\u00a75", "\u00a76"};

    private final CapybaraScoreboard plugin;
    private final DataManager data;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private TeamManager teams;

    public BoardManager(CapybaraScoreboard plugin, DataManager data) {
        this.plugin = plugin;
        this.data = data;
    }

    public void setTeams(TeamManager teams) {
        this.teams = teams;
    }

    public void show(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Component title = mm.deserialize(plugin.getConfig().getString("scoreboard.title", "<bold><gradient:#ff2b2b:#8b4513>CAPYBARA SMP</gradient></bold>"));
        Objective obj = board.registerNewObjective("capybara", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());

        for (int i = 0; i < KEYS.length; i++) {
            Team team = board.registerNewTeam("line" + i);
            team.addEntry(ENTRIES[i]);
        }

        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        update(player);
    }

    public Scoreboard boardOf(UUID id) {
        return boards.get(id);
    }

    public void remove(Player player) {
        boards.remove(player.getUniqueId());
    }

    public void reload() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            show(p);
        }
    }

    public void update(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;
        Objective obj = board.getObjective("capybara");
        if (obj == null) return;
        PlayerData d = data.get(player.getUniqueId());

        // title shows MAINTENANCE while the server is in maintenance mode
        Component baseTitle = mm.deserialize(plugin.getConfig().getString("scoreboard.title", "<bold><gradient:#ff2b2b:#8b4513>CAPYBARA SMP</gradient></bold>"));
        Component wantTitle = plugin.isMaintenance()
                ? baseTitle.append(mm.deserialize(" <red><bold>MAINTENANCE</bold>"))
                : baseTitle;
        if (!wantTitle.equals(obj.displayName())) obj.displayName(wantTitle);

        // whole scoreboard on/off
        DisplaySlot want = d.boardHidden ? null : DisplaySlot.SIDEBAR;
        if (obj.getDisplaySlot() != want) {
            obj.setDisplaySlot(want);
        }

        String teamName = teams == null ? null : teams.nameOf(player.getUniqueId());

        String[] values = {
                String.valueOf(d.kills),
                String.valueOf(d.deaths),
                MoneyUtil.format(d.money),
                MoneyUtil.format(BigInteger.valueOf(d.emerald)),
                formatPlaytime(d.playtimeSeconds),
                String.valueOf(d.lives),
                teamName == null ? "None" : teamName
        };

        for (int i = 0; i < KEYS.length; i++) {
            Score score = obj.getScore(ENTRIES[i]);
            if (d.hiddenLines.contains(KEYS[i])) {
                if (score.isScoreSet()) board.resetScores(ENTRIES[i]);
                continue;
            }
            int value = KEYS.length - i;
            if (!score.isScoreSet() || score.getScore() != value) {
                score.setScore(value);
            }
            Team team = board.getTeam("line" + i);
            if (team == null) continue;
            String template = plugin.getConfig().getString("scoreboard.lines." + KEYS[i], DEFAULTS[i]);
            Component line = mm.deserialize(template, Placeholder.unparsed("value", values[i]));
            if (!line.equals(team.prefix())) {
                team.prefix(line);
            }
        }
    }

    public String formatPlaytime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0 || plugin.getConfig().getBoolean("scoreboard.always-show-hours", true)) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
