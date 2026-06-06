package net.danh.islandportal.hook;

import fr.euphyllia.skyllia.api.SkylliaAPI;
import fr.euphyllia.skyllia.api.event.SkyblockCreateEvent;
import fr.euphyllia.skyllia.api.event.SkyblockDeleteEvent;
import fr.euphyllia.skyllia.api.skyblock.Island;
import fr.euphyllia.skyllia.api.skyblock.Players;
import fr.euphyllia.skyllia.api.skyblock.model.Position;
import net.danh.islandportal.minion.service.MinionService;
import net.danh.islandportal.npc.service.IslandNpcService;
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
    private final IslandNpcService npcService;
    private final MinionService minionService;
    private final PlatformScheduler scheduler;

    public SkylliaListener(PortalService portalService, IslandNpcService npcService, MinionService minionService, PlatformScheduler scheduler) {
        this.portalService = portalService;
        this.npcService = npcService;
        this.minionService = minionService;
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
        Location location = islandLocation(island);
        Player owner = Bukkit.getPlayer(event.getOwnerId());
        if (location != null) {
            scheduler.runAt(location, () -> handleIslandCreated(island, location, event.getOwnerId()));
            return;
        }
        if (owner != null) {
            scheduler.runFor(owner, () -> handleIslandCreated(island, owner.getWorld().getSpawnLocation(), event.getOwnerId()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkyblockDelete(SkyblockDeleteEvent event) {
        Island island = event.getIsland();
        UUID ownerId = island.getOwner() == null ? null : island.getOwner().getMojangId();
        Location location = islandLocation(island);
        String islandId = "skyllia:" + island.getId();
        portalService.handleIslandRemoved(islandId, location, ownerId == null ? null : ownerId.toString(), ownerId == null ? null : ownerId.toString(), members(island));
        npcService.handleIslandRemoved(islandId, location);
        minionService.handleIslandRemoved(islandId, location);
    }

    private void handleIslandCreated(Island island, Location location, UUID ownerId) {
        String islandId = "skyllia:" + island.getId();
        List<String> islandMembers = members(island);
        portalService.handleIslandCreated(islandId, location, ownerId.toString(), islandMembers);
        npcService.handleIslandCreated(islandId, location, ownerId.toString(), islandMembers);
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
