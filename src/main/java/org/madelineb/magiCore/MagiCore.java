package org.madelineb.magiCore;

import org.bukkit.plugin.java.JavaPlugin;

public final class MagiCore extends JavaPlugin {

    private HealthManager healthManager;
    private CombatTagManager combatTagManager;
    private ChairManager chairManager;
    private DeathCoordinatesListener deathCoordinatesListener;
    private TextCommands textCommands;
    private EconomyManager economyManager;
    private Waystones waystones;

    @Override
    public void onEnable() {
        // Create plugin data folder if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Initialize managers
        healthManager = new HealthManager(this);
        combatTagManager = new CombatTagManager(this);
        chairManager = new ChairManager(this);
        deathCoordinatesListener = new DeathCoordinatesListener(this);
        textCommands = new TextCommands();
        economyManager = new EconomyManager(this);
        waystones = new Waystones(this, economyManager);

        // Register command handlers
        this.getCommand("togglesit").setExecutor(chairManager);
        this.getCommand("placeholderone").setExecutor(textCommands);
        this.getCommand("placeholdertwo").setExecutor(textCommands);
        this.getCommand("placeholderthree").setExecutor(textCommands);
        this.getCommand("bag").setExecutor(economyManager);
        this.getCommand("givegold").setExecutor(economyManager);
        this.getCommand("givesouls").setExecutor(economyManager);
        this.getCommand("waystone").setExecutor(waystones);
        this.getCommand("waystonetp").setExecutor(waystones);

        getLogger().info("magiCore has started.");
    }

    @Override
    public void onDisable() {
        // Save data and cleanup
        if (healthManager != null) {
            healthManager.saveAllPlayerHealth();
        }

        if (combatTagManager != null) {
            combatTagManager.saveAllCombatLoggers();
        }

        if (chairManager != null) {
            chairManager.saveSitPreferences();
            chairManager.killAllChairs();
        }

        getLogger().info("magiCore has stopped.");
    }

    public HealthManager getHealthManager() {
        return healthManager;
    }

    public CombatTagManager getCombatTagManager() {
        return combatTagManager;
    }

    public ChairManager getChairManager() {
        return chairManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public Waystones getWaystones() {
        return waystones;
    }
}