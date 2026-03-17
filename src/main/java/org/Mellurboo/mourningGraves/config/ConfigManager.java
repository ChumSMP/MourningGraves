package org.Mellurboo.mourningGraves.config;

import org.Mellurboo.mourningGraves.MourningGraves;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final MourningGraves plugin;
    private FileConfiguration cfg;

    public ConfigManager(MourningGraves plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        cfg = plugin.getConfig();
    }

    // grave behavour
    public long getGraveExpirySeconds()       { return cfg.getLong("grave-expiry-seconds", 300); }
    public boolean isSaveExperience()         { return cfg.getBoolean("save-experience", true); }
    public boolean isGiveCompass()            { return cfg.getBoolean("give-compass", true); }
    public String getCompassName()            { return cfg.getString("compass-name", "&6☠ Grave Compass"); }
    public boolean isRemoveCompassOnCollect() { return cfg.getBoolean("remove-compass-on-collect", true); }
    public boolean isShowNameTag()            { return cfg.getBoolean("show-name-tag", true); }
    public String getNameTagFormat()          { return cfg.getString("name-tag-format", "&f{player}'s Grave\n&7{time}"); }

    // exp level cost to collect
    public int getCollectXpCostLevels()       { return cfg.getInt("collect-xp-cost-levels", 0); }

    // If false, clicking a grave in the GUI does nothing and players must collect in-world
    public boolean isGuiCollectEnabled()      { return cfg.getBoolean("allow-gui-collect", true); }

    // If true, players with graves.others permission can collect other players' graves.
    public boolean isAllowOthersCollect()     { return cfg.getBoolean("allow-others-collect", false); }

    public Material getGraveBlock() {
        String raw = cfg.getString("grave-block", "SOUL_SAND");
        try { return Material.valueOf(raw.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.SOUL_SAND; }
    }

    public Particle getGraveParticle() {
        String raw = cfg.getString("grave-particle", "SOUL_FIRE_FLAME");
        if (raw.equalsIgnoreCase("NONE")) return null;
        NamespacedKey key = NamespacedKey.minecraft(raw.toLowerCase());
        Particle particle = Registry.PARTICLE_TYPE.get(key);
        if (particle == null) {
            plugin.getLogger().warning("Unknown particle '" + raw + "' in config, disabling particles.");
        }
        return particle;
    }

    public Sound getGraveCreateSound() {
        return parseSound(cfg.getString("grave-create-sound", "ENTITY_WITHER_SPAWN"));
    }

    public Sound getGraveCollectSound() {
        return parseSound(cfg.getString("grave-collect-sound", "ENTITY_PLAYER_LEVELUP"));
    }

    private Sound parseSound(String raw) {
        try { return Sound.valueOf(raw.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    // config gui

    public String getGuiTitle()      { return cfg.getString("gui.title", "&8☠ &fYour Graves"); }
    public String getGuiAdminTitle() { return cfg.getString("gui.admin-title", "&8☠ &c{player}'s Graves"); }
    public int getGuiRows()          { return Math.max(1, Math.min(6, cfg.getInt("gui.rows", 6))); }

    public Material getGraveItemMaterial() {
        return parseMaterial(cfg.getString("gui.grave-item", "SOUL_LANTERN"), Material.SOUL_LANTERN);
    }

    public Material getEmptyItemMaterial() {
        return parseMaterial(cfg.getString("gui.empty-item", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
    }

    public Material getFillerItemMaterial() {
        return parseMaterial(cfg.getString("gui.filler-item", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE);
    }

    public String getEmptyName() { return cfg.getString("gui.empty-name", "&7No active graves"); }

    private Material parseMaterial(String raw, Material fallback) {
        try { return Material.valueOf(raw.toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }

    // message helpers

    public String getMessage(String key) {
        String prefix = cfg.getString("messages.prefix", "&8[&6Graves&8] &r");
        String msg    = cfg.getString("messages." + key, "&cMessage not found: " + key);
        return colorize(prefix + msg);
    }

    public static String colorize(String s) {
        return s == null ? "" : s.replace("&", "\u00a7");
    }
}