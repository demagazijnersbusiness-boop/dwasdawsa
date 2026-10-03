package net.capybarasmp.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/** Emerald Sword: every hit kills the target instantly. The sword disappears after a few hours. */
public final class SwordListener implements Listener {

    private final CapybaraScoreboard plugin;
    private final EmeraldManager emerald;

    public SwordListener(CapybaraScoreboard plugin, EmeraldManager emerald) {
        this.plugin = plugin;
        this.emerald = emerald;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!emerald.isSword(tool)) return;

        if (emerald.isExpired(tool)) {
            p.getInventory().setItemInMainHand(null);
            emerald.notifyExpired(p, "Emerald Sword");
            e.setCancelled(true);
            return;
        }

        Entity hit = e.getEntity();
        if (hit instanceof ComplexEntityPart part) hit = part.getParent(); // e.g. the Ender Dragon
        if (!(hit instanceof LivingEntity victim)) return;

        if (victim instanceof Player && !plugin.getConfig().getBoolean("emerald.shop.sword-hits-players", true)) {
            return; // normal damage against players when disabled in the config
        }

        e.setDamage(1_000_000.0);

        // safety net: if something (totem, plugin...) kept it alive, finish it next tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!victim.isValid() || victim.isDead() || victim.isInvulnerable()) return;
            if (victim instanceof Player pl
                    && (pl.getGameMode() == GameMode.CREATIVE || pl.getGameMode() == GameMode.SPECTATOR)) return;
            victim.setHealth(0.0);
        });
    }
}
