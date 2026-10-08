package net.capybarasmp.scoreboard;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

public final class PlayerData {
    public int kills;
    public int deaths;
    public long playtimeSeconds;
    public int lives;
    public BigInteger money;
    public String rank = "";
    public boolean admin;
    // admin /wipe on an offline player: applied the next time they join
    public boolean pendingWipeInv;
    public boolean pendingWipeEnder;

    // Emerald currency (earned by being AFK)
    public long emerald;
    public long lastSpawnerBuy;
    // Skelly Spawners bought in the current daily window (shared by /shop and /emeraldshop)
    public int skellyBought;
    public long skellyWindowStart;

    // personal settings (/settings)
    public boolean noMobSpawn;
    public boolean nightVision;
    // hardcore mode: turned on in /settings, can't be turned off by the player. Dying = spectator for good.
    public boolean hardcore;
    public boolean hardcoreDead;

    // personal scoreboard settings (/scoreboard)
    public boolean boardHidden;
    public final Set<String> hiddenLines = new HashSet<>();

    public PlayerData(int lives, BigInteger money) {
        this.lives = lives;
        this.money = money;
    }
}
