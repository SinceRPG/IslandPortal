package net.danh.islandportal.minion.integration;

import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Modules.Worth.WorthItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class CmiPriceLookup implements PriceLookup {

    @Override
    public String id() {
        return "CMI";
    }

    @Override
    public Double unitSellPrice(Player player, ItemStack item) {
        try {
            item.setAmount(1);
            WorthItem worth = CMI.getInstance().getWorthManager().getWorth(item);
            return worth == null ? null : worth.getSellPrice();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
