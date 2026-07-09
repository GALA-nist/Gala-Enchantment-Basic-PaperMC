package com.gala.geb.enchant;

import com.gala.geb.GEBPlugin;
import com.gala.geb.util.Roman;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EnchantManager {

    /** Prefix for description lore lines, also used to recognize our own lines. */
    private static final String DESC_PREFIX = "\u2726 "; // ✦

    private final GEBPlugin plugin;
    private final NamespacedKey pdcKey;
    private final Map<String, GEBEnchant> enchants = new LinkedHashMap<>();
    private File file;

    public EnchantManager(GEBPlugin plugin) {
        this.plugin = plugin;
        this.pdcKey = new NamespacedKey(plugin, "geb_enchants");
    }

    // ------------------------------------------------------------------
    //  Loading / saving  (systemenchantment.yml)
    // ------------------------------------------------------------------

    public void reload() {
        enchants.clear();
        file = new File(plugin.getDataDir(), "systemenchantment.yml");
        if (!file.exists()) {
            plugin.getDataDir().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create systemenchantment.yml: " + e.getMessage());
                return;
            }
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getKeys(false)) {
            try {
                GEBEnchant.Type type = GEBEnchant.Type.valueOf(yaml.getString(id + ".type", "EFFECT"));
                String effect = yaml.getString(id + ".effect", "");
                ItemKind kind = ItemKind.valueOf(yaml.getString(id + ".item", ""));
                // "max-level" is current; "level" kept for backwards compatibility
                int maxLevel = yaml.getInt(id + ".max-level", yaml.getInt(id + ".level", 1));
                enchants.put(id, new GEBEnchant(id, type, effect, kind, maxLevel));
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping invalid enchantment entry '" + id + "' in systemenchantment.yml");
            }
        }
        plugin.getLogger().info("Loaded " + enchants.size() + " custom enchantment(s).");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (GEBEnchant e : enchants.values()) {
            yaml.set(e.id() + ".type", e.type().name());
            yaml.set(e.id() + ".effect", e.effectKey());
            yaml.set(e.id() + ".item", e.kind().name());
            yaml.set(e.id() + ".max-level", e.maxLevel());
            yaml.set(e.id() + ".display", e.baseDisplay() + " (max " + Roman.toRoman(e.maxLevel()) + ")");
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save systemenchantment.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //  Registry operations
    // ------------------------------------------------------------------

    public Map<String, GEBEnchant> all() { return enchants; }

    public GEBEnchant get(String id) { return enchants.get(id); }

    public GEBEnchant create(GEBEnchant.Type type, String effectKey, ItemKind kind, int maxLevel) {
        String id = (effectKey + "_" + kind.name()).toLowerCase(Locale.ROOT);
        GEBEnchant enchant = new GEBEnchant(id, type, effectKey, kind, maxLevel);
        enchants.put(id, enchant);
        save();
        return enchant;
    }

    public boolean remove(String id) {
        if (enchants.remove(id) == null) return false;
        save();
        reload(); // instant reload, no command needed
        return true;
    }

    public List<GEBEnchant> forKind(ItemKind kind) {
        List<GEBEnchant> list = new ArrayList<>();
        for (GEBEnchant e : enchants.values()) {
            if (e.kind() == kind) list.add(e);
        }
        return list;
    }

    // ------------------------------------------------------------------
    //  Blacklist
    // ------------------------------------------------------------------

    public boolean isAllowed(PotionEffectType effect, ItemKind.Category category) {
        String key = effect.getKey().getKey().toLowerCase(Locale.ROOT);
        List<String> global = plugin.getConfig().getStringList("blacklist.global");
        if (global.stream().anyMatch(s -> s.equalsIgnoreCase(key))) return false;

        boolean harmful = effect.getEffectCategory() == PotionEffectType.Category.HARMFUL;
        boolean beneficial = effect.getEffectCategory() == PotionEffectType.Category.BENEFICIAL;

        if (category == ItemKind.Category.WEAPON) {
            return !(beneficial && plugin.getConfig().getBoolean("blacklist.block-beneficial-on-weapons", true));
        }
        return !(harmful && plugin.getConfig().getBoolean("blacklist.block-harmful-on-armor-and-tools", true));
    }

    public List<PotionEffectType> selectableEffects(ItemKind.Category category) {
        List<PotionEffectType> list = new ArrayList<>();
        for (PotionEffectType effect : RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT)) {
            if (isAllowed(effect, category)) list.add(effect);
        }
        list.sort((a, b) -> a.getKey().getKey().compareTo(b.getKey().getKey()));
        return list;
    }

    public PotionEffectType effectByKey(String key) {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT)
                .get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
    }

    // ------------------------------------------------------------------
    //  Lore styling
    // ------------------------------------------------------------------

    /** Name color: green = buff, red = offensive, aqua = neutral, purple = custom. */
    public NamedTextColor nameColor(GEBEnchant enchant) {
        if (enchant.type() == GEBEnchant.Type.CUSTOM) return NamedTextColor.LIGHT_PURPLE;
        PotionEffectType effect = effectByKey(enchant.effectKey());
        if (effect == null) return NamedTextColor.GRAY;
        return switch (effect.getEffectCategory()) {
            case BENEFICIAL -> NamedTextColor.GREEN;
            case HARMFUL -> NamedTextColor.RED;
            default -> NamedTextColor.AQUA;
        };
    }

    /** Context description, e.g. "When worn: grants Regeneration." */
    public String description(GEBEnchant enchant) {
        if (enchant.type() == GEBEnchant.Type.CUSTOM) {
            return CustomEffect.valueOf(enchant.effectKey()).description();
        }
        String effect = Roman.prettify(enchant.effectKey());
        return switch (enchant.kind().category()) {
            case ARMOR -> "When worn: grants " + effect + ".";
            case TOOL -> "When held: grants " + effect + ".";
            case WEAPON -> "On hit: applies " + effect + " (5s per level).";
        };
    }

    // ------------------------------------------------------------------
    //  Item storage (PDC "id:level;id:level" + styled lore + glint)
    // ------------------------------------------------------------------

    /** All GEB enchants on an item with their levels. */
    public Map<GEBEnchant, Integer> getOnItem(ItemStack item) {
        Map<GEBEnchant, Integer> result = new LinkedHashMap<>();
        if (item == null || !item.hasItemMeta()) return result;
        String stored = item.getItemMeta().getPersistentDataContainer()
                .get(pdcKey, PersistentDataType.STRING);
        if (stored == null || stored.isEmpty()) return result;
        for (String entry : stored.split(";")) {
            String[] parts = entry.split(":");
            GEBEnchant e = enchants.get(parts[0]);
            if (e == null) continue;
            int level = 1;
            if (parts.length > 1) {
                try {
                    level = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) { }
            }
            result.put(e, Math.max(1, Math.min(level, e.maxLevel())));
        }
        return result;
    }

    /**
     * Sets the enchant on the item at the given level (overwrites lower levels),
     * refreshes lore and adds the enchantment glint. Returns true if changed.
     */
    public boolean setEnchant(ItemStack item, GEBEnchant enchant, int level) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType() != Material.ENCHANTED_BOOK && !enchant.kind().matches(item.getType())) return false;

        level = Math.max(1, Math.min(level, enchant.maxLevel()));
        Map<GEBEnchant, Integer> current = getOnItem(item);
        Integer existing = current.get(enchant);
        if (existing != null && existing >= level) return false;

        current.put(enchant, level);
        writeEnchants(item, current);
        return true;
    }

    private void writeEnchants(ItemStack item, Map<GEBEnchant, Integer> map) {
        ItemMeta meta = item.getItemMeta();

        // PDC
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<GEBEnchant, Integer> entry : map.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(entry.getKey().id()).append(':').append(entry.getValue());
        }
        meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, sb.toString());

        // Lore: strip our old lines, then append fresh styled block
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.removeIf(this::isGebLoreLine);
        for (Map.Entry<GEBEnchant, Integer> entry : map.entrySet()) {
            GEBEnchant e = entry.getKey();
            lore.add(Component.text(e.displayName(entry.getValue()), nameColor(e))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(DESC_PREFIX + description(e), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        // Enchantment glint (shimmer) even without vanilla enchants
        meta.setEnchantmentGlintOverride(true);

        item.setItemMeta(meta);
    }

    /** Recognizes lore lines previously written by GEB so they can be replaced. */
    private boolean isGebLoreLine(Component line) {
        String plain = PlainTextComponentSerializer.plainText().serialize(line);
        if (plain.startsWith(DESC_PREFIX)) return true;
        for (GEBEnchant e : enchants.values()) {
            String base = e.baseDisplay() + " ";
            if (plain.startsWith(base) && plain.substring(base.length()).matches("[IVXLCDM]+")) {
                return true;
            }
        }
        return false;
    }

    /** Creates the enchanted book form of an enchantment at a given level. */
    public ItemStack createBook(GEBEnchant enchant, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        level = Math.max(1, Math.min(level, enchant.maxLevel()));
        Map<GEBEnchant, Integer> map = new LinkedHashMap<>();
        map.put(enchant, level);
        writeEnchants(book, map);

        ItemMeta meta = book.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.lore());
        lore.add(Component.text("Applies to: " + enchant.kind().display(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        book.setItemMeta(meta);
        return book;
    }
}
