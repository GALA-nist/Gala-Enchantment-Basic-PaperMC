package com.gala.geb;

import com.gala.geb.command.GEBCommand;
import com.gala.geb.enchant.EnchantManager;
import com.gala.geb.gui.GuiManager;
import com.gala.geb.listener.EffectListener;
import com.gala.geb.listener.EnchantTableListener;
import com.gala.geb.listener.GuiListener;
import com.gala.geb.task.EquipEffectTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public final class GEBPlugin extends JavaPlugin {

    /** All plugin files live in plugins/Gala Enchantment Basic/ */
    private static final String DATA_FOLDER_NAME = "Gala Enchantment Basic";

    private EnchantManager enchantManager;
    private GuiManager guiManager;
    private File dataDir;
    private FileConfiguration config;

    // ------------------------------------------------------------------
    //  Custom data folder: "Gala Enchantment Basic" (with spaces)
    //  (getDataFolder() is final in Paper 26.1, so we use our own method)
    // ------------------------------------------------------------------

    public @NotNull File getDataDir() {
        if (dataDir == null) {
            dataDir = new File(getDataFolder().getParentFile(), DATA_FOLDER_NAME);
        }
        return dataDir;
    }

    @Override
    public @NotNull FileConfiguration getConfig() {
        if (config == null) reloadConfig();
        return config;
    }

    @Override
    public void reloadConfig() {
        saveDefaultConfig();
        config = YamlConfiguration.loadConfiguration(new File(getDataDir(), "config.yml"));
    }

    @Override
    public void saveDefaultConfig() {
        File file = new File(getDataDir(), "config.yml");
        if (file.exists()) return;
        getDataDir().mkdirs();
        try (InputStream in = getResource("config.yml")) {
            if (in != null) Files.copy(in, file.toPath());
        } catch (IOException e) {
            getLogger().severe("Could not create default config.yml: " + e.getMessage());
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        enchantManager = new EnchantManager(this);
        enchantManager.reload();

        guiManager = new GuiManager(this);

        GEBCommand executor = new GEBCommand(this);
        PluginCommand command = getCommand("geb");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new EffectListener(this), this);
        getServer().getPluginManager().registerEvents(new EnchantTableListener(this), this);

        int refresh = Math.max(10, getConfig().getInt("effect-refresh-ticks", 40));
        new EquipEffectTask(this).runTaskTimer(this, refresh, refresh);

        getLogger().info("Gala Enchantment Basic enabled.");
    }

    @Override
    public void onDisable() {
        if (enchantManager != null) enchantManager.save();
        getLogger().info("Gala Enchantment Basic disabled.");
    }

    public EnchantManager enchants() { return enchantManager; }

    public GuiManager gui() { return guiManager; }
}
