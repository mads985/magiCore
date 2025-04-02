package org.madelineb.magiCore;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class HealthManager implements Listener {

    private final MagiCore plugin;
    private final File healthFile;
    private FileConfiguration healthData;

    public HealthManager(MagiCore plugin) {
        this.plugin = plugin;

        // Create or load health.yml
        healthFile = new File(plugin.getDataFolder(), "health.yml");
        if (!healthFile.exists()) {
            try {
                healthFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create health.yml: " + e.getMessage());
            }
        }
        healthData = YamlConfiguration.loadConfiguration(healthFile);

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Set health for online players (in case of reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            setPlayerMaxHealthAttribute(player);
            loadPlayerHealth(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        // Set max health attribute
        setPlayerMaxHealthAttribute(player);

        // Load health with a small delay to ensure player is fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            loadPlayerHealth(player);
        }, 5L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Save player's health
        savePlayerHealth(event.getPlayer());
        try {
            healthData.save(healthFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save health data: " + e.getMessage());
        }
    }

    public void setPlayerMaxHealthAttribute(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(40.0);
        } else {
            plugin.getLogger().warning("Could not set max health for player " + player.getName());
        }
    }

    public void savePlayerHealth(Player player) {
        UUID uuid = player.getUniqueId();
        double health = player.getHealth();
        healthData.set(uuid.toString(), health);
    }

    public void loadPlayerHealth(Player player) {
        UUID uuid = player.getUniqueId();
        if (healthData.contains(uuid.toString())) {
            double savedHealth = healthData.getDouble(uuid.toString());
            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            double validHealth = Math.min(Math.max(savedHealth, 1.0), maxHealth);
            player.setHealth(validHealth);
        }
    }

    public void saveAllPlayerHealth() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerHealth(player);
        }

        try {
            healthData.save(healthFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save health data: " + e.getMessage());
        }
    }
}