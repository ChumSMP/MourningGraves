package org.Mellurboo.mourningGraves.gui;

import org.Mellurboo.mourningGraves.MourningGraves;
import org.Mellurboo.mourningGraves.config.ConfigManager;
import org.Mellurboo.mourningGraves.grave.Grave;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.*;

public class GraveListGUI implements Listener {

    // Hidden tag embedded in lore to identify which grave an item represents
    private static final String GRAVE_TAG = "\u00a70GRAVE:";

    private final MourningGraves plugin;
    private final ConfigManager config;
    private final Map<Inventory, GUISession> sessions = new WeakHashMap<>();

    public GraveListGUI(MourningGraves plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // open GUI

    public void openForPlayer(Player viewer) {
        openFor(viewer, viewer, false);
    }

    public void openForAdmin(Player admin, Player target) {
        openFor(admin, target, true);
    }

    private void openFor(Player viewer, Player target, boolean isAdmin) {
        List<Grave> graves = plugin.getGraveManager().getGravesFor(target.getUniqueId());

        String rawTitle = isAdmin
                ? config.getGuiAdminTitle().replace("{player}", target.getName())
                : config.getGuiTitle();
        String title = ConfigManager.colorize(rawTitle);

        int rows = config.getGuiRows();
        Inventory inv = Bukkit.createInventory(null, rows * 9, title);

        // Background filler
        ItemStack filler = nameless(new ItemStack(config.getFillerItemMaterial()));
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        if (graves.isEmpty()) {
            ItemStack empty = new ItemStack(config.getEmptyItemMaterial());
            ItemMeta m = empty.getItemMeta();
            m.setDisplayName(ConfigManager.colorize(config.getEmptyName()));
            m.setLore(Collections.emptyList());
            empty.setItemMeta(m);
            inv.setItem(inv.getSize() / 2, empty);
        } else {
            int slot = 10;
            for (Grave grave : graves) {
                if (slot >= inv.getSize() - 9) break;
                inv.setItem(slot, buildGraveItem(grave, isAdmin));
                slot++;
                if ((slot + 1) % 9 == 0) slot += 2; // skip border for vanity
            }
        }

        sessions.put(inv, new GUISession(viewer, target, isAdmin));
        viewer.openInventory(inv);
    }

    // gui collect handler
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player viewer)) return;
        GUISession session = sessions.get(e.getInventory());
        if (session == null) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (!clicked.hasItemMeta() || !clicked.getItemMeta().hasLore()) return;

        UUID graveId = extractGraveId(clicked);
        if (graveId == null) return;

        plugin.getGraveManager().getGrave(graveId).ifPresent(grave -> {
            viewer.closeInventory();
            if (session.isAdmin) {
                // Admins collect directly from the GUI
                plugin.getGraveManager().collectGrave(viewer, grave);
            } else {
                if (!config.isGuiCollectEnabled()) {
                    viewer.sendMessage(config.getMessage("gui-collect-disabled"));
                    return;
                }
                // Check the player is the owner or has others permission
                boolean isOwner = grave.getOwnerUUID().equals(viewer.getUniqueId());
                boolean canOthers = config.isAllowOthersCollect()
                        && viewer.hasPermission("graves.others");
                if (!isOwner && !canOthers) {
                    viewer.sendMessage(config.getMessage("no-permission"));
                    return;
                }
                plugin.getGraveManager().collectGrave(viewer, grave);
            }
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (sessions.containsKey(e.getInventory())) e.setCancelled(true);
    }

    // builders

    private ItemStack buildGraveItem(Grave grave, boolean isAdmin) {
        ItemStack item = new ItemStack(config.getGraveItemMaterial());
        ItemMeta meta  = item.getItemMeta();

        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(grave.getCreatedAt()));
        Location loc = grave.getLocation();
        String coords = loc != null
                ? loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                : "unknown";

        meta.setDisplayName(ConfigManager.colorize("&f" + grave.getOwnerName() + "'s Grave"));

        List<String> lore = new ArrayList<>();
        lore.add(ConfigManager.colorize("&7World: &e"   + grave.getWorldName()));
        lore.add(ConfigManager.colorize("&7Location: &e" + coords));
        lore.add(ConfigManager.colorize("&7Items: &e"   + grave.getItems().stream().filter(Objects::nonNull).count()));
        lore.add(ConfigManager.colorize("&7XP: &e"      + grave.getExperience()));
        lore.add(ConfigManager.colorize("&7Created: &e" + date));
        lore.add(ConfigManager.colorize("&7Expires: &e" + grave.timeRemainingFormatted()));
        lore.add("");
        if (isAdmin) {
            lore.add(ConfigManager.colorize("&eClick to teleport"));
        } else if (!config.isGuiCollectEnabled()) {
            lore.add(ConfigManager.colorize("&7Collect at the grave location"));
        } else {
            int cost = config.getCollectXpCostLevels();
            if (cost > 0) {
                lore.add(ConfigManager.colorize("&eClick to collect &7(&b" + cost + " level" + (cost == 1 ? "" : "s") + "&7)"));
            } else {
                lore.add(ConfigManager.colorize("&eClick to collect"));
            }
        }
        lore.add(GRAVE_TAG + grave.getGraveId()); // hidden ID for lookup

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private UUID extractGraveId(ItemStack item) {
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) return null;
        for (String line : lore) {
            if (line.startsWith(GRAVE_TAG)) {
                try { return UUID.fromString(line.substring(GRAVE_TAG.length())); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    private ItemStack nameless(ItemStack item) {
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(" ");
        item.setItemMeta(m);
        return item;
    }

    private record GUISession(Player viewer, Player target, boolean isAdmin) {}
}