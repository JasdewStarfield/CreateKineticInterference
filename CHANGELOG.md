# Changelog

**English** | [简体中文](CHANGELOG_zh-CN.md)

## 2.0 — 2026-10-05

### Added

- Windmills and waterwheels now share local power supply across heights and kinetic networks. Spreading generators over more land provides more power; wind and water supply remain independent.
- Preferred biomes provide twice the ordinary supply. Biome rules inherit common river, ocean, mountain and hill tags to include modded biomes, and can be customized with datapacks.
- Goggles show raw capacity in gray parentheses beside output, supply utilization and local power conditions relative to the baseline. Sneaking shows detailed conditions and competing sources; operating information is hidden while a generator is stopped.
- Added operator commands to inspect generators, export samples and check pending updates, plus datapack rule reloads with `/reload`.

### Changed

- Low-demand generators retain full output. Efficiency falls rapidly near local capacity; default ordinary-biome reference supply is 4096 SU for water and 6144 SU for wind.
- New worlds use the density model. Existing worlds retain LEGACY until the active server configuration is changed and the world restarted. Unloaded generators continue to compete using their last known demand; old records use marked estimates.
- Limited new or increasing generator output until supply allocation completes. Competitor highlights show up to 64 sources and clear on disconnect or dimension changes.
- Updated to Create 6.0.10; declared compatibility is `>=6.0.10, <6.1.0`. Requires NeoForge 21.1.219 or later.

### Fixed

- Fixed a client crash from an empty goggles tooltip when looking at a stopped waterwheel.

Existing density configurations keep their values. To adopt the new balance, set `density.softCapPower` to `8`, water `referenceCapacitySU` to `4096`, and wind `referenceCapacitySU` to `6144`, then restart the world.

## 1.1 — 2026-10-04

### Changed

- Split the existing sneak hint into two Engineer's Goggles lines so it no longer stretches the tooltip at large GUI scales.
- Reworked capacity, tooltip, tick and removal hooks around Create's shared block-entity paths, allowing other addons to compose their behavior with CKI.
- Expanded and standardized the English and Simplified Chinese README files with matching installation, configuration, compatibility and troubleshooting information.

### Fixed

- Preserved Create Picky Wheels' waterwheel stress multipliers and its waterwheel and windmill tooltip additions.
- Preserved Flowing Fluids' waterwheel invalidation behavior while still removing destroyed CKI sources.
- Kept windmill interference tracking active when another addon cancels the windmill-specific tick path; assembled windmills that generate no speed no longer count as active sources.
- Removed stale saved positions when their chunks are loaded and no matching generator remains, without force-loading unloaded chunks or discarding their records.
- Synchronized empty interference-source lists so clients clear stale source highlights after the final source is removed.

### Validation

- Added isolated GameTests for capacity scaling, removal and chunk-unload behavior, saved-data repair, synchronization and windmill tracking.
- Added a pinned compatibility-fixture script for Create Picky Wheels and Flowing Fluids. Visual tooltip behavior and natural fluid or biome interactions still require in-game checks.

## 1.0 — 2026-01-18

- Initial release.
