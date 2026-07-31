# Scout — Android

Phone port of Scout.

## Quick start

1. Follow **[docs/SETUP.md](docs/SETUP.md)** (Android Studio + SDK + Studio JBR).
2. Open the `Android/` folder in Android Studio.
3. Run the `app` configuration.

## Contracts

| Doc | Role |
| --- | --- |
| [Product spec](../docs/SPEC.md) | Behaviour, timings, radar policy, battery principles |
| [Data format](../docs/DATA-FORMAT.md) | On-disk channels & parser rules |
| [Technical platform spec](docs/TECHNICAL.md) | Android stack, modules, sensors, FIT, phases |
| [Setup](docs/SETUP.md) | Install Android Studio, SDK, run on device/emulator |
| [Permissions](docs/PERMISSIONS.md) | What the system permission dialogs mean |
| [Testing](docs/TESTING.md) | Unit, FIT, device, field, and battery test plan |

## Layout

| Module | Role |
| --- | --- |
| `domain/` | Pure Kotlin: codes, queue, undo tallies, pickers, radar decode / vehicle mirror |
| `fit/` | Original FIT encoder (SPEC sample fields only) |
| `app/` | Compose UI, GPS, BLE/ANT+ radar, FGS, settings |

## License

[MIT](../LICENSE) © BikeCoders.

## Status

- [x] Shared product + data specs
- [x] Android technical + setup docs
- [x] P0/P1 — ride session, tag grid/pickers, FGS, domain tests
- [x] P2 — GPS + FIT writer
- [x] P3 — BLE radar
- [x] P4 — ANT+ + battery hygiene / measure checklist
- [x] P5 — settings / export polish
