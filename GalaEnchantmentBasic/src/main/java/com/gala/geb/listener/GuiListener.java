package com.gala.geb.listener;

import com.gala.geb.GEBPlugin;
import com.gala.geb.enchant.CustomEffect;
import com.gala.geb.enchant.GEBEnchant;
import com.gala.geb.enchant.ItemKind;
import com.gala.geb.gui.GebHolder;
import com.gala.geb.gui.GuiManager;
import com.gala.geb.gui.Session;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class GuiListener implements Listener {

    private final GEBPlugin plugin;
    private final GuiManager gui;

    public GuiListener(GEBPlugin plugin) {
        this.plugin = plugin;
        this.gui = plugin.gui();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GebHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GebHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof GebHolder)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR
                || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int slot = event.getSlot();
        Session s = gui.session(player);

        // Global exit button
        if (slot == GuiManager.SLOT_EXIT && clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            gui.clearSession(player);
            return;
        }

        switch (holder.page()) {
            case ITEM_SELECT -> handleItemSelect(player, s, clicked);
            case OPTION_SELECT -> handleOptionSelect(player, s, slot);
            case EFFECT_LIST -> handleEffectList(player, s, slot, clicked);
            case CUSTOM_LIST -> handleCustomList(player, s, slot, clicked);
            case LEVEL_SELECT -> handleLevelSelect(player, s, slot, clicked);
            case CONFIRM -> handleConfirm(player, s, slot);
            case REMOVE_LIST -> handleRemoveList(player, s, slot, clicked);
        }
    }

    // ------------------------------------------------------------------

    private void handleItemSelect(Player player, Session s, ItemStack clicked) {
        for (ItemKind kind : ItemKind.values()) {
            if (kind.icon() == clicked.getType()) {
                s.kind = kind;
                gui.openOptionSelect(player);
                return;
            }
        }
    }

    private void handleOptionSelect(Player player, Session s, int slot) {
        if (slot == GuiManager.SLOT_BACK) {
            gui.openItemSelect(player);
            return;
        }
        if (slot == 21) {
            s.type = GEBEnchant.Type.EFFECT;
            s.effectPage = 0;
            gui.openEffectList(player);
        } else if (slot == 23) {
            s.type = GEBEnchant.Type.CUSTOM;
            gui.openCustomList(player);
        }
    }

    private void handleEffectList(Player player, Session s, int slot, ItemStack clicked) {
        if (slot == GuiManager.SLOT_BACK) {
            gui.openOptionSelect(player);
            return;
        }
        if (slot == GuiManager.SLOT_PREV && clicked.getType() == Material.PAPER) {
            s.effectPage--;
            gui.openEffectList(player);
            return;
        }
        if (slot == GuiManager.SLOT_NEXT && clicked.getType() == Material.PAPER) {
            s.effectPage++;
            gui.openEffectList(player);
            return;
        }
        if (slot >= 36) return;

        List<PotionEffectType> effects = plugin.enchants().selectableEffects(s.kind.category());
        int index = s.effectPage * 36 + slot;
        if (index >= effects.size()) return;
        s.effectKey = effects.get(index).getKey().getKey();
        gui.openLevelSelect(player);
    }

    private void handleCustomList(Player player, Session s, int slot, ItemStack clicked) {
        if (slot == GuiManager.SLOT_BACK) {
            gui.openOptionSelect(player);
            return;
        }
        for (CustomEffect effect : CustomEffect.values()) {
            if (effect.icon() == clicked.getType() && effect.allows(s.kind)) {
                s.customEffect = effect;
                s.effectKey = effect.name();
                gui.openLevelSelect(player);
                return;
            }
        }
    }

    private void handleLevelSelect(Player player, Session s, int slot, ItemStack clicked) {
        if (slot == GuiManager.SLOT_BACK) {
            if (s.type == GEBEnchant.Type.CUSTOM) gui.openCustomList(player);
            else gui.openEffectList(player);
            return;
        }
        if (clicked.getType() != Material.EXPERIENCE_BOTTLE) return;
        s.level = slot + 1;
        gui.openConfirm(player);
    }

    private void handleConfirm(Player player, Session s, int slot) {
        switch (slot) {
            case GuiManager.SLOT_ACCEPT -> {
                GEBEnchant created = plugin.enchants().create(s.type, s.effectKey, s.kind, s.level);
                player.closeInventory();
                gui.clearSession(player);
                player.sendMessage(Component.text("[GEB] ", NamedTextColor.DARK_PURPLE)
                        .append(Component.text("Created enchantment ", NamedTextColor.GREEN))
                        .append(Component.text(created.displayName(), NamedTextColor.AQUA))
                        .append(Component.text(" for " + created.kind().display()
                                + ". Saved to systemenchantment.yml. Run ", NamedTextColor.GREEN))
                        .append(Component.text("/geb reload", NamedTextColor.GOLD))
                        .append(Component.text(" to activate it.", NamedTextColor.GREEN)));
            }
            case GuiManager.SLOT_CONFIRM_BACK -> gui.openLevelSelect(player);
            case GuiManager.SLOT_DECLINE -> {
                player.sendMessage(Component.text("[GEB] Enchantment creation cancelled.", NamedTextColor.RED));
                gui.openItemSelect(player); // revert back to page 1
            }
            default -> { }
        }
    }

    private void handleRemoveList(Player player, Session s, int slot, ItemStack clicked) {
        if (slot == GuiManager.SLOT_PREV && clicked.getType() == Material.PAPER) {
            s.removePage--;
            gui.openRemoveList(player);
            return;
        }
        if (slot == GuiManager.SLOT_NEXT && clicked.getType() == Material.PAPER) {
            s.removePage++;
            gui.openRemoveList(player);
            return;
        }
        if (slot >= 36) return;

        List<GEBEnchant> list = List.copyOf(plugin.enchants().all().values());
        int index = s.removePage * 36 + slot;
        if (index >= list.size()) return;

        GEBEnchant target = list.get(index);
        if (plugin.enchants().remove(target.id())) {
            player.sendMessage(Component.text("[GEB] ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text("Removed ", NamedTextColor.RED))
                    .append(Component.text(target.displayName(), NamedTextColor.AQUA))
                    .append(Component.text(" and reloaded instantly.", NamedTextColor.RED)));
        }
        gui.openRemoveList(player); // refresh the list
    }
}
