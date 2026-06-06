package net.danh.islandportal.minion.model;

import java.util.List;

public record MinionFuelItemSettings(
        String displayName,
        List<String> lore
) {
}
