# CWanticheat punishment configuration update

The build keeps only the Russian (`ru`) and English (`en`) locale files. Other locale YAML files are removed from the packaged resources.

Renamed checks:
- `Simulation` is now displayed and punished as `Prediction`.
- `AntiKB` is now displayed and punished as `VelocityA` (the existing `Knockback` tuning section remains for compatibility).

New checks included in the punishment groups:
- `NoFallA` under `NoFall`.
- `AirStuck` and `AirStuckA` under `AirStuck`.
- `NoSlowA` under `NoSlow`.
- `AimA` and `AimB` under `Aim`.
- `NoWebA` and `NoWebB` under `NoWeb`.
- `KillAuraA` and `KillAuraB` under `KillAura`.
- `HighJumpA` under `HighJump`.
- `ScaffoldA` under `Scaffold`.
- `AutoSwapA` and `GhostHandA` under `Inventory`.
- Other registered checks remain grouped by their existing families.

The RU and EN punishment files list all 151 registered check names exactly once across 35 groups. The Actions workflow verifies the dedicated Bukkit/Paper plugin JAR and includes the modified source archive.
