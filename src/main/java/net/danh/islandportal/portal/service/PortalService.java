package net.danh.islandportal.portal.service;

import net.danh.islandportal.platform.PlatformScheduler;
import net.danh.islandportal.platform.PlatformTask;
import net.danh.islandportal.portal.access.PortalAccessController;
import net.danh.islandportal.portal.config.PortalConfig;
import net.danh.islandportal.portal.item.PortalItemFactory;
import net.danh.islandportal.portal.menu.PortalSettingsHolder;
import net.danh.islandportal.portal.menu.PortalSettingsMenu;
import net.danh.islandportal.portal.model.AccessPolicy;
import net.danh.islandportal.portal.model.DefaultPortalPlacement;
import net.danh.islandportal.portal.model.ManagedPortal;
import net.danh.islandportal.portal.model.PortalAction;
import net.danh.islandportal.portal.model.PortalBlocks;
import net.danh.islandportal.portal.model.PortalType;
import net.danh.islandportal.portal.placement.PortalBlockBuilder;
import net.danh.islandportal.portal.placement.PortalIslandPlacer;
import net.danh.islandportal.portal.storage.PortalRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class PortalService implements Listener {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private final PortalConfig config;
    private final PlatformScheduler scheduler;
    private final PortalRepository repository;
    private final PortalItemFactory itemFactory;
    private final PortalBlockBuilder blockBuilder = new PortalBlockBuilder();
    private final PortalIslandPlacer islandPlacer;
    private final PortalAccessController accessController;
    private final PortalSettingsMenu settingsMenu;
    private final Map<UUID, Long> useCooldowns = new ConcurrentHashMap<>();
    private final Set<String> pendingCreations = ConcurrentHashMap.newKeySet();
    private PlatformTask autoSaveTask;
    private final AtomicBoolean saveDirty = new AtomicBoolean(false);

    public PortalService(JavaPlugin plugin, PortalConfig config, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.config = config;
        this.scheduler = scheduler;
        this.repository = new PortalRepository(plugin, config, this::debug);
        this.itemFactory = new PortalItemFactory(plugin, config);
        this.islandPlacer = new PortalIslandPlacer(plugin, blockBuilder, this::debug);
        this.accessController = new PortalAccessController(config);
        this.settingsMenu = new PortalSettingsMenu(plugin, config);
        long autosaveTicks = 20L * 60L * config.autosaveIntervalMinutes();
        this.autoSaveTask = scheduler.runGlobalTimer(this::saveIfDirty, autosaveTicks, autosaveTicks);
    }

    public void handleIslandCreated(String islandId, Location islandLocation, String owner, List<String> islandMembers) {
        if (!config.enabled() || islandLocation == null || islandLocation.getWorld() == null) {
            debug("Skipped default portal for " + islandId + " because plugin is disabled or island location is missing.");
            return;
        }
        if (config.defaultIslandPortalTypes().isEmpty()) {
            debug("Skipped default portal for " + islandId + " because no portal type has default-on-island: true.");
            return;
        }
        for (PortalType type : config.defaultIslandPortalTypes()) {
            String id = "island:" + islandId + ":" + type.id();
            if (!pendingCreations.add(id) || repository.contains(id)) {
                debug("Default " + type.id() + " portal already exists or is already queued for island " + islandId + ".");
                continue;
            }
            scheduler.runGlobalLater(() -> attemptDefaultPortal(id, type, islandId, islandLocation.clone(), owner, List.copyOf(islandMembers), 1), config.creationDelayTicks());
            debug("Queued default " + type.id() + " portal for island " + islandId + " from island location " + locationString(islandLocation) + ".");
        }
    }

    private void attemptDefaultPortal(String id, PortalType type, String islandId, Location islandLocation, String owner, List<String> islandMembers, int attempt) {
        scheduler.runAtLoaded(islandLocation, () -> {
            if (repository.contains(id)) {
                pendingCreations.remove(id);
                debug("Default " + type.id() + " portal already exists for island " + islandId + ".");
                return;
            }
            DefaultPortalPlacement placement = islandPlacer.place(type, islandLocation);
            if (placement == null) {
                if (attempt < config.creationRetryAttempts()) {
                    debug("Retrying default " + type.id() + " portal for island " + islandId + " after no clear placement was found on attempt " + attempt + ".");
                    scheduler.runGlobalLater(() -> attemptDefaultPortal(id, type, islandId, islandLocation, owner, islandMembers, attempt + 1), config.creationRetryDelayTicks());
                    return;
                }
                pendingCreations.remove(id);
                debug("Could not find a clear portal-island location for " + type.id() + " on island " + islandId + " after " + attempt + " attempt(s).");
                return;
            }
            pendingCreations.remove(id);
            createPortal(id, type, placement.portalBase(), placement.facing(), owner, islandId, true, islandMembers, placement.supportBlocks());
            debug("Created default " + type.id() + " portal for island " + islandId + " at " + locationString(placement.portalBase()) + ".");
        });
    }

    public void handleIslandRemoved(String islandId, Location islandLocation, String actor, String owner, List<String> islandMembers) {
        if (!config.enabled()) {
            return;
        }
        Player recipient = onlinePlayer(actor);
        if (recipient == null) {
            recipient = onlinePlayer(owner);
        }
        removeDefaultPortalItems(islandMembers);
        if (actor != null && !islandMembers.contains(actor)) {
            removeDefaultPortalItems(List.of(actor));
        }

        List<ManagedPortal> toRemove = repository.all().stream()
                .filter(portal -> belongsToIsland(portal, islandId, islandLocation))
                .toList();
        for (ManagedPortal portal : toRemove) {
            PortalType type = config.type(portal.type());
            boolean returnItem = type != null && !portal.defaultPortal() && recipient != null;
            removeManagedPortal(portal, false);
            if (returnItem) {
                giveOrDrop(recipient, createPortalItem(type, 1));
            }
        }
        debug("Cleaned " + toRemove.size() + " portal(s) for removed island " + islandId + ".");
    }

    public ItemStack createPortalItem(PortalType type, int amount) {
        return itemFactory.create(type, amount);
    }

    public void givePortalItem(Player player, PortalType type, int amount) {
        giveOrDrop(player, createPortalItem(type, amount));
    }

    public void createPortal(String id, PortalType type, Location base) {
        createPortal(id, type, base, BlockFace.SOUTH);
    }

    public void createPortal(String id, PortalType type, Location base, BlockFace facing) {
        createPortal(id, type, base, facing, null, null, false, List.of(), List.of());
    }

    public void createPortal(String id, PortalType type, Location base, BlockFace facing, String owner, String islandId, boolean defaultPortal, List<String> islandMembers, List<String> supportBlocks) {
        scheduler.runAtLoaded(base, () -> createPortalNow(id, type, base, facing, owner, islandId, defaultPortal, islandMembers, supportBlocks));
    }

    private void createPortalNow(String id, PortalType type, Location base, BlockFace facing, String owner, String islandId, boolean defaultPortal, List<String> islandMembers, List<String> supportBlocks) {
        if (base.getWorld() == null) {
            return;
        }
        ManagedPortal existing = repository.remove(id);
        if (existing != null) {
            removePortalBlocks(existing, false);
        }

        BlockFace normalizedFacing = blockBuilder.horizontal(facing);
        PortalBlocks portalBlocks = blockBuilder.build(type, base, normalizedFacing);
        if (type.shape().trackOnly()) {
            portalBlocks = includeTrackedSchematicPortalBlocks(type, portalBlocks, supportBlocks);
        }

        ManagedPortal portal = ManagedPortal.of(id, type, base, normalizedFacing.name(), owner, islandId, defaultPortal, islandMembers, portalBlocks.blocks(), portalBlocks.triggerBlocks(), supportBlocks);
        repository.add(portal);
        requestSave();
    }

    private PortalBlocks includeTrackedSchematicPortalBlocks(PortalType type, PortalBlocks portalBlocks, List<String> supportBlocks) {
        Set<String> blocks = ConcurrentHashMap.newKeySet();
        blocks.addAll(portalBlocks.blocks());
        Set<String> triggerBlocks = ConcurrentHashMap.newKeySet();
        triggerBlocks.addAll(portalBlocks.triggerBlocks());
        for (String blockKey : supportBlocks) {
            Location location = locationFromKey(blockKey);
            if (location == null) {
                continue;
            }
            Block block = location.getBlock();
            if (block.getType() != type.shape().portalMaterial()) {
                continue;
            }
            // Track-only schematics may already contain the real Nether portal blocks.
            // Indexing those exact blocks as triggers lets IslandPortal cancel vanilla Nether travel and run the configured custom action.
            blocks.add(blockKey);
            triggerBlocks.add(blockKey);
        }
        return new PortalBlocks(List.copyOf(blocks), List.copyOf(triggerBlocks));
    }

    public void createDefaultPortal(String id, PortalType type, Location origin, Consumer<Boolean> result) {
        createDefaultPortal(id, type, origin, null, result);
    }

    public void createDefaultPortal(String id, PortalType type, Location origin, Player callbackPlayer, Consumer<Boolean> result) {
        scheduler.runAtLoaded(origin, () -> {
            // Manual test placement uses the same region-owned placement path as automatic island creation.
            DefaultPortalPlacement placement = islandPlacer.place(type, origin);
            if (placement == null) {
                completePlacementResult(callbackPlayer, result, false);
                return;
            }
            createPortal(id, type, placement.portalBase(), placement.facing(), null, null, false, List.of(), placement.supportBlocks());
            completePlacementResult(callbackPlayer, result, true);
        });
    }

    private void completePlacementResult(Player callbackPlayer, Consumer<Boolean> result, boolean created) {
        if (callbackPlayer == null) {
            scheduler.runGlobal(() -> result.accept(created));
            return;
        }
        scheduler.runFor(callbackPlayer, () -> result.accept(created));
    }

    public boolean removeNearestPortal(Location location, int radius, boolean dropItem) {
        ManagedPortal nearest = repository.nearest(location, radius);
        if (nearest == null) {
            return false;
        }
        removeManagedPortal(nearest, dropItem);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlacePortal(PlayerInteractEvent event) {
        if (!config.enabled()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        ManagedPortal clickedPortal = portalAt(event.getClickedBlock().getLocation());
        if (clickedPortal != null && event.getPlayer().isSneaking() && event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (!accessController.canPickup(event.getPlayer(), clickedPortal)) {
                send(event.getPlayer(), message("no-permission-pickup"));
                return;
            }
            removeManagedPortal(clickedPortal, false);
            PortalType clickedType = config.type(clickedPortal.type());
            if (clickedType != null && clickedType.giveItemOnBreak()) {
                giveOrDrop(event.getPlayer(), createPortalItem(clickedType, 1));
                send(event.getPlayer(), message("portal-picked-up"));
            }
            return;
        }
        if (clickedPortal != null && event.getPlayer().isSneaking() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (!accessController.canConfigure(event.getPlayer(), clickedPortal)) {
                send(event.getPlayer(), message("no-permission-configure"));
                return;
            }
            scheduler.runFor(event.getPlayer(), () -> settingsMenu.open(event.getPlayer(), clickedPortal));
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getBlockFace() == BlockFace.DOWN) {
            return;
        }
        PortalType type = typeFromItem(event.getItem());
        if (type == null) {
            return;
        }
        if (type.permissions().hasPlace() && !event.getPlayer().hasPermission(type.permissions().place())) {
            event.setCancelled(true);
            send(event.getPlayer(), message("no-permission-place"));
            return;
        }
        event.setCancelled(true);
        Location base = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        createPortal("placed:" + UUID.randomUUID(), type, base, event.getPlayer().getFacing(), event.getPlayer().getUniqueId().toString(), null, false, List.of(event.getPlayer().getUniqueId().toString()), List.of());
        if (type.consumeOnPlace() && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.getItem().subtract(1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreakPortal(BlockBreakEvent event) {
        if (!config.enabled()) {
            return;
        }
        ManagedPortal portal = portalAt(event.getBlock().getLocation());
        if (portal == null) {
            return;
        }
        event.setCancelled(true);
        send(event.getPlayer(), message("break-use-sneak"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!config.enabled()) {
            return;
        }
        ManagedPortal portal = managedPortalNear(event.getFrom());
        if (portal == null) {
            return;
        }
        PortalType type = config.type(portal.type());
        if (type == null) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        suppressVanillaPortal(event.getPlayer());
        executeAction(event.getPlayer(), portal, type);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!config.enabled()) {
            return;
        }
        if (event instanceof PlayerPortalEvent || event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }
        ManagedPortal portal = managedPortalNear(event.getFrom());
        if (portal == null) {
            return;
        }
        PortalType type = config.type(portal.type());
        if (type == null) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        suppressVanillaPortal(event.getPlayer());
        executeAction(event.getPlayer(), portal, type);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityPortalEnter(EntityPortalEnterEvent event) {
        if (!config.enabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ManagedPortal portal = managedPortalNear(event.getLocation());
        if (portal == null) {
            return;
        }
        suppressVanillaPortal(player);
        PortalType type = config.type(portal.type());
        if (type != null) {
            executeAction(player, portal, type);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.enabled()) {
            return;
        }
        if (event.getTo() == null || event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }
        ManagedPortal portal = managedPortalAt(event.getTo());
        if (portal == null) {
            return;
        }
        suppressVanillaPortal(event.getPlayer());
        PortalType type = config.type(portal.type());
        if (type != null) {
            executeAction(event.getPlayer(), portal, type);
        }
    }

    public void executeAction(Player player, ManagedPortal portal, PortalType type) {
        long now = System.currentTimeMillis();
        cleanupCooldowns(now);
        Long lastUse = useCooldowns.get(player.getUniqueId());
        if (lastUse != null && now - lastUse < config.useCooldownMillis()) {
            return;
        }
        useCooldowns.put(player.getUniqueId(), now);
        if (!accessController.canUse(player, portal, type)) {
            send(player, message("no-permission-use"));
            return;
        }
        if (type.action() == PortalAction.COMMANDS) {
            String playerName = player.getName();
            scheduler.runGlobal(() -> type.commands().forEach(command -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command.replace("%player%", playerName))));
            return;
        }
        if (type.target() == null) {
            send(player, message("target-world-missing"));
            return;
        }
        player.teleportAsync(type.target()).thenAccept(success -> {
            if (!success) {
                scheduler.runFor(player, () -> player.sendMessage(message("target-world-missing")));
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        useCooldowns.remove(event.getPlayer().getUniqueId());
        scheduler.runAsync(this::saveIfDirty);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PortalSettingsHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!config.enabled()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        String action = clicked.getItemMeta().getPersistentDataContainer().get(settingsMenu.actionKey(), PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        ManagedPortal portal = repository.byId(holder.portalId());
        if (portal == null) {
            closeInventory(player);
            return;
        }
        if (!accessController.canConfigure(player, portal)) {
            closeInventory(player);
            send(player, message("no-permission-configure"));
            return;
        }
        if (action.equals("close")) {
            closeInventory(player);
            return;
        }
        if (action.equals("pickup")) {
            portal = updatePortalPolicies(portal, next(portal.pickupPolicy()), portal.usePolicy(), portal.configurePolicy());
        } else if (action.equals("use")) {
            portal = updatePortalPolicies(portal, portal.pickupPolicy(), next(portal.usePolicy()), portal.configurePolicy());
        } else if (action.equals("configure")) {
            portal = updatePortalPolicies(portal, portal.pickupPolicy(), portal.usePolicy(), next(portal.configurePolicy()));
        }
        ManagedPortal updatedPortal = portal;
        scheduler.runFor(player, () -> settingsMenu.open(player, updatedPortal));
    }

    public void load() {
        repository.load();
    }

    public void save() {
        saveDirty.set(false);
        repository.save();
    }

    public void shutdown() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        saveIfDirty();
        useCooldowns.clear();
        pendingCreations.clear();
        scheduler.cancelPluginTasks();
    }

    private PortalType typeFromItem(ItemStack item) {
        return itemFactory.typeFrom(item);
    }

    private ManagedPortal portalAt(Location location) {
        return repository.blockAt(location);
    }

    private ManagedPortal managedPortalAt(Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        ManagedPortal portal = repository.triggerAt(world, x, y, z);
        return portal == null ? repository.blockAt(world, x, y, z) : portal;
    }

    private ManagedPortal managedPortalNear(Location location) {
        ManagedPortal portal = managedPortalAt(location);
        if (portal != null) {
            return portal;
        }
        if (location.getWorld() == null) {
            return null;
        }
        String world = location.getWorld().getName();
        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();
        for (int x = -config.portalNearScanHorizontal(); x <= config.portalNearScanHorizontal(); x++) {
            for (int y = -config.portalNearScanBelow(); y <= config.portalNearScanAbove(); y++) {
                for (int z = -config.portalNearScanHorizontal(); z <= config.portalNearScanHorizontal(); z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    portal = repository.triggerAt(world, baseX + x, baseY + y, baseZ + z);
                    if (portal != null) {
                        return portal;
                    }
                    portal = repository.blockAt(world, baseX + x, baseY + y, baseZ + z);
                    if (portal != null) {
                        return portal;
                    }
                }
            }
        }
        return null;
    }

    private void suppressVanillaPortal(Player player) {
        scheduler.runFor(player, () -> player.setPortalCooldown(config.vanillaPortalCooldownTicks()));
    }

    private void requestSave() {
        saveDirty.set(true);
    }

    private void saveIfDirty() {
        if (!saveDirty.compareAndSet(true, false)) {
            return;
        }
        repository.save();
    }

    private void cleanupCooldowns(long now) {
        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        if (useCooldowns.size() <= onlinePlayers + 32) {
            return;
        }
        useCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L || plugin.getServer().getPlayer(entry.getKey()) == null);
    }

    private void removeManagedPortal(ManagedPortal portal, boolean dropItem) {
        repository.remove(portal.id());
        removePortalBlocks(portal, dropItem);
        requestSave();
    }

    private void removePortalBlocks(ManagedPortal portal, boolean dropItem) {
        for (String blockKey : portal.blocks()) {
            Location location = locationFromKey(blockKey);
            if (location != null) {
                scheduler.runAtLoaded(location, () -> location.getBlock().setType(Material.AIR, false));
            }
        }
        for (String blockKey : portal.supportBlocks()) {
            Location location = locationFromKey(blockKey);
            if (location != null) {
                scheduler.runAtLoaded(location, () -> location.getBlock().setType(Material.AIR, false));
            }
        }
        PortalType type = config.type(portal.type());
        Location base = portal.baseLocation();
        if (dropItem && type != null && type.giveItemOnBreak() && base != null) {
            scheduler.runAtLoaded(base, () -> base.getWorld().dropItemNaturally(base, createPortalItem(type, 1)));
        }
    }

    private String key(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private Location locationFromKey(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) {
            return null;
        }
        World world = plugin.getServer().getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String locationString(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private boolean belongsToIsland(ManagedPortal portal, String islandId, Location islandLocation) {
        if (islandId.equals(portal.islandId())) {
            return true;
        }
        Location base = portal.baseLocation();
        return islandLocation != null && base != null && base.getWorld().equals(islandLocation.getWorld()) && base.distanceSquared(islandLocation) <= config.islandCleanupRadius() * config.islandCleanupRadius();
    }

    private ManagedPortal updatePortalPolicies(ManagedPortal portal, AccessPolicy pickupPolicy, AccessPolicy usePolicy, AccessPolicy configurePolicy) {
        ManagedPortal updated = new ManagedPortal(
                portal.id(), portal.type(), portal.world(), portal.x(), portal.y(), portal.z(), portal.facing(),
                portal.owner(), portal.islandId(), portal.defaultPortal(), pickupPolicy, usePolicy, configurePolicy,
                portal.islandMembers(), portal.blocks(), portal.triggerBlocks(), portal.supportBlocks()
        );
        repository.remove(updated.id());
        repository.add(updated);
        requestSave();
        return updated;
    }

    private AccessPolicy next(AccessPolicy policy) {
        AccessPolicy[] values = AccessPolicy.values();
        return values[(policy.ordinal() + 1) % values.length];
    }

    private void removeDefaultPortalItems(List<String> playerIds) {
        for (String playerId : playerIds) {
            Player player = onlinePlayer(playerId);
            if (player == null) {
                continue;
            }
            scheduler.runFor(player, () -> {
                for (ItemStack item : player.getInventory().getContents()) {
                    PortalType type = typeFromItem(item);
                    if (type != null && type.defaultOnIsland()) {
                        item.setAmount(0);
                    }
                }
            });
        }
    }

    private Player onlinePlayer(String playerId) {
        if (playerId == null) {
            return null;
        }
        try {
            return plugin.getServer().getPlayer(UUID.fromString(playerId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        scheduler.runFor(player, () -> {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            Location dropLocation = player.getLocation();
            leftover.values().forEach(extra -> scheduler.runAtLoaded(dropLocation, () -> dropLocation.getWorld().dropItemNaturally(dropLocation, extra)));
        });
    }

    private void send(Player player, Component component) {
        scheduler.runFor(player, () -> player.sendMessage(component));
    }

    private void closeInventory(Player player) {
        scheduler.runFor(player, player::closeInventory);
    }

    private Component message(String path) {
        return miniMessage.deserialize(config.message(path));
    }

    private void debug(String message) {
        if (config.debug()) {
            plugin.getLogger().info("[Debug] " + message);
        }
    }

}
