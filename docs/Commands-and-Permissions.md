# Commands and Permissions

IslandPortal keeps one admin permission and uses per-type configuration for portal and NPC behavior.

---

## Administrative Permission

```text
islandportal.admin
```

Players with this permission can use `/islandportal` and `/ip` administrative commands.

---

## Commands

By default, the main command is `/islandportal` and the alias is `/ip`.

| Command | Description |
|---------|-------------|
| `/ip help` | Shows the help menu. |
| `/ip reload` | Reloads plugin configuration files. |
| `/ip settarget <type>` | Sets a portal type's teleport target to your current location. |
| `/ip give <type> <player> [amount]` | Gives portal items to a player. |
| `/ip create <type>` | Creates a managed portal at your current block. |
| `/ip createisland <type>` | Creates a test portal island near your current location. |
| `/ip remove` | Removes the nearest managed portal. |
| `/ip npc spawn <type> [id]` | Spawns a managed NPC at your current location. |
| `/ip npc remove` | Removes the nearest managed NPC. |

---

## Portal-Specific Permissions

Each portal type can define its own permission nodes in `portals.yml`.

```yaml
permissions:
  place: "islandportal.portal.spawn.place"
  use: "islandportal.portal.spawn.use"
  pickup: "islandportal.portal.spawn.pickup"
  configure: "islandportal.portal.spawn.configure"
```

Fields:

- `place`: Required to place the portal item.
- `use`: Required to trigger the portal action.
- `pickup`: Required to pick the portal back up.
- `configure`: Required to open the portal settings menu.

!!! info "Empty permissions"
    Leave a permission value empty (`""`) to disable that specific permission check.

---

## IslandNPC Permissions and Unlocks

NPC admin commands use:

```text
islandportal.admin
```

Automatic island NPC unlocks are configured per NPC type in `npcs.yml`.

```yaml
unlock:
  default-unlocked: false
  permissions:
    - islandportal.npc.blacksmith
  min-island-members: 2
```

Fields:

- `default-unlocked`: Allows the NPC to spawn without extra requirements.
- `permissions`: Requires the island owner or an online island member to have at least one listed permission.
- `min-island-members`: Requires the island member count to be at least this value.

!!! note "Unlock timing"
    Unlock checks run when IslandPortal handles island creation. Permission-based unlocks require the owner or member to be online at that moment.
