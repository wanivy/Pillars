package dev.tvrz.pillars.listeners;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

import com.alessiodp.parties.api.Parties;
import com.alessiodp.parties.api.interfaces.PartiesAPI;
import com.alessiodp.parties.api.interfaces.Party;

import fr.mrmicky.fastboard.FastBoard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import static dev.tvrz.pillars.commands.PillarsCommand.*;
import static dev.tvrz.pillars.managers.UtilsManager.*;

public class pillarsEventListeners implements Listener {

    private static JavaPlugin plugin = null;
    public static Map<UUID, FastBoard> boards = new HashMap<>();
    private static PartiesAPI partiesAPI;
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    public pillarsEventListeners(JavaPlugin plugin) {
        pillarsEventListeners.plugin = plugin;
        partiesAPI = Parties.getApi();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (!world.getName().startsWith("game_")) {
            return;
        }
        if (blockedUUIDs.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        World deathWorld = player.getWorld();

        if (!deathWorld.getName().startsWith("game_")) {
            return;
        }

        UUID playerUUID = Bukkit.getPlayerUniqueId(player.getName());
        if (playerUUID == null) return;

        Party gameParty = partiesAPI.getPartyOfPlayer(playerUUID);
        if (gameParty == null) return;

        String partyName = gameParty.getName();
        if (partyName == null) return;

        String settingsString = activeGames.get("game_" + partyName);
        Map<String, String> settings = ParseSettings(settingsString);

        int x = Integer.parseInt(settings.getOrDefault("deathSpawnX", "0"));

        int defaultY = Integer.parseInt(settings.getOrDefault("spawnY", "10")) + 15;
        int y = Integer.parseInt(settings.getOrDefault("deathSpawnY", String.valueOf(defaultY)));

        int z = Integer.parseInt(settings.getOrDefault("deathSpawnZ", "0"));

        event.setRespawnLocation(new Location(deathWorld, x, y, z));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.deathMessage(null);
        Player player = event.getEntity();
        World world = player.getWorld();
        FileConfiguration config = plugin.getConfig();
        if (world.getName().startsWith("game_") && player.getGameMode().equals(GameMode.SURVIVAL)) {
            player.setGameMode(GameMode.SPECTATOR);
            List<String> loseMessage = config.getStringList("lose-message");

            UUID playerUUID = Bukkit.getPlayerUniqueId(player.getName());
            if (playerUUID == null) return;

            Party gameParty = partiesAPI.getPartyOfPlayer(playerUUID);
            assert gameParty != null;
            String settingsString = activeGames.get("game_" + gameParty.getName());
            Map<String, String> settings = ParseSettings(settingsString);
            String selectedMode = settings.getOrDefault("mode", "items");
            ConfigurationSection mode = GetModeSection(selectedMode);
            assert mode != null;
            ConfigurationSection deathTitles = mode.getConfigurationSection("death-titles");
            PlayTitles(world.getName(), deathTitles, null, player, "sequence");
            for (UUID playerId : gameParty.getMembers()) {
                Player p = Bukkit.getPlayer(playerId);
                if (p != null && p.isOnline()) {
                    for (String msg : loseMessage) {
                        p.sendMessage(miniMessage.deserialize(msg.replace("%player%", player.getName())));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!world.getName().startsWith("game_")) {
            return;
        }

        if (player.getLocation().getY() < -64) {
            player.setHealth(0.0);
        }

        if (!blockedUUIDs.contains(event.getPlayer().getUniqueId())) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getX() != to.getX() || from.getZ() != to.getZ()) {

            Location fixedLocation = from.clone();
            fixedLocation.setY(to.getY());
            fixedLocation.setYaw(to.getYaw());
            fixedLocation.setPitch(to.getPitch());

            event.setTo(fixedLocation);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();

        FastBoard board = new FastBoard(player);
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection scoreboard = config.getConfigurationSection("fastboard");
        if (scoreboard != null && scoreboard.getBoolean("enabled")) {
            Object title = scoreboard.get("title");
            if (title != null) {
                Component titleComponent = miniMessage.deserialize(title.toString());
                board.updateTitle(LegacyComponentSerializer.legacySection().serialize(titleComponent));
                boards.put(player.getUniqueId(), board);
            }
        }

        Location spawnLoc = playerSpawns.remove(player.getUniqueId());
        if (spawnLoc != null) {
            player.teleport(spawnLoc);
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
            Map<String, String> spawnSettings = playerSpawnSettings.remove(player.getUniqueId());
            if (spawnSettings != null && player.getWorld().getName().startsWith("game_")) {
                CreatePillar(Integer.parseInt(spawnSettings.get("X")), Integer.parseInt(spawnSettings.get("Y")), Integer.parseInt(spawnSettings.get("Z")), Integer.parseInt(spawnSettings.get("Height")), spawnSettings.get("Block"), spawnSettings.get("World"));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();

        FastBoard board = boards.remove(player.getUniqueId());
        if (board != null) {
            board.delete();
        }
    }
}