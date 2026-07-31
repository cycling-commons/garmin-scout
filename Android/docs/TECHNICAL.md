# Scout — Android technical platform spec

Status: **normative for the Android port**  
Implements: [Product SPEC](../../docs/SPEC.md) · [DATA-FORMAT](../../docs/DATA-FORMAT.md)  
Battery policy: SPEC §12.1 (repeated here only where Android APIs apply)

This document says *how* Android Scout is built. Behaviour and on-disk codes
stay in the root docs — do not fork them here. For installing tools, see
**[SETUP.md](SETUP.md)**.

---

## 1. Goals & non-goals

### Goals

- Full Scout tagging + optional radar parity with Garmin v1.0 behaviour.
- Multi-hour rides with **battery use as low as practical** on a phone.
- Original **FIT** output readable by `Garmin/tools/fit-viewer.html` / Atlas ingest.
- Works with **no radar**; radar via **ANT+ if the device has it, else BLE**.
- No account, no telemetry, no required network.

### Non-goals (v1)

- iOS build (no Mac in the workflow yet) — leave `iPhone/` stubbed.
- Kotlin Multiplatform extraction (may come later; keep domain code separable).
- Maps, cloud sync, in-app Atlas upload.
- Supporting button-only / non-touch primary UI.
- Perfect traffic volume / oncoming detection.

---

## 2. Stack

| Piece | Choice | Why |
| --- | --- | --- |
| Language | Kotlin (AGP 9 built-in + Compose compiler plugin) | Android default; clear path to shared KMP later |
| UI | Jetpack Compose + Material 3 | Touch-first grid; low UI complexity |
| Min SDK | **26 (Android 8.0)** | BLE + foreground services without ancient edge cases; still wide device reach |
| Target / compile SDK | **37** | Matches current Studio SDK; AGP 9.3 max |
| Gradle / AGP | **Gradle 9.6+ / AGP 9.3** | Latest Studio JBR is a **supported** Gradle JDK |
| JDK | **Latest Studio JBR (25+; 26 when available)** | Runtime + toolchain + bytecode major version |
| Architecture | Single-activity Compose app | Simple navigation |
| Async | Kotlin coroutines + `Flow` | Sampling, sensors, writers |
| DI | Manual / small factory first; Hilt only if it earns its keep | Keep cold start light |
| FIT | `:fit` minimal original-FIT encoder (no Garmin SDK) | SPEC §4.2 fields + Scout channels only |
| Tests | JVM unit tests for domain; instrumented later for location/BLE | Domain must match parser rules |

**Not used in v1:** React Native, Flutter, Cordova/Capacitor, heavy analytics SDKs.

---

## 3. Module layout

```
Android/
  docs/TECHNICAL.md          ← this file
  app/                       ← Compose UI, permissions, location + BLE/ANT+ adapters, settings
  domain/                    ← pure Kotlin: codes, queue, undo tallies, radar decode / counters
  fit/                       ← FIT encode/flush (JVM; no Android deps)
  tools/validate-scout-fit.mjs
```

Rules:

- `domain/` and `fit/` have **no Android framework imports** — easiest to test and to share later.
- UI talks to a ride façade (`RideViewModel`), not to BLE/ANT+ directly.
- Sensor adapters normalize to the SPEC radar model (`TRACKING` + targets[]).
- Location + BLE/ANT+ live under `app/.../sensors` (recording/FGS under `app/.../recording`).
- BLE Varia-family: service `6a4e3200`, V1 notify `6a4e3203` (community protocol). V2/`6a4e3204` later if needed.
- BLE Magene L508-family: service `8ce5cc01`, unlock+notify on `8ce5cc02` (`57 09 01`).
- ANT+ bike radar: AntLib (`android_antlib_4-16-0.aar`) + ANT Radio Service; device type 40, pages 48/49. Requires ANT Radio Service (or USB ANT stick). PluginLib has no radar plugin — raw channel + decoder.

---

## 4. Runtime architecture

```
┌─────────────────────────────────────────────┐
│  Compose UI (grid / pickers / strip / HUD)  │
└─────────────────────┬───────────────────────┘
                      │ intents (tap, start/pause/stop, pair)
                      ▼
┌─────────────────────────────────────────────┐
│  RideSession                                │
│  timer state · tag queue · live tallies     │
│  mirrors SPEC undo / vehicle display rules  │
└──────┬───────────────────────────┬──────────┘
       │ ~1 Hz tick while RUNNING  │
       ▼                           ▼
┌──────────────┐            ┌─────────────────┐
│ LocationSrc  │            │ RadarSession    │
│ fix + speed  │            │ ANT+ or BLE     │
└──────┬───────┘            └────────┬────────┘
       │                             │
       ▼                             ▼
┌─────────────────────────────────────────────┐
│  SampleAssembler → FitWriter (buffered)     │
└─────────────────────────────────────────────┘
```

