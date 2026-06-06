package net.danh.islandportal.minion.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record MinionMenuHolder(String minionId) implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}
