package org.madelineb.magiCore;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Shrine implements CommandExecutor, Listener {

    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final File shrineFile;
    private FileConfiguration shrineConfig;
    private final int ACTIVATION_COST = 4;
    private final int COOLDOWN_MINUTES = 5;
    private final Map<String, ShrineSession> activeSessions = new HashMap<>();
    private final Map<String, Long> shrineCooldowns = new HashMap<>();
    private final Map<UUID, String> pendingConfirmations = new HashMap<>();

    public Shrine(JavaPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.shrineFile = new File(plugin.getDataFolder(), "shrines.yml");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        loadShrines();
    }

    private static class ShrineSession {
        private final Player activator;
        private final Set<UUID> mobIds = new HashSet<>();

        public ShrineSession(Player activator) {
            this.activator = activator;
        }

        public void addMob(UUID mobId) {
            mobIds.add(mobId);
        }

        public void removeMob(UUID mobId) {
            mobIds.remove(mobId);
        }

        public boolean hasMob(UUID mobId) {
            return mobIds.contains(mobId);
        }

        public boolean isCleared() {
            return mobIds.isEmpty();
        }

        public Player getActivator() {
            return activator;
        }

        public Set<UUID> getMobIds() {
            return mobIds;
        }
    }

    private void loadShrines() {
        if (!shrineFile.exists()) {
            try {
                shrineFile.getParentFile().mkdirs();
                shrineFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create shrines.yml!");
            }
        }
        shrineConfig = YamlConfiguration.loadConfiguration(shrineFile);
    }

    private void saveShrines() {
        try {
            shrineConfig.save(shrineFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save shrines.yml!");
        }
    }

    private boolean createShrine(Player player, String name) {
        if (!player.hasPermission("shrine.create")) {
            player.sendMessage(ChatColor.RED + "You lack permission to create shrines!");
            return false;
        }

        if (shrineConfig.contains("shrines." + name)) {
            player.sendMessage(ChatColor.RED + "A shrine named '" + name + "' already exists!");
            return false;
        }

        Location loc = player.getLocation();
        World world = loc.getWorld();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();

        for (int i = 0; i < 3; i++) {
            if (!world.getBlockAt(x, y + i, z).getType().isAir()) {
                player.sendMessage(ChatColor.RED + "Not enough space to build the shrine!");
                return false;
            }
        }

        world.getBlockAt(x, y, z).setType(Material.BEDROCK);
        world.getBlockAt(x, y + 1, z).setType(Material.LODESTONE);
        world.getBlockAt(x, y + 2, z).setType(Material.SPAWNER);

        shrineConfig.set("shrines." + name + ".world", world.getName());
        shrineConfig.set("shrines." + name + ".x", x);
        shrineConfig.set("shrines." + name + ".y", y + 2);
        shrineConfig.set("shrines." + name + ".z", z);
        saveShrines();

        player.sendMessage(ChatColor.GREEN + "Shrine '" + name + "' created!");
        return true;
    }

    private boolean removeShrine(CommandSender sender, String name) {
        if (!sender.hasPermission("shrine.remove")) {
            sender.sendMessage(ChatColor.RED + "You lack permission to remove shrines!");
            return false;
        }

        if (!shrineConfig.contains("shrines." + name)) {
            sender.sendMessage(ChatColor.RED + "Shrine '" + name + "' doesn't exist!");
            return false;
        }

        World world = Bukkit.getWorld(shrineConfig.getString("shrines." + name + ".world"));
        if (world != null) {
            int x = shrineConfig.getInt("shrines." + name + ".x");
            int y = shrineConfig.getInt("shrines." + name + ".y");
            int z = shrineConfig.getInt("shrines." + name + ".z");

            world.getBlockAt(x, y, z).setType(Material.AIR);
            world.getBlockAt(x, y - 1, z).setType(Material.AIR);
            world.getBlockAt(x, y - 2, z).setType(Material.AIR);
        }

        shrineConfig.set("shrines." + name, null);
        saveShrines();
        sender.sendMessage(ChatColor.GREEN + "Shrine '" + name + "' removed!");
        return true;
    }

    private boolean activateShrine(Player player, String shrineName) {
        if (!shrineConfig.contains("shrines." + shrineName)) {
            player.sendMessage(ChatColor.RED + "Shrine '" + shrineName + "' doesn't exist!");
            return false;
        }

        if (shrineCooldowns.containsKey(shrineName)) {
            long cooldownEnd = shrineCooldowns.get(shrineName);
            if (System.currentTimeMillis() < cooldownEnd) {
                long remainingSec = (cooldownEnd - System.currentTimeMillis()) / 1000;
                player.sendMessage(ChatColor.RED + "This shrine is on cooldown for " + remainingSec + " more seconds.");
                return false;
            }
        }

        if (activeSessions.containsKey(shrineName)) {
            player.sendMessage(ChatColor.RED + "This shrine is already active!");
            return false;
        }

        int remainingSouls = ACTIVATION_COST;

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
            player.sendMessage(ChatColor.RED + "You need " + ACTIVATION_COST + " souls to activate this shrine!");
            return false;
        }

        World world = Bukkit.getWorld(shrineConfig.getString("shrines." + shrineName + ".world"));
        Location shrineLoc = new Location(
                world,
                shrineConfig.getInt("shrines." + shrineName + ".x"),
                shrineConfig.getInt("shrines." + shrineName + ".y"),
                shrineConfig.getInt("shrines." + shrineName + ".z")
        );

        ShrineSession session = new ShrineSession(player);
        activeSessions.put(shrineName, session);
        spawnShrineMobs(shrineLoc, session);

        shrineCooldowns.put(shrineName, System.currentTimeMillis() + (COOLDOWN_MINUTES * 60 * 1000));

        world.playSound(shrineLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        player.sendMessage(ChatColor.GREEN + "Shrine activated! Defeat all mobs to earn rewards.");
        return true;
    }

    private void spawnShrineMobs(Location center, ShrineSession session) {
        World world = center.getWorld();
        Random rand = ThreadLocalRandom.current();

        for (int i = 0; i < rand.nextInt(4) + 3; i++) {
            Location spawnLoc = center.clone().add(rand.nextInt(21) - 10, 0, rand.nextInt(21) - 10);
            spawnLoc.setY(world.getHighestBlockYAt(spawnLoc) + 1);
            LivingEntity mob = (LivingEntity) world.spawnEntity(spawnLoc, EntityType.WITHER_SKELETON);
            session.addMob(mob.getUniqueId());
        }

        for (int i = 0; i < rand.nextInt(3) + 2; i++) {
            Location spawnLoc = center.clone().add(rand.nextInt(21) - 10, 0, rand.nextInt(21) - 10);
            spawnLoc.setY(world.getHighestBlockYAt(spawnLoc) + 1);
            LivingEntity mob = (LivingEntity) world.spawnEntity(spawnLoc, EntityType.VEX);
            session.addMob(mob.getUniqueId());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        UUID mobId = event.getEntity().getUniqueId();

        for (Map.Entry<String, ShrineSession> entry : activeSessions.entrySet()) {
            ShrineSession session = entry.getValue();
            if (session.hasMob(mobId)) {
                session.removeMob(mobId);

                if (session.isCleared()) {
                    Player player = session.getActivator();
                    int reward = ThreadLocalRandom.current().nextInt(4, 13);
                    economyManager.addSouls(player, reward);
                    player.sendMessage(ChatColor.GOLD + "Shrine cleared! You earned " + reward + " souls.");
                    activeSessions.remove(entry.getKey());
                }
                break;
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.SPAWNER) return;

        for (String shrineName : shrineConfig.getConfigurationSection("shrines").getKeys(false)) {
            Location shrineLoc = new Location(
                    Bukkit.getWorld(shrineConfig.getString("shrines." + shrineName + ".world")),
                    shrineConfig.getInt("shrines." + shrineName + ".x"),
                    shrineConfig.getInt("shrines." + shrineName + ".y"),
                    shrineConfig.getInt("shrines." + shrineName + ".z")
            );

            if (clicked.getLocation().equals(shrineLoc)) {
                event.setCancelled(true);
                Player player = event.getPlayer();

                // Check cooldown first
                if (shrineCooldowns.containsKey(shrineName)) {
                    long cooldownEnd = shrineCooldowns.get(shrineName);
                    if (System.currentTimeMillis() < cooldownEnd) {
                        long remainingSec = (cooldownEnd - System.currentTimeMillis()) / 1000;
                        player.sendMessage(ChatColor.RED + "This shrine is on cooldown for " + remainingSec + " more seconds.");
                        return;
                    }
                }

                // Check if already active
                if (activeSessions.containsKey(shrineName)) {
                    player.sendMessage(ChatColor.RED + "This shrine is already active!");
                    return;
                }

                // Check souls
                if (economyManager.getTotalSouls(player) < ACTIVATION_COST) {
                    player.sendMessage(ChatColor.RED + "You need " + ACTIVATION_COST + " souls to activate this shrine!");
                    return;
                }

                // Store pending confirmation
                pendingConfirmations.put(player.getUniqueId(), shrineName);

                // Send confirmation message
                TextComponent message = new TextComponent(ChatColor.GOLD + "Activate this shrine for " + ACTIVATION_COST + " souls? ");
                TextComponent confirmBtn = new TextComponent(ChatColor.GREEN + "[CONFIRM]");
                confirmBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/shrine confirm"));
                confirmBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder("Click to activate the shrine").create()));

                TextComponent cancelBtn = new TextComponent(ChatColor.RED + " [CANCEL]");
                cancelBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/shrine cancel"));
                cancelBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder("Click to cancel").create()));

                message.addExtra(confirmBtn);
                message.addExtra(cancelBtn);
                player.spigot().sendMessage(message);
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

        // Handle confirmation commands
        if (cmd.getName().equalsIgnoreCase("shrine") && args.length > 0) {
            if (args[0].equalsIgnoreCase("confirm")) {
                UUID playerId = player.getUniqueId();
                if (pendingConfirmations.containsKey(playerId)) {
                    String shrineName = pendingConfirmations.get(playerId);
                    pendingConfirmations.remove(playerId);
                    return activateShrine(player, shrineName);
                } else {
                    player.sendMessage(ChatColor.RED + "You don't have any pending shrine activation!");
                    return true;
                }
            }
            else if (args[0].equalsIgnoreCase("cancel")) {
                UUID playerId = player.getUniqueId();
                if (pendingConfirmations.containsKey(playerId)) {
                    pendingConfirmations.remove(playerId);
                    player.sendMessage(ChatColor.YELLOW + "Shrine activation cancelled.");
                }
                return true;
            }
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /shrine <create|remove|list|forceend> [name]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /shrine create <name>");
                    return true;
                }
                createShrine(player, args[1]);
                break;

            case "remove":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /shrine remove <name>");
                    return true;
                }
                removeShrine(player, args[1]);
                break;

            case "activate":
                if (!player.isOp()) {
                    player.sendMessage(ChatColor.RED + "You can only activate shrines by interacting with them!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /shrine activate <name>");
                    return true;
                }
                activateShrine(player, args[1]);
                break;

            case "list":
                showShrines(player);
                break;

            case "forceend":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /shrine forceend <name>");
                    return true;
                }
                forceEndShrine(player, args[1]);
                break;

            default:
                player.sendMessage(ChatColor.RED + "Invalid subcommand. Use: /shrine <create|remove|list|forceend>");
        }
        return true;
    }

    private void showShrines(Player player) {
        if (!shrineConfig.contains("shrines")) {
            player.sendMessage(ChatColor.RED + "No shrines exist yet!");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Available Shrines:");
        for (String name : shrineConfig.getConfigurationSection("shrines").getKeys(false)) {
            TextComponent msg = new TextComponent(ChatColor.YELLOW + "- " + name + " ");
            TextComponent btn = new TextComponent(ChatColor.GREEN + "[Activate]");
            btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/shrine activate " + name));
            btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Cost: " + ACTIVATION_COST + " souls").create()));
            msg.addExtra(btn);
            player.spigot().sendMessage(msg);
        }
    }

    private void forceEndShrine(Player player, String name) {
        if (!player.hasPermission("shrine.forceend")) {
            player.sendMessage(ChatColor.RED + "You lack permission to force-end shrines!");
            return;
        }

        if (!activeSessions.containsKey(name)) {
            player.sendMessage(ChatColor.RED + "Shrine '" + name + "' is not active!");
            return;
        }

        for (UUID mobId : activeSessions.get(name).getMobIds()) {
            Entity mob = Bukkit.getEntity(mobId);
            if (mob != null) mob.remove();
        }

        activeSessions.remove(name);
        player.sendMessage(ChatColor.GREEN + "Shrine '" + name + "' has been force-ended.");
    }
}