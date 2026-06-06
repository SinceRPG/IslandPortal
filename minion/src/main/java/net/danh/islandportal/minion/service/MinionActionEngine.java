package net.danh.islandportal.minion.service;

import net.danh.islandportal.minion.model.ManagedMinion;
import net.danh.islandportal.minion.model.MinionDrop;
import net.danh.islandportal.minion.model.MinionFuel;
import net.danh.islandportal.minion.model.MinionType;
import net.danh.islandportal.minion.storage.MinionRepository;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MinionActionEngine {

    private final MinionRepository repository;
    private final MinionSellService sellService;
    private final Runnable requestSave;

    public MinionActionEngine(MinionRepository repository, MinionSellService sellService, Runnable requestSave) {
        this.repository = repository;
        this.sellService = sellService;
        this.requestSave = requestSave;
    }

    public List<MinionDrop> drops(ManagedMinion minion, MinionType type, MinionFuel fuel) {
        return switch (type.action().type()) {
            case GENERATOR -> effectiveDrops(type, fuel);
            case MINER -> minerDrops(minion, type, fuel);
            case SPAWNER_MINER -> spawnerMinerDrops(minion, type, fuel);
            case COLLECTOR -> collectorDrops(minion, type, fuel);
            case SLAYER -> slayerDrops(minion, type, fuel);
            case LUMBERJACK -> lumberjackDrops(minion, type, fuel);
            case FARMER -> farmerDrops(minion, type, fuel);
            case FISHER -> fisherDrops(minion, type, fuel);
            case SPAWNER -> spawnerDrops(minion, type, fuel);
            case SELLER -> sellerDrops(minion, type);
            case FEEDER -> feederDrops(minion, type);
        };
    }

    private List<MinionDrop> effectiveDrops(MinionType type, MinionFuel fuel) {
        if (fuel == null) {
            return type.drops();
        }
        List<MinionDrop> drops = new java.util.ArrayList<>();
        for (MinionDrop drop : type.drops()) {
            drops.add(new MinionDrop(drop.material(), drop.itemKey(), Math.max(1, (int) Math.floor(drop.amount() * fuel.outputMultiplier()))));
        }
        drops.addAll(fuel.bonusDrops());
        return drops;
    }

    private List<MinionDrop> minerDrops(ManagedMinion minion, MinionType type, MinionFuel fuel) {
        Location location = minion.location();
        if (location == null || location.getWorld() == null) {
            return effectiveDrops(type, fuel);
        }
        List<MinionDrop> drops = new java.util.ArrayList<>();
        BlockFace face = yawFace(minion.yaw());
        Block cursor = location.getBlock().getRelative(face);
        for (int i = 0; i < type.action().blocksPerAction(); i++) {
            Block block = cursor.getRelative(face, i);
            Material material = block.getType();
            if (material.isAir() || (!type.action().allowedMaterials().isEmpty() && !type.action().allowedMaterials().contains(material))) {
                continue;
            }
            drops.add(new MinionDrop(smelted(material, minion.tier() >= type.action().smeltAtTier() && type.action().smeltAtTier() > 0), 1));
            block.setType(Material.AIR, false);
        }
        return drops.isEmpty() ? effectiveDrops(type, fuel) : applyFuel(drops, fuel);
    }

    private List<MinionDrop> spawnerMinerDrops(ManagedMinion minion, MinionType type, MinionFuel fuel) {
        Location location = minion.location();
        if (location == null) {
            return effectiveDrops(type, fuel);
        }
        Block target = location.getBlock().getRelative(yawFace(minion.yaw()));
        if (target.getType().isAir()) {
            target.setType(type.action().spawnBlock(), false);
        }
        return minerDrops(minion, type, fuel);
    }

    private List<MinionDrop> collectorDrops(ManagedMinion minion, MinionType type, MinionFuel fuel) {
        Location location = minion.location();
        if (location == null || location.getWorld() == null) {
            return effectiveDrops(type, fuel);
        }
        List<MinionDrop> drops = new java.util.ArrayList<>();
        for (Entity entity : location.getWorld().getNearbyEntities(location, type.action().range(), type.action().range(), type.action().range())) {
            if (!(entity instanceof Item item)) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            drops.add(new MinionDrop(stack.getType(), stack.getAmount()));
            item.remove();
        }
        return drops.isEmpty() ? effectiveDrops(type, fuel) : applyFuel(drops, fuel);
    }

    private List<MinionDrop> slayerDrops(ManagedMinion minion, MinionType type, MinionFuel fuel) {
        Location location = minion.location();
        if (location == null || location.getWorld() == null) {
            return effectiveDrops(type, fuel);
        }
        for (Entity entity : location.getWorld().getNearbyEntities(location, type.action().range(), type.action().range(), type.action().range())) {
            if (entity instanceof Monster monster) {
                monster.remove();
                return effectiveDrops(type, fuel);
            }
        }
        return effectiveDrops(type, fuel);
    }

    private List<MinionDrop> lumberjackDrops(ManagedMinion minion, MinionType type, MinionFuel fuel) {
        return blockRangeDrops(minion, type, fuel, material -> material.name().endsWith("_LOG") || material.name().endsWith("_WOOD") || material.name().endsWith("_LEAVES"));
    }

    private List<MinionDrop> farmerDrops(ManagedMinion minion, MinionType type, MinionFuel fuel) {
        Location location = minion.location();
        if (location == null || location.getWorld() == null) {
            return effectiveDrops(type, fuel);
        }
        List<MinionDrop> drops = new java.util.ArrayList<>();
        int range = type.action().range();
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                Block block = location.getBlock().getRelative(x, 0, z);
                if (!(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
                    continue;
                }
                drops.add(new MinionDrop(block.getType(), 1));
                ageable.setAge(0);
                block.setBlockData(ageable, false);
                return applyFuel(drops, fuel);
            }
        }
        return effectiveDrops(type, fuel);
    }

    private List<MinionDrop> fisherDrops(ManagedMinion minion, MinionType type, MinionFuel fuel) {
        Location location = minion.location();
        if (location == null) {
            return effectiveDrops(type, fuel);
        }
        Block target = location.getBlock().getRelative(yawFace(minion.yaw()));
        if (target.getType() != Material.WATER) {
            return effectiveDrops(type, fuel);
        }
        return applyFuel(List.of(new MinionDrop(Material.COD, 1)), fuel);
    }

    private List<MinionDrop> spawnerDrops(ManagedMinion minion, MinionType type, MinionFuel fuel) {
        Location location = minion.location();
        if (location != null && location.getWorld() != null) {
            location.getWorld().spawnEntity(location.clone().add(0.0, 1.0, 0.0), type.action().spawnEntity());
        }
        return effectiveDrops(type, fuel);
    }

    private List<MinionDrop> sellerDrops(ManagedMinion minion, MinionType type) {
        Location chest = minion.chestLocation();
        if (chest == null || !(chest.getBlock().getState() instanceof Container container) || !type.shop().enabled()) {
            return List.of();
        }
        Map<String, Integer> sold = new LinkedHashMap<>();
        for (ItemStack item : container.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (!type.action().sellMaterials().isEmpty() && !type.action().sellMaterials().contains(item.getType())) {
                continue;
            }
            sold.merge(item.getType().name(), item.getAmount(), Integer::sum);
            item.setAmount(0);
        }
        sellService.sell(null, type, minion, sold);
        return List.of();
    }

    private List<MinionDrop> feederDrops(ManagedMinion minion, MinionType type) {
        Location location = minion.location();
        if (location == null) {
            return List.of();
        }
        for (ManagedMinion target : repository.all()) {
            Location targetLocation = target.location();
            if (targetLocation == null || !targetLocation.getWorld().equals(location.getWorld()) || targetLocation.distanceSquared(location) > type.action().range() * type.action().range()) {
                continue;
            }
            if (target.fuelUntilMillis() < System.currentTimeMillis()) {
                repository.add(target.withFuel(minion.fuelId(), System.currentTimeMillis() + Math.max(1000L, minion.fuelUntilMillis() - System.currentTimeMillis()), minion.fuelMultiplier()));
                requestSave.run();
            }
        }
        return List.of();
    }

    private List<MinionDrop> blockRangeDrops(ManagedMinion minion, MinionType type, MinionFuel fuel, java.util.function.Predicate<Material> predicate) {
        Location location = minion.location();
        if (location == null || location.getWorld() == null) {
            return effectiveDrops(type, fuel);
        }
        int range = type.action().range();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    Block block = location.getBlock().getRelative(x, y, z);
                    if (!predicate.test(block.getType())) {
                        continue;
                    }
                    Material material = block.getType();
                    block.setType(Material.AIR, false);
                    return applyFuel(List.of(new MinionDrop(material, 1)), fuel);
                }
            }
        }
        return effectiveDrops(type, fuel);
    }

    private List<MinionDrop> applyFuel(List<MinionDrop> drops, MinionFuel fuel) {
        if (fuel == null || drops.isEmpty()) {
            return drops;
        }
        List<MinionDrop> boosted = new java.util.ArrayList<>();
        for (MinionDrop drop : drops) {
            boosted.add(new MinionDrop(drop.material(), drop.itemKey(), Math.max(1, (int) Math.floor(drop.amount() * fuel.outputMultiplier()))));
        }
        boosted.addAll(fuel.bonusDrops());
        return boosted;
    }

    private Material smelted(Material material, boolean smelt) {
        if (!smelt) {
            return material;
        }
        return switch (material) {
            case IRON_ORE, DEEPSLATE_IRON_ORE, RAW_IRON -> Material.IRON_INGOT;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, RAW_GOLD -> Material.GOLD_INGOT;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE, RAW_COPPER -> Material.COPPER_INGOT;
            default -> material;
        };
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
}
