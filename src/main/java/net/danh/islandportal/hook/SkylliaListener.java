package net.danh.islandportal.hook;

import fr.euphyllia.skyllia.api.SkylliaAPI;
import fr.euphyllia.skyllia.api.event.SkyblockCreateEvent;
import fr.euphyllia.skyllia.api.event.SkyblockDeleteEvent;
import fr.euphyllia.skyllia.api.skyblock.Island;
import fr.euphyllia.skyllia.api.skyblock.Players;
import fr.euphyllia.skyllia.api.skyblock.model.Position;
import net.danh.islandportal.platform.PlatformScheduler;
import net.danh.islandportal.portal.service.PortalService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.UUID;

public final class SkylliaListener implements Listener {

    private final PortalService portalService;
    private final PlatformScheduler scheduler;

    public SkylliaListener(PortalService portalService, PlatformScheduler scheduler) {
        this.portalService = portalService;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Island island = SkylliaAPI.getIslandByPlayerId(player.getUniqueId());
        if (island == null) {
            return;
        }
        scheduler.runFor(player, () -> handleIslandCreated(island, player.getLocation(), player.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkyblockCreate(SkyblockCreateEvent event) {
        Island island = event.getIsland();
        Player owner = Bukkit.getPlayer(event.getOwnerId());
        if (owner != null) {
            scheduler.runFor(owner, () -> handleIslandCreated(island, owner.getLocation(), event.getOwnerId()));
            return;
        }
        Location location = islandLocation(island);
        if (location != null) {
            scheduler.runAt(location, () -> handleIslandCreated(island, location, event.getOwnerId()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkyblockDelete(SkyblockDeleteEvent event) {
        Island island = event.getIsland();
        UUID ownerId = island.getOwner() == null ? null : island.getOwner().getMojangId();
        Location location = islandLocation(island);
        portalService.handleIslandRemoved("skyllia:" + island.getId(), location, ownerId == null ? null : ownerId.toString(), ownerId == null ? null : ownerId.toString(), members(island));
    }

    private void handleIslandCreated(Island island, Location location, UUID ownerId) {
        portalService.handleIslandCreated("skyllia:" + island.getId(), location, ownerId.toString(), members(island));
    }

    private Location islandLocation(Island island) {
        World world = Bukkit.getWorlds().stream()
                .filter(SkylliaAPI::isWorldSkyblock)
                .findFirst()
                .orElse(null);
        if (world == null) {
            return null;
        }
        Position position = island.getPosition();
        return new Location(world, position.x(), world.getSpawnLocation().getY(), position.z());
    }

    private List<String> members(Island island) {
        return island.getMembers().stream()
                .map(Players::getMojangId)
                .map(UUID::toString)
                .toList();
    }
}
