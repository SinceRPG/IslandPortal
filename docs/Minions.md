# Minions

IslandPortal Minions are managed island workers. Players place a minion item, the plugin spawns a display entity, production accumulates over time, and the owner manages storage, fuel, upgrades, and pickup through a GUI.

The system is separate from IslandNPC because minions need production state, offline calculation, storage, fuel, tier upgrades, anti-dupe handling, and ownership protection.

> [!WARNING]
> **Beta Feature:** The Minions system is currently in beta. It may contain bugs, balance issues, or break unexpectedly on Folia. It is **not recommended** to use this feature on a production server yet.

---

## Feature Set

- Configurable minion types split across `plugins/IslandPortal/minions/types/*.yml`.
- Admin item command: `/ip minion give <type> <player> [amount]`.
- Placement by right-clicking a block with a minion item.
- Safe placement search if the clicked location is blocked.
- Armor stand display entity with custom name and head material.
- GUI management by right-clicking the minion.
- Shift-left-click pickup shortcut.
- Owner/admin access protection.
- Per-owner type limits.
- Online production.
- Offline production through saved timestamps.
- Configurable offline production cap.
- Internal storage with collect button.
- Fuel items with duration and speed multiplier.
- Booster fuel with output multipliers and bonus drops.
- Explicit GUI input slots for speed fuel and output boosters.
- Configurable tier work areas such as 3x3, 4x4, 5x5, and larger.
- Action types: generator, miner, spawner miner, slayer, collector, feeder, lumberjack, farmer, fisher, seller, and spawner.
- Built-in action animation for display armor stands.
- Temporary target visuals are tagged to the owning minion and cleaned up when the minion is removed, picked up, reset, reloaded, or shut down.
- Farmer minions can show a temporary crop growth animation before harvest feedback completes.
- Rotate direction button.
- Link nearest chest button.
- Tier upgrades with item costs.
- Permission-based offline production caps.
- External shop command integration.
- Storage limits per tier.
- Respawn monitor for missing/dead display entities.
- Island delete/reset cleanup through supported skyblock hooks.
- Dedicated GUI layout in `minions/gui.yml`.
- Runtime behavior/messages in `minions/settings.yml`.
- Per-island global minion cap.
- Linked chest output and auto-sell toggles.
- Admin inspect command.
- Optional Vault economy upgrade costs through a real soft-depend bridge.

!!! note "Bundled config set"
    The default resources now include one file per supported action category, with English notes inside each file. You can edit only the files you use, or add new files under `minions/types/`.

---

## Runtime Settings

`config.yml`:

```yaml
minions:
  enabled: true
  tick-interval-ticks: 20
  autosave-interval-minutes: 5
  respawn-check-ticks: 60
  max-actions-per-tick: 250
  place-radius: 4
```

Recommended values:

- Small server: `tick-interval-ticks: 20`, `max-actions-per-tick: 250`
- Large server: `tick-interval-ticks: 40`, `max-actions-per-tick: 500`
- Strict performance mode: `tick-interval-ticks: 60`, lower offline limits per minion type

!!! note "Chunk loading"
    Minions do not keep chunks loaded. If a minion's chunk is not loaded, action execution waits until the chunk is loaded naturally or by the server. Offline production is timestamp-based and capped, so the plugin does not need thousands of forced chunks.

`minions/settings.yml`:

