# Changelog

## Unreleased

- Split the existing Shift interference hint into two goggle lines without changing its wording, reducing tooltip width at large GUI scales.
- Replace waterwheel/windmill capacity and goggle method overrides with scoped parent-class injections, allowing Create Picky Wheels' multipliers and tooltips to coexist.
- Clean up interference tracking through `SmartBlockEntity.setRemoved`, preserving Flowing Fluids' `invalidate` implementation and the existing chunk-unload behavior.
- Keep windmill tracking on the shared kinetic tick path even when another mod cancels the windmill's tick; assembled windmills with zero generated speed no longer count as active sources.
- Repair saved positions that no longer contain the corresponding generator when their chunks are loaded. Unloaded chunks are never force-loaded or discarded by this repair.
- Send empty interference-source lists so clients can clear stale highlights.
- Add isolated GameTests and pinned optional-mod fixtures. Client tooltip rendering and natural fluid/biome interactions still require in-game acceptance.
