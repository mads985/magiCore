package org.madelineb.magiCore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatTagManager implements Listener {

    private final MagiCore plugin;
    private final Map<UUID, Long> combatTaggedPlayers = new HashMap<>();
    private final Map<UUID, Boolean> combatLoggers = new HashMap<>();
    private final int COMBAT_TAG_DURATION = 15; // Duration in seconds
    private final File combatLogFile;
    private final FileConfiguration combatLogData;

    public CombatTagManager(MagiCore plugin) {
        this.plugin = plugin;

        // Create or load combatloggers.yml
        combatLogFile = new File(plugin.getDataFolder(), "combatloggers.yml");
        if (!combatLogFile.exists()) {
            try {
                combatLogFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create combatloggers.yml: " + e.getMessage());
            }
        }
        combatLogData = YamlConfiguration.loadConfiguration(combatLogFile);

        // Load combat loggers from file
        ConfigurationSection loggersSection = combatLogData.getConfigurationSection("loggers");
        if (loggersSection != null) {
            for (String uuidString : loggersSection.getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                combatLoggers.put(uuid, true);
            }
        }

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Start the combat tag check task that runs every second
        startCombatTagChecker();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        // Check if player is marked as a combat logger
        if (combatLoggers.containsKey(player.getUniqueId())) {
            // Apply effects and schedule death
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Apply nausea and blindness
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 30, 10, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 10, false, false));

                // Inform player
                player.sendMessage(ChatColor.RED + "You logged out during combat! Prepare to face the consequences...");

                // Kill player after effects
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.setHealth(0);
                    player.sendMessage(ChatColor.RED + "You have been punished for combat logging.");

                    // Remove from combat loggers list
                    combatLoggers.remove(player.getUniqueId());
                }, 20L); // Kill after 1 second
            }, 5L); // Delay initial effects by 5 ticks to ensure player is fully connected
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Check if the player is combat tagged
        if (isPlayerCombatTagged(player.getUniqueId())) {
            // Mark player as a combat logger instead of killing them
            combatLoggers.put(player.getUniqueId(), true);
            plugin.getLogger().info(player.getName() + " logged out while combat tagged and was marked for punishment.");
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Remove combat tag and combat logger status when player dies
        combatTaggedPlayers.remove(player.getUniqueId());
        combatLoggers.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        // Check if both entities are players
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player damaged = (Player) event.getEntity();
            Player damager = (Player) event.getDamager();

            // Apply combat tag to both players
            applyCombatTag(damaged);
            applyCombatTag(damager);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Check if the player is combat tagged
        if (isPlayerCombatTagged(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot use commands while in combat!");
        }
    }

    public void applyCombatTag(Player player) {
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        boolean wasTagged = isPlayerCombatTagged(playerUUID);

        // Apply or refresh the combat tag
        combatTaggedPlayers.put(playerUUID, currentTime);

        // Only send message if player wasn't already tagged
        if (!wasTagged) {
            player.sendMessage(ChatColor.RED + "You are now in combat! Don't log out for " + COMBAT_TAG_DURATION + " seconds!");
        }

        // If player is sitting in a chair, kick them out
        if (player.isInsideVehicle() && player.getVehicle() instanceof org.bukkit.entity.ArmorStand) {
            player.getVehicle().eject();
        }
    }

    public boolean isPlayerCombatTagged(UUID playerUUID) {
        if (!combatTaggedPlayers.containsKey(playerUUID)) {
            return false;
        }

        long taggedTime = combatTaggedPlayers.get(playerUUID);
        long currentTime = System.currentTimeMillis();
        long elapsedSeconds = (currentTime - taggedTime) / 1000;

        return elapsedSeconds < COMBAT_TAG_DURATION;
    }

    private void startCombatTagChecker() {
        // Run a task every second to check for expired combat tags
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long currentTime = System.currentTimeMillis();

            // Create a copy of the keys to avoid concurrent modification
            for (UUID playerUUID : new HashMap<>(combatTaggedPlayers).keySet()) {
                long taggedTime = combatTaggedPlayers.get(playerUUID);
                long elapsedSeconds = (currentTime - taggedTime) / 1000;

                // If the tag has expired
                if (elapsedSeconds >= COMBAT_TAG_DURATION) {
                    // Remove the tag
                    combatTaggedPlayers.remove(playerUUID);

                    // Send message to player if they're online
                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player != null && player.isOnline()) {
                        // Execute message on the main thread
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(ChatColor.GREEN + "You are no longer in combat!");
                        });
                    }
                }
            }
        }, 20L, 20L); // 20 ticks = 1 second
    }

    public void saveAllCombatLoggers() {
        // Clear existing data
        combatLogData.set("loggers", null);

        // Save combat loggers
        for (UUID uuid : combatLoggers.keySet()) {
            combatLogData.set("loggers." + uuid.toString(), true);
        }

        try {
            combatLogData.save(combatLogFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save combat loggers data: " + e.getMessage());
        }
    }
}