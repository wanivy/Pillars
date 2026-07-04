package dev.tvrz.pillars.commands;

import fr.mrmicky.fastboard.FastBoard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.alessiodp.parties.api.Parties;
import com.alessiodp.parties.api.interfaces.PartiesAPI;
import com.alessiodp.parties.api.interfaces.Party;

import static org.bukkit.Bukkit.getLogger;
import static dev.tvrz.pillars.utils.enabledModes;

public class pillars implements CommandExecutor, Listener, TabCompleter {

    private static JavaPlugin plugin = null;
    private static final Random random = new Random();
    private static PartiesAPI partiesAPI;

    // Имена файлов/папок мира, которые не нужно копировать при клонировании
    private static final Set<String> WORLD_COPY_EXCLUDES = Set.of(
            "uid.dat", "session.lock"
    );

    public static Set<UUID> blockedUUIDs = ConcurrentHashMap.newKeySet();
    public static Map<String, String> gameArena = new HashMap<>();
    public static Map<String, String> gameMode = new HashMap<>();
    public static Map<String, String> gameTimers = new HashMap<>();
    public static Map<String, String> gameTimerF = new HashMap<>();
    public static Map<String, String> activeGames = new HashMap<>();
    public static Map<String, int[]> gameProgress = new HashMap<>();

    public static Map<UUID, Location> playerSpawns = new HashMap<>();

    private static final Map<String, List<String>> SETTINGS = new HashMap<>();

    public pillars(JavaPlugin plugin) {
        this.plugin = plugin;
        partiesAPI = Parties.getApi();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location spawnLoc = playerSpawns.remove(player.getUniqueId());
        if (spawnLoc != null) {
            player.teleport(spawnLoc);
            player.getInventory().clear();
            player.getEnderChest().clear();
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(5.0f);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        }
    }

