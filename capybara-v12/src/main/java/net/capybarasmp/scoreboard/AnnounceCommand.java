package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

/** /announce <text>: everyone sees the admin's head + name on top of the screen, plus what they said. */
public final class AnnounceCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final CapybaraScoreboard plugin;

    public AnnounceCommand(CapybaraScoreboard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CapybaraScoreboard.isAdmin(sender)) {
            sender.sendMessage(MM.deserialize("<red>You don't have permission."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(MM.deserialize("<red>Usage: /announce <text>"));
            return true;
        }

        String text = String.join(" ", args);
        String name = sender instanceof Player p ? p.getName() : "Server";

        // <head:Name> shows the player's face (Minecraft 1.21.9+); names only contain letters, digits and _
        String head = sender instanceof Player ? "<head:" + name + "> " : "";
        Component top = MM.deserialize(head + "<gold><bold><n>", Placeholder.unparsed("n", name));
        Component message = MM.deserialize("<white><m>", Placeholder.unparsed("m", text));

        int seconds = Math.max(2, plugin.getConfig().getInt("announce-seconds", 7));
        Title title = Title.title(top, message,
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(seconds), Duration.ofSeconds(1)));

        Component chat = MM.deserialize(
                "<gold><bold>ANNOUNCEMENT</bold> " + head + "<yellow><n><gray>: <white><m>",
                Placeholder.unparsed("n", name), Placeholder.unparsed("m", text));

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(title);
            online.sendMessage(chat);
            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
        }
        Bukkit.getConsoleSender().sendMessage(chat);
        return true;
    }
}
