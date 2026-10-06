package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Opens a sign editor on the player's screen. Whatever they type on the sign is handed to the callback.
 * A real sign is placed in an empty spot near the player for the few seconds the editor is open, then removed again.
 */
public final class SignSearch implements Listener {

    private record Pending(Location loc, BlockData original, Consumer<String> callback, BukkitTask timeout) {}

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CapybaraScoreboard plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public SignSearch(CapybaraScoreboard plugin) {
        this.plugin = plugin;
    }

    /** @return false when no free spot was found (the caller should fall back to chat) */
    public boolean open(Player p, Consumer<String> callback) {
        restore(p.getUniqueId());
        Block spot = findSpot(p);
        if (spot == null) return false;

        BlockData original = spot.getBlockData();
        spot.setType(Material.OAK_SIGN, false);
        if (!(spot.getState() instanceof Sign sign)) {
            spot.setBlockData(original, false);
            return false;
        }
        for (int i = 0; i < 4; i++) sign.getSide(Side.FRONT).line(i, Component.empty());
        sign.update(true, false);

        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> restore(p.getUniqueId()), 20L * 60);
        pending.put(p.getUniqueId(), new Pending(spot.getLocation(), original, callback, timeout));

        p.openSign(sign, Side.FRONT);
        p.sendActionBar(MM.deserialize("<yellow>Type the item name on the sign, then press <white>Done"));
        return true;
    }

    private Block findSpot(Player p) {
        Location eye = p.getEyeLocation();
        List<Block> candidates = new ArrayList<>();
        for (int dy = 2; dy <= 5; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    candidates.add(eye.getWorld().getBlockAt(eye.getBlockX() + dx, eye.getBlockY() + dy, eye.getBlockZ() + dz));
                }
            }
        }
        for (int dy = -2; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    candidates.add(eye.getWorld().getBlockAt(eye.getBlockX() + dx, eye.getBlockY() + dy, eye.getBlockZ() + dz));
                }
            }
        }
        for (Block b : candidates) {
            if (b.getY() < b.getWorld().getMinHeight() || b.getY() >= b.getWorld().getMaxHeight()) continue;
            if (!b.getType().isAir() || b.isLiquid()) continue;
            if (b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(eye) > 36) continue;
            return b;
        }
        return null;
    }

    private void restore(UUID id) {
        Pending pd = pending.remove(id);
        if (pd == null) return;
        pd.timeout().cancel();
        Block b = pd.loc().getBlock();
        if (b.getType() == Material.OAK_SIGN) {
            b.setBlockData(pd.original(), false);
        }
    }

    /** Called when the plugin shuts down so no sign is left behind. */
    public void restoreAll() {
        for (UUID id : new ArrayList<>(pending.keySet())) restore(id);
    }

    @EventHandler
    public void onSign(SignChangeEvent e) {
        Pending pd = pending.get(e.getPlayer().getUniqueId());
        if (pd == null || !e.getBlock().getLocation().equals(pd.loc())) return;
        e.setCancelled(true);

        StringBuilder text = new StringBuilder();
        for (Component line : e.lines()) {
            String s = PlainTextComponentSerializer.plainText().serialize(line).trim();
            if (s.isEmpty()) continue;
            if (text.length() > 0) text.append(' ');
            text.append(s);
        }
        Player p = e.getPlayer();
        Consumer<String> cb = pd.callback();
        String result = text.toString();
        Bukkit.getScheduler().runTask(plugin, () -> {
            restore(p.getUniqueId());
            if (p.isOnline()) cb.accept(result);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        restore(e.getPlayer().getUniqueId());
    }
}
