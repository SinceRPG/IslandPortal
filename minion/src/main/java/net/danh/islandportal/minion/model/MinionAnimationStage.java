package net.danh.islandportal.minion.model;

public record MinionAnimationStage(
        String name,
        int delayTicks,
        int durationTicks,
        double rightArmX,
        double rightArmY,
        double rightArmZ,
        double leftArmX,
        double leftArmY,
        double leftArmZ
) {
}
