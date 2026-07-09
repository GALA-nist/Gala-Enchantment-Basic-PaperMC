package com.gala.geb.enchant;

import com.gala.geb.util.Roman;

/**
 * One enchantment created by an admin and stored in systemenchantment.yml.
 * The level picked in the GUI is the MAX level (like Efficiency V);
 * items can carry the enchant at any level from I up to that max.
 */
public class GEBEnchant {

    public enum Type { EFFECT, CUSTOM }

    private final String id;          // e.g. "regeneration_chestplate"
    private final Type type;
    private final String effectKey;   // vanilla effect key ("regeneration") or CustomEffect name ("BLOODLUST")
    private final ItemKind kind;      // which item it applies to
    private final int maxLevel;       // maximum obtainable level

    // ---- Per-enchant tunables (editable in systemenchantment.yml) ----
    private final int effectDuration;    // seconds (weapon hits: per level; worn one-shot: flat)
    private final boolean refreshNeeded; // worn items: keep re-applying the effect (default true)
    private final boolean cooldown;      // weapons: needs cooldown between triggers
    private final int cooldownTime;      // seconds of that cooldown

    public GEBEnchant(String id, Type type, String effectKey, ItemKind kind, int maxLevel) {
        this(id, type, effectKey, kind, maxLevel, 5, true, false, 10);
    }

    public GEBEnchant(String id, Type type, String effectKey, ItemKind kind, int maxLevel,
                      int effectDuration, boolean refreshNeeded, boolean cooldown, int cooldownTime) {
        this.id = id;
        this.type = type;
        this.effectKey = effectKey;
        this.kind = kind;
        this.maxLevel = Math.max(1, maxLevel);
        this.effectDuration = Math.max(1, effectDuration);
        this.refreshNeeded = refreshNeeded;
        this.cooldown = cooldown;
        this.cooldownTime = Math.max(1, cooldownTime);
    }

    public String id()             { return id; }
    public Type type()             { return type; }
    public String effectKey()      { return effectKey; }
    public ItemKind kind()         { return kind; }
    public int maxLevel()          { return maxLevel; }
    public int effectDuration()    { return effectDuration; }
    public boolean refreshNeeded() { return refreshNeeded; }
    public boolean cooldown()      { return cooldown; }
    public int cooldownTime()      { return cooldownTime; }

    /** e.g. "Regeneration" or "Bloodlust" (no level) */
    public String baseDisplay() {
        return type == Type.CUSTOM
                ? CustomEffect.valueOf(effectKey).display()
                : Roman.prettify(effectKey);
    }

    /** e.g. "Regeneration III" */
    public String displayName(int level) {
        return baseDisplay() + " " + Roman.toRoman(Math.max(1, level));
    }

    /** Display at max level, e.g. "Regeneration V" - used in admin lists. */
    public String displayName() {
        return displayName(maxLevel);
    }
}
