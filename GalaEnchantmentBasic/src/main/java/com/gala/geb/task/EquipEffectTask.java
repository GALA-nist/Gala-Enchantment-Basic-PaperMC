package com.gala.geb.task;

import com.gala.geb.GEBPlugin;
import com.gala.geb.enchant.CustomEffect;
import com.gala.geb.enchant.EnchantManager;
import com.gala.geb.enchant.GEBEnchant;
import com.gala.geb.enchant.ItemKind;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

    /** Task period in ticks: fast, so Dreamcatcher cutoffs react in ~0.5s. */
    public static final int PERIOD = 10;

    private final GEBPlugin plugin;
    /** Ticks accumulated since the last (slower) effect refresh pass. */
    private int sinceEffects = 0;
    /** Worn one-shot (refresh-needed: false) enchants already given, per player. */
    private final Map<UUID, Set<String>> oneShotGiven = new HashMap<>();
    /** Players whose flight WE enabled (so we never touch creative or other plugins). */
    private final Set<UUID> flightGranted = new HashSet<>();
    /** Accumulated flight ticks per player, for durability drain. */
    private final Map<UUID, Integer> flightTicks = new HashMap<>();
    /** Active warning boss bars per player. */
    private final Map<UUID, BossBar> warningBars = new HashMap<>();
    /** Sprint-flying gamble: hidden countdown ticks per player. */
    private final Map<UUID, Integer> sprintCountdown = new HashMap<>();

    /** Why the boots lost durability - decides message + sound. */
    public enum DrainCause { FLIGHT, SPRINT, SPAM }

    public EquipEffectTask(GEBPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        int refresh = Math.max(PERIOD, plugin.getConfig().getInt("effect-refresh-ticks", 40));
        sinceEffects += PERIOD;
        boolean doEffects = sinceEffects >= refresh;
        if (doEffects) sinceEffects = 0;
        int duration = refresh + 40; // small overlap so effects do not flicker

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (doEffects) {
                Set<String> wornOneShots = new HashSet<>();
                for (ItemStack piece : player.getInventory().getArmorContents()) {
                    applyEffects(player, piece, ItemKind.Category.ARMOR, duration, wornOneShots);
                }
                applyEffects(player, player.getInventory().getItemInMainHand(),
                        ItemKind.Category.TOOL, duration, wornOneShots);
                // Forget one-shots for enchants no longer worn, so re-equipping re-triggers.
                Set<String> given = oneShotGiven.get(player.getUniqueId());
                if (given != null) given.retainAll(wornOneShots);
            }
            // Dreamcatcher runs EVERY cycle (0.5s) so combat damage to the
            // boots cannot be outrun before the low-durability cutoff lands.
            handleDreamcatcher(player, PERIOD);
        }
    }

    private void applyEffects(Player player, ItemStack item, ItemKind.Category category,
                              int duration, Set<String> wornOneShots) {
        if (item == null) return;
        for (Map.Entry<GEBEnchant, Integer> entry : plugin.enchants().getOnItem(item).entrySet()) {
            GEBEnchant enchant = entry.getKey();
            if (enchant.type() != GEBEnchant.Type.EFFECT) continue;
            if (enchant.kind().category() != category) continue;
            PotionEffectType type = plugin.enchants().effectByKey(enchant.effectKey());
            if (type == null || type.isInstant()) continue;

            if (enchant.refreshNeeded()) {
                // Constant aura: quietly re-applied while worn/held.
                player.addPotionEffect(new PotionEffect(type, duration,
                        entry.getValue() - 1, true, false, true));
            } else {
                // One-shot: applied ONCE on equip for effect-duration seconds,
                // not refreshed until the item is taken off and put back on.
                wornOneShots.add(enchant.id());
                Set<String> given = oneShotGiven.computeIfAbsent(
                        player.getUniqueId(), k -> new HashSet<>());
                if (given.add(enchant.id())) {
                    player.addPotionEffect(new PotionEffect(type,
                            20 * enchant.effectDuration(),
                            entry.getValue() - 1, false, true, true));
                }
            }
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

        // Passive durability drain: ONLY while actually flying with our flight.
        if (flying) {
            int accumulated = flightTicks.merge(uuid, refreshTicks, Integer::sum);
            int drainTicks = Math.max(200,
                    plugin.getConfig().getInt("dreamcatcher.durability-drain-minutes", 3) * 60 * 20);
            if (accumulated >= drainTicks) {
                flightTicks.put(uuid, 0);
                damageBoots(player, boots, true, DrainCause.FLIGHT);
            }
        }

        // Sprint gamble: sprint-flying rolls a hidden countdown (min..max s,
        // stretched by Unbreaking). Outlast it sprinting -> -1 durability and
        // a fresh roll. Stop sprinting early -> countdown voids, no loss.
        if (flying && player.isSprinting()) {
            Integer left = sprintCountdown.get(uuid);
            if (left == null) left = rollSprintTicks(boots);
            left -= refreshTicks;
            if (left <= 0) {
                damageBoots(player, boots, false, DrainCause.SPRINT);
                sprintCountdown.put(uuid, rollSprintTicks(boots));
            } else {
                sprintCountdown.put(uuid, left);
            }
        } else {
            sprintCountdown.remove(uuid); // reverted - no loss until they sprint again
        }
    }

    /** Rolls a fresh sprint countdown in ticks, stretched by Unbreaking. */
    private int rollSprintTicks(ItemStack boots) {
        int minS = Math.max(1, plugin.getConfig().getInt("dreamcatcher.sprint-chance-min-seconds", 3));
        int maxS = Math.max(minS, plugin.getConfig().getInt("dreamcatcher.sprint-chance-max-seconds", 15));
        double perLevel = plugin.getConfig().getDouble("dreamcatcher.sprint-unbreaking-multiplier", 1.0);
        int unbreaking = boots.getEnchantmentLevel(Enchantment.UNBREAKING);
        double scale = 1.0 + unbreaking * perLevel;
        int seconds = ThreadLocalRandom.current().nextInt(minS, maxS + 1);
        return (int) Math.round(seconds * scale * 20);
    }

    /** Called by FlightListener when stutter-sprinting (sprint>stop spam) is detected. */
    public void spamDrain(Player player, ItemStack boots) {
        damageBoots(player, boots, false, DrainCause.SPAM);
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
        oneShotGiven.remove(uuid);
        sprintCountdown.remove(uuid);
        BossBar bar = warningBars.remove(uuid);
        if (bar != null) player.hideBossBar(bar);
    }

    // ------------------------------------------------------------------

    /**
     * Applies 1 durability damage to the boots.
     * respectUnbreaking: passive flight drain rolls the vanilla-style
     * Unbreaking absorb chance; the sprint gamble does NOT (its countdown
     * window is already stretched by Unbreaking instead).
     */
    private void damageBoots(Player player, ItemStack boots,
                             boolean respectUnbreaking, DrainCause cause) {
        if (respectUnbreaking) {
            int unbreaking = boots.getEnchantmentLevel(Enchantment.UNBREAKING);
            if (unbreaking > 0
                    && ThreadLocalRandom.current().nextDouble() >= 1.0 / (unbreaking + 1)) {
                return; // Unbreaking absorbed this hit
            }
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
            return;
        }
        damageable.setDamage(newDamage);
        boots.setItemMeta(meta);
        player.getInventory().setBoots(boots);
        announceDrain(player, cause);
    }

    /** Colored chat message + sound matching why durability was lost. */
    private void announceDrain(Player player, DrainCause cause) {
        String raw;
        switch (cause) {
            case SPRINT -> {
                raw = plugin.getConfig().getString("dreamcatcher.sprint-drain-message",
                        "&d✦ &7Dreamcatcher can't handle the winds! &c-1 durability");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.9f);
            }
            case SPAM -> {
                raw = plugin.getConfig().getString("dreamcatcher.sprint-spam-message",
                        "&d✦ &7Dreamcatcher is dizzy from your stutter-flying! &c-1 durability");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.6f);
            }
            default -> {
                java.util.List<String> pool = plugin.getConfig()
                        .getStringList("dreamcatcher.drain-messages");
                if (pool.isEmpty()) {
                    pool = java.util.List.of(
                            "&d✦ &7Oh, looks like the boots are getting tired... &c-1 durability",
                            "&d✦ &7Dreamcatcher awakens from its sleep! &c-1 durability");
                }
                raw = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                player.playSound(player.getLocation(),
                        Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f);
            }
        }
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
    }
}
