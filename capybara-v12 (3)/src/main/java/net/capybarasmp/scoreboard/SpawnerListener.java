package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.math.BigInteger;

public final class SpawnerListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final SpawnerManager spawners;
    private final SpawnerGui gui;

    public SpawnerListener(SpawnerManager spawners, SpawnerGui gui) {
        this.spawners = spawners;
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!spawners.isSkellyItem(e.getItemInHand())) return;
        spawners.register(e.getBlockPlaced(), e.getPlayer());
        e.getPlayer().sendMessage(MM.deserialize("<green>Skelly Spawner placed! Right-click it to open the loot window."));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        SpawnerManager.Spawner sp = spawners.get(b);
        if (sp == null) return;

        Player p = e.getPlayer();

        // fake stash: anyone can break it, it has no owner and drops nothing
        if (sp.decoy) {
            e.setDropItems(false);
            e.setExpToDrop(0);
            spawners.unregister(b);
            p.sendMessage(MM.deserialize("<gray>That was a fake stash: no owner, nothing dropped."));
            return;
        }

        if (!p.getUniqueId().equals(sp.owner()) && !CapybaraScoreboard.isAdmin(p)) {
            e.setCancelled(true);
            p.sendMessage(MM.deserialize("<red>This is not your Skelly Spawner."));
            return;
        }

        e.setDropItems(false);
        e.setExpToDrop(0);

        // whatever is stored gets sold for the owner, and the whole stack goes back to the player
        BigInteger cash = spawners.sellAll(sp, sp.owner());
        int stack = sp.stack;
        spawners.unregister(b);

        int left = stack;
        while (left > 0) {
            int n = Math.min(64, left);
            left -= n;
            p.getInventory().addItem(spawners.createItem(n)).values()
                    .forEach(rest -> b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), rest));
        }
        p.sendMessage(MM.deserialize("<green>You picked up <white><n> <green>Skelly Spawner(s).",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("n", String.valueOf(stack))));
        if (cash.signum() > 0) {
            p.sendMessage(MM.deserialize("<gray>The stored loot was sold for <green>$<amt><gray> (paid to the owner).",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("amt", MoneyUtil.format(cash))));
        }
    }

    // never let a Skelly Spawner spawn a mob
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(SpawnerSpawnEvent e) {
        CreatureSpawner cs = e.getSpawner();
        if (cs != null && spawners.isSkelly(cs.getBlock())) {
            e.setCancelled(true);
        }
    }

    // right-click: open the loot window, or stack another Skelly Spawner onto it
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || !spawners.isSkelly(b)) return;
        ItemStack item = e.getItem();
        Player p = e.getPlayer();

        // nobody can change it with a spawn egg
        if (item != null && item.getType().name().endsWith("SPAWN_EGG")) {
            e.setUseItemInHand(Event.Result.DENY);
            e.setCancelled(true);
            return;
        }

        boolean holdingSkelly = spawners.isSkellyItem(item);
        // sneaking with a normal block in your hand = you want to build against it
        if (p.isSneaking() && !holdingSkelly && item != null && item.getType().isBlock()) return;

        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return; // the off-hand fires the event a second time

        SpawnerManager.Spawner sp = spawners.get(b);
        if (sp == null) return;
        if (sp.decoy) {
            p.sendMessage(MM.deserialize("<gray>This Skelly Spawner has no owner and makes no money."));
            return;
        }
        if (!p.getUniqueId().equals(sp.owner()) && !CapybaraScoreboard.isAdmin(p)) {
            p.sendMessage(MM.deserialize("<red>This is not your Skelly Spawner."));
            return;
        }

        if (holdingSkelly) {
            int room = spawners.maxStack() - sp.stack;
            if (room <= 0) {
                p.sendMessage(MM.deserialize("<red>This spawner is at its max stack size."));
                return;
            }
            int n = Math.min(room, p.isSneaking() ? item.getAmount() : 1);
            item.setAmount(item.getAmount() - n);
            spawners.addStack(sp, n);
            p.sendMessage(MM.deserialize("<green>Stacked! This spawner is now <yellow>x<n> <green>(<white>double<green> the items per extra spawner).",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("n", String.valueOf(sp.stack))));
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
            return;
        }
        gui.open(p, sp);
    }

    // explosions can't destroy Skelly Spawners
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(spawners::isProtected);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(spawners::isProtected);
    }
}
