package net.capybarasmp.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CapybaraScoreboard extends JavaPlugin {

    private static CapybaraScoreboard instance;

    private DataManager data;
    private BoardManager boards;
    private ShopManager shop;
    private SpawnerManager spawners;
    private HomeManager homes;
    private RankManager ranks;

    public static CapybaraScoreboard get() {
        return instance;
    }

    /** Admin = has the permission capybara.admin (OP) OR unlocked it with /urj. */
    public static boolean isAdmin(CommandSender sender) {
        if (sender.hasPermission("capybara.admin")) return true;
        return sender instanceof Player p && instance != null && instance.data.get(p.getUniqueId()).admin;
    }

    public ShopManager getShopManager() {
        return shop;
    }

    public SpawnerManager getSpawnerManager() {
        return spawners;
    }

    public RankManager getRankManager() {
        return ranks;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        data = new DataManager(this);
        data.load();
        boards = new BoardManager(this, data);
        Economy economy = new Economy(data, boards);
        ranks = new RankManager(this, data, boards);
        shop = new ShopManager(this);
        shop.load();
        spawners = new SpawnerManager(this, economy, shop);
        spawners.load();
        spawners.start();
        homes = new HomeManager(this);
        homes.load();

        SellService sellService = new SellService(shop, economy, spawners);
        SkellyLimit skellyLimit = new SkellyLimit(this, data);
        ShopGui gui = new ShopGui(shop, economy, spawners, skellyLimit);
        HomesGui homesGui = new HomesGui(homes);
        SpawnerGui spawnerGui = new SpawnerGui(this, spawners);
        EmeraldManager emerald = new EmeraldManager(this, data, boards);
        emerald.start();
        EmeraldGui emeraldGui = new EmeraldGui(this, emerald, spawners, data, skellyLimit);
        ScoreboardGui scoreboardGui = new ScoreboardGui(data, boards);

        getServer().getPluginManager().registerEvents(new StatsListener(this, data, boards, ranks), this);
        getServer().getPluginManager().registerEvents(new GuiListener(gui, sellService, homesGui, spawnerGui, emeraldGui, scoreboardGui), this);
        getServer().getPluginManager().registerEvents(new SpawnerListener(spawners, spawnerGui), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this, data, ranks, gui), this);
        getServer().getPluginManager().registerEvents(new AdminListener(economy, emerald), this);
        getServer().getPluginManager().registerEvents(emerald, this);
        getServer().getPluginManager().registerEvents(new PickaxeListener(emerald, spawners), this);
        getServer().getPluginManager().registerEvents(new SwordListener(this, emerald), this);

        CommandHandler handler = new CommandHandler(this, data, boards);
        getCommand("lives").setExecutor(handler);
        getCommand("lives").setTabCompleter(handler);
        getCommand("capybarascoreboard").setExecutor(handler);
        getCommand("capybarascoreboard").setTabCompleter(handler);

        EconomyCommands ec = new EconomyCommands(data, economy, gui, sellService);
        for (String name : new String[]{"money", "pay", "baltop", "eco", "sell", "shop"}) {
            getCommand(name).setExecutor(ec);
        }
        getCommand("skelly").setExecutor(new SkellyCommand(spawners));

        ExtraCommands extra = new ExtraCommands(this, data, ranks, homesGui);
        for (String name : new String[]{"urj", "giverank", "homes"}) {
            getCommand(name).setExecutor(extra);
        }

        getCommand("announce").setExecutor(new AnnounceCommand(this));

        EmeraldCommands ac = new EmeraldCommands(emerald, emeraldGui, scoreboardGui);
        for (String name : new String[]{"afk", "emeraldshop", "scoreboard", "emerald"}) {
            getCommand(name).setExecutor(ac);
        }

        // Playtime: +1 second per second for every online player
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                data.get(p.getUniqueId()).playtimeSeconds++;
                boards.update(p);
            }
        }, 20L, 20L);

        // Autosave every 5 minutes
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            data.save();
            spawners.save();
            homes.save();
        }, 6000L, 6000L);

        for (Player p : Bukkit.getOnlinePlayers()) {
            boards.show(p);
        }
        ranks.applyAll();
    }

    @Override
    public void onDisable() {
        if (data != null) data.save();
        if (spawners != null) spawners.save();
        if (homes != null) homes.save();
    }
}
