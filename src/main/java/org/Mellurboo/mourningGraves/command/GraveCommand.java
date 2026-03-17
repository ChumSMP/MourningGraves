package org.Mellurboo.mourningGraves.command;

import org.Mellurboo.mourningGraves.MourningGraves;
import org.Mellurboo.mourningGraves.config.ConfigManager;
import org.Mellurboo.mourningGraves.grave.Grave;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /grave                   - open your graves GUI
 * /grave list              - same
 * /grave tp <id>           - teleport to a grave (admin: any grave)
 * /grave delete <id>       - delete a grave and drop its items
 * /grave admin [player]    - open another player's graves GUI (admin)
 * /grave reload            - reload config (admin)
 * /grave clearall <player> - remove all graves for a player (admin)
 */

public class GraveCommand implements CommandExecutor, TabCompleter {

    private final MourningGraves plugin;
    private final ConfigManager config;

    public GraveCommand(MourningGraves plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("\u00a7cThis command can only be used by players.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            openList(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "tp"       -> handleTp(player, args);
            case "delete"   -> handleDelete(player, args);
            case "admin"    -> handleAdmin(player, args);
            case "reload"   -> handleReload(player);
            case "clearall" -> handleClearAll(player, args);
            default         -> player.sendMessage(config.getMessage("no-permission"));
        }
        return true;
    }

    // command logic
    private void openList(Player player) {
        if (!player.hasPermission("graves.use")) {
            player.sendMessage(config.getMessage("no-permission"));
            return;
        }
        plugin.getGraveListGUI().openForPlayer(player);
    }

    private void handleTp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ConfigManager.colorize("&cUsage: /grave tp <graveId>"));
            return;
        }
        UUID graveId = parseUUID(args[1]);
        if (graveId == null) { player.sendMessage(config.getMessage("grave-not-found")); return; }

        plugin.getGraveManager().getGrave(graveId).ifPresentOrElse(grave -> {
            if (!grave.getOwnerUUID().equals(player.getUniqueId()) && !player.hasPermission("graves.admin")) {
                player.sendMessage(config.getMessage("no-permission"));
                return;
            }
            Location loc = grave.getLocation();
            if (loc != null) player.teleport(loc.add(0.5, 1, 0.5));
        }, () -> player.sendMessage(config.getMessage("grave-not-found")));
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ConfigManager.colorize("&cUsage: /grave delete <graveId>"));
            return;
        }
        UUID graveId = parseUUID(args[1]);
        if (graveId == null) { player.sendMessage(config.getMessage("grave-not-found")); return; }

        plugin.getGraveManager().getGrave(graveId).ifPresentOrElse(grave -> {
            if (!grave.getOwnerUUID().equals(player.getUniqueId()) && !player.hasPermission("graves.admin")) {
                player.sendMessage(config.getMessage("no-permission"));
                return;
            }
            plugin.getGraveManager().expireGrave(grave);
            player.sendMessage(ConfigManager.colorize("&aGrave deleted and items dropped."));
        }, () -> player.sendMessage(config.getMessage("grave-not-found")));
    }

    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("graves.admin")) {
            player.sendMessage(config.getMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            // Print server-wide summary
            var summary = plugin.getGraveManager().getAllGraves().stream()
                    .collect(Collectors.groupingBy(Grave::getOwnerName, Collectors.counting()));

            if (summary.isEmpty()) {
                player.sendMessage(ConfigManager.colorize("&7No active graves on the server."));
                return;
            }
            player.sendMessage(ConfigManager.colorize("&6Active graves:"));
            summary.forEach((name, count) ->
                    player.sendMessage(ConfigManager.colorize("  &e" + name + " &7\u2014 " + count + " grave(s)")));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ConfigManager.colorize("&cPlayer not found or not online."));
            return;
        }
        plugin.getGraveListGUI().openForAdmin(player, target);
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("graves.admin")) {
            player.sendMessage(config.getMessage("no-permission"));
            return;
        }
        config.reload();
        player.sendMessage(ConfigManager.colorize("&aMourningGraves config reloaded."));
    }

    private void handleClearAll(Player player, String[] args) {
        if (!player.hasPermission("graves.admin")) {
            player.sendMessage(config.getMessage("no-permission"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ConfigManager.colorize("&cUsage: /grave clearall <player>"));
            return;
        }
        String targetName = args[1];
        plugin.getGraveManager().getAllGraves().stream()
                .filter(g -> g.getOwnerName().equalsIgnoreCase(targetName))
                .toList()
                .forEach(g -> plugin.getGraveManager().expireGrave(g));

        player.sendMessage(config.getMessage("admin-cleared").replace("{player}", targetName));
    }

    // command tab completion
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("list", "tp", "delete"));
            if (player.hasPermission("graves.admin"))
                subs.addAll(List.of("admin", "reload", "clearall"));
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();

            // Live grave ID completion for delete and tp
            if (sub.equals("delete") || sub.equals("tp")) {
                boolean isAdmin = player.hasPermission("graves.admin");
                return plugin.getGraveManager().getAllGraves().stream()
                        .filter(g -> isAdmin || g.getOwnerUUID().equals(player.getUniqueId()))
                        .map(g -> g.getGraveId().toString())
                        .filter(id -> id.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }

            // Player name completion for admin and clearall
            if (player.hasPermission("graves.admin")
                    && (sub.equals("admin") || sub.equals("clearall"))) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    // helper
    private UUID parseUUID(String s) {
        try { return UUID.fromString(s); }
        catch (IllegalArgumentException e) { return null; }
    }
}