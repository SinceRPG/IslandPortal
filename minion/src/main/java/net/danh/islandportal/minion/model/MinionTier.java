package net.danh.islandportal.minion.model;

import java.util.List;

public record MinionTier(int level, long intervalMillis, int storageLimit, int actionSize, List<MinionRequirement> upgradeCost, double upgradeMoney) {
}
