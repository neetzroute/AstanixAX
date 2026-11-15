package dev.astanix.axray;

import dev.astanix.axray.command.AxCommand;
import dev.astanix.axray.config.PluginConfig;
import dev.astanix.axray.gui.GuiManager;
import dev.astanix.axray.highlight.HighlightService;
import dev.astanix.axray.storage.DatabaseManager;
import dev.astanix.axray.storage.OreRepository;
import dev.astanix.axray.tracker.OreBreakListener;
import dev.astanix.axray.tracker.OreTrackerService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class AstanixAXPlugin extends JavaPlugin {
    private PluginConfig configManager;
    private DatabaseManager databaseManager;
    private OreRepository repository;
    private OreTrackerService trackerService;
    private HighlightService highlightService;
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.configManager = new PluginConfig(getConfig());

        // Initialize SQLite Database & Connection Pool
        try {
            this.databaseManager = new DatabaseManager(getDataFolder(), configManager, getLogger());
            this.repository = new OreRepository(databaseManager, this, getLogger());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database! Disabling plugin...", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize Services
        this.trackerService = new OreTrackerService(this, configManager, repository, getLogger());
        this.highlightService = new HighlightService(this, configManager);
        this.guiManager = new GuiManager();

        // Register Event Listeners
        getServer().getPluginManager().registerEvents(new OreBreakListener(configManager, trackerService), this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(highlightService, this);

        // Register Command
        PluginCommand axCommand = getCommand("astanixax");
        if (axCommand != null) {
            AxCommand executor = new AxCommand(this);
            axCommand.setExecutor(executor);
            axCommand.setTabCompleter(executor);
        }

        getLogger().info("=================================================");
        getLogger().info(" Astanix-AX v" + getPluginMeta().getVersion() + " has been successfully enabled!");
        getLogger().info(" High-performance Administrative Anti-Xray Active");
        getLogger().info("=================================================");
    }

    @Override
    public void onDisable() {
        // Clear all active packet highlights for online admins
        if (highlightService != null) {
            highlightService.clearAll();
        }

        // Close SQLite connection pool cleanly
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("Astanix-AX has been safely disabled.");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        this.configManager = new PluginConfig(getConfig());
    }

    public PluginConfig getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public OreRepository getRepository() {
        return repository;
    }

    public OreTrackerService getTrackerService() {
        return trackerService;
    }

    public HighlightService getHighlightService() {
        return highlightService;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }
}
