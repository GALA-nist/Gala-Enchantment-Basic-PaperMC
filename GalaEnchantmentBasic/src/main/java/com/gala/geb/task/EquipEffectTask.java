package com.gala.geb.task;

import com.gala.geb.GEBPlugin;
import com.gala.geb.enchant.GEBEnchant;
import com.gala.geb.enchant.ItemKind;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;

/**
 * Re-applies EFFECT enchantments from worn armor and the held tool
 * every few ticks (so effects vanish shortly after unequipping).
 */
public class EquipEffectTask extends BukkitRunnable {

    private final GEBPlugin plugin;

    public EquipEffectTask(GEBPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        int refresh = plugin.getConfig().getInt("effect-refresh-ticks", 40);
        int duration = refresh + 40; // small overlap so effects do not flicker

        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack piece : player.getInventory().getArmorContents()) {
                applyEffects(player, piece, ItemKind.Category.ARMOR, duration);
            }
            applyEffects(player, player.getInventory().getItemInMainHand(),
                    ItemKind.Category.TOOL, duration);
        }
    }

    private void applyEffects(Player player, ItemStack item, ItemKind.Category category, int duration) {
        if (item == null) return;
        for (Map.Entry<GEBEnchant, Integer> entry : plugin.enchants().getOnItem(item).entrySet()) {
            GEBEnchant enchant = entry.getKey();
            if (enchant.type() != GEBEnchant.Type.EFFECT) continue;
            if (enchant.kind().category() != category) continue;
            PotionEffectType type = plugin.enchants().effectByKey(enchant.effectKey());
            if (type == null || type.isInstant()) continue;
            player.addPotionEffect(new PotionEffect(type, duration,
                    entry.getValue() - 1, true, false, true));
        }
    }
}
