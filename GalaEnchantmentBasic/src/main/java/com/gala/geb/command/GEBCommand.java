package com.gala.geb.command;

import com.gala.geb.GEBPlugin;
import com.gala.geb.enchant.GEBEnchant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GEBCommand implements CommandExecutor, TabCompleter {

    private final GEBPlugin plugin;

    public GEBCommand(GEBPlugin plugin) {
        this.plugin = plugin;
    }

    private Component prefix(String text, NamedTextColor color) {
        return Component.text("[GEB] ", NamedTextColor.DARK_PURPLE)
                .append(Component.text(text, color));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        // Operator / admin only
        if (!sender.isOp() && !sender.hasPermission("geb.admin")) {
            sender.sendMessage(prefix("Only operators/admins can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(prefix("Only players can open the create GUI.", NamedTextColor.RED));
                    return true;
                }
                plugin.gui().openItemSelect(player);
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(prefix("Only players can open the remove GUI.", NamedTextColor.RED));
                    return true;
                }
                plugin.gui().session(player).removePage = 0;
                plugin.gui().openRemoveList(player);
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.enchants().reload();
                sender.sendMessage(prefix("Config and systemenchantment.yml reloaded. "
                        + plugin.enchants().all().size() + " enchantment(s) active.", NamedTextColor.GREEN));
            }
            case "list" -> {
                if (plugin.enchants().all().isEmpty()) {
                    sender.sendMessage(prefix("No custom enchantments created yet.", NamedTextColor.YELLOW));
                    return true;
                }
                sender.sendMessage(prefix("Custom enchantments:", NamedTextColor.GREEN));
                for (GEBEnchant e : plugin.enchants().all().values()) {
                    sender.sendMessage(Component.text(" - ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(e.displayName(), NamedTextColor.AQUA))
                            .append(Component.text(" (" + e.kind().display() + ", id: " + e.id() + ")",
                                    NamedTextColor.GRAY)));
                }
            }
            case "give" -> handleGive(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    /** /geb give <player> <enchantId>  ->  gives the enchanted book */
    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(prefix("Usage: /geb give <player> <enchantId>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(prefix("Player not found: " + args[1], NamedTextColor.RED));
            return;
        }
        GEBEnchant enchant = plugin.enchants().get(args[2].toLowerCase(Locale.ROOT));
        if (enchant == null) {
            sender.sendMessage(prefix("Unknown enchantment id: " + args[2], NamedTextColor.RED));
            return;
        }
        target.getInventory().addItem(plugin.enchants().createBook(enchant));
        sender.sendMessage(prefix("Gave " + target.getName() + " the book: "
                + enchant.displayName(), NamedTextColor.GREEN));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(prefix("Gala Enchantment Basic", NamedTextColor.LIGHT_PURPLE));
        sender.sendMessage(Component.text("/geb create ", NamedTextColor.GOLD)
                .append(Component.text("- open the enchantment creator GUI", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/geb remove ", NamedTextColor.GOLD)
                .append(Component.text("- remove created enchantments (instant reload)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/geb reload ", NamedTextColor.GOLD)
                .append(Component.text("- reload config + systemenchantment.yml", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/geb list ", NamedTextColor.GOLD)
                .append(Component.text("- list all created enchantments", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/geb give <player> <id> ", NamedTextColor.GOLD)
                .append(Component.text("- give an enchanted book", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("create", "remove", "reload", "list", "give")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(sub);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (String id : plugin.enchants().all().keySet()) {
                if (id.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(id);
            }
        }
        return out;
    }
}
