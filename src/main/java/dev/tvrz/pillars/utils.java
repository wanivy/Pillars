package dev.tvrz.pillars;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class utils {
    public static final List<String> enabledModes = new ArrayList<>();

    public static void loadEnabledModes(JavaPlugin plugin) {
        enabledModes.clear();

        File modesFolder = new File(plugin.getDataFolder(), "modes");

        // Если папки нет — создаём
        if (!modesFolder.exists()) {
            modesFolder.mkdirs();
            return;
        }

        File[] files = modesFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            if (config.getBoolean("enabled", false)) {
                String modeName = file.getName().replace(".yml", "");
                enabledModes.add(modeName);
            }
        }
    }
}


