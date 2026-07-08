package com.gala.geb.enchant;

import com.gala.geb.util.Roman;

/**
 * One enchantment created by an admin and stored in systemenchantment.yml.
 */
public class GEBEnchant {

    public enum Type { EFFECT, CUSTOM }

    private final String id;          // e.g. "regeneration_chestplate"
    private final Type type;
    private final String effectKey;   // vanilla effect key ("regeneration") or CustomEffect name ("BLOODLUST")
    private final ItemKind kind;      // which item it applies to
    private final int level;          // chosen level (1 = I, 11 = XI)

    public GEBEnchant(String id, Type type, String effectKey, ItemKind kind, int level) {
        this.id = id;
        this.type = type;
        this.effectKey = effectKey;
        this.kind = kind;
        this.level = level;
    }

    public String id()        { return id; }
    public Type type()        { return type; }
    public String effectKey() { return effectKey; }
    public ItemKind kind()    { return kind; }
    public int level()        { return level; }

    /** e.g. "Regeneration XI" or "Bloodlust I" */
    public String displayName() {
        String base = type == Type.CUSTOM
                ? CustomEffect.valueOf(effectKey).display()
                : Roman.prettify(effectKey);
        return base + " " + Roman.toRoman(level);
    }
}
