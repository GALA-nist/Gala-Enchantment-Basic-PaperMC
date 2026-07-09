package com.gala.geb.gui;

import com.gala.geb.enchant.CustomEffect;
import com.gala.geb.enchant.GEBEnchant;
import com.gala.geb.enchant.ItemKind;

/** What one admin has picked so far while walking through the create GUI. */
public class Session {

    public ItemKind kind;               // step 1
    public GEBEnchant.Type type;        // step 2 (EFFECT / CUSTOM)
    public String effectKey;            // step 3 (vanilla effect key)
    public CustomEffect customEffect;   // step 3 (custom option)
    public int level;                   // step 4
    public int effectPage = 0;          // pagination for effect list
    public int removePage = 0;          // pagination for remove list

    public void resetToItemSelect() {
        kind = null;
        type = null;
        effectKey = null;
        customEffect = null;
        level = 0;
        effectPage = 0;
    }
}
