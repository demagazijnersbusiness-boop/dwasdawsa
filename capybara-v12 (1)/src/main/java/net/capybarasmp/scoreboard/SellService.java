package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;

public final class SellService {

    public record Result(long items, BigInteger total, long skipped) {}

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ShopManager shop;
    private final Economy economy;
    private final SpawnerManager spawners;

    public SellService(ShopManager shop, Economy economy, SpawnerManager spawners) {
        this.shop = shop;
        this.economy = economy;
        this.spawners = spawners;
    }

    /** Removes (sets to null) every sellable stack in the array and returns what it was worth. */
    public Result sell(ItemStack[] stacks) {
        long items = 0;
        long skipped = 0;
        BigInteger total = BigInteger.ZERO;
        for (int i = 0; i < stacks.length; i++) {
            ItemStack s = stacks[i];
            if (s == null || s.getType().isAir()) continue;

            // never sell Skelly Spawners, and never destroy the contents of a shulker box
            boolean protectedItem = spawners.isSkellyItem(s)
                    || (s.getType().name().endsWith("SHULKER_BOX") && s.hasItemMeta());
            BigInteger price = protectedItem ? null : shop.sellPriceOrDefault(s.getType());
            if (price == null) {
                skipped += s.getAmount();
                continue;
            }
            total = total.add(price.multiply(BigInteger.valueOf(s.getAmount())));
            items += s.getAmount();
            stacks[i] = null;
        }
        return new Result(items, total, skipped);
    }

    public void payout(Player p, Result r) {
        if (r.items() == 0) {
            p.sendMessage(MM.deserialize("<red>You have nothing sellable."));
        } else {
            economy.add(p.getUniqueId(), r.total());
            p.sendMessage(MM.deserialize("<green>Sold <white><n> items <green>for <yellow>$<amt><green>.",
                    Placeholder.unparsed("n", String.valueOf(r.items())),
                    Placeholder.unparsed("amt", MoneyUtil.format(r.total()))));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
        if (r.skipped() > 0) {
            p.sendMessage(MM.deserialize("<gray><n> items can't be sold (protected items).",
                    Placeholder.unparsed("n", String.valueOf(r.skipped()))));
        }
    }
}
