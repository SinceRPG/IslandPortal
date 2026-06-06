package net.danh.islandportal.minion.event;

import net.danh.islandportal.minion.model.ManagedMinion;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public final class PreMinionPickupEvent extends MinionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private boolean cancelled;

    public PreMinionPickupEvent(Player player, ManagedMinion minion) {
        super(minion);
        this.player = player;
    }

    public Player player() {
        return player;
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
