package net.danh.islandportal.portal.access;

import net.danh.islandportal.portal.config.PortalConfig;
import net.danh.islandportal.portal.model.AccessPolicy;
import net.danh.islandportal.portal.model.ManagedPortal;
import net.danh.islandportal.portal.model.PortalType;
import org.bukkit.entity.Player;

public final class PortalAccessController {

    private final PortalConfig config;

    public PortalAccessController(PortalConfig config) {
        this.config = config;
    }

    public boolean canPickup(Player player, ManagedPortal portal) {
        PortalType type = config.type(portal.type());
        if (type == null) {
            return false;
        }
        if (type.permissions().hasPickup() && !player.hasPermission(type.permissions().pickup())) {
            return false;
        }
        return hasPolicyAccess(player, portal, portal.pickupPolicy());
    }

    public boolean canUse(Player player, ManagedPortal portal, PortalType type) {
        if (type.permissions().hasUse() && !player.hasPermission(type.permissions().use())) {
            return false;
        }
        return hasPolicyAccess(player, portal, portal.usePolicy());
    }

    public boolean canConfigure(Player player, ManagedPortal portal) {
        PortalType type = config.type(portal.type());
        if (type == null) {
            return false;
        }
        if (type.permissions().hasConfigure() && !player.hasPermission(type.permissions().configure())) {
            return false;
        }
        return hasPolicyAccess(player, portal, portal.configurePolicy());
    }

    private boolean hasPolicyAccess(Player player, ManagedPortal portal, AccessPolicy policy) {
        String uuid = player.getUniqueId().toString();
        return switch (policy) {
            case ANYONE -> true;
            case OWNER -> uuid.equals(portal.owner());
            case ISLAND_OWNER -> uuid.equals(portal.owner());
            case ISLAND_MEMBERS -> uuid.equals(portal.owner()) || portal.islandMembers().contains(uuid);
        };
    }
}
