package com.gala.geb.listener;

import com.gala.geb.GEBPlugin;
import com.gala.geb.enchant.GEBEnchant;
import com.gala.geb.enchant.ItemKind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantTableListener implements Listener {

    private final GEBPlugin plugin;

    public EnchantTableListener(GEBPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Enchanting table: chance to roll a GEB enchant on top of vanilla
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (!plugin.getConfig().getBoolean("enchant-table.enabled", true)) return;
        double chance = plugin.getConfig().getDouble("enchant-table.chance", 0.20);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        ItemStack item = event.getItem();
        ItemKind kind = ItemKind.ofMaterial(item.getType());
        if (kind == null) return;

        List<GEBEnchant> candidates = plugin.enchants().forKind(kind);
        if (candidates.isEmpty()) return;

        GEBEnchant chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        // Apply one tick later so vanilla enchanting has finished writing the item.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.enchants().apply(item, chosen)) {
                Player player = event.getEnchanter();
                player.sendMessage(Component.text("[GEB] ", NamedTextColor.DARK_PURPLE)
                        .append(Component.text("Bonus enchantment: ", NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text(chosen.displayName(), NamedTextColor.AQUA)));
            }
        });
    }

    // ------------------------------------------------------------------
    //  Anvil: item + GEB enchanted book  ->  enchanted item
    // ------------------------------------------------------------------

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();
        if (left == null || right == null) return;
        if (right.getType() != Material.ENCHANTED_BOOK) return;

        List<GEBEnchant> bookEnchants = plugin.enchants().getOnItem(right);
        if (bookEnchants.isEmpty()) return;

        ItemStack result = left.clone();
        boolean changed = false;
        for (GEBEnchant enchant : bookEnchants) {
            if (enchant.kind().matches(result.getType())) {
                if (plugin.enchants().apply(result, enchant)) changed = true;
            }
        }
        if (changed) {
            event.setResult(result);
        }
    }
}
