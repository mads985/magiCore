package org.madelineb.magiCore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathCoordinatesListener implements Listener {

    private final MagiCore plugin;

    public DeathCoordinatesListener(MagiCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLocation = player.getLocation();
        World.Environment dimension = player.getWorld().getEnvironment();

        String dimensionName;
        switch (dimension) {
            case NORMAL:
                dimensionName = "Overworld";
                break;
            case NETHER:
                dimensionName = "Nether";
                break;
            case THE_END:
                dimensionName = "The End";
                break;
            default:
                dimensionName = "Unknown";
                break;
        }

        int x = deathLocation.getBlockX();
        int y = deathLocation.getBlockY();
        int z = deathLocation.getBlockZ();

        String message = ChatColor.RED + "You died in the " + ChatColor.WHITE + dimensionName +
                ChatColor.RED + " at coordinates: " + ChatColor.WHITE +
                "X: " + x + ", Y: " + y + ", Z: " + z;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage(message);
        }, 1L);
    }
}