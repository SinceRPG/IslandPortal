package net.danh.islandportal.minion.integration;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.danh.islandportal.minion.model.MinionIntegrationSettings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PriceLookupRegistry {

    private final Map<String, PriceLookup> lookups = new LinkedHashMap<>();

    public PriceLookupRegistry(Server server, MinionIntegrationSettings settings) {
        if (settings.shopGuiPlus() && server.getPluginManager().isPluginEnabled("ShopGUIPlus")) {
            register(new ShopGuiPlusPriceLookup());
        }
        if (settings.essentials() && server.getPluginManager().isPluginEnabled("Essentials")) {
            register(new EssentialsPriceLookup(server));
        }
        if (settings.cmi() && server.getPluginManager().isPluginEnabled("CMI")) {
            register(new CmiPriceLookup());
        }
    }

    public double unitSellPrice(List<String> sources, Player player, ItemStack item, double configuredPrice) {
        for (String source : sources) {
            String normalized = normalize(source);
            if (normalized.equals("CONFIG")) {
                return configuredPrice;
            }
            PriceLookup lookup = lookups.get(normalized);
            if (lookup == null) {
                continue;
            }
            Double price = lookup.unitSellPrice(player, item.clone());
            if (price != null && price >= 0.0) {
                return price;
            }
        }
        return configuredPrice;
    }

    private void register(PriceLookup lookup) {
        lookups.put(normalize(lookup.id()), lookup);
    }

    private String normalize(String value) {
        return value.toUpperCase(Locale.ROOT).replace("-", "_").replace("+", "_PLUS");
    }
}
