package net.danh.islandportal.minion.integration;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.inventory.ItemStack;

final class ItemsAdderItemProvider implements NativeItemProvider {

    @Override
    public ItemStack item(String id) {
        try {
            CustomStack stack = CustomStack.getInstance(id);
            return stack == null ? null : stack.getItemStack();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public boolean matches(ItemStack item, String id) {
        try {
            CustomStack stack = CustomStack.byItemStack(item);
            return stack != null && id.equals(stack.getNamespacedID());
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
