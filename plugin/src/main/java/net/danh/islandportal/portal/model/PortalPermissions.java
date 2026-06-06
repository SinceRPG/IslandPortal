package net.danh.islandportal.portal.model;

public record PortalPermissions(
        String place,
        String use,
        String pickup,
        String configure
) {

    public boolean hasPlace() {
        return place != null && !place.isBlank();
    }

    public boolean hasUse() {
        return use != null && !use.isBlank();
    }

    public boolean hasPickup() {
        return pickup != null && !pickup.isBlank();
    }

    public boolean hasConfigure() {
        return configure != null && !configure.isBlank();
    }
}
