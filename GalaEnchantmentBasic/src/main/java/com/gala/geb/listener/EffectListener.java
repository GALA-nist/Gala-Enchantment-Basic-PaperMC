package com.gala.geb.listener;

import com.gala.geb.GEBPlugin;
import com.gala.geb.enchant.CustomEffect;
import com.gala.geb.enchant.GEBEnchant;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EffectListener implements Listener {

    private final GEBPlugin plugin;
    /** enchant-id -> ready-at millis, per player, for weapon trigger cooldowns. */
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public EffectListener(GEBPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Weapon enchants: trigger on hit (melee or projectile shooter)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        for (Map.Entry<GEBEnchant, Integer> entry : plugin.enchants().getOnItem(weapon).entrySet()) {
            GEBEnchant enchant = entry.getKey();
            int level = entry.getValue();

            // Optional per-enchant cooldown between triggers
            if (enchant.cooldown() && isOnCooldown(attacker, enchant)) continue;

            if (enchant.type() == GEBEnchant.Type.EFFECT) {
                PotionEffectType type = plugin.enchants().effectByKey(enchant.effectKey());
                if (type == null) continue;
                // Buffs (Regeneration, Speed, ...) go to the ATTACKER,
                // debuffs (Poison, Slowness, Weakness, ...) go to the TARGET.
                boolean buff = type.getEffectCategory() == PotionEffectType.Category.BENEFICIAL;
                LivingEntity receiver = buff ? attacker : victim;
                // effect-duration seconds per level, amplifier = level - 1
                receiver.addPotionEffect(new PotionEffect(type,
                        20 * enchant.effectDuration() * level,
                        level - 1, false, true, true));
            } else {
                applyCustomOnHit(attacker, victim, enchant, level);
            }
        }
    }

    /** True while still cooling down; otherwise starts the next cooldown window. */
    private boolean isOnCooldown(Player player, GEBEnchant enchant) {
        long now = System.currentTimeMillis();
        Map<String, Long> byEnchant = cooldowns.computeIfAbsent(
                player.getUniqueId(), k -> new HashMap<>());
        Long readyAt = byEnchant.get(enchant.id());
        if (readyAt != null && now < readyAt) return true;
        byEnchant.put(enchant.id(), now + enchant.cooldownTime() * 1000L);
        return false;
    }

    private void applyCustomOnHit(Player attacker, LivingEntity victim, GEBEnchant enchant, int level) {
        CustomEffect effect;
        try {
            effect = CustomEffect.valueOf(enchant.effectKey());
        } catch (IllegalArgumentException ex) {
            return;
        }
        switch (effect) {
            case BLOODLUST ->
                // Bloodlust: attacker gets Instant Health I on hit (capped at level I).
                    attacker.addPotionEffect(new PotionEffect(
                            PotionEffectType.INSTANT_HEALTH, 1, 0, false, true, true));
            case IGNITE ->
                    victim.setFireTicks(Math.max(victim.getFireTicks(), 20 * 3 * level));
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    //  Tool custom enchants: trigger on block break
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        for (Map.Entry<GEBEnchant, Integer> entry : plugin.enchants().getOnItem(tool).entrySet()) {
            GEBEnchant enchant = entry.getKey();
            if (enchant.type() != GEBEnchant.Type.CUSTOM) continue;
            CustomEffect effect;
            try {
                effect = CustomEffect.valueOf(enchant.effectKey());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (effect == CustomEffect.HARVESTERS_MEAL) {
                // 10% chance per level to restore 1 hunger point
                if (ThreadLocalRandom.current().nextDouble() < 0.10 * entry.getValue()) {
                    player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
                }
            }
        }
    }
}
