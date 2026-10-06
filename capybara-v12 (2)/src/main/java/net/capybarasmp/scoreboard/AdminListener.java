package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.math.BigInteger;

/** Adds "/give money <name> <amount>" and "/give emerald <name> <amount>" for admins (intercepted before the normal /give). */
public final class AdminListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final Economy economy;
    private final EmeraldManager emerald;

    public AdminListener(Economy economy, EmeraldManager emerald) {
        this.economy = economy;
        this.emerald = emerald;
    }

    private String[] parse(String raw) {
        String m = raw.startsWith("/") ? raw.substring(1) : raw;
        String[] a = m.trim().split("\\s+");
        if (a.length < 2) return null;
        String label = a[0].toLowerCase();
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (!label.equals("give")) return null;
        if (!a[1].equalsIgnoreCase("money") && !a[1].equalsIgnoreCase("emerald")) return null;
        return a;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        String[] a = parse(e.getMessage());
        if (a == null || !CapybaraScoreboard.isAdmin(e.getPlayer())) return;
        e.setCancelled(true);
        handle(e.getPlayer(), a);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleCommand(ServerCommandEvent e) {
        String[] a = parse(e.getCommand());
        if (a == null) return;
        e.setCancelled(true);
        handle(e.getSender(), a);
    }

    private void handle(CommandSender sender, String[] a) {
        boolean isEmerald = a[1].equalsIgnoreCase("emerald");
        String kind = isEmerald ? "emerald" : "money";
        if (a.length < 4) {
            sender.sendMessage(MM.deserialize("<red>Usage: /give " + kind + " <name> <amount>"));
            return;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(a[2]);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(a[2]);
        if (target == null) {
            sender.sendMessage(MM.deserialize("<red>That player has never joined."));
            return;
        }
        BigInteger amount;
        try {
            amount = MoneyUtil.parse(a[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(MM.deserialize("<red>Invalid amount. Examples: 500, 1.5K, 2M, 3B, 4T, 5QA"));
            return;
        }
        if (amount.signum() <= 0) {
            sender.sendMessage(MM.deserialize("<red>Amount must be above 0."));
            return;
        }

        if (isEmerald) {
            // Emerald is stored as a long, so cap at the maximum
            BigInteger cap = BigInteger.valueOf(Long.MAX_VALUE / 2);
            long give = amount.min(cap).longValue();
            emerald.add(target.getUniqueId(), give);
            String shown = MoneyUtil.format(BigInteger.valueOf(give));
            sender.sendMessage(MM.deserialize("<green>Gave <#17dd62>◆ <amt> <cur> <green>to <white><name><green>.",
                    Placeholder.unparsed("amt", shown),
                    Placeholder.unparsed("cur", emerald.currency()),
                    Placeholder.unparsed("name", a[2])));
            if (target.getPlayer() != null) {
                target.getPlayer().sendMessage(MM.deserialize("<green>You received <#17dd62>◆ <amt> <cur><green>.",
                        Placeholder.unparsed("amt", shown),
                        Placeholder.unparsed("cur", emerald.currency())));
            }
            return;
        }

        economy.add(target.getUniqueId(), amount);
        sender.sendMessage(MM.deserialize("<green>Gave <yellow>$<amt> <green>to <white><name><green>.",
                Placeholder.unparsed("amt", MoneyUtil.format(amount)),
                Placeholder.unparsed("name", a[2])));
        if (target.getPlayer() != null) {
            target.getPlayer().sendMessage(MM.deserialize("<green>You received <yellow>$<amt><green>.",
                    Placeholder.unparsed("amt", MoneyUtil.format(amount))));
        }
    }
}
