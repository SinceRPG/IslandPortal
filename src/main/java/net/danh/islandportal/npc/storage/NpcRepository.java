package net.danh.islandportal.npc.storage;

import net.danh.islandportal.npc.model.ManagedNpc;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class NpcRepository {

    private final Consumer<String> debug;
    private final File playerDataFolder;
    private final Map<String, ManagedNpc> npcsById = new ConcurrentHashMap<>();
    private final Map<UUID, String> npcIdByEntityId = new ConcurrentHashMap<>();

    public NpcRepository(JavaPlugin plugin, Consumer<String> debug) {
        this.debug = debug;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
    }

    public void load() {
        npcsById.clear();
        npcIdByEntityId.clear();
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
        for (ManagedNpc npc : npcsById.values()) {
            String ownerKey = npc.owner() == null || npc.owner().isBlank() ? "" : npc.owner();
            String fileName = fileNameByOwner.computeIfAbsent(ownerKey, ignored -> dataFileName(npc));
            YamlConfiguration data = groupedData.computeIfAbsent(fileName, ignored -> loadExisting(fileName));
            saveNpcTo(data, npc);
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
            clearStaleNpcSections(savedFiles);
        }
    }

    public Collection<ManagedNpc> all() {
        return List.copyOf(npcsById.values());
    }

    public ManagedNpc byEntity(UUID entityId) {
        String id = npcIdByEntityId.get(entityId);
        return id == null ? null : npcsById.get(id);
    }

    public ManagedNpc byId(String id) {
        return npcsById.get(id);
    }

    public boolean contains(String id) {
        return npcsById.containsKey(id);
    }

    public void add(ManagedNpc npc) {
        ManagedNpc previous = npcsById.put(npc.id(), npc);
        if (previous != null && previous.entityId() != null) {
            npcIdByEntityId.remove(previous.entityId());
        }
        if (npc.entityId() != null) {
            npcIdByEntityId.put(npc.entityId(), npc.id());
        }
    }

    public ManagedNpc remove(String id) {
        ManagedNpc npc = npcsById.remove(id);
        if (npc != null && npc.entityId() != null) {
            npcIdByEntityId.remove(npc.entityId());
        }
        return npc;
    }

    public ManagedNpc nearest(Location location, int radius) {
        ManagedNpc nearest = null;
        double nearestDistance = radius * radius;
        for (ManagedNpc npc : npcsById.values()) {
            Location npcLocation = npc.location();
            if (npcLocation == null || !npcLocation.getWorld().equals(location.getWorld())) {
                continue;
            }
            double distance = npcLocation.distanceSquared(location);
            if (distance <= nearestDistance) {
                nearest = npc;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void loadFile(YamlConfiguration data) {
        ConfigurationSection npcs = data.getConfigurationSection("npcs");
        if (npcs == null) {
            return;
        }
        for (String id : npcs.getKeys(false)) {
            ConfigurationSection section = npcs.getConfigurationSection(id);
            if (section == null || npcsById.containsKey(id)) {
                continue;
            }
            add(new ManagedNpc(
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
                    null
            ));
        }
    }

    private YamlConfiguration loadExisting(String fileName) {
        File file = new File(playerDataFolder, fileName);
        YamlConfiguration data = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        data.set("npcs", null);
        return data;
    }

    private void saveNpcTo(YamlConfiguration data, ManagedNpc npc) {
        String path = "npcs." + npc.id() + ".";
        data.set(path + "type", npc.type());
        data.set(path + "world", npc.world());
        data.set(path + "x", npc.x());
        data.set(path + "y", npc.y());
        data.set(path + "z", npc.z());
        data.set(path + "yaw", npc.yaw());
        data.set(path + "pitch", npc.pitch());
        data.set(path + "owner", npc.owner());
        data.set(path + "island-id", npc.islandId());
        data.set(path + "island-members", npc.islandMembers());
    }

    private void clearStaleNpcSections(Set<String> touchedFiles) {
        File[] files = playerDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (touchedFiles.contains(file.getName())) {
                continue;
            }
            YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
            if (!data.isConfigurationSection("npcs")) {
                continue;
            }
            data.set("npcs", null);
            try {
                data.save(file);
            } catch (IOException exception) {
                debug.accept("Could not clear stale npc data in playerdata/" + file.getName() + ": " + exception.getMessage());
            }
        }
    }

    private String dataFileName(ManagedNpc npc) {
        if (npc.owner() == null || npc.owner().isBlank()) {
            return "server.yml";
        }
        return npc.owner().replaceAll("[^A-Za-z0-9_.-]", "_") + ".yml";
    }
}
