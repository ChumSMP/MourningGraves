package org.Mellurboo.mourningGraves.listener;

import org.Mellurboo.mourningGraves.MourningGraves;
import org.Mellurboo.mourningGraves.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PlayerDeathListener implements Listener {

    private final MourningGraves plugin;
    private final ConfigManager config;

    public PlayerDeathListener(MourningGraves plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // In Paper 1.21 event.getDrops() starts empty
        List<ItemStack> graveItems = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) graveItems.add(item.clone());
        }

        int xp = config.isSaveExperience() ? event.getDroppedExp() : 0;

        // Nothing to save, saves us a job
        if (graveItems.isEmpty() && xp == 0) return;
        player.getInventory().clear();
        event.getDrops().clear();

        if (config.isSaveExperience()) event.setDroppedExp(0);

        Location graveLoc = findGraveLocation(player.getLocation().clone());

        plugin.getGraveManager().createGrave(player, graveLoc, graveItems, xp);

        String msg = config.getMessage("grave-created")
                .replace("{x}", String.valueOf(graveLoc.getBlockX()))
                .replace("{y}", String.valueOf(graveLoc.getBlockY()))
                .replace("{z}", String.valueOf(graveLoc.getBlockZ()))
                .replace("{world}", graveLoc.getWorld().getName());

        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> { if (player.isOnline()) player.sendMessage(msg); }, 5L);
    }

    // find a sensible place to put the grave
    private Location findGraveLocation(Location loc) {
        loc = loc.getBlock().getLocation();

        int attempts = 0;
        while (loc.getBlock().getType().isSolid() && attempts++ < 10) loc.add(0, 1, 0);

        attempts = 0;
        while (!loc.getBlock().getType().isSolid()
                && loc.getBlockY() > loc.getWorld().getMinHeight()
                && attempts++ < 10) {
            loc.subtract(0, 1, 0);
        }

        return loc;
    }
}