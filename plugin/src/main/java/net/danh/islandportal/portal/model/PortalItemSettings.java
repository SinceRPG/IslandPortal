package net.danh.islandportal.portal.model;

import org.bukkit.Material;

import java.util.List;

public record PortalItemSettings(
        Material material,
        String displayName,
        List<String> lore,
        Integer customModelData,
        boolean unbreakable,
        Boolean enchantmentGlint,
        List<String> itemFlags
) {
}
