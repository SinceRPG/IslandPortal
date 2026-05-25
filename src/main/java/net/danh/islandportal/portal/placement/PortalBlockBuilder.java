package net.danh.islandportal.portal.placement;

import net.danh.islandportal.portal.model.PortalBlocks;
import net.danh.islandportal.portal.model.PortalShape;
import net.danh.islandportal.portal.model.PortalType;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;

import java.util.ArrayList;
import java.util.List;

public final class PortalBlockBuilder {

    public PortalBlocks build(PortalType type, Location base, BlockFace facing) {
        List<String> blocks = new ArrayList<>();
        List<String> triggerBlocks = new ArrayList<>();
        PortalShape shape = type.shape();
        BlockData portalData = shape.portalMaterial().createBlockData();
        BlockFace widthFace = widthFace(horizontal(facing));
        if (portalData instanceof Orientable orientable) {
            orientable.setAxis(widthFace == BlockFace.EAST ? Axis.X : Axis.Z);
            portalData = orientable;
        }

        for (int x = 0; x <= shape.width() + 1; x++) {
            for (int y = 0; y <= shape.height() + 1; y++) {
                boolean frame = x == 0 || x == shape.width() + 1 || y == 0 || y == shape.height() + 1;
                Block block = relativeBlock(base, widthFace, x, y);
                if (shape.replaceOnlyAir() && !block.getType().isAir()) {
                    continue;
                }
                if (frame) {
                    block.setType(shape.frameMaterial(), false);
                } else {
                    block.setBlockData(portalData, false);
                    triggerBlocks.add(key(block.getLocation()));
                }
                blocks.add(key(block.getLocation()));
            }
        }
        return new PortalBlocks(blocks, triggerBlocks);
    }

    public BlockFace horizontal(BlockFace facing) {
        return switch (facing) {
            case NORTH, SOUTH, EAST, WEST -> facing;
            default -> BlockFace.SOUTH;
        };
    }

    public BlockFace widthFace(BlockFace facing) {
        return switch (facing) {
            case EAST, WEST -> BlockFace.SOUTH;
            default -> BlockFace.EAST;
        };
    }

    private Block relativeBlock(Location base, BlockFace widthFace, int x, int y) {
        return base.clone()
                .add(widthFace.getModX() * x, y, widthFace.getModZ() * x)
                .getBlock();
    }

    private String key(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
