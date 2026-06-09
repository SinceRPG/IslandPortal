# Folia Architecture

IslandPortal is designed from the ground up to support **Folia's Regionized Multithreading** natively, while remaining 100% backward compatible with Paper.

---

## 🧵 Scheduler Layer

All task dispatch goes through the abstract `PlatformScheduler`. This ensures the plugin always picks the right thread for the right job, regardless of the platform.

| Method | Behavior on Folia | Behavior on Paper |
|--------|------------------|-------------------|
| `runGlobal` | Uses Folia's global region scheduler. | Uses Bukkit's standard scheduler. |
| `runGlobalLater` | Returns a cancellable `PlatformTask`. | Returns a cancellable Bukkit task. |
| `runAsync` | Uses Folia's async scheduler. | Uses Bukkit's async scheduler. |
| `runAtLoaded` | Loads target chunk async, runs logic on region. | Uses Bukkit's standard scheduler. |
| `runFor` | Uses the entity scheduler. | Uses Bukkit's standard scheduler. |

---

## 🛡️ Thread Safety Models

### Entity Safety
Player inventories, GUI interactions, message callbacks, portal cooldown changes, NPC look-at-player rotation, NPC movement, NPC removal, and NPC respawn entity updates are scheduled through entity-aware execution paths. This keeps entity-owned state confined to the correct scheduler.

### Block Safety
Portal block placement, portal island generation, schematic pasting, and island cleanup are all scheduled through `runAtLoaded`. 
Furthermore, portal island placement explicitly **rejects candidates whose full clearance bounds cross chunk boundaries**, guaranteeing that a single region task exclusively owns every block it reads and writes.

### Teleport Safety
Portal teleport actions use the native `teleportAsync` API. Any failure messages are dispatched in the completion callback back through the player's entity scheduler.

### Storage Safety
Portal and NPC state are held in fast concurrent maps. The autosave routines use dirty flags and preserve separate data sections when managed data shares the same owner file.
