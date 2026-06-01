# Configuration

IslandPortal generates editable YAML files in:

```text
plugins/IslandPortal/
```

Main files:

- `config.yml`: Runtime behavior, integrations, cooldowns, and scheduler loops.
- `portals.yml`: Portal types, items, shapes, island placement, access policies, and actions.
- `npcs.yml`: IslandNPC types, spawn behavior, movement, unlocks, and interactions.
- `menus.yml`: Portal settings GUI.
- `messages.yml`: Command and player-facing messages.

---

## `config.yml`

### Global

```yaml
# Master switch for the plugin.
enabled: true

# Prints internal placement, cleanup, migration, portal, and NPC diagnostics.
debug: false
```

### Hooks

Only enable hooks for skyblock plugins installed on your server.

```yaml
hooks:
  bentobox: true
  superior-skyblock: true
  skyllia: true
```

### Commands

```yaml
commands:
  description: "Manage island portals"
  aliases:
    - ip
```

### Runtime

```yaml
runtime:
  # Saves dirty portal data every N minutes.
  autosave-interval-minutes: 10

  # Prevents vanilla Nether behavior while using managed portals.
  vanilla-portal-cooldown-ticks: 200

  # Per-player portal action cooldown.
  use-cooldown-millis: 1000

  # Nearby lookup radius when portal events report slightly shifted positions.
  portal-near-scan:
    horizontal: 2
    below: 1
    above: 2
```

### IslandNPC Runtime

```yaml
island-npcs:
  # Master switch for all IslandNPC behavior.
  enabled: true

  # Prevents players from spam-clicking NPCs.
  interaction-cooldown-millis: 750

  # How often NPCs rotate to face nearby players.
  look-interval-ticks: 10

  # How often IslandNPC evaluates movement.
  # Per-NPC movement.interval-ticks is still respected.
  movement-interval-ticks: 10

  # How often IslandNPC checks whether managed entities disappeared or died.
  respawn-check-ticks: 40

  # Delay before respawning a missing/dead NPC.
  respawn-delay-ticks: 40

  # Delay after island creation before default NPCs are placed.
  creation-delay-ticks: 60
```

### Island Creation and Cleanup

```yaml
# Delay after island creation before portal placement starts.
creation-delay-ticks: 60

# Retry count if the skyblock plugin is still pasting/building the island.
creation-retry-attempts: 5

# Delay between placement retries.
creation-retry-delay-ticks: 40

# Fallback cleanup radius when an exact island id is unavailable.
island-cleanup-radius: 100
```

---

## `portals.yml`

`portals.yml` defines custom portal types.

Portal types can configure:

- Portal item material, display name, lore, custom model data, flags, and glint.
- Frame size and materials.
- Track-only mode for schematic portals.
- Island offsets and facing.
- Portal island generation or schematic paste settings.
- Default-on-island behavior.
- Access policies.
- Per-type permission nodes.
- Teleport or command actions.

See [Schematic Portals](Schematic-Portal-Islands.md) for schematic-specific setup.

---

## `npcs.yml`

`npcs.yml` defines IslandNPC types.

NPC types can configure:

- Entity type and villager profession.
- Nameplate and visual behavior.
- Safe spawn search.
- Look-at-player behavior.
- Controlled movement.
- Automatic respawn.
- Unlock requirements.
- Left-click and right-click interactions.
- Player commands and console commands.

See [Island NPCs](IslandNPC.md) for the full reference and examples.

---

## `menus.yml`

`menus.yml` controls the portal settings GUI:

- Inventory title.
- Inventory size.
- Item slots.
- Materials.
- Display names.
- Lore.
- Custom model data.
- GUI actions.

---

## `messages.yml`

`messages.yml` controls player-facing and command messages.

Portal interaction messages support MiniMessage formatting. Command messages are kept plain for console compatibility.
