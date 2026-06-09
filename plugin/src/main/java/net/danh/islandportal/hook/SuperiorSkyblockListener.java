package net.danh.islandportal.hook;

import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandWorldResetEvent;
import com.bgsoftware.superiorskyblock.api.events.PostIslandCreateEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.world.Dimension;
import net.danh.islandportal.npc.service.IslandNpcService;
import net.danh.islandportal.portal.service.PortalService;
import net.danh.islandportal.platform.PlatformScheduler;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

public final class SuperiorSkyblockListener implements Listener {

    private final PortalService portalService;
    private final IslandNpcService npcService;
    private final PlatformScheduler scheduler;

    public SuperiorSkyblockListener(PortalService portalService, IslandNpcService npcService, PlatformScheduler scheduler) {
        this.portalService = portalService;
        this.npcService = npcService;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandCreated(PostIslandCreateEvent event) {
        Island island = event.getIsland();
        Location location = location(island);
        String islandId = "superior:" + island.getUniqueId();
        List<String> islandMembers = members(island);
        Runnable task = () -> {
            portalService.handleIslandCreated(islandId, location, owner(island), islandMembers);
            npcService.handleIslandCreated(islandId, location, owner(island), islandMembers);
        };
        if (location != null) {
            scheduler.runAt(location, task);
        } else {
            scheduler.runGlobal(task);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDisband(IslandDisbandEvent event) {
        Island island = event.getIsland();
        Location location = location(island);
        String islandId = "superior:" + island.getUniqueId();
        Runnable task = () -> {
            portalService.handleIslandRemoved(islandId, location, event.getPlayer().getUniqueId().toString(), owner(island), members(island));
            npcService.handleIslandRemoved(islandId, location);
        };
        if (location != null) {
            scheduler.runAt(location, task);
        } else {
            scheduler.runGlobal(task);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandWorldReset(IslandWorldResetEvent event) {
        Island island = event.getIsland();
        String actor = event.getPlayer() == null ? null : event.getPlayer().getUniqueId().toString();
        Location location = location(island);
        String islandId = "superior:" + island.getUniqueId();
        Runnable task = () -> {
            portalService.handleIslandRemoved(islandId, location, actor, owner(island), members(island));
            npcService.handleIslandRemoved(islandId, location);
        };
        if (location != null) {
            scheduler.runAt(location, task);
        } else {
            scheduler.runGlobal(task);
        }
    }

    private Location location(Island island) {
        Location location = island.getIslandHome(Dimension.getByName(World.Environment.NORMAL.name()));
        return location == null ? island.getCenter(Dimension.getByName(World.Environment.NORMAL.name())) : location;
    }

    private String owner(Island island) {
        return island.getOwner() == null ? null : island.getOwner().getUniqueId().toString();
    }

    private List<String> members(Island island) {
        return island.getIslandMembers(true).stream().map(player -> player.getUniqueId().toString()).toList();
    }
}
