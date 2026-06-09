# Island NPCs

IslandNPC is a managed NPC system built into IslandPortal. It is designed for skyblock islands where NPCs should be easy to configure, safe to spawn, safe to move, and automatically restored if something removes or kills the entity.

The system does not require Citizens or any external NPC plugin. It uses real Bukkit living entities, stores them in IslandPortal player data, and runs through the same Paper/Folia-aware scheduler layer as portals.

---

## What IslandNPC Can Do

- Spawn NPCs automatically when an island is created.
- Spawn NPCs manually with `/ip npc spawn <type> [id]`.
- Remove the nearest managed NPC with `/ip npc remove`.
- Give each NPC a custom MiniMessage name.
- Use different living entity types.
- Configure villager professions.
- Make NPCs silent, glowing, invulnerable, non-collidable, adult, or baby.
- Make NPCs look at nearby players.
- Make NPCs wander around their saved spawn anchor.
- Search for a safe spawn position if the configured offset is blocked.
- Run left-click and right-click actions separately.
- Send dialogue messages.
- Run player commands.
- Run console commands.
- Unlock NPCs by default, permission, or island member count.
- Respawn NPCs if they die, despawn, are removed, or disappear after reload.
- Clean up island NPCs when supported skyblock islands are deleted or reset.

!!! note "Design note"
    IslandNPC movement uses controlled step teleportation instead of vanilla AI pathfinding. This avoids NPCs walking off small islands, loading unexpected chunks, or fighting Folia region ownership.

---

## File Location

```text
plugins/IslandPortal/npcs.yml
```

---

## Quick Start

```yaml
npc-types:
  guide:
    entity-type: VILLAGER
    name: "<gold>Island Guide"
    profession: LIBRARIAN
    default-on-island: true
    island-offset:
      x: 2.5
      y: 1.0
      z: 2.5
    interactions:
      right-click:
        messages:
          - "<yellow>%npc%<gray>: <white>Welcome to your island, %player%."
        player-commands: []
        console-commands: []
```

Reload:

```text
/ip reload
```

Spawn manually:

```text
/ip npc spawn guide
```

---

## Complete NPC Type Reference

```yaml
npc-types:
  example:
    # Living Bukkit entity type. Non-living types fall back to VILLAGER.
    entity-type: VILLAGER

    # Display name. MiniMessage is supported.
    name: "<gold>Example NPC"

    # Whether the nameplate is always visible.
    name-visible: true

    # Villager-only. Ignored for non-villager entities.
    profession: LIBRARIAN

    # Applies to ageable entities.
    baby: false

    # Visual glow effect.
    glowing: false

    # Prevents idle mob sounds.
    silent: true

    # Prevents normal damage from killing the NPC.
    invulnerable: true

    # Whether players/entities collide with this NPC.
    collidable: false

    # If true, this NPC type is created automatically on island creation.
    default-on-island: true

    # Desired position relative to the island location/home.
    island-offset:
      x: 2.5
      y: 1.0
      z: 2.5

    # If the desired offset is blocked, scan nearby positions.
    spawn-search:
      enabled: true
      horizontal-radius: 8
      vertical-radius: 6
      step: 1

    # Default rotation after spawn or respawn.
    yaw: 180
    pitch: 0

    # Rotates the NPC toward nearby players.
    look-at-player:
      enabled: true
      radius: 8

    # Controlled wandering around the saved spawn anchor.
    movement:
      enabled: true
      radius: 4
      interval-ticks: 20
      step-distance: 0.35
      target-attempts: 12

    # Spawn requirements for automatic island NPCs.
    unlock:
      default-unlocked: true
      permissions: []
      min-island-members: 0

    # Separate action lists for left click and right click.
    interactions:
      left-click:
        messages:
          - "<yellow>%npc%<gray>: <white>You left-clicked me."
        player-commands: []
        console-commands: []
      right-click:
        messages:
          - "<yellow>%npc%<gray>: <white>Hello, %player%."
        player-commands:
          - "is"
        console-commands:
          - "say %player% talked to %npc_id%."
```

---

## Field Notes

### `entity-type`

Examples:

```yaml
entity-type: VILLAGER
entity-type: WANDERING_TRADER
entity-type: ZOMBIE
entity-type: ALLAY
entity-type: CAT
```

!!! warning "Living entities only"
    Non-living entity types are ignored and fall back to `VILLAGER`.

### `profession`

Only applies when `entity-type: VILLAGER`.

Examples:

```yaml
profession: NONE
profession: FARMER
profession: LIBRARIAN
profession: TOOLSMITH
profession: WEAPONSMITH
```

### `spawn-search`

This prevents common island problems:

- The configured offset is inside a wall.
- The island schematic pasted one block higher or lower than expected.
- The intended block is air above the void.
- Another plugin placed blocks at the target location.

A safe stand location requires:

- The feet block is passable.
- The head block is passable.
- The block below is solid.
- The chunk is already loaded.

Recommended defaults:

```yaml
spawn-search:
  enabled: true
  horizontal-radius: 8
  vertical-radius: 6
  step: 1
```

### `movement`

Movement is anchored to the NPC's saved spawn location. The NPC picks random safe targets inside the radius and moves in small controlled steps.

