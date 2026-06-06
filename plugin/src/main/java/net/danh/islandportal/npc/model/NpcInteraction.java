package net.danh.islandportal.npc.model;

import java.util.List;

public record NpcInteraction(List<String> messages, List<String> playerCommands, List<String> consoleCommands) {
}
