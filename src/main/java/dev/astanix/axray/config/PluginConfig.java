package dev.astanix.axray.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PluginConfig {
    private final String dbFile;
    private final int maxPoolSize;
    private final int minIdle;
    private final long idleTimeoutMs;
    private final long connectionTimeoutMs;

    private final boolean ignoreCreative;
    private final boolean ignoreSilkTouch;
    private final Set<String> monitoredWorlds;
    private final double clusterRadius;
    private final long clusterTimeWindowMinutes;

    private final Material highlightMaterial;
    private final boolean spectatorOnTeleport;
    private final boolean savePreviousState;
    private final int autoClearMinutes;

    private final String prefix;
    private final String noPermission;
    private final String teleportedToCluster;
    private final String teleportHint;
    private final String highlightCleared;
    private final String clusterCleanMarked;
    private final String clusterUncleanMarked;
    private final String playerNotFound;
    private final String configReloaded;

    public PluginConfig(FileConfiguration config) {
        this.dbFile = config.getString("database.file", "axray_data.db");
        this.maxPoolSize = config.getInt("database.maximum-pool-size", 10);
        this.minIdle = config.getInt("database.minimum-idle", 2);
        this.idleTimeoutMs = config.getLong("database.idle-timeout-ms", 300000);
        this.connectionTimeoutMs = config.getLong("database.connection-timeout-ms", 10000);

        this.ignoreCreative = config.getBoolean("tracker.ignore-creative", true);
        this.ignoreSilkTouch = config.getBoolean("tracker.ignore-silk-touch", false);
        List<String> worlds = config.getStringList("tracker.monitored-worlds");
        this.monitoredWorlds = new HashSet<>(worlds);
        this.clusterRadius = config.getDouble("tracker.cluster-radius", 3.5);
        this.clusterTimeWindowMinutes = config.getLong("tracker.cluster-time-window-minutes", 15);

        String matName = config.getString("inspection.highlight-material", "GOLD_BLOCK");
        Material mat = Material.matchMaterial(matName);
        this.highlightMaterial = (mat != null && mat.isBlock()) ? mat : Material.GOLD_BLOCK;

        this.spectatorOnTeleport = config.getBoolean("inspection.spectator-on-teleport", true);
        this.savePreviousState = config.getBoolean("inspection.save-previous-state", true);
        this.autoClearMinutes = config.getInt("inspection.auto-clear-minutes", 30);

        this.prefix = config.getString("messages.prefix", "<gradient:#00D2FF:#3A7BD5><bold>[Astanix-AX]</bold></gradient> ");
        this.noPermission = config.getString("messages.no-permission", "<red>У вас нет прав для выполнения этой команды!</red>");
        this.teleportedToCluster = config.getString("messages.teleported-to-cluster", "<green>Телепортация к кладке <white>#{id}</white> игрока <yellow>{player}</yellow>. Блоки подсвечены золотом!</green>");
        this.teleportHint = config.getString("messages.teleport-hint", "<gray>Для сброса подсветки введите <click:run_command:'/ax clear'><yellow><u>/ax clear</u></yellow></click> или нажмите кнопку в GUI.</gray>");
        this.highlightCleared = config.getString("messages.highlight-cleared", "<gray>Подсветка блоков успешно снята, оригинальный вид восстановлен.</gray>");
        this.clusterCleanMarked = config.getString("messages.cluster-clean-marked", "<green>Кладка <white>#{id}</white> помечена как <bold>ЧИСТАЯ</bold> ✔</green>");
        this.clusterUncleanMarked = config.getString("messages.cluster-unclean-marked", "<yellow>С кладки <white>#{id}</white> снята отметка чистоты ⚠</yellow>");
        this.playerNotFound = config.getString("messages.player-not-found", "<red>Игрок не найден или не имеет записей о добыче.</red>");
        this.configReloaded = config.getString("messages.config-reloaded", "<green>Конфигурация успешно перезагружена!</green>");
    }

    public String getDbFile() { return dbFile; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public int getMinIdle() { return minIdle; }
    public long getIdleTimeoutMs() { return idleTimeoutMs; }
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public boolean isIgnoreCreative() { return ignoreCreative; }
    public boolean isIgnoreSilkTouch() { return ignoreSilkTouch; }
    public boolean isWorldMonitored(String worldName) {
        return monitoredWorlds.isEmpty() || monitoredWorlds.contains(worldName);
    }
    public double getClusterRadius() { return clusterRadius; }
    public long getClusterTimeWindowMinutes() { return clusterTimeWindowMinutes; }
    public Material getHighlightMaterial() { return highlightMaterial; }
    public boolean isSpectatorOnTeleport() { return spectatorOnTeleport; }
    public boolean isSavePreviousState() { return savePreviousState; }
    public int getAutoClearMinutes() { return autoClearMinutes; }
    public String getPrefix() { return prefix; }
    public String getNoPermission() { return noPermission; }
    public String getTeleportedToCluster() { return teleportedToCluster; }
    public String getTeleportHint() { return teleportHint; }
    public String getHighlightCleared() { return highlightCleared; }
    public String getClusterCleanMarked() { return clusterCleanMarked; }
    public String getClusterUncleanMarked() { return clusterUncleanMarked; }
    public String getPlayerNotFound() { return playerNotFound; }
    public String getConfigReloaded() { return configReloaded; }
}
