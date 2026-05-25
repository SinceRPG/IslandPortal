package net.danh.islandportal.portal.item;

import net.danh.islandportal.portal.config.PortalConfig;
import net.danh.islandportal.portal.model.PortalItemSettings;
import net.danh.islandportal.portal.model.PortalType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class PortalItemFactory {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PortalConfig config;
    private final NamespacedKey itemTypeKey;

    public PortalItemFactory(JavaPlugin plugin, PortalConfig config) {
        this.config = config;
        this.itemTypeKey = new NamespacedKey(plugin, "portal_type");
    }

    public ItemStack create(PortalType type, int amount) {
        PortalItemSettings settings = type.item();
        ItemStack item = new ItemStack(settings.material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize(settings.displayName()));
        if (!settings.lore().isEmpty()) {
            meta.lore(settings.lore().stream().map(miniMessage::deserialize).toList());
        }
        if (settings.customModelData() != null) {
            meta.setCustomModelData(settings.customModelData());
        }
        meta.setUnbreakable(settings.unbreakable());
        if (settings.enchantmentGlint() != null) {
            meta.setEnchantmentGlintOverride(settings.enchantmentGlint());
        }
        for (String flagName : settings.itemFlags()) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flagName.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        meta.getPersistentDataContainer().set(itemTypeKey, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    public PortalType typeFrom(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String typeId = item.getItemMeta().getPersistentDataContainer().get(itemTypeKey, PersistentDataType.STRING);
        return typeId == null ? null : config.type(typeId);
    }
}
