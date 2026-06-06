package net.danh.islandportal.minion.model;

import org.bukkit.Material;

import java.util.List;

public record MinionMenuItem(int slot, Material material, String name, List<String> lore, String action, Integer customModelData) {
}
