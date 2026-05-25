package net.danh.islandportal.portal.placement;

import net.danh.islandportal.portal.model.DefaultPortalPlacement;
import net.danh.islandportal.portal.model.PortalIslandMode;
import net.danh.islandportal.portal.model.PortalIslandSettings;
import net.danh.islandportal.portal.model.PortalShape;
import net.danh.islandportal.portal.model.PortalType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class PortalIslandPlacer {

    private final JavaPlugin plugin;
    private final PortalBlockBuilder blockBuilder;
    private final WorldEditSchematicPaster schematicPaster;
    private final Consumer<String> debug;
    private final boolean requireSingleChunkPlacement;

    public PortalIslandPlacer(JavaPlugin plugin, PortalBlockBuilder blockBuilder, Consumer<String> debug, boolean requireSingleChunkPlacement) {
        this.plugin = plugin;
        this.blockBuilder = blockBuilder;
        this.schematicPaster = worldEditAvailable() ? new WorldEditSchematicPaster(debug) : null;
        this.debug = debug;
        this.requireSingleChunkPlacement = requireSingleChunkPlacement;
    }

    public DefaultPortalPlacement place(PortalType type, Location islandLocation) {
        return place(type, islandLocation, false);
    }

    public DefaultPortalPlacement place(PortalType type, Location islandLocation, boolean randomizeExactOffset) {
        PortalIslandSettings settings = type.portalIsland();
        BlockFace facing = settings.randomFacing() ? randomFacing() : type.islandFacing();
        if (!settings.enabled()) {
            return new DefaultPortalPlacement(islandLocation.clone().add(randomizedOffset(type, randomizeExactOffset)).getBlock().getLocation(), facing, List.of());
        }
        if (settings.mode() == PortalIslandMode.SCHEMATIC) {
            DefaultPortalPlacement schematicPlacement = placeSchematic(type, islandLocation, facing, settings, randomizeExactOffset);
            if (schematicPlacement != null) {
                return schematicPlacement;
            }
            debug.accept("Falling back to generated portal-island platform for " + type.id() + ".");
        }

        Location origin = placementOrigin(type, islandLocation, settings, null, randomizeExactOffset);
        boolean loggedChunkSkip = false;
        for (Location platformCenter : platformCandidates(origin, settings.searchRadius(), settings.searchStep())) {
            Bounds bounds = platformBounds(platformCenter, type, facing);
            if (requireSingleChunkPlacement && !isOwnedBySameChunk(platformCenter, bounds)) {
                if (!loggedChunkSkip) {
                    debug.accept("Skipped one or more portal-island candidates for " + type.id() + " because their full clearance bounds cross chunk boundaries on Folia.");
                    loggedChunkSkip = true;
                }
                continue;
            }
            if (!isClear(platformCenter, bounds)) {
                continue;
            }
            List<String> supportBlocks = createPortalIsland(platformCenter, settings);
            Location portalBase = platformCenter.clone().add(settings.portalOffset()).getBlock().getLocation();
            debug.accept("Portal-island for " + type.id() + " selected clear location " + locationString(platformCenter) + ".");
            return new DefaultPortalPlacement(portalBase, facing, supportBlocks);
        }
        return null;
    }

    private DefaultPortalPlacement placeSchematic(PortalType type, Location islandLocation, BlockFace facing, PortalIslandSettings settings, boolean randomizeExactOffset) {
        if (schematicPaster == null) {
            debug.accept("WorldEdit/FAWE is not available for schematic portal-island " + type.id() + ".");
            return null;
        }
        if (settings.schematic().isBlank()) {
            debug.accept("No schematic file configured for schematic portal-island " + type.id() + ".");
            return null;
        }
        File schematicFile = new File(plugin.getDataFolder(), settings.schematic());
        if (!schematicFile.isFile()) {
            debug.accept("Configured portal-island schematic does not exist: " + schematicFile.getPath());
            return null;
        }
        WorldEditSchematicPaster.LoadedSchematic schematic;
        try {
            schematic = schematicPaster.load(schematicFile);
        } catch (Exception exception) {
            debug.accept("Could not load portal-island schematic " + schematicFile.getName() + ": " + exception.getMessage());
            return null;
        }

        Location origin = placementOrigin(type, islandLocation, settings, schematic, randomizeExactOffset);
        boolean loggedChunkSkip = false;
        for (Location pasteOrigin : platformCandidates(origin, settings.searchRadius(), settings.searchStep())) {
            Bounds bounds = schematicBounds(pasteOrigin, type, facing, schematic);
            if (requireSingleChunkPlacement && !isOwnedBySameChunk(pasteOrigin, bounds)) {
                if (!loggedChunkSkip) {
                    debug.accept("Skipped one or more schematic portal-island candidates for " + type.id() + " because their full clearance bounds cross chunk boundaries on Folia.");
                    loggedChunkSkip = true;
                }
                continue;
            }
            if (!isClear(pasteOrigin, bounds)) {
                continue;
            }
            try {
                List<String> supportBlocks = schematic.paste(pasteOrigin, settings.schematicIgnoreAir());
                Location portalBase = pasteOrigin.clone().add(settings.portalOffset()).getBlock().getLocation();
                debug.accept("Schematic portal-island for " + type.id() + " pasted at " + locationString(pasteOrigin) + ".");
                return new DefaultPortalPlacement(portalBase, facing, supportBlocks);
            } catch (Exception exception) {
                debug.accept("Could not paste portal-island schematic " + schematicFile.getName() + ": " + exception.getMessage());
                return null;
            }
        }
        return null;
    }

    private List<Location> platformCandidates(Location origin, int searchRadius, int searchStep) {
        List<Location> candidates = new CopyOnWriteArrayList<>();
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

    private Location placementOrigin(PortalType type, Location islandLocation, PortalIslandSettings settings, WorldEditSchematicPaster.LoadedSchematic schematic, boolean randomizeExactOffset) {
        Location origin = islandLocation.clone().add(randomizedOffset(type, randomizeExactOffset)).getBlock().getLocation();
        if (!settings.alignToIslandY()) {
            return origin;
        }
        int anchorY = schematic == null ? 0 : settings.schematicAnchorY();
        origin.setY(islandLocation.getBlockY() - anchorY + settings.yOffset());
        return origin.getBlock().getLocation();
    }

    private Bounds platformBounds(Location platformCenter, PortalType type, BlockFace facing) {
        PortalIslandSettings island = type.portalIsland();
        PortalShape shape = type.shape();
        Location portalBase = platformCenter.clone().add(island.portalOffset()).getBlock().getLocation();
        BlockFace widthFace = blockBuilder.widthFace(facing);
        int portalEndX = portalBase.getBlockX() + widthFace.getModX() * (shape.width() + 1);
        int portalEndZ = portalBase.getBlockZ() + widthFace.getModZ() * (shape.width() + 1);
        int minX = Math.min(platformCenter.getBlockX() - island.platformRadius(), Math.min(portalBase.getBlockX(), portalEndX));
        int maxX = Math.max(platformCenter.getBlockX() + island.platformRadius(), Math.max(portalBase.getBlockX(), portalEndX));
        int minZ = Math.min(platformCenter.getBlockZ() - island.platformRadius(), Math.min(portalBase.getBlockZ(), portalEndZ));
        int maxZ = Math.max(platformCenter.getBlockZ() + island.platformRadius(), Math.max(portalBase.getBlockZ(), portalEndZ));
        int minY = platformCenter.getBlockY() - island.dirtDepth();
        int maxY = portalBase.getBlockY() + shape.height() + 2;

        return new Bounds(minX, maxX, minY, maxY, minZ, maxZ, island.clearance());
    }

    private Bounds schematicBounds(Location pasteOrigin, PortalType type, BlockFace facing, WorldEditSchematicPaster.LoadedSchematic schematic) {
        PortalIslandSettings island = type.portalIsland();
        PortalShape shape = type.shape();
        Location portalBase = pasteOrigin.clone().add(island.portalOffset()).getBlock().getLocation();
        BlockFace widthFace = blockBuilder.widthFace(facing);
        int portalEndX = portalBase.getBlockX() + widthFace.getModX() * (shape.width() + 1);
        int portalEndZ = portalBase.getBlockZ() + widthFace.getModZ() * (shape.width() + 1);
        int minX = Math.min(pasteOrigin.getBlockX() + schematic.minX(), Math.min(portalBase.getBlockX(), portalEndX));
        int maxX = Math.max(pasteOrigin.getBlockX() + schematic.maxX(), Math.max(portalBase.getBlockX(), portalEndX));
        int minY = Math.min(pasteOrigin.getBlockY() + schematic.minY(), portalBase.getBlockY());
        int maxY = Math.max(pasteOrigin.getBlockY() + schematic.maxY(), portalBase.getBlockY() + shape.height() + 1);
        int minZ = Math.min(pasteOrigin.getBlockZ() + schematic.minZ(), Math.min(portalBase.getBlockZ(), portalEndZ));
        int maxZ = Math.max(pasteOrigin.getBlockZ() + schematic.maxZ(), Math.max(portalBase.getBlockZ(), portalEndZ));

        return new Bounds(minX, maxX, minY, maxY, minZ, maxZ, island.clearance());
    }

    private boolean isClear(Location origin, Bounds bounds) {
        for (int x = bounds.minXWithClearance(); x <= bounds.maxXWithClearance(); x++) {
            for (int y = bounds.minYWithClearance(); y <= bounds.maxYWithClearance(); y++) {
                for (int z = bounds.minZWithClearance(); z <= bounds.maxZWithClearance(); z++) {
                    if (!origin.getWorld().getBlockAt(x, y, z).getType().isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isOwnedBySameChunk(Location origin, Bounds bounds) {
        int chunkX = origin.getBlockX() >> 4;
        int chunkZ = origin.getBlockZ() >> 4;
        return (bounds.minXWithClearance() >> 4) == chunkX
                && (bounds.maxXWithClearance() >> 4) == chunkX
                && (bounds.minZWithClearance() >> 4) == chunkZ
                && (bounds.maxZWithClearance() >> 4) == chunkZ;
    }

    private List<String> createPortalIsland(Location center, PortalIslandSettings settings) {
        List<String> blocks = new CopyOnWriteArrayList<>();
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

    private Vector randomizedOffset(PortalType type, boolean randomizeExactOffset) {
        PortalIslandSettings settings = type.portalIsland();
        if (settings.randomMaxDistance() <= 0 && !randomizeExactOffset) {
            return type.islandOffset();
        }
        int min = Math.min(settings.randomMinDistance(), settings.randomMaxDistance());
        int max = Math.max(settings.randomMinDistance(), settings.randomMaxDistance());
        if (max <= 0) {
            min = Math.max(1, settings.searchStep());
            max = Math.max(min, settings.searchRadius());
        }
        int distance = ThreadLocalRandom.current().nextInt(min, max + 1);
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        return type.islandOffset().clone().add(new Vector(Math.round(Math.cos(angle) * distance), 0, Math.round(Math.sin(angle) * distance)));
    }

    private BlockFace randomFacing() {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        return faces[ThreadLocalRandom.current().nextInt(faces.length)];
    }

    private boolean worldEditAvailable() {
        try {
            Class.forName("com.sk89q.worldedit.WorldEdit");
            Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        } catch (ClassNotFoundException ignored) {
            return false;
        }
        return Bukkit.getPluginManager().isPluginEnabled("WorldEdit") || Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
    }

    private String key(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private String locationString(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, int clearance) {

        private int minXWithClearance() {
            return minX - clearance;
        }

        private int maxXWithClearance() {
            return maxX + clearance;
        }

        private int minYWithClearance() {
            return minY - clearance;
        }

        private int maxYWithClearance() {
            return maxY + clearance;
        }

        private int minZWithClearance() {
            return minZ - clearance;
        }

        private int maxZWithClearance() {
            return maxZ + clearance;
        }
    }
}
