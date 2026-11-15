package dev.astanix.axray.storage;

import dev.astanix.axray.model.OreBlock;
import dev.astanix.axray.model.OreCluster;
import dev.astanix.axray.model.OreType;
import dev.astanix.axray.model.PlayerOreStats;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OreRepository {
    private final DatabaseManager dbManager;
    private final Plugin plugin;
    private final Logger logger;

    public OreRepository(DatabaseManager dbManager, Plugin plugin, Logger logger) {
        this.dbManager = dbManager;
        this.plugin = plugin;
        this.logger = logger;
    }

    private <T> CompletableFuture<T> supplyAsync(ThrowingSupplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "[Astanix-AX] Error executing async database task", t);
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private CompletableFuture<Void> runAsync(ThrowingRunnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "[Astanix-AX] Error executing async database task", t);
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public CompletableFuture<Long> createCluster(OreCluster cluster) {
        return supplyAsync(() -> {
            String sql = """
                INSERT INTO clusters (player_uuid, player_name, world, ore_type, center_x, center_y, center_z, block_count, is_clean, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """;
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, cluster.getPlayerUuid().toString());
                stmt.setString(2, cluster.getPlayerName());
                stmt.setString(3, cluster.getWorldName());
                stmt.setString(4, cluster.getOreType().name());
                stmt.setDouble(5, cluster.getCenterX());
                stmt.setDouble(6, cluster.getCenterY());
                stmt.setDouble(7, cluster.getCenterZ());
                stmt.setInt(8, cluster.getBlockCount());
                stmt.setBoolean(9, cluster.isClean());
                stmt.setTimestamp(10, Timestamp.from(cluster.getCreatedAt()));
                stmt.setTimestamp(11, Timestamp.from(cluster.getUpdatedAt()));
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
            throw new SQLException("Failed to retrieve generated key for cluster");
        });
    }

    public CompletableFuture<Void> updateCluster(long clusterId, double centerX, double centerY, double centerZ, int blockCount, Instant updatedAt) {
        return runAsync(() -> {
            String sql = """
                UPDATE clusters
                SET center_x = ?, center_y = ?, center_z = ?, block_count = ?, updated_at = ?
                WHERE id = ?;
            """;
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setDouble(1, centerX);
                stmt.setDouble(2, centerY);
                stmt.setDouble(3, centerZ);
                stmt.setInt(4, blockCount);
                stmt.setTimestamp(5, Timestamp.from(updatedAt));
                stmt.setLong(6, clusterId);
                stmt.executeUpdate();
            }
        });
    }

    public CompletableFuture<Long> insertOreBlock(OreBlock block) {
        return supplyAsync(() -> {
            String sql = """
                INSERT INTO ore_blocks (cluster_id, player_uuid, world, x, y, z, ore_type, mined_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?);
            """;
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, block.clusterId());
                stmt.setString(2, block.playerUuid().toString());
                stmt.setString(3, block.worldName());
                stmt.setInt(4, block.x());
                stmt.setInt(5, block.y());
                stmt.setInt(6, block.z());
                stmt.setString(7, block.oreType().name());
                stmt.setTimestamp(8, Timestamp.from(block.minedAt()));
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
            throw new SQLException("Failed to retrieve generated key for ore_block");
        });
    }

    public CompletableFuture<Void> setClusterClean(long clusterId, boolean clean) {
        return runAsync(() -> {
            String sql = "UPDATE clusters SET is_clean = ? WHERE id = ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setBoolean(1, clean);
                stmt.setLong(2, clusterId);
                stmt.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<PlayerOreStats>> getTopPlayers(int limit, int offset) {
        return supplyAsync(() -> {
            String sql = """
                SELECT 
                    player_uuid,
                    player_name,
                    SUM(CASE WHEN ore_type = 'DIAMOND' THEN block_count ELSE 0 END) AS diamond_count,
                    SUM(CASE WHEN ore_type = 'DEBRIS' THEN block_count ELSE 0 END) AS debris_count,
                    SUM(CASE WHEN ore_type = 'EMERALD' THEN block_count ELSE 0 END) AS emerald_count,
                    SUM(block_count) AS total_count,
                    COUNT(*) AS total_clusters,
                    SUM(CASE WHEN is_clean = 0 THEN 1 ELSE 0 END) AS unreviewed_clusters,
                    MAX(updated_at) AS last_mined
                FROM clusters
                GROUP BY player_uuid, player_name
                ORDER BY total_count DESC
                LIMIT ? OFFSET ?;
            """;

            List<PlayerOreStats> list = new ArrayList<>();
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                stmt.setInt(2, offset);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                        String name = rs.getString("player_name");
                        int diamonds = rs.getInt("diamond_count");
                        int debris = rs.getInt("debris_count");
                        int emeralds = rs.getInt("emerald_count");
                        int total = rs.getInt("total_count");
                        int totalClusters = rs.getInt("total_clusters");
                        int unreviewedClusters = rs.getInt("unreviewed_clusters");
                        Timestamp ts = rs.getTimestamp("last_mined");
                        Instant lastMined = ts != null ? ts.toInstant() : Instant.EPOCH;

                        list.add(new PlayerOreStats(uuid, name, diamonds, debris, emeralds, total, totalClusters, unreviewedClusters, lastMined));
                    }
                }
            }
            return list;
        });
    }

    public CompletableFuture<List<OreCluster>> getPlayerClusters(UUID playerUuid, OreType filterType, Boolean filterClean) {
        return supplyAsync(() -> {
            StringBuilder sql = new StringBuilder("SELECT * FROM clusters WHERE player_uuid = ?");
            if (filterType != null) {
                sql.append(" AND ore_type = ?");
            }
            if (filterClean != null) {
                sql.append(" AND is_clean = ?");
            }
            sql.append(" ORDER BY id DESC");

            List<OreCluster> clusters = new ArrayList<>();
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                int paramIdx = 1;
                stmt.setString(paramIdx++, playerUuid.toString());
                if (filterType != null) {
                    stmt.setString(paramIdx++, filterType.name());
                }
                if (filterClean != null) {
                    stmt.setBoolean(paramIdx++, filterClean);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        String name = rs.getString("player_name");
                        String world = rs.getString("world");
                        OreType oreType = OreType.valueOf(rs.getString("ore_type"));
                        double cx = rs.getDouble("center_x");
                        double cy = rs.getDouble("center_y");
                        double cz = rs.getDouble("center_z");
                        int count = rs.getInt("block_count");
                        boolean clean = rs.getBoolean("is_clean");
                        Instant created = rs.getTimestamp("created_at").toInstant();
                        Instant updated = rs.getTimestamp("updated_at").toInstant();

                        clusters.add(new OreCluster(id, playerUuid, name, world, oreType, cx, cy, cz, count, clean, created, updated, null));
                    }
                }
            }
            return clusters;
        });
    }

    public CompletableFuture<List<OreBlock>> getClusterBlocks(long clusterId) {
        return supplyAsync(() -> {
            String sql = "SELECT * FROM ore_blocks WHERE cluster_id = ? ORDER BY id ASC";
            List<OreBlock> blocks = new ArrayList<>();
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, clusterId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                        String world = rs.getString("world");
                        int x = rs.getInt("x");
                        int y = rs.getInt("y");
                        int z = rs.getInt("z");
                        OreType oreType = OreType.valueOf(rs.getString("ore_type"));
                        Instant minedAt = rs.getTimestamp("mined_at").toInstant();
                        blocks.add(new OreBlock(id, clusterId, playerUuid, world, x, y, z, oreType, minedAt));
                    }
                }
            }
            return blocks;
        });
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
