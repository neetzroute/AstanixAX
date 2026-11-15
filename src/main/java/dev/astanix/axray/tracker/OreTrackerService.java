package dev.astanix.axray.tracker;

import dev.astanix.axray.config.PluginConfig;
import dev.astanix.axray.model.OreBlock;
import dev.astanix.axray.model.OreCluster;
import dev.astanix.axray.model.OreType;
import dev.astanix.axray.storage.OreRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OreTrackerService {
    private final Plugin plugin;
    private final PluginConfig config;
    private final OreRepository repository;
    private final Logger logger;

    // In-memory cache of recent clusters per player for fast, non-blocking spatial grouping
    private final Map<UUID, List<OreCluster>> recentPlayerClusters = new ConcurrentHashMap<>();

    public OreTrackerService(Plugin plugin, PluginConfig config, OreRepository repository, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.logger = logger;

        startCleanupTask();
    }

    private void startCleanupTask() {
        // Run periodic cleanup every 5 minutes using modern Paper scheduled executor
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> {
            Instant threshold = Instant.now().minus(Duration.ofMinutes(config.getClusterTimeWindowMinutes()));
            recentPlayerClusters.values().forEach(list ->
                    list.removeIf(cluster -> cluster.getUpdatedAt().isBefore(threshold))
            );
            recentPlayerClusters.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }, 5, 5, java.util.concurrent.TimeUnit.MINUTES);
    }

    public void handleOreBreak(Player player, Location location, OreType oreType) {
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        String worldName = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        Instant now = Instant.now();
        double radius = config.getClusterRadius();
        Duration timeWindow = Duration.ofMinutes(config.getClusterTimeWindowMinutes());

        List<OreCluster> playerClusters = recentPlayerClusters.computeIfAbsent(playerUuid, k -> new CopyOnWriteArrayList<>());

        OreCluster matchedCluster = null;
        for (OreCluster cluster : playerClusters) {
            if (cluster.getOreType() == oreType
                    && cluster.isNearby(worldName, x, y, z, radius)
                    && Duration.between(cluster.getUpdatedAt(), now).compareTo(timeWindow) <= 0) {
                matchedCluster = cluster;
                break;
            }
        }

        if (matchedCluster != null) {
            // Append to existing cluster
            OreCluster cluster = matchedCluster;
            OreBlock oreBlock = new OreBlock(0, cluster.getId(), playerUuid, worldName, x, y, z, oreType, now);
            cluster.addBlock(oreBlock);

            // Persist changes asynchronously
            repository.insertOreBlock(oreBlock);
            repository.updateCluster(
                    cluster.getId(),
                    cluster.getCenterX(),
                    cluster.getCenterY(),
                    cluster.getCenterZ(),
                    cluster.getBlockCount(),
                    cluster.getUpdatedAt()
            );
        } else {
            // Create a new cluster
            OreCluster newCluster = new OreCluster(
                    0,
                    playerUuid,
                    playerName,
                    worldName,
                    oreType,
                    x,
                    y,
                    z,
                    1,
                    false,
                    now,
                    now,
                    null
            );

            // Asynchronously save cluster first to obtain generated ID
            repository.createCluster(newCluster).thenAccept(generatedClusterId -> {
                OreCluster persistentCluster = new OreCluster(
                        generatedClusterId,
                        playerUuid,
                        playerName,
                        worldName,
                        oreType,
                        x,
                        y,
                        z,
                        1,
                        false,
                        now,
                        now,
                        null
                );

                OreBlock oreBlock = new OreBlock(0, generatedClusterId, playerUuid, worldName, x, y, z, oreType, now);
                persistentCluster.addBlock(oreBlock);

                repository.insertOreBlock(oreBlock);
                playerClusters.add(persistentCluster);
            }).exceptionally(ex -> {
                logger.log(Level.SEVERE, "[Astanix-AX] Failed to save new ore cluster for player " + playerName, ex);
                return null;
            });
        }
    }
}
