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

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

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

    public Waystones(JavaPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.waystoneFile = new File(plugin.getDataFolder(), "waystones.yml");

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Load waystones from config
        loadWaystones();
    }

    /**
     * Loads waystones from configuration file.
     */
    private void loadWaystones() {
        if (!waystoneFile.exists()) {
            try {
                waystoneFile.getParentFile().mkdirs();
                waystoneFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create waystones.yml!");
                e.printStackTrace();
            }
        }

        waystoneConfig = YamlConfiguration.loadConfiguration(waystoneFile);

        // Check for existing waystones and verify that they still exist in the world
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

                // Verify the middle block is a lodestone
                if (block.getType() != Material.LODESTONE) {
                    plugin.getLogger().warning("Waystone " + waystoneName + " at " + loc + " is not a valid waystone structure!");
                }
            }
        }
    }

    /**
     * Saves waystones to configuration file.
     */
    private void saveWaystones() {
        try {
            waystoneConfig.save(waystoneFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save waystones.yml!");
            e.printStackTrace();
        }
    }

    /**
     * Creates a waystone at the player's location.
     */
    private boolean createWaystone(Player player, String name) {
        // Check if waystone with this name already exists
        if (waystoneConfig.contains("waystones." + name)) {
            player.sendMessage(ChatColor.RED + "A waystone with this name already exists!");
            return false;
        }

        Location loc = player.getLocation();
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // Check if there's enough space for the waystone (3 blocks tall)
        for (int i = 0; i < 3; i++) {
            Block block = world.getBlockAt(x, y + i, z);
            if (!block.getType().isAir()) {
                player.sendMessage(ChatColor.RED + "Not enough space to create a waystone!");
                return false;
            }
        }

        // Place the waystone blocks
        world.getBlockAt(x, y, z).setType(Material.BEDROCK);
        world.getBlockAt(x, y + 1, z).setType(Material.LODESTONE);
        world.getBlockAt(x, y + 2, z).setType(Material.BEACON);

        // Save waystone to config
        waystoneConfig.set("waystones." + name + ".world", world.getName());
        waystoneConfig.set("waystones." + name + ".x", x);
        waystoneConfig.set("waystones." + name + ".y", y + 1); // Store the middle block location (lodestone)
        waystoneConfig.set("waystones." + name + ".z", z);
        saveWaystones();

        player.sendMessage(ChatColor.GREEN + "Waystone '" + name + "' created successfully!");
        return true;
    }

    /**
     * Removes a waystone by name.
     * Returns true if successful, false otherwise.
     */
    private boolean removeWaystone(CommandSender sender, String name) {
        if (!waystoneConfig.contains("waystones." + name)) {
            sender.sendMessage(ChatColor.RED + "No waystone with that name exists!");
            return false;
        }

        // Get the waystone location
        String worldName = waystoneConfig.getString("waystones." + name + ".world");
        World world = Bukkit.getWorld(worldName);

        if (world != null) {
            // Get coordinates
            int x = waystoneConfig.getInt("waystones." + name + ".x");
            int y = waystoneConfig.getInt("waystones." + name + ".y"); // This is the lodestone level
            int z = waystoneConfig.getInt("waystones." + name + ".z");

            // Remove the physical blocks (only if the world is loaded)
            Block beaconBlock = world.getBlockAt(x, y + 1, z); // Beacon is one above lodestone
            Block lodestoneBlock = world.getBlockAt(x, y, z);  // Lodestone is at the stored y value
            Block bedrockBlock = world.getBlockAt(x, y - 1, z); // Bedrock is one below lodestone

            // Only remove blocks if they match the expected waystone structure
            if (beaconBlock.getType() == Material.BEACON) {
                beaconBlock.setType(Material.AIR);
            }

            if (lodestoneBlock.getType() == Material.LODESTONE) {
                lodestoneBlock.setType(Material.AIR);
            }

            // Don't remove bedrock in case it's part of natural terrain
            // Only remove if in creative mode and explicitly enabled
            if (sender instanceof Player && ((Player)sender).getGameMode() == GameMode.CREATIVE) {
                if (bedrockBlock.getType() == Material.BEDROCK) {
                    bedrockBlock.setType(Material.AIR);
                }
            }
        }

        // Remove from config
        waystoneConfig.set("waystones." + name, null);
        saveWaystones();

        sender.sendMessage(ChatColor.GREEN + "Waystone '" + name + "' has been removed.");
        return true;
    }

    /**
     * Shows available waystones to a player.
     */
    private void showWaystones(Player player) {
        if (!waystoneConfig.contains("waystones") || waystoneConfig.getConfigurationSection("waystones").getKeys(false).isEmpty()) {
            player.sendMessage(ChatColor.RED + "No waystones are available.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Waystones:");

        for (String waystoneName : waystoneConfig.getConfigurationSection("waystones").getKeys(false)) {
            TextComponent message = new TextComponent(ChatColor.YELLOW + waystoneName + " - ");

            TextComponent teleportButton = new TextComponent(ChatColor.GREEN + "teleport (" + ChatColor.DARK_PURPLE + SOUL_COST + " souls" + ChatColor.GREEN + ")");
            teleportButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/waystonetp " + waystoneName));
            teleportButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("Click to teleport to " + waystoneName + " (costs " + SOUL_COST + " souls)").create()));

            message.addExtra(teleportButton);
            player.spigot().sendMessage(message);
        }
    }

    /**
     * Teleports a player to a waystone.
     */
    private boolean teleportToWaystone(Player player, String waystoneName) {
        if (!waystoneConfig.contains("waystones." + waystoneName)) {
            player.sendMessage(ChatColor.RED + "That waystone doesn't exist!");
            return false;
        }

        // Check if player has enough souls
        int totalSouls = economyManager.getTotalSouls(player);
        if (totalSouls < SOUL_COST) {
            player.sendMessage(ChatColor.RED + "You don't have enough souls! You need " + SOUL_COST + " souls to teleport.");
            return false;
        }

        // Remove souls from player's inventory first, then enderchest if needed
        int remainingSouls = SOUL_COST;

        // First try removing from main inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (economyManager.isValidSoul(item)) {
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

        // If we still need more souls, check enderchest
        if (remainingSouls > 0) {
            for (ItemStack item : player.getEnderChest().getContents()) {
                if (economyManager.isValidSoul(item)) {
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

        // Get waystone location
        String worldName = waystoneConfig.getString("waystones." + waystoneName + ".world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            player.sendMessage(ChatColor.RED + "Error: World not found!");
            return false;
        }

        int x = waystoneConfig.getInt("waystones." + waystoneName + ".x");
        int y = waystoneConfig.getInt("waystones." + waystoneName + ".y");
        int z = waystoneConfig.getInt("waystones." + waystoneName + ".z");

        // Find a safe location to teleport within a 5-block radius
        Location teleportLoc = findSafeLocation(world, x, y, z, 5);

        if (teleportLoc == null) {
            player.sendMessage(ChatColor.RED + "Could not find a safe location to teleport to!");
            return false;
        }

        // Teleport player and apply nausea effect
        player.teleport(teleportLoc);
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 5 * 20, 0)); // 5 seconds of nausea
        player.sendMessage(ChatColor.GREEN + "You have been teleported to waystone '" + waystoneName + "'.");

        return true;
    }

    /**
     * Finds a safe location to teleport within a given radius.
     */
    private Location findSafeLocation(World world, int x, int y, int z, int radius) {
        List<Location> safeLocations = new ArrayList<>();

        for (int i = 0; i < 20; i++) { // Try up to 20 random locations
            int randX = x + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int randZ = z + ThreadLocalRandom.current().nextInt(-radius, radius + 1);

            // Find the highest non-air block at this x,z coordinate
            int highestY = world.getHighestBlockYAt(randX, randZ);
            if (highestY < y - 5) highestY = y; // If too low, use the waystone's y level

            // Check if this is a safe location (2 air blocks above a solid block)
            Block groundBlock = world.getBlockAt(randX, highestY, randZ);
            Block feetBlock = world.getBlockAt(randX, highestY + 1, randZ);
            Block headBlock = world.getBlockAt(randX, highestY + 2, randZ);

            if (groundBlock.getType().isSolid() &&
                    feetBlock.getType().isAir() &&
                    headBlock.getType().isAir()) {

                safeLocations.add(new Location(world, randX + 0.5, highestY + 1, randZ + 0.5));
            }
        }

        if (safeLocations.isEmpty()) {
            // If no safe location found, try just returning a location on top of the waystone
            Location waystoneTop = new Location(world, x + 0.5, y + 2, z + 0.5);
            return waystoneTop;
        }

        // Return a random safe location from the list
        return safeLocations.get(ThreadLocalRandom.current().nextInt(safeLocations.size()));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Ensure the sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("waystone")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /waystone <name> or /waystone remove <name>");
                return true;
            }

            // Handle removal of a waystone
            if (args[0].equalsIgnoreCase("remove")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /waystone remove <name>");
                    return true;
                }
                if (!player.hasPermission("waystone.remove")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to remove waystones.");
                    return true;
                }
                String waystoneName = args[1];
                removeWaystone(player, waystoneName);
                return true;
            }

            // Handle creation of a waystone
            if (!player.hasPermission("waystone.create")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to create waystones.");
                return true;
            }
            String waystoneName = args[0];
            createWaystone(player, waystoneName);
            return true;
        }

        if (command.getName().equalsIgnoreCase("waystonetp")) {
            if (args.length < 1) {
                showWaystones(player);
                return true;
            }
            String waystoneName = args[0];
            teleportToWaystone(player, waystoneName);
            return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click interactions with main hand
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.LODESTONE) {
            return;
        }

        // Check if this lodestone is part of a waystone
        for (String waystoneName : waystoneConfig.getConfigurationSection("waystones").getKeys(false)) {
            String worldName = waystoneConfig.getString("waystones." + waystoneName + ".world");
            if (!worldName.equals(clickedBlock.getWorld().getName())) {
                continue;
            }

            int x = waystoneConfig.getInt("waystones." + waystoneName + ".x");
            int y = waystoneConfig.getInt("waystones." + waystoneName + ".y");
            int z = waystoneConfig.getInt("waystones." + waystoneName + ".z");

            if (clickedBlock.getX() == x && clickedBlock.getY() == y && clickedBlock.getZ() == z) {
                // This is a waystone, show the list of waystones
                event.setCancelled(true);
                showWaystones(event.getPlayer());
                return;
            }
        }
    }
}
