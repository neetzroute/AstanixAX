package dev.astanix.axray.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Instant;
import java.util.UUID;

public record OreBlock(
        long id,
        long clusterId,
        UUID playerUuid,
        String worldName,
        int x,
        int y,
        int z,
        OreType oreType,
        Instant minedAt
) {
    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    public double distanceSquared(int targetX, int targetY, int targetZ) {
        long dx = (long) x - targetX;
        long dy = (long) y - targetY;
        long dz = (long) z - targetZ;
        return (dx * dx) + (dy * dy) + (dz * dz);
    }
}