```yaml
movement:
  enabled: true
  radius: 4
  interval-ticks: 20
  step-distance: 0.35
  target-attempts: 12
```

Recommended values:

- Small decorative NPC: `radius: 2`, `interval-ticks: 30`
- Active hub-style NPC: `radius: 4`, `interval-ticks: 20`
- Mostly static NPC: `enabled: false`

!!! warning "Keep island size in mind"
    Do not set `radius` larger than the walkable island platform. IslandNPC validates each target, but a small radius keeps movement clean and predictable.

### `look-at-player`

This only rotates the entity. It does not move it.

```yaml
look-at-player:
  enabled: true
  radius: 8
```

### `unlock`

Unlocks are evaluated when a supported island creation event is handled.

```yaml
unlock:
  default-unlocked: false
  permissions:
    - islandportal.npc.blacksmith
  min-island-members: 2
```

Meaning:

- The NPC is not automatically unlocked by default.
- At least one online island member or owner must have `islandportal.npc.blacksmith`.
- The island must have at least 2 members.

### `interactions`

Left click and right click are fully separate.

```yaml
interactions:
  left-click:
    messages:
      - "<red>%npc%<gray>: <white>Please do not hit me."
    player-commands: []
    console-commands: []
  right-click:
    messages:
      - "<green>%npc%<gray>: <white>Opening the shop."
    player-commands:
      - "shop"
    console-commands:
      - "eco give %player% 10"
```

Placeholders:

| Placeholder | Value |
|-------------|-------|
| `%player%` | Player name |
| `%npc%` | Configured NPC display name |
| `%npc_id%` | Managed NPC id |
| `%island%` | Island id if available |
| `%owner%` | Owner UUID if available |

---

## Example: Static Quest NPC

```yaml
npc-types:
  quest_master:
    entity-type: VILLAGER
    name: "<light_purple>Quest Master"
    profession: CARTOGRAPHER
    default-on-island: true
    island-offset:
      x: -3.5
      y: 1.0
      z: 1.5
    spawn-search:
      enabled: true
      horizontal-radius: 6
      vertical-radius: 4
      step: 1
    look-at-player:
      enabled: true
      radius: 10
    movement:
      enabled: false
      radius: 0
      interval-ticks: 20
      step-distance: 0.25
      target-attempts: 6
    unlock:
      default-unlocked: true
      permissions: []
      min-island-members: 0
    interactions:
      left-click:
        messages:
          - "<light_purple>Quest Master<gray>: <white>Right-click me to view quests."
        player-commands: []
        console-commands: []
      right-click:
        messages: []
        player-commands:
          - "quests"
        console-commands: []
```

---

## Example: Permission-Locked Blacksmith

```yaml
npc-types:
  blacksmith:
    entity-type: VILLAGER
    name: "<dark_gray>Blacksmith"
    profession: TOOLSMITH
    default-on-island: true
    island-offset:
      x: 4.5
      y: 1.0
      z: -2.5
    spawn-search:
      enabled: true
      horizontal-radius: 10
      vertical-radius: 6
      step: 1
    look-at-player:
      enabled: true
      radius: 8
    movement:
      enabled: true
      radius: 3
      interval-ticks: 25
      step-distance: 0.3
      target-attempts: 10
    unlock:
      default-unlocked: false
      permissions:
        - islandportal.npc.blacksmith
      min-island-members: 0
    interactions:
      left-click:
        messages:
          - "<dark_gray>Blacksmith<gray>: <white>Bring me stronger materials."
        player-commands: []
        console-commands: []
      right-click:
        messages:
          - "<dark_gray>Blacksmith<gray>: <white>Opening upgrades."
        player-commands: []
        console-commands:
          - "upgradegui open %player%"
```

---

## Respawn Behavior

IslandNPC automatically restores managed NPCs when:

- The NPC dies.
- Another plugin removes the entity.
- The entity becomes invalid.
- The server reloads and the entity is not present.
- Chunks unload/reload and the tracked entity id is no longer valid.

Runtime settings:

```yaml
island-npcs:
  respawn-check-ticks: 40
  respawn-delay-ticks: 40
```

!!! note "Respawn anchor"
    NPCs respawn at their saved safe spawn anchor, not at their last wandering position. This keeps long-running islands clean and predictable.

---

## Troubleshooting

### NPC does not spawn

Check:

- `island-npcs.enabled` is `true`.
- The NPC type exists in `npcs.yml`.
- `default-on-island` is `true` for automatic island spawning.
- Unlock requirements are satisfied.
- `spawn-search` can find a safe block.
- The target world/chunk is loaded.

### NPC spawns but does not move

Check:

- `movement.enabled` is `true`.
- `movement.radius` is greater than `0`.
- There are safe blocks inside the movement radius.
- `island-npcs.movement-interval-ticks` is not too high.

### NPC keeps returning to its spawn

This happens when the NPC is outside its allowed movement radius or was moved by another plugin. IslandNPC returns it to the anchor to protect island layout.

### NPC is killed by another plugin

IslandNPC will respawn it after `respawn-delay-ticks`, as long as its managed data still exists.
