package net.danh.islandportal.minion.model;

public record MinionIntegrationSettings(
        boolean vault,
        boolean shopGuiPlus,
        boolean essentials,
        boolean cmi,
        boolean packetEvents,
        int packetEventsViewRange,
        boolean headDatabase,
        boolean itemsAdder,
        boolean oraxen,
        boolean nexo,
        boolean mmoItems,
        boolean mythicMobs
) {
}
