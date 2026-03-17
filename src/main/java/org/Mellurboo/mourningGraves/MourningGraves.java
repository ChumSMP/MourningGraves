package org.Mellurboo.mourningGraves;

import org.Mellurboo.mourningGraves.command.GraveCommand;
import org.Mellurboo.mourningGraves.config.ConfigManager;
import org.Mellurboo.mourningGraves.grave.GraveManager;
import org.Mellurboo.mourningGraves.gui.GraveListGUI;
import org.Mellurboo.mourningGraves.listener.PlayerDeathListener;
import org.Mellurboo.mourningGraves.listener.PlayerInteractListener;
import org.bukkit.plugin.java.JavaPlugin;

public class MourningGraves extends JavaPlugin {

    private ConfigManager configManager;
    private GraveManager graveManager;
    private GraveListGUI graveListGUI;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        graveManager  = new GraveManager(this);
        graveManager.loadAll();
        graveListGUI  = new GraveListGUI(this);

        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(this), this);

        GraveCommand graveCmd = new GraveCommand(this);
        var cmd = getCommand("grave");
        if (cmd != null) {
            cmd.setExecutor(graveCmd);
            cmd.setTabCompleter(graveCmd);
        }

        getLogger().info("MourningGraves enabled - " + graveManager.getAllGraves().size() + " graves loaded.");
    }

    @Override
    public void onDisable() {
        if (graveManager != null) graveManager.saveAll();
        getLogger().info("MourningGraves disabled - graves saved.");
    }

    public ConfigManager    getConfigManager() { return configManager; }
    public GraveManager     getGraveManager()  { return graveManager; }
    public GraveListGUI     getGraveListGUI()  { return graveListGUI; }
}