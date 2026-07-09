package com.gala.geb.listener;

import com.gala.geb.GEBPlugin;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Grindstone disenchanting: removes GEB enchantments just like vanilla
 * removes normal ones. Works even when the item ONLY has GEB enchants
 * (vanilla would otherwise show no result at all).
 */
public class GrindstoneListener implements Listener {

    private final GEBPlugin plugin;

    public GrindstoneListener(GEBPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onGrindstone(PrepareGrindstoneEvent event) {
        GrindstoneInventory inv = event.getInventory();
        ItemStack upper = inv.getUpperItem();
        ItemStack lower = inv.getLowerItem();

        boolean upperHas = hasGeb(upper);
        boolean lowerHas = hasGeb(lower);
        if (!upperHas && !lowerHas) return;

        ItemStack result = event.getResult();
        if (result != null && result.getType() != Material.AIR) {
            // Vanilla already made a result (stripped vanilla enchants) -
            // strip our enchants from it too.
            ItemStack stripped = result.clone();
            plugin.enchants().stripAll(stripped);
            event.setResult(stripped);
            return;
        }

        // Vanilla produced nothing: the item only has GEB enchants.
        // Only handle the single-item case; combining two items stays vanilla.
        boolean upperEmpty = upper == null || upper.getType() == Material.AIR;
        boolean lowerEmpty = lower == null || lower.getType() == Material.AIR;
        if (!upperEmpty && !lowerEmpty) return;

        ItemStack source = upperEmpty ? lower : upper;
        ItemStack stripped = source.clone();
        stripped.setAmount(1);
        if (plugin.enchants().stripAll(stripped)) {
            event.setResult(stripped);
        }
    }

    private boolean hasGeb(ItemStack item) {
        return item != null && item.getType() != Material.AIR
                && !plugin.enchants().getOnItem(item).isEmpty();
    }
}
