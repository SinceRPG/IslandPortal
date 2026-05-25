package net.danh.islandportal.portal.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

public record ManagedPortal(String id, String type, String world, int x, int y, int z, String facing, String owner, String islandId, boolean defaultPortal, AccessPolicy pickupPolicy, AccessPolicy usePolicy, AccessPolicy configurePolicy, List<String> islandMembers, List<String> blocks, List<String> triggerBlocks, List<String> supportBlocks) {

    public Location baseLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z);
    }

    public static ManagedPortal of(String id, PortalType type, Location base, String facing, String owner, String islandId, boolean defaultPortal, List<String> islandMembers, List<String> blocks, List<String> triggerBlocks, List<String> supportBlocks) {
        return new ManagedPortal(id, type.id(), base.getWorld().getName(), base.getBlockX(), base.getBlockY(), base.getBlockZ(), facing, owner, islandId, defaultPortal, type.pickupPolicy(), type.usePolicy(), type.configurePolicy(), List.copyOf(islandMembers), List.copyOf(blocks), List.copyOf(triggerBlocks), List.copyOf(supportBlocks));
    }
}
