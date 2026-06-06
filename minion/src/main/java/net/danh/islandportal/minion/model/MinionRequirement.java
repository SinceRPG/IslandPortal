package net.danh.islandportal.minion.model;

import org.bukkit.Material;

public record MinionRequirement(Material material, String itemKey, int amount) {

    public MinionRequirement(Material material, int amount) {
        this(material, material.name(), amount);
    }
}
