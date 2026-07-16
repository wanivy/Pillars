package dev.tvrz.pillars.managers;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import fr.mrmicky.fastboard.FastBoard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import static dev.tvrz.pillars.commands.PillarsCommand.gameArena;
import static dev.tvrz.pillars.commands.PillarsCommand.gameMode;
import static dev.tvrz.pillars.commands.PillarsCommand.gameTimers;
import static dev.tvrz.pillars.commands.PillarsCommand.gameTimerF;

public class UtilsManager {

    private static JavaPlugin plugin;
    private static final Random random = new Random();
    public static final List<String> enabledModes = new ArrayList<>();
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    public UtilsManager(JavaPlugin plugin) {
        init(plugin);
    }

    public static void init(JavaPlugin plugin) {
        UtilsManager.plugin = plugin;
    }

    private static final Set<String> WORLD_COPY_EXCLUDES = Set.of(
            "uid.dat", "session.lock"
    );

    public static void loadEnabledModes(JavaPlugin plugin) {
        enabledModes.clear();

        File modesFolder = new File(plugin.getDataFolder(), "modes");

        if (!modesFolder.exists()) {
            if (!modesFolder.mkdirs()) {
                plugin.getLogger().warning("Failed to create modes folder");
            }
            return;
        }

        File[] files = modesFolder.listFiles((_, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            if (config.getBoolean("enabled", false)) {
                String modeName = file.getName().replace(".yml", "");
                enabledModes.add(modeName);
            }
        }
    }

    public static Map<String, String> ParseSettings(String arg) {
        return Arrays.stream(arg.split("\\s+"))
                .map(s -> s.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> parts[0].trim(),
                        parts -> parts[1].trim(),
                        (_, replacement) -> replacement
                ));
    }

    public static void CreatePillar(int x, int y, int z, int height, String baseBlock, String worldName) {
        Material baseMat = Material.getMaterial(baseBlock);
        if (baseMat != null) FillRegion(worldName, x, y, z, x, y - height, z, baseMat);
        SetBlock(worldName, x, y + 4, z, Material.GLASS);
        FillRegion(worldName, x+1, y+5, z, x+1, y+7, z, Material.GLASS);
        FillRegion(worldName, x-1, y+5, z, x-1, y+7, z, Material.GLASS);
        FillRegion(worldName, x, y+5, z+1, x, y+7, z+1, Material.GLASS);
        FillRegion(worldName, x, y+5, z-1, x, y+7, z-1, Material.GLASS);
    }

