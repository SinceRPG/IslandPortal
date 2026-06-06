package net.danh.islandportal.minion.integration;

import org.bukkit.entity.ArmorStand;

public final class NoopCosmeticAnimationBridge implements CosmeticAnimationBridge {

    @Override
    public void play(ArmorStand stand, String stageName) {
    }
}
