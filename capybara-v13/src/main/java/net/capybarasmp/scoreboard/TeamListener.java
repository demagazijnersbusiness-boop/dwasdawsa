package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/** Teammates can't hurt or kill each other (melee, arrows, tridents, snowballs...). */
public final class TeamListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final TeamManager teams;

    public TeamListener(TeamManager teams) {
        this.teams = teams;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (teams.friendlyFire()) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        Entity damager = e.getDamager();
        if (damager instanceof Player p) {
            attacker = p;
        } else if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) attacker = p;
        }
        if (attacker == null || attacker.equals(victim)) return;

        if (teams.sameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
            e.setCancelled(true);
            attacker.sendActionBar(MM.deserialize("<red>You can't hurt your teammate <white>" + victim.getName() + "<red>!"));
        }
    }
}
