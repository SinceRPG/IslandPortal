package net.danh.islandportal.portal.model;

import org.bukkit.Material;
import org.bukkit.util.Vector;

public record PortalIslandSettings(
        boolean enabled,
        PortalIslandMode mode,
        int platformRadius,
        int dirtDepth,
        Material topMaterial,
        Material fillMaterial,
        Vector portalOffset,
        int randomMinDistance,
        int randomMaxDistance,
        boolean randomFacing,
        int searchRadius,
        int searchStep,
        int clearance,
        String schematic,
        boolean schematicIgnoreAir,
        boolean alignToIslandY,
        int schematicAnchorY,
        int yOffset
) {
}
