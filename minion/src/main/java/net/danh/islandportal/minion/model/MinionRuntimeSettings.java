package net.danh.islandportal.minion.model;

public record MinionRuntimeSettings(
        String idPrefix,
        int perIslandGlobalLimit,
        int islandCleanupRadius,
        int linkChestRange,
        boolean autoSellEnabled,
        boolean workWhenOwnerOffline,
        boolean outputToLinkedChest,
        boolean hopperOutput,
        MinionActionAreaSettings actionArea,
        MinionAnimationSettings animations,
        MinionFuelItemSettings fuelItems,
        MinionPlacementSettings placement,
        MinionPermissionSettings permissions,
        MinionDefaultEquipmentSettings defaultEquipment,
        MinionDisplayEntitySettings displayEntity,
        MinionIntegrationSettings integrations
) {
    public int animationResetTicks() {
        return animations.resetTicks();
    }
}
