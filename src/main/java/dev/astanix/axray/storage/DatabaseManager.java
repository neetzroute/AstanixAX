package dev.astanix.axray.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.astanix.axray.config.PluginConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {
    private final Logger logger;
    private final HikariDataSource dataSource;

    public DatabaseManager(File dataFolder, PluginConfig config, Logger logger) {
        this.logger = logger;

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dbFile = new File(dataFolder, config.getDbFile());
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("AstanixAX-SQLite-Pool");
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMinIdle());
        hikariConfig.setIdleTimeout(config.getIdleTimeoutMs());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
        
        // SQLite optimization pragmas
        hikariConfig.addDataSourceProperty("journal_mode", "WAL");
        hikariConfig.addDataSourceProperty("synchronous", "NORMAL");
        hikariConfig.addDataSourceProperty("foreign_keys", "ON");

        this.dataSource = new HikariDataSource(hikariConfig);
        initTables();
    }

    private void initTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clusters (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(32) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    ore_type VARCHAR(32) NOT NULL,
                    center_x DOUBLE NOT NULL,
                    center_y DOUBLE NOT NULL,
                    center_z DOUBLE NOT NULL,
                    block_count INTEGER NOT NULL DEFAULT 1,
                    is_clean BOOLEAN NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                );
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ore_blocks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    cluster_id INTEGER NOT NULL REFERENCES clusters(id) ON DELETE CASCADE,
                    player_uuid VARCHAR(36) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    ore_type VARCHAR(32) NOT NULL,
                    mined_at TIMESTAMP NOT NULL
                );
            """);

            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_clusters_player ON clusters(player_uuid);");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_clusters_clean ON clusters(is_clean);");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_clusters_ore ON clusters(ore_type);");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_blocks_cluster ON ore_blocks(cluster_id);");

            logger.info("[Astanix-AX] Database tables and indexes successfully initialized.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Astanix-AX] Failed to initialize SQLite tables!", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("[Astanix-AX] Database connection pool closed.");
        }
    }
}
