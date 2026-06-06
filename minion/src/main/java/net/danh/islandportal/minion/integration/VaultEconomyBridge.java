package net.danh.islandportal.minion.integration;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultEconomyBridge implements EconomyBridge {

    private final Server server;

    public VaultEconomyBridge(Server server) {
        this.server = server;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0.0) {
            return true;
        }
        Economy economy = economy();
        if (economy == null) {
            return false;
        }
        if (!economy.has(player, amount)) {
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0.0) {
            return true;
        }
        Economy economy = economy();
        if (economy == null) {
            return false;
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

    @Override
    public boolean available() {
        return economy() != null;
    }

    private Economy economy() {
        if (!server.getPluginManager().isPluginEnabled("Vault")) {
            return null;
        }
        RegisteredServiceProvider<Economy> registration = server.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }
}
