# IslandPortal

IslandPortal is a Paper/Folia island portal plugin for Skyblock servers. It creates managed custom portals, stores portal ownership and access policy data, and supports BentoBox, SuperiorSkyblock2, and Skyllia integrations.

## Features

- Folia-aware scheduling through a platform compatibility layer.
- Managed Nether-portal-looking custom portals without vanilla Nether travel leakage.
- Configurable portal types, menu items, access policies, command metadata, cooldowns, autosave interval, and integration toggles.
- In-memory portal state with periodic and shutdown persistence to `playerdata`.
- Optional integrations with BentoBox, SuperiorSkyblock2, and Skyllia.

## Requirements

- Java 25
- Paper or Folia compatible with the configured Paper API version
- Optional: BentoBox, SuperiorSkyblock2, or Skyllia

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

## License

MIT License. See `LICENSE`.
