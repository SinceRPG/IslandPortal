package net.danh.islandportal.minion.model;

public record MinionDisplayEntitySettings(
        boolean customNameVisible,
        boolean arms,
        boolean basePlate,
        boolean invulnerable,
        boolean persistent,
        boolean removeWhenFarAway,
        double maxHealth
) {
}
