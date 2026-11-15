package dev.astanix.axray.model;

import java.time.Instant;
import java.util.UUID;

public record PlayerOreStats(
        UUID playerUuid,
        String playerName,
        int diamondCount,
        int debrisCount,
        int emeraldCount,
        int totalCount,
        int clusterCount,
        int unreviewedClusterCount,
        Instant lastMinedAt
) {
    public int getOreCount(OreType type) {
        return switch (type) {
            case DIAMOND -> diamondCount;
            case DEBRIS -> debrisCount;
            case EMERALD -> emeraldCount;
        };
    }
}