```yaml
runtime:
  id-prefix: "minion:"
  per-island-global-limit: 80
  island-cleanup-radius: 100
  link-chest-range: 5
  work-when-owner-offline: true
  permissions:
    place-per-type: false
    place-prefix: "islandportal.minion.place."
    admin-bypass: true
  placement:
    vertical-search-min: -2
    vertical-search-max: 2
  display-entity:
    custom-name-visible: true
    arms: true
    base-plate: false
    invulnerable: true
    persistent: false
    remove-when-far-away: false
    max-health: 20.0
  default-equipment:
    # Used when a type does not define display.* equipment.
    chestplate-material: LEATHER_CHESTPLATE
    chestplate-item: LEATHER_CHESTPLATE
    leggings-material: LEATHER_LEGGINGS
    leggings-item: LEATHER_LEGGINGS
    boots-material: LEATHER_BOOTS
    boots-item: LEATHER_BOOTS
    main-hand-material: WOODEN_PICKAXE
    main-hand-item: WOODEN_PICKAXE
  animations:
    reset-ticks: 14
    target-visible-ticks: 18
    target-y-offset: 0.75
    block-break:
      enabled: true
      progress:
        - 0.15
        - 0.35
        - 0.55
        - 0.75
        - 0.95
    crop-growth:
      enabled: true
      stage-ticks: 2
  output:
    linked-chest: true
    hopper: true
  action-area:
    # Used when a tier does not define action-size directly.
    # With these values T1=3x3, T2=4x4, T3=5x5.
    default-size: 3
    size-per-tier: 1
  auto-sell:
    enabled: false
  integrations:
    vault: true
    shopgui-plus: true
    essentials: true
    cmi: true
    packet-events:
      enabled: true
      view-range: 48
    head-database: true
    itemsadder: true
    oraxen: true
    nexo: true
    mmoitems: true
    mythicmobs: true
```

Use `runtime.integrations` to disable an optional bridge on servers that have the plugin installed but should not use it for minions.

`runtime.permissions.place-per-type` requires a permission per minion type, for example `islandportal.minion.place.miner`.

---

## Commands

```text
/ip minion give <type> <player> [amount]
/ip minion fuel <fuel> <player> [amount]
/ip minion booster <fuel> <player> [amount]
/ip minion remove
```

Examples:

```text
/ip minion give cobblestone Steve 1
/ip minion give miner Steve 1
/ip minion give seller Alex 1
/ip minion remove
```

---

## File Layout

```text
plugins/IslandPortal/minions/
  gui.yml
  settings.yml
  fuels.yml
  types/
    collector.yml
    farmer.yml
    feeder.yml
    fisher.yml
    generator.yml
    lumberjack.yml
    miner.yml
    seller.yml
    slayer.yml
    spawner.yml
    spawner-miner.yml
```

Notes:

- Put shared fuel/booster definitions in `fuels.yml`.
- Put GUI title, size, slots, icons, names, lore, and actions in `gui.yml`.
- Put runtime limits, animation timing, output routing, auto-sell, and minion messages in `settings.yml`.
- Put minion type definitions in separate files under `types/`.
- Each bundled file contains English notes explaining what the keys do and which behavior is supported by the current code.
- You may create more files, for example `types/donor.yml`, `types/nether.yml`, or `types/event.yml`.
- Avoid duplicate minion IDs across files. If two files define the same ID, the last loaded file wins.
- Legacy `plugins/IslandPortal/minions.yml` is still read only as a fallback if the new folder files do not exist.

--- 

## Code Layout

The minion implementation is split so behavior stays easier to change:

| File | Responsibility |
|------|----------------|
| `MinionService` | Bukkit events, task lifecycle, placement, GUI action routing, and production orchestration. |
| `MinionItemFactory` | Minion item creation and item-to-type detection. |
| `MinionDisplayService` | Armor stand spawn, respawn, cleanup, and animation playback. |
| `MinionSellService` | Sell command placeholders, Vault deposits, and native price lookup use. |
| `NativeItemBridge` + providers | Optional HeadDatabase/ItemsAdder/Oraxen/Nexo/MMOItems/MythicMobs item adapters. |
| `PriceLookupRegistry` + providers | Optional ShopGUI+/Essentials/CMI price lookup adapters. |
| `CosmeticAnimationBridge` + providers | PacketEvents cosmetic animation packets with no-op fallback. |
| `minion.event.*` | Bukkit events for place, pickup, and chest-link integrations. |

