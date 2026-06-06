package net.danh.islandportal.minion.model;

import org.bukkit.Material;

import java.util.List;

public record MinionFuel(String id, Material material, long durationMillis, double speedMultiplier, double outputMultiplier, List<MinionDrop> bonusDrops) {
}
