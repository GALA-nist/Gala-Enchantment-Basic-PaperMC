package com.gala.geb.enchant;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/**
 * Hard-coded "Custom" enchantment behaviors (the non-vanilla-effect option).
 * New ones can be added here and handled in CombatListener / BlockListener.
 */
public enum CustomEffect {

    /**
     * Weapon only. When you hit ANY entity (player, hostile or passive mob),
     * the ATTACKER receives Instant Health I. Max level I.
     */
    BLOODLUST("Bloodlust", Material.REDSTONE, 1,
            EnumSet.of(ItemKind.Category.WEAPON),
            "On hit: heals you with Instant Health I."),

    /**
     * Weapon only. Sets the target on fire for 3s per level. Max level III.
     */
    IGNITE("Ignite", Material.BLAZE_POWDER, 3,
            EnumSet.of(ItemKind.Category.WEAPON),
            "On hit: sets the target on fire (3s per level)."),

    /**
     * Tool only. Breaking a block restores a little hunger. Max level II.
     */
    HARVESTERS_MEAL("Harvester's Meal", Material.BREAD, 2,
            EnumSet.of(ItemKind.Category.TOOL),
            "On block break: small chance to restore hunger.");

    private final String display;
    private final Material icon;
    private final int maxLevel;
    private final Set<ItemKind.Category> allowed;
    private final String description;

    CustomEffect(String display, Material icon, int maxLevel,
                 Set<ItemKind.Category> allowed, String description) {
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

    public boolean allows(ItemKind.Category category) {
        return allowed.contains(category);
    }
}