### Recording states (map to SPEC §4.1)

| App state | GPS | Radar | FIT | Wake / screen |
| --- | --- | --- | --- | --- |
| Idle (no ride) | Off | Off | — | Normal |
| Recording RUNNING | On (~1 Hz) | On if enabled+paired | Append samples | Foreground service; screen-on only if user enabled |
| Recording PAUSED | Off or passive | Disconnect / no scan | Flush; no samples | Service may stay for “ride open” but radios down |
| Recording STOPPED | Off | Off | Final flush + close file | Tear down service |

Taps while paused/stopped may animate in UI but **must not** enqueue tags into the file (SPEC).

---

## 5. Battery (Android mapping of SPEC §12.1)

### Must

1. **No high-accuracy GPS outside RUNNING.**
2. **No BLE scan loop and no ANT+/BLE radar connection outside RUNNING** (except an explicit, user-started pairing screen).
3. **Use a foreground service only while a ride is open** (RUNNING or briefly PAUSED if required for notification continuity). Stop it on STOPPED / dismiss.
4. **Prefer `FusedLocationProviderClient`** with the lowest priority that still yields usable tag coordinates and speed while RUNNING. Do not also poll GNSS + network “for redundancy.”
5. **Radar:** prefer ANT+ APIs / USB accessories when present; else BLE GATT connection + notifications. After pair, **connect — do not continuous-scan** during the ride.
6. **Buffer FIT records**; flush on pause, stop, and periodically (e.g. every N seconds or N records), not necessarily every sample if unsafe only on crash — balance durability vs flash wear (flush at least on pause/stop and on a short interval).
7. **Keep-screen-on is a setting** (default **off**). When off, system timeout applies; tagging page should still be one tap from the notification.
8. **No network** in the recording path. No ads, no analytics SDKs.

### Should

- Use `PRIORITY_BALANCED_POWER_ACCURACY` if field tests show tags still land well enough; fall back to `PRIORITY_HIGH_ACCURACY` only if required.
- Request location interval ~1000 ms, min update distance 0 while RUNNING (Cadence matters more than distance for Scout channels).
- On PAUSED: `removeLocationUpdates` immediately; close radar GATT / release ANT+ channel.
- Avoid perpetual `PARTIAL_WAKE_LOCK`; rely on foreground service + location callbacks. If a wake lock is unavoidable, hold only while RUNNING.
- Dark-friendly UI; do not force max brightness.

### Measure

Before calling v1 “done,” run at least:

- 2+ hour recording, **no radar**, keep-screen-on **off**
- 2+ hour recording, **BLE or ANT+ radar connected**, keep-screen-on **off**
- Compare idle drain: app force-stopped vs ride **paused** (radios must be down)

Treat large idle / paused regressions as bugs. Field log template:

| Test | Duration | Start SoC % | End SoC % | Notes |
| --- | --- | --- | --- | --- |
| Idle force-stop | 2 h | | | baseline |
| Ride paused | 2 h | | | GPS/radar off |
| Recording, no radar | 2 h | | | |
| Recording + radar | 2 h | | | transport: ANT+ / BLE |

P4 battery hygiene shipped: GPS-only while RUNNING; radar connect only while RUNNING (or pair screen); disconnect on pause/stop; no BLE scan during ride; FIT flush on pause/stop + every 30 records.

---

## 6. Location

- Permission: `ACCESS_FINE_LOCATION` (+ `ACCESS_COARSE` as appropriate).  
  Background: use foreground service with `location` type on API 29+ / 34+ as required; request background location **only if** product later needs recording with UI dismissed — **v1 may require the ride notification + app-visible session** to avoid background-location Play friction. Prefer: recording continues with FGS when app is backgrounded, without separate “all the time” permission if Play policy allows for the FGS path.
- While RUNNING, assemble one Scout sample per tick with: timestamp, lat/lon (if available), speed, drained tag (or zeros), radar fields.
- If no fix yet: still write Scout channels; position may be invalid/omitted per FIT rules — do not invent coordinates.
- Rider speed for the strip: from location speed when available (SPEC display = closing + rider).

---

## 7. Radar

### API shape (normalize both transports)

```text
RadarSession
  state: ABSENT | SCANNING | CONNECTING | TRACKING | DISCONNECTED
  targets: List<{ occupied, rangeM, closingSpeedMps }>  // ≤ 8 occupied slots
```

Only `TRACKING` writes real `radar_*` values; everything else → `255` (SPEC §8).

