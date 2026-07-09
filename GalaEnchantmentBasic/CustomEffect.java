package com.gala.geb.task;

import com.gala.geb.GEBPlugin;
import com.gala.geb.enchant.CustomEffect;
import com.gala.geb.enchant.EnchantManager;
import com.gala.geb.enchant.GEBEnchant;
import com.gala.geb.enchant.ItemKind;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Re-applies EFFECT enchantments from worn armor and the held tool
 * every few ticks, and manages the whole Dreamcatcher lifecycle:
 * grant/revoke, durability drain while flying, low-durability cutoff,
 * the red warning boss bar, and join/quit safety.
 */
public class EquipEffectTask extends BukkitRunnable {

    private final GEBPlugin plugin;
    /** Players whose flight WE enabled (so we never touch creative or other plugins). */
    private final Set<UUID> flightGranted = new HashSet<>();
    /** Accumulated flight ticks per player, for durability drain. */
    private final Map<UUID, Integer> flightTicks = new HashMap<>();
    /** Active warning boss bars per player. */
    private final Map<UUID, BossBar> warningBars = new HashMap<>();

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

            handleDreamcatcher(player, refresh);
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
    //  Dreamcatcher
    // ------------------------------------------------------------------

    private void handleDreamcatcher(Player player, int refreshTicks) {
        GameMode mode = player.getGameMode();
        boolean managedMode = mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
        ItemStack boots = player.getInventory().getBoots();
        boolean wearing = managedMode
                && plugin.enchants().hasCustomEffect(boots, CustomEffect.DREAMCATCHER);
        UUID uuid = player.getUniqueId();

        if (!wearing) {
            flightTicks.remove(uuid);
            hideBar(player);
            if (flightGranted.remove(uuid) && managedMode) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
            return;
        }

        // Keep allowFlight on even with worn boots, so a double-jump attempt
        // triggers the FlightListener warning instead of silently doing nothing.
        if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
            flightGranted.add(uuid);
        }
        if (!flightGranted.contains(uuid)) {
            hideBar(player);
            return; // flight belongs to another plugin - never drain for it
        }

        int minDurability = plugin.getConfig().getInt("dreamcatcher.min-durability", 30);
        int remaining = EnchantManager.remainingDurability(boots);
        boolean flying = player.isFlying();

        // Mid-air cutoff: boots got too worn while flying -> force landing.
        if (flying && remaining < minDurability) {
            player.setFlying(false);
            flightTicks.remove(uuid);
            hideBar(player);
            player.sendMessage(Component.text("[GEB] ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text("Your Dreamcatcher boots are too worn and gave out mid-air! ",
                            NamedTextColor.RED))
                    .append(Component.text("Repair them to fly again. Brace for the fall!",
                            NamedTextColor.YELLOW)));
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 0.6f);
            return;
        }

        // Red warning boss bar: only WHILE FLYING and durability at/below the
        // warn threshold. Hidden the moment the player lands or repairs.
        int warnAt = plugin.getConfig().getInt("dreamcatcher.warn-durability", 35);
        if (flying && remaining <= warnAt) {
            showBar(player, remaining, warnAt);
        } else {
            hideBar(player);
        }

        // Durability drain: ONLY while actually flying with our flight.
        if (flying) {
            int accumulated = flightTicks.merge(uuid, refreshTicks, Integer::sum);
            int drainTicks = Math.max(200,
                    plugin.getConfig().getInt("dreamcatcher.durability-drain-minutes", 5) * 60 * 20);
            if (accumulated >= drainTicks) {
                flightTicks.put(uuid, 0);
                damageBoots(player, boots);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Warning boss bar
    // ------------------------------------------------------------------

    private void showBar(Player player, int remaining, int warnAt) {
        float progress = Math.max(0f, Math.min(1f, remaining / (float) warnAt));
        Component title = Component.text("WARNING: Dreamcatcher boots at " + remaining
                + " durability - repair them soon!", NamedTextColor.RED);
        BossBar bar = warningBars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(title, progress, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
            warningBars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(title);
            bar.progress(progress);
        }
    }

    private void hideBar(Player player) {
        BossBar bar = warningBars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    // ------------------------------------------------------------------
    //  Join / quit safety (called from FlightListener)
    // ------------------------------------------------------------------

    /**
     * A player who logged out mid-flight rejoins with vanilla flight reset,
     * i.e. already falling. Restore flight instantly so they do not splat.
     */
    public void restoreOnJoin(Player player) {
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) return;
        ItemStack boots = player.getInventory().getBoots();
        if (!plugin.enchants().hasCustomEffect(boots, CustomEffect.DREAMCATCHER)) return;
        if (EnchantManager.remainingDurability(boots)
                < plugin.getConfig().getInt("dreamcatcher.min-durability", 30)) return;

        if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
            flightGranted.add(player.getUniqueId());
        }
        // Airborne on rejoin -> they were probably flying when they left.
        if (!player.isOnGround()) {
            player.setFlying(true);
            player.sendMessage(Component.text("[GEB] ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text("Your Dreamcatcher boots caught you - flight restored.",
                            NamedTextColor.GREEN)));
        }
    }

    /** Frees all per-player state so nothing leaks across sessions. */
    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        flightTicks.remove(uuid);
        flightGranted.remove(uuid);
        BossBar bar = warningBars.remove(uuid);
        if (bar != null) player.hideBossBar(bar);
    }

    // ------------------------------------------------------------------

    /** Applies 1 durability damage to the boots, respecting Unbreaking. */
    private void damageBoots(Player player, ItemStack boots) {
        int unbreaking = boots.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0
                && ThreadLocalRandom.current().nextDouble() >= 1.0 / (unbreaking + 1)) {
            return; // Unbreaking absorbed this hit
        }
        ItemMeta meta = boots.getItemMeta();
        if (meta == null || meta.isUnbreakable() || !(meta instanceof Damageable damageable)) return;

        int newDamage = damageable.getDamage() + 1;
        if (newDamage >= boots.getType().getMaxDurability()) {
            // Boots break entirely
            player.getInventory().setBoots(null);
            player.setFlying(false);
            hideBar(player);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            player.sendMessage(Component.text("[GEB] ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text("Your Dreamcatcher boots broke!", NamedTextColor.RED)));
        } else {
            damageable.setDamage(newDamage);
            boots.setItemMeta(meta);
            player.getInventory().setBoots(boots);
        }
    }
}
