package dev.astanix.axray.highlight;

import dev.astanix.axray.config.PluginConfig;
import dev.astanix.axray.model.OreBlock;
import dev.astanix.axray.model.OreCluster;
import dev.astanix.axray.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HighlightService implements Listener {
    private final Plugin plugin;
    private final PluginConfig config;
    private final Map<UUID, InspectionSession> activeSessions = new ConcurrentHashMap<>();

    public record InspectionSession(
            UUID adminUuid,
            long clusterId,
            String playerName,
            Location previousLocation,
            GameMode previousGameMode,
            Set<Location> highlightedLocations,
            Instant startTime
    ) {}

    public HighlightService(Plugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void startInspection(Player admin, OreCluster cluster, List<OreBlock> blocks) {
        // Clear previous session if present
        if (activeSessions.containsKey(admin.getUniqueId())) {
            clearHighlight(admin, false);
        }

        Location center = cluster.getCenterLocation();
        if (center == null || center.getWorld() == null) {
            admin.sendMessage(TextUtil.parse(config.getPrefix() + "<red>Мир этой кладки недоступен или выгружен!</red>"));
            return;
        }

        Location prevLoc = admin.getLocation();
        GameMode prevMode = admin.getGameMode();

        // Safe vantage point slightly offset from center
        Location tpLoc = center.clone().add(0.5, 1.0, 0.5);

        admin.teleportAsync(tpLoc).thenAccept(success -> {
            if (!success) {
                admin.sendMessage(TextUtil.parse(config.getPrefix() + "<red>Не удалось телепортироваться к кладке!</red>"));
                return;
            }

            if (config.isSpectatorOnTeleport()) {
                admin.setGameMode(GameMode.SPECTATOR);
            }

            BlockData fakeBlockData = Bukkit.createBlockData(config.getHighlightMaterial());
            Set<Location> highlightedLocs = new HashSet<>();

            // Send packet block updates exclusively to this admin
            for (OreBlock oreBlock : blocks) {
                Location loc = oreBlock.toLocation();
                if (loc != null) {
                    highlightedLocs.add(loc);
                    admin.sendBlockChange(loc, fakeBlockData);
                }
            }

            InspectionSession session = new InspectionSession(
                    admin.getUniqueId(),
                    cluster.getId(),
                    cluster.getPlayerName(),
                    prevLoc,
                    prevMode,
                    highlightedLocs,
                    Instant.now()
            );
            activeSessions.put(admin.getUniqueId(), session);

            // Inform admin with action buttons
            Map<String, String> placeholders = Map.of(
                    "id", String.valueOf(cluster.getId()),
                    "player", cluster.getPlayerName()
            );
            admin.sendMessage(TextUtil.parse(config.getPrefix() + config.getTeleportedToCluster(), placeholders));

            // Interactive clickable control bar
            Component controlBar = Component.text("   ")
                    .append(Component.text("[✔ Пометить ЧИСТО]")
                            .color(NamedTextColor.GREEN)
                            .decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/ax markclean " + cluster.getId()))
                            .hoverEvent(HoverEvent.showText(Component.text("Нажмите, чтобы отметить кладку проверенной"))))
                    .append(Component.text("  "))
                    .append(Component.text("[✖ Сбросить подсветку]")
                            .color(NamedTextColor.RED)
                            .decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/ax clear"))
                            .hoverEvent(HoverEvent.showText(Component.text("Нажмите, чтобы сбросить подсветку и вернуть вид"))))
                    .append(Component.text("  "))
                    .append(Component.text("[☰ Кладка игрока]")
                            .color(NamedTextColor.AQUA)
                            .decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/ax inspect " + cluster.getPlayerName()))
                            .hoverEvent(HoverEvent.showText(Component.text("Нажмите, чтобы открыть меню кладок этого игрока"))));

            admin.sendMessage(controlBar);
            admin.playSound(admin.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        });
    }

    public boolean clearHighlight(Player admin, boolean notify) {
        InspectionSession session = activeSessions.remove(admin.getUniqueId());
        if (session == null) {
            return false;
        }

        // Restore original block states to the admin client
        for (Location loc : session.highlightedLocations()) {
            if (loc.isWorldLoaded()) {
                admin.sendBlockChange(loc, loc.getBlock().getBlockData());
            }
        }

        if (config.isSpectatorOnTeleport() && admin.getGameMode() == GameMode.SPECTATOR) {
            admin.setGameMode(session.previousGameMode() != null ? session.previousGameMode() : GameMode.SURVIVAL);
        }

        if (notify) {
            admin.sendMessage(TextUtil.parse(config.getPrefix() + config.getHighlightCleared()));
            admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.0f);
        }
        return true;
    }

    public boolean hasActiveSession(UUID adminUuid) {
        return activeSessions.containsKey(adminUuid);
    }

    public InspectionSession getSession(UUID adminUuid) {
        return activeSessions.get(adminUuid);
    }

    public void clearAll() {
        for (UUID uuid : new ArrayList<>(activeSessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                clearHighlight(player, false);
            }
        }
        activeSessions.clear();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeSessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        InspectionSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            clearHighlight(player, true);
        }
    }
}
