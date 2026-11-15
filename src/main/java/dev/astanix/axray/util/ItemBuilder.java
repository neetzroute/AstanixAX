package dev.astanix.axray.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ItemBuilder {
    private final ItemStack itemStack;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.itemStack = new ItemStack(material, Math.max(1, amount));
        this.meta = itemStack.getItemMeta();
    }

    public ItemBuilder(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
        this.meta = this.itemStack.getItemMeta();
    }

    public static ItemBuilder from(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder playerHead(UUID uuid, String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        if (skullMeta != null) {
            if (uuid != null) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                skullMeta.setOwningPlayer(offlinePlayer);
            }
        }
        return new ItemBuilder(head);
    }

    public ItemBuilder name(Component name) {
        if (meta != null && name != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        }
        return this;
    }

    public ItemBuilder name(String miniMessageText) {
        return name(TextUtil.parse(miniMessageText));
    }

    public ItemBuilder lore(List<Component> lore) {
        if (meta != null && lore != null) {
            List<Component> formatted = new ArrayList<>();
            for (Component line : lore) {
                formatted.add(line.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(formatted);
        }
        return this;
    }

    public ItemBuilder lore(Component... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(String... miniMessageLines) {
        List<Component> list = new ArrayList<>();
        for (String line : miniMessageLines) {
            list.add(TextUtil.parse(line));
        }
        return lore(list);
    }

    public ItemBuilder addLoreLine(Component line) {
        if (meta != null && line != null) {
            List<Component> current = meta.lore();
            if (current == null) {
                current = new ArrayList<>();
            } else {
                current = new ArrayList<>(current);
            }
            current.add(line.decoration(TextDecoration.ITALIC, false));
            meta.lore(current);
        }
        return this;
    }

    public ItemBuilder addLoreLine(String miniMessageLine) {
        return addLoreLine(TextUtil.parse(miniMessageLine));
    }

    public ItemBuilder glow(boolean glowing) {
        if (meta != null) {
            if (glowing) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                meta.removeEnchant(Enchantment.UNBREAKING);
                meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        itemStack.setAmount(Math.max(1, amount));
        return this;
    }

    public <T extends ItemMeta> ItemBuilder meta(Class<T> metaClass, Consumer<T> consumer) {
        if (metaClass.isInstance(meta)) {
            consumer.accept(metaClass.cast(meta));
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }
}
