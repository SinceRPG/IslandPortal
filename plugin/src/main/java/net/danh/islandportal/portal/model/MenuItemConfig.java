package net.danh.islandportal.portal.model;

import org.bukkit.Material;

import java.util.List;

public record MenuItemConfig(
        String id,
        List<Integer> slots,
        String action,
        Material material,
        int amount,
        String displayName,
        List<String> lore,
        Integer customModelData,
        boolean unbreakable,
        Boolean enchantmentGlint,
        List<String> itemFlags
) {
}