Configuration is intentionally split into `settings.yml`, `gui.yml`, `fuels.yml`, and `types/*.yml` so server owners do not need to edit Java for normal behavior changes.

---

## `fuels.yml` Reference

```yaml
fuels:
  coal:
    material: COAL
    duration-seconds: 1800
    speed-multiplier: 1.25
    output-multiplier: 1.0
    bonus-drops:
      items: []
```

---

## Type File Reference

## Work Area

Each tier can define the square action area used by production visuals and target selection:

```yaml
tiers:
  1:
    interval-seconds: 24
    storage-limit: 64
    action-size: 3
  2:
    interval-seconds: 18
    storage-limit: 128
    action-size: 4
```

If `action-size` is missing, IslandPortal uses `runtime.action-area` from `minions/settings.yml`.

```yaml
runtime:
  action-area:
    default-size: 3
    size-per-tier: 1
```

With that default, tier 1 is 3x3, tier 2 is 4x4, tier 3 is 5x5, and so on. On every production action the minion picks a target in that area, rotates toward it, and plays the target visual there.

---

```yaml
minion-types:
  cobblestone:
    item:
      # Placement item given by /ip minion give.
      material: PLAYER_HEAD
      display-name: "<gray>Cobblestone Minion"
      lore:
        - "<dark_gray>Place this on your island."
        - "<gray>Produces cobblestone over time."
      custom-model-data: 10001

    display:
      # Display entity name. %tier% is supported.
      name: "<gray>Cobblestone Minion <white>T%tier%"

      # Armor stand helmet material.
      head-material: COBBLESTONE
      small: true
      glowing: false
      health: 20

    action:
      # GENERATOR, MINER, SLAYER, COLLECTOR, FEEDER, LUMBERJACK,
      # FARMER, FISHER, SELLER, SPAWNER, SPAWNER_MINER
      type: SPAWNER_MINER
      range: 3
      blocks-per-action: 1
      smelt-at-tier: 0
      spawn-block: COBBLESTONE
      spawn-entity: ZOMBIE
      allowed-materials:
        - COBBLESTONE
      sell-materials: []

    limits:
      # Maximum number of this type per owner UUID.
      max-per-owner: 8

    # If true, production pauses unless fuel is active.
    requires-fuel: false

    # Maximum offline time that can produce resources.
    offline-production-limit-seconds: 21600

    # Optional permission overrides checked when the owner is online.
    offline-production-limit-permission:
      - permission: islandportal.minion.offline.vip
        limit-seconds: 43200
      - permission: islandportal.minion.offline.mvp
        limit-seconds: 86400

    shop:
      enabled: false
      sell-commands:
        - "eco give %player% %amount%"

    drops:
      items:
        - material: COBBLESTONE
          amount: 1

    tiers:
      1:
        # Seconds per production operation.
        interval-seconds: 20

        # Total stored item count, across all drops.
        storage-limit: 64

        # Square work area size. If omitted, runtime.action-area decides it.
        action-size: 3

        # Cost to upgrade from this tier to the next tier.
        upgrade-cost:
          - material: COBBLESTONE
            amount: 64
        # Optional Vault money cost for upgrading into this tier.
        upgrade-money: 0.0
      2:
        interval-seconds: 15
        storage-limit: 128
        upgrade-cost:
          - material: COBBLESTONE
            amount: 256
      3:
        interval-seconds: 10
        storage-limit: 256
        upgrade-cost: []
```

---

## Production Logic

For each minion:

1. The service reads the current tier.
2. It checks fuel state if `requires-fuel` is enabled.
3. It calculates elapsed time from `last-production-millis`.
4. It caps elapsed time by `offline-production-limit-seconds`.
5. It calculates how many production operations fit into the elapsed time.
6. It adds drops until storage is full or `max-actions-per-tick` is reached.
7. It saves the new production timestamp and storage.

Fuel changes the effective interval:

```text
effective interval = tier interval / fuel speed multiplier
```

