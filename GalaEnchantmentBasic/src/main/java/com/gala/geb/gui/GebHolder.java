package com.gala.geb.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Marks an inventory as belonging to GEB and tells us which page it is. */
public class GebHolder implements InventoryHolder {

    public enum Page {
        ITEM_SELECT,     // page 1: tools / weapons / armor rows
        OPTION_SELECT,   // Effect or Custom
        EFFECT_LIST,     // vanilla effect list (paginated)
        CUSTOM_LIST,     // custom effect list
        LEVEL_SELECT,    // I .. max
        CONFIRM,         // result tab: accept / back / decline
        REMOVE_LIST      // /geb remove
    }

    private final Page page;
    private Inventory inventory;

    public GebHolder(Page page) {
        this.page = page;
    }

    public Page page() { return page; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
