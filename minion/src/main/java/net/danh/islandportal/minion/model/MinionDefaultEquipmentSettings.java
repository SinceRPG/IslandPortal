package net.danh.islandportal.minion.model;

import org.bukkit.Material;

public record MinionDefaultEquipmentSettings(
        Material chestplateMaterial,
        String chestplateItemKey,
        Material leggingsMaterial,
        String leggingsItemKey,
        Material bootsMaterial,
        String bootsItemKey,
        Material mainHandMaterial,
        String mainHandItemKey
) {
}
