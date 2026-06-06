package net.danh.islandportal.minion.integration;

import org.bukkit.OfflinePlayer;

public final class UnavailableEconomyBridge implements EconomyBridge {

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return amount <= 0.0;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return amount <= 0.0;
    }

    @Override
    public boolean available() {
        return false;
    }
}
