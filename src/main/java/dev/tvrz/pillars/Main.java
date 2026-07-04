package dev.tvrz.pillars;

import fr.mrmicky.fastboard.FastBoard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.tvrz.pillars.commands.pillars;
import dev.tvrz.pillars.commands.reload;
import dev.tvrz.pillars.listeners.pillarsEventListeners;

import java.io.*;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

import static dev.tvrz.pillars.commands.pillars.updateBoard;
import static dev.tvrz.pillars.utils.loadEnabledModes;

public final class Main extends JavaPlugin {

    @Override
    public void onEnable() {

        saveDefaultConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getCommand("pillars").setExecutor(new pillars(this));
        getCommand("pillars").setTabCompleter(new pillars(this));
        getCommand("pillars-reload").setExecutor(new reload(this));
        getServer().getPluginManager().registerEvents(new pillarsEventListeners(this), this);

        File modesFolder = new File(getDataFolder(), "modes");
        if (!modesFolder.exists()) {
            modesFolder.mkdirs();
            File items = new File(modesFolder, "items.yml");
            try (InputStream in = getResource("items.yml")) {
                if (in != null) {
                    Files.copy(in, items.toPath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            File bouncers = new File(modesFolder, "bouncers.yml");
            try (InputStream in = getResource("bouncers.yml")) {
                if (in != null) {
                    Files.copy(in, bouncers.toPath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        loadEnabledModes(this);

        FileConfiguration config = getConfig();
        ConfigurationSection scoreboard = config.getConfigurationSection("fastboard");
        if (scoreboard != null && scoreboard.getBoolean("enabled")) {
            getServer().getScheduler().runTaskTimer(this, () -> {
                for (Map.Entry<UUID, FastBoard> entry : pillarsEventListeners.boards.entrySet()) {
                    UUID uuid = entry.getKey();
                    FastBoard board = entry.getValue();

                    updateBoard(board, uuid);
                }
            }, 0, 5);
        }
        getLogger().info(" ");
        getLogger().info(" ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░");
        getLogger().info(" ░░██████╗░██╗██╗░░░░░██╗░░░░░░█████╗░██████╗░░██████╗░░");
        getLogger().info(" ░░██╔══██╗██║██║░░░░░██║░░░░░██╔══██╗██╔══██╗██╔════╝░░");
        getLogger().info(" ░░██████╔╝██║██║░░░░░██║░░░░░███████║██████╔╝╚█████╗░░░");
        getLogger().info(" ░░██╔═══╝░██║██║░░░░░██║░░░░░██╔══██║██╔══██╗░╚═══██╗░░");
        getLogger().info(" ░░██║░░░░░██║███████╗███████╗██║░░██║██║░░██║██████╔╝░░");
        getLogger().info(" ░░╚═╝░░░░░╚═╝╚══════╝╚══════╝╚═╝░░╚═╝╚═╝░░╚═╝╚═════╝░░░");
        getLogger().info(" ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░");
        getLogger().info(" ");
    }

    @Override
    public void onDisable() {

    }
}