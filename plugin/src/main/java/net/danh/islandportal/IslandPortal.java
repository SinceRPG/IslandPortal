package net.danh.islandportal;

import net.danh.islandportal.command.IslandPortalCommand;
import net.danh.islandportal.hook.BentoBoxListener;
import net.danh.islandportal.hook.SkylliaListener;
import net.danh.islandportal.hook.SuperiorSkyblockListener;
import net.danh.islandportal.minion.config.MinionConfig;
import net.danh.islandportal.minion.service.MinionService;
import net.danh.islandportal.npc.config.IslandNpcConfig;
import net.danh.islandportal.npc.service.IslandNpcService;
import net.danh.islandportal.platform.PlatformScheduler;
import net.danh.islandportal.portal.config.PortalConfig;
import net.danh.islandportal.portal.service.PortalService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class IslandPortal extends JavaPlugin {

    private PortalConfig portalConfig;
    private IslandNpcConfig npcConfig;
    private MinionConfig minionConfig;
    private PortalService portalService;
    private IslandNpcService npcService;
    private MinionService minionService;
    private PlatformScheduler platformScheduler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledResource("messages.yml");
        saveBundledResource("portals.yml");
        saveBundledResource("npcs.yml");
        saveBundledResource("minions/fuels.yml");
        saveBundledResource("minions/settings.yml");
        saveBundledResource("minions/gui.yml");
        saveBundledResource("minions/types/collector.yml");
        saveBundledResource("minions/types/farmer.yml");
        saveBundledResource("minions/types/feeder.yml");
        saveBundledResource("minions/types/fisher.yml");
        saveBundledResource("minions/types/generator.yml");
        saveBundledResource("minions/types/lumberjack.yml");
        saveBundledResource("minions/types/miner.yml");
        saveBundledResource("minions/types/seller.yml");
        saveBundledResource("minions/types/slayer.yml");
        saveBundledResource("minions/types/spawner.yml");
        saveBundledResource("minions/types/spawner-miner.yml");
        saveBundledResource("menus.yml");
        saveBundledResource("schematics/spawn_portal_island.schem");
        saveBundledResource("schematics/track_only_portal_island.schem");

        portalConfig = new PortalConfig(this);
        npcConfig = new IslandNpcConfig(this);
        minionConfig = new MinionConfig(this);
        platformScheduler = new PlatformScheduler(this);
        portalService = new PortalService(this, portalConfig, platformScheduler);
        npcService = new IslandNpcService(this, npcConfig, platformScheduler);
        minionService = new MinionService(this, minionConfig, platformScheduler);
        portalService.load();
        npcService.load();
        minionService.load();

        IslandPortalCommand command = new IslandPortalCommand(this, portalConfig, portalService, npcConfig, npcService, minionConfig, minionService);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, command::register);

        if (portalConfig.bentoBoxHook() && getServer().getPluginManager().isPluginEnabled("BentoBox")) {
            getServer().getPluginManager().registerEvents(new BentoBoxListener(portalService, npcService, minionService), this);
            debug("Hooked BentoBox island-create events.");
        }
        if (portalConfig.superiorSkyblockHook() && getServer().getPluginManager().isPluginEnabled("SuperiorSkyblock2")) {
            getServer().getPluginManager().registerEvents(new SuperiorSkyblockListener(portalService, npcService, minionService), this);
            debug("Hooked SuperiorSkyblock2 island-create events.");
        }
        if (portalConfig.skylliaHook() && getServer().getPluginManager().isPluginEnabled("Skyllia")) {
            getServer().getPluginManager().registerEvents(new SkylliaListener(portalService, npcService, minionService, platformScheduler), this);
            debug("Hooked Skyllia island lookup support.");
        }
        getServer().getPluginManager().registerEvents(portalService, this);
        getServer().getPluginManager().registerEvents(npcService, this);
        getServer().getPluginManager().registerEvents(minionService, this);
        debug("Platform scheduler mode: " + (platformScheduler.folia() ? "Folia" : "Paper"));
    }

    @Override
    public void onDisable() {
        if (npcService != null) {
            npcService.shutdown();
        }
        if (minionService != null) {
            minionService.shutdown();
        }
        if (portalService != null) {
            portalService.shutdown();
        }
    }

    public void reloadPortalConfig() {
        reloadConfig();
        portalConfig.reload();
        npcService.reload();
        minionService.reload();
    }

    private void debug(String message) {
        if (portalConfig.debug()) {
            getLogger().info("[Debug] " + message);
        }
    }

    private void saveBundledResource(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }
}