    static {
        SETTINGS.put("world", Bukkit.getWorlds()
                .stream()
                .map(World::getName)
                .collect(Collectors.toList()));
        SETTINGS.put("mode", enabledModes);
        SETTINGS.put("spawnY", Collections.emptyList());
        SETTINGS.put("deathSpawnX", Collections.emptyList());
        SETTINGS.put("deathSpawnY", Collections.emptyList());
        SETTINGS.put("deathSpawnZ", Collections.emptyList());
        SETTINGS.put("border", Arrays.asList("true", "false"));
        SETTINGS.put("borderDiameter", Collections.emptyList());
        SETTINGS.put("borderTime", Collections.emptyList());
        SETTINGS.put("circleDiameter", Collections.emptyList());
        SETTINGS.put("pillarHeight", Arrays.asList("1", "4", "8", "16"));
        SETTINGS.put("pillarsBlocks", Arrays.stream(Material.values())
                .map(Material::name)
                .collect(Collectors.toList()));
        SETTINGS.put("itemGiveDelay", Arrays.asList("4", "8", "10"));
        SETTINGS.put("debugMode", Arrays.asList("true", "false"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length <= 1) {
            return null;
        }

        Set<String> usedKeys = new HashSet<>();

        // Собираем уже использованные ключи
        for (String arg : args) {
            if (arg.contains("=")) {
                usedKeys.add(arg.split("=")[0].toLowerCase());
            }
        }

        String currentArg = args[args.length - 1];

        // Если вводится значение (есть "=")
        if (currentArg.contains("=")) {
            String[] split = currentArg.split("=", 2);
            String key = split[0];
            String valuePart = split.length > 1 ? split[1].toLowerCase() : "";

            if (!SETTINGS.containsKey(key)) {
                return Collections.emptyList();
            }

            List<String> possibleValues = SETTINGS.get(key);

            // Если значений нет (числовое поле) — не предлагаем ничего
            if (possibleValues.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> completions = new ArrayList<>();
            for (String value : possibleValues) {
                if (value.toLowerCase().startsWith(valuePart)) {
                    completions.add(key + "=" + value);
                }
            }

            return completions;
        }

        // Если вводится ключ
        String keyPart = currentArg.toLowerCase();
        List<String> completions = new ArrayList<>();

        for (String key : SETTINGS.keySet()) {
            if (usedKeys.contains(key.toLowerCase())) continue;

            if (key.toLowerCase().startsWith(keyPart)) {
                completions.add(key + "=");
            }
        }

        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Используйте: /pillars <ID игры> [параметры]");
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String settingsString = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            Map<String, String> settings = ParseSettings(settingsString);
            String partyName = args[0];
            String worldName = "game_" + partyName.toLowerCase();

            File dataFolder = new File(plugin.getDataFolder(), "modes");
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File file = new File(dataFolder, settings.getOrDefault("mode", "items")+".yml");
            if (!file.exists()) {
                sender.sendMessage("Ошибка: Не удалось загрузить режим " + settings.getOrDefault("mode", "items"));
                return;
            }

            // Копирование файлов мира — тяжёлая I/O операция, делаем её здесь, на async-потоке,
            // чтобы не блокировать главный поток сервера
            if (!CopyWorldFolder(settings.getOrDefault("world", "world"), worldName)) {
                sender.sendMessage("Ошибка: не удалось скопировать мир " + settings.getOrDefault("world", "world"));
                return;
            }

            gameArena.put(worldName, settings.getOrDefault("world", "world"));
            gameMode.put(worldName, settings.getOrDefault("mode", "items"));

            CompletableFuture<Void> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Boolean successful = СreateWorld(
                        worldName,
                        Integer.parseInt(settings.getOrDefault("deathSpawnX", "0")),
                        Integer.parseInt(settings.getOrDefault("deathSpawnY", Integer.toString(Integer.parseInt(settings.getOrDefault("spawnY", "10")) + 15))),
                        Integer.parseInt(settings.getOrDefault("deathSpawnZ", "0")),
                        settings.getOrDefault("border", "true").equals("true"),
                        Integer.parseInt(settings.getOrDefault("borderDiameter", "75")),
                        Integer.parseInt(settings.getOrDefault("borderTime", "600"))
                );
                if (!successful) {
                    sender.sendMessage("Ошибка: не удалось загрузить скопированный мир " + worldName);
                }
                future.complete(null);
            });
            future.join();
            if (Bukkit.getWorld(worldName) == null) {
                return;
            }
            gameArena.put(worldName, settings.get("worldName"));
            activeGames.put(worldName, settingsString);
            Party party = partiesAPI.getParty(partyName);
            int partyFetchAttempts = 0;
            while (party == null && partyFetchAttempts < 10) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                party = partiesAPI.getParty(partyName);
                partyFetchAttempts++;
            }
            if (party == null) {
                sender.sendMessage(ChatColor.RED + "Ошибка: не удалось получить пати " + partyName);
                return;
            }
            final List<UUID> gamePartyList = new ArrayList<>();
            getLogger().info(party.getMembers().toString());
            gamePartyList.addAll(party.getMembers());
            gameProgress.put(worldName, new int[]{0, gamePartyList.size()});
            Bukkit.getScheduler().runTask(plugin, () -> {
                InitPlayerSpawns(
                        worldName,
                        gamePartyList,
                        (double) Integer.parseInt(settings.getOrDefault("circleDiameter", "0")),
                        Integer.parseInt(settings.getOrDefault("spawnY", "10")),
                        Integer.parseInt(settings.getOrDefault("pillarHeight", "10")),
                        settings.getOrDefault("pillarsBlocks", "bedrock").toUpperCase()
                );
            });
            StartGame(partyName, settings);
        });
        return true;
    }

    public void StartGame(String partyName, Map<String, String> settings) {
        FileConfiguration config = plugin.getConfig();
        String worldName = "game_" + partyName.toLowerCase();
        Party party = partiesAPI.getParty(partyName);
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            List<UUID> playerList = Bukkit.getWorld(worldName).getPlayers().stream()
                    .map(Player::getUniqueId)
                    .toList();
            if (playerList.containsAll(party.getMembers())) {
                break;
            }
        }

        ConfigurationSection mode = GetModeSection(settings.getOrDefault("mode", "items"));
        if (mode == null) {
            getLogger().warning("Не удалось загрузить режим: " + settings.getOrDefault("mode", "items"));
            Bukkit.getScheduler().runTask(plugin, () -> DeleteWorld(worldName));
            return;
        }

        ConfigurationSection startTitles = mode.getConfigurationSection("start-titles");
        PlayTitles(worldName, startTitles, null, null, "sequence");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayTitles(worldName, startTitles, null, null, "after");
        });

        for (UUID playerUUID : party.getMembers()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.getInventory().clear();
            player.getEnderChest().clear();
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(5.0f);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            if (!mode.getBoolean("block-move.enabled", false)) {
                blockedUUIDs.remove(playerUUID);
            } else {
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    blockedUUIDs.remove(playerUUID);
                }, mode.getInt("block-move.duration") * 20L);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                FillRegion(worldName, (int) (player.getX()-2), Integer.parseInt(settings.getOrDefault("spawnY", "10")) + 3, (int) (player.getZ()-2), (int) (player.getX()+2), Integer.parseInt(settings.getOrDefault("spawnY", "10")) + 7, (int) (player.getZ()+2), Material.AIR);
            });
        }

        if (mode.getBoolean("potion-effects.enabled", false)) {
            GivePotionEffects(worldName, mode.getConfigurationSection("potion-effects"), null);
        }

        StartGameItemsCycle(worldName, settings.getOrDefault("debugMode", "false").equals("true"), settings, mode);

        Map<String, String> placeholders = new HashMap<>();
        List<Player> alivePlayers = Bukkit.getWorld(worldName).getPlayers().stream()
                .filter(player -> player.getGameMode() == GameMode.SURVIVAL)
                .collect(Collectors.toList());
        if (!alivePlayers.isEmpty()) {
            ConfigurationSection endTitles = mode.getConfigurationSection("end-titles.win");
            placeholders.put("player", alivePlayers.getFirst().getName());
            PlayTitles(worldName, endTitles, placeholders, null, "sequence");
        } else {
            ConfigurationSection endTitles = mode.getConfigurationSection("end-titles.draw");
            PlayTitles(worldName, endTitles, null, null, "sequence");
        }

        for (Player player : Bukkit.getWorld(worldName).getPlayers()) {
            player.getInventory().clear();
            player.getEnderChest().clear();
        }

        ConfigurationSection velocity = config.getConfigurationSection("velocity");
        if (velocity != null && velocity.getBoolean("enabled", false) && velocity.getBoolean("connect-to-lobby-on-game-end", false)) {
            for (Player player : Bukkit.getWorld(worldName).getPlayers()) {
                if (player != null) sendPlayerToServer(player, velocity.get("velocity-lobby").toString());
            }
        }
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            DeleteWorld(worldName);
        });
        gameMode.remove(worldName);
        gameArena.remove(worldName);
        gameTimers.remove(worldName);
        gameTimerF.remove(worldName);
        activeGames.remove(worldName);
    }

    public static void GivePotionEffects(String worldName, ConfigurationSection effectsSelection, Player singlePlayer) {

        List<?> effects = effectsSelection.getList("effects");
        if (effects == null || effects.isEmpty()) return;

        World world = Bukkit.getWorld(worldName);

        List<Player> targetPlayers = (singlePlayer != null) ? List.of(singlePlayer) : world.getPlayers();
        if (targetPlayers.isEmpty()) return;

        for (Object obj : effects) {
            ConfigurationSection data = null;

            // Обработка объекта данных
            if (obj instanceof ConfigurationSection cs) {
                data = cs;
            } else if (obj instanceof Map<?, ?> map) {
                data = new MemoryConfiguration();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    data.set(entry.getKey().toString(), entry.getValue());
                }
            }

            if (data == null) continue;

            PotionEffect effect = new PotionEffect(
                    PotionEffectType.getByName(data.getString("type").toUpperCase()),
                    data.getInt("duration", 1) * 20,
                    data.getInt("amplifier", 1),
                    data.getBoolean("ambient", false),
                    data.getBoolean("particles", false)
            );

            for (Player p : targetPlayers) {
                Bukkit.getScheduler().runTask(plugin, () -> p.addPotionEffect(effect));
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

        // возвращаем корневую секцию
        return config.getConfigurationSection("");
    }

    public static void PlayTitles(String worldName, ConfigurationSection titlesSection, Map<String, String> placeholders, Player singlePlayer, String sequenceName) {
        if (titlesSection == null || !titlesSection.getBoolean("enabled", true)) return;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        List<?> sequence = titlesSection.getList(sequenceName);
        if (sequence == null || sequence.isEmpty()) return;

        MiniMessage miniMessage = MiniMessage.miniMessage();

        // Определяем список получателей
        List<Player> targetPlayers = (singlePlayer != null) ? List.of(singlePlayer) : world.getPlayers();
        if (targetPlayers.isEmpty()) return;

        for (Object obj : sequence) {
            ConfigurationSection data = null;

            // Обработка объекта данных
            if (obj instanceof ConfigurationSection cs) {
                data = cs;
            } else if (obj instanceof Map<?, ?> map) {
                data = new MemoryConfiguration();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    data.set(entry.getKey().toString(), entry.getValue());
                }
            }

            if (data == null) continue;

            // Обработка текста и плейсхолдеров
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

            // Тайминги в тиках
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

            // Отправка титров и звуков
            for (Player p : targetPlayers) {
                if (!(Objects.equals(rawTitle, "") && Objects.equals(rawSubtitle, ""))) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.showTitle(titleObj));
                }

                if (data.isConfigurationSection("sound")) {
                    ConfigurationSection soundData = data.getConfigurationSection("sound");
                    if (soundData != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                NamespacedKey key = NamespacedKey.minecraft(soundData.getString("name", "ENTITY_EXPERIENCE_ORB_PICKUP"));
                                Sound sound = Registry.SOUNDS.get(key);
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

            // Пауза в текущем потоке (Thread Blocking)
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

    public static Boolean СreateWorld(String newWorld, Integer deathSpawnX, Integer deathSpawnY, Integer deathSpawnZ, Boolean border, Integer borderDiameter, Integer borderTime) {
        if (Bukkit.getWorld(newWorld) != null) {
            return true;
        }

        try {
            // На этом этапе папка мира уже должна быть скопирована (см. CopyWorldFolder),
            // здесь только подгружаем её через стандартный Bukkit/Paper WorldCreator.
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
                worldBorder.changeSize(1, borderTime);
                worldBorder.setDamageAmount(5.0);
                worldBorder.setDamageBuffer(0);
            }
        } catch (Exception e) {
            getLogger().warning(e.toString());
            if (!Bukkit.getWorld(newWorld).equals(null)) {
                DeleteWorld(newWorld);
            }
            return false;
        }

        return true;
    }

    /**
     * Копирует папку мира на диске (region-файлы, level.dat и т.д.), не трогая главный поток.
     * Должна вызываться ДО {@link #СreateWorld}, асинхронно.
     */
    private static boolean CopyWorldFolder(String sourceWorldName, String targetWorldName) {
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

        // Удаление файлов с диска не требует главного потока — делаем асинхронно
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                DeleteDirectory(worldFolder.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось удалить папку мира " + worldName + ": " + e.getMessage());
            }
        });
    }

    private static void DeleteDirectory(Path path) throws IOException {
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

    public static Boolean InitPlayerSpawns(String worldName, List<UUID> gamePartyList, Double circleDiameter, Integer spawnY, Integer pillarHeight, String pillarBlocks) {
        Integer playerCount = gamePartyList.size();
        if (circleDiameter == 0) {
            circleDiameter = СalculateDiameter(playerCount, 7);
        }
        List<double[]> spawnPoints = GetCirclePoints(circleDiameter, playerCount, 0, 0);
        FileConfiguration config = plugin.getConfig();
        List<String> joinMessage = config.getStringList("join-message");
        for (int i = 0; i < spawnPoints.size(); i++) {
            double[] point = spawnPoints.get(i);
            UUID playerUUID = gamePartyList.get(i);
            World world = Bukkit.getWorld(worldName);
            blockedUUIDs.add(playerUUID);
            int pillarX = (int) Math.floor(point[0]);
            int pillarZ = (int) Math.floor(point[1]);
            float[] angles = GetYawPitch(
                    new Location(
                            world,
                            point[0],
                            spawnY,
                            point[1]
                    ),
                    new Location(
                            world,
                            0,
                            spawnY,
                            0
                    )
            );
            Location spawnLoc = new Location(
                    world,
                    point[0],
                    spawnY+5,
                    point[1],
                    angles[0],
                    angles[1]
            ).toCenterLocation();

            if (Bukkit.getPlayer(playerUUID) == null) {
                playerSpawns.put(playerUUID, spawnLoc);
            } else {
                Player player = Bukkit.getPlayer(playerUUID);
                player.teleport(spawnLoc);
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().clear();
                player.getEnderChest().clear();
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(5.0f);
                gameProgress.get(worldName)[0]++;
                for (Player worldPlayer : world.getPlayers()) {
                    if (worldPlayer != null && worldPlayer.isOnline()) {
                        for (String msg : joinMessage) {
                            worldPlayer.sendMessage(MiniMessage.miniMessage().deserialize(msg.replace("%player%", player.getName())));
                        }
                    }
                }
            }
            CreatePillar(pillarX, spawnY, pillarZ, pillarHeight, pillarBlocks, worldName);
        }
        return true;
    }

    public static double СalculateDiameter(int nPoints, double targetDist) {
        if (nPoints < 2) {
            return 0;
        }
        double angle = Math.PI / nPoints;
        double diameter = targetDist / Math.sin(angle);
        return diameter;
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

    public static void StartGameItemsCycle(String worldName, Boolean debugMode, Map<String, String> settings, ConfigurationSection mode ) {
        String selectedMode = settings.getOrDefault("mode", "items").toString();
        if (mode == null) {
            getLogger().warning("Не удалось найти режим: " + selectedMode);
            return;
        }

        List<Player> alivePlayers = Bukkit.getWorld(worldName).getPlayers().stream()
                .filter(player -> player.getGameMode() == GameMode.SURVIVAL)
                .collect(Collectors.toList());

        while (true) {
            if ( !debugMode ) {
                if ( alivePlayers.size() <= 1 ) break;
            } else {
                if ( alivePlayers.size() < 1 ) break;
            }
            for (int i = 0; i < Integer.parseInt(settings.getOrDefault("itemGiveDelay", "8")); i++) {
                Integer timer = Integer.parseInt(settings.getOrDefault("itemGiveDelay", "8"))-i;
                String timer_format = format(timer, i);

                gameTimerF.put(worldName, timer_format.toString());
                gameTimers.put(worldName, timer.toString());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                alivePlayers = Bukkit.getWorld(worldName).getPlayers().stream()
                        .filter(player -> player.getGameMode() == GameMode.SURVIVAL)
                        .collect(Collectors.toList());
                if ( !debugMode ) {
                    if ( alivePlayers.size() <= 1 ) break;
                } else {
                    if ( alivePlayers.size() < 1 ) break;
                }
            }
            if ( !debugMode ) {
                if ( alivePlayers.size() <= 1 ) break;
            } else {
                if ( alivePlayers.size() < 1 ) break;
            }
            for (Player player : Bukkit.getWorld(worldName).getPlayers()) {
                if (player != null && player.isOnline()) {
                    if (player.getGameMode() == GameMode.SURVIVAL) {
                        if (mode != null && mode.getBoolean("enabled", false)) {
                            List<String> commands = mode.getStringList("commands");
                            if (!commands.isEmpty()) {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    Random random = new Random();
                                    Integer random_int = random.nextInt(101) - 50;
                                    String randomCommand = commands.get(random.nextInt(commands.size()));
                                    randomCommand = randomCommand
                                            .replace("%player%", player.getName())
                                            .replace("%world%", worldName)
                                            .replace("%random-int%", random_int.toString())
                                            .replace("%randomItem%", getRandomBlockMaterial().name());
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), randomCommand);
                                });
                            }
                        }
                        if (mode != null && mode.getBoolean("random-item", false)) {
                            player.getInventory().addItem(new ItemStack(getRandomBlockMaterial(), 1));
                        }
                    }
                }
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
                        (existing, replacement) -> replacement
                ));
    }

    private static void CreatePillar(int x, int y, int z, int height, String baseBlock, String worldName) {
        Material baseMat = Material.getMaterial(baseBlock);
        if (baseMat != null) FillRegion(worldName, x, y, z, x, y - height, z, baseMat);
        SetBlock(worldName, x, y + 4, z, Material.GLASS);
        FillRegion(worldName, x+1, y+5, z, x+1, y+7, z, Material.GLASS);
        FillRegion(worldName, x-1, y+5, z, x-1, y+7, z, Material.GLASS);
        FillRegion(worldName, x, y+5, z+1, x, y+7, z+1, Material.GLASS);
        FillRegion(worldName, x, y+5, z-1, x, y+7, z-1, Material.GLASS);
    }

    private static void SetBlock(String worldName, int x, int y, int z, Material material) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) world.getBlockAt(x, y, z).setType(material);
    }

    private static Material getRandomBlockMaterial() {
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
        String formated = "";
        Integer i = 0;
        while (i < a-1) {
            formated += "&f&l▮";
            i++;
        }
        i = 0;
        while (i < b+1) {
            formated += "&7&l▮";
            i++;
        }
        return formated;
    }

    private static void FillRegion(String worldName, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
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
            e.printStackTrace();
        }

        player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
    }

    public static void updateBoard(FastBoard board, UUID playerUUID) {

        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null || !player.isOnline()) {
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", player.getName());

        String worldName = player.getWorld().getName();

        placeholders.put("%arena%", Objects.toString(gameArena.get(worldName), "Загрузка..."));
        placeholders.put("%mode%", Objects.toString(gameMode.get(worldName), "Загрузка..."));
        placeholders.put("%timer%", Objects.toString(gameTimers.get(worldName), "Загрузка..."));
        placeholders.put("%timerFormated%", Objects.toString(gameTimerF.get(worldName), "Загрузка..."));

        long aliveCount = player.getWorld().getPlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL)
                .count();

        placeholders.put("%playersAlive%", String.valueOf(aliveCount));

        ConfigurationSection fastboard = plugin.getConfig().getConfigurationSection("fastboard");
        if (fastboard == null) return;

        List<String> lines = fastboard.getStringList("lines").stream()
                .map(line -> {
                    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                        line = line.replace(entry.getKey(), entry.getValue());
                    }
                    return ChatColor.translateAlternateColorCodes('&', line);
                })
                .collect(Collectors.toList());

        board.updateLines(lines.toArray(new String[0]));
    }
}