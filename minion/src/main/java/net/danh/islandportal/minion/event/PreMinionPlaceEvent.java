package net.danh.islandportal.minion.event;

import net.danh.islandportal.minion.model.MinionType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class PreMinionPlaceEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final MinionType type;
    private final Location location;
    private boolean cancelled;
    private boolean overridePlayerLimit;

    public PreMinionPlaceEvent(Player player, MinionType type, Location location) {
        this.player = player;
        this.type = type;
        this.location = location;
    }

    public Player player() {
        return player;
    }

    public MinionType type() {
        return type;
    }

    public Location location() {
        return location;
    }

    public boolean overridePlayerLimit() {
        return overridePlayerLimit;
    }

    public void setOverridePlayerLimit(boolean overridePlayerLimit) {
        this.overridePlayerLimit = overridePlayerLimit;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
