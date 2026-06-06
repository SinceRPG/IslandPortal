package net.danh.islandportal.minion.service;

import net.danh.islandportal.minion.config.MinionConfig;
import net.danh.islandportal.minion.event.MinionChestLinkEvent;
import net.danh.islandportal.minion.event.PreMinionPickupEvent;
import net.danh.islandportal.minion.event.PreMinionPlaceEvent;
import net.danh.islandportal.minion.integration.EconomyBridge;
import net.danh.islandportal.minion.integration.NativeItemBridge;
import net.danh.islandportal.minion.integration.NoopCosmeticAnimationBridge;
import net.danh.islandportal.minion.integration.PacketEventsCosmeticAnimationBridge;
import net.danh.islandportal.minion.integration.PriceLookupRegistry;
import net.danh.islandportal.minion.integration.UnavailableEconomyBridge;
import net.danh.islandportal.minion.integration.VaultEconomyBridge;
import net.danh.islandportal.minion.menu.MinionMenuHolder;
import net.danh.islandportal.minion.model.ManagedMinion;
import net.danh.islandportal.minion.model.MinionDrop;
import net.danh.islandportal.minion.model.MinionFuel;
import net.danh.islandportal.minion.model.MinionMenuItem;
import net.danh.islandportal.minion.model.MinionOfflineLimit;
import net.danh.islandportal.minion.model.MinionRequirement;
import net.danh.islandportal.minion.model.MinionTier;
import net.danh.islandportal.minion.model.MinionType;
import net.danh.islandportal.minion.storage.MinionRepository;
import net.danh.islandportal.platform.PlatformScheduler;
import net.danh.islandportal.platform.PlatformTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MinionService implements Listener {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private final MinionConfig config;
    private final PlatformScheduler scheduler;
    private final MinionRepository repository;
    private EconomyBridge economyBridge;
    private NativeItemBridge nativeItemBridge;
    private PriceLookupRegistry priceLookupRegistry;
    private MinionItemFactory itemFactory;
    private MinionSellService sellService;
    private MinionDisplayService displayService;
    private MinionActionEngine actionEngine;
    private final NamespacedKey minionItemKey;
    private final NamespacedKey minionTierKey;
    private final NamespacedKey minionActionsKey;
    private final NamespacedKey minionFuelIdKey;
    private final NamespacedKey minionFuelRemainingKey;
    private final NamespacedKey minionBoosterIdKey;
    private final NamespacedKey minionBoosterRemainingKey;
    private final NamespacedKey minionStorageKey;
    private final NamespacedKey minionFuelItemKey;
    private final NamespacedKey minionEntityKey;
    private final NamespacedKey menuActionKey;
    private final AtomicBoolean saveDirty = new AtomicBoolean(false);
    private PlatformTask productionTask;
    private PlatformTask autosaveTask;
    private PlatformTask respawnTask;
    private java.util.Iterator<ManagedMinion> tickIterator;

    public MinionService(JavaPlugin plugin, MinionConfig config, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.config = config;
        this.scheduler = scheduler;
        this.repository = new MinionRepository(plugin, this::debug);
        this.minionItemKey = new NamespacedKey(plugin, "minion_type");
        this.minionTierKey = new NamespacedKey(plugin, "minion_tier");
        this.minionActionsKey = new NamespacedKey(plugin, "minion_actions");
        this.minionFuelIdKey = new NamespacedKey(plugin, "minion_fuel_id");
        this.minionFuelRemainingKey = new NamespacedKey(plugin, "minion_fuel_remaining");
        this.minionBoosterIdKey = new NamespacedKey(plugin, "minion_booster_id");
        this.minionBoosterRemainingKey = new NamespacedKey(plugin, "minion_booster_remaining");
        this.minionStorageKey = new NamespacedKey(plugin, "minion_storage");
        this.minionFuelItemKey = new NamespacedKey(plugin, "minion_fuel_item");
        this.minionEntityKey = new NamespacedKey(plugin, "managed_minion_id");
        this.menuActionKey = new NamespacedKey(plugin, "minion_action");
        reloadHelpers();
    }

    private void reloadHelpers() {
        this.economyBridge = economyBridge(plugin);
        this.nativeItemBridge = new NativeItemBridge(plugin.getServer(), config.runtimeSettings().integrations());
        this.priceLookupRegistry = new PriceLookupRegistry(plugin.getServer(), config.runtimeSettings().integrations());
        this.itemFactory = new MinionItemFactory(config, nativeItemBridge, minionItemKey);
        this.sellService = new MinionSellService(plugin, scheduler, economyBridge, nativeItemBridge, priceLookupRegistry);
        this.actionEngine = new MinionActionEngine(repository, sellService, this::requestSave);
        this.displayService = new MinionDisplayService(plugin, config, scheduler, repository, nativeItemBridge, cosmeticAnimationBridge(plugin), minionEntityKey, this::requestSave);
    }

    private EconomyBridge economyBridge(JavaPlugin plugin) {
        if (!config.runtimeSettings().integrations().vault() || !plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
            return new UnavailableEconomyBridge();
        }
        return new VaultEconomyBridge(plugin.getServer());
    }

    private net.danh.islandportal.minion.integration.CosmeticAnimationBridge cosmeticAnimationBridge(JavaPlugin plugin) {
        if (!config.runtimeSettings().integrations().packetEvents() || (!plugin.getServer().getPluginManager().isPluginEnabled("packetevents") && !plugin.getServer().getPluginManager().isPluginEnabled("PacketEvents"))) {
            return new NoopCosmeticAnimationBridge();
        }
        return new PacketEventsCosmeticAnimationBridge(config.runtimeSettings().integrations().packetEventsViewRange());
    }

    public void load() {
        repository.load();
        scheduler.runGlobal(() -> {
            displayService.removeLoaded();
            for (ManagedMinion minion : repository.all()) {
                spawn(minion);
            }
        });
        startTasks();
    }

    public void reload() {
        config.reload();
        reloadHelpers();
        scheduler.runGlobal(() -> {
            displayService.removeLoaded();
            repository.load();
            for (ManagedMinion minion : repository.all()) {
                spawn(minion);
            }
        });
        startTasks();
    }

    public void shutdown() {
        if (productionTask != null) {
            productionTask.cancel();
            productionTask = null;
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        if (respawnTask != null) {
            respawnTask.cancel();
            respawnTask = null;
        }
        saveIfDirty();
        displayService.removeLoaded();
    }

    public ItemStack createMinionItem(MinionType type, int amount) {
        return itemFactory.create(type, amount);
    }

    public ItemStack createMinionItem(MinionType type, int amount, long actions) {
        return itemFactory.create(type, amount, actions);
    }

    private ItemStack createMinionItem(MinionType type, ManagedMinion minion, int amount) {
        ItemStack item = itemFactory.create(type, amount, minion.actionCount());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            long now = System.currentTimeMillis();
            meta.getPersistentDataContainer().set(minionTierKey, PersistentDataType.INTEGER, minion.tier());
            meta.getPersistentDataContainer().set(minionActionsKey, PersistentDataType.LONG, minion.actionCount());
            meta.getPersistentDataContainer().set(minionFuelIdKey, PersistentDataType.STRING, minion.fuelId() == null ? "" : minion.fuelId());
            meta.getPersistentDataContainer().set(minionFuelRemainingKey, PersistentDataType.LONG, Math.max(0L, minion.fuelUntilMillis() - now));
            meta.getPersistentDataContainer().set(minionBoosterIdKey, PersistentDataType.STRING, minion.boosterId() == null ? "" : minion.boosterId());
            meta.getPersistentDataContainer().set(minionBoosterRemainingKey, PersistentDataType.LONG, Math.max(0L, minion.boosterUntilMillis() - now));
            meta.getPersistentDataContainer().set(minionStorageKey, PersistentDataType.STRING, serializeStorage(minion.storage()));

            // Add stats to lore
            List<net.kyori.adventure.text.Component> lore = meta.hasLore() ? new java.util.ArrayList<>(meta.lore()) : new java.util.ArrayList<>();
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(config.message("item-stat-tier", "%tier%", String.valueOf(minion.tier()))));
            lore.add(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(config.message("item-stat-actions", "%actions%", String.valueOf(minion.actionCount()))));
            String storageLimit = String.valueOf(type.tier(minion.tier()).storageLimit());
            lore.add(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(config.message("item-stat-storage", "%stored%", String.valueOf(storageAmount(minion.storage())), "%storage_limit%", storageLimit)));
            meta.lore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    public void giveMinionItem(Player player, MinionType type, int amount) {
        scheduler.runFor(player, () -> giveOrDrop(player, createMinionItem(type, amount)));
    }

    public void giveFuelItem(Player player, MinionFuel fuel, int amount) {
        scheduler.runFor(player, () -> giveOrDrop(player, createFuelItem(fuel, amount)));
    }

    public boolean removeNearest(Location location, int radius, boolean returnItem) {
        ManagedMinion minion = repository.nearest(location, radius);
        if (minion == null) {
            return false;
        }
        removeMinion(minion, returnItem ? location : null);
        return true;
    }

    public boolean inspectNearest(Player player, int radius) {
        ManagedMinion minion = repository.nearest(player.getLocation(), radius);
        if (minion == null) {
            return false;
        }
        MinionType type = config.type(minion.type());
        String storageLimit = type == null ? "?" : String.valueOf(type.tier(minion.tier()).storageLimit());
        String chest = minion.chestLocation() == null ? "none" : minion.chestWorld() + " " + minion.chestX() + "," + minion.chestY() + "," + minion.chestZ();
        player.sendMessage(message("inspect-id", "%id%", minion.id()));
        player.sendMessage(message("inspect-type", "%type%", minion.type(), "%tier%", String.valueOf(minion.tier())));
        player.sendMessage(message("inspect-owner", "%owner%", minion.owner()));
        player.sendMessage(message("inspect-island", "%island%", String.valueOf(minion.islandId())));
        player.sendMessage(message("inspect-storage", "%stored%", String.valueOf(storageAmount(minion.storage())), "%storage_limit%", storageLimit));
        player.sendMessage(message("inspect-actions", "%actions%", String.valueOf(minion.actionCount())));
        player.sendMessage(message("inspect-fuel", "%fuel%", fuelText(minion) + " booster " + boosterText(minion)));
        player.sendMessage(message("inspect-chest", "%chest%", chest));
        return true;
    }

    public void handleIslandRemoved(String islandId, Location islandLocation) {
        for (ManagedMinion minion : repository.all()) {
            Location location = minion.location();
            boolean sameIsland = islandId != null && islandId.equals(minion.islandId());
            int cleanupRadius = config.runtimeSettings().islandCleanupRadius();
            boolean nearby = islandLocation != null && location != null && location.getWorld().equals(islandLocation.getWorld()) && location.distanceSquared(islandLocation) <= cleanupRadius * cleanupRadius;
            if (sameIsland || nearby) {
                returnMinionToOwner(minion);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceMinion(PlayerInteractEvent event) {
        if (!config.enabled() || event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        MinionType type = itemFactory.typeFromItem(event.getItem());
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!canPlaceType(player, type)) {
            player.sendMessage(message("missing-place-permission", "%permission%", config.runtimeSettings().permissions().placePrefix() + type.id()));
            return;
        }
        PreMinionPlaceEvent placeEvent = new PreMinionPlaceEvent(player, type, event.getClickedBlock().getRelative(event.getBlockFace()).getLocation());
        plugin.getServer().getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            return;
        }
        if (!placeEvent.overridePlayerLimit() && repository.countByOwnerAndType(player.getUniqueId().toString(), type.id()) >= type.maxPerOwner()) {
            player.sendMessage(message("limit-type"));
            return;
        }
        BlockFace face = event.getBlockFace() == BlockFace.DOWN ? BlockFace.UP : event.getBlockFace();
        Location desired = event.getClickedBlock().getRelative(face).getLocation().add(0.5, 0.0, 0.5);
        desired.setYaw(player.getLocation().getYaw());
        desired.setPitch(0.0f);
        placeMinion(player, type, desired, event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMinionInteract(PlayerInteractAtEntityEvent event) {
        ManagedMinion minion = minion(event.getRightClicked());
        if (minion == null) {
            return;
        }
        event.setCancelled(true);
        if (!canManage(event.getPlayer(), minion)) {
            event.getPlayer().sendMessage(message("cannot-manage"));
            return;
        }
        scheduler.runFor(event.getPlayer(), () -> openMenu(event.getPlayer(), minion.id()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMinionDamage(EntityDamageByEntityEvent event) {
        ManagedMinion minion = minion(event.getEntity());
        if (minion == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            if (!canManage(player, minion)) {
                player.sendMessage(message("cannot-manage"));
                return;
            }
            if (player.isSneaking()) {
                pickupMinion(player, minion);
                return;
            }
            scheduler.runFor(player, () -> openMenu(player, minion.id()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMinionDeath(EntityDeathEvent event) {
        ManagedMinion minion = minion(event.getEntity());
        if (minion == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        repository.add(minion.withEntityId(null));
        requestSave();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MinionMenuHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        String action = clicked.getItemMeta().getPersistentDataContainer().get(menuActionKey, PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        ManagedMinion minion = repository.byId(holder.minionId());
        if (minion == null) {
            player.closeInventory();
            return;
        }
        if (!canManage(player, minion)) {
            player.closeInventory();
            player.sendMessage(message("cannot-manage"));
            return;
        }
        switch (action) {
            case "collect" -> collect(player, minion);
            case "fuel" -> addPowerItem(player, minion, event.getCursor(), false);
            case "booster" -> addPowerItem(player, minion, event.getCursor(), true);
            case "upgrade" -> upgrade(player, minion);
            case "pickup" -> pickupMinion(player, minion);
            case "sell" -> sell(player, minion);
            case "rotate" -> rotate(minion);
            case "link_chest" -> linkNearestChest(player, minion);
            case "close" -> player.closeInventory();
            default -> {
            }
        }
        ManagedMinion updated = repository.byId(holder.minionId());
            if (updated != null && !action.equals("pickup") && !action.equals("close")) {
            scheduler.runFor(player, () -> openMenu(player, updated.id()));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof MinionMenuHolder holder) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        List<Integer> topSlots = event.getRawSlots().stream()
                .filter(slot -> slot < topSize)
                .toList();
        if (topSlots.isEmpty()) {
            return;
        }
        if (topSlots.size() != 1) {
            event.setCancelled(true);
            return;
        }
        String action = actionByRawSlot(topSlots.getFirst());
        if (!"fuel".equals(action) && !"booster".equals(action)) {
            event.setCancelled(true);
            return;
        }
        ManagedMinion minion = repository.byId(holder.minionId());
        if (minion == null || !canManage(player, minion)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        addPowerItem(player, minion, event.getOldCursor(), "booster".equals(action));
        ManagedMinion updated = repository.byId(holder.minionId());
        if (updated != null) {
            scheduler.runFor(player, () -> openMenu(player, updated.id()));
        }
    }

    private void placeMinion(Player player, MinionType type, Location desired, ItemStack sourceItem) {
        scheduler.runAtLoaded(desired, () -> {
            Location location = safeStandLocation(desired);
            if (location == null) {
                scheduler.runFor(player, () -> player.sendMessage(message("no-safe-location")));
                return;
            }
            String id = config.runtimeSettings().idPrefix() + UUID.randomUUID();
            String owner = player.getUniqueId().toString();
            String islandId = islandKey(player, location);
            if (repository.countByIsland(islandId) >= config.runtimeSettings().perIslandGlobalLimit()) {
                scheduler.runFor(player, () -> player.sendMessage(message("limit-island")));
                return;
            }
            ManagedMinion minion = restoreFromItem(ManagedMinion.of(id, type, location, owner, islandId, List.of(owner)), sourceItem);
            
            // Check for overlap
            int size = type.tier(minion.tier()).actionSize();
            double radius = size / 2.0;
            boolean overlaps = false;
            for (ManagedMinion existing : repository.all()) {
                if (existing.location() != null && existing.location().getWorld().equals(location.getWorld())) {
                    MinionType existingType = config.type(existing.type());
                    if (existingType != null) {
                        int existingSize = existingType.tier(existing.tier()).actionSize();
                        double existingRadius = existingSize / 2.0;
                        double distanceX = Math.abs(existing.location().getX() - location.getX());
                        double distanceZ = Math.abs(existing.location().getZ() - location.getZ());
                        if (distanceX < radius + existingRadius && distanceZ < radius + existingRadius) {
                            overlaps = true;
                            break;
                        }
                    }
                }
            }
            if (overlaps) {
                scheduler.runFor(player, () -> player.sendMessage(config.message("placement-overlap", "<red>You cannot place minions too close to each other. Work areas cannot overlap.")));
                return;
            }

            repository.add(minion);
            spawn(minion);
            requestSave();
            if (type.itemMaterial() != Material.AIR && player.getGameMode() != GameMode.CREATIVE && sourceItem != null) {
                scheduler.runFor(player, () -> sourceItem.subtract(1));
            }
        });
    }

    private void spawn(ManagedMinion minion) {
        displayService.spawn(minion);
    }

    private void startTasks() {
        if (productionTask != null) {
            productionTask.cancel();
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (respawnTask != null) {
            respawnTask.cancel();
        }
        productionTask = scheduler.runGlobalTimer(this::tickProduction, config.tickIntervalTicks(), config.tickIntervalTicks());
        long autosaveTicks = 20L * 60L * config.autosaveIntervalMinutes();
        autosaveTask = scheduler.runGlobalTimer(() -> scheduler.runAsync(this::saveIfDirty), autosaveTicks, autosaveTicks);
        respawnTask = scheduler.runGlobalTimer(this::respawnMissing, config.respawnCheckTicks(), config.respawnCheckTicks());
    }

    private void tickProduction() {
        if (!config.enabled()) {
            return;
        }
        int scheduled = 0;
        long now = System.currentTimeMillis();
        if (tickIterator == null || !tickIterator.hasNext()) {
            tickIterator = repository.all().iterator();
        }
        while (tickIterator.hasNext()) {
            if (scheduled >= config.maxActionsPerTick()) {
                return;
            }
            ManagedMinion minion = tickIterator.next();
            MinionType type = config.type(minion.type());
            Location location = minion.location();
            if (type == null || location == null) {
                continue;
            }
            if (!config.runtimeSettings().workWhenOwnerOffline() && onlinePlayer(minion.owner()) == null) {
                continue;
            }
            scheduled++;
            scheduler.runAtLoaded(location, () -> produce(minion, type, now));
        }
    }

    private int produce(ManagedMinion minion, MinionType type, long now) {
        MinionTier tier = type.tier(minion.tier());
        if (type.requiresFuel() && now > minion.fuelUntilMillis()) {
            if (minion.lastProductionMillis() < now) {
                repository.add(minion.withProduction(now, minion.storage()));
                requestSave();
            }
            return 0;
        }
        MinionFuel activeFuel = now <= minion.fuelUntilMillis() ? config.fuel(minion.fuelId()) : null;
        MinionFuel activeBooster = now <= minion.boosterUntilMillis() ? config.fuel(minion.boosterId()) : null;
        double multiplier = activeFuel == null ? 1.0 : minion.fuelMultiplier();
        long interval = Math.max(1L, Math.round(tier.intervalMillis() / multiplier));
        long elapsed = Math.min(now - minion.lastProductionMillis(), offlineLimit(type, minion));
        if (elapsed < interval) {
            return 0;
        }
        long operations = elapsed / interval;
        ManagedMinion workingMinion = orientForWork(minion, tier);
        Map<String, Integer> storage = new LinkedHashMap<>(workingMinion.storage());
        int produced = 0;
        Location workTarget = workTarget(workingMinion, tier);
        displayService.showWorkTarget(workingMinion.id(), type, workTarget);
        displayService.animate(workingMinion);
        for (long index = 0; index < operations && produced < config.maxActionsPerTick(); index++) {
            List<MinionDrop> drops = actionEngine.drops(workingMinion, type, activeBooster);
            if (!canAddDrops(storage, drops, tier.storageLimit())) {
                break;
            }
            addDrops(storage, drops);
            produced++;
        }
        if (produced > 0 && config.runtimeSettings().outputToLinkedChest()) {
            flushStorageToLinkedChest(minion, storage);
        }
        if (produced > 0 && config.runtimeSettings().autoSellEnabled() && type.shop().enabled() && !storage.isEmpty()) {
            sellService.sell(null, type, workingMinion, storage);
            storage.clear();
        }
        long lastProduction = workingMinion.lastProductionMillis() + produced * interval;
        if (produced == 0 && storageAmount(storage) >= tier.storageLimit()) {
            lastProduction = now;
        }
        if (produced > 0 || lastProduction != workingMinion.lastProductionMillis()) {
            repository.add(workingMinion.withProduction(lastProduction, storage, produced));
            requestSave();
        }
        return produced;
    }

    private ManagedMinion orientForWork(ManagedMinion minion, MinionTier tier) {
        Location target = workTarget(minion, tier);
        Location base = minion.location();
        if (base == null || target == null) {
            return minion;
        }
        float yaw = yawToward(base, target);
        ManagedMinion oriented = minion.withRotation(yaw);
        repository.add(oriented);
        Entity entity = oriented.entityId() == null ? null : plugin.getServer().getEntity(oriented.entityId());
        if (entity != null) {
            Location entityLocation = entity.getLocation();
            entityLocation.setYaw(yaw);
            scheduler.runFor(entity, () -> entity.teleportAsync(entityLocation));
        }
        return oriented;
    }

    private Location workTarget(ManagedMinion minion, MinionTier tier) {
        Location base = minion.location();
        if (base == null || base.getWorld() == null) {
            return null;
        }
        int size = Math.max(1, tier.actionSize());
        int start = -(size / 2);
        int index = (int) Math.floorMod(minion.actionCount(), (long) size * size);
        int xOffset = start + (index % size);
        int zOffset = start + (index / size);
        if (xOffset == 0 && zOffset == 0 && size > 1) {
            xOffset = 1;
        }
        return new Location(
                base.getWorld(),
                base.getBlockX() + xOffset + 0.5,
                base.getBlockY() + config.runtimeSettings().animations().targetYOffset(),
                base.getBlockZ() + zOffset + 0.5,
                base.getYaw(),
                base.getPitch()
        );
    }

    private float yawToward(Location from, Location to) {
        Vector offset = to.toVector().subtract(from.toVector());
        if (offset.lengthSquared() <= 0.0001) {
            return from.getYaw();
        }
        return (float) Math.toDegrees(Math.atan2(-offset.getX(), offset.getZ()));
    }

    private boolean canAddDrops(Map<String, Integer> storage, List<MinionDrop> drops, int limit) {
        int total = storageAmount(storage);
        int adding = drops.stream().mapToInt(MinionDrop::amount).sum();
        return total + adding <= limit;
    }

    private void addDrops(Map<String, Integer> storage, List<MinionDrop> drops) {
        for (MinionDrop drop : drops) {
            storage.merge(nativeItemBridge.storageKey(drop.itemKey(), drop.material()), drop.amount(), Integer::sum);
        }
    }

    private int storageAmount(Map<String, Integer> storage) {
        return storage.values().stream().mapToInt(Integer::intValue).sum();
    }

    private void openMenu(Player player, String minionId) {
        ManagedMinion minion = repository.byId(minionId);
        if (minion == null) {
            return;
        }
        MinionType type = config.type(minion.type());
        if (type == null) {
            return;
        }
        String title = placeholders(config.menuTitle(), type, minion);
        Inventory inventory = plugin.getServer().createInventory(new MinionMenuHolder(minion.id()), config.menuSize(), miniMessage.deserialize(title));
        for (MinionMenuItem item : config.menuItems()) {
            inventory.setItem(item.slot(), menuItem(item, type, minion));
        }
        player.openInventory(inventory);
    }

    private ItemStack menuItem(MinionMenuItem configured, MinionType type, ManagedMinion minion) {
        ItemStack item = new ItemStack(configured.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize(placeholders(configured.name(), type, minion)));
        if (configured.customModelData() != null) {
            org.bukkit.inventory.meta.components.CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of((float) configured.customModelData().intValue()));
            meta.setCustomModelDataComponent(component);
        }
        List<String> lore = expandedLore(configured.lore(), type, minion);
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(miniMessage::deserialize).toList());
        }
        meta.getPersistentDataContainer().set(menuActionKey, PersistentDataType.STRING, configured.action());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFuelItem(MinionFuel fuel, int amount) {
        ItemStack item = new ItemStack(fuel.material(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(miniMessage.deserialize(fuelPlaceholders(config.runtimeSettings().fuelItems().displayName(), fuel)));
            meta.lore(config.runtimeSettings().fuelItems().lore().stream()
                    .map(line -> miniMessage.deserialize(fuelPlaceholders(line, fuel)))
                    .toList());
            meta.getPersistentDataContainer().set(minionFuelItemKey, PersistentDataType.STRING, fuel.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> expandedLore(List<String> lore, MinionType type, ManagedMinion minion) {
        List<String> expanded = new java.util.ArrayList<>();
        for (String line : lore) {
            if ("%upgrade_lore%".equals(line)) {
                expanded.addAll(upgradeLore(type, minion));
                continue;
            }
            expanded.add(placeholders(line, type, minion));
        }
        return expanded;
    }

    private String placeholders(String input, MinionType type, ManagedMinion minion) {
        return input
                .replace("%type%", type.id())
                .replace("%tier%", String.valueOf(minion.tier()))
                .replace("%stored%", String.valueOf(storageAmount(minion.storage())))
                .replace("%actions%", String.valueOf(minion.actionCount()))
                .replace("%storage_limit%", String.valueOf(type.tier(minion.tier()).storageLimit()))
                .replace("%action_size%", String.valueOf(type.tier(minion.tier()).actionSize()))
                .replace("%fuel%", fuelText(minion))
                .replace("%booster%", boosterText(minion));
    }

    private List<String> upgradeLore(MinionType type, ManagedMinion minion) {
        MinionTier next = type.nextTier(minion.tier());
        if (next == null) {
            return List.of(config.message("upgrade-max-tier-lore"));
        }
        if (next.upgradeCost().isEmpty()) {
            if (next.upgradeMoney() <= 0.0) {
                return List.of(
                        config.message("upgrade-tier-lore", "%tier%", String.valueOf(next.level())),
                        config.message("upgrade-cost-lore"),
                        config.message("upgrade-cost-free-lore")
                );
            }
        }
        List<String> lore = new java.util.ArrayList<>();
        lore.add(config.message("upgrade-tier-lore", "%tier%", String.valueOf(next.level())));
        lore.add(config.message("upgrade-cost-lore"));
        if (next.upgradeMoney() > 0.0) {
            lore.add(config.message("upgrade-cost-money-lore", "%amount%", String.valueOf(next.upgradeMoney())));
        }
        for (MinionRequirement requirement : next.upgradeCost()) {
            lore.add(config.message("upgrade-cost-item-lore", "%amount%", String.valueOf(requirement.amount()), "%item%", nativeItemBridge.storageKey(requirement.itemKey(), requirement.material())));
        }
        return lore;
    }

    private String fuelText(ManagedMinion minion) {
        long remaining = minion.fuelUntilMillis() - System.currentTimeMillis();
        if (remaining <= 0) {
            return config.message("fuel-none");
        }
        return config.message("fuel-active", "%time%", String.valueOf(Math.max(1, remaining / 1000L)), "%multiplier%", String.valueOf(minion.fuelMultiplier()));
    }

    private String boosterText(ManagedMinion minion) {
        long remaining = minion.boosterUntilMillis() - System.currentTimeMillis();
        if (remaining <= 0) {
            return config.message("fuel-none");
        }
        return config.message("fuel-active", "%time%", String.valueOf(Math.max(1, remaining / 1000L)), "%multiplier%", String.valueOf(minion.boosterMultiplier()));
    }

    private String displayName(MinionType type, ManagedMinion minion) {
        String configured = minion.customName() == null || minion.customName().isBlank() ? type.display().name() : minion.customName();
        return configured.replace("%tier%", String.valueOf(minion.tier())).replace("%type%", type.id());
    }

    private void collect(Player player, ManagedMinion minion) {
        if (minion.storage().isEmpty()) {
            player.sendMessage(message("storage-empty"));
            return;
        }
        for (Map.Entry<String, Integer> entry : minion.storage().entrySet()) {
            Material material = Material.matchMaterial(entry.getKey());
            giveOrDrop(player, nativeItemBridge.item(entry.getKey(), material == null ? Material.STONE : material, entry.getValue()));
        }
        repository.add(minion.withProduction(System.currentTimeMillis(), Map.of()));
        requestSave();
    }

    private void addPowerItem(Player player, ManagedMinion minion, ItemStack offeredItem, boolean booster) {
        if (offeredItem == null || offeredItem.getType().isAir()) {
            player.sendMessage(message("fuel-missing"));
            return;
        }
        for (MinionFuel fuel : config.fuels()) {
            if (!matchesFuelItem(offeredItem, fuel)) {
                continue;
            }
            offeredItem.subtract(1);
            long now = System.currentTimeMillis();
            ManagedMinion updated;
            if (booster) {
                long base = Math.max(now, minion.boosterUntilMillis());
                updated = minion.withBooster(fuel.id(), base + fuel.durationMillis(), fuel.outputMultiplier());
            } else {
                long base = Math.max(now, minion.fuelUntilMillis());
                updated = minion.withFuel(fuel.id(), base + fuel.durationMillis(), fuel.speedMultiplier());
            }
            repository.add(updated);
            requestSave();
            player.sendMessage(message(booster ? "booster-added" : "fuel-added", "%fuel%", fuel.id()));
            return;
        }
        player.sendMessage(message("fuel-missing"));
    }

    private boolean matchesFuelItem(ItemStack item, MinionFuel fuel) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String fuelId = item.getItemMeta().getPersistentDataContainer().get(minionFuelItemKey, PersistentDataType.STRING);
        if (fuelId != null) {
            return fuel.id().equalsIgnoreCase(fuelId);
        }
        return nativeItemBridge.matches(item, fuel.material().name(), fuel.material());
    }

    private String fuelPlaceholders(String input, MinionFuel fuel) {
        return input
                .replace("%fuel%", fuel.id())
                .replace("%material%", fuel.material().name())
                .replace("%speed%", String.valueOf(fuel.speedMultiplier()))
                .replace("%output%", String.valueOf(fuel.outputMultiplier()));
    }

    private void upgrade(Player player, ManagedMinion minion) {
        MinionType type = config.type(minion.type());
        if (type == null) {
            return;
        }
        MinionTier next = type.nextTier(minion.tier());
        if (next == null) {
            player.sendMessage(message("max-tier"));
            return;
        }
        if (!hasRequirements(player, next.upgradeCost())) {
            player.sendMessage(message("missing-upgrade-items"));
            return;
        }
        if (next.upgradeMoney() > 0.0 && !economyBridge.withdraw(player, next.upgradeMoney())) {
            player.sendMessage(message(economyBridge.available() ? "missing-upgrade-money" : "vault-missing"));
            return;
        }
        for (MinionRequirement requirement : next.upgradeCost()) {
            consume(player, requirement.itemKey(), requirement.material(), requirement.amount());
        }
        ManagedMinion upgraded = minion.withTier(next.level());
        repository.add(upgraded);
        displayService.refresh(upgraded);
        requestSave();
        player.sendMessage(message("upgraded", "%tier%", String.valueOf(next.level())));
    }

    private boolean hasRequirements(Player player, List<MinionRequirement> requirements) {
        for (MinionRequirement requirement : requirements) {
            if (count(player, requirement.itemKey(), requirement.material()) < requirement.amount()) {
                return false;
            }
        }
        return true;
    }

    private boolean canManage(Player player, ManagedMinion minion) {
        String playerId = player.getUniqueId().toString();
        return player.hasPermission("islandportal.admin") || playerId.equals(minion.owner()) || minion.islandMembers().contains(playerId);
    }

    private boolean canPlaceType(Player player, MinionType type) {
        if (!config.runtimeSettings().permissions().placePerType()) {
            return true;
        }
        if (config.runtimeSettings().permissions().adminBypass() && player.hasPermission("islandportal.admin")) {
            return true;
        }
        return player.hasPermission(config.runtimeSettings().permissions().placePrefix() + type.id());
    }

    private long offlineLimit(MinionType type, ManagedMinion minion) {
        Player owner = onlinePlayer(minion.owner());
        if (owner == null) {
            return type.offlineLimitMillis();
        }
        long limit = type.offlineLimitMillis();
        for (MinionOfflineLimit permissionLimit : type.offlineLimits()) {
            if (owner.hasPermission(permissionLimit.permission())) {
                limit = Math.max(limit, permissionLimit.limitMillis());
            }
        }
        return limit;
    }

    private Player onlinePlayer(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return null;
        }
        try {
            return plugin.getServer().getPlayer(UUID.fromString(playerId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void sell(Player player, ManagedMinion minion) {
        MinionType type = config.type(minion.type());
        if (type == null || !type.shop().enabled()) {
            player.sendMessage(message("shop-missing"));
            return;
        }
        if (minion.storage().isEmpty()) {
            player.sendMessage(message("storage-empty"));
            return;
        }
        sellService.sell(player, type, minion, minion.storage());
        repository.add(minion.withProduction(System.currentTimeMillis(), Map.of()));
        requestSave();
        player.sendMessage(message("storage-sold"));
    }

    private void rotate(ManagedMinion minion) {
        ManagedMinion rotated = minion.withRotation((minion.yaw() + 90.0f) % 360.0f);
        repository.add(rotated);
        Entity entity = rotated.entityId() == null ? null : plugin.getServer().getEntity(rotated.entityId());
        if (entity != null) {
            Location location = entity.getLocation();
            location.setYaw(rotated.yaw());
            scheduler.runFor(entity, () -> entity.teleportAsync(location));
        }
        displayService.refresh(rotated);
        requestSave();
    }

    private void linkNearestChest(Player player, ManagedMinion minion) {
        Location location = minion.location();
        if (location == null || location.getWorld() == null) {
            return;
        }
        int range = config.runtimeSettings().linkChestRange();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    Block block = location.getBlock().getRelative(x, y, z);
                    if (block.getState() instanceof Container) {
                        MinionChestLinkEvent linkEvent = new MinionChestLinkEvent(player, minion, block);
                        plugin.getServer().getPluginManager().callEvent(linkEvent);
                        if (linkEvent.isCancelled()) {
                            player.sendMessage(message(linkEvent.failMessageKey() == null || linkEvent.failMessageKey().isBlank() ? "chest-link-cancelled" : linkEvent.failMessageKey()));
                            return;
                        }
                        repository.add(minion.withChest(block.getLocation()));
                        requestSave();
                        player.sendMessage(message("chest-linked"));
                        return;
                    }
                }
            }
        }
        player.sendMessage(message("chest-missing"));
    }

    private int count(Player player, Material material) {
        return count(player, material.name(), material);
    }

    private int count(Player player, String itemKey, Material material) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (nativeItemBridge.matches(item, itemKey, material)) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    private boolean consume(Player player, Material material, int amount) {
        return consume(player, material.name(), material, amount);
    }

    private boolean consume(Player player, String itemKey, Material material, int amount) {
        if (count(player, itemKey, material) < amount) {
            return false;
        }
        int remaining = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (!nativeItemBridge.matches(item, itemKey, material)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.subtract(take);
            remaining -= take;
            if (remaining <= 0) {
                return true;
            }
        }
        return true;
    }

    private void pickupMinion(Player player, ManagedMinion minion) {
        PreMinionPickupEvent pickupEvent = new PreMinionPickupEvent(player, minion);
        plugin.getServer().getPluginManager().callEvent(pickupEvent);
        if (pickupEvent.isCancelled()) {
            return;
        }
        collect(player, minion);
        ManagedMinion latest = repository.byId(minion.id());
        if (latest == null) {
            return;
        }
        MinionType type = config.type(latest.type());
        removeMinion(latest, null);
        if (type != null) {
            giveOrDrop(player, createMinionItem(type, latest, 1));
        }
        player.closeInventory();
    }

    private void removeMinion(ManagedMinion minion, Location dropLocation) {
        displayService.removeCosmetics(minion.id());
        repository.remove(minion.id());
        if (minion.entityId() != null) {
            Entity entity = plugin.getServer().getEntity(minion.entityId());
            if (entity != null) {
                scheduler.runFor(entity, entity::remove);
            }
        }
        MinionType type = config.type(minion.type());
        if (dropLocation != null && type != null && dropLocation.getWorld() != null) {
            scheduler.runAtLoaded(dropLocation, () -> dropLocation.getWorld().dropItemNaturally(dropLocation, createMinionItem(type, minion, 1)));
        }
        requestSave();
    }

    private void returnMinionToOwner(ManagedMinion minion) {
        displayService.removeCosmetics(minion.id());
        MinionType type = config.type(minion.type());
        if (type == null) {
            removeMinion(minion, null);
            return;
        }
        ItemStack item = createMinionItem(type, minion, 1);
        repository.remove(minion.id());
        if (minion.entityId() != null) {
            Entity entity = plugin.getServer().getEntity(minion.entityId());
            if (entity != null) {
                scheduler.runFor(entity, entity::remove);
            }
        }
        Player owner = onlinePlayer(minion.owner());
        if (owner != null) {
            scheduler.runFor(owner, () -> giveOrDrop(owner, item));
        } else {
            Location location = minion.location();
            if (location != null && location.getWorld() != null) {
                scheduler.runAtLoaded(location, () -> location.getWorld().dropItemNaturally(location, item));
            }
        }
        requestSave();
    }

    private void respawnMissing() {
        if (!config.enabled()) {
            return;
        }
        for (ManagedMinion minion : repository.all()) {
            if (displayService.isAlive(minion.entityId())) {
                continue;
            }
            spawn(minion.withEntityId(null));
        }
    }

    private ManagedMinion minion(Entity entity) {
        String id = entity.getPersistentDataContainer().get(minionEntityKey, PersistentDataType.STRING);
        return id == null ? null : repository.byId(id);
    }

    private ManagedMinion restoreFromItem(ManagedMinion minion, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return minion;
        }
        var data = item.getItemMeta().getPersistentDataContainer();
        int tier = Math.max(1, data.getOrDefault(minionTierKey, PersistentDataType.INTEGER, minion.tier()));
        long actions = Math.max(0L, data.getOrDefault(minionActionsKey, PersistentDataType.LONG, minion.actionCount()));
        String fuelId = data.getOrDefault(minionFuelIdKey, PersistentDataType.STRING, "");
        long fuelRemaining = Math.max(0L, data.getOrDefault(minionFuelRemainingKey, PersistentDataType.LONG, 0L));
        String boosterId = data.getOrDefault(minionBoosterIdKey, PersistentDataType.STRING, "");
        long boosterRemaining = Math.max(0L, data.getOrDefault(minionBoosterRemainingKey, PersistentDataType.LONG, 0L));
        Map<String, Integer> storage = deserializeStorage(data.getOrDefault(minionStorageKey, PersistentDataType.STRING, ""));
        long now = System.currentTimeMillis();
        MinionFuel fuel = config.fuel(fuelId);
        MinionFuel booster = config.fuel(boosterId);
        return new ManagedMinion(
                minion.id(),
                minion.type(),
                minion.world(),
                minion.x(),
                minion.y(),
                minion.z(),
                minion.yaw(),
                minion.pitch(),
                minion.owner(),
                minion.islandId(),
                minion.islandMembers(),
                minion.customName(),
                minion.chestWorld(),
                minion.chestX(),
                minion.chestY(),
                minion.chestZ(),
                tier,
                System.currentTimeMillis(),
                fuel == null || fuelRemaining <= 0L ? "" : fuel.id(),
                fuel == null || fuelRemaining <= 0L ? 0L : now + fuelRemaining,
                fuel == null ? 1.0 : fuel.speedMultiplier(),
                booster == null || boosterRemaining <= 0L ? "" : booster.id(),
                booster == null || boosterRemaining <= 0L ? 0L : now + boosterRemaining,
                booster == null ? 1.0 : booster.outputMultiplier(),
                actions,
                storage,
                null
        );
    }

    private String serializeStorage(Map<String, Integer> storage) {
        if (storage == null || storage.isEmpty()) {
            return "";
        }
        return storage.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> entry.getKey().replace(";", "").replace("=", "") + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(";"));
    }

    private Map<String, Integer> deserializeStorage(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        Map<String, Integer> storage = new LinkedHashMap<>();
        for (String entry : value.split(";")) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2 || parts[0].isBlank()) {
                continue;
            }
            try {
                int amount = Integer.parseInt(parts[1]);
                if (amount > 0) {
                    storage.put(parts[0], amount);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return storage;
    }

    private String actionByRawSlot(int rawSlot) {
        if (rawSlot < 0 || rawSlot >= config.menuSize()) {
            return "";
        }
        for (MinionMenuItem item : config.menuItems()) {
            if (item.slot() == rawSlot) {
                return item.action();
            }
        }
        return "";
    }

    private Location safeStandLocation(Location preferred) {
        if (isSafeStandLocation(preferred)) {
            return centered(preferred);
        }
        World world = preferred.getWorld();
        if (world == null) {
            return null;
        }
        int baseX = preferred.getBlockX();
        int baseY = preferred.getBlockY();
        int baseZ = preferred.getBlockZ();
        for (int radius = 1; radius <= config.placeRadius(); radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) {
                        continue;
                    }
                    for (int y = config.runtimeSettings().placement().verticalSearchMin(); y <= config.runtimeSettings().placement().verticalSearchMax(); y++) {
                        Location candidate = new Location(world, baseX + x + 0.5, baseY + y, baseZ + z + 0.5, preferred.getYaw(), preferred.getPitch());
                        if (isSafeStandLocation(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isSafeStandLocation(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        return feet.isPassable() && head.isPassable() && ground.getType().isSolid();
    }

    private Location centered(Location location) {
        Location centered = location.clone();
        centered.setX(centered.getBlockX() + 0.5);
        centered.setZ(centered.getBlockZ() + 0.5);
        return centered;
    }

    private void flushStorageToLinkedChest(ManagedMinion minion, Map<String, Integer> storage) {
        Location chestLocation = minion.chestLocation();
        if (chestLocation == null || !(chestLocation.getBlock().getState() instanceof Container container) || storage.isEmpty()) {
            return;
        }
        Map<String, Integer> remaining = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : storage.entrySet()) {
            Material material = Material.matchMaterial(entry.getKey());
            if (material == null) {
                remaining.put(entry.getKey(), entry.getValue());
                continue;
            }
            Map<Integer, ItemStack> leftovers = container.getInventory().addItem(new ItemStack(material, entry.getValue()));
            int left = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            if (left > 0) {
                remaining.put(entry.getKey(), left);
            }
        }
        storage.clear();
        storage.putAll(remaining);
    }

    private String islandKey(Player player, Location location) {
        String ownerFallback = "owner:" + player.getUniqueId();
        if (location == null || location.getWorld() == null) {
            return ownerFallback;
        }
        return ownerFallback;
    }

    private Component message(String key, String... replacements) {
        String value = config.message(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace(replacements[i], replacements[i + 1]);
        }
        return miniMessage.deserialize(value);
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        Location dropLocation = player.getLocation();
        leftover.values().forEach(extra -> scheduler.runAtLoaded(dropLocation, () -> dropLocation.getWorld().dropItemNaturally(dropLocation, extra)));
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

    private void debug(String message) {
        if (config.debug()) {
            plugin.getLogger().info("[Debug] " + message);
        }
    }
}
