package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class RankManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CapybaraScoreboard plugin;
    private final DataManager data;
    private final BoardManager boards;

    public RankManager(CapybaraScoreboard plugin, DataManager data, BoardManager boards) {
        this.plugin = plugin;
        this.data = data;
        this.boards = boards;
    }

    private record Def(String prefix, String color) {}

    /** Built-in ranks: they work even when the server still has an old config.yml. */
    private static final Map<String, Def> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("owner", new Def("<dark_red><bold>[OWNER]</bold> ", "dark_red"));
        DEFAULTS.put("mod", new Def("<blue><bold>[MOD]</bold> ", "blue"));
        DEFAULTS.put("media", new Def("<white><font:capybara:media>\uE000</font> </white>", "light_purple"));
    }

    /** owner, mod, media first, then any extra rank from config.yml (for example dev). */
    public Set<String> ids() {
        Set<String> out = new LinkedHashSet<>(DEFAULTS.keySet());
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("ranks");
        if (s != null) out.addAll(s.getKeys(false));
        return out;
    }

    public boolean exists(String id) {
        return id != null && !id.isEmpty() && ids().contains(id);
    }

    public Component prefix(String id) {
        Def d = DEFAULTS.get(id);
        return MM.deserialize(plugin.getConfig().getString("ranks." + id + ".prefix", d == null ? "" : d.prefix()));
    }

    public NamedTextColor color(String id) {
        Def d = DEFAULTS.get(id);
        String name = plugin.getConfig().getString("ranks." + id + ".color", d == null ? "white" : d.color()).toLowerCase();
        NamedTextColor c = NamedTextColor.NAMES.value(name);
        return c == null ? NamedTextColor.WHITE : c;
    }

    /** Tab list name. */
    public void apply(Player p) {
        String r = data.get(p.getUniqueId()).rank;
        if (!exists(r)) {
            p.playerListName(null);
            return;
        }
        p.playerListName(prefix(r).append(Component.text(p.getName(), color(r))));
    }

    /** Name tag above heads: adds players to a team with the rank prefix on every viewer's scoreboard. */
    public void syncNametags() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard b = boards.boardOf(viewer.getUniqueId());
            if (b == null) continue;
            for (String id : ids()) {
                String teamName = ("rk_" + id);
                if (teamName.length() > 16) teamName = teamName.substring(0, 16);
                Team t = b.getTeam(teamName);
                if (t == null) t = b.registerNewTeam(teamName);
                t.prefix(prefix(id));
                t.color(color(id));
                for (Player target : Bukkit.getOnlinePlayers()) {
                    boolean has = id.equals(data.get(target.getUniqueId()).rank);
                    if (has) {
                        if (!t.hasEntry(target.getName())) t.addEntry(target.getName());
                    } else if (t.hasEntry(target.getName())) {
                        t.removeEntry(target.getName());
                    }
                }
            }
        }
    }

    public void applyAll() {
        for (Player p : Bukkit.getOnlinePlayers()) apply(p);
        syncNametags();
        refreshTab();
    }

    /** Tab list header: server name + player count. */
    public void refreshTab() {
        int n = Bukkit.getOnlinePlayers().size();
        Component header = MM.deserialize(plugin.getConfig().getString("scoreboard.title", "CAPYBARA SMP"))
                .append(Component.newline())
                .append(MM.deserialize("<white><n> Players", Placeholder.unparsed("n", String.valueOf(n))));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendPlayerListHeaderAndFooter(header, Component.empty());
        }
    }
}
