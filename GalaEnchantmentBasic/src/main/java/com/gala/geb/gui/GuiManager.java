package com.gala.geb.gui;

import com.gala.geb.GEBPlugin;
import com.gala.geb.enchant.CustomEffect;
import com.gala.geb.enchant.GEBEnchant;
import com.gala.geb.enchant.ItemKind;
import com.gala.geb.util.Roman;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuiManager {

    public static final int SIZE = 54;

    // Fixed control slots (bottom row)
    public static final int SLOT_BACK = 45;
    public static final int SLOT_PREV = 48;
    public static final int SLOT_NEXT = 50;
    public static final int SLOT_EXIT = 53;

    // Confirm page slots
    public static final int SLOT_ACCEPT = 29;
    public static final int SLOT_CONFIRM_BACK = 31;
    public static final int SLOT_DECLINE = 33;

    private final GEBPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public GuiManager(GEBPlugin plugin) {
        this.plugin = plugin;
    }

    public Session session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), u -> new Session());
    }

    public void clearSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    //  Item helpers
    // ------------------------------------------------------------------

    private ItemStack button(Material material, String name, NamedTextColor color, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (loreLines.length > 0) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, pane);
        }
    }

    private void controls(Inventory inv, boolean withBack) {
        if (withBack) {
            inv.setItem(SLOT_BACK, button(Material.ARROW, "Back", NamedTextColor.YELLOW, "Go to the previous page"));
        }
        inv.setItem(SLOT_EXIT, button(Material.BARRIER, "Exit", NamedTextColor.RED, "Close this menu"));
    }

    private Inventory make(GebHolder.Page page, String title) {
        GebHolder holder = new GebHolder(page);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                Component.text(title, NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        holder.setInventory(inv);
        return inv;
    }

    // ------------------------------------------------------------------
    //  Page 1: item select (tools / weapons / armor)
    // ------------------------------------------------------------------

    public void openItemSelect(Player player) {
        session(player).resetToItemSelect();
        Inventory inv = make(GebHolder.Page.ITEM_SELECT, "GEB » Choose an item");

        int tool = 0, weapon = 9, armor = 18;
        for (ItemKind kind : ItemKind.values()) {
            int slot = switch (kind.category()) {
                case TOOL -> tool++;
                case WEAPON -> weapon++;
                case ARMOR -> armor++;
            };
            inv.setItem(slot, button(kind.icon(), kind.display(), NamedTextColor.AQUA,
                    "Category: " + Roman.prettify(kind.category().name()),
                    "Click to create an enchantment", "for this item type."));
        }
        controls(inv, false);
        fill(inv);
        player.openInventory(inv);
    }

    // ------------------------------------------------------------------
    //  Page 2: Effect or Custom
    // ------------------------------------------------------------------

    public void openOptionSelect(Player player) {
        Session s = session(player);
        Inventory inv = make(GebHolder.Page.OPTION_SELECT, "GEB » " + s.kind.display() + " » Type");
        inv.setItem(21, button(Material.POTION, "Effect", NamedTextColor.GREEN,
                "Use a vanilla Minecraft effect", "(Regeneration, Speed, Poison, ...)"));
        inv.setItem(23, button(Material.NETHER_STAR, "Custom", NamedTextColor.LIGHT_PURPLE,
                "Use a special GEB effect", "(Bloodlust, Ignite, ...)"));
        controls(inv, true);
        fill(inv);
        player.openInventory(inv);
    }

    // ------------------------------------------------------------------
    //  Page 3a: vanilla effect list (paginated, blacklist applied)
    // ------------------------------------------------------------------

    public void openEffectList(Player player) {
        Session s = session(player);
        List<PotionEffectType> effects = plugin.enchants().selectableEffects(s.kind.category());

        int perPage = 36;
        int pages = Math.max(1, (int) Math.ceil(effects.size() / (double) perPage));
        if (s.effectPage >= pages) s.effectPage = pages - 1;
        if (s.effectPage < 0) s.effectPage = 0;

        Inventory inv = make(GebHolder.Page.EFFECT_LIST,
                "GEB » " + s.kind.display() + " » Effect (" + (s.effectPage + 1) + "/" + pages + ")");

        int start = s.effectPage * perPage;
        for (int i = 0; i < perPage && start + i < effects.size(); i++) {
            PotionEffectType effect = effects.get(start + i);
            String key = effect.getKey().getKey();
            inv.setItem(i, button(iconFor(effect), Roman.prettify(key), NamedTextColor.GREEN,
                    "Vanilla effect: minecraft:" + key,
                    "Click to choose this effect."));
        }
        if (s.effectPage > 0)
            inv.setItem(SLOT_PREV, button(Material.PAPER, "Previous page", NamedTextColor.YELLOW));
        if (s.effectPage < pages - 1)
            inv.setItem(SLOT_NEXT, button(Material.PAPER, "Next page", NamedTextColor.YELLOW));
        controls(inv, true);
        fill(inv);
        player.openInventory(inv);
    }

    private Material iconFor(PotionEffectType effect) {
        return switch (effect.getEffectCategory()) {
            case BENEFICIAL -> Material.SPLASH_POTION;
            case HARMFUL -> Material.LINGERING_POTION;
            default -> Material.POTION;
        };
    }

    // ------------------------------------------------------------------
    //  Page 3b: custom effect list
    // ------------------------------------------------------------------

    public void openCustomList(Player player) {
        Session s = session(player);
        Inventory inv = make(GebHolder.Page.CUSTOM_LIST, "GEB » " + s.kind.display() + " » Custom");
        int slot = 10;
        for (CustomEffect effect : CustomEffect.values()) {
            if (!effect.allows(s.kind.category())) continue;
            inv.setItem(slot, button(effect.icon(), effect.display(), NamedTextColor.LIGHT_PURPLE,
                    effect.description(),
                    "Max level: " + Roman.toRoman(effect.maxLevel())));
            slot += 2;
        }
        if (slot == 10) {
            inv.setItem(22, button(Material.STRUCTURE_VOID, "No custom effects available",
                    NamedTextColor.RED, "No custom effect supports", s.kind.display() + " items."));
        }
        controls(inv, true);
        fill(inv);
        player.openInventory(inv);
    }

    // ------------------------------------------------------------------
    //  Page 4: level select
    // ------------------------------------------------------------------

    public void openLevelSelect(Player player) {
        Session s = session(player);
        int max = s.type == GEBEnchant.Type.CUSTOM
                ? s.customEffect.maxLevel()
                : plugin.getConfig().getInt("max-level", 11);
        max = Math.max(1, Math.min(max, 36));

        String effectName = s.type == GEBEnchant.Type.CUSTOM
                ? s.customEffect.display() : Roman.prettify(s.effectKey);

        Inventory inv = make(GebHolder.Page.LEVEL_SELECT, "GEB » " + effectName + " » Level");
        for (int lvl = 1; lvl <= max; lvl++) {
            ItemStack item = button(Material.EXPERIENCE_BOTTLE,
                    effectName + " " + Roman.toRoman(lvl), NamedTextColor.GOLD,
                    "Click to pick level " + Roman.toRoman(lvl));
            item.setAmount(Math.min(lvl, 64));
            inv.setItem(lvl - 1, item);
        }
        controls(inv, true);
        fill(inv);
        player.openInventory(inv);
    }

    // ------------------------------------------------------------------
    //  Page 5: result / confirm tab
    // ------------------------------------------------------------------

    public void openConfirm(Player player) {
        Session s = session(player);
        String effectName = s.type == GEBEnchant.Type.CUSTOM
                ? s.customEffect.display() : Roman.prettify(s.effectKey);

        Inventory inv = make(GebHolder.Page.CONFIRM, "GEB » Confirm enchantment");

        // Summary item in the middle top
        inv.setItem(13, button(s.kind.icon(), effectName + " " + Roman.toRoman(s.level), NamedTextColor.AQUA,
                "Item: " + s.kind.display() + " (" + Roman.prettify(s.kind.category().name()) + ")",
                "Type: " + (s.type == GEBEnchant.Type.CUSTOM ? "Custom" : "Vanilla effect"),
                "Effect: " + effectName,
                "Level: " + Roman.toRoman(s.level)));

        inv.setItem(SLOT_ACCEPT, button(Material.LIME_WOOL, "Accept", NamedTextColor.GREEN,
                "Create this enchantment and", "save it to systemenchantment.yml"));
        inv.setItem(SLOT_CONFIRM_BACK, button(Material.ARROW, "Back", NamedTextColor.YELLOW,
                "Return to the level selection"));
        inv.setItem(SLOT_DECLINE, button(Material.RED_WOOL, "Decline", NamedTextColor.RED,
                "Cancel everything and return", "to the item selection (page 1)"));

        inv.setItem(SLOT_EXIT, button(Material.BARRIER, "Exit", NamedTextColor.RED, "Close this menu"));
        fill(inv);
        player.openInventory(inv);
    }

    // ------------------------------------------------------------------
    //  Remove GUI  (/geb remove)
    // ------------------------------------------------------------------

    public void openRemoveList(Player player) {
        Session s = session(player);
        List<GEBEnchant> list = new ArrayList<>(plugin.enchants().all().values());

        int perPage = 36;
        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) perPage));
        if (s.removePage >= pages) s.removePage = pages - 1;
        if (s.removePage < 0) s.removePage = 0;

        Inventory inv = make(GebHolder.Page.REMOVE_LIST,
                "GEB » Remove enchantment (" + (s.removePage + 1) + "/" + pages + ")");

        if (list.isEmpty()) {
            inv.setItem(22, button(Material.STRUCTURE_VOID, "No enchantments created yet",
                    NamedTextColor.RED, "Use /geb create first."));
        }
        int start = s.removePage * perPage;
        for (int i = 0; i < perPage && start + i < list.size(); i++) {
            GEBEnchant e = list.get(start + i);
            inv.setItem(i, button(e.kind().icon(), e.displayName(), NamedTextColor.RED,
                    "Item: " + e.kind().display(),
                    "ID: " + e.id(),
                    "Click to REMOVE this enchantment.",
                    "(Removal reloads instantly)"));
        }
        if (s.removePage > 0)
            inv.setItem(SLOT_PREV, button(Material.PAPER, "Previous page", NamedTextColor.YELLOW));
        if (s.removePage < pages - 1)
            inv.setItem(SLOT_NEXT, button(Material.PAPER, "Next page", NamedTextColor.YELLOW));
        controls(inv, false);
        fill(inv);
        player.openInventory(inv);
    }
}
