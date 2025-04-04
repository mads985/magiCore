package org.madelineb.magiCore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class EssentialsY implements CommandExecutor, Listener {
    private final JavaPlugin plugin;
    private final FileConfiguration starterConfig;
    private final File starterFile;
    private final Map<UUID, UUID> lastMessengers = new HashMap<>();
    private final Set<UUID> godmodePlayers = new HashSet<>();

    public EssentialsY(JavaPlugin plugin) {
        this.plugin = plugin;
        this.starterFile = new File(plugin.getDataFolder(), "starter.yml");
        this.starterConfig = YamlConfiguration.loadConfiguration(starterFile);

        if (!starterFile.exists()) {
            plugin.saveResource("starter.yml", false);
        }

        // Register commands
        Objects.requireNonNull(plugin.getCommand("msg")).setExecutor(this);
        Objects.requireNonNull(plugin.getCommand("r")).setExecutor(this);
        Objects.requireNonNull(plugin.getCommand("flyspeed")).setExecutor(this);
        Objects.requireNonNull(plugin.getCommand("god")).setExecutor(this);
        Objects.requireNonNull(plugin.getCommand("starterremove")).setExecutor(this);

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "msg":
                return handleMsg(sender, args);
            case "r":
                return handleReply(sender, args);
            case "flyspeed":
                return handleFlySpeed(sender, args);
            case "god":
                return handleGod(sender);
            case "starterremove":
                return handleStarterRemove(sender, args);
        }
        return false;
    }

    private boolean handleMsg(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /msg <player> <message>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found!");
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        sender.sendMessage(ChatColor.GRAY + "[me -> " + target.getName() + "] " + message);
        target.sendMessage(ChatColor.GRAY + "[" + sender.getName() + " -> me] " + message);
        lastMessengers.put(target.getUniqueId(), ((Player) sender).getUniqueId());
        return true;
    }

    private boolean handleReply(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /r <message>");
            return true;
        }

        UUID lastSender = lastMessengers.get(((Player) sender).getUniqueId());
        if (lastSender == null) {
            sender.sendMessage(ChatColor.RED + "No one to reply to!");
            return true;
        }

        Player target = Bukkit.getPlayer(lastSender);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player is offline!");
            return true;
        }

        String message = String.join(" ", args);
        sender.sendMessage(ChatColor.GRAY + "[me -> " + target.getName() + "] " + message);
        target.sendMessage(ChatColor.GRAY + "[" + sender.getName() + " -> me] " + message);
        lastMessengers.put(target.getUniqueId(), ((Player) sender).getUniqueId());
        return true;
    }

    private boolean handleFlySpeed(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }
        if (!sender.hasPermission("essentials.flyspeed")) {
            sender.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /flyspeed <1-100>");
            return true;
        }

        try {
            float speed = Float.parseFloat(args[0]) / 100f;
            speed = Math.max(0.1f, Math.min(1.0f, speed));
            ((Player) sender).setFlySpeed(speed);
            sender.sendMessage(ChatColor.GREEN + "Fly speed set to " + (int)(speed * 100) + "%");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number!");
        }
        return true;
    }

    private boolean handleGod(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }
        if (!sender.hasPermission("essentials.god")) {
            sender.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }

        Player player = (Player) sender;
        boolean godmode = !godmodePlayers.contains(player.getUniqueId());

        if (godmode) {
            godmodePlayers.add(player.getUniqueId());
            player.setInvulnerable(true);
            player.sendMessage(ChatColor.GREEN + "Godmode enabled");
        } else {
            godmodePlayers.remove(player.getUniqueId());
            player.setInvulnerable(false);
            player.sendMessage(ChatColor.RED + "Godmode disabled");
        }
        return true;
    }

    private boolean handleStarterRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("essentials.starter.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /starterremove <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found!");
            return true;
        }

        List<String> received = new ArrayList<>(starterConfig.getStringList("received"));
        received.remove(target.getUniqueId().toString());
        starterConfig.set("received", received);
        try {
            starterConfig.save(starterFile);
            sender.sendMessage(ChatColor.GREEN + "Reset starter status for " + target.getName());
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Failed to save config!");
            e.printStackTrace();
        }
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<String> received = starterConfig.getStringList("received");
        String uuid = player.getUniqueId().toString();

        if (!received.contains(uuid)) {
            // Give starter items
            player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 8));
            player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));

            // Apply Regen 5 for 5 seconds
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION,
                    5 * 20, // 5 seconds
                    4, // Level 5 (0-based)
                    true, // Ambient
                    true // Particles
            ));

            // Add to received list
            received.add(uuid);
            starterConfig.set("received", received);
            try {
                starterConfig.save(starterFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (godmodePlayers.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
}