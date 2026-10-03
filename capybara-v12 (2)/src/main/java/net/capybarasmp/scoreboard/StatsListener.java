package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class StatsListener implements Listener {

    private final CapybaraScoreboard plugin;
    private final DataManager data;
    private final BoardManager boards;
    private final RankManager ranks;

    public StatsListener(CapybaraScoreboard plugin, DataManager data, BoardManager boards, RankManager ranks) {
        this.plugin = plugin;
        this.data = data;
        this.boards = boards;
        this.ranks = ranks;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        data.get(p.getUniqueId()); // creates data with starting lives for new players
        boards.show(p);
        ranks.apply(p);
        ranks.syncNametags();
        ranks.refreshTab();
        enforceZeroLives(p);
        p.sendMessage(MiniMessage.miniMessage().deserialize(
                plugin.getConfig().getString("join-message", "<gray>Systems made by <#ff4fa3><bold>@ItssCapy")));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer());
        data.save();
        Bukkit.getScheduler().runTask(plugin, ranks::refreshTab);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        PlayerData vd = data.get(victim.getUniqueId());
        vd.deaths++;
        if (plugin.getConfig().getBoolean("lives.lose-on-death", true) && vd.lives > 0) {
            vd.lives--;
        }
        boards.update(victim);

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            data.get(killer.getUniqueId()).kills++;
            boards.update(killer);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> enforceZeroLives(p));
    }

    private void enforceZeroLives(Player p) {
        String action = plugin.getConfig().getString("lives.on-zero-lives", "NONE");
        if (action.equalsIgnoreCase("SPECTATOR") && data.get(p.getUniqueId()).lives <= 0) {
            p.setGameMode(GameMode.SPECTATOR);
        }
    }
}
