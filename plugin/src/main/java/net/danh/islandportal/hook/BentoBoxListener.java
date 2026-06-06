package net.danh.islandportal.hook;

import net.danh.islandportal.npc.service.IslandNpcService;
import net.danh.islandportal.minion.service.MinionService;
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
    private final IslandNpcService npcService;
    private final MinionService minionService;

    public BentoBoxListener(PortalService portalService, IslandNpcService npcService, MinionService minionService) {
        this.portalService = portalService;
        this.npcService = npcService;
        this.minionService = minionService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandCreated(IslandCreatedEvent event) {
        Island island = event.getIsland();
        if (island == null) {
            return;
        }
        Location location = islandLocation(island, event.getLocation());
        String islandId = "bentobox:" + island.getUniqueId();
        List<String> islandMembers = members(island);
        portalService.handleIslandCreated(islandId, location, uuid(island.getOwner()), islandMembers);
        npcService.handleIslandCreated(islandId, location, uuid(island.getOwner()), islandMembers);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNewIsland(IslandNewIslandEvent event) {
        Island island = event.getIsland();
        if (island == null) {
            return;
        }
        Location location = islandLocation(island, event.getLocation());
        String islandId = "bentobox:" + island.getUniqueId();
        List<String> islandMembers = members(island);
        portalService.handleIslandCreated(islandId, location, uuid(island.getOwner()), islandMembers);
        npcService.handleIslandCreated(islandId, location, uuid(island.getOwner()), islandMembers);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDelete(IslandDeleteEvent event) {
        Island island = event.getIsland();
        if (island != null) {
            Location location = islandLocation(island, event.getLocation());
            String islandId = "bentobox:" + island.getUniqueId();
            portalService.handleIslandRemoved(islandId, location, uuid(event.getPlayerUUID()), uuid(island.getOwner()), members(island));
            npcService.handleIslandRemoved(islandId, location);
            minionService.handleIslandRemoved(islandId, location);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandReset(IslandResetEvent event) {
        Island island = event.getOldIsland();
        Location location = islandLocation(island, event.getLocation());
        String islandId = "bentobox:" + island.getUniqueId();
        portalService.handleIslandRemoved(islandId, location, uuid(event.getPlayerUUID()), uuid(island.getOwner()), members(island));
        npcService.handleIslandRemoved(islandId, location);
        minionService.handleIslandRemoved(islandId, location);
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
