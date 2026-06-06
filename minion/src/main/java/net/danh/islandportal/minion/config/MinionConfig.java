package net.danh.islandportal.minion.config;

import net.danh.islandportal.minion.model.MinionAnimationStage;
import net.danh.islandportal.minion.model.MinionAnimationSettings;
import net.danh.islandportal.minion.model.MinionDefaultEquipmentSettings;
import net.danh.islandportal.minion.model.MinionDisplaySettings;
import net.danh.islandportal.minion.model.MinionActionSettings;
import net.danh.islandportal.minion.model.MinionActionAreaSettings;
import net.danh.islandportal.minion.model.MinionActionType;
import net.danh.islandportal.minion.model.MinionDisplayEntitySettings;
import net.danh.islandportal.minion.model.MinionDrop;
import net.danh.islandportal.minion.model.MinionFuel;
import net.danh.islandportal.minion.model.MinionFuelItemSettings;
import net.danh.islandportal.minion.model.MinionIntegrationSettings;
import net.danh.islandportal.minion.model.MinionMenuItem;
import net.danh.islandportal.minion.model.MinionOfflineLimit;
import net.danh.islandportal.minion.model.MinionPermissionSettings;
import net.danh.islandportal.minion.model.MinionPlacementSettings;
import net.danh.islandportal.minion.model.MinionRequirement;
import net.danh.islandportal.minion.model.MinionRuntimeSettings;
import net.danh.islandportal.minion.model.MinionShopSettings;
import net.danh.islandportal.minion.model.MinionTier;
import net.danh.islandportal.minion.model.MinionType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MinionConfig {

    private final JavaPlugin plugin;
    private final Map<String, MinionType> types = new ConcurrentHashMap<>();
    private final Map<String, MinionFuel> fuels = new ConcurrentHashMap<>();
    private final Map<String, String> messages = new ConcurrentHashMap<>();
    private final Map<String, MinionMenuItem> menuItems = new ConcurrentHashMap<>();
    private boolean enabled;
    private boolean debug;
    private int tickIntervalTicks;
    private int autosaveIntervalMinutes;
    private int respawnCheckTicks;
    private int maxActionsPerTick;
    private int placeRadius;
    private String menuTitle;
    private int menuSize;
    private MinionRuntimeSettings runtimeSettings;

    public MinionConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration root = plugin.getConfig();
        enabled = root.getBoolean("minions.enabled", true);
        debug = root.getBoolean("debug", false);
        tickIntervalTicks = Math.max(1, root.getInt("minions.tick-interval-ticks", 20));
        autosaveIntervalMinutes = Math.max(1, root.getInt("minions.autosave-interval-minutes", 5));
        respawnCheckTicks = Math.max(1, root.getInt("minions.respawn-check-ticks", 60));
        maxActionsPerTick = Math.max(1, root.getInt("minions.max-actions-per-tick", 250));
        placeRadius = Math.max(1, root.getInt("minions.place-radius", 4));

        types.clear();
        fuels.clear();
        messages.clear();
        menuItems.clear();
        loadSettings();
        loadMenu();
        loadFolderConfig();
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean debug() {
        return debug;
    }

    public int tickIntervalTicks() {
        return tickIntervalTicks;
    }

    public int autosaveIntervalMinutes() {
        return autosaveIntervalMinutes;
    }

    public int respawnCheckTicks() {
        return respawnCheckTicks;
    }

    public int maxActionsPerTick() {
        return maxActionsPerTick;
    }

    public int placeRadius() {
        return placeRadius;
    }

    public String menuTitle() {
        return menuTitle;
    }

    public int menuSize() {
        return menuSize;
    }

    public MinionRuntimeSettings runtimeSettings() {
        return runtimeSettings;
    }

    public String message(String key) {
        return messages.getOrDefault(key, key);
    }

    public String message(String key, String... replacements) {
        String value = message(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace(replacements[i], replacements[i + 1]);
        }
        return value;
    }

    public Collection<MinionMenuItem> menuItems() {
        return menuItems.values();
    }

    public Collection<MinionType> types() {
        return types.values();
    }

    public MinionType type(String id) {
        return types.get(normalize(id));
    }

    public Collection<MinionFuel> fuels() {
        return fuels.values();
    }

    public MinionFuel fuel(String id) {
        return fuels.get(normalize(id));
    }

    private void loadFuels(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection fuelSection = section.getConfigurationSection(id);
            if (fuelSection == null) {
                continue;
            }
            String normalizedId = normalize(id);
            fuels.put(normalizedId, new MinionFuel(
                    normalizedId,
                    material(fuelSection.getString("material"), Material.COAL),
                    seconds(fuelSection.getLong("duration-seconds", 1800)),
                    Math.max(0.1, fuelSection.getDouble("speed-multiplier", 1.25)),
                    Math.max(0.1, fuelSection.getDouble("output-multiplier", 1.0)),
                    drops(fuelSection.getConfigurationSection("bonus-drops"))
            ));
        }
    }

    private void loadFolderConfig() {
        File minionsFolder = new File(plugin.getDataFolder(), "minions");
        File fuelsFile = new File(minionsFolder, "fuels.yml");
        File typesFolder = new File(minionsFolder, "types");
        if (fuelsFile.exists()) {
            loadFuels(YamlConfiguration.loadConfiguration(fuelsFile).getConfigurationSection("fuels"));
        }
        if (typesFolder.exists()) {
            File[] files = typesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    loadTypes(YamlConfiguration.loadConfiguration(file).getConfigurationSection("minion-types"));
                }
            }
        }
        File legacyFile = new File(plugin.getDataFolder(), "minions.yml");
        if (!fuelsFile.exists() && !typesFolder.exists() && legacyFile.exists()) {
            YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
            loadFuels(legacy.getConfigurationSection("fuels"));
            loadTypes(legacy.getConfigurationSection("minion-types"));
        }
    }

    private void loadSettings() {
        runtimeSettings = defaultRuntimeSettings();
        putDefaultMessages();
        File file = new File(new File(plugin.getDataFolder(), "minions"), "settings.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection runtime = data.getConfigurationSection("runtime");
        if (runtime != null) {
            runtimeSettings = new MinionRuntimeSettings(
                    runtime.getString("id-prefix", "minion:"),
                    Math.max(1, runtime.getInt("per-island-global-limit", 80)),
                    Math.max(1, runtime.getInt("island-cleanup-radius", 100)),
                    Math.max(1, runtime.getInt("link-chest-range", 5)),
                    runtime.getBoolean("auto-sell.enabled", false),
                    runtime.getBoolean("work-when-owner-offline", true),
                    runtime.getBoolean("output.linked-chest", true),
                    runtime.getBoolean("output.hopper", true),
                    actionArea(runtime.getConfigurationSection("action-area")),
                    animations(runtime.getConfigurationSection("animations")),
                    fuelItems(runtime.getConfigurationSection("fuel-items")),
                    placement(runtime.getConfigurationSection("placement")),
                    permissions(runtime.getConfigurationSection("permissions")),
                    defaultEquipment(runtime.getConfigurationSection("default-equipment")),
                    displayEntity(runtime.getConfigurationSection("display-entity")),
                    integrations(runtime.getConfigurationSection("integrations"))
            );
        }
        ConfigurationSection messageSection = data.getConfigurationSection("messages");
        if (messageSection != null) {
            for (String key : messageSection.getKeys(false)) {
                messages.put(key, messageSection.getString(key, messages.getOrDefault(key, key)));
            }
        }
    }

    private MinionRuntimeSettings defaultRuntimeSettings() {
        return new MinionRuntimeSettings(
                "minion:",
                80,
                100,
                5,
                false,
                true,
                true,
                true,
                new MinionActionAreaSettings(3, 1),
                new MinionAnimationSettings(14, 18, 0.75, true, List.of(0.15f, 0.35f, 0.55f, 0.75f, 0.95f), true, 2),
                new MinionFuelItemSettings(
                        "<gold>%fuel%",
                        List.of("<gray>Minion fuel", "<gray>Speed: <white>x%speed%", "<gray>Output: <white>x%output%")
                ),
                new MinionPlacementSettings(-2, 2),
                new MinionPermissionSettings(false, "islandportal.minion.place.", true),
                new MinionDefaultEquipmentSettings(
                        Material.LEATHER_CHESTPLATE,
                        Material.LEATHER_CHESTPLATE.name(),
                        Material.LEATHER_LEGGINGS,
                        Material.LEATHER_LEGGINGS.name(),
                        Material.LEATHER_BOOTS,
                        Material.LEATHER_BOOTS.name(),
                        Material.WOODEN_PICKAXE,
                        Material.WOODEN_PICKAXE.name()
                ),
                new MinionDisplayEntitySettings(true, true, false, true, false, false, 20.0),
                new MinionIntegrationSettings(true, true, true, true, true, 48, true, true, true, true, true, true)
        );
    }

    private MinionAnimationSettings animations(ConfigurationSection section) {
        MinionAnimationSettings defaults = defaultRuntimeSettings().animations();
        if (section == null) {
            return defaults;
        }
        List<Float> progress = section.getDoubleList("block-break.progress").stream()
                .map(value -> Math.max(0.0f, Math.min(1.0f, value.floatValue())))
                .toList();
        return new MinionAnimationSettings(
                Math.max(1, section.getInt("reset-ticks", defaults.resetTicks())),
                Math.max(1, section.getInt("target-visible-ticks", defaults.targetVisibleTicks())),
                section.getDouble("target-y-offset", defaults.targetYOffset()),
                section.getBoolean("block-break.enabled", defaults.blockBreakEnabled()),
                progress.isEmpty() ? defaults.blockBreakProgress() : progress,
                section.getBoolean("crop-growth.enabled", defaults.cropGrowthEnabled()),
                Math.max(1, section.getInt("crop-growth.stage-ticks", defaults.cropGrowthStageTicks()))
        );
    }

    private MinionActionAreaSettings actionArea(ConfigurationSection section) {
        MinionActionAreaSettings defaults = defaultRuntimeSettings().actionArea();
        if (section == null) {
            return defaults;
        }
        return new MinionActionAreaSettings(
                Math.max(1, section.getInt("default-size", defaults.defaultSize())),
                Math.max(0, section.getInt("size-per-tier", defaults.sizePerTier()))
        );
    }

    private MinionFuelItemSettings fuelItems(ConfigurationSection section) {
        MinionFuelItemSettings defaults = defaultRuntimeSettings().fuelItems();
        if (section == null) {
            return defaults;
        }
        List<String> lore = section.getStringList("lore");
        return new MinionFuelItemSettings(
                section.getString("display-name", defaults.displayName()),
                lore.isEmpty() ? defaults.lore() : lore
        );
    }

    private MinionPlacementSettings placement(ConfigurationSection section) {
        if (section == null) {
            return defaultRuntimeSettings().placement();
        }
        int min = section.getInt("vertical-search-min", -2);
        int max = section.getInt("vertical-search-max", 2);
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
        return new MinionPlacementSettings(min, max);
    }

    private MinionPermissionSettings permissions(ConfigurationSection section) {
        if (section == null) {
            return defaultRuntimeSettings().permissions();
        }
        return new MinionPermissionSettings(
                section.getBoolean("place-per-type", false),
                section.getString("place-prefix", "islandportal.minion.place."),
                section.getBoolean("admin-bypass", true)
        );
    }

    private MinionDefaultEquipmentSettings defaultEquipment(ConfigurationSection section) {
        MinionDefaultEquipmentSettings defaults = defaultRuntimeSettings().defaultEquipment();
        if (section == null) {
            return defaults;
        }
        return new MinionDefaultEquipmentSettings(
                material(section.getString("chestplate-material"), defaults.chestplateMaterial()),
                section.getString("chestplate-item", section.getString("chestplate-material", defaults.chestplateItemKey())),
                material(section.getString("leggings-material"), defaults.leggingsMaterial()),
                section.getString("leggings-item", section.getString("leggings-material", defaults.leggingsItemKey())),
                material(section.getString("boots-material"), defaults.bootsMaterial()),
                section.getString("boots-item", section.getString("boots-material", defaults.bootsItemKey())),
                material(section.getString("main-hand-material"), defaults.mainHandMaterial()),
                section.getString("main-hand-item", section.getString("main-hand-material", defaults.mainHandItemKey()))
        );
    }

    private MinionDisplayEntitySettings displayEntity(ConfigurationSection section) {
        if (section == null) {
            return defaultRuntimeSettings().displayEntity();
        }
        return new MinionDisplayEntitySettings(
                section.getBoolean("custom-name-visible", true),
                section.getBoolean("arms", true),
                section.getBoolean("base-plate", false),
                section.getBoolean("invulnerable", true),
                section.getBoolean("persistent", false),
                section.getBoolean("remove-when-far-away", false),
                Math.max(1.0, section.getDouble("max-health", 20.0))
        );
    }

    private MinionIntegrationSettings integrations(ConfigurationSection section) {
        if (section == null) {
            return defaultRuntimeSettings().integrations();
        }
        return new MinionIntegrationSettings(
                section.getBoolean("vault", true),
                section.getBoolean("shopgui-plus", true),
                section.getBoolean("essentials", true),
                section.getBoolean("cmi", true),
                section.getBoolean("packet-events.enabled", true),
                Math.max(1, section.getInt("packet-events.view-range", 48)),
                section.getBoolean("head-database", true),
                section.getBoolean("itemsadder", true),
                section.getBoolean("oraxen", true),
                section.getBoolean("nexo", true),
                section.getBoolean("mmoitems", true),
                section.getBoolean("mythicmobs", true)
        );
    }

    private void putDefaultMessages() {
        messages.put("limit-type", "<red>You have reached the limit for this minion type.");
        messages.put("limit-island", "<red>This island has reached its global minion limit.");
        messages.put("cannot-manage", "<red>You cannot manage this minion.");
        messages.put("no-safe-location", "<red>No safe minion placement location found.");
        messages.put("missing-place-permission", "<red>You need <white>%permission%<red> to place this minion.");
        messages.put("storage-empty", "<gray>Minion storage is empty.");
        messages.put("fuel-added", "<green>Fuel added: <white>%fuel%");
        messages.put("booster-added", "<green>Booster added: <white>%fuel%");
        messages.put("fuel-missing", "<red>Place a configured fuel or booster item on your cursor first.");
        messages.put("max-tier", "<gray>This minion is already max tier.");
        messages.put("missing-upgrade-items", "<red>You do not have the required upgrade items.");
        messages.put("missing-upgrade-money", "<red>You do not have enough money for this upgrade.");
        messages.put("vault-missing", "<red>Vault economy is not available.");
        messages.put("upgraded", "<green>Minion upgraded to tier <white>%tier%<green>.");
        messages.put("shop-missing", "<red>This minion has no shop integration configured.");
        messages.put("storage-sold", "<green>Minion storage sold.");
        messages.put("chest-linked", "<green>Linked nearest chest.");
        messages.put("chest-missing", "<red>No chest found nearby.");
        messages.put("chest-link-cancelled", "<red>Chest linking was cancelled.");
        messages.put("inspect-id", "<gray>Minion id: <white>%id%");
        messages.put("inspect-type", "<gray>Type: <white>%type% <gray>Tier: <white>%tier%");
        messages.put("inspect-owner", "<gray>Owner: <white>%owner%");
        messages.put("inspect-island", "<gray>Island: <white>%island%");
        messages.put("inspect-storage", "<gray>Storage: <white>%stored%/%storage_limit%");
        messages.put("inspect-actions", "<gray>Actions: <white>%actions%");
        messages.put("inspect-fuel", "<gray>Fuel: <white>%fuel%");
        messages.put("inspect-chest", "<gray>Chest: <white>%chest%");
        messages.put("upgrade-max-tier-lore", "<gray>Already max tier.");
        messages.put("upgrade-tier-lore", "<gray>Upgrade to tier <white>%tier%");
        messages.put("upgrade-cost-lore", "<gray>Cost:");
        messages.put("upgrade-cost-free-lore", "<green>Free");
        messages.put("upgrade-cost-money-lore", "<dark_gray>- <green>$%amount%");
        messages.put("upgrade-cost-item-lore", "<dark_gray>- <white>%amount%x %item%");
        messages.put("fuel-none", "none");
        messages.put("fuel-active", "%time%s x%multiplier%");
        messages.put("placement-overlap", "<red>You cannot place minions too close to each other. Work areas cannot overlap.");
        messages.put("item-stat-tier", "<gray>Tier: <white>%tier%");
        messages.put("item-stat-actions", "<gray>Actions: <white>%actions%");
        messages.put("item-stat-storage", "<gray>Storage: <white>%stored%/%storage_limit%");
    }

    private void loadMenu() {
        menuTitle = "<dark_gray>Minion: <gold>%type%";
        menuSize = 27;
        menuItems.clear();
        File file = new File(new File(plugin.getDataFolder(), "minions"), "gui.yml");
        if (!file.exists()) {
            putDefaultMenuItems();
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        menuTitle = data.getString("title", menuTitle);
        int configuredSize = data.getInt("size", menuSize);
        menuSize = configuredSize >= 9 && configuredSize <= 54 && configuredSize % 9 == 0 ? configuredSize : 27;
        ConfigurationSection items = data.getConfigurationSection("items");
        if (items == null) {
            putDefaultMenuItems();
            return;
        }
        for (String key : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            int slot = section.getInt("slot", -1);
            if (slot < 0 || slot >= menuSize) {
                continue;
            }
            menuItems.put(key, new MinionMenuItem(
                    slot,
                    material(section.getString("material"), Material.STONE),
                    section.getString("name", key),
                    section.getStringList("lore"),
                    section.getString("action", key),
                    section.isInt("custom-model-data") ? section.getInt("custom-model-data") : null
            ));
        }
    }

    private void putDefaultMenuItems() {
        Map<String, MinionMenuItem> defaults = new LinkedHashMap<>();
        defaults.put("status", new MinionMenuItem(4, Material.PLAYER_HEAD, "<gold>Status", List.of("<gray>Tier: <white>%tier%", "<gray>Stored: <white>%stored%/%storage_limit%", "<gray>Work Area: <white>%action_size%x%action_size%", "<gray>Fuel: <white>%fuel%", "<gray>Booster: <white>%booster%"), "none", null));
        defaults.put("collect", new MinionMenuItem(10, Material.CHEST, "<green>Collect", List.of("<gray>Move stored items to your inventory."), "collect", null));
        defaults.put("fuel", new MinionMenuItem(12, Material.COAL, "<yellow>Fuel Slot", List.of("<gray>Place speed fuel here.", "<gray>Current: <white>%fuel%"), "fuel", null));
        defaults.put("booster", new MinionMenuItem(13, Material.BLAZE_POWDER, "<gold>Booster Slot", List.of("<gray>Place output booster here.", "<gray>Current: <white>%booster%"), "booster", null));
        defaults.put("upgrade", new MinionMenuItem(15, Material.ANVIL, "<aqua>Upgrade", List.of("%upgrade_lore%"), "upgrade", null));
        defaults.put("sell", new MinionMenuItem(17, Material.EMERALD, "<green>Sell Storage", List.of("<gray>Runs configured shop commands."), "sell", null));
        defaults.put("pickup", new MinionMenuItem(18, Material.BARRIER, "<red>Pickup", List.of("<gray>Collect storage and return the minion item."), "pickup", null));
        defaults.put("rotate", new MinionMenuItem(20, Material.COMPASS, "<yellow>Rotate", List.of("<gray>Rotates the minion direction."), "rotate", null));
        defaults.put("close", new MinionMenuItem(22, Material.OAK_DOOR, "<gray>Close", List.of(), "close", null));
        defaults.put("link_chest", new MinionMenuItem(24, Material.CHEST, "<gold>Link Chest", List.of("<gray>Links the nearest chest in range."), "link_chest", null));
        menuItems.putAll(defaults);
    }

    private void loadTypes(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection typeSection = section.getConfigurationSection(id);
            if (typeSection == null) {
                continue;
            }
            String normalizedId = normalize(id);
            MinionType type = new MinionType(
                    normalizedId,
                    material(typeSection.getString("item.material"), Material.PLAYER_HEAD),
                    typeSection.getString("item.item", typeSection.getString("item.material", Material.PLAYER_HEAD.name())),
                    typeSection.getString("item.display-name", normalizedId),
                    typeSection.getStringList("item.lore"),
                    typeSection.isInt("item.custom-model-data") ? typeSection.getInt("item.custom-model-data") : null,
                    display(typeSection.getConfigurationSection("display"), normalizedId),
                    tiers(typeSection.getConfigurationSection("tiers")),
                    drops(typeSection.getConfigurationSection("drops")),
                    action(typeSection.getConfigurationSection("action")),
                    Math.max(1, typeSection.getInt("limits.max-per-owner", 8)),
                    seconds(typeSection.getLong("offline-production-limit-seconds", 21600)),
                    offlineLimits(typeSection.getMapList("offline-production-limit-permission")),
                    typeSection.getBoolean("requires-fuel", false),
                    shop(typeSection.getConfigurationSection("shop"))
            );
            types.put(normalizedId, type);
        }
    }

    private MinionDisplaySettings display(ConfigurationSection section, String id) {
        if (section == null) {
            MinionDefaultEquipmentSettings equipment = runtimeSettings.defaultEquipment();
            return new MinionDisplaySettings(
                    "<gold>" + id + " Minion",
                    Material.PLAYER_HEAD,
                    Material.PLAYER_HEAD.name(),
                    equipment.chestplateMaterial(),
                    equipment.chestplateItemKey(),
                    equipment.leggingsMaterial(),
                    equipment.leggingsItemKey(),
                    equipment.bootsMaterial(),
                    equipment.bootsItemKey(),
                    equipment.mainHandMaterial(),
                    equipment.mainHandItemKey(),
                    true,
                    false,
                    20.0,
                    List.of()
            );
        }
        return new MinionDisplaySettings(
                section.getString("name", "<gold>" + id + " Minion"),
                material(section.getString("head-material"), Material.PLAYER_HEAD),
                section.getString("head-item", section.getString("head-material", Material.PLAYER_HEAD.name())),
                material(section.getString("chestplate-material"), runtimeSettings.defaultEquipment().chestplateMaterial()),
                section.getString("chestplate-item", section.getString("chestplate-material", runtimeSettings.defaultEquipment().chestplateItemKey())),
                material(section.getString("leggings-material"), runtimeSettings.defaultEquipment().leggingsMaterial()),
                section.getString("leggings-item", section.getString("leggings-material", runtimeSettings.defaultEquipment().leggingsItemKey())),
                material(section.getString("boots-material"), runtimeSettings.defaultEquipment().bootsMaterial()),
                section.getString("boots-item", section.getString("boots-material", runtimeSettings.defaultEquipment().bootsItemKey())),
                material(section.getString("main-hand-material"), runtimeSettings.defaultEquipment().mainHandMaterial()),
                section.getString("main-hand-item", section.getString("main-hand-material", runtimeSettings.defaultEquipment().mainHandItemKey())),
                section.getBoolean("small", true),
                section.getBoolean("glowing", false),
                Math.max(1.0, section.getDouble("health", 20.0)),
                animationStages(section.getConfigurationSection("animations"))
        );
    }

    private List<MinionAnimationStage> animationStages(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        return section.getKeys(false).stream()
                .map(key -> animationStage(key, section.getConfigurationSection(key)))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private MinionAnimationStage animationStage(String key, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return new MinionAnimationStage(
                key,
                Math.max(0, section.getInt("delay-ticks", 0)),
                Math.max(1, section.getInt("duration-ticks", runtimeSettings.animationResetTicks())),
                section.getDouble("right-arm.x", -70.0),
                section.getDouble("right-arm.y", 0.0),
                section.getDouble("right-arm.z", 0.0),
                section.getDouble("left-arm.x", 0.0),
                section.getDouble("left-arm.y", 0.0),
                section.getDouble("left-arm.z", 0.0)
        );
    }

    private MinionActionSettings action(ConfigurationSection section) {
        if (section == null) {
            return new MinionActionSettings(MinionActionType.GENERATOR, 3, 1, 0, Material.STONE, EntityType.ZOMBIE, List.of(), List.of());
        }
        return new MinionActionSettings(
                actionType(section.getString("type", "GENERATOR")),
                Math.max(1, section.getInt("range", 3)),
                Math.max(1, section.getInt("blocks-per-action", 1)),
                Math.max(0, section.getInt("smelt-at-tier", 0)),
                material(section.getString("spawn-block"), Material.STONE),
                entityType(section.getString("spawn-entity", "ZOMBIE")),
                materials(section.getStringList("allowed-materials")),
                materials(section.getStringList("sell-materials"))
        );
    }

    private MinionActionType actionType(String value) {
        try {
            return MinionActionType.valueOf(value.toUpperCase(Locale.ROOT).replace("-", "_"));
        } catch (IllegalArgumentException exception) {
            return MinionActionType.GENERATOR;
        }
    }

    private EntityType entityType(String value) {
        try {
            EntityType type = EntityType.valueOf(value.toUpperCase(Locale.ROOT));
            return type.isAlive() ? type : EntityType.ZOMBIE;
        } catch (IllegalArgumentException exception) {
            return EntityType.ZOMBIE;
        }
    }

    private List<Material> materials(List<String> names) {
        return names.stream()
                .map(name -> material(name, null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<MinionTier> tiers(ConfigurationSection section) {
        if (section == null) {
            return List.of(new MinionTier(1, 60_000L, 64, 3, List.of(), 0.0));
        }
        return section.getKeys(false).stream()
                .map(key -> tier(key, section.getConfigurationSection(key)))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(MinionTier::level))
                .toList();
    }

    private MinionTier tier(String key, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        int level;
        try {
            level = Integer.parseInt(key);
        } catch (NumberFormatException exception) {
            level = section.getInt("level", 1);
        }
        return new MinionTier(
                Math.max(1, level),
                Math.max(1_000L, section.getLong("interval-seconds", 60) * 1000L),
                Math.max(1, section.getInt("storage-limit", 64)),
                Math.max(1, section.getInt("action-size", runtimeSettings.actionArea().defaultSize() + Math.max(0, level - 1) * runtimeSettings.actionArea().sizePerTier())),
                requirements(section.getMapList("upgrade-cost")),
                Math.max(0.0, section.getDouble("upgrade-money", 0.0))
        );
    }

    private List<MinionRequirement> requirements(List<Map<?, ?>> entries) {
        return entries.stream()
                .map(entry -> new MinionRequirement(
                        material(string(entry.get("material"), "STONE"), Material.STONE),
                        string(entry.get("item"), string(entry.get("material"), "STONE")),
                        Math.max(1, integer(entry.get("amount"), 1))
                ))
                .toList();
    }

    private List<MinionOfflineLimit> offlineLimits(List<Map<?, ?>> entries) {
        return entries.stream()
                .map(entry -> new MinionOfflineLimit(
                        string(entry.get("permission"), ""),
                        seconds(longValue(entry.get("limit-seconds"), 21600L))
                ))
                .filter(limit -> !limit.permission().isBlank())
                .toList();
    }

    private MinionShopSettings shop(ConfigurationSection section) {
        if (section == null) {
            return new MinionShopSettings(false, List.of(), 0.0, Map.of(), List.of("CONFIG"));
        }
        List<String> priceSources = section.getStringList("price-sources");
        return new MinionShopSettings(
                section.getBoolean("enabled", false),
                section.getStringList("sell-commands"),
                Math.max(0.0, section.getDouble("default-price", 0.0)),
                prices(section.getConfigurationSection("prices")),
                priceSources.isEmpty() ? List.of("CONFIG") : priceSources
        );
    }

    private Map<String, Double> prices(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Double> prices = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            prices.put(key, Math.max(0.0, section.getDouble(key, 0.0)));
            prices.put(key.toLowerCase(Locale.ROOT), Math.max(0.0, section.getDouble(key, 0.0)));
        }
        return prices;
    }

    private List<MinionDrop> drops(ConfigurationSection section) {
        if (section == null) {
            return List.of(new MinionDrop(Material.COBBLESTONE, 1));
        }
        return section.getMapList("items").stream()
                .map(entry -> new MinionDrop(
                        material(string(entry.get("material"), "COBBLESTONE"), Material.COBBLESTONE),
                        string(entry.get("item"), string(entry.get("material"), "COBBLESTONE")),
                        Math.max(1, integer(entry.get("amount"), 1))
                ))
                .toList();
    }

    private Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        return material == null ? fallback : material;
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private long seconds(long seconds) {
        return Math.max(0L, seconds) * 1000L;
    }

    private String normalize(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }
}
