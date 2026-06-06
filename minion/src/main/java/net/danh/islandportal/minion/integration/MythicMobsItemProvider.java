package net.danh.islandportal.minion.integration;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.inventory.ItemStack;

final class MythicMobsItemProvider implements NativeItemProvider {

    @Override
    public ItemStack item(String id) {
        try {
            return MythicBukkit.inst().getItemManager().getItemStack(id);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
