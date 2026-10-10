package net.capybarasmp.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Player teams: a leader, members, invites. Teammates can't hurt each other. Saved in teams.yml. */
public final class TeamManager {

    public static final class Team {
        private final String name;
        private UUID leader;
        private final Set<UUID> members = new LinkedHashSet<>();

        Team(String name, UUID leader) {
            this.name = name;
            this.leader = leader;
            this.members.add(leader);
        }

        public String name() { return name; }
        public UUID leader() { return leader; }
        public Set<UUID> members() { return Collections.unmodifiableSet(members); }
        public boolean isLeader(UUID id) { return leader.equals(id); }
        public String key() { return name.toLowerCase(); }
    }

    private final CapybaraScoreboard plugin;
    private final BoardManager boards;
    private final File file;
    private final Map<String, Team> byName = new LinkedHashMap<>();
    private final Map<UUID, Team> byPlayer = new HashMap<>();
    // invitee -> (team key -> expires at)
    private final Map<UUID, Map<String, Long>> invites = new HashMap<>();

    public TeamManager(CapybaraScoreboard plugin, BoardManager boards) {
        this.plugin = plugin;
        this.boards = boards;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
    }

    // ------------------------------------------------------------ config

    public BigInteger createCost() {
        try {
            BigInteger b = MoneyUtil.parse(plugin.getConfig().getString("team.create-cost", "1M"));
            return b.signum() < 0 ? BigInteger.ZERO : b;
        } catch (NumberFormatException e) {
            return BigInteger.valueOf(1_000_000L);
        }
    }

    public int maxMembers() {
        return Math.max(2, plugin.getConfig().getInt("team.max-members", 10));
    }

    public long inviteSeconds() {
        return Math.max(10L, plugin.getConfig().getLong("team.invite-expire-seconds", 120L));
    }

    public boolean friendlyFire() {
        return plugin.getConfig().getBoolean("team.friendly-fire", false);
    }

    public static boolean validName(String s) {
        return s != null && s.matches("[A-Za-z0-9_]{3,16}");
    }

    // ------------------------------------------------------------ lookups

    public Team teamOf(UUID id) {
        return byPlayer.get(id);
    }

    public String nameOf(UUID id) {
        Team t = byPlayer.get(id);
        return t == null ? null : t.name();
    }

    public boolean sameTeam(UUID a, UUID b) {
        Team t = byPlayer.get(a);
        return t != null && t == byPlayer.get(b);
    }

    public Team find(String name) {
        return name == null ? null : byName.get(name.toLowerCase());
    }

    public Collection<Team> all() {
        return Collections.unmodifiableCollection(byName.values());
    }

    // ------------------------------------------------------------ changes

    public Team create(String name, UUID leader) {
        Team t = new Team(name, leader);
        byName.put(t.key(), t);
        byPlayer.put(leader, t);
        save();
        refresh(t.members, null);
        return t;
    }

    public void addMember(Team t, UUID id) {
        t.members.add(id);
        byPlayer.put(id, t);
        clearInvites(id);
        save();
        refresh(t.members, null);
    }

    public void removeMember(Team t, UUID id) {
        t.members.remove(id);
        byPlayer.remove(id);
        save();
        refresh(t.members, id);
    }

    public void disband(Team t) {
        List<UUID> old = new ArrayList<>(t.members);
        for (UUID id : old) byPlayer.remove(id);
        byName.remove(t.key());
        for (Map<String, Long> m : invites.values()) m.remove(t.key());
        save();
        refresh(old, null);
    }

    public void transfer(Team t, UUID newLeader) {
        t.leader = newLeader;
        save();
    }

    private void refresh(Collection<UUID> a, UUID extra) {
        List<UUID> all = new ArrayList<>(a);
        if (extra != null) all.add(extra);
        for (UUID id : all) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) boards.update(p);
        }
    }

    // ------------------------------------------------------------ invites

    public void addInvite(UUID invitee, Team t) {
        invites.computeIfAbsent(invitee, k -> new HashMap<>())
                .put(t.key(), System.currentTimeMillis() + inviteSeconds() * 1000L);
    }

    /** Team keys that currently have a valid invite for this player. */
    public List<String> invitesFor(UUID invitee) {
        Map<String, Long> m = invites.get(invitee);
        List<String> out = new ArrayList<>();
        if (m == null) return out;
        long now = System.currentTimeMillis();
        m.entrySet().removeIf(e -> e.getValue() <= now || !byName.containsKey(e.getKey()));
        out.addAll(m.keySet());
        return out;
    }

    public boolean hasInvite(UUID invitee, Team t) {
        return invitesFor(invitee).contains(t.key());
    }

    public void removeInvite(UUID invitee, Team t) {
        Map<String, Long> m = invites.get(invitee);
        if (m != null) m.remove(t.key());
    }

    public void clearInvites(UUID invitee) {
        invites.remove(invitee);
    }

    // ------------------------------------------------------------ saving

    public void load() {
        byName.clear();
        byPlayer.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("teams");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(k);
            if (c == null) continue;
            try {
                String name = c.getString("name", "");
                if (!validName(name)) continue;
                Team t = new Team(name, UUID.fromString(c.getString("leader", "")));
                for (String m : c.getStringList("members")) {
                    try {
                        t.members.add(UUID.fromString(m));
                    } catch (IllegalArgumentException ignored) {
                        // skip broken member ids
                    }
                }
                byName.put(t.key(), t);
                for (UUID id : t.members) byPlayer.put(id, t);
            } catch (IllegalArgumentException ignored) {
                // skip broken team
            }
        }
        plugin.getLogger().info("Loaded " + byName.size() + " teams.");
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (Team t : byName.values()) {
            String base = "teams." + (i++);
            y.set(base + ".name", t.name());
            y.set(base + ".leader", t.leader().toString());
            List<String> ids = new ArrayList<>();
            for (UUID id : t.members) ids.add(id.toString());
            y.set(base + ".members", ids);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save teams.yml: " + ex.getMessage());
        }
    }

    public Set<UUID> memberIds(Team t) {
        return t.members();
    }
}
