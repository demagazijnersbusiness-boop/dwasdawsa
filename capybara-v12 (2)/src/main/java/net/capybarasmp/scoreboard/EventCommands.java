package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Locale;

/** /create event [name], /createevent [name], /joinevent, /startevent, /endevent */
public final class EventCommands implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final EventManager events;

    public EventCommands(EventManager events) {
        this.events = events;
    }

    private void send(CommandSender s, String mini) {
        s.sendMessage(MM.deserialize(mini));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);

        if (name.equals("joinevent")) {
            if (sender instanceof Player p) events.join(p);
            else send(sender, "<red>Players only.");
            return true;
        }

        if (!CapybaraScoreboard.isAdmin(sender)) {
            send(sender, "<red>You don't have permission.");
            return true;
        }
        switch (name) {
            case "create" -> {
                if (args.length == 0 || !args[0].equalsIgnoreCase("event")) {
                    send(sender, "<red>Usage: /create event [name]");
                } else {
                    events.create(sender, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                }
            }
            case "createevent" -> events.create(sender, String.join(" ", args));
            case "startevent" -> events.start(sender);
            case "endevent" -> events.cancel(sender);
            default -> {
                return false;
            }
        }
        return true;
    }
}
