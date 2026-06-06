package net.danh.islandportal.minion.integration;

import com.earth2me.essentials.IEssentials;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;

final class EssentialsPriceLookup implements PriceLookup {

    private final Server server;

    EssentialsPriceLookup(Server server) {
        this.server = server;
    }

    @Override
    public String id() {
        return "ESSENTIALS";
    }

    @Override
    public Double unitSellPrice(Player player, ItemStack item) {
        Plugin plugin = server.getPluginManager().getPlugin("Essentials");
        if (!(plugin instanceof IEssentials essentials)) {
            return null;
        }
        try {
            item.setAmount(1);
            BigDecimal price = essentials.getWorth().getPrice(essentials, item);
            return price == null ? null : price.doubleValue();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
