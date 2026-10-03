package net.capybarasmp.scoreboard;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShopManager {

    public record ShopItem(Material material, BigInteger price, boolean skelly) {}

    public record Category(String id, String name, Material icon, List<ShopItem> items) {}

    private final CapybaraScoreboard plugin;
    private final Map<Material, BigInteger> sellPrices = new HashMap<>();
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private BigInteger defaultSell = BigInteger.ONE;
    private final Set<Material> blocked = new HashSet<>();

    public ShopManager(CapybaraScoreboard plugin) {
        this.plugin = plugin;
    }

    public void load() {
        sellPrices.clear();
        categories.clear();
        blocked.clear();

        File f = new File(plugin.getDataFolder(), "shop.yml");
        if (!f.exists()) plugin.saveResource("shop.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

        ConfigurationSection sell = y.getConfigurationSection("sell");
        if (sell != null) {
            for (String key : sell.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                BigInteger price = price(sell.getString(key));
                if (m == null || !m.isItem() || price == null) {
                    plugin.getLogger().warning("shop.yml: invalid sell entry '" + key + "'");
                    continue;
                }
                sellPrices.put(m, price);
            }
        }

        // items that can never be bought (names that do not exist in this version are ignored)
        for (String name : y.getStringList("blocked-items")) {
            Material bm = Material.matchMaterial(name);
            if (bm != null) blocked.add(bm);
        }

        int percent = Math.max(0, y.getInt("sell-percent", 25));
        BigInteger def = price(y.getString("default-sell-price", "1"));
        defaultSell = def == null ? BigInteger.ZERO : def;

        ConfigurationSection cats = y.getConfigurationSection("categories");
        if (cats != null) {
            for (String id : cats.getKeys(false)) {
                ConfigurationSection cs = cats.getConfigurationSection(id);
                if (cs == null) continue;
                Material icon = Material.matchMaterial(cs.getString("icon", "CHEST"));
                if (icon == null || !icon.isItem()) icon = Material.CHEST;
                List<ShopItem> items = new ArrayList<>();
                ConfigurationSection is = cs.getConfigurationSection("items");
                if (is != null) {
                    for (String key : is.getKeys(false)) {
                        BigInteger price = price(is.getString(key));
                        if (price == null) {
                            plugin.getLogger().warning("shop.yml: invalid price for '" + key + "' in " + id);
                            continue;
                        }
                        if (key.equalsIgnoreCase("SKELLY_SPAWNER")) {
                            items.add(new ShopItem(Material.SPAWNER, price, true));
                            continue;
                        }
                        Material m = Material.matchMaterial(key);
                        if (m == null || !m.isItem()) {
                            plugin.getLogger().warning("shop.yml: invalid shop entry '" + key + "' in " + id);
                            continue;
                        }
                        if (blocked.contains(m)) continue;
                        items.add(new ShopItem(m, price, false));
                        // automatic sell price when none is set
                        if (!sellPrices.containsKey(m) && percent > 0) {
                            BigInteger auto = price.multiply(BigInteger.valueOf(percent)).divide(BigInteger.valueOf(100));
                            if (auto.signum() <= 0) auto = BigInteger.ONE;
                            sellPrices.put(m, auto);
                        }
                    }
                }
                categories.put(id, new Category(id, cs.getString("name", id), icon, items));
            }
        }
        addAutoCategories(y);
        plugin.getLogger().info("Loaded " + sellPrices.size() + " sell prices and " + categories.size() + " shop categories.");
    }

    // ------------------------------------------------------------ every item in the game

    private BigInteger bi(String s, String fallback) {
        BigInteger b = price(s);
        return b != null ? b : price(fallback);
    }

    private static boolean excluded(Material m) {
        String n = m.name();
        if (n.startsWith("LEGACY_") || !m.isItem() || m.isAir()) return true;
        return n.contains("COMMAND_BLOCK") || n.startsWith("STRUCTURE_") || n.startsWith("TEST_")
                || n.equals("JIGSAW") || n.equals("BARRIER") || n.equals("BEDROCK") || n.equals("LIGHT")
                || n.equals("DEBUG_STICK") || n.equals("KNOWLEDGE_BOOK") || n.equals("SPAWNER")
                || n.equals("TRIAL_SPAWNER") || n.equals("VAULT") || n.equals("REINFORCED_DEEPSLATE")
                || n.equals("FILLED_MAP") || n.equals("WRITTEN_BOOK") || n.equals("ENCHANTED_BOOK")
                || n.equals("END_PORTAL_FRAME");
    }

    private static BigInteger autoPrice(Material m, BigInteger base) {
        String n = m.name();
        if (n.contains("NETHERITE")) return base.max(BigInteger.valueOf(50_000));
        if (n.contains("DIAMOND")) return base.max(BigInteger.valueOf(5_000));
        if (n.contains("GOLD")) return base.max(BigInteger.valueOf(1_000));
        return base;
    }

    /** Every item that is not in a category of shop.yml gets added to automatic categories. */
    private void addAutoCategories(YamlConfiguration y) {
        if (!y.getBoolean("auto-all-items", true)) return;
        BigInteger defaultBuy = bi(y.getString("default-buy-price", "200"), "200");
        BigInteger eggPrice = bi(y.getString("spawn-egg-price", "25K"), "25K");
        int autoPercent = Math.max(0, y.getInt("auto-sell-percent", 10));

        Set<Material> listed = new HashSet<>();
        for (Category c : categories.values()) {
            for (ShopItem si : c.items()) listed.add(si.material());
        }

        Map<String, List<ShopItem>> groups = new LinkedHashMap<>();
        groups.put("auto_blocks", new ArrayList<>());
        groups.put("auto_deco", new ArrayList<>());
        groups.put("auto_items", new ArrayList<>());
        groups.put("auto_eggs", new ArrayList<>());

        for (Material m : Material.values()) {
            if (listed.contains(m) || excluded(m) || blocked.contains(m)) continue;
            BigInteger price;
            String group;
            if (m.name().endsWith("_SPAWN_EGG")) {
                group = "auto_eggs";
                price = eggPrice;
            } else {
                price = autoPrice(m, defaultBuy);
                if (!m.isBlock()) group = "auto_items";
                else group = m.isSolid() ? "auto_blocks" : "auto_deco";
            }
            groups.get(group).add(new ShopItem(m, price, false));
            if (!sellPrices.containsKey(m) && autoPercent > 0) {
                BigInteger sell = price.multiply(BigInteger.valueOf(autoPercent)).divide(BigInteger.valueOf(100));
                sellPrices.put(m, sell.signum() > 0 ? sell : BigInteger.ONE);
            }
        }

        Map<String, String> names = Map.of(
                "auto_blocks", "All Blocks",
                "auto_deco", "Plants & Decoration+",
                "auto_items", "All Other Items",
                "auto_eggs", "Spawn Eggs");
        Map<String, Material> icons = Map.of(
                "auto_blocks", Material.GRASS_BLOCK,
                "auto_deco", Material.POPPY,
                "auto_items", Material.NETHER_STAR,
                "auto_eggs", Material.ZOMBIE_SPAWN_EGG);

        for (Map.Entry<String, List<ShopItem>> e : groups.entrySet()) {
            List<ShopItem> list = e.getValue();
            if (list.isEmpty()) continue;
            list.sort(Comparator.comparing(si -> si.material().name()));
            categories.put(e.getKey(), new Category(e.getKey(), names.get(e.getKey()), icons.get(e.getKey()), list));
        }
    }

    private BigInteger price(String s) {
        if (s == null) return null;
        try {
            BigInteger b = MoneyUtil.parse(s);
            return b.signum() > 0 ? b : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public BigInteger sellPrice(Material m) {
        return sellPrices.get(m);
    }

    /** Listed sell price, or the default-sell-price for items that are not listed (null = cannot be sold). */
    public BigInteger sellPriceOrDefault(Material m) {
        BigInteger p = sellPrices.get(m);
        if (p != null) return p;
        return defaultSell.signum() > 0 ? defaultSell : null;
    }

    public Collection<Category> categories() {
        return categories.values();
    }

    public Category category(String id) {
        return categories.get(id);
    }

    /** Finds every shop item whose name contains all the words of the query. */
    public List<ShopItem> search(String query) {
        String q = query == null ? "" : query.toLowerCase().trim();
        List<ShopItem> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        String[] tokens = q.split("\\s+");
        Set<String> seen = new HashSet<>();
        for (Category c : categories.values()) {
            for (ShopItem si : c.items()) {
                String name = si.skelly() ? "skelly spawner" : si.material().name().toLowerCase().replace('_', ' ');
                boolean ok = true;
                for (String t : tokens) {
                    if (!name.contains(t)) {
                        ok = false;
                        break;
                    }
                }
                String key = si.skelly() ? "skelly" : si.material().name();
                if (ok && seen.add(key)) out.add(si);
            }
        }
        return out;
    }

    public static String pretty(Material m) {
        String[] parts = m.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
