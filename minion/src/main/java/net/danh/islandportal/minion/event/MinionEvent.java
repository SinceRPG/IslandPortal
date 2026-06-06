package net.danh.islandportal.minion.event;

import net.danh.islandportal.minion.model.ManagedMinion;
import org.bukkit.event.Event;

public abstract class MinionEvent extends Event {

    private final ManagedMinion minion;

    protected MinionEvent(ManagedMinion minion) {
        this.minion = minion;
    }

    public ManagedMinion minion() {
        return minion;
    }
}
