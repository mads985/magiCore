package org.madelineb.magiCore;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.md_5.bungee.api.chat.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Waystones implements CommandExecutor, Listener {

    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final File waystoneFile;
    private FileConfiguration waystoneConfig;
    private final int SOUL_COST = 2;
    private final Map<UUID, Location> activeWaystoneSessions = new HashMap<>();

    public Waystones(JavaPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.waystoneFile = new File(plugin.getDataFolder(), "waystones.yml");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        loadWaystones();
    }

    private void loadWaystones() {
        if (!waystoneFile.exists()) {
            try {
                waystoneFile.getParentFile().mkdirs();
                waystoneFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create waystones.yml!");
            }
        }

        waystoneConfig = YamlConfiguration.loadConfiguration(waystoneFile);

        if (waystoneConfig.contains("waystones")) {
            for (String waystoneName : waystoneConfig.getConfigurationSection("waystones").getKeys(false)) {
                String worldName = waystoneConfig.getString("waystones." + waystoneName + ".world");
                World world = Bukkit.getWorld(worldName);

                if (world == null) {
                    plugin.getLogger().warning("World " + worldName + " not found for waystone " + waystoneName);
                    continue;
                }

                int x = waystoneConfig.getInt("waystones." + waystoneName + ".x");
                int y = waystoneConfig.getInt("waystones." + waystoneName + ".y");
                int z = waystoneConfig.getInt("waystones." + waystoneName + ".z");

                Location loc = new Location(world, x, y, z);
                Block block = loc.getBlock();

                if (block.getType() != Material.LODESTONE) {
                    plugin.getLogger().warning("Waystone " + waystoneName + " at " + loc + " is not a valid waystone structure!");
                }
            }
        }
    }

    private void saveWaystones() {
        try {
            waystoneConfig.save(waystoneFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save waystones.yml!");
        }
    }

    private boolean createWaystone(Player player, String name) {
        if (!player.hasPermission("waystone.create")) {
            player.sendMessage(ChatColor.RED + "You lack permission to create waystones!");
            return false;
        }

        if (waystoneConfig.contains("waystones." + name)) {
            player.sendMessage(ChatColor.RED + "A waystone named '" + name + "' already exists!");
            return false;
        }

        Location loc = player.getLocation();
        World world = loc.getWorld();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();

        for (int i = 0; i < 3; i++) {
            if (!world.getBlockAt(x, y + i, z).getType().isAir()) {
                player.sendMessage(ChatColor.RED + "Not enough space to build the waystone!");
                return false;
            }
        }

        world.getBlockAt(x, y, z).setType(Material.BEDROCK);
        world.getBlockAt(x, y + 1, z).setType(Material.LODESTONE);
        world.getBlockAt(x, y + 2, z).setType(Material.BEACON);

        waystoneConfig.set("waystones." + name + ".world", world.getName());
        waystoneConfig.set("waystones." + name + ".x", x);
        waystoneConfig.set("waystones." + name + ".y", y + 1);
        waystoneConfig.set("waystones." + name + ".z", z);
        saveWaystones();

        player.sendMessage(ChatColor.GREEN + "Waystone '" + name + "' created!");
        return true;
    }

    private boolean removeWaystone(CommandSender sender, String name) {
        if (!sender.hasPermission("waystone.remove")) {
            sender.sendMessage(ChatColor.RED + "You lack permission to remove waystones!");
            return false;
        }

        if (!waystoneConfig.contains("waystones." + name)) {
            sender.sendMessage(ChatColor.RED + "Waystone '" + name + "' doesn't exist!");
            return false;
        }

        World world = Bukkit.getWorld(waystoneConfig.getString("waystones." + name + ".world"));
        if (world != null) {
            int x = waystoneConfig.getInt("waystones." + name + ".x");
            int y = waystoneConfig.getInt("waystones." + name + ".y");
            int z = waystoneConfig.getInt("waystones." + name + ".z");

            world.getBlockAt(x, y + 1, z).setType(Material.AIR); // Beacon
            world.getBlockAt(x, y, z).setType(Material.AIR);     // Lodestone

            if (sender instanceof Player && ((Player)sender).getGameMode() == GameMode.CREATIVE) {
                world.getBlockAt(x, y - 1, z).setType(Material.AIR); // Bedrock
            }
        }

        waystoneConfig.set("waystones." + name, null);
        saveWaystones();
        sender.sendMessage(ChatColor.GREEN + "Waystone '" + name + "' removed!");
        return true;
    }

    private boolean teleportToWaystone(Player player, String waystoneName) {
        Location sourceWaystone = activeWaystoneSessions.get(player.getUniqueId());
        if (sourceWaystone == null) {
            player.sendMessage(ChatColor.RED + "You must initiate teleportation from a waystone!");
            return false;
        }

        if (!waystoneConfig.contains("waystones." + waystoneName)) {
            player.sendMessage(ChatColor.RED + "That waystone doesn't exist!");
            return false;
        }

        // Check if trying to teleport to current waystone
        if (waystoneConfig.getInt("waystones." + waystoneName + ".x") == sourceWaystone.getBlockX() &&
                waystoneConfig.getInt("waystones." + waystoneName + ".y") == sourceWaystone.getBlockY() &&
                waystoneConfig.getInt("waystones." + waystoneName + ".z") == sourceWaystone.getBlockZ()) {
            player.sendMessage(ChatColor.RED + "You're already at this waystone!");
            return false;
        }

        // Check if player has enough souls
        int totalSouls = economyManager.getTotalSouls(player);
        if (totalSouls < SOUL_COST) {
            player.sendMessage(ChatColor.RED + "You don't have enough souls! You need " + SOUL_COST + " souls to teleport.");
            return false;
        }

        // Remove souls
        int remainingSouls = SOUL_COST;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && economyManager.isValidSoul(item)) {
                if (item.getAmount() >= remainingSouls) {
                    item.setAmount(item.getAmount() - remainingSouls);
                    remainingSouls = 0;
                    break;
                } else {
                    remainingSouls -= item.getAmount();
                    item.setAmount(0);
                }
            }
            if (remainingSouls == 0) break;
        }

        if (remainingSouls > 0) {
            for (ItemStack item : player.getEnderChest().getContents()) {
                if (item != null && economyManager.isValidSoul(item)) {
                    if (item.getAmount() >= remainingSouls) {
                        item.setAmount(item.getAmount() - remainingSouls);
                        remainingSouls = 0;
                        break;
                    } else {
                        remainingSouls -= item.getAmount();
                        item.setAmount(0);
                    }
                }
                if (remainingSouls == 0) break;
            }
        }

        if (remainingSouls > 0) {
            player.sendMessage(ChatColor.RED + "You need " + SOUL_COST + " souls to teleport!");
            return false;
        }

        // Get destination waystone location
        String worldName = waystoneConfig.getString("waystones." + waystoneName + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Error: World not found!");
            return false;
        }

        int x = waystoneConfig.getInt("waystones." + waystoneName + ".x");
        int y = waystoneConfig.getInt("waystones." + waystoneName + ".y");
        int z = waystoneConfig.getInt("waystones." + waystoneName + ".z");

        Location teleportLoc = findSafeLocation(world, x, y, z, 5);
        if (teleportLoc == null) {
            player.sendMessage(ChatColor.RED + "Could not find a safe location to teleport to!");
            return false;
        }

        player.teleport(teleportLoc);
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 5 * 20, 0));
        player.sendMessage(ChatColor.GREEN + "You have been teleported to waystone '" + waystoneName + "'.");

        // Clear the session after successful teleport
        activeWaystoneSessions.remove(player.getUniqueId());
        return true;
    }

    private Location findSafeLocation(World world, int x, int y, int z, int radius) {
        List<Location> safeLocations = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            int randX = x + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int randZ = z + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int highestY = world.getHighestBlockYAt(randX, randZ);

            Block ground = world.getBlockAt(randX, highestY, randZ);
            Block feet = world.getBlockAt(randX, highestY + 1, randZ);
            Block head = world.getBlockAt(randX, highestY + 2, randZ);

            if (ground.getType().isSolid() && feet.getType().isAir() && head.getType().isAir()) {
                safeLocations.add(new Location(world, randX + 0.5, highestY + 1, randZ + 0.5));
            }
        }

        if (safeLocations.isEmpty()) {
            return new Location(world, x + 0.5, y + 2, z + 0.5);
        }
        return safeLocations.get(ThreadLocalRandom.current().nextInt(safeLocations.size()));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.LODESTONE) return;

        for (String waystoneName : waystoneConfig.getConfigurationSection("waystones").getKeys(false)) {
            Location waystoneLoc = new Location(
                    Bukkit.getWorld(waystoneConfig.getString("waystones." + waystoneName + ".world")),
                    waystoneConfig.getInt("waystones." + waystoneName + ".x"),
                    waystoneConfig.getInt("waystones." + waystoneName + ".y"),
                    waystoneConfig.getInt("waystones." + waystoneName + ".z")
            );

            if (clicked.getLocation().equals(waystoneLoc)) {
                event.setCancelled(true);
                Player player = event.getPlayer();

                // Store the source waystone location
                activeWaystoneSessions.put(player.getUniqueId(), waystoneLoc);

                // Show waystone list immediately
                showWaystones(player);
                return;
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("waystone")) {
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Usage: /waystone <name> or /waystone remove <name>");
                return true;
            }

            if (args[0].equalsIgnoreCase("remove")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /waystone remove <name>");
                    return true;
                }
                return removeWaystone(player, args[1]);
            }

            return createWaystone(player, args[0]);
        }

        if (cmd.getName().equalsIgnoreCase("waystonetp")) {
            if (!activeWaystoneSessions.containsKey(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You must be at a waystone to teleport!");
                return true;
            }

            if (args.length < 1) {
                showWaystones(player);
                return true;
            }

            return teleportToWaystone(player, args[0]);
        }

        return false;
    }

    private void showWaystones(Player player) {
        if (!waystoneConfig.contains("waystones")) {
            player.sendMessage(ChatColor.RED + "No waystones exist yet!");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Available Waystones:");
        for (String name : waystoneConfig.getConfigurationSection("waystones").getKeys(false)) {
            // Check if this is the current waystone
            Location current = activeWaystoneSessions.get(player.getUniqueId());
            Location waystoneLoc = new Location(
                    Bukkit.getWorld(waystoneConfig.getString("waystones." + name + ".world")),
                    waystoneConfig.getInt("waystones." + name + ".x"),
                    waystoneConfig.getInt("waystones." + name + ".y"),
                    waystoneConfig.getInt("waystones." + name + ".z")
            );

            if (current != null && current.equals(waystoneLoc)) {
                player.sendMessage(ChatColor.GRAY + "- " + name + " (Current Location)");
                continue;
            }

            TextComponent msg = new TextComponent(ChatColor.YELLOW + "- " + name + " ");
            TextComponent btn = new TextComponent(ChatColor.GREEN + "[Teleport]");
            btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/waystonetp " + name));
            btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("Click to teleport (" + SOUL_COST + " souls)").create()));
            msg.addExtra(btn);
            player.spigot().sendMessage(msg);
        }
    }
}