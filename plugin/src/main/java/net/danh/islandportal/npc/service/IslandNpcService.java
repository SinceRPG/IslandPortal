package net.danh.islandportal.npc.service;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import net.danh.islandportal.npc.config.IslandNpcConfig;
import net.danh.islandportal.npc.model.ManagedNpc;
import net.danh.islandportal.npc.model.NpcClickAction;
import net.danh.islandportal.npc.model.NpcInteraction;
import net.danh.islandportal.npc.model.NpcType;
import net.danh.islandportal.npc.storage.NpcRepository;
import net.danh.islandportal.platform.PlatformScheduler;
import net.danh.islandportal.platform.PlatformTask;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public final class IslandNpcService implements Listener {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private final IslandNpcConfig config;
    private final PlatformScheduler scheduler;
    private final NpcRepository repository;
    private final NamespacedKey npcIdKey;
    private final Map<String, Long> interactionCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Location> movementTargets = new ConcurrentHashMap<>();
    private final Map<String, Long> nextMovementMillis = new ConcurrentHashMap<>();
    private final AtomicBoolean saveDirty = new AtomicBoolean(false);
    private PacketListenerCommon packetEventsListener;
    private PlatformTask lookTask;
    private PlatformTask movementTask;
    private PlatformTask respawnTask;

    public IslandNpcService(JavaPlugin plugin, IslandNpcConfig config, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.config = config;
        this.scheduler = scheduler;
        this.repository = new NpcRepository(plugin, this::debug);
        this.npcIdKey = new NamespacedKey(plugin, "island_npc_id");
        registerPacketEventsListener();
    }

    public void load() {
        repository.load();
        scheduler.runGlobal(() -> {
            removeLoadedPluginNpcs();
            for (ManagedNpc npc : repository.all()) {
                spawn(npc);
            }
        });
        startRuntimeTasks();
    }

    public void reload() {
        config.reload();
        scheduler.runGlobal(() -> {
            removeLoadedPluginNpcs();
            repository.load();
            for (ManagedNpc npc : repository.all()) {
                spawn(npc);
            }
        });
        startRuntimeTasks();
    }

    public void handleIslandCreated(String islandId, Location islandLocation, String owner, List<String> islandMembers) {
        if (!config.enabled() || islandLocation == null || islandLocation.getWorld() == null) {
            return;
        }
        for (NpcType type : config.defaultIslandNpcs()) {
            String id = "island:" + islandId + ":" + type.id();
            if (repository.contains(id) || !unlocked(type, owner, islandMembers)) {
                continue;
            }
            scheduler.runGlobalLater(() -> createNpc(id, type, islandLocation.clone().add(type.islandOffset()), owner, islandId, islandMembers), config.creationDelayTicks());
        }
    }

    public void handleIslandRemoved(String islandId, Location islandLocation) {
        if (!config.enabled()) {
            return;
        }
        for (ManagedNpc npc : repository.all()) {
            Location location = npc.location();
            boolean sameIsland = islandId != null && islandId.equals(npc.islandId());
            boolean nearby = islandLocation != null && location != null && location.getWorld().equals(islandLocation.getWorld()) && location.distanceSquared(islandLocation) <= 100 * 100;
            if (sameIsland || nearby) {
                removeNpc(npc, true);
            }
        }
    }

    public boolean createManualNpc(String id, NpcType type, Location location, Player owner) {
        if (!config.enabled() || location.getWorld() == null || repository.contains(id)) {
            return false;
        }
        createNpc(id, type, location, owner.getUniqueId().toString(), null, List.of(owner.getUniqueId().toString()));
        return true;
    }

    public boolean removeNearest(Location location, int radius) {
        ManagedNpc npc = repository.nearest(location, radius);
        if (npc == null) {
            return false;
        }
        removeNpc(npc, true);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ManagedNpc npc = npc(event.getRightClicked());
        if (npc == null) {
            return;
        }
        event.setCancelled(true);
        interact(event.getPlayer(), npc, NpcClickAction.RIGHT);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ManagedNpc npc = npc(event.getEntity());
        if (npc == null) {
            return;
        }
        event.setCancelled(true);
        interact(player, npc, NpcClickAction.LEFT);
    }

    private void handleAttackPacket(Player player, int entityId) {
        Entity entity = entityByRuntimeId(player.getWorld(), entityId);
        if (entity == null) {
            return;
        }
        ManagedNpc npc = npc(entity);
        if (npc == null) {
            return;
        }
        scheduler.runFor(player, () -> interact(player, npc, NpcClickAction.LEFT));
    }

    private Entity entityByRuntimeId(World world, int entityId) {
        for (Entity entity : world.getEntities()) {
            if (entity.getEntityId() == entityId) {
                return entity;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onNpcDeath(EntityDeathEvent event) {
        ManagedNpc npc = npc(event.getEntity());
        if (npc == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        repository.add(npc.withEntityId(null));
        movementTargets.remove(npc.id());
        requestSave();
        scheduler.runGlobalLater(() -> respawnIfMissing(npc.id()), config.respawnDelayTicks());
    }

    public void save() {
        saveDirty.set(false);
        repository.save();
    }

    public void shutdown() {
        if (lookTask != null) {
            lookTask.cancel();
            lookTask = null;
        }
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
        if (respawnTask != null) {
            respawnTask.cancel();
            respawnTask = null;
        }
        unregisterPacketEventsListener();
        saveIfDirty();
        interactionCooldowns.clear();
        movementTargets.clear();
        nextMovementMillis.clear();
        removeLoadedPluginNpcs();
    }

    private void registerPacketEventsListener() {
        if (packetEventsListener != null || !packetEventsEnabled()) {
            return;
        }
        packetEventsListener = PacketEvents.getAPI().getEventManager().registerListener(new SimplePacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
                if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY || !(event.getPlayer() instanceof Player player)) {
                    return;
                }
                WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
                if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                    return;
                }
                handleAttackPacket(player, packet.getEntityId());
            }
        });
    }

    private void unregisterPacketEventsListener() {
        if (packetEventsListener == null || !packetEventsEnabled()) {
            packetEventsListener = null;
            return;
        }
        PacketEvents.getAPI().getEventManager().unregisterListener(packetEventsListener);
        packetEventsListener = null;
    }

    private boolean packetEventsEnabled() {
        return plugin.getServer().getPluginManager().isPluginEnabled("packetevents")
                || plugin.getServer().getPluginManager().isPluginEnabled("PacketEvents");
    }

    private void createNpc(String id, NpcType type, Location location, String owner, String islandId, List<String> islandMembers) {
        scheduler.runAtLoaded(location, () -> {
            Location safeLocation = safeSpawnLocation(location, type);
            if (safeLocation == null) {
                debug("Could not find a safe spawn location for NPC " + id + " near " + locationString(location) + ".");
                return;
            }
            safeLocation.setYaw(type.yaw());
            safeLocation.setPitch(type.pitch());
            ManagedNpc npc = ManagedNpc.of(id, type, safeLocation, owner, islandId, islandMembers);
            repository.add(npc);
            spawn(npc);
            requestSave();
            debug("Created NPC " + id + " at " + locationString(safeLocation) + ".");
        });
    }

    private void spawn(ManagedNpc npc) {
        NpcType type = config.type(npc.type());
        Location location = npc.location();
        if (type == null || location == null || location.getWorld() == null) {
            return;
        }
        scheduler.runAtLoaded(location, () -> {
            ManagedNpc latest = repository.byId(npc.id());
            if (latest != null && isEntityAlive(latest.entityId())) {
                return;
            }
            Entity spawned = location.getWorld().spawnEntity(location, type.entityType());
            if (!(spawned instanceof LivingEntity living)) {
                spawned.remove();
                return;
            }
            configureEntity(living, npc.id(), type);
            repository.add(npc.withEntityId(living.getUniqueId()));
            requestSave();
        });
    }

    private void configureEntity(LivingEntity entity, String npcId, NpcType type) {
        entity.customName(miniMessage.deserialize(type.name()));
        entity.setCustomNameVisible(type.nameVisible());
        entity.setAI(false);
        entity.setSilent(type.silent());
        entity.setInvulnerable(type.invulnerable());
        entity.setCollidable(type.collidable());
        entity.setGlowing(type.glowing());
        entity.setPersistent(false);
        entity.setRemoveWhenFarAway(false);
        entity.getPersistentDataContainer().set(npcIdKey, PersistentDataType.STRING, npcId);
        if (entity instanceof Ageable ageable) {
            if (type.baby()) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
        }
        if (entity instanceof Villager villager && !type.profession().isBlank()) {
            profession(type.profession(), villager);
        }
    }

    private void profession(String value, Villager villager) {
        try {
            Villager.Profession profession = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(value.toLowerCase(Locale.ROOT)));
            if (profession != null) {
                villager.setProfession(profession);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private Location safeSpawnLocation(Location preferred, NpcType type) {
        if (!type.spawnSearch().enabled()) {
            return isSafeStandLocation(preferred) ? centered(preferred) : null;
        }
        Location exact = isSafeStandLocation(preferred) ? centered(preferred) : null;
        if (exact != null) {
            return exact;
        }
        World world = preferred.getWorld();
        if (world == null) {
            return null;
        }
        int baseX = preferred.getBlockX();
        int baseY = preferred.getBlockY();
        int baseZ = preferred.getBlockZ();
        int step = type.spawnSearch().step();
        for (int radius = step; radius <= type.spawnSearch().horizontalRadius(); radius += step) {
            for (int x = -radius; x <= radius; x += step) {
                for (int z = -radius; z <= radius; z += step) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) {
                        continue;
                    }
                    for (int yOffset = 0; yOffset <= type.spawnSearch().verticalRadius(); yOffset++) {
                        Location upper = new Location(world, baseX + x + 0.5, baseY + yOffset, baseZ + z + 0.5, preferred.getYaw(), preferred.getPitch());
                        if (isSafeStandLocation(upper)) {
                            return upper;
                        }
                        if (yOffset == 0) {
                            continue;
                        }
                        Location lower = new Location(world, baseX + x + 0.5, baseY - yOffset, baseZ + z + 0.5, preferred.getYaw(), preferred.getPitch());
                        if (isSafeStandLocation(lower)) {
                            return lower;
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
        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        return feet.isPassable() && head.isPassable() && ground.getType().isSolid();
    }

    private Location safeMovementLocation(Location anchor, NpcType type) {
        World world = anchor.getWorld();
        if (world == null) {
            return null;
        }
        double radius = type.movement().radius();
        if (radius <= 0.0) {
            return isSafeStandLocation(anchor) ? centered(anchor) : null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < type.movement().targetAttempts(); attempt++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double distance = random.nextDouble(radius + 0.01);
            Location candidate = anchor.clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
            Location safe = nearestSafeMovementLocation(candidate, anchor, type);
            if (safe != null) {
                return safe;
            }
        }
        return null;
    }

    private Location nearestSafeMovementLocation(Location preferred, Location anchor, NpcType type) {
        if (isWithinMovementRadius(preferred, anchor, type.movement().radius()) && isSafeStandLocation(preferred)) {
            return centered(preferred);
        }
        World world = preferred.getWorld();
        if (world == null) {
            return null;
        }
        int verticalRadius = type.spawnSearch().verticalRadius();
        int x = preferred.getBlockX();
        int baseY = preferred.getBlockY();
        int z = preferred.getBlockZ();
        for (int yOffset = 0; yOffset <= verticalRadius; yOffset++) {
            Location upper = new Location(world, x + 0.5, baseY + yOffset, z + 0.5, preferred.getYaw(), preferred.getPitch());
            if (isWithinMovementRadius(upper, anchor, type.movement().radius()) && isSafeStandLocation(upper)) {
                return upper;
            }
            if (yOffset == 0) {
                continue;
            }
            Location lower = new Location(world, x + 0.5, baseY - yOffset, z + 0.5, preferred.getYaw(), preferred.getPitch());
            if (isWithinMovementRadius(lower, anchor, type.movement().radius()) && isSafeStandLocation(lower)) {
                return lower;
            }
        }
        return null;
    }

    private boolean isWithinMovementRadius(Location location, Location anchor, double radius) {
        return location.getWorld() != null
                && anchor.getWorld() != null
                && location.getWorld().equals(anchor.getWorld())
                && horizontalDistanceSquared(location, anchor) <= radius * radius;
    }

    private double horizontalDistanceSquared(Location first, Location second) {
        double x = first.getX() - second.getX();
        double z = first.getZ() - second.getZ();
        return x * x + z * z;
    }

    private Location centered(Location location) {
        Location centered = location.clone();
        centered.setX(centered.getBlockX() + 0.5);
        centered.setZ(centered.getBlockZ() + 0.5);
        return centered;
    }

    private void interact(Player player, ManagedNpc npc, NpcClickAction clickAction) {
        long now = System.currentTimeMillis();
        String cooldownKey = player.getUniqueId() + ":" + npc.id() + ":" + clickAction.name();
        Long lastUse = interactionCooldowns.get(cooldownKey);
        if (lastUse != null && now - lastUse < config.interactionCooldownMillis()) {
            return;
        }
        interactionCooldowns.put(cooldownKey, now);

        NpcType type = config.type(npc.type());
        if (type == null) {
            return;
        }
        NpcInteraction interaction = type.interactions().get(clickAction);
        if (interaction == null) {
            return;
        }
        for (String message : interaction.messages()) {
            scheduler.runFor(player, () -> player.sendMessage(miniMessage.deserialize(placeholders(message, player, npc, type))));
        }
        for (String command : interaction.playerCommands()) {
            scheduler.runFor(player, () -> player.performCommand(placeholders(command, player, npc, type)));
        }
        if (!interaction.consoleCommands().isEmpty()) {
            scheduler.runGlobal(() -> interaction.consoleCommands().forEach(command -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), placeholders(command, player, npc, type))));
        }
    }

    private ManagedNpc npc(Entity entity) {
        String npcId = entity.getPersistentDataContainer().get(npcIdKey, PersistentDataType.STRING);
        return npcId == null ? null : repository.all().stream().filter(candidate -> candidate.id().equals(npcId)).findFirst().orElse(null);
    }

    private void startRuntimeTasks() {
        startLookTask();
        startMovementTask();
        startRespawnTask();
    }

    private void startLookTask() {
        if (lookTask != null) {
            lookTask.cancel();
        }
        lookTask = scheduler.runGlobalTimer(this::lookAtNearbyPlayers, config.lookIntervalTicks(), config.lookIntervalTicks());
    }

    private void startMovementTask() {
        if (movementTask != null) {
            movementTask.cancel();
        }
        movementTask = scheduler.runGlobalTimer(this::moveNpcs, config.movementIntervalTicks(), config.movementIntervalTicks());
    }

    private void startRespawnTask() {
        if (respawnTask != null) {
            respawnTask.cancel();
        }
        respawnTask = scheduler.runGlobalTimer(this::respawnMissingNpcs, config.respawnCheckTicks(), config.respawnCheckTicks());
    }

    private void lookAtNearbyPlayers() {
        if (!config.enabled()) {
            return;
        }
        for (ManagedNpc npc : repository.all()) {
            NpcType type = config.type(npc.type());
            if (type == null || !type.lookAtPlayer() || npc.entityId() == null) {
                continue;
            }
            Entity entity = plugin.getServer().getEntity(npc.entityId());
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            scheduler.runFor(living, () -> rotateTowardNearestPlayer(living, type.lookRadius()));
        }
    }

    private void rotateTowardNearestPlayer(LivingEntity entity, double radius) {
        Player nearest = entity.getNearbyEntities(radius, radius, radius).stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(entity.getLocation())))
                .orElse(null);
        if (nearest == null) {
            return;
        }
        Location location = entity.getLocation();
        Vector direction = nearest.getEyeLocation().toVector().subtract(location.toVector());
        location.setDirection(direction);
        entity.teleportAsync(location);
    }

    private void moveNpcs() {
        if (!config.enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ManagedNpc npc : repository.all()) {
            NpcType type = config.type(npc.type());
            if (type == null || !type.movement().enabled() || npc.entityId() == null) {
                continue;
            }
            long nextMove = nextMovementMillis.getOrDefault(npc.id(), 0L);
            if (now < nextMove) {
                continue;
            }
            nextMovementMillis.put(npc.id(), now + type.movement().intervalTicks() * 50L);
            Entity entity = plugin.getServer().getEntity(npc.entityId());
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            scheduler.runFor(living, () -> moveNpc(npc, living, type));
        }
    }

    private void moveNpc(ManagedNpc npc, LivingEntity entity, NpcType type) {
        Location anchor = npc.location();
        if (anchor == null || anchor.getWorld() == null || !entity.isValid() || entity.isDead()) {
            return;
        }
        if (!entity.getWorld().equals(anchor.getWorld()) || horizontalDistanceSquared(entity.getLocation(), anchor) > type.movement().radius() * type.movement().radius() * 4.0) {
            entity.teleportAsync(anchor);
            movementTargets.remove(npc.id());
            return;
        }
        Location target = movementTargets.get(npc.id());
        if (target == null || !isSafeStandLocation(target) || entity.getLocation().distanceSquared(target) <= 0.45) {
            target = safeMovementLocation(anchor, type);
            if (target == null) {
                movementTargets.remove(npc.id());
                return;
            }
            movementTargets.put(npc.id(), target);
        }
        Location next = nextStep(entity.getLocation(), target, type.movement().stepDistance());
        if (next == null || !isSafeStandLocation(next)) {
            movementTargets.remove(npc.id());
            return;
        }
        Vector direction = target.toVector().subtract(entity.getLocation().toVector());
        next.setDirection(direction);
        entity.teleportAsync(next);
    }

    private Location nextStep(Location current, Location target, double stepDistance) {
        Vector offset = target.toVector().subtract(current.toVector());
        if (offset.lengthSquared() <= 0.0001) {
            return target.clone();
        }
        Vector step = offset.clone().normalize().multiply(Math.min(stepDistance, offset.length()));
        Location next = current.clone().add(step);
        next.setY(target.getY());
        return next;
    }

    private void respawnMissingNpcs() {
        if (!config.enabled()) {
            return;
        }
        for (ManagedNpc npc : repository.all()) {
            if (isEntityAlive(npc.entityId())) {
                continue;
            }
            scheduler.runGlobalLater(() -> respawnIfMissing(npc.id()), config.respawnDelayTicks());
        }
    }

    private void respawnIfMissing(String npcId) {
        ManagedNpc npc = repository.byId(npcId);
        if (npc == null || isEntityAlive(npc.entityId())) {
            return;
        }
        movementTargets.remove(npc.id());
        nextMovementMillis.remove(npc.id());
        spawn(npc.withEntityId(null));
    }

    private boolean isEntityAlive(UUID entityId) {
        if (entityId == null) {
            return false;
        }
        Entity entity = plugin.getServer().getEntity(entityId);
        return entity instanceof LivingEntity living && living.isValid() && !living.isDead();
    }

    private boolean unlocked(NpcType type, String owner, List<String> islandMembers) {
        if (type.unlock().defaultUnlocked() && type.unlock().permissions().isEmpty() && type.unlock().minIslandMembers() <= 0) {
            return true;
        }
        if (islandMembers.size() < type.unlock().minIslandMembers()) {
            return false;
        }
        if (type.unlock().permissions().isEmpty()) {
            return type.unlock().defaultUnlocked();
        }
        for (String playerId : islandMembers) {
            Player player = onlinePlayer(playerId);
            if (player != null && hasAnyPermission(player, type.unlock().permissions())) {
                return true;
            }
        }
        Player ownerPlayer = onlinePlayer(owner);
        return ownerPlayer != null && hasAnyPermission(ownerPlayer, type.unlock().permissions());
    }

    private boolean hasAnyPermission(Player player, List<String> permissions) {
        return permissions.stream().anyMatch(permission -> !permission.isBlank() && player.hasPermission(permission));
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

    private void removeNpc(ManagedNpc npc, boolean save) {
        repository.remove(npc.id());
        movementTargets.remove(npc.id());
        nextMovementMillis.remove(npc.id());
        if (npc.entityId() != null) {
            Entity entity = plugin.getServer().getEntity(npc.entityId());
            if (entity != null) {
                scheduler.runFor(entity, entity::remove);
            }
        }
        if (save) {
            requestSave();
        }
    }

    private void removeLoadedPluginNpcs() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(npcIdKey)) {
                    entity.remove();
                }
            }
        }
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

    private String placeholders(String text, Player player, ManagedNpc npc, NpcType type) {
        return text
                .replace("%player%", player.getName())
                .replace("%npc%", type.name())
                .replace("%npc_id%", npc.id())
                .replace("%island%", npc.islandId() == null ? "" : npc.islandId())
                .replace("%owner%", npc.owner() == null ? "" : npc.owner());
    }

    private String locationString(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private void debug(String message) {
        if (config.debug()) {
            plugin.getLogger().info("[Debug] " + message);
        }
    }
}
