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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HighlightService implements Listener {
    private static final String TEAM_NAME = "ax_glow_yellow";
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
            List<Entity> glowingEntities,
            Instant startTime
    ) {}

    public HighlightService(Plugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        getOrCreateYellowTeam();
    }

    private Team getOrCreateYellowTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(TEAM_NAME);
        }
        team.color(NamedTextColor.YELLOW);
        return team;
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
            List<Entity> glowingEntities = new ArrayList<>();
            Team yellowTeam = getOrCreateYellowTeam();

            // Send packet block updates and spawn client-only yellow glowing bounding boxes
            for (OreBlock oreBlock : blocks) {
                Location loc = oreBlock.toLocation();
                if (loc != null && loc.getWorld() != null) {
                    highlightedLocs.add(loc);

                    // Client-side block appearance (GOLD_BLOCK)
                    admin.sendBlockChange(loc, fakeBlockData);

                    // Client-only glowing box visible through walls
                    Location entityLoc = loc.clone().add(0.5, 0.0, 0.5);
                    try {
                        Shulker box = loc.getWorld().spawn(entityLoc, Shulker.class, s -> {
                            s.setVisibleByDefault(false);
                            s.setInvisible(true);
                            s.setGlowing(true);
                            s.setAI(false);
                            s.setInvulnerable(true);
                            s.setSilent(true);
                            s.setPersistent(false);
                            s.setCollidable(false);
                            s.setPeek(0.0f);
                            s.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 1, false, false, false));
                        });

                        yellowTeam.addEntity(box);
                        admin.showEntity(plugin, box);
                        glowingEntities.add(box);
                    } catch (Exception ignored) {
                    }
                }
            }

            InspectionSession session = new InspectionSession(
                    admin.getUniqueId(),
                    cluster.getId(),
                    cluster.getPlayerName(),
                    prevLoc,
                    prevMode,
                    highlightedLocs,
                    glowingEntities,
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
                            .hoverEvent(HoverEvent.showText(Component.text("Пометить чистой, выйти из спектатора и вернуться на исходное место"))))
                    .append(Component.text("  "))
                    .append(Component.text("[✖ Выйти из инспекции]")
                            .color(NamedTextColor.RED)
                            .decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/ax clear"))
                            .hoverEvent(HoverEvent.showText(Component.text("Сбросить подсветку, выйти из спектатора и вернуться назад"))))
                    .append(Component.text("  "))
                    .append(Component.text("[☰ Меню игрока]")
                            .color(NamedTextColor.AQUA)
                            .decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/ax inspect " + cluster.getPlayerName()))
                            .hoverEvent(HoverEvent.showText(Component.text("Открыть меню всех кладок этого игрока"))));

            admin.sendMessage(controlBar);
            admin.playSound(admin.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        });
    }

    public boolean clearHighlight(Player admin, boolean notify) {
        InspectionSession session = activeSessions.remove(admin.getUniqueId());
        if (session == null) {
            return false;
        }

        Team yellowTeam = getOrCreateYellowTeam();

        // Remove glowing outline entities
        for (Entity entity : session.glowingEntities()) {
            if (entity != null && entity.isValid()) {
                yellowTeam.removeEntity(entity);
                entity.remove();
            }
        }

        // Restore original block states to the admin client
        for (Location loc : session.highlightedLocations()) {
            if (loc.isWorldLoaded()) {
                admin.sendBlockChange(loc, loc.getBlock().getBlockData());
            }
        }

        // Teleport admin back to the location from which they entered inspection
        if (session.previousLocation() != null) {
            admin.teleportAsync(session.previousLocation());
        }

        // Restore previous gamemode (exit spectator mode)
        if (session.previousGameMode() != null) {
            admin.setGameMode(session.previousGameMode());
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
        Team yellowTeam = getOrCreateYellowTeam();
        for (UUID uuid : new ArrayList<>(activeSessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                clearHighlight(player, false);
            } else {
                InspectionSession session = activeSessions.remove(uuid);
                if (session != null) {
                    for (Entity entity : session.glowingEntities()) {
                        if (entity != null && entity.isValid()) {
                            yellowTeam.removeEntity(entity);
                            entity.remove();
                        }
                    }
                }
            }
        }
        activeSessions.clear();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        InspectionSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            Team yellowTeam = getOrCreateYellowTeam();
            for (Entity entity : session.glowingEntities()) {
                if (entity != null && entity.isValid()) {
                    yellowTeam.removeEntity(entity);
                    entity.remove();
                }
            }
            // Restore gamemode before quit
            if (session.previousGameMode() != null) {
                player.setGameMode(session.previousGameMode());
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (activeSessions.containsKey(player.getUniqueId())) {
            clearHighlight(player, true);
        }
    }
}
