<p align="center"><img src="src/main/resources/createkineticinterference.png" alt="Create: Kinetic Interference icon" width="180"></p>

# Create: Kinetic Interference

*Give your generators some room.*

**English** | [简体中文](README_zh-CN.md)

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-5C9E31)
![Loader](https://img.shields.io/badge/Loader-NeoForge-E58B32)
![Create](https://img.shields.io/badge/Create-6.0.10-D9A441)
![License](https://img.shields.io/badge/License-MIT-3B82F6)

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-kinetic-interference) · [Source](https://github.com/JasdewStarfield/CreateKineticInterference) · [Issues](https://github.com/JasdewStarfield/CreateKineticInterference/issues) · [Changelog](CHANGELOG.md)

**Create: Kinetic Interference (CKI)** gives Create windmills and waterwheels a shared local supply of stress capacity. Low-demand generators retain full output; dense arrays gradually approach the capacity of the area they cover. Spreading generators over more land makes room for more power.

## Features

- Windmills compete for wind supply. Small and large waterwheels share water supply, in proportion to their raw output. The two resources are independent.
- Generators at the same horizontal position share supply across heights and kinetic networks. Supply changes smoothly as you move across the world.
- Rivers support larger waterwheel arrays; mountain and ocean biomes support larger windmill arrays. Biome conditions are sampled at a fixed height, default Y=64.
- Engineer's Goggles show supply efficiency, with raw SU in gray parentheses beside the output, including pending allocation. Sneaking adds resource conditions, competitor counts and estimated unloaded sources.
- Optional highlights outline up to 64 nearby competitors. Gameplay calculation includes every competitor.
- Existing worlds retain the legacy counting model until the administrator selects the density model and restarts.

Running generators also show local power conditions relative to the baseline, such as “1.5× baseline”. Higher values mean more shared local power. This comparison and supply utilization are hidden while the generator is stopped.

## Requirements and installation

This README describes the current source. Released downloads may precede the **Unreleased** changes in the [changelog](CHANGELOG.md).

| Item | Requirement |
| --- | --- |
| Minecraft | `1.21.1` |
| Loader | NeoForge `21.1.219` or later for Minecraft 1.21.1 |
| Java | `21` |
| Install on | Client and server |
| Create | Current build: `6.0.10`; declared range: `[6.0.10,6.1.0)` |

Install matching CKI versions on client and server, together with Create and its dependencies. Place the JARs in each instance's `mods/` folder. Create Picky Wheels and Flowing Fluids are optional.

## Quick start

1. Create a test world with CKI and Create, using their default settings.
2. Build two working small waterwheels within 32 horizontal blocks of one another. Each should supply its own rotation; they can belong to separate kinetic networks.
3. Wear Engineer's Goggles and look at a wheel. Wait for the pending supply allocation to finish, then add more wheels in the same area and compare total SU.

A small array keeps most of its raw capacity. Larger, more concentrated arrays lose more efficiency. Moving generators farther apart or extending the array into a new area increases the supply it can use. CKI adjusts stress capacity and leaves the generator's rotation speed to Create.

To show competitors, enable `visuals.enableDebugHighlights` in the client config, restart the client, then sneak (default: `Shift`) and right-click a generator while wearing goggles. Highlights last 3 seconds by default.

### Supply and unloaded sources

Density mode uses a horizontal collection circle for each generator. Overlapping circles compete for the same sustainable supply; with the default 16-block collection radius, their centers can compete up to 32 blocks apart. Demand includes the generator's own raw SU, so a very large single generator can also reach the area's limit. Biome supply cannot raise a generator above its raw capacity.

An unloaded source keeps its last known demand. Old coordinate-only records receive a configured estimate during migration, and the estimate is replaced when the source loads. Destroyed or replaced sources are removed after verification in a loaded chunk. These queries do not load or generate chunks.

## Configuration

Enter a world once to generate the files. Paths below are relative to the game instance or dedicated-server directory.

| File | Purpose |
| --- | --- |
| `config/createkineticinterference-server.toml` | Server-controlled gameplay rules |
| `config/createkineticinterference-client.toml` | Highlights on this client |

An existing `serverconfig/createkineticinterference-server.toml` inside the world overrides the instance config. In singleplayer this is usually under `saves/<world>/`; on a dedicated server it is under the directory selected by `level-name`. Stop the world/server before editing gameplay settings, then restart it. Multiplayer clients use the server's rules.

### Density settings

Configuration paths below combine the TOML section and key. Density mode uses Euclidean XZ distance.

| Path | Default | Effect |
| --- | --- | --- |
| `calculationModel` | `AUTO` | Use the world's saved choice; new worlds select DENSITY, detected old worlds/configs select LEGACY |
| `density.water.collectionRadius` | `16` | Water collection radius in blocks |
| `density.wind.collectionRadius` | `16` | Wind collection radius in blocks |
| `density.water.referenceCapacitySU` | `4096` | Ordinary-biome reference water supply within a collection circle |
| `density.wind.referenceCapacitySU` | `6144` | Ordinary-biome reference wind supply within a collection circle |
| `density.water.profile` | `createkineticinterference:water` | Water biome rules |
| `density.wind.profile` | `createkineticinterference:wind` | Wind biome rules |
| `density.softCapPower` | `8` | Saturation knee sharpness: higher values delay reductions until closer to capacity, range 2–8 |
| `density.integrationStep` | `2` | Integration spacing; at most one quarter of each collection radius |
| `density.environmentGridStep` | `4` | Environment sampling spacing |
| `density.biomeBlendRadius` | `8` | Biome smoothing radius |
| `density.biomeSampleY` | `64` | Fixed sampling height, clamped to the dimension's build limits |
| `density.recheckInterval` | `40` | Capability recheck interval in ticks |
| `density.workBudgetMs` | `1.25` | Target calculation work per tick; large rebuilds show pending status |
| `density.water.legacyUnloadedPotentialSU` | `256` | Estimated demand for unloaded old waterwheel records |
| `density.wind.legacyUnloadedPotentialSU` | `4096` | Estimated demand for unloaded old windmill records |

Built-in abundant-biome rules multiply supply by 2. Water uses the common river biome tag. Wind uses the common ocean, mountain and hill biome tags, including modded biomes registered in these tags. All sources in the same XZ column use the same sampling height.

### Existing worlds and model changes

Existing density configs retain their values. To adopt the new balance, stop the world, set `density.softCapPower` to `8`, water `referenceCapacitySU` to `4096`, and wind to `6144`, then restart. Built-in preferred biomes provide twice the ordinary supply; custom datapacks retain their own rules.

For coincident sources in a uniform ordinary biome, demand below about 75% of circle capacity stays at full output, then efficiency falls quickly near capacity. One 4096 SU windmill can run at full output; two achieve about 69%. In a uniform preferred biome, both can run at full output. Layout, biome boundaries and collection coverage affect the result.

1. Back up the world and its active CKI server configuration.
2. Load production areas to validate generators and replace old coordinate-only estimates where possible.
3. Stop the world/server, set `calculationModel = "DENSITY"` in the active config, then restart.
4. Inspect generator SU and rebuild overloaded networks as needed. The density model can change an existing factory's capacity.

Set `calculationModel = "LEGACY"` and restart to return to counting mode in this version. `AUTO` keeps the saved world choice. Restore the matching world/config backup when returning to an older JAR.

In LEGACY mode, the existing `general.windmill` and `general.waterwheel` radius, factor and distance settings apply. Default radius is 32, wind factor 0.2 and water factor 0.1:

```text
Efficiency = 1 / (1 + nearby same-type sources × factor)
```

One nearby waterwheel gives approximately 90.9% efficiency. Legacy distance modes are `EUCLIDEAN_2D`, `EUCLIDEAN_3D`, `MANHATTAN_2D` and `MANHATTAN_3D`; both groups default to `EUCLIDEAN_2D`. Windmills use `general.windmill.checkInterval`, default 40 ticks.

### Datapack profiles

Add a JSON resource to an existing Minecraft 1.21.1 datapack, for example `data/your_pack/cki_density_profiles/water.json`:

```json
{
  "schema_version": 1,
  "resource_type": "water",
  "default_multiplier": 1.0,
  "rules": [
    { "biome_tag": "createkineticinterference:water_abundant", "priority": 100, "multiplier": 2.0 }
  ],
  "dimension_multipliers": { "minecraft:the_nether": 0.5 }
}
```

Select `your_pack:water` in `density.water.profile` and restart. Subsequent edits to that profile and its biome tags can be applied with `/reload`. The highest-priority matching rule applies once. Equal-priority overlapping rules, missing required tags, invalid numbers or missing selected profiles reject the new ruleset and retain the previous complete ruleset. The first startup falls back to built-in rules and logs the problem.

Extend `data/createkineticinterference/tags/worldgen/biome/water_abundant.json` or `wind_abundant.json` in your datapack to include modded biomes. Optional biome members can use `{"id":"your_mod:biome","required":false}`.

### Diagnostics and client settings

These commands require operator permission level 2 and are read-only:

```text
/cki density inspect <pos>
/cki density sample <water|wind> <center> <radius> <step>
/cki density stats
```

`inspect` shows raw and allocated SU, local supply and loaded/snapshot/estimated competitor counts. `stats` includes pending age, queue delay and calculation work. `sample` exports supply, demand and fulfillment to CSV under the server's `cki-diagnostics/` directory; range and work limits apply.

| Client setting | Default |
| --- | --- |
| `visuals.enableDebugHighlights` | `false` |
| `visuals.debugHighlightsDuration` | `3000` ms |

## Compatibility

Supported sources are Create windmills, small waterwheels and large waterwheels. Picky Wheels' supported capacity multipliers contribute to raw demand; its tooltip additions and Flowing Fluids' removal behavior continue through their own hooks. Follow Picky Wheels' Flowing Fluids settings when combining their water-source requirements. Addons that replace the shared Create capacity or lifecycle paths can require a dedicated adapter.

## Building from source

Use Java 21 in this version repository:

```powershell
.\gradlew.bat build --no-configuration-cache --no-daemon --console=plain
```

On Linux/macOS use `bash ./gradlew` with the same arguments. The JAR is generated in `build/libs/`.

## Feedback

Report issues through [Issues](https://github.com/JasdewStarfield/CreateKineticInterference/issues), including Minecraft/NeoForge/Create/CKI and addon versions, singleplayer or server setup, reproduction steps, active config and logs. Include screenshots for tooltip or highlight issues.

## License and credits

Code is licensed under **MIT**; see [LICENSE](LICENSE). Author: Jasdew Starfield. Built on [Create](https://www.curseforge.com/minecraft/mc-mods/create) and [NeoForge](https://neoforged.net/).
