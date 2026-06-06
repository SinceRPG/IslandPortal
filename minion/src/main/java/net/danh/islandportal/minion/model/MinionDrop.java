package net.danh.islandportal.minion.model;

import org.bukkit.Material;

public record MinionDrop(Material material, String itemKey, int amount) {

    public MinionDrop(Material material, int amount) {
        this(material, material.name(), amount);
    }
}
