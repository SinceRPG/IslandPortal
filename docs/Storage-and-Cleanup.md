# Storage and Cleanup

IslandPortal tracks managed portals and managed NPCs so island resets do not leave stray blocks, entities, or stale data behind.

---

## Stored Data

Data is stored in:

```text
plugins/IslandPortal/playerdata/
```

The repository groups data by owner when possible. Server-owned objects are stored in `server.yml`.

---

## Managed Portal Data

Each managed portal stores:

- Portal id.
- Portal type.
- World and base block location.
- Facing direction.
- Owner UUID.
- Island id.
- Default portal flag.
- Access policies.
- Island members.
- Portal frame blocks.
- Trigger blocks.
- Support blocks.
- Optional return location.

---

## Managed NPC Data

Each managed NPC stores:

- NPC id.
- NPC type.
- World and safe spawn anchor.
- Yaw and pitch.
- Owner UUID.
- Island id.
- Island members.
- Runtime entity id while the entity is alive.

!!! note "Runtime entity id"
    Entity ids are runtime-only. If the entity disappears, IslandNPC respawns the NPC from the saved spawn anchor and updates the tracked entity id.

---

## Auto-Cleanup

When a supported skyblock plugin deletes, resets, or removes an island, IslandPortal removes objects linked to that island.

Portal cleanup removes:

1. Portal frame blocks.
2. Trigger blocks.
3. Tracked support blocks from generated platforms or schematics.

NPC cleanup removes:

1. The live Bukkit entity if it exists.
2. The managed NPC data entry.
3. Runtime movement and respawn tracking state.

---

## Autosave

Portal and NPC repositories use dirty flags to avoid unnecessary disk writes.

Important notes:

- Portal autosave is controlled by `runtime.autosave-interval-minutes`.
- NPC data is saved when NPC data changes.
- File I/O is designed to avoid main-thread or region-thread blocking.
- Portal and NPC sections share the same owner data files safely.

!!! warning "Do not edit while running"
    Avoid editing files in `playerdata/` while the server is running. Use configuration files for behavior changes and admin commands for managed object changes.
