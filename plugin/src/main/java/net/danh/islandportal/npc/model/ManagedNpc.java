package net.danh.islandportal.npc.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.UUID;

public record ManagedNpc(
        String id,
        String type,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String owner,
        String islandId,
        List<String> islandMembers,
        UUID entityId
) {

    public Location location() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    public ManagedNpc withEntityId(UUID newEntityId) {
        return new ManagedNpc(id, type, world, x, y, z, yaw, pitch, owner, islandId, islandMembers, newEntityId);
    }

    public static ManagedNpc of(String id, NpcType type, Location location, String owner, String islandId, List<String> islandMembers) {
        return new ManagedNpc(
                id,
                type.id(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                owner,
                islandId,
                List.copyOf(islandMembers),
                null
        );
    }
}