Fuel can also change output:

```yaml
fuels:
  enchanted_coal:
    material: COAL_BLOCK
    duration-seconds: 7200
    speed-multiplier: 1.75
    output-multiplier: 1.25
    bonus-drops:
      items:
        - material: EXPERIENCE_BOTTLE
          amount: 1
```

---

## Action Types

| Type | Behavior |
|------|----------|
| `GENERATOR` | Produces configured `drops.items` by timer only. |
| `MINER` | Selects a target inside the tier work area, rotates toward it, and mines matching blocks when present. If no block is available, configured drops still produce so the minion remains self-contained. |
| `SPAWNER_MINER` | Creates/mines configured block output through the same work-area targeting flow. |
| `SLAYER` | Selects a target inside the work area, shows a cosmetic mob target, and produces configured drops. Nearby hostile mobs can still be removed when present. |
| `COLLECTOR` | Collects dropped item entities in range when present; otherwise uses configured drops so it can run as a normal minion. |
| `FEEDER` | Shares its active fuel state with nearby minions in range. |
| `LUMBERJACK` | Targets the work area, chops logs/wood/leaves when present, and otherwise produces configured drops. |
| `FARMER` | Targets the work area, plays a crop growth animation, harvests mature crops when present, and otherwise produces configured drops. |
| `FISHER` | Targets the work area, plays item/target feedback, and produces configured drops without requiring external water unless you customize the type logic later. |
| `SELLER` | Sells items from a linked chest with configured sell commands. |
| `SPAWNER` | Spawns the configured entity near the minion. |

!!! warning "Protection plugins"
    No protection plugin native API is declared or used yet. The current action engine is conservative and chunk-safe, but deep WorldGuard/CoreProtect/SkyBlock protection decisions should be added only when a real optional adapter is implemented.

---

## Chest Linking

Open the minion GUI and click **Link Chest**. IslandPortal scans nearby blocks and stores the nearest container location.

Linked chests are used by `SELLER` minions.

---

## Rotate Direction

Open the minion GUI and click **Rotate**. The minion rotates 90 degrees and saves the new yaw.

Direction is used by:

- `MINER`
- `SPAWNER_MINER`
- `FISHER`

---

## Animations

When a minion performs a production action, its armor stand display briefly moves, then resets. Default fallback timing is in `runtime.animations.reset-ticks`; custom multi-stage animations live under each type's `display.animations`.

Temporary target visuals are configured in `runtime.animations`:

- `target-visible-ticks`: lifetime for BlockDisplay, ItemDisplay, and cosmetic mob targets.
- `target-y-offset`: vertical offset for target visuals.
- `block-break.enabled`: whether viewer-only crack overlays are sent.
- `block-break.progress`: crack progress frames from 0.0 to 1.0.
- `crop-growth.enabled`: whether farmer targets show staged crop growth.
- `crop-growth.stage-ticks`: delay between crop age frames.

All temporary target entities are tagged with the owner minion id. Pickup, remove, island reset cleanup, reload, and shutdown remove those cosmetics immediately instead of waiting for their timer.

The production/action delay is controlled by tier interval:

```yaml
tiers:
  1:
    interval-seconds: 20
```

For large servers, increase intervals and/or `minions.tick-interval-ticks`.

Example multi-stage animation:

```yaml
display:
  animations:
    windup:
      delay-ticks: 0
      duration-ticks: 4
      right-arm:
        x: -35
        y: 0
        z: 0
    swing:
      delay-ticks: 4
      duration-ticks: 8
      right-arm:
        x: -80
        y: 0
        z: 0
```

If PacketEvents is installed and `runtime.integrations.packet-events.enabled` is true, animation stages also send lightweight client-side entity animation packets to viewers within `view-range`.

---

## Storage

Minion storage is internal data, not a physical chest.

The GUI shows:

- Tier.
- Stored amount.
- Storage limit.
- Fuel state.

