package net.danh.islandportal.portal.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record PortalSettingsHolder(String portalId) implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }
}
