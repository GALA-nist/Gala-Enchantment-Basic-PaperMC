package com.gala.geb.enchant;

import com.gala.geb.GEBPlugin;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
        file = new File(plugin.getDataFolder(), "systemenchantment.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
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
                int level = yaml.getInt(id + ".level", 1);
                enchants.put(id, new GEBEnchant(id, type, effect, kind, level));
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
            yaml.set(e.id() + ".level", e.level());
            yaml.set(e.id() + ".display", e.displayName());
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

    /** Creates (or overwrites) and saves. Returns the enchant. */
    public GEBEnchant create(GEBEnchant.Type type, String effectKey, ItemKind kind, int level) {
        String id = (effectKey + "_" + kind.name()).toLowerCase(Locale.ROOT);
        GEBEnchant enchant = new GEBEnchant(id, type, effectKey, kind, level);
        enchants.put(id, enchant);
        save();
        return enchant;
    }

    /** Removes an enchantment and instantly persists + reloads the registry. */
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

    /** True when this vanilla effect may be used on the given item category. */
    public boolean isAllowed(PotionEffectType effect, ItemKind.Category category) {
        String key = effect.getKey().getKey().toLowerCase(Locale.ROOT);
        List<String> global = plugin.getConfig().getStringList("blacklist.global");
        if (global.stream().anyMatch(s -> s.equalsIgnoreCase(key))) return false;

        boolean harmful = effect.getEffectCategory() == PotionEffectType.Category.HARMFUL;
        boolean beneficial = effect.getEffectCategory() == PotionEffectType.Category.BENEFICIAL;

        if (category == ItemKind.Category.WEAPON) {
            // Weapon effects hit the TARGET - beneficial ones would help the enemy.
            return !(beneficial && plugin.getConfig().getBoolean("blacklist.block-beneficial-on-weapons", true));
        }
        // Armor & tools buff the wearer - harmful ones would hurt the owner.
        return !(harmful && plugin.getConfig().getBoolean("blacklist.block-harmful-on-armor-and-tools", true));
    }

    /** All selectable vanilla effects for a category, blacklist already applied. */
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
    //  Item application (lore + PersistentDataContainer)
    // ------------------------------------------------------------------

    public List<GEBEnchant> getOnItem(ItemStack item) {
        List<GEBEnchant> result = new ArrayList<>();
        if (item == null || !item.hasItemMeta()) return result;
        String stored = item.getItemMeta().getPersistentDataContainer()
                .get(pdcKey, PersistentDataType.STRING);
        if (stored == null || stored.isEmpty()) return result;
        for (String id : stored.split(";")) {
            GEBEnchant e = enchants.get(id);
            if (e != null) result.add(e);
        }
        return result;
    }

    /** Applies the enchant to a real item (adds gray lore line + PDC tag). */
    public boolean apply(ItemStack item, GEBEnchant enchant) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType() != Material.ENCHANTED_BOOK && !enchant.kind().matches(item.getType())) return false;

        ItemMeta meta = item.getItemMeta();
        String stored = meta.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
        List<String> ids = new ArrayList<>();
        if (stored != null && !stored.isEmpty()) {
            for (String s : stored.split(";")) ids.add(s);
        }
        if (ids.contains(enchant.id())) return false; // already there
        ids.add(enchant.id());
        meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, String.join(";", ids));

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text(enchant.displayName(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return true;
    }

    /** Creates the enchanted book form of an enchantment. */
    public ItemStack createBook(GEBEnchant enchant) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, enchant.id());
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(enchant.displayName(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Applies to: " + enchant.kind().display(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        book.setItemMeta(meta);
        return book;
    }
}
