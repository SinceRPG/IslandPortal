package net.danh.islandportal.minion.model;

import java.util.List;

public record MinionAnimationSettings(
        int resetTicks,
        int targetVisibleTicks,
        double targetYOffset,
        boolean blockBreakEnabled,
        List<Float> blockBreakProgress,
        boolean cropGrowthEnabled,
        int cropGrowthStageTicks
) {
}
