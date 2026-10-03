package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Events: an admin makes an event (/create event), players join (/joinevent), and when the admin
 * starts it (/startevent) every player gets a wheel that spins and lands on a prize. Some prizes are good, some bad.
 */
public final class EventManager {

    private record Prize(String name, Material icon, boolean good, int weight, Consumer<Player> action) {}

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int[] DELAYS = delays();

    private final CapybaraScoreboard plugin;
    private final DataManager data;
    private final Economy economy;
    private final EmeraldManager emerald;
    private final SpawnerManager spawners;
    private final Random random = new Random();
    private final List<Prize> prizes = new ArrayList<>();

    private boolean open;
    private String name = "";
    private final Set<UUID> participants = new LinkedHashSet<>();

    public EventManager(CapybaraScoreboard plugin, DataManager data, Economy economy, EmeraldManager emerald, SpawnerManager spawners) {
        this.plugin = plugin;
        this.data = data;
        this.economy = economy;
        this.emerald = emerald;
        this.spawners = spawners;
        buildPrizes();
    }

    // ------------------------------------------------------------ prizes

    private void giveItem(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
    }

    private void money(Player p, long amount) {
        economy.add(p.getUniqueId(), BigInteger.valueOf(amount));
    }

    private void effect(Player p, PotionEffectType type, int seconds, int amplifier) {
        p.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier));
    }

    private void buildPrizes() {
        // ---- good
        prizes.add(new Prize("$100K", Material.GOLD_INGOT, true, 20, p -> money(p, 100_000)));
        prizes.add(new Prize("$1M", Material.GOLD_BLOCK, true, 6, p -> money(p, 1_000_000)));
        prizes.add(new Prize("$10M", Material.DIAMOND_BLOCK, true, 1, p -> money(p, 10_000_000)));
        prizes.add(new Prize("50 Emerald", Material.EMERALD, true, 12, p -> emerald.add(p.getUniqueId(), 50)));
        prizes.add(new Prize("250 Emerald", Material.EMERALD_BLOCK, true, 3, p -> emerald.add(p.getUniqueId(), 250)));
        prizes.add(new Prize("8 Golden Apples", Material.GOLDEN_APPLE, true, 10, p -> giveItem(p, new ItemStack(Material.GOLDEN_APPLE, 8))));
        prizes.add(new Prize("16 Diamonds", Material.DIAMOND, true, 10, p -> giveItem(p, new ItemStack(Material.DIAMOND, 16))));
        prizes.add(new Prize("Netherite Ingot", Material.NETHERITE_INGOT, true, 3, p -> giveItem(p, new ItemStack(Material.NETHERITE_INGOT))));
        prizes.add(new Prize("Wayback Totem", Material.TOTEM_OF_UNDYING, true, 2, p -> giveItem(p, emerald.createTotem())));
        prizes.add(new Prize("Skelly Spawner", Material.SPAWNER, true, 1, p -> giveItem(p, spawners.createItem(1))));
        prizes.add(new Prize("+1 Life", Material.GLISTERING_MELON_SLICE, true, 4, p -> {
            PlayerData d = data.get(p.getUniqueId());
            d.lives = Math.min(plugin.getConfig().getInt("max-lives", 20), d.lives + 1);
        }));
        prizes.add(new Prize("Speed & Strength (5 min)", Material.SUGAR, true, 6, p -> {
            effect(p, PotionEffectType.SPEED, 300, 1);
            effect(p, PotionEffectType.STRENGTH, 300, 0);
        }));
        // ---- bad
        prizes.add(new Prize("Lose 10% of your money", Material.RED_DYE, false, 8, p -> {
            BigInteger bal = economy.balance(p.getUniqueId());
            economy.take(p.getUniqueId(), bal.divide(BigInteger.TEN));
        }));
        prizes.add(new Prize("Blind & Slow (30s)", Material.INK_SAC, false, 8, p -> {
            effect(p, PotionEffectType.BLINDNESS, 30, 0);
            effect(p, PotionEffectType.SLOWNESS, 30, 1);
        }));
        prizes.add(new Prize("Super Hungry", Material.ROTTEN_FLESH, false, 6, p -> {
            p.setFoodLevel(2);
            effect(p, PotionEffectType.HUNGER, 30, 1);
        }));
        prizes.add(new Prize("Mining Fatigue (2 min)", Material.WOODEN_PICKAXE, false, 5, p -> effect(p, PotionEffectType.MINING_FATIGUE, 120, 1)));
        prizes.add(new Prize("Lightning Scare!", Material.LIGHTNING_ROD, false, 4, p -> p.getWorld().strikeLightningEffect(p.getLocation())));
        prizes.add(new Prize("Lose 1 Life", Material.WITHER_ROSE, false, 2, p -> {
            PlayerData d = data.get(p.getUniqueId());
            if (d.lives > 0) d.lives--;
        }));
    }

    private Prize randomPrize() {
        int total = 0;
        for (Prize pr : prizes) total += pr.weight();
        int roll = random.nextInt(total);
        for (Prize pr : prizes) {
            roll -= pr.weight();
            if (roll < 0) return pr;
        }
        return prizes.get(0);
    }

    // ------------------------------------------------------------ event commands

    public boolean isOpen() {
        return open;
    }

    public void create(CommandSender actor, String eventName) {
        if (open) {
            actor.sendMessage(MM.deserialize("<red>There is already an event open. Use /startevent or /endevent."));
            return;
        }
        open = true;
        name = eventName.isBlank() ? "Wheel Event" : eventName;
        participants.clear();
        Bukkit.broadcast(MM.deserialize("<gold><bold>EVENT</bold> <gray>» <white><n> <gray>is open! Type <yellow>/joinevent <gray>to join.",
                Placeholder.unparsed("n", name)));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
    }

    public void join(Player p) {
        if (!open) {
            p.sendMessage(MM.deserialize("<red>There is no event open right now."));
            return;
        }
        if (!participants.add(p.getUniqueId())) {
            p.sendMessage(MM.deserialize("<yellow>You already joined the event."));
            return;
        }
        p.sendMessage(MM.deserialize("<green>You joined <white><n><green>! <gray>(<c> players)",
                Placeholder.unparsed("n", name), Placeholder.unparsed("c", String.valueOf(participants.size()))));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    public void cancel(CommandSender actor) {
        if (!open) {
            actor.sendMessage(MM.deserialize("<red>There is no event open."));
            return;
        }
        open = false;
        participants.clear();
        Bukkit.broadcast(MM.deserialize("<gold><bold>EVENT</bold> <gray>» <red>The event was cancelled."));
    }

    public void start(CommandSender actor) {
        if (!open) {
            actor.sendMessage(MM.deserialize("<red>There is no event open. Create one with /create event."));
            return;
        }
        List<Player> players = new ArrayList<>();
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) players.add(p);
        }
        if (players.isEmpty()) {
            actor.sendMessage(MM.deserialize("<red>Nobody joined the event yet."));
            return;
        }
        open = false;
        participants.clear();
        Bukkit.broadcast(MM.deserialize("<gold><bold>EVENT</bold> <gray>» <white>The wheel is spinning for <yellow><c> <white>players!",
                Placeholder.unparsed("c", String.valueOf(players.size()))));
        for (Player p : players) spin(p);
    }

    // ------------------------------------------------------------ the wheel

    private static int[] delays() {
        // ticks between two steps: fast at first, slower and slower
        int[] d = new int[36];
        for (int i = 0; i < d.length; i++) {
            d[i] = i < 18 ? 2 : i < 26 ? 3 : i < 31 ? 5 : i < 34 ? 8 : 12;
        }
        return d;
    }

    private ItemStack icon(Prize pr) {
        ItemStack it = new ItemStack(pr.icon());
        it.editMeta(m -> m.displayName(MM.deserialize("<!italic>" + (pr.good() ? "<green>" : "<red>") + pr.name())));
        return it;
    }

    private void spin(Player p) {
        Prize winner = randomPrize();
        int steps = DELAYS.length;                 // window start goes from 0 to steps
        int winnerIndex = steps + 4;               // the centre of the final window
        List<Prize> strip = new ArrayList<>();
        for (int i = 0; i < winnerIndex + 5; i++) strip.add(randomPrize());
        strip.set(winnerIndex, winner);

        WheelHolder h = new WheelHolder();
        Inventory inv = Bukkit.createInventory(h, 27, MM.deserialize("<gold><bold>Wheel of Prizes"));
        h.setInventory(inv);
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        pane.editMeta(m -> m.displayName(Component.empty()));
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        ItemStack pointer = new ItemStack(Material.END_ROD);
        pointer.editMeta(m -> m.displayName(MM.deserialize("<!italic><yellow><bold>\u25bc  WINNER  \u25bc")));
        inv.setItem(4, pointer);
        ItemStack pointerUp = new ItemStack(Material.END_ROD);
        pointerUp.editMeta(m -> m.displayName(MM.deserialize("<!italic><yellow><bold>\u25b2  WINNER  \u25b2")));
        inv.setItem(22, pointerUp);
        p.openInventory(inv);

        step(p, inv, strip, 0, winner);
    }

    private void step(Player p, Inventory inv, List<Prize> strip, int k, Prize winner) {
        for (int s = 0; s < 9; s++) {
            inv.setItem(9 + s, icon(strip.get(k + s)));
        }
        if (p.isOnline()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);

        if (k >= DELAYS.length) {
            finish(p, winner);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> step(p, inv, strip, k + 1, winner), DELAYS[k]);
    }

    private void finish(Player p, Prize winner) {
        if (!p.isOnline()) return;
        winner.action().accept(p);
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.sendMessage(MM.deserialize("<gold><bold>WHEEL</bold> <gray>» " + (winner.good() ? "<green>You won: <white>" : "<red>Bad luck: <white>") + "<n>",
                    Placeholder.unparsed("n", winner.name())));
            p.playSound(p.getLocation(), winner.good() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_WITHER_HURT, 1f, 1f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() instanceof WheelHolder) {
                    p.closeInventory();
                }
            }, 60L);
        });
    }
}
