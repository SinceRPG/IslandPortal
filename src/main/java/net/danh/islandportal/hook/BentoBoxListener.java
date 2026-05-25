package net.danh.islandportal.hook;

import net.danh.islandportal.portal.service.PortalService;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import world.bentobox.bentobox.api.events.island.IslandCreatedEvent;
import world.bentobox.bentobox.api.events.island.IslandDeleteEvent;
import world.bentobox.bentobox.api.events.island.IslandResetEvent;
import world.bentobox.bentobox.api.events.island.IslandNewIslandEvent;
import world.bentobox.bentobox.database.objects.Island;

import java.util.List;
import java.util.UUID;

public final class BentoBoxListener implements Listener {

    private final PortalService portalService;

    public BentoBoxListener(PortalService portalService) {
        this.portalService = portalService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandCreated(IslandCreatedEvent event) {
        Island island = event.getIsland();
        if (island == null) {
            return;
        }
        portalService.handleIslandCreated("bentobox:" + island.getUniqueId(), islandLocation(island, event.getLocation()), uuid(island.getOwner()), members(island));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNewIsland(IslandNewIslandEvent event) {
        Island island = event.getIsland();
        if (island == null) {
            return;
        }
        portalService.handleIslandCreated("bentobox:" + island.getUniqueId(), islandLocation(island, event.getLocation()), uuid(island.getOwner()), members(island));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDelete(IslandDeleteEvent event) {
        Island island = event.getIsland();
        if (island != null) {
            portalService.handleIslandRemoved("bentobox:" + island.getUniqueId(), islandLocation(island, event.getLocation()), uuid(event.getPlayerUUID()), uuid(island.getOwner()), members(island));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandReset(IslandResetEvent event) {
        Island island = event.getOldIsland();
        portalService.handleIslandRemoved("bentobox:" + island.getUniqueId(), islandLocation(island, event.getLocation()), uuid(event.getPlayerUUID()), uuid(island.getOwner()), members(island));
    }

    private Location islandLocation(Island island, Location eventLocation) {
        try {
            return island.getHome("");
        } catch (RuntimeException exception) {
            return eventLocation == null ? island.getCenter() : eventLocation;
        }
    }

    private String uuid(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    private List<String> members(Island island) {
        return island.getMemberSet().stream().map(UUID::toString).toList();
    }
}
