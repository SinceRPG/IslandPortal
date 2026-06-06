package net.danh.islandportal.minion.model;

import org.bukkit.Material;

import java.util.List;

public record MinionDisplaySettings(
        String name,
        Material headMaterial,
        String headItemKey,
        Material chestplateMaterial,
        String chestplateItemKey,
        Material leggingsMaterial,
        String leggingsItemKey,
        Material bootsMaterial,
        String bootsItemKey,
        Material mainHandMaterial,
        String mainHandItemKey,
        boolean small,
        boolean glowing,
        double health,
        List<MinionAnimationStage> animationStages
) {
}