    public static void SetBlock(String worldName, int x, int y, int z, Material material) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) world.getBlockAt(x, y, z).setType(material);
    }

    public static Material getRandomBlockMaterial() {
        List<Material> itemMaterials = Arrays.stream(Material.values())
                .filter(Material::isItem)
                .toList();

        return itemMaterials.get(random.nextInt(itemMaterials.size()));
    }

    public static float[] GetYawPitch(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - (from.getY() + 1.62);
        double dz = to.getZ() - from.getZ();

        double distanceXZ = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distanceXZ));

        yaw = (yaw + 360) % 360;

        return new float[]{yaw, pitch};
    }

    public static String format(Integer a, Integer b) {
        StringBuilder filled = new StringBuilder();
        filled.repeat("▮", Math.max(0, a - 1));
        StringBuilder empty = new StringBuilder();
        empty.repeat("▮", Math.max(0, b + 1));
        return "<white><b>" + filled + "</b></white><gray><b>" + empty + "</b></gray>";
    }

    public static void FillRegion(String worldName, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
            int maxX = Math.max(x1, x2), maxY = Math.max(y1, y2), maxZ = Math.max(z1, z2);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        world.getBlockAt(x, y, z).setType(material);
                    }
                }
            }
            System.out.println("Заполнено " + (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) + " блоков " + material.name() + " в " + worldName + ".");
        }
    }

    public static void sendPlayerToServer(Player player, String targetServer) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);

        try {
            out.writeUTF("Connect");
            out.writeUTF(targetServer);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to send player to server: " + e.getMessage());
        }

        player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
    }

    public static void updateBoard(FastBoard board, UUID playerUUID) {
        if (plugin == null) return;

        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null || !player.isOnline()) {
            return;
        }

        String worldName = player.getWorld().getName();

        long aliveCount = player.getWorld().getPlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL)
                .count();

        ConfigurationSection fastboard = plugin.getConfig().getConfigurationSection("fastboard");
        if (fastboard == null) return;

        List<String> lines = fastboard.getStringList("lines").stream()
                .map(line -> {
                    Component component = miniMessage.deserialize(line,
                            Placeholder.unparsed("player", player.getName()),
                            Placeholder.unparsed("arena", Objects.toString(gameArena.get(worldName), "Загрузка...")),
                            Placeholder.unparsed("mode", Objects.toString(gameMode.get(worldName), "Загрузка...")),
                            Placeholder.unparsed("timer", Objects.toString(gameTimers.get(worldName), "Загрузка...")),
                            Placeholder.unparsed("timer-formatted", Objects.toString(gameTimerF.get(worldName), "Загрузка...")),
                            Placeholder.unparsed("players-alive", String.valueOf(aliveCount))
                    );
                    return LegacyComponentSerializer.legacySection().serialize(component);
                })
                .toList();

        board.updateLines(lines.toArray(new String[0]));
    }

    public static double CalculateDiameter(int nPoints, double targetDist) {
        if (nPoints < 2) {
            return 0;
        }
        double angle = Math.PI / nPoints;
        return targetDist / Math.sin(angle);
    }

    public static List<double[]> GetCirclePoints(double diameter, int n, double centerX, double centerY) {
        double radius = diameter / 2;
        List<double[]> points = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            points.add(new double[]{x, y});
        }

        return points;
    }

    public static void GivePotionEffects(String worldName, ConfigurationSection effectsSelection, Player singlePlayer) {

        List<?> effects = effectsSelection.getList("effects");
        if (effects == null || effects.isEmpty()) return;

        World world = Bukkit.getWorld(worldName);

        List<Player> targetPlayers;
        if ((singlePlayer != null)) {
            targetPlayers = List.of(singlePlayer);
        } else {
            assert world != null;
            targetPlayers = world.getPlayers();
        }
        if (targetPlayers.isEmpty()) return;

        for (Object obj : effects) {
            ConfigurationSection data = null;

            if (obj instanceof ConfigurationSection cs) {
                data = cs;
            } else if (obj instanceof Map<?, ?> map) {
                data = new MemoryConfiguration();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    data.set(entry.getKey().toString(), entry.getValue());
                }
            }

            if (data == null) continue;

            try {
                @SuppressWarnings("deprecation")
                PotionEffectType effectType = PotionEffectType.getByName(data.getString("type", "SPEED"));
                if (effectType == null) {
                    plugin.getLogger().warning("Unknown potion effect type: " + data.getString("type", "SPEED"));
                    continue;
                }

                PotionEffect effect = new PotionEffect(
                        effectType,
                        data.getInt("duration", 1) * 20,
                        data.getInt("amplifier", 1),
                        data.getBoolean("ambient", false),
                        data.getBoolean("particles", false)
                );

                for (Player p : targetPlayers) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.addPotionEffect(effect));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error applying potion effect: " + e.getMessage());
            }
        }
    }

    public static ConfigurationSection GetModeSection(String fileName) {

        File folder = new File(plugin.getDataFolder(), "modes");
        File file = new File(folder, fileName + ".yml");

        if (!file.exists()) {
            return null;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        return config.getConfigurationSection("");
    }

    public static void PlayTitles(String worldName, ConfigurationSection titlesSection, Map<String, String> placeholders, Player singlePlayer, String sequenceName) {
        if (titlesSection == null || !titlesSection.getBoolean("enabled", true)) return;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        List<?> sequence = titlesSection.getList(sequenceName);
        if (sequence == null || sequence.isEmpty()) return;

        List<Player> targetPlayers = (singlePlayer != null) ? List.of(singlePlayer) : world.getPlayers();
        if (targetPlayers.isEmpty()) return;

        for (Object obj : sequence) {
            ConfigurationSection data = null;

            if (obj instanceof ConfigurationSection cs) {
                data = cs;
            } else if (obj instanceof Map<?, ?> map) {
                data = new MemoryConfiguration();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    data.set(entry.getKey().toString(), entry.getValue());
                }
            }

            if (data == null) continue;

            String rawTitle = data.getString("title", "");
            String rawSubtitle = data.getString("subtitle", "");

            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    String ph = "%" + entry.getKey() + "%";
                    rawTitle = rawTitle.replace(ph, entry.getValue());
                    rawSubtitle = rawSubtitle.replace(ph, entry.getValue());
                }
            }

            Component title = miniMessage.deserialize(rawTitle);
            Component subtitle = miniMessage.deserialize(rawSubtitle);

            int fadeIn = data.getInt("fade-in", 10);
            int stay = data.getInt("stay", 40);
            int fadeOut = data.getInt("fade-out", 10);
            int delayAfter = data.getInt("delay-after", 0);


            net.kyori.adventure.title.Title titleObj = net.kyori.adventure.title.Title.title(
                    title,
                    subtitle,
                    net.kyori.adventure.title.Title.Times.times(
                            Duration.ofMillis(fadeIn * 50L),
                            Duration.ofMillis(stay * 50L),
                            Duration.ofMillis(fadeOut * 50L)
                    )
            );

            for (Player p : targetPlayers) {
                if (!(Objects.equals(rawTitle, "") && Objects.equals(rawSubtitle, ""))) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.showTitle(titleObj));
                }

                if (data.isConfigurationSection("sound")) {
                    ConfigurationSection soundData = data.getConfigurationSection("sound");
                    if (soundData != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                NamespacedKey key = NamespacedKey.minecraft(soundData.getString("name", "entity.experience_orb.pickup"));
                                Sound sound = Registry.SOUNDS.get(key);
                                assert sound != null;
                                p.playSound(p.getLocation(), sound,
                                        (float) soundData.getDouble("volume", 1.0),
                                        (float) soundData.getDouble("pitch", 1.0));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                }
            }

            try {
                if (delayAfter * 50L > 0) {
                    Thread.sleep(delayAfter * 50L);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public static Boolean CreateWorld(String newWorld, Integer deathSpawnX, Integer deathSpawnY, Integer deathSpawnZ, Boolean border, Integer borderDiameter, Integer borderTime) {
        if (Bukkit.getWorld(newWorld) != null) {
            return true;
        }

        try {
            World world = new WorldCreator(newWorld).createWorld();
            if (world == null) {
                return false;
            }

            Location safeSpawn = new Location(
                    world,
                    deathSpawnX + 0.5, deathSpawnY, deathSpawnZ + 0.5,
                    0f, 0f
            );
            world.setSpawnLocation(safeSpawn);

            if (border) {
                WorldBorder worldBorder = world.getWorldBorder();
                worldBorder.setCenter(0.5, 0.5);
                worldBorder.setSize(borderDiameter);
                worldBorder.changeSize(1, borderTime* 20L);
                worldBorder.setDamageAmount(5.0);
                worldBorder.setDamageBuffer(0);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error creating world: " + e.getMessage());
            if (Bukkit.getWorld(newWorld) != null) {
                DeleteWorld(newWorld);
            }
            return false;
        }

        return true;
    }

    public static boolean CopyWorldFolder(String sourceWorldName, String targetWorldName) {
        World loadedSource = Bukkit.getWorld(sourceWorldName);
        File sourceFolder = (loadedSource != null)
                ? loadedSource.getWorldFolder()
                : new File(Bukkit.getWorldContainer(), sourceWorldName);

        if (!sourceFolder.exists() || !sourceFolder.isDirectory()) {
            plugin.getLogger().warning("Исходный мир не найден на диске: " + sourceFolder.getPath());
            return false;
        }

        File targetFolder = new File(Bukkit.getWorldContainer(), targetWorldName);
        if (targetFolder.exists()) {
            plugin.getLogger().warning("Папка мира уже существует: " + targetFolder.getPath());
            return false;
        }

        Path sourcePath = sourceFolder.toPath();
        Path targetPath = targetFolder.toPath();

        try {
            try (var stream = Files.walk(sourcePath)) {
                stream.forEach(path -> {
                    String relative = sourcePath.relativize(path).toString();
                    if (relative.isEmpty()) return;
                    if (WORLD_COPY_EXCLUDES.contains(relative)
                            || relative.startsWith("playerdata")
                            || relative.startsWith("stats")
                            || relative.startsWith("advancements")) {
                        return;
                    }
                    try {
                        Path destination = targetPath.resolve(relative);
                        if (Files.isDirectory(path)) {
                            Files.createDirectories(destination);
                        } else {
                            Files.createDirectories(destination.getParent());
                            Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        } catch (IOException | UncheckedIOException e) {
            plugin.getLogger().warning("Ошибка копирования мира " + sourceWorldName + " -> " + targetWorldName + ": " + e.getMessage());
            return false;
        }

        return true;
    }

    public static void DeleteWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        if (!world.getPlayers().isEmpty()) {
            World fallback = Bukkit.getWorld("world");
            for (Player player : new ArrayList<>(world.getPlayers())) {
                player.teleport(new Location(fallback, 0.5, 100, 0.5));
                player.setGameMode(GameMode.SURVIVAL);
            }
        }

        File worldFolder = world.getWorldFolder();
        boolean unloaded = Bukkit.unloadWorld(world, false);
        if (!unloaded) {
            plugin.getLogger().warning("Не удалось выгрузить мир: " + worldName);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                DeleteDirectory(worldFolder.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось удалить папку мира " + worldName + ": " + e.getMessage());
            }
        });
    }

    public static void DeleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }
}