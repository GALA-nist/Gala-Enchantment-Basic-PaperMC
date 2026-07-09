package com.gala.geb.enchant;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Every clickable "item type" in the create GUI.
 * Row 1 = tools, Row 2 = weapons, Row 3 = armor.
 */
public enum ItemKind {

    // ---- Row 1 : Tools ----
    PICKAXE(Category.TOOL, Material.NETHERITE_PICKAXE, "Pickaxe"),
    AXE(Category.TOOL, Material.NETHERITE_AXE, "Axe"),
    SHOVEL(Category.TOOL, Material.NETHERITE_SHOVEL, "Shovel"),
    HOE(Category.TOOL, Material.NETHERITE_HOE, "Hoe"),
    SHEARS(Category.TOOL, Material.SHEARS, "Shears"),
    FISHING_ROD(Category.TOOL, Material.FISHING_ROD, "Fishing Rod"),
    FLINT_AND_STEEL(Category.TOOL, Material.FLINT_AND_STEEL, "Flint & Steel"),
    BRUSH(Category.TOOL, Material.BRUSH, "Brush"),

    // ---- Row 2 : Weapons ----
    SWORD(Category.WEAPON, Material.NETHERITE_SWORD, "Sword"),
    MACE(Category.WEAPON, Material.MACE, "Mace"),
    TRIDENT(Category.WEAPON, Material.TRIDENT, "Trident"),
    BOW(Category.WEAPON, Material.BOW, "Bow"),
    CROSSBOW(Category.WEAPON, Material.CROSSBOW, "Crossbow"),

    // ---- Row 3 : Armor ----
    HELMET(Category.ARMOR, Material.NETHERITE_HELMET, "Helmet"),
    CHESTPLATE(Category.ARMOR, Material.NETHERITE_CHESTPLATE, "Chestplate"),
    LEGGINGS(Category.ARMOR, Material.NETHERITE_LEGGINGS, "Leggings"),
    BOOTS(Category.ARMOR, Material.NETHERITE_BOOTS, "Boots");

    public enum Category { TOOL, WEAPON, ARMOR }

    private final Category category;
    private final Material icon;
    private final String display;

    ItemKind(Category category, Material icon, String display) {
        this.category = category;
        this.icon = icon;
        this.display = display;
    }

    public Category category() { return category; }
    public Material icon()     { return icon; }
    public String display()    { return display; }

    /** Does the given material belong to this kind? (any wood/stone/iron/... tier) */
    public boolean matches(Material material) {
        String name = material.name().toUpperCase(Locale.ROOT);
        return switch (this) {
            case SHEARS, FISHING_ROD, FLINT_AND_STEEL, BRUSH, MACE, TRIDENT, BOW, CROSSBOW ->
                    name.equals(this.name());
            default -> name.endsWith("_" + this.name());
        };
    }

    public static ItemKind ofMaterial(Material material) {
        for (ItemKind kind : values()) {
            if (kind.matches(material)) return kind;
        }
        return null;
    }
}
