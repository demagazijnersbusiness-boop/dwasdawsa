package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Skelly Spawner: looks like a skeleton spawner, but never spawns mobs.
 * Instead it stores what skeletons would drop (bones + arrows). The owner right-clicks it to open
 * a GUI and sell the loot. Right-click with another Skelly Spawner to stack (every stack = 1 more
 * "skeleton killer", so the item output goes up). Placed spawners are saved in spawners.yml.
 */
public final class SpawnerManager {

    public static final class Spawner {
        private final UUID owner;
        private final String ownerName;
        private final String world;
        private final int x, y, z;
        public int stack = 1;
        public long bones;
        public long arrows;
        /** Fake stash: no owner, stores nothing, makes no money. */
        public boolean decoy;

        public Spawner(UUID owner, String ownerName, String world, int x, int y, int z) {
            this.owner = owner;
            this.ownerName = ownerName;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public UUID owner() { return owner; }
        public String ownerName() { return ownerName; }
        public String world() { return world; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public long total() { return bones + arrows; }

        public String key() {
            return world + ";" + x + ";" + y + ";" + z;
        }
    }

    /** Owner id used by fake stashes (nobody). */
    public static final UUID NO_OWNER = new UUID(0L, 0L);

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TAG = "skelly_holo";

    private final CapybaraScoreboard plugin;
    private final Economy economy;
    private final ShopManager shop;
    private final File file;
    private final NamespacedKey itemKey;
    private final Map<String, Spawner> spawners = new HashMap<>();
    private BukkitTask task;

    public SpawnerManager(CapybaraScoreboard plugin, Economy economy, ShopManager shop) {
        this.plugin = plugin;
        this.economy = economy;
        this.shop = shop;
        this.file = new File(plugin.getDataFolder(), "spawners.yml");
        this.itemKey = new NamespacedKey(plugin, "skelly_spawner");
    }

    // ------------------------------------------------------------ config values

    private int interval() {
        return Math.max(1, plugin.getConfig().getInt("skelly-spawner.interval-seconds", 10));
    }

    private int killsPerCycle() {
        return Math.max(1, plugin.getConfig().getInt("skelly-spawner.kills-per-cycle", 4));
    }

    /** Storage capacity of ONE spawner of the stack (in items). */
    public long capacity(Spawner sp) {
        long per = Math.max(64, plugin.getConfig().getLong("skelly-spawner.capacity-per-spawner", 2880));
        return per * sp.stack;
    }

    public int maxStack() {
        return Math.max(1, plugin.getConfig().getInt("skelly-spawner.max-stack", 10000));
    }

    // ------------------------------------------------------------ item

    public ItemStack createItem(int amount) {
        ItemStack it = new ItemStack(Material.SPAWNER, amount);
        it.editMeta(m -> {
            m.displayName(MM.deserialize("<!italic><white><bold>SKELLY SPAWNER"));
            m.lore(List.of(
                    MM.deserialize("<!italic><gray>Place it down. No mobs spawn from it."),
                    MM.deserialize("<!italic><gray>It stores <white>bones <gray>& <white>arrows<gray>."),
                    MM.deserialize("<!italic><yellow>Right-click <gray>» open it and sell the loot"),
                    MM.deserialize("<!italic><yellow>Right-click with another one <gray>» stack (more items)")));
            m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        });
        return it;
    }

    public boolean isSkellyItem(ItemStack it) {
        if (it == null || it.getType() != Material.SPAWNER || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------ registry

    private static String key(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    public Spawner get(Block b) {
        return spawners.get(key(b.getLocation()));
    }

    public Spawner getByKey(String key) {
        return spawners.get(key);
    }

    public boolean isSkelly(Block b) {
        return spawners.containsKey(key(b.getLocation()));
    }

    public void register(Block b, Player owner) {
        Spawner sp = new Spawner(owner.getUniqueId(), owner.getName(), b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        spawners.put(sp.key(), sp);

        BlockState st = b.getState();
        if (st instanceof CreatureSpawner cs) {
            cs.setSpawnedType(EntityType.SKELETON); // only for the spinning skeleton visual
            cs.setSpawnCount(0);
            cs.setMaxNearbyEntities(0);
            cs.update();
        }
        save();
        ensureHologram(sp);
    }

    /** Fake stash: looks like a Skelly Spawner, but has NO owner, stores nothing and never makes money. */
    public void registerDecoy(Block b) {
        Spawner sp = new Spawner(NO_OWNER, "None", b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        sp.decoy = true;
        spawners.put(sp.key(), sp);

        BlockState st = b.getState();
        if (st instanceof CreatureSpawner cs) {
            cs.setSpawnedType(EntityType.SKELETON);
            cs.setSpawnCount(0);
            cs.setMaxNearbyEntities(0);
            cs.update();
        }
        save();
        ensureHologram(sp);
    }

    /** True for real Skelly Spawners (explosions can't break these); fake stashes are not protected. */
    public boolean isProtected(Block b) {
        Spawner sp = get(b);
        return sp != null && !sp.decoy;
    }

    public void unregister(Block b) {
        Spawner sp = spawners.remove(key(b.getLocation()));
        if (sp != null) {
            removeHologram(sp);
            save();
        }
    }

    /** Adds {@code n} spawners to the stack (caller already removed the items). */
    public void addStack(Spawner sp, int n) {
        sp.stack = (int) Math.min((long) maxStack(), (long) sp.stack + n);
        refreshHologram(sp);
        save();
    }

    // ------------------------------------------------------------ selling the loot

    private BigInteger priceOf(Material m) {
        return shop.sellPriceOrDefault(m);
    }

    public BigInteger value(Spawner sp) {
        return priceOf(Material.BONE).multiply(BigInteger.valueOf(sp.bones))
                .add(priceOf(Material.ARROW).multiply(BigInteger.valueOf(sp.arrows)));
    }

    /** Sells {@code amount} of BONE/ARROW out of the spawner, pays the owner (or given player) and returns the money. */
    public BigInteger sell(Spawner sp, Material type, long amount, UUID payTo) {
        if (sp.decoy) return BigInteger.ZERO; // fake stashes never pay anything
        long have = type == Material.BONE ? sp.bones : type == Material.ARROW ? sp.arrows : 0;
        long n = Math.min(have, amount);
        if (n <= 0) return BigInteger.ZERO;
        if (type == Material.BONE) sp.bones -= n; else sp.arrows -= n;
        BigInteger money = priceOf(type).multiply(BigInteger.valueOf(n));
        economy.add(payTo, money);
        return money;
    }

    public long take(Spawner sp, Material type, long amount) {
        long have = type == Material.BONE ? sp.bones : type == Material.ARROW ? sp.arrows : 0;
        long n = Math.min(have, amount);
        if (n <= 0) return 0;
        if (type == Material.BONE) sp.bones -= n; else sp.arrows -= n;
        return n;
    }

    /** Sells everything that is stored and pays the given player. */
    public BigInteger sellAll(Spawner sp, UUID payTo) {
        BigInteger v = sell(sp, Material.BONE, sp.bones, payTo);
        return v.add(sell(sp, Material.ARROW, sp.arrows, payTo));
    }

    // ------------------------------------------------------------ saving

    public void load() {
        spawners.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("spawners");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(k);
            if (c == null) continue;
            try {
                Spawner sp = new Spawner(UUID.fromString(c.getString("owner", "")), c.getString("name", "Unknown"),
                        c.getString("world", "world"), c.getInt("x"), c.getInt("y"), c.getInt("z"));
                sp.stack = Math.max(1, c.getInt("stack", 1));
                sp.bones = Math.max(0, c.getLong("bones", 0));
                sp.arrows = Math.max(0, c.getLong("arrows", 0));
                sp.decoy = c.getBoolean("decoy", false);
                spawners.put(sp.key(), sp);
            } catch (IllegalArgumentException ignored) {
                // skip broken entries
            }
        }
        plugin.getLogger().info("Loaded " + spawners.size() + " Skelly Spawners.");
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (Spawner sp : spawners.values()) {
            String base = "spawners." + (i++);
            y.set(base + ".owner", sp.owner().toString());
            y.set(base + ".name", sp.ownerName());
            y.set(base + ".world", sp.world());
            y.set(base + ".x", sp.x());
            y.set(base + ".y", sp.y());
            y.set(base + ".z", sp.z());
            y.set(base + ".stack", sp.stack);
            y.set(base + ".bones", sp.bones);
            y.set(base + ".arrows", sp.arrows);
            if (sp.decoy) y.set(base + ".decoy", true);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save spawners.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ loot generation

    public void start() {
        if (task != null) task.cancel();
        long ticks = interval() * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, ticks, ticks);
    }

    private boolean loaded(World w, Spawner sp) {
        return w != null && w.isChunkLoaded(sp.x() >> 4, sp.z() >> 4);
    }

    private void generate(Spawner sp) {
        if (sp.decoy) return; // fake stash: nothing is ever generated
        long room = capacity(sp) - sp.total();
        if (room <= 0) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long kills = (long) sp.stack * killsPerCycle();
        long bones = 0;
        long arrows = 0;
        if (kills <= 400) {
            // like a real skeleton: 0-2 bones and 0-2 arrows per kill
            for (long i = 0; i < kills; i++) {
                bones += r.nextInt(3);
                arrows += r.nextInt(3);
            }
        } else {
            long j = Math.max(1, kills / 10);
            bones = kills + r.nextLong(-j, j + 1);
            arrows = kills + r.nextLong(-j, j + 1);
        }
        long b = Math.min(bones, room);
        room -= b;
        long a = Math.min(arrows, room);
        sp.bones += b;
        sp.arrows += a;
    }

    private void tick() {
        boolean particles = plugin.getConfig().getBoolean("skelly-spawner.particles", true);
        List<Spawner> dead = new ArrayList<>();

        for (Spawner sp : spawners.values()) {
            World w = Bukkit.getWorld(sp.world());
            if (loaded(w, sp)) {
                if (w.getBlockAt(sp.x(), sp.y(), sp.z()).getType() != Material.SPAWNER) {
                    dead.add(sp); // block was removed by something else
                    continue;
                }
                ensureHologram(sp);
                if (particles && !sp.decoy) {
                    w.spawnParticle(Particle.HAPPY_VILLAGER, new Location(w, sp.x() + 0.5, sp.y() + 1.1, sp.z() + 0.5),
                            4, 0.3, 0.15, 0.3, 0);
                }
            }
            generate(sp);
        }

        for (Spawner sp : dead) {
            removeHologram(sp);
            spawners.remove(sp.key());
        }
        if (!dead.isEmpty()) save();
    }

    // ------------------------------------------------------------ hologram

    private Location center(Spawner sp) {
        World w = Bukkit.getWorld(sp.world());
        if (w == null) return null;
        return new Location(w, sp.x() + 0.5, sp.y() + 1.35, sp.z() + 0.5);
    }

    private List<Entity> findHolograms(Location c) {
        List<Entity> out = new ArrayList<>();
        Collection<Entity> near = c.getWorld().getNearbyEntities(c, 0.6, 0.3, 0.6);
        for (Entity e : near) {
            if (e instanceof TextDisplay && e.getScoreboardTags().contains(TAG)) out.add(e);
        }
        return out;
    }

    private Component hologramText(Spawner sp) {
        if (sp.decoy) {
            return MM.deserialize("<white><bold>SKELLY SPAWNER</bold><newline><gray>Owner: <dark_gray>None");
        }
        return MM.deserialize(
                "<white><bold>SKELLY SPAWNER</bold> <yellow>x<stack><newline><gray>Owner: <white><owner><newline><green>Right-click to open",
                Placeholder.unparsed("stack", String.valueOf(sp.stack)),
                Placeholder.unparsed("owner", sp.ownerName()));
    }

    public void ensureHologram(Spawner sp) {
        if (!plugin.getConfig().getBoolean("skelly-spawner.hologram", true)) return;
        Location c = center(sp);
        if (c == null || !loaded(c.getWorld(), sp)) return;
        if (!findHolograms(c).isEmpty()) return;
        c.getWorld().spawn(c, TextDisplay.class, td -> {
            td.text(hologramText(sp));
            td.setBillboard(Display.Billboard.CENTER);
            td.addScoreboardTag(TAG);
        });
    }

    public void removeHologram(Spawner sp) {
        Location c = center(sp);
        if (c == null || !loaded(c.getWorld(), sp)) return;
        for (Entity e : findHolograms(c)) e.remove();
    }

    public void refreshHologram(Spawner sp) {
        removeHologram(sp);
        ensureHologram(sp);
    }

    /** Re-creates every hologram (used after /csb reload so new config values show up). */
    public void refreshHolograms() {
        for (Spawner sp : new ArrayList<>(spawners.values())) {
            refreshHologram(sp);
        }
    }
}
