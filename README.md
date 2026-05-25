# IslandPortal

IslandPortal is a Paper/Folia island portal plugin for Skyblock servers. It creates managed custom portals, stores portal ownership and access policy data, and supports BentoBox, SuperiorSkyblock2, and Skyllia integrations.

## Features

- Folia-aware scheduling through a platform compatibility layer.
- Managed Nether-portal-looking custom portals without vanilla Nether travel leakage.
- Configurable portal types, menu items, access policies, command metadata, cooldowns, autosave interval, and integration toggles.
- In-memory portal state with periodic and shutdown persistence to `playerdata`.
- Optional integrations with BentoBox, SuperiorSkyblock2, and Skyllia.
- Optional WorldEdit/FastAsyncWorldEdit schematic portal islands with generated-platform fallback.
- GitHub Actions build validation and wiki markdown validation.

## Requirements

- Java 25
- Paper or Folia compatible with the configured Paper API version
- Optional: BentoBox, SuperiorSkyblock2, Skyllia, WorldEdit, or FastAsyncWorldEdit

## Build

```bash
./gradlew build
```

The shaded plugin jar is generated in `build/libs`.

## Configuration

The plugin writes these files on first start:

- `config.yml`
- `portals.yml`
- `menus.yml`
- `messages.yml`

Runtime tuning such as autosave interval, vanilla portal cooldown, portal lookup scan radius, command aliases, and integration toggles is in `config.yml`.

Default portal islands can be generated from simple materials or pasted from a WorldEdit/FAWE schematic. The plugin bundles sample schematics at `schematics/spawn_portal_island.schem` and `schematics/track_only_portal_island.schem`; production servers can place their own schematic under the plugin data folder and point `portal-island.schematic` to it. If WorldEdit/FAWE is missing or the schematic cannot be loaded, the plugin falls back to the generated platform mode.

## Documentation

Detailed documentation is maintained in the `wiki` folder and can be published to a GitHub wiki:

- Folia architecture
- Configuration
- Schematic portal islands
- Commands and permissions
- Storage and cleanup
- Troubleshooting

## License

MIT License. See `LICENSE`.
