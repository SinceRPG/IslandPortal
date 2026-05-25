package net.danh.islandportal.portal.placement;

import net.danh.islandportal.portal.model.DefaultPortalPlacement;
import net.danh.islandportal.portal.model.PortalIslandSettings;
import net.danh.islandportal.portal.model.PortalShape;
import net.danh.islandportal.portal.model.PortalType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class PortalIslandPlacer {

    private final PortalBlockBuilder blockBuilder;
    private final Consumer<String> debug;

    public PortalIslandPlacer(PortalBlockBuilder blockBuilder, Consumer<String> debug) {
        this.blockBuilder = blockBuilder;
        this.debug = debug;
    }

    public DefaultPortalPlacement place(PortalType type, Location islandLocation) {
        PortalIslandSettings settings = type.portalIsland();
        BlockFace facing = settings.randomFacing() ? randomFacing() : type.islandFacing();
        if (!settings.enabled()) {
            return new DefaultPortalPlacement(islandLocation.clone().add(randomizedOffset(type)).getBlock().getLocation(), facing, List.of());
        }

        Location origin = islandLocation.clone().add(randomizedOffset(type)).getBlock().getLocation();
        for (Location platformCenter : platformCandidates(origin, settings.searchRadius(), settings.searchStep())) {
            if (!isClearForPortalIsland(platformCenter, type)) {
                continue;
            }
            List<String> supportBlocks = createPortalIsland(platformCenter, settings);
            Location portalBase = platformCenter.clone().add(settings.portalOffset()).getBlock().getLocation();
            debug.accept("Portal-island for " + type.id() + " selected clear location " + locationString(platformCenter) + ".");
            return new DefaultPortalPlacement(portalBase, facing, supportBlocks);
        }
        return null;
    }

    private List<Location> platformCandidates(Location origin, int searchRadius, int searchStep) {
        List<Location> candidates = new ArrayList<>();
        candidates.add(origin.clone());
        for (int radius = searchStep; radius <= searchRadius; radius += searchStep) {
            for (int x = -radius; x <= radius; x += searchStep) {
                candidates.add(origin.clone().add(x, 0, -radius));
                candidates.add(origin.clone().add(x, 0, radius));
            }
            for (int z = -radius + searchStep; z <= radius - searchStep; z += searchStep) {
                candidates.add(origin.clone().add(-radius, 0, z));
                candidates.add(origin.clone().add(radius, 0, z));
            }
        }
        return candidates;
    }

    private boolean isClearForPortalIsland(Location platformCenter, PortalType type) {
        PortalIslandSettings island = type.portalIsland();
        PortalShape shape = type.shape();
        Location portalBase = platformCenter.clone().add(island.portalOffset()).getBlock().getLocation();
        BlockFace widthFace = blockBuilder.widthFace(type.islandFacing());
        int portalEndX = portalBase.getBlockX() + widthFace.getModX() * (shape.width() + 1);
        int portalEndZ = portalBase.getBlockZ() + widthFace.getModZ() * (shape.width() + 1);
        int minX = Math.min(platformCenter.getBlockX() - island.platformRadius(), Math.min(portalBase.getBlockX(), portalEndX));
        int maxX = Math.max(platformCenter.getBlockX() + island.platformRadius(), Math.max(portalBase.getBlockX(), portalEndX));
        int minZ = Math.min(platformCenter.getBlockZ() - island.platformRadius(), Math.min(portalBase.getBlockZ(), portalEndZ));
        int maxZ = Math.max(platformCenter.getBlockZ() + island.platformRadius(), Math.max(portalBase.getBlockZ(), portalEndZ));
        int minY = platformCenter.getBlockY() - island.dirtDepth();
        int maxY = portalBase.getBlockY() + shape.height() + 2;

        for (int x = minX - island.clearance(); x <= maxX + island.clearance(); x++) {
            for (int y = minY - island.clearance(); y <= maxY + island.clearance(); y++) {
                for (int z = minZ - island.clearance(); z <= maxZ + island.clearance(); z++) {
                    if (!platformCenter.getWorld().getBlockAt(x, y, z).getType().isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private List<String> createPortalIsland(Location center, PortalIslandSettings settings) {
        List<String> blocks = new ArrayList<>();
        int radius = settings.platformRadius();
        int radiusSquared = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int distanceSquared = x * x + z * z;
                if (distanceSquared > radiusSquared) {
                    continue;
                }
                int edgeDepthTrim = distanceSquared > (radius - 1) * (radius - 1) ? 1 : 0;
                int depth = Math.max(1, settings.dirtDepth() - edgeDepthTrim);
                for (int y = 0; y > -depth; y--) {
                    Block block = center.clone().add(x, y, z).getBlock();
                    block.setType(y == 0 ? settings.topMaterial() : settings.fillMaterial(), false);
                    blocks.add(key(block.getLocation()));
                }
            }
        }
        return blocks;
    }

    private Vector randomizedOffset(PortalType type) {
        PortalIslandSettings settings = type.portalIsland();
        if (settings.randomMaxDistance() <= 0) {
            return type.islandOffset();
        }
        int min = Math.min(settings.randomMinDistance(), settings.randomMaxDistance());
        int max = Math.max(settings.randomMinDistance(), settings.randomMaxDistance());
        int distance = ThreadLocalRandom.current().nextInt(min, max + 1);
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        return type.islandOffset().clone().add(new Vector(Math.round(Math.cos(angle) * distance), 0, Math.round(Math.sin(angle) * distance)));
    }

    private BlockFace randomFacing() {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        return faces[ThreadLocalRandom.current().nextInt(faces.length)];
    }

    private String key(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private String locationString(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }
}
