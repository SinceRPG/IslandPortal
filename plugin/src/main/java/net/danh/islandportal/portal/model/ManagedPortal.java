package net.danh.islandportal.portal.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

public record ManagedPortal(String id, String type, String world, int x, int y, int z, String facing, String owner, String islandId, boolean defaultPortal, AccessPolicy pickupPolicy, AccessPolicy usePolicy, AccessPolicy configurePolicy, List<String> islandMembers, List<String> blocks, List<String> triggerBlocks, List<String> supportBlocks, String returnWorld, double returnX, double returnY, double returnZ, float returnYaw, float returnPitch) {

    public ManagedPortal(String id, String type, String world, int x, int y, int z, String facing, String owner, String islandId, boolean defaultPortal, AccessPolicy pickupPolicy, AccessPolicy usePolicy, AccessPolicy configurePolicy, List<String> islandMembers, List<String> blocks, List<String> triggerBlocks, List<String> supportBlocks) {
        this(id, type, world, x, y, z, facing, owner, islandId, defaultPortal, pickupPolicy, usePolicy, configurePolicy, islandMembers, blocks, triggerBlocks, supportBlocks, null, 0.0, 0.0, 0.0, 0.0f, 0.0f);
    }

    public Location baseLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z);
    }

    public Location returnLocation() {
        if (returnWorld == null || returnWorld.isBlank()) {
            return null;
        }
        World bukkitWorld = Bukkit.getWorld(returnWorld);
        return bukkitWorld == null ? null : new Location(bukkitWorld, returnX, returnY, returnZ, returnYaw, returnPitch);
    }

    public static ManagedPortal of(String id, PortalType type, Location base, String facing, String owner, String islandId, boolean defaultPortal, List<String> islandMembers, List<String> blocks, List<String> triggerBlocks, List<String> supportBlocks, Location returnLocation) {
        String returnWorld = returnLocation == null || returnLocation.getWorld() == null ? null : returnLocation.getWorld().getName();
        double returnX = returnLocation == null ? 0.0 : returnLocation.getX();
        double returnY = returnLocation == null ? 0.0 : returnLocation.getY();
        double returnZ = returnLocation == null ? 0.0 : returnLocation.getZ();
        float returnYaw = returnLocation == null ? 0.0f : returnLocation.getYaw();
        float returnPitch = returnLocation == null ? 0.0f : returnLocation.getPitch();
        return new ManagedPortal(id, type.id(), base.getWorld().getName(), base.getBlockX(), base.getBlockY(), base.getBlockZ(), facing, owner, islandId, defaultPortal, type.pickupPolicy(), type.usePolicy(), type.configurePolicy(), List.copyOf(islandMembers), List.copyOf(blocks), List.copyOf(triggerBlocks), List.copyOf(supportBlocks), returnWorld, returnX, returnY, returnZ, returnYaw, returnPitch);
    }
}
