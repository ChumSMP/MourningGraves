package org.Mellurboo.mourningGraves.storage;

import org.Mellurboo.mourningGraves.MourningGraves;
import org.Mellurboo.mourningGraves.grave.Grave;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Persists graves to individual YAML files at one per grave.
 * File location: plugins/MourningGraves/graves/<graveId>.yml
 */
public class GraveStorage {

    private final MourningGraves plugin;
    private final Path storageDir;

    public GraveStorage(MourningGraves plugin) {
        this.plugin = plugin;
        String folder = plugin.getConfig().getString("storage.folder", "graves");
        this.storageDir = plugin.getDataFolder().toPath().resolve(folder);
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create graves storage directory", e);
        }
    }

    // API
    public void saveGrave(Grave grave) {
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("graveId",    grave.getGraveId().toString());
        yaml.set("ownerUUID",  grave.getOwnerUUID().toString());
        yaml.set("ownerName",  grave.getOwnerName());
        yaml.set("world",      grave.getWorldName());
        yaml.set("x",          grave.getLocation() != null ? grave.getLocation().getX() : 0);
        yaml.set("y",          grave.getLocation() != null ? grave.getLocation().getY() : 0);
        yaml.set("z",          grave.getLocation() != null ? grave.getLocation().getZ() : 0);
        yaml.set("yaw",        grave.getLocation() != null ? grave.getLocation().getYaw() : 0);
        yaml.set("experience", grave.getExperience());
        // Store longs as strings YAML integers are 32-bit and silently truncate epoch millis :/
        yaml.set("createdAt",  String.valueOf(grave.getCreatedAt()));
        yaml.set("expiresAt",  String.valueOf(grave.getExpiresAt()));

        if (grave.getOriginalBlockData() != null)
            yaml.set("originalBlockData", grave.getOriginalBlockData());

        // Bukkit-native ItemStack serialization
        List<ItemStack> items = grave.getItems();
        yaml.set("itemCount", items.size());
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) != null) yaml.set("items." + i, items.get(i));
        }

        try {
            yaml.save(graveFile(grave.getGraveId()).toFile());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save grave " + grave.getGraveId(), e);
        }
    }

    public void deleteGrave(UUID graveId) {
        try {
            Files.deleteIfExists(graveFile(graveId));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete grave " + graveId, e);
        }
    }

    public List<Grave> loadAll() {
        List<Grave> result = new ArrayList<>();
        try (var stream = Files.list(storageDir)) {
            stream.filter(p -> p.toString().endsWith(".yml")).forEach(p -> {
                try {
                    Grave grave = loadGrave(p);
                    if (grave != null) result.add(grave);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load grave from " + p, e);
                }
            });
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to list graves directory", e);
        }
        return result;
    }

    // load graves from yaml
    private Grave loadGrave(Path path) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());

        try {
            UUID graveId   = UUID.fromString(yaml.getString("graveId"));
            UUID ownerUUID = UUID.fromString(yaml.getString("ownerUUID"));
            String ownerName = yaml.getString("ownerName");
            String world   = yaml.getString("world");
            double x       = yaml.getDouble("x");
            double y       = yaml.getDouble("y");
            double z       = yaml.getDouble("z");
            float  yaw     = (float) yaml.getDouble("yaw");
            int    xp      = yaml.getInt("experience");
            long createdAt = Long.parseLong(yaml.getString("createdAt", "0"));
            long expiresAt = Long.parseLong(yaml.getString("expiresAt", "-1"));

            int itemCount = yaml.getInt("itemCount", 0);
            List<ItemStack> items = new ArrayList<>(Collections.nCopies(itemCount, null));
            if (yaml.isConfigurationSection("items")) {
                for (String key : yaml.getConfigurationSection("items").getKeys(false)) {
                    try {
                        int idx = Integer.parseInt(key);
                        ItemStack item = yaml.getItemStack("items." + key);
                        if (idx < itemCount) items.set(idx, item);
                    } catch (NumberFormatException ignored) {}
                }
            }

            Grave grave = new Grave(graveId, ownerUUID, ownerName, world,
                    x, y, z, yaw, items, xp, createdAt, expiresAt);

            if (yaml.contains("originalBlockData"))
                grave.setOriginalBlockData(yaml.getString("originalBlockData"));

            return grave;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not deserialize grave from " + path, e);
            return null;
        }
    }

    private Path graveFile(UUID graveId) {
        return storageDir.resolve(graveId + ".yml");
    }
}