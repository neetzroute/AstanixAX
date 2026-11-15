package dev.astanix.axray.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class OreCluster {
    private final long id;
    private final UUID playerUuid;
    private final String playerName;
    private final String worldName;
    private final OreType oreType;
    private double centerX;
    private double centerY;
    private double centerZ;
    private int blockCount;
    private boolean clean;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<OreBlock> blocks;

    public OreCluster(long id, UUID playerUuid, String playerName, String worldName, OreType oreType,
                      double centerX, double centerY, double centerZ, int blockCount, boolean clean,
                      Instant createdAt, Instant updatedAt, List<OreBlock> blocks) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.worldName = worldName;
        this.oreType = oreType;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.blockCount = blockCount;
        this.clean = clean;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.blocks = blocks != null ? new ArrayList<>(blocks) : new ArrayList<>();
    }

    public long getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getWorldName() {
        return worldName;
    }

    public OreType getOreType() {
        return oreType;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public boolean isClean() {
        return clean;
    }

    public void setClean(boolean clean) {
        this.clean = clean;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<OreBlock> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public synchronized void addBlock(OreBlock block) {
        blocks.add(block);
        this.blockCount = blocks.size();
        this.updatedAt = block.minedAt();
        recalculateCenter();
    }

    private void recalculateCenter() {
        if (blocks.isEmpty()) return;
        double sumX = 0;
        double sumY = 0;
        double sumZ = 0;
        for (OreBlock b : blocks) {
            sumX += b.x();
            sumY += b.y();
            sumZ += b.z();
        }
        this.centerX = sumX / blocks.size();
        this.centerY = sumY / blocks.size();
        this.centerZ = sumZ / blocks.size();
    }

    public Location getCenterLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, centerX, centerY, centerZ);
    }

    public boolean isNearby(String world, int targetX, int targetY, int targetZ, double maxRadius) {
        if (!this.worldName.equalsIgnoreCase(world)) return false;
        double maxRadiusSq = maxRadius * maxRadius;

        // Check distance to cluster center
        double dx = this.centerX - targetX;
        double dy = this.centerY - targetY;
        double dz = this.centerZ - targetZ;
        if ((dx * dx + dy * dy + dz * dz) <= maxRadiusSq) {
            return true;
        }

        // Check distance to any existing block in the cluster (chain connection)
        for (OreBlock b : blocks) {
            if (b.distanceSquared(targetX, targetY, targetZ) <= maxRadiusSq) {
                return true;
            }
        }
        return false;
    }
}
