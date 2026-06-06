package net.danh.islandportal.minion.event;

import net.danh.islandportal.minion.model.ManagedMinion;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public final class MinionChestLinkEvent extends MinionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Block block;
    private boolean cancelled;
    private String failMessageKey;

    public MinionChestLinkEvent(Player player, ManagedMinion minion, Block block) {
        super(minion);
        this.player = player;
        this.block = block;
    }

    public Player player() {
        return player;
    }

    public Block block() {
        return block;
    }

    public String failMessageKey() {
        return failMessageKey;
    }

    public void setFailMessageKey(String failMessageKey) {
        this.failMessageKey = failMessageKey;
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