The Collect button transfers stored items to the owner's inventory. Overflow is dropped naturally at the player location.

!!! warning "Storage limit"
    The storage limit is total item count, not per-material count. If a minion produces multiple drops, all drops share the same storage limit.

---

## Fuel

Fuel is configured globally:

```yaml
fuels:
  coal:
    material: COAL
    duration-seconds: 1800
    speed-multiplier: 1.25
  enchanted_coal:
    material: COAL_BLOCK
    duration-seconds: 7200
    speed-multiplier: 1.75
```

Fuel and booster slots are explicit input slots:

1. The player opens the minion GUI.
2. The player picks up a configured fuel or booster item on the cursor.
3. The player clicks `Fuel Slot` for speed or `Booster Slot` for output.
4. IslandPortal consumes one cursor item and extends the matching timer.

!!! note "Fuel stacking"
    Fuel duration stacks by extending the expiry timestamp. The latest consumed fuel sets the active multiplier value.

---

## Shop Integration

The Sell Storage button sells each stored item key. Prices are configured per item, with an optional default price.

```yaml
shop:
  enabled: true
  price-sources:
    - SHOPGUI_PLUS
    - ESSENTIALS
    - CMI
    - CONFIG
  default-price: 1.0
  prices:
    COBBLESTONE: 0.25
    itemsadder:custom:mineral_shard: 4.5
  sell-commands:
    - "eco give %player% %total_price%"
    - "say %player% sold %amount%x %item% for %total_price% from %type%"
```

`price-sources` controls lookup order. `SHOPGUI_PLUS`, `ESSENTIALS`, and `CMI` use native API lookups when the matching plugin is installed. `CONFIG` falls back to `prices` and `default-price`.

If `sell-commands` is empty and Vault is installed, IslandPortal deposits `%total_price%` directly through Vault. Otherwise commands are dispatched by console.

Placeholders:

| Placeholder | Value |
|-------------|-------|
| `%player%` | Player name |
| `%material%` | Stored material or item key |
| `%item%` | Stored material or item key |
| `%amount%` | Stored amount |
| `%price%` | Per-item price |
| `%unit_price%` | Per-item price |
| `%total_price%` | Amount multiplied by per-item price |
| `%minion_id%` | Managed minion id |
| `%type%` | Minion type id |

!!! warning "Command success"
    Command integrations cannot know whether another plugin actually paid the player unless that plugin exposes an API. Direct Vault payment is used only when no sell commands are configured.

IslandPortal declares Vault, ShopGUIPlus, Essentials, CMI, HeadDatabase, ItemsAdder, Oraxen, Nexo, MMOItems, MythicMobs, and PacketEvents as soft dependencies because the minion system compiles against their APIs and calls them only through optional adapters when those plugins are installed.

!!! note "Soft-depend policy"
    A plugin should only be added to `paper-plugin.yml` when IslandPortal actually calls that plugin's API. Command-based integration does not require a soft-depend.

Current state:

- Native Vault economy withdrawal is used for `upgrade-money`; direct Vault sell deposits are used when `sell-commands` is empty.
- Native ShopGUI+, Essentials, and CMI sell price lookups can be used before config prices.
- Native HeadDatabase, ItemsAdder, Oraxen, and Nexo selectors can be used for minion display heads.
- Native ItemsAdder, Oraxen, Nexo, MMOItems, and MythicMobs item selectors can be used for minion items, configured drops, and upgrade costs.
- PacketEvents is used for viewer-only animation packets when installed; armor stand poses remain the fallback and authoritative server-side display.
- Server owners can still integrate many shop/economy plugins through console commands.
- Native adapters can be added later one plugin at a time, and only then should the matching soft-depend be declared.

Native item selectors:

