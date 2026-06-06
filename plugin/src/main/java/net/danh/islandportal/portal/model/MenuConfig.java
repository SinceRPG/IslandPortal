package net.danh.islandportal.portal.model;

import java.util.List;

public record MenuConfig(
        String title,
        int size,
        List<MenuItemConfig> items
) {
}
