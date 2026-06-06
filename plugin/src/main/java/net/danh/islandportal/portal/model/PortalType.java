package net.danh.islandportal.portal.model;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.List;

public record PortalType(
        String id,
        PortalItemSettings item,
        PortalShape shape,
        Vector islandOffset,
        BlockFace islandFacing,
        PortalIslandSettings portalIsland,
        boolean defaultOnIsland,
        boolean consumeOnPlace,
        boolean giveItemOnBreak,
        AccessPolicy pickupPolicy,
        AccessPolicy usePolicy,
        AccessPolicy configurePolicy,
        PortalPermissions permissions,
        PortalAction action,
        Location target,
        List<String> commands
) {
}
