package net.danh.islandportal.npc.model;

import java.util.List;

public record NpcUnlockSettings(boolean defaultUnlocked, List<String> permissions, int minIslandMembers) {

    public boolean hasRequirements() {
        return !defaultUnlocked || !permissions.isEmpty() || minIslandMembers > 0;
    }
}
