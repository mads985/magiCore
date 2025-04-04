package org.madelineb.magiCore;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class TabList {
    private final Map<String, String> roleColors = new HashMap<>();

    public TabList(JavaPlugin plugin) {
        // Initialize colors
        roleColors.put("*", "#cbb0ff");
        roleColors.put("magite", "#66cbd3");
        roleColors.put("default", "#aaaaaa");
    }

    // Main update method (previously called startUpdates)
    public void updateAllPlayers() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            String role = getPlayerRole(player); // Implement your role detection
            updatePlayerTab(player, role);
        });
    }

    public void updatePlayerTab(Player player, String role) {
        String hexColor = roleColors.getOrDefault(role, roleColors.get("default"));
        ChatColor color = ChatColor.of(hexColor);
        player.setPlayerListName(color + player.getName());
    }

    private String getPlayerRole(Player player) {
        // Implement your role detection logic here
        if (player.hasPermission("magicore.role.magite")) return "magite";
        if (player.hasPermission("magicore.role.star")) return "*";
        return "default";
    }
}