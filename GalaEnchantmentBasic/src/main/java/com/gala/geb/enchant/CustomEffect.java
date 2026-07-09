package com.gala.geb.enchant;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/**
 * Hard-coded "Custom" enchantment behaviors (the non-vanilla-effect option).
 * Each one declares exactly which item kinds it can be created for.
 * New ones can be added here and handled in the listeners / tasks.
 */
public enum CustomEffect {

    /**
     * Weapon only. When you hit ANY entity (player, hostile or passive mob),
     * the ATTACKER receives Instant Health I. Max level I.
     */
    BLOODLUST("Bloodlust", Material.REDSTONE, 1,
            weapons(),
            "On hit: heals you with Instant Health I."),

    /**
     * Weapon only. Sets the target on fire for 3s per level. Max level III.
     */
    IGNITE("Ignite", Material.BLAZE_POWDER, 3,
            weapons(),
            "On hit: sets the target on fire (3s per level)."),

    /**
     * Tool only. Breaking a block restores a little hunger. Max level II.
     */
    HARVESTERS_MEAL("Harvester's Meal", Material.BREAD, 2,
            tools(),
            "On block break: small chance to restore hunger."),

    /**
     * Boots only. While worn, double-jump to start flying (like creative
     * flight). Take the boots off and the flight is gone. Max level I.
     */
    DREAMCATCHER("Dreamcatcher", Material.FEATHER, 1,
            EnumSet.of(ItemKind.BOOTS),
            "While worn: double-jump to fly.");

    private final String display;
    private final Material icon;
    private final int maxLevel;
    private final Set<ItemKind> allowed;
    private final String description;

    CustomEffect(String display, Material icon, int maxLevel,
                 Set<ItemKind> allowed, String description) {
        this.display = display;
        this.icon = icon;
        this.maxLevel = maxLevel;
        this.allowed = allowed;
        this.description = description;
    }

    public String display()      { return display; }
    public Material icon()       { return icon; }
    public int maxLevel()        { return maxLevel; }
    public String description()  { return description; }

    public boolean allows(ItemKind kind) {
        return allowed.contains(kind);
    }

    // ---- helpers to build kind sets from categories ----

    private static Set<ItemKind> weapons()  { return ofCategory(ItemKind.Category.WEAPON); }
    private static Set<ItemKind> tools()    { return ofCategory(ItemKind.Category.TOOL); }

    private static Set<ItemKind> ofCategory(ItemKind.Category category) {
        EnumSet<ItemKind> set = EnumSet.noneOf(ItemKind.class);
        for (ItemKind kind : ItemKind.values()) {
            if (kind.category() == category) set.add(kind);
        }
        return set;
    }
}
