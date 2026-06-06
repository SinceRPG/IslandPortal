package net.danh.islandportal.minion.service;

import net.danh.islandportal.minion.integration.EconomyBridge;
import net.danh.islandportal.minion.integration.NativeItemBridge;
import net.danh.islandportal.minion.integration.PriceLookupRegistry;
import net.danh.islandportal.minion.model.ManagedMinion;
import net.danh.islandportal.minion.model.MinionType;
import net.danh.islandportal.platform.PlatformScheduler;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MinionSellService {

    private final JavaPlugin plugin;
    private final PlatformScheduler scheduler;
    private final EconomyBridge economyBridge;
    private final NativeItemBridge nativeItemBridge;
    private final PriceLookupRegistry priceLookupRegistry;

    public MinionSellService(JavaPlugin plugin, PlatformScheduler scheduler, EconomyBridge economyBridge, NativeItemBridge nativeItemBridge, PriceLookupRegistry priceLookupRegistry) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.economyBridge = economyBridge;
        this.nativeItemBridge = nativeItemBridge;
        this.priceLookupRegistry = priceLookupRegistry;
    }

    public void sell(Player player, MinionType type, ManagedMinion minion, Map<String, Integer> soldItems) {
        String playerName = player == null ? ownerName(minion) : player.getName();
        for (Map.Entry<String, Integer> entry : soldItems.entrySet()) {
            double unitPrice = unitPrice(player, type, entry.getKey());
            double totalPrice = unitPrice * entry.getValue();
            if (type.shop().sellCommands().isEmpty()) {
                OfflinePlayer payee = player == null ? offlineOwner(minion) : player;
                if (payee != null) {
                    economyBridge.deposit(payee, totalPrice);
                }
            }
            for (String command : type.shop().sellCommands()) {
                String parsed = command
                        .replace("%player%", playerName)
                        .replace("%material%", entry.getKey())
                        .replace("%item%", entry.getKey())
                        .replace("%amount%", String.valueOf(entry.getValue()))
                        .replace("%unit_price%", money(unitPrice))
                        .replace("%price%", money(unitPrice))
                        .replace("%total_price%", money(totalPrice))
                        .replace("%minion_id%", minion.id())
                        .replace("%type%", type.id());
                scheduler.runGlobal(() -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), parsed));
            }
        }
    }

    private double unitPrice(Player player, MinionType type, String itemKey) {
        double configured = type.shop().price(itemKey);
        Material material = Material.matchMaterial(itemKey);
        ItemStack pricedItem = nativeItemBridge.item(itemKey, material == null ? Material.STONE : material, 1);
        return priceLookupRegistry.unitSellPrice(type.shop().priceSources(), player, pricedItem, configured);
    }

    private String money(double amount) {
        if (amount == Math.rint(amount)) {
            return String.valueOf((long) amount);
        }
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    private String ownerName(ManagedMinion minion) {
        Player owner = onlinePlayer(minion.owner());
        return owner == null ? minion.owner() : owner.getName();
    }

    private Player onlinePlayer(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return null;
        }
        try {
            return plugin.getServer().getPlayer(UUID.fromString(playerId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private OfflinePlayer offlineOwner(ManagedMinion minion) {
        try {
            return plugin.getServer().getOfflinePlayer(UUID.fromString(minion.owner()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
