package net.danh.islandportal.portal.config;

import net.danh.islandportal.portal.model.AccessPolicy;
import net.danh.islandportal.portal.model.MenuConfig;
import net.danh.islandportal.portal.model.MenuItemConfig;
import net.danh.islandportal.portal.model.PortalAction;
import net.danh.islandportal.portal.model.PortalIslandMode;
import net.danh.islandportal.portal.model.PortalIslandSettings;
import net.danh.islandportal.portal.model.PortalItemSettings;
import net.danh.islandportal.portal.model.PortalPermissions;
import net.danh.islandportal.portal.model.PortalShape;
import net.danh.islandportal.portal.model.PortalType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalConfig {

    private final JavaPlugin plugin;
    private final Map<String, PortalType> portalTypes = new ConcurrentHashMap<>();
    private File portalsFile;
    private FileConfiguration portalsConfig;
    private FileConfiguration messagesConfig;
    private FileConfiguration menusConfig;
    private MenuConfig settingsMenu;
    private boolean enabled;
    private boolean debug;
    private int islandCleanupRadius;
    private int creationDelayTicks;
    private int creationRetryAttempts;
    private int creationRetryDelayTicks;
    private int autosaveIntervalMinutes;
    private int vanillaPortalCooldownTicks;
    private int useCooldownMillis;
    private int portalNearScanHorizontal;
    private int portalNearScanBelow;
    private int portalNearScanAbove;
    private boolean bentoBoxHook;
    private boolean superiorSkyblockHook;
    private boolean skylliaHook;
    private String commandDescription;
    private List<String> commandAliases;
    private String defaultPortalType;

    public PortalConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        portalsFile = new File(plugin.getDataFolder(), "portals.yml");
        portalsConfig = YamlConfiguration.loadConfiguration(portalsFile);
        messagesConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        menusConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "menus.yml"));

        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("enabled", true);
        debug = config.getBoolean("debug", false);
        islandCleanupRadius = Math.max(1, config.getInt("island-cleanup-radius", 100));
        creationDelayTicks = Math.max(0, config.getInt("creation-delay-ticks", 60));
        creationRetryAttempts = Math.max(1, config.getInt("creation-retry-attempts", 5));
        creationRetryDelayTicks = Math.max(1, config.getInt("creation-retry-delay-ticks", 40));
        autosaveIntervalMinutes = Math.max(1, config.getInt("runtime.autosave-interval-minutes", 10));
        vanillaPortalCooldownTicks = Math.max(1, config.getInt("runtime.vanilla-portal-cooldown-ticks", 200));
        useCooldownMillis = Math.max(1, config.getInt("runtime.use-cooldown-millis", 1000));
        portalNearScanHorizontal = Math.max(0, config.getInt("runtime.portal-near-scan.horizontal", 2));
        portalNearScanBelow = Math.max(0, config.getInt("runtime.portal-near-scan.below", 1));
        portalNearScanAbove = Math.max(0, config.getInt("runtime.portal-near-scan.above", 2));
        bentoBoxHook = config.getBoolean("hooks.bentobox", true);
        superiorSkyblockHook = config.getBoolean("hooks.superior-skyblock", true);
        skylliaHook = config.getBoolean("hooks.skyllia", true);
        commandDescription = config.getString("commands.description", "Manage island portals");
        commandAliases = config.getStringList("commands.aliases");
        if (commandAliases.isEmpty()) {
            commandAliases = List.of("ip");
        }
        defaultPortalType = normalize(portalsConfig.getString("default-portal-type", "spawn"));
        portalTypes.clear();
        settingsMenu = menu("settings");

        ConfigurationSection section = portalsConfig.getConfigurationSection("portal-types");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection typeSection = section.getConfigurationSection(id);
            if (typeSection == null) {
                continue;
            }
            String normalizedId = normalize(id);
            PortalType type = new PortalType(
                    normalizedId,
                    item(typeSection, normalizedId),
                    shape(typeSection),
                    new Vector(
                            typeSection.getInt("island-offset.x", 3),
                            typeSection.getInt("island-offset.y", 0),
                            typeSection.getInt("island-offset.z", 0)
                    ),
                    blockFace(typeSection.getString("island-facing", "SOUTH")),
                    portalIsland(typeSection.getConfigurationSection("portal-island")),
                    typeSection.getBoolean("default-on-island", normalizedId.equals(defaultPortalType)),
                    typeSection.getBoolean("consume-on-place", true),
                    typeSection.getBoolean("give-item-on-break", true),
                    accessPolicy(typeSection.getString("default-pickup-policy", "ISLAND_MEMBERS"), AccessPolicy.ISLAND_MEMBERS),
                    accessPolicy(typeSection.getString("default-use-policy", "ISLAND_MEMBERS"), AccessPolicy.ISLAND_MEMBERS),
                    accessPolicy(typeSection.getString("default-configure-policy", "OWNER"), AccessPolicy.OWNER),
                    permissions(typeSection.getConfigurationSection("permissions")),
                    action(typeSection.getString("action.mode", "TELEPORT")),
                    target(typeSection.getConfigurationSection("action.target")),
                    typeSection.getStringList("action.commands")
            );
            portalTypes.put(normalizedId, type);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public int creationDelayTicks() {
        return creationDelayTicks;
    }

    public int creationRetryAttempts() {
        return creationRetryAttempts;
    }

    public int creationRetryDelayTicks() {
        return creationRetryDelayTicks;
    }

    public boolean debug() {
        return debug;
    }

    public int islandCleanupRadius() {
        return islandCleanupRadius;
    }

    public int autosaveIntervalMinutes() {
        return autosaveIntervalMinutes;
    }

    public int vanillaPortalCooldownTicks() {
        return vanillaPortalCooldownTicks;
    }

    public int useCooldownMillis() {
        return useCooldownMillis;
    }

    public int portalNearScanHorizontal() {
        return portalNearScanHorizontal;
    }

    public int portalNearScanBelow() {
        return portalNearScanBelow;
    }

    public int portalNearScanAbove() {
        return portalNearScanAbove;
    }

    public boolean bentoBoxHook() {
        return bentoBoxHook;
    }

    public boolean superiorSkyblockHook() {
        return superiorSkyblockHook;
    }

    public boolean skylliaHook() {
        return skylliaHook;
    }

    public String commandDescription() {
        return commandDescription;
    }

    public List<String> commandAliases() {
        return commandAliases;
    }

    public Collection<PortalType> defaultIslandPortalTypes() {
        return portalTypes.values().stream().filter(PortalType::defaultOnIsland).toList();
    }

    public Collection<PortalType> portalTypes() {
        return portalTypes.values();
    }

    public PortalType type(String id) {
        return portalTypes.get(normalize(id));
    }

    public PortalType defaultType() {
        PortalType configured = type(defaultPortalType);
        if (configured != null) {
            return configured;
        }
        return portalTypes.values().stream().findFirst().orElse(null);
    }

    public void setTarget(String typeId, Location location) {
        String path = "portal-types." + normalize(typeId) + ".action.";
        portalsConfig.set(path + "mode", PortalAction.TELEPORT.name());
        portalsConfig.set(path + "target.world", location.getWorld().getName());
        portalsConfig.set(path + "target.x", location.getX());
        portalsConfig.set(path + "target.y", location.getY());
        portalsConfig.set(path + "target.z", location.getZ());
        portalsConfig.set(path + "target.yaw", location.getYaw());
        portalsConfig.set(path + "target.pitch", location.getPitch());
        try {
            portalsConfig.save(portalsFile);
        } catch (IOException exception) {
            debug("Could not save portals.yml: " + exception.getMessage());
        }
        reload();
    }

    public String message(String path) {
        return messagesConfig.getString(path, path);
    }

    public MenuConfig settingsMenu() {
        return settingsMenu;
    }

    private PortalAction action(String value) {
        try {
            return PortalAction.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return PortalAction.TELEPORT;
        }
    }

    private AccessPolicy accessPolicy(String value, AccessPolicy fallback) {
        try {
            return AccessPolicy.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private PortalPermissions permissions(ConfigurationSection section) {
        if (section == null) {
            return new PortalPermissions("", "", "", "");
        }
        return new PortalPermissions(
                section.getString("place", ""),
                section.getString("use", ""),
                section.getString("pickup", ""),
                section.getString("configure", "")
        );
    }

    private PortalItemSettings item(ConfigurationSection typeSection, String id) {
        ConfigurationSection section = typeSection.getConfigurationSection("item");
        if (section == null) {
            return new PortalItemSettings(
                    material(typeSection.getString("item.material"), Material.NETHER_STAR),
                    typeSection.getString("display-name", id),
                    typeSection.getStringList("lore"),
                    typeSection.isInt("custom-model-data") ? typeSection.getInt("custom-model-data") : null,
                    typeSection.getBoolean("unbreakable", false),
                    typeSection.isBoolean("enchantment-glint") ? typeSection.getBoolean("enchantment-glint") : null,
                    typeSection.getStringList("item-flags")
            );
        }
        return new PortalItemSettings(
                material(section.getString("material"), Material.NETHER_STAR),
                section.getString("display-name", id),
                section.getStringList("lore"),
                section.isInt("custom-model-data") ? section.getInt("custom-model-data") : null,
                section.getBoolean("unbreakable", false),
                section.isBoolean("enchantment-glint") ? section.getBoolean("enchantment-glint") : null,
                section.getStringList("item-flags")
        );
    }

    private MenuConfig menu(String id) {
        ConfigurationSection section = menusConfig.getConfigurationSection(id);
        if (section == null) {
            return new MenuConfig("Portal Settings", 27, List.of());
        }
        int size = Math.max(9, Math.min(54, section.getInt("size", 27)));
        size = (size / 9) * 9;
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection == null) {
            return new MenuConfig(section.getString("title", "Portal Settings"), size, List.of());
        }
        List<MenuItemConfig> items = itemsSection.getKeys(false).stream()
                .map(itemId -> menuItem(itemId, itemsSection.getConfigurationSection(itemId)))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new MenuConfig(section.getString("title", "Portal Settings"), size, items);
    }

    private MenuItemConfig menuItem(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return new MenuItemConfig(
                id,
                menuSlots(section),
                section.getString("action", "none"),
                material(section.getString("material"), Material.GRAY_STAINED_GLASS_PANE),
                Math.max(1, section.getInt("amount", 1)),
                section.getString("display-name", ""),
                section.getStringList("lore"),
                section.isInt("custom-model-data") ? section.getInt("custom-model-data") : null,
                section.getBoolean("unbreakable", false),
                section.isBoolean("enchantment-glint") ? section.getBoolean("enchantment-glint") : null,
                section.getStringList("item-flags")
        );
    }

    private List<Integer> menuSlots(ConfigurationSection section) {
        List<Integer> slots = section.getIntegerList("slots");
        if (!slots.isEmpty()) {
            return slots;
        }
        return List.of(section.getInt("slot", 0));
    }

    private PortalShape shape(ConfigurationSection typeSection) {
        ConfigurationSection section = typeSection.getConfigurationSection("portal");
        if (section == null) {
            return new PortalShape(
                    Math.max(1, typeSection.getInt("width", 2)),
                    Math.max(1, typeSection.getInt("height", 3)),
                    material(typeSection.getString("frame-material"), Material.OBSIDIAN),
                    material(typeSection.getString("portal-material"), Material.NETHER_PORTAL),
                    typeSection.getBoolean("replace-only-air", false),
                    typeSection.getBoolean("track-only", false)
            );
        }
        return new PortalShape(
                Math.max(1, section.getInt("width", 2)),
                Math.max(1, section.getInt("height", 3)),
                material(section.getString("frame-material"), Material.OBSIDIAN),
                material(section.getString("portal-material"), Material.NETHER_PORTAL),
                section.getBoolean("replace-only-air", false),
                section.getBoolean("track-only", false)
        );
    }

    private PortalIslandSettings portalIsland(ConfigurationSection section) {
        if (section == null) {
            return new PortalIslandSettings(false, PortalIslandMode.PLATFORM, 4, 3, Material.GRASS_BLOCK, Material.DIRT, new Vector(0, 1, 0), 0, 0, false, 48, 6, 3, "", true, false, 0, 0);
        }
        return new PortalIslandSettings(
                section.getBoolean("enabled", false),
                portalIslandMode(section.getString("mode", "PLATFORM")),
                Math.max(1, section.getInt("platform-radius", 4)),
                Math.max(1, section.getInt("dirt-depth", 3)),
                material(section.getString("top-material"), Material.GRASS_BLOCK),
                material(section.getString("fill-material"), Material.DIRT),
                new Vector(
                        section.getInt("portal-offset.x", 0),
                        section.getInt("portal-offset.y", 1),
                        section.getInt("portal-offset.z", 0)
                ),
                Math.max(0, section.getInt("random-distance.min", 0)),
                Math.max(0, section.getInt("random-distance.max", 0)),
                section.getBoolean("random-facing", false),
                Math.max(0, section.getInt("search-radius", 48)),
                Math.max(1, section.getInt("search-step", 6)),
                Math.max(0, section.getInt("clearance", 3)),
                section.getString("schematic", ""),
                section.getBoolean("schematic-ignore-air", true),
                section.getBoolean("vertical-align.enabled", false),
                section.getInt("vertical-align.schematic-anchor-y", 0),
                section.getInt("vertical-align.y-offset", 0)
        );
    }

    private PortalIslandMode portalIslandMode(String value) {
        try {
            return PortalIslandMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return PortalIslandMode.PLATFORM;
        }
    }

    private BlockFace blockFace(String value) {
        try {
            BlockFace face = BlockFace.valueOf(value.toUpperCase(Locale.ROOT));
            return switch (face) {
                case NORTH, SOUTH, EAST, WEST -> face;
                default -> BlockFace.SOUTH;
            };
        } catch (IllegalArgumentException exception) {
            return BlockFace.SOUTH;
        }
    }

    private Location target(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                section.getDouble("x", 0.5),
                section.getDouble("y", world.getSpawnLocation().getY()),
                section.getDouble("z", 0.5),
                (float) section.getDouble("yaw", 0.0),
                (float) section.getDouble("pitch", 0.0)
        );
    }

    private Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        return material == null ? fallback : material;
    }

    static String normalize(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }

    private void debug(String message) {
        if (debug) {
            plugin.getLogger().info("[Debug] " + message);
        }
    }
}
