package net.danh.islandportal.minion.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;
import java.util.List;
import java.util.UUID;

public record ManagedMinion(
        String id,
        String type,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String owner,
        String islandId,
        List<String> islandMembers,
        String customName,
        String chestWorld,
        int chestX,
        int chestY,
        int chestZ,
        int tier,
        long lastProductionMillis,
        String fuelId,
        long fuelUntilMillis,
        double fuelMultiplier,
        String boosterId,
        long boosterUntilMillis,
        double boosterMultiplier,
        long actionCount,
        Map<String, Integer> storage,
        UUID entityId
) {

    public Location location() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    public ManagedMinion withEntityId(UUID newEntityId) {
        return new ManagedMinion(id, type, world, x, y, z, yaw, pitch, owner, islandId, islandMembers, customName, chestWorld, chestX, chestY, chestZ, tier, lastProductionMillis, fuelId, fuelUntilMillis, fuelMultiplier, boosterId, boosterUntilMillis, boosterMultiplier, actionCount, storage, newEntityId);
    }

    public ManagedMinion withProduction(long newLastProductionMillis, Map<String, Integer> newStorage) {
        return withProduction(newLastProductionMillis, newStorage, 0L);
    }

    public ManagedMinion withProduction(long newLastProductionMillis, Map<String, Integer> newStorage, long addedActions) {
        return new ManagedMinion(id, type, world, x, y, z, yaw, pitch, owner, islandId, islandMembers, customName, chestWorld, chestX, chestY, chestZ, tier, newLastProductionMillis, fuelId, fuelUntilMillis, fuelMultiplier, boosterId, boosterUntilMillis, boosterMultiplier, Math.max(0L, actionCount + addedActions), Map.copyOf(newStorage), entityId);
    }

    public ManagedMinion withTier(int newTier) {
        return new ManagedMinion(id, type, world, x, y, z, yaw, pitch, owner, islandId, islandMembers, customName, chestWorld, chestX, chestY, chestZ, newTier, lastProductionMillis, fuelId, fuelUntilMillis, fuelMultiplier, boosterId, boosterUntilMillis, boosterMultiplier, actionCount, storage, entityId);
    }

    public ManagedMinion withFuel(String newFuelId, long newFuelUntilMillis, double newFuelMultiplier) {
        return new ManagedMinion(id, type, world, x, y, z, yaw, pitch, owner, islandId, islandMembers, customName, chestWorld, chestX, chestY, chestZ, tier, lastProductionMillis, newFuelId, newFuelUntilMillis, newFuelMultiplier, boosterId, boosterUntilMillis, boosterMultiplier, actionCount, storage, entityId);
    }

    public ManagedMinion withBooster(String newBoosterId, long newBoosterUntilMillis, double newBoosterMultiplier) {
        return new ManagedMinion(id, type, world, x, y, z, yaw, pitch, owner, islandId, islandMembers, customName, chestWorld, chestX, chestY, chestZ, tier, lastProductionMillis, fuelId, fuelUntilMillis, fuelMultiplier, newBoosterId, newBoosterUntilMillis, newBoosterMultiplier, actionCount, storage, entityId);
    }

    public ManagedMinion withRotation(float newYaw) {
        return new ManagedMinion(id, type, world, x, y, z, newYaw, pitch, owner, islandId, islandMembers, customName, chestWorld, chestX, chestY, chestZ, tier, lastProductionMillis, fuelId, fuelUntilMillis, fuelMultiplier, boosterId, boosterUntilMillis, boosterMultiplier, actionCount, storage, entityId);
    }

    public ManagedMinion withCustomName(String newCustomName) {
        return new ManagedMinion(id, type, world, x, y, z, yaw, pitch, owner, islandId, islandMembers, newCustomName, chestWorld, chestX, chestY, chestZ, tier, lastProductionMillis, fuelId, fuelUntilMillis, fuelMultiplier, boosterId, boosterUntilMillis, boosterMultiplier, actionCount, storage, entityId);
    }

    public ManagedMinion withChest(Location chestLocation) {
        String newChestWorld = chestLocation == null || chestLocation.getWorld() == null ? "" : chestLocation.getWorld().getName();
        int newChestX = chestLocation == null ? 0 : chestLocation.getBlockX();
        int newChestY = chestLocation == null ? 0 : chestLocation.getBlockY();
        int newChestZ = chestLocation == null ? 0 : chestLocation.getBlockZ();
        return new ManagedMinion(id, type, world, x, y, z, yaw, pitch, owner, islandId, islandMembers, customName, newChestWorld, newChestX, newChestY, newChestZ, tier, lastProductionMillis, fuelId, fuelUntilMillis, fuelMultiplier, boosterId, boosterUntilMillis, boosterMultiplier, actionCount, storage, entityId);
    }

    public Location chestLocation() {
        if (chestWorld == null || chestWorld.isBlank()) {
            return null;
        }
        World bukkitWorld = Bukkit.getWorld(chestWorld);
        return bukkitWorld == null ? null : new Location(bukkitWorld, chestX, chestY, chestZ);
    }

    public static ManagedMinion of(String id, MinionType type, Location location, String owner, String islandId, List<String> islandMembers) {
        return new ManagedMinion(
                id,
                type.id(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                owner,
                islandId,
                List.copyOf(islandMembers),
                "",
                "",
                0,
                0,
                0,
                1,
                System.currentTimeMillis(),
                "",
                0L,
                1.0,
                "",
                0L,
                1.0,
                0L,
                Map.of(),
                null
        );
    }
}
