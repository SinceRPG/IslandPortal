package net.danh.islandportal.minion.integration;

import org.bukkit.inventory.ItemStack;

public interface NativeItemProvider {

    ItemStack item(String id);

    default boolean matches(ItemStack item, String id) {
        ItemStack expected = item(id);
        if (expected == null) {
            return false;
        }
        expected.setAmount(item.getAmount());
        return item.isSimilar(expected);
    }
}
