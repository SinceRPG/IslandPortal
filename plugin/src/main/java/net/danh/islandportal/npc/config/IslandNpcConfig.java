package net.danh.islandportal.npc.config;

import net.danh.islandportal.npc.model.NpcClickAction;
import net.danh.islandportal.npc.model.NpcInteraction;
import net.danh.islandportal.npc.model.NpcMovementSettings;
import net.danh.islandportal.npc.model.NpcSpawnSearchSettings;
import net.danh.islandportal.npc.model.NpcType;
import net.danh.islandportal.npc.model.NpcUnlockSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IslandNpcConfig {

    private final JavaPlugin plugin;
    private final Map<String, NpcType> npcTypes = new ConcurrentHashMap<>();
    private FileConfiguration npcConfig;
    private boolean enabled;
    private boolean debug;
    private int interactionCooldownMillis;
    private int lookIntervalTicks;
    private int movementIntervalTicks;
    private int respawnCheckTicks;
    private int respawnDelayTicks;
    private int creationDelayTicks;

    public IslandNpcConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("island-npcs.enabled", true);
        debug = config.getBoolean("debug", false);
        interactionCooldownMillis = Math.max(1, config.getInt("island-npcs.interaction-cooldown-millis", 750));
        lookIntervalTicks = Math.max(1, config.getInt("island-npcs.look-interval-ticks", 10));
        movementIntervalTicks = Math.max(1, config.getInt("island-npcs.movement-interval-ticks", 10));
        respawnCheckTicks = Math.max(1, config.getInt("island-npcs.respawn-check-ticks", 40));
        respawnDelayTicks = Math.max(1, config.getInt("island-npcs.respawn-delay-ticks", 40));
        creationDelayTicks = Math.max(0, config.getInt("island-npcs.creation-delay-ticks", config.getInt("creation-delay-ticks", 60)));

        npcConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "npcs.yml"));
        npcTypes.clear();
        ConfigurationSection section = npcConfig.getConfigurationSection("npc-types");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection npcSection = section.getConfigurationSection(id);
            if (npcSection == null) {
                continue;
            }
            String normalizedId = normalize(id);
            NpcType type = new NpcType(
                    normalizedId,
                    entityType(npcSection.getString("entity-type", "VILLAGER")),
                    npcSection.getString("name", normalizedId),
                    npcSection.getBoolean("name-visible", true),
                    npcSection.getString("profession", ""),
                    npcSection.getBoolean("baby", false),
                    npcSection.getBoolean("glowing", false),
                    npcSection.getBoolean("silent", true),
                    npcSection.getBoolean("invulnerable", true),
                    npcSection.getBoolean("collidable", false),
                    npcSection.getBoolean("default-on-island", false),
                    vector(npcSection.getConfigurationSection("island-offset")),
                    spawnSearch(npcSection.getConfigurationSection("spawn-search")),
                    (float) npcSection.getDouble("yaw", 0.0),
                    (float) npcSection.getDouble("pitch", 0.0),
                    npcSection.getBoolean("look-at-player.enabled", true),
                    Math.max(1.0, npcSection.getDouble("look-at-player.radius", 8.0)),
                    movement(npcSection.getConfigurationSection("movement")),
                    unlock(npcSection.getConfigurationSection("unlock")),
                    interactions(npcSection.getConfigurationSection("interactions"))
            );
            npcTypes.put(normalizedId, type);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean debug() {
        return debug;
    }

    public int interactionCooldownMillis() {
        return interactionCooldownMillis;
    }

    public int lookIntervalTicks() {
        return lookIntervalTicks;
    }

    public int movementIntervalTicks() {
        return movementIntervalTicks;
    }

    public int respawnCheckTicks() {
        return respawnCheckTicks;
    }

    public int respawnDelayTicks() {
        return respawnDelayTicks;
    }

    public int creationDelayTicks() {
        return creationDelayTicks;
    }

    public Collection<NpcType> npcTypes() {
        return npcTypes.values();
    }

    public Collection<NpcType> defaultIslandNpcs() {
        return npcTypes.values().stream().filter(NpcType::defaultOnIsland).toList();
    }

    public NpcType type(String id) {
        return npcTypes.get(normalize(id));
    }

    private NpcUnlockSettings unlock(ConfigurationSection section) {
        if (section == null) {
            return new NpcUnlockSettings(true, List.of(), 0);
        }
        return new NpcUnlockSettings(
                section.getBoolean("default-unlocked", true),
                section.getStringList("permissions"),
                Math.max(0, section.getInt("min-island-members", 0))
        );
    }

    private NpcSpawnSearchSettings spawnSearch(ConfigurationSection section) {
        if (section == null) {
            return new NpcSpawnSearchSettings(true, 8, 6, 1);
        }
        return new NpcSpawnSearchSettings(
                section.getBoolean("enabled", true),
                Math.max(0, section.getInt("horizontal-radius", 8)),
                Math.max(0, section.getInt("vertical-radius", 6)),
                Math.max(1, section.getInt("step", 1))
        );
    }

    private NpcMovementSettings movement(ConfigurationSection section) {
        if (section == null) {
            return new NpcMovementSettings(false, 4.0, 20, 0.35, 12);
        }
        return new NpcMovementSettings(
                section.getBoolean("enabled", false),
                Math.max(0.0, section.getDouble("radius", 4.0)),
                Math.max(1, section.getInt("interval-ticks", 20)),
                Math.max(0.05, section.getDouble("step-distance", 0.35)),
                Math.max(1, section.getInt("target-attempts", 12))
        );
    }

    private Map<NpcClickAction, NpcInteraction> interactions(ConfigurationSection section) {
        Map<NpcClickAction, NpcInteraction> interactions = new EnumMap<>(NpcClickAction.class);
        interactions.put(NpcClickAction.LEFT, interaction(section == null ? null : section.getConfigurationSection("left-click")));
        interactions.put(NpcClickAction.RIGHT, interaction(section == null ? null : section.getConfigurationSection("right-click")));
        return interactions;
    }

    private NpcInteraction interaction(ConfigurationSection section) {
        if (section == null) {
            return new NpcInteraction(List.of(), List.of(), List.of());
        }
        return new NpcInteraction(
                section.getStringList("messages"),
                section.getStringList("player-commands"),
                section.getStringList("console-commands")
        );
    }

    private Vector vector(ConfigurationSection section) {
        if (section == null) {
            return new Vector(0, 1, 0);
        }
        return new Vector(section.getDouble("x", 0.0), section.getDouble("y", 1.0), section.getDouble("z", 0.0));
    }

    private EntityType entityType(String value) {
        try {
            EntityType type = EntityType.valueOf(value.toUpperCase(Locale.ROOT));
            return type.isAlive() ? type : EntityType.VILLAGER;
        } catch (IllegalArgumentException exception) {
            return EntityType.VILLAGER;
        }
    }

    private String normalize(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }
}
