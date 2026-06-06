package net.danh.islandportal.minion.integration;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface PriceLookup {

    String id();

    Double unitSellPrice(Player player, ItemStack item);
}
