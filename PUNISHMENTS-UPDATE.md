# CWanticheat punishment configuration update

These 11 locale files replace the matching files inside `CWanticheat-source.zip`.
They are grouped by the registered `@CheckData` names from the source archive.

- `NoFall` is a standalone group, separate from `GroundSpoof`.
- `Timer` contains `Timer`, `TimerLimit`, `TickTimer`, `NegativeTimer`, and `VehicleTimer`.
- `BadPackets` contains the 25 actual checks `BadPacketsA`–`BadPacketsZ` (this version has no `BadPacketsT`).
- Other lettered families (such as `Sprint`, `PacketOrder`, `Crash`, `Chat`, `Elytra`, `MultiActions`, `Inventory`, `Vehicle`, `Break`, and `Place`) list every registered subtype.
- `InvalidInteractCursor`, `InvalidInteractTarget`, and `SelfInteract` are kept in their own `Interaction` group.

The configuration contains all 137 registered checks exactly once in each locale.
