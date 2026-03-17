package org.Mellurboo.mourningGraves.grave;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
public class Grave {

    private final UUID graveId;
    private final UUID ownerUUID;
    private final String ownerName;

    private final String worldName;
    private final double x, y, z;
    private final float yaw;

    private final List<ItemStack> items;
    private final int experience;

    private final long createdAt;
    private final long expiresAt;  // -1 = never

    // Serialized BlockData string of the block that was here before the grave
    private String originalBlockData;

    // Flipped true when the grave is collected or expired so that particle/bob
    private volatile boolean removed = false;

    // used when creating a new grave on death
    public Grave(UUID ownerUUID, String ownerName, Location location,
                 List<ItemStack> items, int experience, long expirySeconds) {
        this.graveId    = UUID.randomUUID();
        this.ownerUUID  = ownerUUID;
        this.ownerName  = ownerName;
        this.worldName  = location.getWorld().getName();
        this.x          = location.getX();
        this.y          = location.getY();
        this.z          = location.getZ();
        this.yaw        = location.getYaw();
        this.items      = items;
        this.experience = experience;
        this.createdAt  = System.currentTimeMillis();
        this.expiresAt  = expirySeconds < 0 ? -1L : createdAt + expirySeconds * 1000L;
    }

    // Used when deserializing from YAML storage
    public Grave(UUID graveId, UUID ownerUUID, String ownerName,
                 String worldName, double x, double y, double z, float yaw,
                 List<ItemStack> items, int experience,
                 long createdAt, long expiresAt) {
        this.graveId    = graveId;
        this.ownerUUID  = ownerUUID;
        this.ownerName  = ownerName;
        this.worldName  = worldName;
        this.x          = x;
        this.y          = y;
        this.z          = z;
        this.yaw        = yaw;
        this.items      = items;
        this.experience = experience;
        this.createdAt  = createdAt;
        this.expiresAt  = expiresAt;
    }

    // getters
    public UUID getGraveId()     { return graveId; }
    public UUID getOwnerUUID()   { return ownerUUID; }
    public String getOwnerName() { return ownerName; }
    public String getWorldName() { return worldName; }
    public List<ItemStack> getItems() { return items; }
    public int getExperience()   { return experience; }
    public long getCreatedAt()   { return createdAt; }
    public long getExpiresAt()   { return expiresAt; }

    public String getOriginalBlockData()          { return originalBlockData; }
    public void setOriginalBlockData(String data) { this.originalBlockData = data; }

    public boolean isRemoved() { return removed; }
    public void markRemoved()  { this.removed = true; }

    // True if this grave is done for any reason (collected OR expired)
    public boolean isDone()    { return removed || isExpired(); }

    public Location getLocation() {
        var world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z, yaw, 0);
    }

    public boolean isExpired() {
        return expiresAt != -1L && System.currentTimeMillis() > expiresAt;
    }

    public long secondsRemaining() {
        if (expiresAt == -1L) return -1L;
        return Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000L);
    }

    public String timeRemainingFormatted() {
        long secs = secondsRemaining();
        if (secs < 0) return "\u221e";
        long m = secs / 60, s = secs % 60;
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}