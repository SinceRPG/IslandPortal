package net.danh.islandportal.minion.integration;

import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.inventory.ItemStack;

final class HeadDatabaseItemProvider implements NativeItemProvider {

    @Override
    public ItemStack item(String id) {
        try {
            return new HeadDatabaseAPI().getItemHead(id);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
