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
import org.bukkit.inventory.view.AnvilView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantTableListener implements Listener {

    private final GEBPlugin plugin;

    public EnchantTableListener(GEBPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Enchanting table: chance to roll a GEB enchant on top of vanilla.
    //  The XP cost decides the tier: ~8 levels per tier (configurable),
    //  so a 24-level enchant can reach tier III, cheaper ones cap lower.
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

        // Tier cap from the XP level cost of the picked slot
        int perTier = Math.max(1, plugin.getConfig().getInt("enchant-table.levels-per-tier", 8));
        int cost = event.getExpLevelCost();
        int cap = Math.max(1, Math.min(chosen.maxLevel(), cost / perTier));
        // Roll the capped tier or one below it, so results vary a little
        final int rolled = cap <= 1 ? 1
                : ThreadLocalRandom.current().nextInt(cap - 1, cap + 1); // cap-1 or cap

        // Apply one tick later so vanilla enchanting has finished writing the item.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.enchants().setEnchant(item, chosen, rolled)) {
                Player player = event.getEnchanter();
                player.sendMessage(Component.text("[GEB] ", NamedTextColor.DARK_PURPLE)
                        .append(Component.text("Bonus enchantment: ", NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text(chosen.displayName(rolled), NamedTextColor.AQUA)));
            }
        });
    }

    // ------------------------------------------------------------------
    //  Anvil combining:
    //   - item + GEB book  -> enchant transferred
    //   - same level + same level -> level + 1 (capped at max, like vanilla)
    //   - different levels -> keeps the higher one
    //  Works for item+item and book+book too.
    // ------------------------------------------------------------------

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();
        if (left == null || right == null) return;

        Map<GEBEnchant, Integer> rightEnchants = plugin.enchants().getOnItem(right);
        if (rightEnchants.isEmpty()) return;

        // Sacrifice must be a GEB book, or the same kind of item (sword+sword etc.)
        boolean rightIsBook = right.getType() == Material.ENCHANTED_BOOK;
        if (!rightIsBook && right.getType() != left.getType()) return;

        // Start from the vanilla result when there is one (keeps vanilla merges),
        // otherwise from a copy of the left item.
        ItemStack result = event.getResult();
        ItemStack base = (result != null && result.getType() != Material.AIR)
                ? result.clone() : left.clone();
        if (base.getAmount() > 1) base.setAmount(1);

        Map<GEBEnchant, Integer> baseEnchants = plugin.enchants().getOnItem(base);
        boolean baseIsBook = base.getType() == Material.ENCHANTED_BOOK;

        boolean changed = false;
        int workCost = 0;
        for (Map.Entry<GEBEnchant, Integer> entry : rightEnchants.entrySet()) {
            GEBEnchant enchant = entry.getKey();
            int rightLevel = entry.getValue();

            // Book targets must match the item kind; book-on-book always allowed
            if (!baseIsBook && !enchant.kind().matches(base.getType())) continue;

            Integer current = baseEnchants.get(enchant);
            int target;
            if (current == null) {
                target = rightLevel;
            } else if (current.intValue() == rightLevel && current < enchant.maxLevel()) {
                target = current + 1;                    // III + III = IV
            } else {
                target = Math.max(current, rightLevel);  // keep the higher one
            }

            if (current == null || target > current) {
                if (plugin.enchants().setEnchant(base, enchant, target)) {
                    changed = true;
                    workCost += target * 2;
                }
            }
        }

        if (changed) {
            event.setResult(base);
            AnvilView view = event.getView();
            // Never free: charge at least the vanilla cost or our computed one
            view.setRepairCost(Math.max(view.getRepairCost(), Math.max(2, workCost)));
        }
    }
}
