package net.danh.islandportal.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PlatformScheduler {

    private final Plugin plugin;
    private final boolean folia;

    public PlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
    }

    public boolean folia() {
        return folia;
    }

    public void runGlobal(Runnable runnable) {
        if (folia) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    public PlatformTask runGlobalLater(Runnable runnable, long delayTicks) {
        if (folia) {
            ScheduledTask task = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> runnable.run(), Math.max(1L, delayTicks));
            return task::cancel;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, runnable, Math.max(1L, delayTicks));
        return task::cancel;
    }

    public PlatformTask runGlobalTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (folia) {
            ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> runnable.run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
            return task::cancel;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return task::cancel;
    }

    public void runAsync(Runnable runnable) {
        if (folia) {
            Bukkit.getAsyncScheduler().runNow(plugin, ignored -> runnable.run());
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    public void runAt(Location location, Runnable runnable) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        if (folia) {
            Bukkit.getRegionScheduler().execute(plugin, world, location.getBlockX() >> 4, location.getBlockZ() >> 4, runnable);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    public void runAtLoaded(Location location, Runnable runnable) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (world.isChunkLoaded(chunkX, chunkZ)) {
            runAt(location, runnable);
        }
    }

    public void runFor(Entity entity, Runnable runnable) {
        if (folia) {
            // Folia entity state, inventory, and GUI operations must run on the entity scheduler.
            entity.getScheduler().execute(plugin, runnable, null, 1L);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    public void cancelPluginTasks() {
        if (folia) {
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
            return;
        }
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
