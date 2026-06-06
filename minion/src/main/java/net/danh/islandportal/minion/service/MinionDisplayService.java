package net.danh.islandportal.minion.service;

import net.danh.islandportal.minion.config.MinionConfig;
import net.danh.islandportal.minion.integration.CosmeticAnimationBridge;
import net.danh.islandportal.minion.integration.NativeItemBridge;
import net.danh.islandportal.minion.model.ManagedMinion;
import net.danh.islandportal.minion.model.MinionAnimationStage;
import net.danh.islandportal.minion.model.MinionDisplayEntitySettings;
import net.danh.islandportal.minion.model.MinionType;
import net.danh.islandportal.minion.storage.MinionRepository;
import net.danh.islandportal.platform.PlatformScheduler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.EulerAngle;

import java.util.List;
import java.util.UUID;

public final class MinionDisplayService {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private final MinionConfig config;
    private final PlatformScheduler scheduler;
    private final MinionRepository repository;
    private final NativeItemBridge nativeItemBridge;
    private final CosmeticAnimationBridge cosmeticAnimationBridge;
    private final NamespacedKey minionEntityKey;
    private final NamespacedKey minionCosmeticKey;
    private final Runnable requestSave;

    public MinionDisplayService(JavaPlugin plugin, MinionConfig config, PlatformScheduler scheduler, MinionRepository repository, NativeItemBridge nativeItemBridge, CosmeticAnimationBridge cosmeticAnimationBridge, NamespacedKey minionEntityKey, Runnable requestSave) {
        this.plugin = plugin;
        this.config = config;
        this.scheduler = scheduler;
        this.repository = repository;
        this.nativeItemBridge = nativeItemBridge;
        this.cosmeticAnimationBridge = cosmeticAnimationBridge;
        this.minionEntityKey = minionEntityKey;
        this.minionCosmeticKey = new NamespacedKey(plugin, "managed_minion_cosmetic_id");
        this.requestSave = requestSave;
    }

