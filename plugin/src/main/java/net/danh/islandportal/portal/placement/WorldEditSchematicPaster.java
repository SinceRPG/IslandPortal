package net.danh.islandportal.portal.placement;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class WorldEditSchematicPaster {

    private final Consumer<String> debug;

    public WorldEditSchematicPaster(Consumer<String> debug) {
        this.debug = debug;
    }

    public LoadedSchematic load(File file) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IOException("unsupported schematic format");
        }
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();
            return new LoadedSchematic(clipboard, debug);
        }
    }

    public static final class LoadedSchematic {

        private final Clipboard clipboard;
        private final Consumer<String> debug;
        private final BlockVector3 origin;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private LoadedSchematic(Clipboard clipboard, Consumer<String> debug) {
            this.clipboard = clipboard;
            this.debug = debug;
            this.origin = clipboard.getOrigin();
            Region region = clipboard.getRegion();
            BlockVector3 minimum = region.getMinimumPoint().subtract(origin);
            BlockVector3 maximum = region.getMaximumPoint().subtract(origin);
            this.minX = minimum.x();
            this.minY = minimum.y();
            this.minZ = minimum.z();
            this.maxX = maximum.x();
            this.maxY = maximum.y();
            this.maxZ = maximum.z();
        }

        public int minX() {
            return minX;
        }

        public int minY() {
            return minY;
        }

        public int minZ() {
            return minZ;
        }

        public int maxX() {
            return maxX;
        }

        public int maxY() {
            return maxY;
        }

        public int maxZ() {
            return maxZ;
        }

        public List<String> paste(Location pasteOrigin, boolean ignoreAir) throws Exception {
            if (pasteOrigin.getWorld() == null) {
                return List.of();
            }
            BlockVector3 to = BlockVector3.at(pasteOrigin.getBlockX(), pasteOrigin.getBlockY(), pasteOrigin.getBlockZ());
            List<String> supportBlocks = supportBlocks(pasteOrigin, ignoreAir);
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(pasteOrigin.getWorld()))) {
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(to)
                        .ignoreAirBlocks(ignoreAir)
                        .build();
                Operations.complete(operation);
            }
            debug.accept("Tracked " + supportBlocks.size() + " schematic support block(s).");
            return supportBlocks;
        }

        private List<String> supportBlocks(Location pasteOrigin, boolean ignoreAir) {
            List<String> blocks = new CopyOnWriteArrayList<>();
            for (BlockVector3 block : clipboard.getRegion()) {
                if (ignoreAir && clipboard.getBlock(block).getBlockType().getMaterial().isAir()) {
                    continue;
                }
                BlockVector3 relative = block.subtract(origin);
                blocks.add(key(
                        pasteOrigin.getWorld().getName(),
                        pasteOrigin.getBlockX() + relative.x(),
                        pasteOrigin.getBlockY() + relative.y(),
                        pasteOrigin.getBlockZ() + relative.z()
                ));
            }
            return blocks;
        }

        private String key(String world, int x, int y, int z) {
            return world + ":" + x + ":" + y + ":" + z;
        }
    }
}
