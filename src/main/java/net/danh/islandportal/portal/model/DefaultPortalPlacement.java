package net.danh.islandportal.portal.model;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.util.List;

public record DefaultPortalPlacement(Location portalBase, BlockFace facing, List<String> supportBlocks) {
}
