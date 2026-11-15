package dev.astanix.axray.command;

import dev.astanix.axray.AstanixAXPlugin;
import dev.astanix.axray.gui.ClusterListGui;
import dev.astanix.axray.gui.PlayerListGui;
import dev.astanix.axray.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AxCommand implements CommandExecutor, TabCompleter {
    private final AstanixAXPlugin plugin;

    public AxCommand(AstanixAXPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("astanix.axray.admin")) {
            sender.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getNoPermission()));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() + "<red>Только игроки могут открывать GUI!</red>"));
                return true;
            }
            new PlayerListGui(plugin, player, 0).open();
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "clear" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() + "<red>Только игроки могут выполнять эту команду!</red>"));
                    return true;
                }
                boolean cleared = plugin.getHighlightService().clearHighlight(player, true);
                if (!cleared) {
                    player.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() + "<gray>У вас нет активной подсветки кладки.</gray>"));
                }
                return true;
            }

            case "reload" -> {
                plugin.reloadPluginConfig();
                sender.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getConfigReloaded()));
                return true;
            }

            case "markclean" -> {
                if (args.length < 2) {
                    sender.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() + "<red>Использование: /ax markclean <ID кладки></red>"));
                    return true;
                }
                try {
                    long clusterId = Long.parseLong(args[1]);
                    plugin.getRepository().setClusterClean(clusterId, true).thenRun(() -> {
                        if (sender instanceof Player player) {
                            player.getScheduler().run(plugin, task -> {
                                boolean hadSession = plugin.getHighlightService().hasActiveSession(player.getUniqueId());
                                if (hadSession) {
                                    plugin.getHighlightService().clearHighlight(player, false);
                                    player.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() +
                                            "<green>Кладка <white>#" + clusterId + "</white> помечена как <bold>ЧИСТАЯ</bold> ✔ Вы возвращены на исходную позицию!</green>"));
                                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                                } else {
                                    player.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getClusterCleanMarked(),
                                            Map.of("id", String.valueOf(clusterId))));
                                }
                            }, null);
                        } else {
                            sender.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getClusterCleanMarked(),
                                    Map.of("id", String.valueOf(clusterId))));
                        }
                    });
                } catch (NumberFormatException e) {
                    sender.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() + "<red>Неверный формат ID кладки!</red>"));
                }
                return true;
            }

            case "inspect" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() + "<red>Только игроки могут открывать GUI!</red>"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() + "<red>Использование: /ax inspect <игрок></red>"));
                    return true;
                }

                String targetName = args[1];
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                if (target.getUniqueId() == null) {
                    player.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getPlayerNotFound()));
                    return true;
                }

                new ClusterListGui(plugin, player, target.getUniqueId(), targetName, null, null, 0).open();
                return true;
            }

            default -> {
                // Check if sub is player name
                if (sender instanceof Player player) {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                    new ClusterListGui(plugin, player, target.getUniqueId(), args[0], null, null, 0).open();
                    return true;
                }
                sender.sendMessage(TextUtil.parse(plugin.getConfigManager().getPrefix() + "<red>Неизвестная подкоманда! Используйте /ax, /ax inspect <player>, /ax clear, /ax reload</red>"));
                return true;
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("astanix.axray.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(List.of("clear", "inspect", "reload"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("inspect")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}
