package net.capybarasmp.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Emerald Pickaxe: breaking one block breaks a 3x2 area (6 blocks) in the direction you are mining. */
public final class PickaxeListener implements Listener {

    private final EmeraldManager emerald;
    private final SpawnerManager spawners;
    private final Set<UUID> busy = new HashSet<>();

    public PickaxeListener(EmeraldManager emerald, SpawnerManager spawners) {
        this.emerald = emerald;
        this.spawners = spawners;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (busy.contains(p.getUniqueId())) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!emerald.isPickaxe(tool)) return;

        if (emerald.isExpired(tool)) {
            p.getInventory().setItemInMainHand(null);
            emerald.notifyExpired(p);
            e.setCancelled(true);
            return;
        }

        Block origin = e.getBlock();
        BlockFace face = null;
        double fracY = 0.5;
        RayTraceResult rt = p.rayTraceBlocks(7);
        if (rt != null && rt.getHitBlock() != null && rt.getHitBlock().equals(origin) && rt.getHitBlockFace() != null) {
            face = rt.getHitBlockFace();
            if (rt.getHitPosition() != null) {
                fracY = rt.getHitPosition().getY() - origin.getY();
            }
        }
        if (face == null) {
            face = p.getFacing().getOppositeFace();
        }

        // offsets of the 3x2 area (the broken block itself is included as 0,0,0)
        List<int[]> offsets = new ArrayList<>();
        int rowDir = fracY < 0.5 ? 1 : -1;
        for (int w = -1; w <= 1; w++) {
            for (int r = 0; r < 2; r++) {
                if (face == BlockFace.NORTH || face == BlockFace.SOUTH) {
                    offsets.add(new int[]{w, r * rowDir, 0});
                } else if (face == BlockFace.EAST || face == BlockFace.WEST) {
                    offsets.add(new int[]{0, r * rowDir, w});
                } else {
                    BlockFace f = p.getFacing();
                    if (f == BlockFace.NORTH || f == BlockFace.SOUTH) {
                        offsets.add(new int[]{w, 0, r * f.getModZ()});
                    } else {
                        offsets.add(new int[]{r * f.getModX(), 0, w});
                    }
                }
            }
        }

        busy.add(p.getUniqueId());
        try {
            for (int[] o : offsets) {
                if (o[0] == 0 && o[1] == 0 && o[2] == 0) continue;
                Block b = origin.getRelative(o[0], o[1], o[2]);
                if (!canBreak(b)) continue;
                BlockBreakEvent ev = new BlockBreakEvent(b, p);
                Bukkit.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) continue;
                if (ev.isDropItems()) {
                    b.breakNaturally(tool);
                } else {
                    b.setType(Material.AIR);
                }
            }
        } finally {
            busy.remove(p.getUniqueId());
        }
    }

    private boolean canBreak(Block b) {
        Material m = b.getType();
        if (m.isAir() || b.isLiquid()) return false;
        if (m.getHardness() < 0) return false;          // bedrock, barrier, portal frames...
        if (m == Material.SPAWNER || spawners.isSkelly(b)) return false;
        if (b.getState() instanceof Container) return false; // never destroy chests, furnaces, shulkers...
        return true;
    }
}
