# Changelog

**English** | [简体中文](CHANGELOG_zh-CN.md)

## Unreleased

- Raw generator capacity now appears in gray parentheses beside the original output line. Preferred-biome rules inherit common river, ocean, mountain and hill tags for modded biomes.

- Low-demand generators retain full output, with a sharper reduction near local capacity. Ordinary water supply is now 4096 SU and wind supply 6144 SU; built-in preferred biomes provide twice the supply.

- Added continuous XZ supply sharing for windmills and waterwheels, with output-based competition, smooth biome conditions and configurable reference SU.
- New worlds use the density model. Existing worlds retain LEGACY until an administrator changes the active server config and restarts; unloaded old records use visible estimates.
- Added datapack biome profiles and atomic `/reload`, fixed-height environment sampling, and operator commands for source inspection, samples and pending work.
- Expanded goggles with actual / raw SU and resource conditions; capped highlight synchronization at 64 competitors and cleared highlights on disconnect or dimension changes.
- Preserved last known unloaded demand and limited new/increasing capability while an allocation batch is pending.
- Updated the development baseline to Create 6.0.10 and NeoForge 21.1.219.

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
