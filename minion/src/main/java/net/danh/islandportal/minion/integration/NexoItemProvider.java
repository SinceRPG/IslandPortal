package net.danh.islandportal.minion.integration;

import com.nexomc.nexo.api.NexoItems;
import org.bukkit.inventory.ItemStack;

final class NexoItemProvider implements NativeItemProvider {

    @Override
    public ItemStack item(String id) {
        try {
            return NexoItems.itemFromId(id).build();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public boolean matches(ItemStack item, String id) {
        try {
            return id.equals(NexoItems.idFromItem(item));
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
