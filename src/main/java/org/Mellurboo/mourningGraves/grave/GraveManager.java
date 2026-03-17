package org.Mellurboo.mourningGraves.grave;

import org.Mellurboo.mourningGraves.MourningGraves;
import org.Mellurboo.mourningGraves.config.ConfigManager;
import org.Mellurboo.mourningGraves.storage.GraveStorage;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GraveManager {

    private final MourningGraves plugin;
    private final ConfigManager config;
    private final GraveStorage storage;
    private final GraveRenderer renderer;

    private final Map<UUID, Grave> graves        = new ConcurrentHashMap<>();
    private final Map<String, UUID> locationIndex = new ConcurrentHashMap<>();

    public GraveManager(MourningGraves plugin) {
        this.plugin   = plugin;
        this.config   = plugin.getConfigManager();
        this.storage  = new GraveStorage(plugin);
        this.renderer = new GraveRenderer(plugin);
    }

    // init graves
    public void loadAll() {
        List<Grave> loaded = storage.loadAll();
        for (Grave grave : loaded) {
            if (grave.isExpired()) {
                storage.deleteGrave(grave.getGraveId());
            } else {
                register(grave);
            }
        }
        plugin.getLogger().info("Loaded " + graves.size() + " graves.");

        // Re-spawn all display entities after world is ready (1 tick delay)
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Grave grave : graves.values()) {
                renderer.spawnGrave(grave);
            }
        });

        startExpiryTask();
    }

    private void startExpiryTask() {
        new BukkitRunnable() {
            @Override public void run() {
                graves.values().stream()
                        .filter(Grave::isExpired)
                        .toList()
                        .forEach(GraveManager.this::expireGrave);
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    // new grave
    public Grave createGrave(Player player, Location location, List<ItemStack> items, int xp) {
        long expiry = player.hasPermission("graves.unlimitedTime") ? -1 : config.getGraveExpirySeconds();

        Grave grave = new Grave(player.getUniqueId(), player.getName(), location, items, xp, expiry);
        register(grave);
        storage.saveGrave(grave);

        Bukkit.getScheduler().runTask(plugin, () -> renderer.spawnGrave(grave));

        if (config.isGiveCompass()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) giveCompass(player, grave);
            }, 10L);
        }

        return grave;
    }

    // collection
    public boolean collectGrave(Player player, Grave grave) {
        if (grave.isExpired()) {
            expireGrave(grave);
            player.sendMessage(config.getMessage("grave-expired"));
            return false;
        }

        // XP level cost to collect
        int costLevels = config.getCollectXpCostLevels();
        if (costLevels > 0 && !player.hasPermission("graves.unlimitedTime")) {
            if (player.getLevel() < costLevels) {
                player.sendMessage(config.getMessage("not-enough-levels")
                        .replace("{levels}", String.valueOf(costLevels)));
                return false;
            }
            player.setLevel(player.getLevel() - costLevels);
        }

        Map<Integer, ItemStack> leftOver = player.getInventory().addItem(
                grave.getItems().stream().filter(Objects::nonNull).toArray(ItemStack[]::new)
        );
        leftOver.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));

        if (config.isSaveExperience() && grave.getExperience() > 0)
            player.giveExp(grave.getExperience());

        if (config.getGraveCollectSound() != null)
            player.playSound(player.getLocation(), config.getGraveCollectSound(), 1f, 1f);

        if (config.isRemoveCompassOnCollect()) removeCompass(player);

        player.sendMessage(config.getMessage("grave-collected")
                .replace("{items}", String.valueOf(grave.getItems().stream().filter(Objects::nonNull).count()))
                .replace("{xp}", String.valueOf(grave.getExperience())));

        removeGrave(grave);
        return true;
    }

    // grave invalid (expire)
    public void expireGrave(Grave grave) {
        Location loc = grave.getLocation();
        if (loc != null && loc.getWorld() != null)
            grave.getItems().stream().filter(Objects::nonNull)
                    .forEach(item -> loc.getWorld().dropItemNaturally(loc, item));

        Player owner = Bukkit.getPlayer(grave.getOwnerUUID());
        if (owner != null) owner.sendMessage(config.getMessage("grave-expired"));

        removeGrave(grave);
    }

    private void removeGrave(Grave grave) {
        graves.remove(grave.getGraveId());
        locationIndex.remove(locationKey(grave.getLocation()));
        storage.deleteGrave(grave.getGraveId());
        Bukkit.getScheduler().runTask(plugin, () -> renderer.removeGrave(grave));
    }

    // getters (lookups)
    public Optional<Grave> getGraveAt(Location loc) {
        UUID id = locationIndex.get(locationKey(loc));
        return id == null ? Optional.empty() : Optional.ofNullable(graves.get(id));
    }

    public List<Grave> getGravesFor(UUID playerUUID) {
        return graves.values().stream()
                .filter(g -> g.getOwnerUUID().equals(playerUUID))
                .sorted(Comparator.comparingLong(Grave::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Grave> getAllGraves() { return List.copyOf(graves.values()); }

    public Optional<Grave> getGrave(UUID graveId) { return Optional.ofNullable(graves.get(graveId)); }

    public void saveGrave(Grave grave) { storage.saveGrave(grave); }
    public void saveAll()              { graves.values().forEach(storage::saveGrave); }

    // compass
    private void giveCompass(Player player, Grave grave) {
        Location graveLoc = grave.getLocation();
        if (graveLoc == null) return;

        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta  = (CompassMeta) compass.getItemMeta();
        meta.setDisplayName(ConfigManager.colorize(config.getCompassName()));
        meta.setLodestoneTracked(false);
        meta.setLodestone(graveLoc);
        meta.setLore(List.of(
                ConfigManager.colorize("&7Points to your grave at"),
                ConfigManager.colorize("&e" + graveLoc.getBlockX() + ", " + graveLoc.getBlockY() + ", " + graveLoc.getBlockZ()),
                ConfigManager.colorize("&7World: &f" + grave.getWorldName())
        ));
        compass.setItemMeta(meta);

        player.getInventory().addItem(compass);
        player.sendMessage(config.getMessage("compass-given"));
    }

    private void removeCompass(Player player) {
        var inv = player.getInventory();
        String compassName = ConfigManager.colorize(config.getCompassName());
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.COMPASS) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && compassName.equals(meta.getDisplayName())) {
                    inv.setItem(i, null);
                    break;
                }
            }
        }
    }

    // internal grave registers
    private void register(Grave grave) {
        graves.put(grave.getGraveId(), grave);
        Location loc = grave.getLocation();
        if (loc != null) locationIndex.put(locationKey(loc), grave.getGraveId());
    }

    private String locationKey(Location loc) {
        if (loc == null) return "null";
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}