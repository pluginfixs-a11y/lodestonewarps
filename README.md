# Lodestone Warps

Server-side Fabric mod for Minecraft **26.2**. Link two lodestones with a recovery compass and get a two-way
teleport between them — with a warmup, a cooldown and penalties that grow with distance. Vanilla clients
connect without installing anything.

## How it works

1. Sneak-right-click a lodestone with a recovery compass. The compass is used up when the pair is completed
   (second lodestone), not when the first one is set.
2. Do the same on a second lodestone — the pair becomes a warp.
3. Right-click a linked lodestone: after a short warmup (moving cancels it) you are teleported to the pair.

The warmup only starts at **full health**. A player who is missing even half a heart does not warp: the mod
plays the cancel puff (smoke + extinguish sound) every few seconds instead. There is no text in this mod, so
that puff is the only feedback — heal up and it works.

Distances are limited (`min_distance` / `max_distance`, same dimension by default). The further the warp, the
longer the cooldown and the harsher the debuffs: weakness, then slowness, then mining fatigue; blindness comes
on top. Debuffs are reapplied every few ticks and (by default) cannot be milked away.

If the exit is blocked the mod clears space around it; if the exit is gone entirely the entrance smokes for a
second and then explodes (configurable). Exit chunks are preloaded so you do not land in unloaded terrain.

## Config

`config/lodestonewarps.json`, generated on first start. The interesting knobs:

| key | default | what |
|---|---|---|
| `max_distance` / `min_distance` | `5000` / `16` | allowed warp length |
| `same_dimension_only` | `true` | no cross-dimension warps |
| `creation_requires_sneak` | `true` | sneak to create |
| `consume_recovery_compass` | `true` | compass is used up |
| `warmup_seconds` | `5` | teleport warmup |
| `cooldown_seconds_per_500` | `60` | cooldown scaling |
| `weakness_seconds_per_1000` | `180` | debuff scaling |
| `weakness_amplifier` / `slowness_amplifier` / `mining_fatigue_amplifier` | `1` | debuff levels |
| `slowness_from_distance` / `mining_fatigue_from_distance` | `500` / `1500` | when the extra debuffs start |
| `blindness_seconds` | `5` | blindness after the jump |
| `blindness_removable_by_milk` | `true` | `false` — blindness is reapplied like weakness, milk does not help |
| `weakness_protected_from_milk` | `true` | `true` — weakness/slowness/fatigue are reapplied after milk |
| `apply_hp_penalty_after_teleport` | `false` | HP penalty on arrival (off: the debuffs are the penalty) |
| `exit_clear_*` | — | clearing space at the destination |
| `missing_exit_explosion` / `explosion_power` | `true` / `2.0` | what happens when the exit is destroyed |

Penalties are per session: they are not written to disk, so logging out clears the cooldown and the
debuffs — there is no `blindness_persist_after_logout`-style option because nothing implements it.

## Install

Fabric Loader ≥ 0.20.1 + Fabric API on the server, jar into `mods/`. Warp data is stored in `world/data`.

## Build

```
./gradlew build
```

## License

MIT.
