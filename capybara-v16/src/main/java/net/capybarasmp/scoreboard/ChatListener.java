package net.capybarasmp.scoreboard;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {

    private final CapybaraScoreboard plugin;
    private final DataManager data;
    private final RankManager ranks;
    private final ShopGui gui;

    public ChatListener(CapybaraScoreboard plugin, DataManager data, RankManager ranks, ShopGui gui) {
        this.plugin = plugin;
        this.data = data;
        this.ranks = ranks;
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();

        // shop search: the next chat message is the search text
        if (gui.isAwaitingSearch(p)) {
            e.setCancelled(true);
            gui.stopAwaitingSearch(p);
            String query = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (query.isEmpty() || query.equalsIgnoreCase("cancel")) gui.openMenu(p);
                else gui.openSearchResults(p, query);
            });
            return;
        }

        // rank prefix in chat
        String rank = data.get(p.getUniqueId()).rank;
        if (!ranks.exists(rank)) return;
        e.renderer(ChatRenderer.viewerUnaware((source, name, message) ->
                ranks.prefix(rank)
                        .append(name.color(ranks.color(rank)))
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(message)));
    }
}
