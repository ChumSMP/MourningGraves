package org.Mellurboo.mourningGraves.grave;

import org.Mellurboo.mourningGraves.MourningGraves;
import org.Mellurboo.mourningGraves.config.ConfigManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

// Spawns and removes the Display entities that make up a graves visuals.
public class GraveRenderer {

    private final MourningGraves plugin;
    private final ConfigManager config;

    private static final float BOB_AMPLITUDE = 0.08f;
    private static final float BOB_SPEED     = 0.08f;

    public GraveRenderer(MourningGraves plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    // API
    public void spawnGrave(Grave grave) {
        Location loc = grave.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        World world = loc.getWorld();

        // Force-load the chunk so entity operations work immediately
        world.getChunkAt(loc).load(true);

        // Kill any leftover entities
        removeEntitiesByTag(world, graveTag(grave));

        Location centre = loc.clone().add(0.5, 0.0, 0.5);

        spawnBlockSetup(grave, loc);
        ItemDisplay skull = spawnSkullDisplay(grave, centre, world);
        TextDisplay text  = config.isShowNameTag() ? spawnTextDisplay(grave, centre, world) : null;

        startBobTask(grave, skull);
        if (text != null) startTextTask(grave, text);
        if (config.getGraveParticle() != null) startParticleTask(grave);
        if (config.getGraveCreateSound() != null)
            world.playSound(loc, config.getGraveCreateSound(), 1f, 0.7f);
    }

    public void removeGrave(Grave grave) {
        grave.markRemoved();

        Location loc = grave.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        World world = loc.getWorld();

        // Force-load the chunk before scanning
        world.getChunkAt(loc).load(true);

        // Remove all entities belonging to this grave
        removeEntitiesByTag(world, graveTag(grave));

        // Restore the original block
        Material current = loc.getBlock().getType();
        if (current == config.getGraveBlock()) {
            if (grave.getOriginalBlockData() != null) {
                try {
                    loc.getBlock().setBlockData(Bukkit.createBlockData(grave.getOriginalBlockData()));
                } catch (IllegalArgumentException e) {
                    loc.getBlock().setType(Material.AIR);
                }
            } else {
                loc.getBlock().setType(Material.AIR);
            }
        }
    }

    // setup grave display

    private void spawnBlockSetup(Grave grave, Location loc) {
        // Save the original block on first spawn only
        if (grave.getOriginalBlockData() == null) {
            grave.setOriginalBlockData(loc.getBlock().getBlockData().getAsString());
            plugin.getGraveManager().saveGrave(grave);
        }
        loc.getBlock().setType(config.getGraveBlock());
    }

    private ItemDisplay spawnSkullDisplay(Grave grave, Location centre, World world) {
        Location skullLoc = centre.clone().add(0, 1.4, 0);
        float correctedYaw = grave.getLocation().getYaw() + 180f;
        float yawRad = (float) Math.toRadians(-correctedYaw);

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(grave.getOwnerUUID()));
        skull.setItemMeta(meta);

        return world.spawn(skullLoc, ItemDisplay.class, entity -> {
            entity.setItemStack(skull);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setPersistent(false); // we manage persistence ourselves via tags
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.addScoreboardTag(graveTag(grave));
            entity.addScoreboardTag("mourning_grave");
            entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(yawRad, 0, 1, 0),
                    new Vector3f(0.7f, 0.7f, 0.7f),
                    new AxisAngle4f(0, 0, 1, 0)
            ));
        });
    }

    // overhead text display logic
    private TextDisplay spawnTextDisplay(Grave grave, Location centre, World world) {
        Location textLoc = centre.clone().add(0, 2.3, 0);

        return world.spawn(textLoc, TextDisplay.class, entity -> {
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setPersistent(false); // managed via tags
            entity.setBillboard(Display.Billboard.VERTICAL);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setBackgroundColor(Color.fromARGB(80, 0, 0, 0));
            entity.setSeeThrough(false);
            entity.setLineWidth(200);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.addScoreboardTag(graveTag(grave));
            entity.addScoreboardTag("mourning_grave");
            refreshText(entity, grave);
        });
    }

    private void refreshText(TextDisplay td, Grave grave) {
        String raw = config.getNameTagFormat()
                .replace("{player}", grave.getOwnerName())
                .replace("{time}", grave.timeRemainingFormatted());
        td.setText(ConfigManager.colorize(raw));
    }

    // plugin tasks
    private void startBobTask(Grave grave, ItemDisplay id) {
        float correctedYaw = grave.getLocation().getYaw() + 180f;
        float yawRad = (float) Math.toRadians(-correctedYaw);
        final float[] angle = {0f};

        new BukkitRunnable() {
            @Override public void run() {
                if (grave.isDone() || id.isDead()) { cancel(); return; }
                angle[0] += BOB_SPEED;
                float offsetY = (float) Math.sin(angle[0]) * BOB_AMPLITUDE;
                id.setTransformation(new Transformation(
                        new Vector3f(0, offsetY, 0),
                        new AxisAngle4f(yawRad, 0, 1, 0),
                        new Vector3f(0.7f, 0.7f, 0.7f),
                        new AxisAngle4f(0, 0, 1, 0)
                ));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void startTextTask(Grave grave, TextDisplay td) {
        new BukkitRunnable() {
            @Override public void run() {
                if (grave.isDone() || td.isDead()) { cancel(); return; }
                refreshText(td, grave);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startParticleTask(Grave grave) {
        new BukkitRunnable() {
            @Override public void run() {
                if (grave.isDone()) { cancel(); return; }
                Location loc = grave.getLocation();
                if (loc == null || loc.getWorld() == null) { cancel(); return; }
                Particle particle = config.getGraveParticle();
                if (particle != null)
                    loc.getWorld().spawnParticle(particle,
                            loc.clone().add(0.5, 0.8, 0.5), 6, 0.25, 0.4, 0.25, 0.005);
            }
        }.runTaskTimer(plugin, 0L, 12L);
    }

    // Unique scoreboard tag for all entities belonging to one grave
    private String graveTag(Grave grave) {
        return "mourning_" + grave.getGraveId();
    }

    // Removes all entities in the world that carry the given scoreboard tag
    private void removeEntitiesByTag(World world, String tag) {
        for (Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains(tag)) {
                entity.remove();
            }
        }
    }
}