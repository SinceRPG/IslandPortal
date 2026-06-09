package net.danh.islandportal;

import net.danh.islandportal.command.IslandPortalCommand;
import net.danh.islandportal.hook.BentoBoxListener;
import net.danh.islandportal.hook.SkylliaListener;
import net.danh.islandportal.hook.SuperiorSkyblockListener;
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
    private PortalService portalService;
    private IslandNpcService npcService;
    private PlatformScheduler platformScheduler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledResource("messages.yml");
        saveBundledResource("portals.yml");
        saveBundledResource("npcs.yml");
        saveBundledResource("menus.yml");
        saveBundledResource("schematics/spawn_portal_island.schem");
        saveBundledResource("schematics/track_only_portal_island.schem");

        portalConfig = new PortalConfig(this);
        npcConfig = new IslandNpcConfig(this);
        platformScheduler = new PlatformScheduler(this);
        portalService = new PortalService(this, portalConfig, platformScheduler);
        npcService = new IslandNpcService(this, npcConfig, platformScheduler);
        portalService.load();
        npcService.load();

        IslandPortalCommand command = new IslandPortalCommand(this, portalConfig, portalService, npcConfig, npcService);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, command::register);

        if (portalConfig.bentoBoxHook() && getServer().getPluginManager().getPlugin("BentoBox") != null) {
            getServer().getPluginManager().registerEvents(new BentoBoxListener(portalService, npcService, platformScheduler), this);
            debug("Hooked BentoBox island-create events.");
        }
        if (portalConfig.superiorSkyblockHook() && getServer().getPluginManager().getPlugin("SuperiorSkyblock2") != null) {
            getServer().getPluginManager().registerEvents(new SuperiorSkyblockListener(portalService, npcService, platformScheduler), this);
            debug("Hooked SuperiorSkyblock2 island-create events.");
        }
        if (portalConfig.skylliaHook() && getServer().getPluginManager().getPlugin("Skyllia") != null) {
            getServer().getPluginManager().registerEvents(new SkylliaListener(portalService, npcService, platformScheduler), this);
            debug("Hooked Skyllia island lookup support.");
        }
        getServer().getPluginManager().registerEvents(portalService, this);
        getServer().getPluginManager().registerEvents(npcService, this);
        debug("Platform scheduler mode: " + (platformScheduler.folia() ? "Folia" : "Paper"));
    }

    @Override
    public void onDisable() {
        if (npcService != null) {
            npcService.shutdown();
        }
        if (portalService != null) {
            portalService.shutdown();
        }
    }

    public void reloadPortalConfig() {
        reloadConfig();
        portalConfig.reload();
        npcService.reload();
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