    public void spawn(ManagedMinion minion) {
        MinionType type = config.type(minion.type());
        Location location = minion.location();
        if (type == null || location == null || location.getWorld() == null) {
            return;
        }
        scheduler.runAtLoaded(location, () -> {
            ManagedMinion latest = repository.byId(minion.id());
            if (latest != null && isAlive(latest.entityId())) {
                return;
            }
            ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class);
            applyBaseSettings(stand, type, minion);
            stand.getPersistentDataContainer().set(minionEntityKey, PersistentDataType.STRING, minion.id());
            repository.add(minion.withEntityId(stand.getUniqueId()));
            requestSave.run();
        });
    }

    public void refresh(ManagedMinion minion) {
        if (minion.entityId() == null) {
            spawn(minion);
            return;
        }
        Entity entity = plugin.getServer().getEntity(minion.entityId());
        if (!(entity instanceof ArmorStand stand)) {
            spawn(minion.withEntityId(null));
            return;
        }
        scheduler.runFor(stand, () -> {
            MinionType type = config.type(minion.type());
            if (type != null && stand.isValid()) {
                applyBaseSettings(stand, type, minion);
            }
        });
    }

    public void animate(ManagedMinion minion) {
        if (minion.entityId() == null) {
            return;
        }
        Entity entity = plugin.getServer().getEntity(minion.entityId());
        if (!(entity instanceof ArmorStand stand)) {
            return;
        }
        scheduler.runFor(stand, () -> {
            MinionType type = config.type(minion.type());
            if (type != null && !type.display().animationStages().isEmpty()) {
                runAnimationStages(stand, type.display().animationStages());
                return;
            }
            cosmeticAnimationBridge.play(stand, "swing");
            stand.setRightArmPose(new EulerAngle(Math.toRadians(-70), 0.0, 0.0));
            scheduler.runGlobalLater(() -> scheduler.runFor(stand, () -> {
                if (stand.isValid()) {
                    stand.setRightArmPose(EulerAngle.ZERO);
                }
            }), config.runtimeSettings().animationResetTicks());
        });
    }

    public void showWorkTarget(ManagedMinion minion, MinionType type) {
        Location base = minion.location();
        if (base == null || base.getWorld() == null) {
            return;
        }
        showWorkTarget(minion.id(), type, cosmeticTargetLocation(base, minion.yaw()));
    }

    public void showWorkTarget(String minionId, MinionType type, Location target) {
        if (target == null || target.getWorld() == null) {
            return;
        }
        scheduler.runAtLoaded(target, () -> {
            showBlockBreak(target);
            switch (type.action().type()) {
                case SLAYER, SPAWNER -> showTemporaryMob(minionId, target, type);
                case COLLECTOR, FISHER, SELLER, FEEDER -> showTemporaryItem(minionId, target, firstDropMaterial(type));
                case FARMER -> showCropGrowth(minionId, target, firstDropMaterial(type));
                default -> showTemporaryBlock(minionId, target, type.action().spawnBlock());
            }
        });
    }

    public void removeLoaded() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(minionEntityKey) || entity.getPersistentDataContainer().has(minionCosmeticKey)) {
                    entity.remove();
                }
            }
        }
    }

    public void removeCosmetics(String minionId) {
        if (minionId == null || minionId.isBlank()) {
            return;
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                String owner = entity.getPersistentDataContainer().get(minionCosmeticKey, PersistentDataType.STRING);
                if (minionId.equals(owner)) {
                    entity.remove();
                }
            }
        }
    }

    public boolean isAlive(UUID entityId) {
        if (entityId == null) {
            return false;
        }
        Entity entity = plugin.getServer().getEntity(entityId);
        return entity instanceof LivingEntity living && living.isValid() && !living.isDead();
    }

    private void applyBaseSettings(ArmorStand stand, MinionType type, ManagedMinion minion) {
        MinionDisplayEntitySettings settings = config.runtimeSettings().displayEntity();
        stand.customName(miniMessage.deserialize(displayName(type, minion)));
        stand.setCustomNameVisible(settings.customNameVisible());
        stand.setSmall(type.display().small());
        stand.setArms(settings.arms());
        stand.setBasePlate(settings.basePlate());
        stand.setInvulnerable(settings.invulnerable());
        stand.setHealth(Math.min(settings.maxHealth(), type.display().health()));
        stand.setGlowing(type.display().glowing());
        stand.setPersistent(settings.persistent());
        stand.setRemoveWhenFarAway(settings.removeWhenFarAway());
        stand.getEquipment().setHelmet(nativeItemBridge.item(type.display().headItemKey(), type.display().headMaterial(), 1));
        stand.getEquipment().setChestplate(nativeItemBridge.item(type.display().chestplateItemKey(), type.display().chestplateMaterial(), 1));
        stand.getEquipment().setLeggings(nativeItemBridge.item(type.display().leggingsItemKey(), type.display().leggingsMaterial(), 1));
        stand.getEquipment().setBoots(nativeItemBridge.item(type.display().bootsItemKey(), type.display().bootsMaterial(), 1));
        stand.getEquipment().setItemInMainHand(nativeItemBridge.item(type.display().mainHandItemKey(), type.display().mainHandMaterial(), 1));
    }

    private void showTemporaryBlock(String minionId, Location target, Material material) {
        Material displayMaterial = material == null || material.isAir() ? Material.STONE : material;
        Location displayLocation = target.clone().add(-0.5, -0.25, -0.5);
        BlockDisplay display = target.getWorld().spawn(displayLocation, BlockDisplay.class);
        tagCosmetic(display, minionId);
        display.setBlock(displayMaterial.createBlockData());
        display.setRotation(target.getYaw(), 0.0f);
        removeLater(display);
    }

    private void showTemporaryItem(String minionId, Location target, Material material) {
        Material displayMaterial = material == null || material.isAir() ? Material.STONE : material;
        ItemDisplay display = target.getWorld().spawn(target, ItemDisplay.class);
        tagCosmetic(display, minionId);
        display.setItemStack(nativeItemBridge.item(displayMaterial.name(), displayMaterial, 1));
        display.setRotation(target.getYaw(), 0.0f);
        removeLater(display);
    }

    private void showTemporaryMob(String minionId, Location target, MinionType type) {
        Entity entity = target.getWorld().spawnEntity(target, type.action().spawnEntity());
        tagCosmetic(entity, minionId);
        entity.setRotation(target.getYaw(), 0.0f);
        if (entity instanceof LivingEntity living) {
            living.setAI(false);
            living.setSilent(true);
            living.setInvulnerable(true);
            living.setCollidable(false);
        }
        removeLater(entity);
    }

    private void showCropGrowth(String minionId, Location target, Material material) {
        if (!config.runtimeSettings().animations().cropGrowthEnabled()) {
            showTemporaryBlock(minionId, target, material);
            return;
        }
        Material displayMaterial = cropMaterial(material);
        Location displayLocation = target.clone().add(-0.5, -0.25, -0.5);
        BlockDisplay display = target.getWorld().spawn(displayLocation, BlockDisplay.class);
        tagCosmetic(display, minionId);
        display.setRotation(target.getYaw(), 0.0f);
        BlockData initialData = displayMaterial.createBlockData();
        if (!(initialData instanceof Ageable ageable)) {
            display.setBlock(initialData);
            removeLater(display);
            return;
        }
        int maxAge = ageable.getMaximumAge();
        for (int age = 0; age <= maxAge; age++) {
            int scheduledAge = age;
            scheduler.runGlobalLater(() -> scheduler.runFor(display, () -> {
                if (!display.isValid()) {
                    return;
                }
                BlockData data = displayMaterial.createBlockData();
                if (data instanceof Ageable stage) {
                    stage.setAge(scheduledAge);
                    display.setBlock(stage);
                }
            }), (long) age * config.runtimeSettings().animations().cropGrowthStageTicks());
        }
        removeLater(display);
    }

    private void tagCosmetic(Entity entity, String minionId) {
        entity.getPersistentDataContainer().set(minionCosmeticKey, PersistentDataType.STRING, minionId);
    }

    private Material cropMaterial(Material material) {
        if (material == null || material.isAir()) {
            return Material.WHEAT;
        }
        return switch (material) {
            case WHEAT, WHEAT_SEEDS, BREAD -> Material.WHEAT;
            case CARROT, CARROTS -> Material.CARROTS;
            case POTATO, POTATOES -> Material.POTATOES;
            case BEETROOT, BEETROOTS, BEETROOT_SEEDS -> Material.BEETROOTS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> material;
        };
    }

    private void removeLater(Entity entity) {
        scheduler.runGlobalLater(() -> scheduler.runFor(entity, () -> {
            if (entity.isValid()) {
                entity.remove();
            }
        }), config.runtimeSettings().animations().targetVisibleTicks());
    }

    private void showBlockBreak(Location target) {
        if (!config.runtimeSettings().animations().blockBreakEnabled()) {
            return;
        }
        List<Float> progressStages = config.runtimeSettings().animations().blockBreakProgress();
        for (int index = 0; index < progressStages.size(); index++) {
            float progress = progressStages.get(index);
            long delay = index + 1L;
            scheduler.runGlobalLater(() -> scheduler.runAtLoaded(target, () -> {
                for (Player player : target.getWorld().getNearbyPlayers(target, config.runtimeSettings().integrations().packetEventsViewRange())) {
                    player.sendBlockDamage(target, progress);
                }
            }), delay);
        }
        scheduler.runGlobalLater(() -> scheduler.runAtLoaded(target, () -> {
            for (Player player : target.getWorld().getNearbyPlayers(target, config.runtimeSettings().integrations().packetEventsViewRange())) {
                player.sendBlockDamage(target, 0.0f);
            }
        }), config.runtimeSettings().animations().targetVisibleTicks());
    }

    private Location cosmeticTargetLocation(Location base, float yaw) {
        BlockFace face = yawFace(yaw);
        World world = base.getWorld();
        return new Location(world, base.getBlockX() + face.getModX() + 0.5, base.getBlockY() + config.runtimeSettings().animations().targetYOffset(), base.getBlockZ() + face.getModZ() + 0.5, base.getYaw(), base.getPitch());
    }

    private Material firstDropMaterial(MinionType type) {
        return type.drops().isEmpty() ? Material.STONE : type.drops().getFirst().material();
    }

    private BlockFace yawFace(float yaw) {
        float normalized = (yaw % 360 + 360) % 360;
        if (normalized >= 45 && normalized < 135) {
            return BlockFace.WEST;
        }
        if (normalized >= 135 && normalized < 225) {
            return BlockFace.NORTH;
        }
        if (normalized >= 225 && normalized < 315) {
            return BlockFace.EAST;
        }
        return BlockFace.SOUTH;
    }

    private void runAnimationStages(ArmorStand stand, List<MinionAnimationStage> stages) {
        for (MinionAnimationStage stage : stages) {
            scheduler.runGlobalLater(() -> scheduler.runFor(stand, () -> {
                if (!stand.isValid()) {
                    return;
                }
                cosmeticAnimationBridge.play(stand, stage.name());
                stand.setRightArmPose(angle(stage.rightArmX(), stage.rightArmY(), stage.rightArmZ()));
                stand.setLeftArmPose(angle(stage.leftArmX(), stage.leftArmY(), stage.leftArmZ()));
                scheduler.runGlobalLater(() -> scheduler.runFor(stand, () -> {
                    if (stand.isValid()) {
                        stand.setRightArmPose(EulerAngle.ZERO);
                        stand.setLeftArmPose(EulerAngle.ZERO);
                    }
                }), stage.durationTicks());
            }), stage.delayTicks());
        }
    }

    private EulerAngle angle(double x, double y, double z) {
        return new EulerAngle(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z));
    }

    private String displayName(MinionType type, ManagedMinion minion) {
        String configured = minion.customName() == null || minion.customName().isBlank() ? type.display().name() : minion.customName();
        return configured.replace("%tier%", String.valueOf(minion.tier())).replace("%type%", type.id());
    }
}
