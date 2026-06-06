package net.danh.islandportal.minion.integration;

import net.brcdev.shopgui.ShopGuiPlusApi;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class ShopGuiPlusPriceLookup implements PriceLookup {

    @Override
    public String id() {
        return "SHOPGUI_PLUS";
    }

    @Override
    public Double unitSellPrice(Player player, ItemStack item) {
        try {
            item.setAmount(1);
            double price = player == null ? ShopGuiPlusApi.getItemStackPriceSell(item) : ShopGuiPlusApi.getItemStackPriceSell(player, item);
            return price >= 0.0 ? price : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
