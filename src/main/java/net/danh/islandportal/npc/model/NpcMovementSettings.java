package net.danh.islandportal.npc.model;

public record NpcMovementSettings(boolean enabled, double radius, int intervalTicks, double stepDistance, int targetAttempts) {
}
