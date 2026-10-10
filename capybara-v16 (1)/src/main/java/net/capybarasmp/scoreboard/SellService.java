package net.capybarasmp.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

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

    private static boolean isShulker(ItemStack s) {
        return s.getType().name().endsWith("SHULKER_BOX");
    }

    /** Price of one normal stack, or null when it can't be sold (Skelly Spawners and unpriced items). */
    private BigInteger priceOf(ItemStack s) {
        if (spawners.isSkellyItem(s) || spawners.isDkaItem(s)) return null;
        BigInteger price = shop.sellPriceOrDefault(s.getType());
        return price == null ? null : price.multiply(BigInteger.valueOf(s.getAmount()));
    }

    /**
     * Removes (sets to null) every sellable stack in the array and returns what it was worth.
     * Shulker boxes are sold WITH everything inside them. If something inside can't be sold, that item stays in
     * the shulker and the shulker is given back (nothing is ever destroyed).
     */
    public Result sell(ItemStack[] stacks) {
        long items = 0;
        long skipped = 0;
        BigInteger total = BigInteger.ZERO;
        for (int i = 0; i < stacks.length; i++) {
            ItemStack s = stacks[i];
            if (s == null || s.getType().isAir()) continue;

            if (isShulker(s) && s.getItemMeta() instanceof BlockStateMeta bsm
                    && bsm.getBlockState() instanceof ShulkerBox box) {
                ItemStack[] inside = box.getInventory().getContents();
                long leftOver = 0;
                for (int j = 0; j < inside.length; j++) {
                    ItemStack in = inside[j];
                    if (in == null || in.getType().isAir()) continue;
                    BigInteger price = priceOf(in);
                    if (price == null) {
                        leftOver += in.getAmount();
                        continue;
                    }
                    total = total.add(price);
                    items += in.getAmount();
                    inside[j] = null;
                }
                BigInteger boxPrice = leftOver == 0 ? priceOf(s) : null;
                if (boxPrice != null) {
                    // everything inside was sold, now the empty shulker itself
                    total = total.add(boxPrice);
                    items += s.getAmount();
                    stacks[i] = null;
                } else {
                    // keep the shulker with what couldn't be sold; only write it back if something was sold
                    skipped += leftOver > 0 ? leftOver : s.getAmount();
                    box.getInventory().setContents(inside);
                    bsm.setBlockState(box);
                    s.setItemMeta(bsm);
                }
                continue;
            }

            BigInteger price = priceOf(s);
            if (price == null) {
                skipped += s.getAmount();
                continue;
            }
            total = total.add(price);
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
