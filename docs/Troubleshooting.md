# Troubleshooting

If something is not working as expected, check the sections below.

---

## Portal Island Does Not Spawn

If a new island is created but the portal island does not appear:

1. Set `debug: true` in `config.yml` and restart.
2. Ensure WorldEdit or FastAsyncWorldEdit is installed if the portal type uses `mode: SCHEMATIC`.
3. Verify the `.schem` file exists under `plugins/IslandPortal/schematics/`.
4. Check whether the candidate area has enough air clearance.
5. Increase `creation-delay-ticks`, `creation-retry-attempts`, and `creation-retry-delay-ticks` if the skyblock plugin pastes islands slowly.

---

## Portal Does Not Teleport

Check:

1. `action.mode` is set to `TELEPORT` in `portals.yml`.
2. `action.target.world` exists and is loaded.
3. The player has the required permission.
4. The player passes the portal access policy.
5. The portal is managed and tracked.

---

## Vanilla Nether Travel Happens

If a custom portal sends players to the Nether:

1. Increase `runtime.vanilla-portal-cooldown-ticks`.
2. Confirm the portal is managed by using `/ip remove` near it as an admin.
3. Check `runtime.portal-near-scan` if the server reports portal events from shifted positions.

---

## NPC Does Not Spawn

Check:

1. `island-npcs.enabled` is `true`.
2. The NPC type exists in `npcs.yml`.
3. `default-on-island` is `true` for automatic island NPCs.
4. Unlock requirements are satisfied.
5. `spawn-search` can find a safe stand location.
6. The target world exists and the chunk can load.

---

## NPC Spawns in the Wrong Place

Check:

1. `island-offset` is relative to the island location/home provided by the skyblock plugin.
2. `spawn-search.horizontal-radius` is not too large.
3. `spawn-search.vertical-radius` is not too large.
4. Your island schematic does not have unexpected blocks around the desired NPC location.

!!! note "Safe spawn behavior"
    If the exact offset is blocked, IslandNPC intentionally picks the nearest safe stand location it can find. This is expected behavior.

---

## NPC Does Not Move

Check:

1. `movement.enabled` is `true`.
2. `movement.radius` is greater than `0`.
3. There are safe blocks inside the movement radius.
4. `movement.interval-ticks` is not extremely high.
5. `island-npcs.movement-interval-ticks` is not extremely high.

---

## NPC Keeps Returning to Spawn

This means IslandNPC detected the NPC outside its allowed movement area or another plugin moved the entity.

Fix:

- Reduce external plugin interference.
- Increase `movement.radius` only if the island has enough safe walkable space.
- Keep NPCs away from portals, void edges, and moving platforms.

---

## NPC Dies or Disappears

IslandNPC should respawn it automatically.

Check:

1. `island-npcs.respawn-check-ticks` is not too high.
2. `island-npcs.respawn-delay-ticks` is not too high.
3. The NPC data still exists in `playerdata/`.
4. The world is loaded.

---

## Folia Region Warnings

If you see region ownership warnings:

1. Update IslandPortal to the latest build.
2. Keep portal schematics compact.
3. Keep NPC movement radius modest.
4. Avoid using other plugins that teleport or mutate IslandPortal-managed entities from the wrong thread.
