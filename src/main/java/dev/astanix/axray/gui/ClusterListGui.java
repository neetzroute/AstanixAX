package dev.astanix.axray.gui;

import dev.astanix.axray.AstanixAXPlugin;
import dev.astanix.axray.model.OreBlock;
import dev.astanix.axray.model.OreCluster;
import dev.astanix.axray.model.OreType;
import dev.astanix.axray.util.ItemBuilder;
import dev.astanix.axray.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClusterListGui extends AbstractGui {
    private static final int PAGE_SIZE = 36;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AstanixAXPlugin plugin;
    private final UUID targetPlayerUuid;
    private final String targetPlayerName;
    private final OreType filterOre;
    private final Boolean filterClean;
    private final int page;

    public ClusterListGui(AstanixAXPlugin plugin, Player player, UUID targetPlayerUuid, String targetPlayerName,
                          OreType filterOre, Boolean filterClean, int page) {
        super(player, 54, TextUtil.parse("<gradient:#00D2FF:#3A7BD5><bold>Astanix-AX</bold></gradient> <dark_gray>| " + targetPlayerName + "</dark_gray>"));
        this.plugin = plugin;
        this.targetPlayerUuid = targetPlayerUuid;
        this.targetPlayerName = targetPlayerName;
        this.filterOre = filterOre;
        this.filterClean = filterClean;
        this.page = Math.max(0, page);

        setupHeaderAndFooter();
        loadClustersAsync();
    }

    private void setupHeaderAndFooter() {
        ItemStack filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();

        // Top bar filler
        for (int i = 0; i < 9; i++) {
            setItem(i, filler);
        }

        // Bottom bar filler
        for (int i = 45; i < 54; i++) {
            setItem(i, filler);
        }

        // Back to Player List button (slot 0)
        ItemStack back = ItemBuilder.from(Material.DARK_OAK_DOOR)
                .name("<yellow>◀ Назад к списку игроков</yellow>")
                .lore("<gray>Вернуться в главное меню</gray>")
                .build();
        setItem(0, back, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            new PlayerListGui(plugin, player, 0).open();
        });

        // Filter: All Ores (slot 2)
        ItemStack filterAll = ItemBuilder.from(Material.NETHER_STAR)
                .name("<white><bold>Все руды</bold></white>")
                .glow(filterOre == null)
                .build();
        setItem(2, filterAll, e -> switchFilter(null, filterClean));

        // Filter: Diamonds (slot 3)
        ItemStack filterDiamond = ItemBuilder.from(Material.DIAMOND)
                .name("<aqua><bold>Алмазы</bold></aqua>")
                .glow(filterOre == OreType.DIAMOND)
                .build();
        setItem(3, filterDiamond, e -> switchFilter(OreType.DIAMOND, filterClean));

        // Filter: Ancient Debris (slot 4)
        ItemStack filterDebris = ItemBuilder.from(Material.NETHERITE_SCRAP)
                .name("<gold><bold>Древние обломки</bold></gold>")
                .glow(filterOre == OreType.DEBRIS)
                .build();
        setItem(4, filterDebris, e -> switchFilter(OreType.DEBRIS, filterClean));

        // Filter: Emeralds (slot 5)
        ItemStack filterEmerald = ItemBuilder.from(Material.EMERALD)
                .name("<green><bold>Изумруды</bold></green>")
                .glow(filterOre == OreType.EMERALD)
                .build();
        setItem(5, filterEmerald, e -> switchFilter(OreType.EMERALD, filterClean));

        // Filter: Clean Status (slot 7)
        String statusTitle;
        Material statusMat;
        if (filterClean == null) {
            statusTitle = "<yellow>Фильтр статуса: <white>Все кладки</white></yellow>";
            statusMat = Material.HOPPER;
        } else if (filterClean) {
            statusTitle = "<green>Фильтр статуса: <white>Только чистые ✔</white></green>";
            statusMat = Material.LIME_DYE;
        } else {
            statusTitle = "<red>Фильтр статуса: <white>Только непроверенные ⚠</white></red>";
            statusMat = Material.RED_DYE;
        }

        ItemStack statusItem = ItemBuilder.from(statusMat)
                .name(statusTitle)
                .lore(
                        "<gray>Кликните для переключения:</gray>",
                        "<gray>• Все -> Только непроверенные -> Только чистые</gray>"
                )
                .build();
        setItem(7, statusItem, e -> {
            Boolean nextClean;
            if (filterClean == null) {
                nextClean = false; // Only unreviewed
            } else if (!filterClean) {
                nextClean = true;  // Only clean
            } else {
                nextClean = null;  // All
            }
            switchFilter(filterOre, nextClean);
        });

        // Refresh (slot 8)
        ItemStack refresh = ItemBuilder.from(Material.COMPASS)
                .name("<green>↻ Обновить список</green>")
                .build();
        setItem(8, refresh, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            new ClusterListGui(plugin, player, targetPlayerUuid, targetPlayerName, filterOre, filterClean, page).open();
        });

        // Previous page button (slot 45)
        if (page > 0) {
            ItemStack prev = ItemBuilder.from(Material.ARROW)
                    .name("<yellow>◀ Предыдущая страница</yellow>")
                    .addLoreLine("<gray>Перейти на страницу " + page + "</gray>")
                    .build();
            setItem(45, prev, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                new ClusterListGui(plugin, player, targetPlayerUuid, targetPlayerName, filterOre, filterClean, page - 1).open();
            });
        }

        // Active Session Clear button (slot 49)
        if (plugin.getHighlightService().hasActiveSession(player.getUniqueId())) {
            ItemStack clear = ItemBuilder.from(Material.REDSTONE_BLOCK)
                    .name("<red><bold>✖ Сбросить подсветку блоков</bold></red>")
                    .lore("<gray>Восстановить оригинальный вид мира для вас</gray>")
                    .glow(true)
                    .build();
            setItem(49, clear, e -> {
                plugin.getHighlightService().clearHighlight(player, true);
                setupHeaderAndFooter();
            });
        }

        // Placeholder loading
        setItem(26, ItemBuilder.from(Material.STRUCTURE_VOID).name("<yellow>Загрузка кладок...</yellow>").build());
    }

    private void switchFilter(OreType newOre, Boolean newClean) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
        new ClusterListGui(plugin, player, targetPlayerUuid, targetPlayerName, newOre, newClean, 0).open();
    }

    private void loadClustersAsync() {
        plugin.getRepository().getPlayerClusters(targetPlayerUuid, filterOre, filterClean).thenAccept(allClusters -> {
            player.getScheduler().run(plugin, task -> {
                renderClusters(allClusters);
            }, null);
        }).exceptionally(ex -> {
            player.getScheduler().run(plugin, task -> {
                setItem(26, ItemBuilder.from(Material.BARRIER)
                        .name("<red>Ошибка при загрузке кладок из базы данных!</red>")
                        .build());
            }, null);
            return null;
        });
    }

    private void renderClusters(List<OreCluster> allClusters) {
        // Clear slots 9 to 44
        for (int i = 9; i < 45; i++) {
            setItem(i, null);
        }

        if (allClusters.isEmpty()) {
            setItem(26, ItemBuilder.from(Material.BARRIER)
                    .name("<red>Кладки руды не найдены</red>")
                    .lore("<gray>По заданным критериям фильтра нет записей.</gray>")
                    .build());
            return;
        }

        int fromIndex = page * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, allClusters.size());

        if (fromIndex >= allClusters.size()) {
            return;
        }

        List<OreCluster> pageClusters = allClusters.subList(fromIndex, toIndex);

        for (int i = 0; i < pageClusters.size(); i++) {
            OreCluster cluster = pageClusters.get(i);
            int slot = 9 + i;

            renderClusterItem(slot, cluster);
        }

        // Next page button (slot 53)
        if (toIndex < allClusters.size()) {
            ItemStack next = ItemBuilder.from(Material.ARROW)
                    .name("<yellow>Следующая страница ▶</yellow>")
                    .addLoreLine("<gray>Перейти на страницу " + (page + 2) + "</gray>")
                    .build();
            setItem(53, next, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                new ClusterListGui(plugin, player, targetPlayerUuid, targetPlayerName, filterOre, filterClean, page + 1).open();
            });
        }
    }

    private void renderClusterItem(int slot, OreCluster cluster) {
        String statusFormatted = cluster.isClean()
                ? "<green><bold>✔ ПРОВЕРЕНО (ЧИСТО)</bold></green>"
                : "<yellow><bold>⚠ ТРЕБУЕТ ПРОВЕРКИ</bold></yellow>";

        String title = cluster.isClean()
                ? "<green>✔ Кладка #" + cluster.getId() + "</green> <gray>(" + cluster.getOreType().getDisplayName() + ")</gray>"
                : "<gold>⚠ Кладка #" + cluster.getId() + "</gold> <gray>(" + cluster.getOreType().getDisplayName() + ")</gray>";

        String coords = String.format("%s [%.0f, %.0f, %.0f]", cluster.getWorldName(), cluster.getCenterX(), cluster.getCenterY(), cluster.getCenterZ());
        String dateStr = DATE_FORMATTER.format(cluster.getCreatedAt());

        ItemStack item = ItemBuilder.from(cluster.getOreType().getBlockIcon())
                .name(title)
                .lore(
                        "<gray>Статус проверки:</gray> " + statusFormatted,
                        " ",
                        "<gray>Тип руды:</gray> " + cluster.getOreType().getDisplayName(),
                        "<gray>Количество блоков:</gray> <white><bold>" + cluster.getBlockCount() + "</bold> шт.</white>",
                        "<gray>Координаты:</gray> <yellow>" + coords + "</yellow>",
                        "<gray>Дата обнаружения:</gray> <dark_gray>" + dateStr + "</dark_gray>",
                        " ",
                        "<yellow>▶ Левый клик:</yellow> <white>Телепортация и подсветка (GOLD_BLOCK)</white>",
                        "<aqua>▶ Правый клик:</aqua> <white>Сменить статус проверки [Чисто / Не проверено]</white>"
                )
                .glow(cluster.isClean())
                .build();

        setItem(slot, item, event -> {
            if (event.isRightClick()) {
                // Toggle Clean status
                boolean newClean = !cluster.isClean();
                cluster.setClean(newClean);

                plugin.getRepository().setClusterClean(cluster.getId(), newClean).thenRun(() -> {
                    player.getScheduler().run(plugin, task -> {
                        // If player is currently inspecting this cluster in spectator and marks it clean:
                        if (newClean && plugin.getHighlightService().hasActiveSession(player.getUniqueId())) {
                            var session = plugin.getHighlightService().getSession(player.getUniqueId());
                            if (session != null && session.clusterId() == cluster.getId()) {
                                player.closeInventory();
                                plugin.getHighlightService().clearHighlight(player, false);
                                player.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() +
                                        "<green>Кладка <white>#" + cluster.getId() + "</white> помечена как <bold>ЧИСТАЯ</bold> ✔ Вы возвращены на исходную позицию!</green>"));
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                                return;
                            }
                        }

                        renderClusterItem(slot, cluster);
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, newClean ? 1.4f : 0.8f);

                        String msg = newClean
                                ? plugin.getConfigManager().getClusterCleanMarked()
                                : plugin.getConfigManager().getClusterUncleanMarked();
                        player.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() + msg,
                                Map.of("id", String.valueOf(cluster.getId()))));
                    }, null);
                });
            } else if (event.isLeftClick()) {
                // Teleport and packet highlight
                player.closeInventory();
                plugin.getRepository().getClusterBlocks(cluster.getId()).thenAccept(blocks -> {
                    player.getScheduler().run(plugin, task -> {
                        plugin.getHighlightService().startInspection(player, cluster, blocks);
                    }, null);
                });
            }
        });
    }
}
