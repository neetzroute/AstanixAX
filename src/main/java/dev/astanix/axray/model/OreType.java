package dev.astanix.axray.model;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.Optional;

public enum OreType {
    DIAMOND("Алмазная руда", Material.DIAMOND, Material.DIAMOND_ORE, TextColor.color(0x55FFFF)),
    DEBRIS("Древние обломки", Material.NETHERITE_SCRAP, Material.ANCIENT_DEBRIS, TextColor.color(0xA06540)),
    EMERALD("Изумрудная руда", Material.EMERALD, Material.EMERALD_ORE, TextColor.color(0x55FF55));

    private final String displayName;
    private final Material itemIcon;
    private final Material blockIcon;
    private final TextColor color;

    OreType(String displayName, Material itemIcon, Material blockIcon, TextColor color) {
        this.displayName = displayName;
        this.itemIcon = itemIcon;
        this.blockIcon = blockIcon;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getItemIcon() {
        return itemIcon;
    }

    public Material getBlockIcon() {
        return blockIcon;
    }

    public TextColor getColor() {
        return color;
    }

    public Component getFormattedName() {
        return Component.text(displayName, color);
    }

    public static Optional<OreType> fromMaterial(Material material) {
        if (material == null) return Optional.empty();
        return switch (material) {
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> Optional.of(DIAMOND);
            case ANCIENT_DEBRIS -> Optional.of(DEBRIS);
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> Optional.of(EMERALD);
            default -> Optional.empty();
        };
    }
}
