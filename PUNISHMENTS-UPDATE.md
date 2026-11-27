# CWanticheat punishment configuration update

These 11 locale files replace the matching files inside `CWanticheat-source.zip`.
They are grouped by the registered `@CheckData` names from the source archive plus the new checks added in this branch.

- `NoFallA` is included with `NoFall`; `GroundSpoof` remains a separate family.
- `NoSlowA` is included with `NoSlow`.
- `AimA` and `AimB` are included with the existing `Aim` family.
- `AirStuck`, `NoWebA`/`NoWebB`, and `KillAuraA`/`KillAuraB` have dedicated punishment groups.
- `Timer` contains `Timer`, `TimerLimit`, `TickTimer`, `NegativeTimer`, and `VehicleTimer`.
- `BadPackets` contains the 25 actual checks `BadPacketsA`–`BadPacketsZ` (this version has no `BadPacketsT`).
- Other lettered families list every registered subtype.
- `InvalidInteractCursor`, `InvalidInteractTarget`, and `SelfInteract` remain in their own `Interaction` group.

The configuration lists all 146 registered checks exactly once in each locale across 31 punishment groups.
