package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Maintenance mode. /maintenance on  -> everyone who is not an admin is kicked and can't join.
 * Admins stay online (and can join). The multiplayer server list shows a maintenance MOTD and
 * the sidebar scoreboard shows MAINTENANCE in its title. The state is saved in maintenance.yml
 * so it survives restarts.
 */
public final class MaintenanceManager implements CommandExecutor, org.bukkit.command.TabCompleter, Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CapybaraScoreboard plugin;
    private final BoardManager boards;
    private final File file;
    private boolean on;

    public MaintenanceManager(CapybaraScoreboard plugin, BoardManager boards) {
        this.plugin = plugin;
        this.boards = boards;
        this.file = new File(plugin.getDataFolder(), "maintenance.yml");
        if (file.exists()) {
            this.on = YamlConfiguration.loadConfiguration(file).getBoolean("enabled", false);
        }
    }

    public boolean isOn() {
        return on;
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("enabled", on);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save maintenance.yml: " + e.getMessage());
        }
    }

    private Component kickMessage() {
        return MM.deserialize(plugin.getConfig().getString("maintenance.kick-message",
                "<red><bold>SERVER MAINTENANCE</bold><newline><gray>The server is in maintenance. Please come back later."));
    }

    private void setMaintenance(CommandSender by, boolean value) {
        on = value;
        save();
        for (Player p : Bukkit.getOnlinePlayers()) boards.update(p);
        if (on) {
            int kicked = 0;
            for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                if (CapybaraScoreboard.isAdmin(p)) {
                    p.sendMessage(MM.deserialize("<red><bold>MAINTENANCE ON.</bold> <gray>Normal players were kicked, admins can stay."));
                } else {
                    p.kick(kickMessage());
                    kicked++;
                }
            }
            plugin.getLogger().warning(by.getName() + " turned maintenance ON (" + kicked + " players kicked).");
            by.sendMessage(MM.deserialize("<green>Maintenance is now <red>ON<green>. Kicked <white>" + kicked + " <green>player(s)."));
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (CapybaraScoreboard.isAdmin(p)) {
                    p.sendMessage(MM.deserialize("<green><bold>MAINTENANCE OFF.</bold> <gray>Everyone can join again."));
                }
            }
            plugin.getLogger().warning(by.getName() + " turned maintenance OFF.");
            by.sendMessage(MM.deserialize("<green>Maintenance is now <white>OFF<green>. Everyone can join again."));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        boolean allowed = sender instanceof ConsoleCommandSender || CapybaraScoreboard.isAdmin(sender);
        if (!allowed) {
            sender.sendMessage(MM.deserialize("<red>You don't have permission."));
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "on" -> {
                if (on) sender.sendMessage(MM.deserialize("<yellow>Maintenance is already ON."));
                else setMaintenance(sender, true);
            }
            case "off" -> {
                if (!on) sender.sendMessage(MM.deserialize("<yellow>Maintenance is already OFF."));
                else setMaintenance(sender, false);
            }
            case "status" -> sender.sendMessage(MM.deserialize("<gray>Maintenance is <white>" + (on ? "ON" : "OFF") + "<gray>."));
            default -> sender.sendMessage(MM.deserialize("<red>Usage: /maintenance <on|off|status>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && (sender instanceof ConsoleCommandSender || CapybaraScoreboard.isAdmin(sender))) {
            for (String s : List.of("on", "off", "status")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        }
        return out;
    }

    /** Blocks normal players from joining during maintenance. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent e) {
        if (!on) return;
        if (CapybaraScoreboard.isAdmin(e.getPlayer())) return;
        e.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMessage());
    }

    /** Shows the maintenance MOTD in the multiplayer server list. */
    @EventHandler
    public void onPing(ServerListPingEvent e) {
        if (!on) return;
        e.motd(MM.deserialize(plugin.getConfig().getString("maintenance.motd",
                "<red><bold>MAINTENANCE</bold> <gray>- the server is closed for now")));
    }

    /** Tells admins who join that maintenance is on. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!on || !CapybaraScoreboard.isAdmin(e.getPlayer())) return;
        e.getPlayer().sendMessage(MM.deserialize("<red><bold>MAINTENANCE IS ON.</bold> <gray>Only admins can join. Use /maintenance off to open the server."));
    }
}
