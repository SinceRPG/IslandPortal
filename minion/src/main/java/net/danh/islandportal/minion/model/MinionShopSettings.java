package net.danh.islandportal.minion.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public record MinionShopSettings(boolean enabled, List<String> sellCommands, double defaultPrice, Map<String, Double> prices, List<String> priceSources) {

    public double price(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return defaultPrice;
        }
        Double exact = prices.get(itemKey);
        if (exact != null) {
            return exact;
        }
        Double normalized = prices.get(itemKey.toLowerCase(Locale.ROOT));
        return normalized == null ? defaultPrice : normalized;
    }
}
