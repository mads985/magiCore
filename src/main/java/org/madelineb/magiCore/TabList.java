package org.madelineb.magiCore;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class TabList implements Listener {
    private final Map<String, String> roleColors = new HashMap<>();
    private final JavaPlugin plugin;

    public TabList(JavaPlugin plugin) {
        this.plugin = plugin;
        // Initialize colors
        roleColors.put("*", "#cbb0ff");
        roleColors.put("magite", "#66cbd3");
        roleColors.put("default", "#ffffff");

        // Register chat listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // Main update method
    public void updateAllPlayers() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            String role = getPlayerRole(player);
            updatePlayerTab(player, role);
        });
    }

    public void updatePlayerTab(Player player, String role) {
        String hexColor = roleColors.getOrDefault(role, roleColors.get("default"));
        ChatColor color = ChatColor.of(hexColor);
        player.setPlayerListName(color + player.getName());
    }

    // New method for chat formatting
    private String getFormattedName(Player player) {
        String role = getPlayerRole(player);
        String hexColor = roleColors.getOrDefault(role, roleColors.get("default"));
        return ChatColor.of(hexColor) + player.getName();
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String formattedName = getFormattedName(player);

        // Format: <color>PlayerName&7: &fMessage
        event.setFormat(formattedName + ChatColor.GRAY + ": " + ChatColor.WHITE + "%2$s");
    }

    private String getPlayerRole(Player player) {
        if (player.hasPermission("magicore.role.magite")) return "magite";
        if (player.hasPermission("magicore.role.star")) return "*";
        return "default";
    }
}