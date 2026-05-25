package net.danh.islandportal.portal.storage;

import net.danh.islandportal.portal.config.PortalConfig;
import net.danh.islandportal.portal.model.AccessPolicy;
import net.danh.islandportal.portal.model.ManagedPortal;
import net.danh.islandportal.portal.model.PortalType;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PortalRepository {

    private final JavaPlugin plugin;
    private final PortalConfig config;
    private final Consumer<String> debug;
    private final File legacyDataFile;
    private final File playerDataFolder;
    private final Map<String, ManagedPortal> portalsById = new ConcurrentHashMap<>();
    private final Map<String, String> portalIdByBlock = new ConcurrentHashMap<>();
    private final Map<String, String> portalIdByTriggerBlock = new ConcurrentHashMap<>();

    public PortalRepository(JavaPlugin plugin, PortalConfig config, Consumer<String> debug) {
        this.plugin = plugin;
        this.config = config;
        this.debug = debug;
        this.legacyDataFile = new File(plugin.getDataFolder(), "data.yml");
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
    }

    public void load() {
        portalsById.clear();
        portalIdByBlock.clear();
        portalIdByTriggerBlock.clear();
        loadPlayerData();
        loadLegacyData();
    }

    public void save() {
        if (!playerDataFolder.exists() && !playerDataFolder.mkdirs()) {
            debug.accept("Could not create playerdata folder.");
            return;
        }
        Map<String, YamlConfiguration> groupedData = new ConcurrentHashMap<>();
        Map<String, String> fileNameByOwner = new ConcurrentHashMap<>();
        for (ManagedPortal portal : portalsById.values()) {
            String ownerKey = portal.owner() == null || portal.owner().isBlank() ? "" : portal.owner();
            String fileName = fileNameByOwner.computeIfAbsent(ownerKey, ignored -> dataFileName(portal));
            YamlConfiguration data = groupedData.computeIfAbsent(fileName, ignored -> new YamlConfiguration());
            savePortalTo(data, portal);
        }
        Set<String> savedFiles = ConcurrentHashMap.newKeySet();
        for (Map.Entry<String, YamlConfiguration> entry : groupedData.entrySet()) {
            try {
                File target = new File(playerDataFolder, entry.getKey());
                entry.getValue().save(target);
                savedFiles.add(entry.getKey());
            } catch (IOException exception) {
                debug.accept("Could not save playerdata/" + entry.getKey() + ": " + exception.getMessage());
            }
        }
        if (savedFiles.size() == groupedData.size()) {
            clearStalePlayerDataFiles(savedFiles);
        }
    }

    public Collection<ManagedPortal> all() {
        return List.copyOf(portalsById.values());
    }

    public ManagedPortal byId(String id) {
        return portalsById.get(id);
    }

    public boolean contains(String id) {
        return portalsById.containsKey(id);
    }

    public void add(ManagedPortal portal) {
        portalsById.put(portal.id(), portal);
        index(portal);
    }

    public ManagedPortal remove(String id) {
        ManagedPortal portal = portalsById.remove(id);
        if (portal != null) {
            unindex(portal);
        }
        return portal;
    }

    public ManagedPortal blockAt(Location location) {
        String id = portalIdByBlock.get(key(location));
        return id == null ? null : portalsById.get(id);
    }

    public ManagedPortal blockAt(String world, int x, int y, int z) {
        String id = portalIdByBlock.get(key(world, x, y, z));
        return id == null ? null : portalsById.get(id);
    }

    public ManagedPortal triggerAt(Location location) {
        String id = portalIdByTriggerBlock.get(key(location));
        return id == null ? null : portalsById.get(id);
    }

    public ManagedPortal triggerAt(String world, int x, int y, int z) {
        String id = portalIdByTriggerBlock.get(key(world, x, y, z));
        return id == null ? null : portalsById.get(id);
    }

    public ManagedPortal nearest(Location location, int radius) {
        ManagedPortal nearest = null;
        double nearestDistance = radius * radius;
        for (ManagedPortal portal : portalsById.values()) {
            Location base = portal.baseLocation();
            if (base == null || !base.getWorld().equals(location.getWorld())) {
                continue;
            }
            double distance = base.distanceSquared(location);
            if (distance <= nearestDistance) {
                nearest = portal;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void index(ManagedPortal portal) {
        for (String block : portal.blocks()) {
            portalIdByBlock.put(block, portal.id());
        }
        for (String block : portal.triggerBlocks()) {
            portalIdByTriggerBlock.put(block, portal.id());
        }
    }

    private void unindex(ManagedPortal portal) {
        for (String block : portal.blocks()) {
            portalIdByBlock.remove(block);
            portalIdByTriggerBlock.remove(block);
        }
        for (String block : portal.triggerBlocks()) {
            portalIdByTriggerBlock.remove(block);
        }
    }

    private void loadPlayerData() {
        if (!playerDataFolder.exists()) {
            return;
        }
        File[] files = playerDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            loadPortalFile(YamlConfiguration.loadConfiguration(file));
        }
    }

    private void loadLegacyData() {
        if (!legacyDataFile.exists() || hasPlayerDataFiles()) {
            return;
        }
        int before = portalsById.size();
        loadPortalFile(YamlConfiguration.loadConfiguration(legacyDataFile));
        if (portalsById.size() > before) {
            debug.accept("Migrated legacy data.yml portal data into playerdata files.");
            save();
        }
    }

    private boolean hasPlayerDataFiles() {
        File[] files = playerDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        return files != null && files.length > 0;
    }

    private void loadPortalFile(YamlConfiguration data) {
        ConfigurationSection portals = data.getConfigurationSection("portals");
        if (portals == null) {
            return;
        }
        for (String id : portals.getKeys(false)) {
            ConfigurationSection section = portals.getConfigurationSection(id);
            if (section == null || portalsById.containsKey(id)) {
                continue;
            }
            ManagedPortal portal = portalFromSection(id, section);
            portalsById.put(id, portal);
            index(portal);
        }
    }

    private ManagedPortal portalFromSection(String id, ConfigurationSection section) {
        PortalType type = config.type(section.getString("type", "spawn"));
        return new ManagedPortal(
                id,
                section.getString("type", "spawn"),
                section.getString("world"),
                section.getInt("x"),
                section.getInt("y"),
                section.getInt("z"),
                section.getString("facing", "SOUTH"),
                section.getString("owner"),
                section.getString("island-id"),
                section.getBoolean("default-portal", false),
                policy(section.getString("pickup-policy"), type, AccessKind.PICKUP),
                policy(section.getString("use-policy"), type, AccessKind.USE),
                policy(section.getString("configure-policy"), type, AccessKind.CONFIGURE),
                section.getStringList("island-members"),
                section.getStringList("blocks"),
                section.getStringList("trigger-blocks").isEmpty() ? section.getStringList("blocks") : section.getStringList("trigger-blocks"),
                section.getStringList("support-blocks"),
                section.getString("return.world"),
                section.getDouble("return.x"),
                section.getDouble("return.y"),
                section.getDouble("return.z"),
                (float) section.getDouble("return.yaw"),
                (float) section.getDouble("return.pitch")
        );
    }

    private AccessPolicy policy(String value, PortalType type, AccessKind kind) {
        if (value != null) {
            try {
                return AccessPolicy.valueOf(value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (type == null) {
            return AccessPolicy.ISLAND_MEMBERS;
        }
        return switch (kind) {
            case PICKUP -> type.pickupPolicy();
            case USE -> type.usePolicy();
            case CONFIGURE -> type.configurePolicy();
        };
    }

    private void savePortalTo(YamlConfiguration data, ManagedPortal portal) {
        String path = "portals." + portal.id() + ".";
        data.set(path + "type", portal.type());
        data.set(path + "world", portal.world());
        data.set(path + "x", portal.x());
        data.set(path + "y", portal.y());
        data.set(path + "z", portal.z());
        data.set(path + "facing", portal.facing());
        data.set(path + "owner", portal.owner());
        data.set(path + "island-id", portal.islandId());
        data.set(path + "default-portal", portal.defaultPortal());
        data.set(path + "pickup-policy", portal.pickupPolicy().name());
        data.set(path + "use-policy", portal.usePolicy().name());
        data.set(path + "configure-policy", portal.configurePolicy().name());
        data.set(path + "island-members", portal.islandMembers());
        data.set(path + "blocks", portal.blocks());
        data.set(path + "trigger-blocks", portal.triggerBlocks());
        data.set(path + "support-blocks", portal.supportBlocks());
        data.set(path + "return.world", portal.returnWorld());
        data.set(path + "return.x", portal.returnX());
        data.set(path + "return.y", portal.returnY());
        data.set(path + "return.z", portal.returnZ());
        data.set(path + "return.yaw", portal.returnYaw());
        data.set(path + "return.pitch", portal.returnPitch());
    }

    private void clearStalePlayerDataFiles(Set<String> keepFiles) {
        File[] files = playerDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (keepFiles.contains(file.getName())) {
                continue;
            }
            if (!file.delete()) {
                debug.accept("Could not clear stale playerdata file " + file.getName() + ".");
            }
        }
    }

    private String dataFileName(ManagedPortal portal) {
        if (portal.owner() == null || portal.owner().isBlank()) {
            return "server.yml";
        }
        // The repository may autosave from an async worker. Avoid OfflinePlayer lookups here because Bukkit identity APIs are not async-safe.
        return sanitizeFileName(portal.owner()) + ".yml";
    }

    private String sanitizeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private String key(Location location) {
        return key(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private enum AccessKind {
        PICKUP,
        USE,
        CONFIGURE
    }
}
