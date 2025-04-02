package org.madelineb.magiCore;
    // 100% credit to https://github.com/Minemobs/Chairs
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChairManager implements Listener, CommandExecutor {

    private final MagiCore plugin;
    private boolean sitEnabled = true;
    private final Set<UUID> playersWithSitEnabled = new HashSet<>();
    private final File sitFile;
    private final FileConfiguration sitData;
    private static final Map<Block, Chair> chairs = new HashMap<>();

    private record Chair(LivingEntity stair, Location oldPlayerLoc, AtomicBoolean hasMoved) {}

    public ChairManager(MagiCore plugin) {
        this.plugin = plugin;

        // Create or load sitEnabled.yml
        sitFile = new File(plugin.getDataFolder(), "sitEnabled.yml");
        if (!sitFile.exists()) {
            try {
                sitFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create sitEnabled.yml: " + e.getMessage());
            }
        }
        sitData = YamlConfiguration.loadConfiguration(sitFile);

        // Load sit preferences
        sitEnabled = sitData.getBoolean("globalEnabled", true);
        ConfigurationSection sitSection = sitData.getConfigurationSection("players");
        if (sitSection != null) {
            for (String uuidString : sitSection.getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                boolean enabled = sitSection.getBoolean(uuidString);
                if (enabled) {
                    playersWithSitEnabled.add(uuid);
                }
            }
        }

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("togglesit")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (args.length > 0 && args[0].equalsIgnoreCase("global") && player.hasPermission("magicore.sit.admin")) {
            sitEnabled = !sitEnabled;
            player.sendMessage(ChatColor.GREEN + "Global sit functionality is now " +
                    (sitEnabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") +
                    ChatColor.GREEN + ".");

            // If disabled globally, remove all chairs
            if (!sitEnabled) {
                killAllChairs();
            }

            return true;
        }

        if (playersWithSitEnabled.contains(uuid)) {
            playersWithSitEnabled.remove(uuid);
            player.sendMessage(ChatColor.GREEN + "Sitting has been " + ChatColor.RED + "disabled" +
                    ChatColor.GREEN + " for you.");
        } else {
            playersWithSitEnabled.add(uuid);
            player.sendMessage(ChatColor.GREEN + "Sitting has been " + ChatColor.GREEN + "enabled" +
                    ChatColor.GREEN + " for you.");
        }

        return true;
    }

    @EventHandler
    public void onChairInteract(PlayerInteractEvent event) {
        // Check if sitting is enabled globally and for this player
        if (!sitEnabled || !playersWithSitEnabled.contains(event.getPlayer().getUniqueId())) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getPlayer().isSneaking() ||
                event.getPlayer().isInsideVehicle() || !(event.getClickedBlock().getBlockData() instanceof Stairs stairs) ||
                !event.getClickedBlock().getLocation().add(0, 1, 0).getBlock().isEmpty() ||
                stairs.getHalf() == Half.TOP) {
            return;
        }

        // Check if player is in combat
        if (plugin.getCombatTagManager().isPlayerCombatTagged(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot sit while in combat!");
            return;
        }

        LivingEntity ast;
        if (!chairs.containsKey(event.getClickedBlock())) {
            Location centeredLoc = getCenteredLoc(event.getClickedBlock());
            centeredLoc.setDirection(stairs.getFacing().getDirection().multiply(-1));
            ast = event.getPlayer().getWorld()
                    .spawn(centeredLoc, ArmorStand.class, chair -> {
                        chair.setMarker(true);
                        chair.setSilent(true);
                        chair.setGravity(false);
                        chair.setPersistent(true);
                        chair.setVisible(false);
                    });
            chairs.put(event.getClickedBlock(), new Chair(ast, event.getPlayer().getLocation(), new AtomicBoolean()));
        } else {
            ast = chairs.get(event.getClickedBlock()).stair();
        }

        if (!ast.getPassengers().isEmpty()) return;
        ast.addPassenger(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getDismounted() instanceof ArmorStand ast)) return;
        Location location = ast.getLocation();
        var entryOptional = chairs.values().stream().filter(entry -> entry.stair().getUniqueId().equals(ast.getUniqueId())).findFirst();
        if (entryOptional.isEmpty()) return;
        chairs.remove(location.subtract(.5d, .25d, .5d).getBlock());
        var oldPlayerLocation = entryOptional.get().oldPlayerLoc();
        if (oldPlayerLocation.getBlock().isEmpty() && !oldPlayerLocation.add(0, -1, 0).getBlock().isPassable() &&
                oldPlayerLocation.add(0, 1, 0).getBlock().isEmpty() && !entryOptional.get().hasMoved().get()) {
            player.teleport(oldPlayerLocation);
        } else {
            player.teleport(player.getLocation().add(0, 1, 0).setDirection(player.getEyeLocation().getDirection()));
        }
        ast.remove();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!chairs.containsKey(event.getBlock()))
            return;
        LivingEntity entity = chairs.get(event.getBlock()).stair();
        entity.eject();
        entity.remove();
        chairs.remove(event.getBlock());
    }

    @EventHandler
    public void onBlockExplode(EntityExplodeEvent event) {
        chairs.keySet().stream()
                .filter(event.blockList()::contains)
                .map(chairs::get)
                .map(Chair::stair)
                .forEach(entity -> {
                    entity.eject();
                    entity.remove();
                });

        event.blockList().forEach(chairs::remove);
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        onPiston(event.getBlocks(), event.getDirection());
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        onPiston(event.getBlocks(), event.getDirection());
    }

    private Location getCenteredLoc(Block block) {
        return getCenteredLoc(block.getLocation());
    }

    private Location getCenteredLoc(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ())
                .add(.5d, .25d, .5d);
    }

    private void onPiston(List<Block> blocks, BlockFace direction) {
        blocks.stream().filter(chairs::containsKey).map(Block::getState).forEach(block -> {
            LivingEntity ast = chairs.get(block.getBlock()).stair();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location add = getCenteredLoc(block.getBlock().getLocation().add(direction.getDirection()));
                Block blk = add.getBlock();
                if (blk.getBlockData() instanceof Stairs rot)
                    add.setDirection(rot.getFacing().getDirection());
                setEntityPos(ast, add);
                chairs.put(blk, new Chair(ast, chairs.get(block.getBlock()).oldPlayerLoc(), new AtomicBoolean(true)));
                chairs.remove(block.getBlock());
            });
        });
    }

    private void setEntityPos(Entity entity, Location loc) {
        try {
            Class<?> entityClass = Class.forName("org.bukkit.craftbukkit."
                    + Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3]
                    + ".entity.CraftEntity");
            Object ent = entityClass.cast(entity);
            Method getHandle = ent.getClass().getMethod("getHandle");
            Object handle = getHandle.invoke(ent);
            Method method = getHandle.getReturnType().getMethod("a", double.class, double.class, double.class,
                    float.class, float.class);
            method.invoke(handle, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().severe(() -> Throwables.getStackTraceAsString(e));
        }
    }

    public void killAllChairs() {
        ImmutableMap.copyOf(chairs).forEach((block, entry) -> {
            var entity = entry.stair();
            entity.eject();
            entity.remove();
            chairs.remove(block);
        });
    }

    public void saveSitPreferences() {
        sitData.set("globalEnabled", sitEnabled);
        sitData.set("players", null);
        for (UUID uuid : playersWithSitEnabled) {
            sitData.set("players." + uuid.toString(), true);
        }

        try {
            sitData.save(sitFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save sit data: " + e.getMessage());
        }
    }

    public boolean isSitEnabled() {
        return sitEnabled;
    }

    public Set<UUID> getPlayersWithSitEnabled() {
        return playersWithSitEnabled;
    }
}