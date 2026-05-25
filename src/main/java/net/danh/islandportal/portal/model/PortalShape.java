package net.danh.islandportal.portal.model;

import org.bukkit.Material;

public record PortalShape(
        int width,
        int height,
        Material frameMaterial,
        Material portalMaterial,
        boolean replaceOnlyAir,
        boolean trackOnly
) {
}
