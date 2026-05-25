package net.danh.islandportal;

import net.danh.islandportal.command.IslandPortalCommand;
import net.danh.islandportal.hook.BentoBoxListener;
import net.danh.islandportal.hook.SkylliaListener;
import net.danh.islandportal.hook.SuperiorSkyblockListener;
import net.danh.islandportal.platform.PlatformScheduler;
import net.danh.islandportal.portal.config.PortalConfig;
import net.danh.islandportal.portal.service.PortalService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class IslandPortal extends JavaPlugin {

    private PortalConfig portalConfig;
    private PortalService portalService;
    private PlatformScheduler platformScheduler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledResource("messages.yml");
        saveBundledResource("portals.yml");
        saveBundledResource("menus.yml");
        saveBundledResource("schematics/spawn_portal_island.schem");
        saveBundledResource("schematics/track_only_portal_island.schem");

        portalConfig = new PortalConfig(this);
        platformScheduler = new PlatformScheduler(this);
        portalService = new PortalService(this, portalConfig, platformScheduler);
        portalService.load();

        IslandPortalCommand command = new IslandPortalCommand(this, portalConfig, portalService);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, command::register);

        if (portalConfig.bentoBoxHook() && getServer().getPluginManager().isPluginEnabled("BentoBox")) {
            getServer().getPluginManager().registerEvents(new BentoBoxListener(portalService), this);
            debug("Hooked BentoBox island-create events.");
        }
        if (portalConfig.superiorSkyblockHook() && getServer().getPluginManager().isPluginEnabled("SuperiorSkyblock2")) {
            getServer().getPluginManager().registerEvents(new SuperiorSkyblockListener(portalService), this);
            debug("Hooked SuperiorSkyblock2 island-create events.");
        }
        if (portalConfig.skylliaHook() && getServer().getPluginManager().isPluginEnabled("Skyllia")) {
            getServer().getPluginManager().registerEvents(new SkylliaListener(portalService, platformScheduler), this);
            debug("Hooked Skyllia island lookup support.");
        }
        getServer().getPluginManager().registerEvents(portalService, this);
        debug("Platform scheduler mode: " + (platformScheduler.folia() ? "Folia" : "Paper"));
    }

    @Override
    public void onDisable() {
        if (portalService != null) {
            portalService.shutdown();
        }
    }

    public void reloadPortalConfig() {
        reloadConfig();
        portalConfig.reload();
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
