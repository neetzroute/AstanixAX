package dev.astanix.axray.tracker;

import dev.astanix.axray.config.PluginConfig;
import dev.astanix.axray.model.OreType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public class OreBreakListener implements Listener {
    private final PluginConfig config;
    private final OreTrackerService trackerService;

    public OreBreakListener(PluginConfig config, OreTrackerService trackerService) {
        this.config = config;
        this.trackerService = trackerService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Check gamemode
        if (config.isIgnoreCreative() && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
            return;
        }

        // Check world
        if (!config.isWorldMonitored(block.getWorld().getName())) {
            return;
        }

        Material material = block.getType();
        Optional<OreType> optionalOre = OreType.fromMaterial(material);
        if (optionalOre.isEmpty()) {
            return;
        }

        // Check silk touch filter if enabled
        if (config.isIgnoreSilkTouch()) {
            ItemStack tool = player.getInventory().getItemInMainHand();
            if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
                return;
            }
        }

        trackerService.handleOreBreak(player, block.getLocation(), optionalOre.get());
    }
}
