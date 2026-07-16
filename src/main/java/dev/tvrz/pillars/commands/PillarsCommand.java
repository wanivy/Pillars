package dev.tvrz.pillars.commands;

import dev.tvrz.pillars.managers.UtilsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.alessiodp.parties.api.Parties;
import com.alessiodp.parties.api.interfaces.PartiesAPI;
import com.alessiodp.parties.api.interfaces.Party;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.checkerframework.checker.nullness.qual.NonNull;

import static dev.tvrz.pillars.managers.UtilsManager.*;

public class PillarsCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static JavaPlugin plugin = null;
    private static PartiesAPI partiesAPI;

    public static Set<UUID> blockedUUIDs = ConcurrentHashMap.newKeySet();
    public static Map<String, String> gameArena = new HashMap<>();
    public static Map<String, String> gameMode = new HashMap<>();
    public static Map<String, String> gameTimers = new HashMap<>();
    public static Map<String, String> gameTimerF = new HashMap<>();
    public static Map<String, String> activeGames = new HashMap<>();
    public static Map<String, int[]> gameProgress = new HashMap<>();

    public static Map<UUID, Location> playerSpawns = new HashMap<>();
    public static Map<UUID, Map<String, String>> playerSpawnSettings = new HashMap<>();

    private static final Map<String, List<String>> SETTINGS = new HashMap<>();

    public PillarsCommand(JavaPlugin plugin) {
        PillarsCommand.plugin = plugin;
        partiesAPI = Parties.getApi();
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
    public List<String> onTabComplete(@org.jspecify.annotations.NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String @NonNull [] args) {

        if (args.length <= 1) {
            return null;
        }

        Set<String> usedKeys = new HashSet<>();

        for (String arg : args) {
            if (arg.contains("=")) {
                usedKeys.add(arg.split("=")[0].toLowerCase());
            }
        }

        String currentArg = args[args.length - 1];

        if (currentArg.contains("=")) {
            String[] split = currentArg.split("=", 2);
            String key = split[0];
            String valuePart = split.length > 1 ? split[1].toLowerCase() : "";

            if (!SETTINGS.containsKey(key)) {
                return Collections.emptyList();
            }

            List<String> possibleValues = SETTINGS.get(key);

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
    public boolean onCommand(@org.jspecify.annotations.NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(MM.deserialize("<red>Используйте: /pillars <ID игры> [параметры]>"));
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String settingsString = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            Map<String, String> settings = ParseSettings(settingsString);
            String partyName = args[0];
            String worldName = "game_" + partyName.toLowerCase();

            File dataFolder = new File(plugin.getDataFolder(), "modes");
            if (!dataFolder.exists()) {
                if (!dataFolder.mkdirs()) {
                    plugin.getLogger().warning("Failed to create modes folder");
                }
            }

            File file = new File(dataFolder, settings.getOrDefault("mode", "items")+".yml");
            if (!file.exists()) {
                sender.sendMessage(MM.deserialize("<red>Ошибка: Не удалось загрузить режим " + settings.getOrDefault("mode", "items")));
                return;
            }
            if (!CopyWorldFolder(settings.getOrDefault("world", "world"), worldName)) {
                sender.sendMessage(MM.deserialize("<red>Ошибка: не удалось скопировать мир " + settings.getOrDefault("world", "world")));
                return;
            }

            gameArena.put(worldName, settings.getOrDefault("world", "world"));
            gameMode.put(worldName, settings.getOrDefault("mode", "items"));

            CompletableFuture<Void> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Boolean successful = CreateWorld(
                        worldName,
                        Integer.parseInt(settings.getOrDefault("deathSpawnX", "0")),
                        Integer.parseInt(settings.getOrDefault("deathSpawnY", Integer.toString(Integer.parseInt(settings.getOrDefault("spawnY", "10")) + 15))),
                        Integer.parseInt(settings.getOrDefault("deathSpawnZ", "0")),
                        settings.getOrDefault("border", "true").equals("true"),
                        Integer.parseInt(settings.getOrDefault("borderDiameter", "75")),
                        Integer.parseInt(settings.getOrDefault("borderTime", "600"))
                );
                if (!successful) {
                    sender.sendMessage(MM.deserialize("<red>Ошибка: не удалось загрузить скопированный мир " + worldName));
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
                sender.sendMessage(MM.deserialize("<red>Ошибка: не удалось получить пати " + partyName));
                return;
            }
            plugin.getLogger().info(party.getMembers().toString());
            final List<UUID> gamePartyList = new ArrayList<>(party.getMembers());
            gameProgress.put(worldName, new int[]{0, gamePartyList.size()});
            Bukkit.getScheduler().runTask(plugin, () -> InitPlayerSpawns(
                    worldName,
                    gamePartyList,
                    (double) Integer.parseInt(settings.getOrDefault("circleDiameter", "0")),
                    Integer.parseInt(settings.getOrDefault("spawnY", "10")),
                    Integer.parseInt(settings.getOrDefault("pillarHeight", "10")),
                    settings.getOrDefault("pillarsBlocks", "bedrock").toUpperCase()
            ));
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
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                List<UUID> playerList = world.getPlayers().stream()
                        .map(Player::getUniqueId)
                        .toList();
                assert party != null;
                if (new HashSet<>(playerList).containsAll(party.getMembers())) {
                    break;
                }
            }
        }

        ConfigurationSection mode = GetModeSection(settings.getOrDefault("mode", "items"));
        if (mode == null) {
            plugin.getLogger().warning("Не удалось загрузить режим: " + settings.getOrDefault("mode", "items"));
            Bukkit.getScheduler().runTask(plugin, () -> DeleteWorld(worldName));
            return;
        }

        ConfigurationSection startTitles = mode.getConfigurationSection("start-titles");
        PlayTitles(worldName, startTitles, null, null, "sequence");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> PlayTitles(worldName, startTitles, null, null, "after"));

        if (party != null) {
            for (UUID playerUUID : party.getMembers()) {
                Player player = Bukkit.getPlayer(playerUUID);
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.getInventory().clear();
            player.getEnderChest().clear();
            var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                double maxHealth = maxHealthAttr.getDefaultValue();
                if (maxHealth > 0) {
                    player.setHealth(maxHealth);
                }
            }
            player.setFoodLevel(20);
                player.setSaturation(5.0f);
                player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
                if (!mode.getBoolean("block-move.enabled", false)) {
                    blockedUUIDs.remove(playerUUID);
                } else {
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> blockedUUIDs.remove(playerUUID), mode.getInt("block-move.duration") * 20L);
                }
                Bukkit.getScheduler().runTask(plugin, () -> FillRegion(worldName, (int) (player.getX()-2), Integer.parseInt(settings.getOrDefault("spawnY", "10")) + 3, (int) (player.getZ()-2), (int) (player.getX()+2), Integer.parseInt(settings.getOrDefault("spawnY", "10")) + 7, (int) (player.getZ()+2), Material.AIR));
            }
        }

        if (mode.getBoolean("potion-effects.enabled", false)) {
            ConfigurationSection potionSection = mode.getConfigurationSection("potion-effects");
            if (potionSection != null) {
                GivePotionEffects(worldName, potionSection, null);
            }
        }

        StartGameItemsCycle(worldName, settings.getOrDefault("debugMode", "false").equals("true"), settings, mode);

        Map<String, String> placeholders = new HashMap<>();
        World gameWorld = Bukkit.getWorld(worldName);
        if (gameWorld != null) {
            List<Player> alivePlayers = gameWorld.getPlayers().stream()
                    .filter(player -> player.getGameMode() == GameMode.SURVIVAL)
                    .toList();
            if (!alivePlayers.isEmpty()) {
                ConfigurationSection endTitles = mode.getConfigurationSection("end-titles.win");
                placeholders.put("player", alivePlayers.getFirst().getName());
                PlayTitles(worldName, endTitles, placeholders, null, "sequence");
            } else {
                ConfigurationSection endTitles = mode.getConfigurationSection("end-titles.draw");
                PlayTitles(worldName, endTitles, null, null, "sequence");
            }

            for (Player player : gameWorld.getPlayers()) {
                player.getInventory().clear();
                player.getEnderChest().clear();
            }
        }

        ConfigurationSection velocity = config.getConfigurationSection("velocity");
        if (velocity != null && velocity.getBoolean("enabled", false) && velocity.getBoolean("connect-to-lobby-on-game-end", false)) {
            World playerWorld = Bukkit.getWorld(worldName);
            if (playerWorld != null) {
                for (Player player : playerWorld.getPlayers()) {
                    if (player != null) {
                        Object velocityLobby = velocity.get("velocity-lobby");
                        if (velocityLobby != null) {
                            sendPlayerToServer(player, velocityLobby.toString());
                        }
                    }
                }
            }
        }
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Bukkit.getScheduler().runTask(plugin, () -> DeleteWorld(worldName));
        gameMode.remove(worldName);
        gameArena.remove(worldName);
        gameTimers.remove(worldName);
        gameTimerF.remove(worldName);
        activeGames.remove(worldName);
    }

    public static void InitPlayerSpawns(String worldName, List<UUID> gamePartyList, Double circleDiameter, Integer spawnY, Integer pillarHeight, String pillarBlocks) {
        int playerCount = gamePartyList.size();
        if (circleDiameter == 0) {
            circleDiameter = CalculateDiameter(playerCount, 7);
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
                Map<String, String> spawnSettings = new HashMap<>();
                spawnSettings.put("X", Integer.toString(pillarX));
                spawnSettings.put("Y", Integer.toString(spawnY));
                spawnSettings.put("Z", Integer.toString(pillarZ));
                spawnSettings.put("Height", Integer.toString(pillarHeight));
                spawnSettings.put("Block", pillarBlocks);
                spawnSettings.put("World", worldName);
                playerSpawnSettings.put(playerUUID, spawnSettings);
             } else {
                Player player = Bukkit.getPlayer(playerUUID);
                assert player != null;
                player.teleport(spawnLoc);
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().clear();
                player.getEnderChest().clear();
                var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealthAttr != null) {
                    double maxHealth = maxHealthAttr.getDefaultValue();
                    if (maxHealth > 0) {
                        player.setHealth(maxHealth);
                    }
                }
                player.setFoodLevel(20);
                player.setSaturation(5.0f);
                gameProgress.get(worldName)[0]++;
                assert world != null;
                for (Player worldPlayer : world.getPlayers()) {
                    if (worldPlayer != null && worldPlayer.isOnline()) {
                        for (String msg : joinMessage) {
                            worldPlayer.sendMessage(MM.deserialize(msg,
                                    Placeholder.parsed("player", player.getName()),
                                    Placeholder.parsed("joined_players", Integer.toString(gameProgress.get(worldName)[0])),
                                    Placeholder.parsed("required_players", Integer.toString(gameProgress.get(worldName)[1]))
                            ));
                        }
                    }
                }
                CreatePillar(pillarX, spawnY, pillarZ, pillarHeight, pillarBlocks, worldName);
            }
        }
    }

    @SuppressWarnings("BusyWait")
    public static void StartGameItemsCycle(String worldName, Boolean debugMode, Map<String, String> settings, ConfigurationSection mode ) {
        String selectedMode = settings.getOrDefault("mode", "items");
        if (mode == null) {
            plugin.getLogger().warning("Не удалось найти режим: " + selectedMode);
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        List<Player> alivePlayers = world.getPlayers().stream()
                .filter(player -> player.getGameMode() == GameMode.SURVIVAL)
                .collect(Collectors.toList());

        while (true) {
            if ( !debugMode ) {
                if ( alivePlayers.size() <= 1 ) break;
            } else {
                if (alivePlayers.isEmpty()) break;
            }
            for (int i = 0; i < Integer.parseInt(settings.getOrDefault("itemGiveDelay", "8")); i++) {
                Integer timer = Integer.parseInt(settings.getOrDefault("itemGiveDelay", "8"))-i;
                String timer_format = format(timer, i);

                gameTimerF.put(worldName, timer_format);
                gameTimers.put(worldName, timer.toString());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                World currentWorld = Bukkit.getWorld(worldName);
                if (currentWorld != null) {
                    alivePlayers = currentWorld.getPlayers().stream()
                            .filter(player -> player.getGameMode() == GameMode.SURVIVAL)
                            .toList();
                }
                if ( !debugMode ) {
                    if ( alivePlayers.size() <= 1 ) break;
                } else {
                    if ( alivePlayers.isEmpty() ) break;
                }
            }
            if ( !debugMode ) {
                if ( alivePlayers.size() <= 1 ) break;
            } else {
                if ( alivePlayers.isEmpty() ) break;
            }
            World currentWorld = Bukkit.getWorld(worldName);
            if (currentWorld != null) {
                for (Player player : currentWorld.getPlayers()) {
                    if (player != null && player.isOnline()) {
                        if (player.getGameMode() == GameMode.SURVIVAL) {
                            if (mode.getBoolean("enabled", false)) {
                                List<String> commands = mode.getStringList("commands");
                                if (!commands.isEmpty()) {
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        Random random = new Random();
                                        int random_int = random.nextInt(101) - 50;

                                        String rawCommand = commands.get(random.nextInt(commands.size()));

                                        Component parsedComponent = MM.deserialize(rawCommand,
                                                Placeholder.unparsed("player", player.getName()),
                                                Placeholder.unparsed("random-int", Integer.toString(random_int)),
                                                Placeholder.unparsed("random-item", UtilsManager.getRandomBlockMaterial().name()),
                                                Placeholder.unparsed("world", worldName)
                                        );

                                        String randomCommand = PlainTextComponentSerializer.plainText().serialize(parsedComponent);

                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), randomCommand);
                                    });
                                }
                            }
                            if (mode.getBoolean("random-item", false)) {
                                player.getInventory().addItem(new ItemStack(UtilsManager.getRandomBlockMaterial(), 1));
                            }
                        }
                    }
                }
            }
        }
    }
}