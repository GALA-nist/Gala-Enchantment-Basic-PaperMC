package com.gala.geb.task;

import com.gala.geb.GEBPlugin;
import com.gala.geb.enchant.CustomEffect;
import com.gala.geb.enchant.GEBEnchant;
import com.gala.geb.enchant.ItemKind;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Re-applies EFFECT enchantments from worn armor and the held tool
 * every few ticks (so effects vanish shortly after unequipping),
 * and manages Dreamcatcher flight for boots.
 */
public class EquipEffectTask extends BukkitRunnable {

    private final GEBPlugin plugin;
    /** Players whose flight WE enabled (so we never touch creative or other plugins). */
    private final Set<UUID> flightGranted = new HashSet<>();

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

            handleDreamcatcher(player);
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

    // ------------------------------------------------------------------
    //  Dreamcatcher: boots that let you fly (double-jump to lift off,
    //  the same input as creative flight - works on mobile clients too).
    // ------------------------------------------------------------------

    private void handleDreamcatcher(Player player) {
        GameMode mode = player.getGameMode();
        boolean managedMode = mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
        boolean wearing = managedMode && hasDreamcatcher(player.getInventory().getBoots());
        UUID uuid = player.getUniqueId();

        if (wearing) {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
                flightGranted.add(uuid);
            }
            // If allowFlight was already true from elsewhere, leave it alone.
        } else if (flightGranted.remove(uuid) && managedMode) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    private boolean hasDreamcatcher(ItemStack boots) {
        if (boots == null) return false;
        for (GEBEnchant enchant : plugin.enchants().getOnItem(boots).keySet()) {
            if (enchant.type() == GEBEnchant.Type.CUSTOM
                    && CustomEffect.DREAMCATCHER.name().equals(enchant.effectKey())) {
                return true;
            }
        }
        return false;
    }
}
