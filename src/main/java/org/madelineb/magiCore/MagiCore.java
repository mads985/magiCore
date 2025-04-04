package org.madelineb.magiCore;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class MagiCore extends JavaPlugin {

    private HealthManager healthManager;
    private CombatTagManager combatTagManager;
    private ChairManager chairManager;
    private EconomyManager economyManager;
    private Waystones waystones;
    private Shrine shrine;
    private TabList tabList;

    @Override
    public void onEnable() {
        // Create data folder if needed
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Initialize core components
        healthManager = new HealthManager(this);
        combatTagManager = new CombatTagManager(this);
        chairManager = new ChairManager(this);
        economyManager = new EconomyManager(this);
        waystones = new Waystones(this, economyManager);
        shrine = new Shrine(this, economyManager);

        // Initialize TabList
        tabList = new TabList(this);

        // Register commands
        registerCommands();

        // Start tab list updates
        new BukkitRunnable() {
            @Override
            public void run() {
                tabList.updateAllPlayers();
            }
        }.runTaskTimer(this, 0L, 20L);

        getLogger().info("magiCore has started.");
    }

    private void registerCommands() {
        getCommand("togglesit").setExecutor(chairManager);
        getCommand("bag").setExecutor(economyManager);
        getCommand("givegold").setExecutor(economyManager);
        getCommand("givesouls").setExecutor(economyManager);
        getCommand("waystone").setExecutor(waystones);
        getCommand("waystonetp").setExecutor(waystones);
        getCommand("shrine").setExecutor(shrine);
    }

    @Override
    public void onDisable() {
        // Save data
        healthManager.saveAllPlayerHealth();
        combatTagManager.saveAllCombatLoggers();
        chairManager.saveSitPreferences();
        chairManager.killAllChairs();

        getLogger().info("magiCore has stopped.");
    }

    // Getters
    public HealthManager getHealthManager() { return healthManager; }
    public CombatTagManager getCombatTagManager() { return combatTagManager; }
    public ChairManager getChairManager() { return chairManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public Waystones getWaystones() { return waystones; }
    public TabList getTabList() { return tabList; } // Updated getter name
}
