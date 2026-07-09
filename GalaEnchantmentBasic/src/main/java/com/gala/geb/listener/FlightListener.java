package com.gala.geb.listener;

import com.gala.geb.GEBPlugin;
import com.gala.geb.enchant.CustomEffect;
import com.gala.geb.enchant.EnchantManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the Dreamcatcher double-jump: if the boots are below the
 * durability threshold, the lift-off is blocked and the player gets
 * a chat warning telling them to repair.
 */
public class FlightListener implements Listener {

    private final GEBPlugin plugin;
    /** Small message cooldown so spamming space does not flood the chat. */
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public FlightListener(GEBPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying()) return; // only care about lift-off attempts
        Player player = event.getPlayer();
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) return;

        ItemStack boots = player.getInventory().getBoots();
        if (!plugin.enchants().hasCustomEffect(boots, CustomEffect.DREAMCATCHER)) return;

        int remaining = EnchantManager.remainingDurability(boots);
        int min = plugin.getConfig().getInt("dreamcatcher.min-durability", 30);
        if (remaining >= min) return; // healthy boots, let the flight happen

        // Boots too worn: block the lift-off and warn.
        event.setCancelled(true);
        player.setFlying(false);

        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last == null || now - last > 2000) {
            lastWarning.put(player.getUniqueId(), now);
            player.sendMessage(Component.text("[GEB] ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text("Your Dreamcatcher boots are too damaged to fly! ",
                            NamedTextColor.RED))
                    .append(Component.text("(" + remaining + " durability left, needs " + min + ") ",
                            NamedTextColor.GRAY))
                    .append(Component.text("Repair them at an anvil.", NamedTextColor.YELLOW)));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.4f);
        }
    }
}
