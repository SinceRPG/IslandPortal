package net.danh.islandportal.minion.integration;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class PacketEventsCosmeticAnimationBridge implements CosmeticAnimationBridge {

    private final int viewRange;

    public PacketEventsCosmeticAnimationBridge(int viewRange) {
        this.viewRange = Math.max(1, viewRange);
    }

    @Override
    public void play(ArmorStand stand, String stageName) {
        WrapperPlayServerEntityAnimation packet = new WrapperPlayServerEntityAnimation(stand.getEntityId(), animation(stageName));
        for (Player viewer : stand.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(stand.getLocation()) <= viewRange * viewRange) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
            }
        }
    }

    private WrapperPlayServerEntityAnimation.EntityAnimationType animation(String stageName) {
        String normalized = stageName == null ? "" : stageName.toUpperCase(Locale.ROOT).replace("-", "_");
        if (normalized.contains("OFF_HAND") || normalized.contains("OFFHAND")) {
            return WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_OFF_HAND;
        }
        if (normalized.contains("MAGIC")) {
            return WrapperPlayServerEntityAnimation.EntityAnimationType.MAGIC_CRITICAL_HIT;
        }
        if (normalized.contains("CRITICAL") || normalized.contains("CRIT")) {
            return WrapperPlayServerEntityAnimation.EntityAnimationType.CRITICAL_HIT;
        }
        if (normalized.contains("HURT")) {
            return WrapperPlayServerEntityAnimation.EntityAnimationType.HURT;
        }
        return WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM;
    }
}
