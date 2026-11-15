package dev.astanix.axray.gui;

import dev.astanix.axray.AstanixAXPlugin;
import dev.astanix.axray.model.PlayerOreStats;
import dev.astanix.axray.util.ItemBuilder;
import dev.astanix.axray.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class PlayerListGui extends AbstractGui {
    private static final int PAGE_SIZE = 45;
    private final AstanixAXPlugin plugin;
    private final int page;

    public PlayerListGui(AstanixAXPlugin plugin, Player player, int page) {
        super(player, 54, TextUtil.parse("<gradient:#00D2FF:#3A7BD5><bold>Astanix-AX</bold></gradient> <dark_gray>| Топ игроков (Стр. " + (page + 1) + ")</dark_gray>"));
        this.plugin = plugin;
        this.page = Math.max(0, page);

        setupBottomBar();
        loadPlayersAsync();
    }

    private void setupBottomBar() {
        ItemStack filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();
        for (int i = 45; i < 54; i++) {
            setItem(i, filler);
        }

        // Previous page button
        if (page > 0) {
            ItemStack prev = ItemBuilder.from(Material.ARROW)
                    .name("<yellow>◀ Предыдущая страница</yellow>")
                    .addLoreLine("<gray>Перейти на страницу " + page + "</gray>")
                    .build();
            setItem(45, prev, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                new PlayerListGui(plugin, player, page - 1).open();
            });
        }

        // Help / Info book
        ItemStack info = ItemBuilder.from(Material.BOOK)
                .name("<aqua><bold>Astanix-AX Инспектор</bold></aqua>")
                .lore(
                        "<gray>Сортировка игроков по суммарной</gray>",
                        "<gray>добыче ценной руды (Алмазы, Незерит, Изумруды).</gray>",
                        " ",
                        "<yellow>Кликните по голове игрока,</yellow>",
                        "<yellow>чтобы просмотреть его кладки руды.</yellow>"
                )
                .build();
        setItem(48, info);

        // Refresh button
        ItemStack refresh = ItemBuilder.from(Material.COMPASS)
                .name("<green>↻ Обновить список</green>")
                .addLoreLine("<gray>Перезагрузить данные из базы</gray>")
                .build();
        setItem(49, refresh, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            new PlayerListGui(plugin, player, page).open();
        });

        // Clear highlight button if session active
        if (plugin.getHighlightService().hasActiveSession(player.getUniqueId())) {
            ItemStack clear = ItemBuilder.from(Material.REDSTONE_BLOCK)
                    .name("<red><bold>✖ Сбросить подсветку блоков</bold></red>")
                    .lore(
                            "<gray>У вас активна подсветка кладки.</gray>",
                            "<yellow>Кликните, чтобы восстановить исходный вид блоков.</yellow>"
                    )
                    .glow(true)
                    .build();
            setItem(50, clear, e -> {
                plugin.getHighlightService().clearHighlight(player, true);
                setupBottomBar();
            });
        }

        // Loading placeholder in slot 22
        ItemStack loading = ItemBuilder.from(Material.STRUCTURE_VOID)
                .name("<yellow>Загрузка статистики...</yellow>")
                .build();
        setItem(22, loading);
    }

    private void loadPlayersAsync() {
        plugin.getRepository().getTopPlayers(PAGE_SIZE, page * PAGE_SIZE).thenAccept(players -> {
            // Paper entity thread scheduler
            player.getScheduler().run(plugin, task -> {
                renderPlayers(players);
            }, null);
        }).exceptionally(ex -> {
            player.getScheduler().run(plugin, task -> {
                setItem(22, ItemBuilder.from(Material.BARRIER)
                        .name("<red>Ошибка загрузки данных из БД!</red>")
                        .build());
            }, null);
            return null;
        });
    }

    private void renderPlayers(List<PlayerOreStats> players) {
        // Clear slots 0-44
        for (int i = 0; i < PAGE_SIZE; i++) {
            setItem(i, null);
        }

        if (players.isEmpty()) {
            setItem(22, ItemBuilder.from(Material.BARRIER)
                    .name("<red>Записи о добыче руды отсутствуют</red>")
                    .addLoreLine("<gray>На сервере еще не зафиксирована добыча ценных руд.</gray>")
                    .build());
            return;
        }

        for (int i = 0; i < players.size(); i++) {
            PlayerOreStats stats = players.get(i);
            int slot = i;

            String timeAgo = formatDuration(Duration.between(stats.lastMinedAt(), Instant.now()));

            ItemStack head = ItemBuilder.playerHead(stats.playerUuid(), stats.playerName())
                    .name("<gold><bold>" + stats.playerName() + "</bold></gold>")
                    .lore(
                            "<gray>Всего ценной руды:</gray> <white><bold>" + stats.totalCount() + "</bold> шт.</white>",
                            " ",
                            "<aqua>◆ Алмазная руда:</aqua> <white>" + stats.diamondCount() + "</white>",
                            "<gold>◆ Древние обломки:</gold> <white>" + stats.debrisCount() + "</white>",
                            "<green>◆ Изумрудная руда:</green> <white>" + stats.emeraldCount() + "</white>",
                            " ",
                            "<yellow>Всего кладок:</yellow> <white>" + stats.clusterCount() + "</white> " +
                                    (stats.unreviewedClusterCount() > 0
                                            ? "<red>(" + stats.unreviewedClusterCount() + " непроверено)</red>"
                                            : "<green>(все проверены)</green>"),
                            "<dark_gray>Последняя добыча: " + timeAgo + " назад</dark_gray>",
                            " ",
                            "<yellow>⚡ Кликните для перехода к кладкам игрока</yellow>"
                    )
                    .build();

            setItem(slot, head, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                new ClusterListGui(plugin, player, stats.playerUuid(), stats.playerName(), null, null, 0).open();
            });
        }

        // Next page button
        if (players.size() == PAGE_SIZE) {
            ItemStack next = ItemBuilder.from(Material.ARROW)
                    .name("<yellow>Следующая страница ▶</yellow>")
                    .addLoreLine("<gray>Перейти на страницу " + (page + 2) + "</gray>")
                    .build();
            setItem(53, next, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                new PlayerListGui(plugin, player, page + 1).open();
            });
        }
    }

    private String formatDuration(Duration duration) {
        long seconds = Math.abs(duration.getSeconds());
        if (seconds < 60) return seconds + " сек.";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " мин.";
        long hours = minutes / 60;
        if (hours < 24) return hours + " ч.";
        long days = hours / 24;
        return days + " дн.";
    }
}
