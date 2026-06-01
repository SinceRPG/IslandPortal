package net.danh.islandportal.npc.model;

import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.Map;

public record NpcType(
        String id,
        EntityType entityType,
        String name,
        boolean nameVisible,
        String profession,
        boolean baby,
        boolean glowing,
        boolean silent,
        boolean invulnerable,
        boolean collidable,
        boolean defaultOnIsland,
        Vector islandOffset,
        NpcSpawnSearchSettings spawnSearch,
        float yaw,
        float pitch,
        boolean lookAtPlayer,
        double lookRadius,
        NpcMovementSettings movement,
        NpcUnlockSettings unlock,
        Map<NpcClickAction, NpcInteraction> interactions
) {
}
