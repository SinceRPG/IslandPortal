package net.danh.islandportal.minion.model;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

public record MinionActionSettings(
        MinionActionType type,
        int range,
        int blocksPerAction,
        int smeltAtTier,
        Material spawnBlock,
        EntityType spawnEntity,
        List<Material> allowedMaterials,
        List<Material> sellMaterials
) {
}
