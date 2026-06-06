package net.danh.islandportal.minion.integration;

import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

final class MmoItemsItemProvider implements NativeItemProvider {

    @Override
    public ItemStack item(String id) {
        String[] parts = id.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            return MMOItems.plugin.getItem(parts[0].toUpperCase(Locale.ROOT), parts[1]);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
