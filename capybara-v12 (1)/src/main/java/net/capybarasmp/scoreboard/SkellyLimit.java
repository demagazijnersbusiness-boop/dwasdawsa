package net.capybarasmp.scoreboard;

import java.util.UUID;

/** Limits how many Skelly Spawners one player can buy per day (default 5 per 24 hours, both shops together). */
public final class SkellyLimit {

    private final CapybaraScoreboard plugin;
    private final DataManager data;

    public SkellyLimit(CapybaraScoreboard plugin, DataManager data) {
        this.plugin = plugin;
        this.data = data;
    }

    /** 0 = unlimited */
    public int limit() {
        return Math.max(0, plugin.getConfig().getInt("skelly-spawner.daily-limit", 5));
    }

    public long windowHours() {
        return Math.max(1L, plugin.getConfig().getLong("skelly-spawner.limit-window-hours", 24L));
    }

    private long windowMs() {
        return windowHours() * 3_600_000L;
    }

    private void refresh(PlayerData d) {
        if (d.skellyWindowStart > 0 && System.currentTimeMillis() - d.skellyWindowStart >= windowMs()) {
            d.skellyBought = 0;
            d.skellyWindowStart = 0;
        }
    }

    public int remaining(UUID id) {
        if (limit() == 0) return Integer.MAX_VALUE;
        PlayerData d = data.get(id);
        refresh(d);
        return Math.max(0, limit() - d.skellyBought);
    }

    /** Milliseconds until the player may buy again (0 when they can buy now). */
    public long waitMs(UUID id) {
        if (remaining(id) > 0) return 0L;
        PlayerData d = data.get(id);
        return Math.max(0L, d.skellyWindowStart + windowMs() - System.currentTimeMillis());
    }

    public void record(UUID id) {
        PlayerData d = data.get(id);
        refresh(d);
        if (d.skellyBought == 0) d.skellyWindowStart = System.currentTimeMillis();
        d.skellyBought++;
    }
}
