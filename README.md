# Scout

In-ride tagging for hazards, closures, surfaces, resupply, scenery — and optional
rear-radar vehicle logging — written into your ride file. Built for the
[Cycling Commons Atlas](https://cyclingcommons.org); MIT-licensed and usable
standalone.

This repo is the **multi-platform Scout tree**. Shared behaviour and the on-disk
contract live at the root; each platform folder holds that port’s code and any
deltas.

## Shared docs (normative)

| Doc | Role |
| --- | --- |
| **[docs/SPEC.md](docs/SPEC.md)** | Product behaviour: UI, timings, undo, surfaces, radar transport policy |
| **[docs/DATA-FORMAT.md](docs/DATA-FORMAT.md)** | On-disk channels, codes, parser rules (undo / surfaces / vehicles) |

Platform ports implement these. Do not invent parallel semantics.

**Doc rule:** general specs stay here. If a platform must diverge, document the
delta under that platform’s folder and add a **short pointer** in the relevant
root doc (SPEC or DATA-FORMAT).

## Platforms

| Folder | Status |
| --- | --- |
| [Garmin/](Garmin/) | Connect IQ data field (reference implementation, v1.0 shipped) |
| [Android/](Android/) | Phone app — **v1 phases P0–P5 done** · [setup](Android/docs/SETUP.md) · [permissions](Android/docs/PERMISSIONS.md) · [tech](Android/docs/TECHNICAL.md) · [testing](Android/docs/TESTING.md) |
| [Hammerhead-Karoo/](Hammerhead-Karoo/) | Karoo port — not started |
| [iPhone/](iPhone/) | iOS port — not started (needs a Mac) |

Radar pairing policy (all ports): **native ANT+ when available, otherwise
Bluetooth LE**. See [SPEC §8](docs/SPEC.md#8-radar).

## Reference tools

The FIT viewer and parser tests currently live with the Garmin tree (first
implementation):

- [`Garmin/tools/fit-viewer.html`](Garmin/tools/fit-viewer.html)
- [`Garmin/tools/test-fit-parser.mjs`](Garmin/tools/test-fit-parser.mjs)
- Android FIT smoke: [`Android/tools/validate-scout-fit.mjs`](Android/tools/validate-scout-fit.mjs)

```sh
node Garmin/tools/test-fit-parser.mjs Garmin/tools/fit-viewer.html
```

## License

[MIT](LICENSE) © BikeCoders (see each platform folder for its copy where applicable).
