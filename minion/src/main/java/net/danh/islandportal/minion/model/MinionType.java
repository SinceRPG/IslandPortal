package net.danh.islandportal.minion.model;

import org.bukkit.Material;

import java.util.List;

public record MinionType(
        String id,
        Material itemMaterial,
        String itemKey,
        String itemName,
        List<String> itemLore,
        Integer customModelData,
        MinionDisplaySettings display,
        List<MinionTier> tiers,
        List<MinionDrop> drops,
        MinionActionSettings action,
        int maxPerOwner,
        long offlineLimitMillis,
        List<MinionOfflineLimit> offlineLimits,
        boolean requiresFuel,
        MinionShopSettings shop
) {

    public MinionTier tier(int level) {
        return tiers.stream()
                .filter(tier -> tier.level() == level)
                .findFirst()
                .orElseGet(() -> tiers().isEmpty() ? new MinionTier(1, 60_000L, 64, 3, List.of(), 0.0) : tiers().getFirst());
    }

    public MinionTier nextTier(int level) {
        return tiers.stream()
                .filter(tier -> tier.level() > level)
                .findFirst()
                .orElse(null);
    }
}
