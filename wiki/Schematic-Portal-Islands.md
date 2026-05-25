# Schematic Portal Islands

## Default Schematic

The plugin bundles:

```yaml
schematics/spawn_portal_island.schem
```

It is copied to the plugin data folder on first startup.

For production use, place your own `.schem` file under `plugins/IslandPortal/schematics` and update `portal-island.schematic`.

The plugin also bundles a larger track-only sample:

```yaml
schematics/track_only_portal_island.schem
```

Use it with:

```yaml
portal:
  width: 2
  height: 3
  portal-material: NETHER_PORTAL
  track-only: true

portal-island:
  schematic: "schematics/track_only_portal_island.schem"
  portal-offset:
    x: 6
    y: 5
    z: 3
  vertical-align:
    enabled: true
    schematic-anchor-y: 4
    y-offset: 0
```

## Enabling Schematic Mode

```yaml
portal-island:
  enabled: true
  mode: SCHEMATIC
  schematic: "schematics/spawn_portal_island.schem"
  schematic-ignore-air: true
  vertical-align:
    enabled: true
    schematic-anchor-y: 0
    y-offset: 0
  portal-offset:
    x: 3
    y: 4
    z: 4
```

The schematic builds the island support structure. The managed portal frame and trigger blocks are still created by IslandPortal after the paste, which keeps default portal detection, access checks, cleanup, and persistence reliable.

## Full Custom Portal

If the schematic already contains the exact portal frame and portal blocks, enable `portal.track-only`:

```yaml
portal:
  width: 2
  height: 3
  frame-material: OBSIDIAN
  portal-material: NETHER_PORTAL
  replace-only-air: false
  track-only: true
```

In this mode IslandPortal does not replace schematic blocks. It only registers the frame area and inner trigger area as a managed portal.

If `portal-material` is `NETHER_PORTAL`, IslandPortal also indexes matching portal blocks from the pasted schematic as triggers. This prevents vanilla Nether travel and routes the player through the configured custom action.

## Fallback Behavior

If WorldEdit or FastAsyncWorldEdit is not installed, or the schematic cannot be loaded, IslandPortal falls back to generated platform mode.

## Placement Search

The search starts from `island-offset` plus any random distance. Set `random-distance.min`, `random-distance.max`, and `search-radius` to `0` when the schematic must paste at an exact custom location.

## Vertical Alignment

Use `vertical-align` when a custom schematic appears too high or too low compared to the main island.

- `enabled`: aligns the schematic to the island location Y.
- `schematic-anchor-y`: the relative Y level inside the schematic that should become level with the island.
- `y-offset`: final adjustment after alignment. Use a negative value to lower the pasted portal island.

Examples:

```yaml
vertical-align:
  enabled: true
  schematic-anchor-y: 0
  y-offset: -4
```

If the schematic origin is at the bottom and the walking surface is 8 blocks above the origin:

```yaml
vertical-align:
  enabled: true
  schematic-anchor-y: 8
  y-offset: 0
```