### Transport selection

1. If ANT+ bike radar is usable on this device → ANT+ adapter.  
2. Else → BLE adapter (Varia-compatible / documented BLE radar).  
3. User may pick preferred device in settings; persist id + transport.

### Pairing UX

- Dedicated settings / “Pair radar” flow (scan only here).
- Persist bonded/preferred address.
- Clear copy for: no adapter, permission denied, not tracking, battery tip (“radar uses more power”).

### Live strip

Same corroboration rule as SPEC / Garmin `writeRadar` (pending rise, presence check). Domain module owns the math; UI only renders.

---

## 8. Tagging UI

- Full-screen (immersive enough for bar mount): 2-column grid + bottom radar strip + recording dot.
- Timings exact to SPEC §6 (`PICK_MS` 12s, `CORRECT_MS` 3s, undo 3s/6s, queue ≥ 16).
- Colours and labels per SPEC (English v1).
- Haptic via `HapticFeedback` / `Vibrator` when enabled; distinct pattern for undo; never block tagging if haptic fails.
- Hit-testing: strip taps resolve to the tile above (SPEC).

Do not add menus that steal focus mid-ride beyond the in-field pickers.

---

## 9. FIT output

- Write **original FIT** with developer fields:

  | id | name | type |
  | --- | --- | --- |
  | 0 | `poi_type` | uint8 |
  | 1 | `poi_detail` | uint8 |
  | 2 | `radar_count` | uint8 |
  | 3 | `radar_near` | uint8 |
  | 4 | `radar_speed` | uint8 |

- One record ≈ one second while RUNNING; invalid radar = 255.
- **Android record payload (SPEC §4.2 only — privacy):** `timestamp`, `position_lat`,
  `position_long`, `speed` (when available), plus the five Scout developer fields.
  No HR, cadence, altitude, device serials, or other extras.
- File location: app-private `files/rides/scout-….fit`. Settings lists history with **Share** / **Delete**; share sheet after Stop as well.
- Acceptance: drop output on `Garmin/tools/fit-viewer.html` and pass the same expectations as Garmin rides (tags, undo pairs, surfaces, radar coverage).

---

## 10. Permissions & Play considerations

User-facing explanations of the system dialogs:
**[PERMISSIONS.md](PERMISSIONS.md)**.

Declare only what we use:

- Location (**fine** / precise) + FGS location while recording — approximate alone is not enough for tags  
- Bluetooth Connect / Scan (API 31+; system copy often says “nearby devices”) for BLE radar  
- Notifications (API 33+) for the **recording** foreground-service notification only  
- ANT+ via ANT Radio Service when present (no extra Play “nearby” dialog for ANT itself)  
- Vibrate (optional)

Privacy copy: local recording only; no account; radar optional; no ads / analytics.

---

## 11. Implementation phases

| Phase | Deliverable | Status |
| --- | --- | --- |
| **P0** | App shell, permissions, RideSession start/pause/stop, FGS notification | Done |
| **P1** | Tag grid + pickers + queue + tallies + haptics (no radar) | Done |
| **P2** | Location sampling + FIT writer; viewer-validated file | Done |
| **P3** | BLE radar pair + TRACKING samples + strip | Done |
| **P4** | ANT+ path when hardware present; battery pass (measure §5) | Done |
| **P5** | Settings (units, keep-screen-on, preferred radar), export/share polish | Done |

Ship gating: P2 is already useful without radar; P3–P4 match Garmin’s optional radar story; P5 finishes v1 UX.

---

## 12. Testing

Full plan: **[TESTING.md](TESTING.md)** (unit, FIT viewer, device smoke, field ride, battery).

| Layer | What |
| --- | --- |
| Domain / fit unit tests | Undo, pickers, queue, vehicle corroboration, BLE/ANT+ decode, FIT CRC |
| FIT golden | `Android/tools/validate-scout-fit.mjs` + `Garmin/tools/fit-viewer.html` |
| Manual ride | Real device, bar mount, with/without radar, pause/resume |
| Battery | §5 measure checklist |

---

## 13. Out of scope / later

- Extract `domain/` + `fit/` to KMP for a future iOS SwiftUI UI.
- In-app Atlas upload.
- Open-surface strip indicator (root ROADMAP idea).
- Karoo-specific packaging (separate platform folder).

---

## 14. Doc control

| Item | Value |
| --- | --- |
| Document | Android technical platform spec |
| Owns | Stack, modules, Android battery mapping, phases |
| Does not own | `poi_*` codes, undo/surface/vehicle parser rules (root docs) |

When Android must diverge from SPEC behaviour, write the delta here and add a
one-line pointer in root `docs/SPEC.md` §13.
