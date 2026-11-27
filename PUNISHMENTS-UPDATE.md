# CWanticheat punishment configuration update

Only Russian (`ru.yml`) and English (`en.yml`) locale files are retained.

- `AutoSwapA` and `GhostHandA` each have their own dedicated punishment group.
- `BaritoneA` is grouped with the existing `Baritone` check.
- `VoidClip` has its own punishment group.
- `StrafeA` and `StrafeB` are grouped under `Strafe`.
- The existing movement, combat, inventory, and packet checks remain grouped by their current families.

The build validates the new check classes and adds their default configuration before packaging the Bukkit/Paper plugin. The RU and EN punishment files contain 155 unique checks across 39 groups.
