package net.capybarasmp.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CapybaraScoreboard extends JavaPlugin {

    private static CapybaraScoreboard instance;

    private DataManager data;
    private BoardManager boards;
    private MaintenanceManager maintenance;
    private ShopManager shop;
    private SpawnerManager spawners;
    private HomeManager homes;
    private RankManager ranks;
    private TeamManager teams;
    private SignSearch signSearch;

    public static CapybaraScoreboard get() {
        return instance;
    }

    /** Admin = has the permission capybara.admin (OP) OR unlocked it with /urj. */
    public static boolean isAdmin(CommandSender sender) {
        if (sender.hasPermission("capybara.admin")) return true;
        return sender instanceof Player p && instance != null && instance.data.get(p.getUniqueId()).admin;
    }

    public boolean isMaintenance() {
        return maintenance != null && maintenance.isOn();
    }

    public DataManager getData() {
        return data;
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
        teams = new TeamManager(this, boards);
        teams.load();
        boards.setTeams(teams);
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
        signSearch = new SignSearch(this);
        gui.setSignSearch(signSearch);
        HomesGui homesGui = new HomesGui(homes);
        SpawnerGui spawnerGui = new SpawnerGui(this, spawners);
        EmeraldManager emerald = new EmeraldManager(this, data, boards);
        emerald.start();
        EmeraldGui emeraldGui = new EmeraldGui(this, emerald, spawners, data, skellyLimit);
        ScoreboardGui scoreboardGui = new ScoreboardGui(data, boards);
        SettingsGui settingsGui = new SettingsGui(this, data);
        settingsGui.start();
        EventManager events = new EventManager(this, data, economy, emerald, spawners);
        AdminTools adminTools = new AdminTools(this, spawners);

        getServer().getPluginManager().registerEvents(new StatsListener(this, data, boards, ranks), this);
        getServer().getPluginManager().registerEvents(new GuiListener(gui, sellService, homesGui, spawnerGui, emeraldGui, scoreboardGui, settingsGui), this);
        getServer().getPluginManager().registerEvents(new SpawnerListener(spawners, spawnerGui), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this, data, ranks, gui), this);
        getServer().getPluginManager().registerEvents(new AdminListener(economy, emerald), this);
        getServer().getPluginManager().registerEvents(emerald, this);
        getServer().getPluginManager().registerEvents(new PickaxeListener(emerald, spawners), this);
        getServer().getPluginManager().registerEvents(new SwordListener(this, emerald), this);
        getServer().getPluginManager().registerEvents(new TeamListener(teams), this);
        getServer().getPluginManager().registerEvents(signSearch, this);
        getServer().getPluginManager().registerEvents(settingsGui, this);
        getServer().getPluginManager().registerEvents(new TotemListener(emerald), this);
        getServer().getPluginManager().registerEvents(adminTools, this);

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

        ModCommands mod = new ModCommands(this);
        for (String name : new String[]{"ban", "kick", "unban", "rtp"}) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(mod);
                getCommand(name).setTabCompleter(mod);
            }
        }
        SchematicCommands schem = new SchematicCommands(this);
        if (getCommand("schem") != null) {
            getCommand("schem").setExecutor(schem);
            getCommand("schem").setTabCompleter(schem);
        }
        maintenance = new MaintenanceManager(this, boards);
        getServer().getPluginManager().registerEvents(maintenance, this);
        if (getCommand("maintenance") != null) {
            getCommand("maintenance").setExecutor(maintenance);
            getCommand("maintenance").setTabCompleter(maintenance);
        }
        ExtraCommands extra = new ExtraCommands(this, data, ranks, homesGui);
        getServer().getPluginManager().registerEvents(extra, this);
        for (String name : new String[]{"urj", "giverank", "homes", "reset", "wipe"}) {
            getCommand(name).setExecutor(extra);
            getCommand(name).setTabCompleter(extra);
        }

        getCommand("announce").setExecutor(new AnnounceCommand(this));

        TeamCommand teamCommand = new TeamCommand(teams, economy);
        getCommand("team").setExecutor(teamCommand);
        getCommand("team").setTabCompleter(teamCommand);

        EmeraldCommands ac = new EmeraldCommands(emerald, spawners, emeraldGui, scoreboardGui);
        for (String name : new String[]{"afk", "emeraldshop", "scoreboard", "emerald", "getstar", "upgradepickaxe"}) {
            getCommand(name).setExecutor(ac);
        }

        getCommand("settings").setExecutor((sender, cmd, label, args) -> {
            if (sender instanceof Player pl) settingsGui.open(pl);
            else sender.sendMessage("Players only.");
            return true;
        });
        SetCommand setCommand = new SetCommand(emerald, economy);
        getCommand("set").setExecutor(setCommand);
        getCommand("set").setTabCompleter(setCommand);

        EventCommands eventCommands = new EventCommands(events);
        for (String name : new String[]{"create", "createevent", "joinevent", "startevent", "endevent"}) {
            getCommand(name).setExecutor(eventCommands);
        }
        for (String name : new String[]{"spawnfakestash", "goto", "invis", "fakeleft", "fly", "godmode", "spectator", "spectate", "gamemode", "freebuild"}) {
            getCommand(name).setExecutor(adminTools);
            getCommand(name).setTabCompleter(adminTools);
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
        if (signSearch != null) signSearch.restoreAll();
        if (data != null) data.save();
        if (spawners != null) spawners.save();
        if (homes != null) homes.save();
        if (teams != null) teams.save();
    }
}