```yaml
item:
  material: IRON_PICKAXE
  item: itemsadder:custom:miner_minion

display:
  head-material: PLAYER_HEAD
  head-item: hdb:12345
  animations:
    windup:
      delay-ticks: 0
      duration-ticks: 4
      right-arm:
        x: -35
        y: 0
        z: 0
    swing:
      delay-ticks: 4
      duration-ticks: 8
      right-arm:
        x: -80
        y: 0
        z: 0
    magic-critical:
      delay-ticks: 12
      duration-ticks: 4
      right-arm:
        x: -20
        y: 0
        z: 0

drops:
  items:
    - material: EMERALD
      item: nexo:compressed_emerald
      amount: 1

tiers:
  2:
    upgrade-cost:
      - material: DIAMOND
        item: mmoitems:MATERIAL:REFINED_DIAMOND
        amount: 8
```

---

## Permission-Based Offline Limits

The base offline cap is:

```yaml
offline-production-limit-seconds: 21600
```

Optional permission overrides:

```yaml
offline-production-limit-permission:
  - permission: islandportal.minion.offline.vip
    limit-seconds: 43200
  - permission: islandportal.minion.offline.mvp
    limit-seconds: 86400
```

!!! note "Permission timing"
    Permission-based offline caps are checked when the owner is online. If the owner is offline, IslandPortal uses the base `offline-production-limit-seconds`.

---

## Upgrades

Each tier defines production speed and storage. The target tier defines the cost to upgrade into it.

```yaml
tiers:
  1:
    interval-seconds: 20
    storage-limit: 64
    upgrade-cost: []
  2:
    interval-seconds: 15
    storage-limit: 128
    upgrade-money: 2500.0
    upgrade-cost:
      - material: COBBLESTONE
        amount: 64
```

!!! note "Cost direction"
    To upgrade from tier 1 to tier 2, configure the cost under tier `2`.

---

## Placement and Safety

Players place minions by right-clicking a block with a minion item.

Safe placement requires:

- Feet block is passable.
- Head block is passable.
- Block below is solid.
- Chunk is already loaded.

If the exact clicked location is invalid, IslandPortal scans nearby blocks up to `minions.place-radius`.

---

## Ownership and Anti-Dupe

Protections:

- Only the owner or `islandportal.admin` can manage a minion.
- Pickup collects storage first, removes the data entry, then returns the minion item.
- Pickup returns the minion item with `%actions%` preserved in item lore placeholders.
- GUI actions re-read the managed minion by id before mutating state.
- Production state is saved with dirty flags.
- Display entities are runtime-only and respawn from saved data.

---

## Example: Fuel-Required Diamond Minion

```yaml
minion-types:
  diamond:
    item:
      material: DIAMOND
      display-name: "<aqua>Diamond Minion"
      lore:
        - "<gray>Requires fuel to produce."
    display:
      name: "<aqua>Diamond Minion <white>T%tier%"
      head-material: DIAMOND_BLOCK
      small: true
      glowing: true
    limits:
      max-per-owner: 2
    requires-fuel: true
    offline-production-limit-seconds: 7200
    drops:
      items:
        - material: DIAMOND
          amount: 1
    tiers:
      1:
        interval-seconds: 300
        storage-limit: 16
        upgrade-cost: []
      2:
        interval-seconds: 240
        storage-limit: 32
        upgrade-cost:
          - material: DIAMOND
            amount: 16
```

---

## Extension Points

## Developer Events

IslandPortal exposes cancellable Bukkit events for third-party integrations:

| Event | Use |
|-------|-----|
| `PreMinionPlaceEvent` | Cancel placement or override the per-owner type limit. |
| `PreMinionPickupEvent` | Cancel pickup before storage is collected and the minion is removed. |
| `MinionChestLinkEvent` | Cancel or reject a linked chest, optionally with a message key. |

Managed minions also store `action-count`. The value increments when production operations complete, persists in playerdata, appears in `/ip minion inspect`, and can be used in minion item lore with `%actions%`.

The following integrations are intentionally left as extension points:

- Shop plugin buy-price lookups.
- PacketEvents virtual/client-only entities.
