package net.danh.islandportal.minion.integration;

import org.bukkit.OfflinePlayer;

public interface EconomyBridge {

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    boolean available();
}
