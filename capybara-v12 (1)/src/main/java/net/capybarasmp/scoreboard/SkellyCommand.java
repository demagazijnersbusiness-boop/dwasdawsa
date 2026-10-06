package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class SkellyCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final SpawnerManager spawners;

    public SkellyCommand(SpawnerManager spawners) {
        this.spawners = spawners;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CapybaraScoreboard.isAdmin(sender)) {
            sender.sendMessage(MM.deserialize("<red>You don't have permission."));
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(MM.deserialize("<red>Usage: /skelly give <player> [amount]"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MM.deserialize("<red>That player is not online."));
            return true;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                sender.sendMessage(MM.deserialize("<red>Amount must be a number."));
                return true;
            }
        }
        ItemStack item = spawners.createItem(amount);
        target.getInventory().addItem(item).values()
                .forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        sender.sendMessage(MM.deserialize("<green>Gave " + amount + " Skelly Spawner(s) to " + target.getName() + "."));
        return true;
    }
}
