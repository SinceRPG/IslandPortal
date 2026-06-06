package net.danh.islandportal.minion.integration;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.inventory.ItemStack;

final class OraxenItemProvider implements NativeItemProvider {

    @Override
    public ItemStack item(String id) {
        try {
            return OraxenItems.getItemById(id).build();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public boolean matches(ItemStack item, String id) {
        try {
            return id.equals(OraxenItems.getIdByItem(item));
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
