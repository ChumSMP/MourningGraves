package org.Mellurboo.mourningGraves.listener;

import org.Mellurboo.mourningGraves.MourningGraves;
import org.Mellurboo.mourningGraves.config.ConfigManager;
import org.Mellurboo.mourningGraves.grave.Grave;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;

public class PlayerInteractListener implements Listener {

    private final MourningGraves plugin;

    public PlayerInteractListener(MourningGraves plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        Optional<Grave> graveOpt = plugin.getGraveManager()
                .getGraveAt(event.getClickedBlock().getLocation());

        if (graveOpt.isEmpty()) return;
        event.setCancelled(true);

        Grave grave     = graveOpt.get();
        ConfigManager config = plugin.getConfigManager();
        boolean isOwner = grave.getOwnerUUID().equals(player.getUniqueId());
        boolean isAdmin = player.hasPermission("graves.admin");
        boolean canOthers = config.isAllowOthersCollect()
                && player.hasPermission("graves.others");

        // Determine access authorisation?
        if (!isOwner && !isAdmin && !canOthers) {
            player.sendMessage(config.getMessage("no-permission"));
            return;
        }

        if (isAdmin && !isOwner) {
            // Admins right-clicking someone else's grave
            plugin.getGraveManager().collectGrave(player, grave);
            return;
        }

        // Owner or permitted other player
        plugin.getGraveManager().collectGrave(player, grave);
    }
}