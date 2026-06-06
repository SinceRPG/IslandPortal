package net.danh.islandportal.minion.storage;

import net.danh.islandportal.minion.model.ManagedMinion;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class MinionRepository {

    private final Consumer<String> debug;
    private final File playerDataFolder;
    private final Map<String, ManagedMinion> minionsById = new ConcurrentHashMap<>();
    private final Map<UUID, String> minionIdByEntityId = new ConcurrentHashMap<>();

    public MinionRepository(JavaPlugin plugin, Consumer<String> debug) {
        this.debug = debug;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
    }

    public void load() {
        minionsById.clear();
        minionIdByEntityId.clear();
        if (!playerDataFolder.exists()) {
            return;
        }
        File[] files = playerDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            loadFile(YamlConfiguration.loadConfiguration(file));
        }
    }

    public void save() {
        if (!playerDataFolder.exists() && !playerDataFolder.mkdirs()) {
            debug.accept("Could not create playerdata folder.");
            return;
        }
        Map<String, YamlConfiguration> groupedData = new ConcurrentHashMap<>();
        Map<String, String> fileNameByOwner = new ConcurrentHashMap<>();
        for (ManagedMinion minion : minionsById.values()) {
            String ownerKey = minion.owner() == null || minion.owner().isBlank() ? "" : minion.owner();
            String fileName = fileNameByOwner.computeIfAbsent(ownerKey, ignored -> dataFileName(minion));
            YamlConfiguration data = groupedData.computeIfAbsent(fileName, this::loadExisting);
            saveMinionTo(data, minion);
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
            clearStaleMinionSections(savedFiles);
        }
    }

    public Collection<ManagedMinion> all() {
        return List.copyOf(minionsById.values());
    }

    public ManagedMinion byId(String id) {
        return minionsById.get(id);
    }

    public ManagedMinion byEntity(UUID entityId) {
        String id = minionIdByEntityId.get(entityId);
        return id == null ? null : minionsById.get(id);
    }

    public boolean contains(String id) {
        return minionsById.containsKey(id);
    }

    public void add(ManagedMinion minion) {
        ManagedMinion previous = minionsById.put(minion.id(), minion);
        if (previous != null && previous.entityId() != null) {
            minionIdByEntityId.remove(previous.entityId());
        }
        if (minion.entityId() != null) {
            minionIdByEntityId.put(minion.entityId(), minion.id());
        }
    }

    public ManagedMinion remove(String id) {
        ManagedMinion minion = minionsById.remove(id);
        if (minion != null && minion.entityId() != null) {
            minionIdByEntityId.remove(minion.entityId());
        }
        return minion;
    }

    public ManagedMinion nearest(Location location, int radius) {
        ManagedMinion nearest = null;
        double nearestDistance = radius * radius;
        for (ManagedMinion minion : minionsById.values()) {
            Location minionLocation = minion.location();
            if (minionLocation == null || !minionLocation.getWorld().equals(location.getWorld())) {
                continue;
            }
            double distance = minionLocation.distanceSquared(location);
            if (distance <= nearestDistance) {
                nearest = minion;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public long countByOwnerAndType(String owner, String type) {
        return minionsById.values().stream()
                .filter(minion -> owner != null && owner.equals(minion.owner()))
                .filter(minion -> type.equals(minion.type()))
                .count();
    }

    public long countByIsland(String islandId) {
        return minionsById.values().stream()
                .filter(minion -> islandId != null && islandId.equals(minion.islandId()))
                .count();
    }

    private void loadFile(YamlConfiguration data) {
        ConfigurationSection minions = data.getConfigurationSection("minions");
        if (minions == null) {
            return;
        }
        for (String id : minions.getKeys(false)) {
            ConfigurationSection section = minions.getConfigurationSection(id);
            if (section == null || minionsById.containsKey(id)) {
                continue;
            }
            add(new ManagedMinion(
                    id,
                    section.getString("type", ""),
                    section.getString("world", ""),
                    section.getDouble("x"),
                    section.getDouble("y"),
                    section.getDouble("z"),
                    (float) section.getDouble("yaw"),
                    (float) section.getDouble("pitch"),
                    section.getString("owner"),
                    section.getString("island-id"),
                    section.getStringList("island-members"),
                    section.getString("custom-name", ""),
                    section.getString("chest.world", ""),
                    section.getInt("chest.x", 0),
                    section.getInt("chest.y", 0),
                    section.getInt("chest.z", 0),
                    Math.max(1, section.getInt("tier", 1)),
                    section.getLong("last-production-millis", System.currentTimeMillis()),
                    section.getString("fuel-id", ""),
                    section.getLong("fuel-until-millis", 0L),
                    Math.max(1.0, section.getDouble("fuel-multiplier", 1.0)),
                    section.getString("booster-id", ""),
                    section.getLong("booster-until-millis", 0L),
                    Math.max(1.0, section.getDouble("booster-multiplier", 1.0)),
                    Math.max(0L, section.getLong("action-count", section.getLong("statistics", 0L))),
                    storage(section.getConfigurationSection("storage")),
                    null
            ));
        }
    }

    private Map<String, Integer> storage(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Integer> storage = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            int amount = section.getInt(key, 0);
            if (amount > 0) {
                storage.put(key, amount);
            }
        }
        return storage;
    }

    private YamlConfiguration loadExisting(String fileName) {
        File file = new File(playerDataFolder, fileName);
        YamlConfiguration data = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        data.set("minions", null);
        return data;
    }

    private void saveMinionTo(YamlConfiguration data, ManagedMinion minion) {
        String path = "minions." + minion.id() + ".";
        data.set(path + "type", minion.type());
        data.set(path + "world", minion.world());
        data.set(path + "x", minion.x());
        data.set(path + "y", minion.y());
        data.set(path + "z", minion.z());
        data.set(path + "yaw", minion.yaw());
        data.set(path + "pitch", minion.pitch());
        data.set(path + "owner", minion.owner());
        data.set(path + "island-id", minion.islandId());
        data.set(path + "island-members", minion.islandMembers());
        data.set(path + "custom-name", minion.customName());
        data.set(path + "chest.world", minion.chestWorld());
        data.set(path + "chest.x", minion.chestX());
        data.set(path + "chest.y", minion.chestY());
        data.set(path + "chest.z", minion.chestZ());
        data.set(path + "tier", minion.tier());
        data.set(path + "last-production-millis", minion.lastProductionMillis());
        data.set(path + "fuel-id", minion.fuelId());
        data.set(path + "fuel-until-millis", minion.fuelUntilMillis());
        data.set(path + "fuel-multiplier", minion.fuelMultiplier());
        data.set(path + "booster-id", minion.boosterId());
        data.set(path + "booster-until-millis", minion.boosterUntilMillis());
        data.set(path + "booster-multiplier", minion.boosterMultiplier());
        data.set(path + "action-count", minion.actionCount());
        data.set(path + "storage", null);
        for (Map.Entry<String, Integer> entry : minion.storage().entrySet()) {
            data.set(path + "storage." + entry.getKey(), entry.getValue());
        }
    }

    private void clearStaleMinionSections(Set<String> touchedFiles) {
        File[] files = playerDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (touchedFiles.contains(file.getName())) {
                continue;
            }
            YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
            if (!data.isConfigurationSection("minions")) {
                continue;
            }
            data.set("minions", null);
            try {
                data.save(file);
            } catch (IOException exception) {
                debug.accept("Could not clear stale minion data in playerdata/" + file.getName() + ": " + exception.getMessage());
            }
        }
    }

    private String dataFileName(ManagedMinion minion) {
        if (minion.owner() == null || minion.owner().isBlank()) {
            return "server.yml";
        }
        return minion.owner().replaceAll("[^A-Za-z0-9_.-]", "_") + ".yml";
    }
}
