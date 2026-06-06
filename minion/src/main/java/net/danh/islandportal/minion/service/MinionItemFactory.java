package net.danh.islandportal.minion.service;

import net.danh.islandportal.minion.config.MinionConfig;
import net.danh.islandportal.minion.integration.NativeItemBridge;
import net.danh.islandportal.minion.model.MinionType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class MinionItemFactory {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final MinionConfig config;
    private final NativeItemBridge nativeItemBridge;
    private final NamespacedKey minionItemKey;

    public MinionItemFactory(MinionConfig config, NativeItemBridge nativeItemBridge, NamespacedKey minionItemKey) {
        this.config = config;
        this.nativeItemBridge = nativeItemBridge;
        this.minionItemKey = minionItemKey;
    }

    public ItemStack create(MinionType type, int amount) {
        return create(type, amount, 0L);
    }

    public ItemStack create(MinionType type, int amount, long actions) {
        ItemStack item = nativeItemBridge.item(type.itemKey(), type.itemMaterial(), amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize(type.itemName()));
        if (!type.itemLore().isEmpty()) {
            meta.lore(type.itemLore().stream()
                    .map(line -> line.replace("%type%", type.id()).replace("%actions%", String.valueOf(actions)))
                    .map(miniMessage::deserialize)
                    .toList());
        }
        if (type.customModelData() != null) {
            org.bukkit.inventory.meta.components.CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of((float) type.customModelData().intValue()));
            meta.setCustomModelDataComponent(component);
        }
        meta.getPersistentDataContainer().set(minionItemKey, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    public MinionType typeFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String typeId = item.getItemMeta().getPersistentDataContainer().get(minionItemKey, PersistentDataType.STRING);
        return typeId == null ? null : config.type(typeId);
    }
}
