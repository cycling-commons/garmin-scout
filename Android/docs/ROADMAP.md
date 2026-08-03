# Scout Android — Roadmap

Post–P6 ideas, specced enough to pick up later. Nothing here is built yet unless
noted elsewhere.

| Related | Role |
| --- | --- |
| [TECHNICAL.md](TECHNICAL.md) | Shipped stack and phases |
| [../Garmin/ROADMAP.md](../Garmin/ROADMAP.md) | Connect IQ port ideas |
| [../../docs/SHARING.md](../../docs/SHARING.md) | Atlas upload (specified, not shipped) |

---

## Recover an interrupted ride on relaunch

**Status:** Shipped (Android). Persists ride state + partial FIT path; offers
**Resume ride** or **Discard** on cold start. See `RideRecoveryStore`,
`RecoveryScreen`, and `ScoutFitWriter.resumeAppend`.

**Problem:** If the app process is killed while recording (swipe away, force
stop, OOM), the partial FIT on disk is flushed only up to the last periodic
write (~30 s of samples at 1 Hz). The in-memory session is lost; on relaunch
Scout shows idle and the rider must start a new ride.

**Goal:** Detect an unfinished session on cold start (open FIT + persisted ride
state), offer **Resume ride** or **Discard**, and reconnect the writer to the
existing file.

**Sketch:**

- Persist minimal state: `timer`, file path, elapsed offset, controller queue /
  open surface, ride start time.
- On launch, if state says RUNNING/PAUSED and the file exists → show recovery UI
  before the ride screen.
- `RideFitSession` must support append to an existing file (or salvage partial
  file and continue in a new segment — pick one).
- Force-stop / user discard clears state and leaves or deletes the partial file
  (rider choice).

**Non-goals for v1:** Cloud sync of partial rides; merging two partial files.

---

## In-app Atlas upload

See [SHARING.md](../../docs/SHARING.md) and [ATLAS-SERVER.md](../../docs/ATLAS-SERVER.md).

---

## Open-surface strip reminder

Normative in [SPEC §7.1](../../docs/SPEC.md); Android has a banner — align with
Garmin strip copy when that port catches up.

---

## Kotlin Multiplatform

Extract `domain/` + `fit/` for a future iOS UI (TECHNICAL §13).
