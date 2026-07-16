package dev.tvrz.pillars;

import dev.tvrz.pillars.commands.StatusCommand;
import dev.tvrz.pillars.managers.UtilsManager;
import fr.mrmicky.fastboard.FastBoard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.tvrz.pillars.commands.PillarsCommand;
import dev.tvrz.pillars.commands.ReloadCommand;
import dev.tvrz.pillars.listeners.pillarsEventListeners;
import org.jspecify.annotations.NonNull;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.tvrz.pillars.managers.UtilsManager.updateBoard;
import static dev.tvrz.pillars.managers.UtilsManager.loadEnabledModes;

public final class MainPaper extends JavaPlugin {

    @Override
    public void onEnable() {

        UtilsManager.init(this);

        saveDefaultConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        PillarsCommand pillarsExecutor = new PillarsCommand(this);
        getServer().getCommandMap().register("pillars", new Command("pillars") {
            @Override
            public boolean execute(@NonNull CommandSender sender, @NonNull String commandLabel, String @NonNull [] args) {
                return pillarsExecutor.onCommand(sender, this, commandLabel, args);
            }

            @Override
            public @NonNull List<String> tabComplete(@NonNull CommandSender sender, @NonNull String alias, String @NonNull [] args) throws IllegalArgumentException {
                List<String> completions = pillarsExecutor.onTabComplete(sender, this, alias, args);
                return completions != null ? completions : super.tabComplete(sender, alias, args);
            }
        });

        ReloadCommand reloadExecutor = new ReloadCommand(this);
        getServer().getCommandMap().register("pillars", new Command("pillars-reload") {
            @Override
            public boolean execute(@NonNull CommandSender sender, @NonNull String commandLabel, String @NonNull [] args) {
                return reloadExecutor.onCommand(sender, this, commandLabel, args);
            }
        });

        StatusCommand statusExecutor = new StatusCommand();
        getServer().getCommandMap().register("pillars", new Command("pillars-status") {
            @Override
            public boolean execute(@NonNull CommandSender sender, @NonNull String commandLabel, String @NonNull [] args) {
                return statusExecutor.onCommand(sender, this, commandLabel, args);
            }
        });

        getServer().getPluginManager().registerEvents(new pillarsEventListeners(this), this);

        UtilsManager.loadEnabledModes(this);

        File modesFolder = new File(getDataFolder(), "modes");
        if (!modesFolder.exists() && !modesFolder.mkdirs()) {
            getLogger().warning("Failed to create modes folder");
        }

        File items = new File(modesFolder, "items.yml");
        if (!items.exists()) {
            try (InputStream in = getResource("items.yml")) {
                if (in != null) {
                    Files.copy(in, items.toPath());
                }
            } catch (IOException e) {
                getLogger().severe("Failed to copy items.yml: " + e.getMessage());
            }
        }

        File bouncers = new File(modesFolder, "bouncers.yml");
        if (!bouncers.exists()) {
            try (InputStream in = getResource("bouncers.yml")) {
                if (in != null) {
                    Files.copy(in, bouncers.toPath());
                }
            } catch (IOException e) {
                getLogger().severe("Failed to copy bouncers.yml: " + e.getMessage());
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

}