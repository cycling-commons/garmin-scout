# Scout — Product Spec

Version: **1.0** (parity with Garmin Connect IQ Scout v1.0.0)  
Status: **normative for all ports** (Garmin, Android, Karoo, iPhone, …)

This document defines *what* Scout does and *how its data must behave*. It is
deliberately free of Connect IQ, Android, or iOS APIs. Platform ports implement
this contract; they do not invent parallel semantics.

The on-disk recording contract (FIT developer fields, codes, undo / surface /
vehicle rules) is **[DATA-FORMAT.md](DATA-FORMAT.md)**. Ports that cannot write
Garmin FIT must still emit an **equivalent record stream** that a conforming
parser can map 1:1 onto those fields.

**Where docs live:** shared specs stay in this `docs/` folder. Platform-only
deltas go under that platform’s folder, with a short mention here (or in
DATA-FORMAT) pointing at them.

---

## 1. Product summary

Scout is an in-ride companion for tagging road conditions and (optionally)
logging rear radar observations into the ride file.

- Tap coloured tiles while riding to stamp hazards, closures, surfaces,
  resupply, scenery, and other points onto the current GPS sample.
- With a compatible bike radar paired, every sample also carries what the radar
  saw that second. Distinct-vehicle counting is done by interpretation rules,
  not by inventing smarter on-device tracking.
- Built for the [Cycling Commons Atlas](https://cyclingcommons.org), but the app
  and its data format stand alone (MIT). No account, no telemetry, no forced
  upload.

**Non-goals (v1 parity):** accounts, cloud sync inside the app, maps while
riding, editing past tags mid-ride, exact closure end-dates on the bike,
oncoming/crossing traffic, button-only / non-touch primary tagging UI.

---

## 2. Design principles

### 2.1 Keep the recorder dumb

The device (phone, head unit, …) **logs raw observations**:

| Channel | What is written |
| --- | --- |
| Tag | At most one `(poi_type, poi_detail)` per sample |
| Radar | Raw `(radar_count, radar_near, radar_speed)` per sample, or “not tracking” |

All interpretation lives in the **parser / ingest layer** (and may be mirrored
on-device for live display only):

- Double-tap undo → `applyUndoRule`
- Surface begin/switch/END → `buildSurfaceSegments`
- Vehicle arrivals → `countVehicles`

Rules can change later and be re-applied to already-recorded rides without
updating every client.

### 2.2 Device and parser must agree

Anything the UI shows as a live tally (undo counts, car count, open-surface
hint later) **must** use the same rule as the reference parser in
[`Garmin/tools/fit-viewer.html`](../Garmin/tools/fit-viewer.html). Change both;
add a test.

### 2.3 Codes are append-only

`poi_type`, closure durations, surface types, and radar invalid markers are a
**stable contract**. Add new codes; never renumber or reuse.

### 2.4 Privacy

- Writes only to the rider’s own activity file (and local preferences such as
  paired radar id).
- No network required for core function.
- Upload / contribution to Cycling Commons (or anything else) is **opt-in and
  out of band** in v1; the recorder never phones home.
- Radar is optional: tagging works with no radar.

---

## 3. Roles & system context

```
┌──────────────────────────────┐
│  Scout client (any platform) │
│  - GPS + timer               │
│  - Tag UI                    │
│  - Radar adapter             │
│  - Activity writer           │
└──────────────┬───────────────┘
               │ record stream (~1 Hz)
               ▼
┌──────────────────────────────┐
│  Activity file (FIT or equiv)│
│  poi_* + radar_* per sample  │
└──────────────┬───────────────┘
               │ after ride
               ▼
┌──────────────────────────────┐
│  Parser / viewer / Atlas     │
│  undo · surfaces · vehicles  │
└──────────────────────────────┘
```

Optional accessories:

- **Bike radar** (Garmin Varia–compatible or equivalent) via the radar transport
  (see §8).

---

## 4. Activity & sampling model

### 4.1 Recording states

| Timer state | Behaviour |
| --- | --- |
| Running | Samples are written; taps enqueue tags that drain onto samples |
| Paused / stopped | UI may still flash, but **nothing is written** to the activity |

A **recording indicator** (e.g. red vs grey dot) must reflect this so the rider
knows whether taps will land in the file.

### 4.2 Sample cadence

- Target **~1 sample per second** while recording (same granularity as the
  Garmin data field’s `compute()`).
- Each sample carries: timestamp, position (lat/lon when available), speed
  (when available), and the five Scout channels below.
- **FIFO tag queue** (capacity ≥ 16): at most **one** queued tag is drained onto
  each sample. A single pending slot is forbidden — a fast double-tap would
  collapse into one record and silently break undo.

### 4.3 Scout channels on every sample

| Field | Type | Meaning |
| --- | --- | --- |
| `poi_type` | uint8 | 0 = no tag; else category (§5) |
| `poi_detail` | uint8 | Qualifier keyed by `poi_type`; else 0 |
| `radar_count` | uint8 | Simultaneous targets this second, or invalid |
| `radar_near` | uint8 | Nearest target range in metres, or invalid |
| `radar_speed` | uint8 | Closing speed of nearest target in kph, or invalid |

**Invalid marker:** `255` (uint8 FIT invalid). Real radar readings clamp to
`0..254`. **`255` means “radar not tracking” — never invent `0` for that case.**
Empty road while tracking = `radar_count = 0` with near/speed invalid as
appropriate.

Canonical on-disk encoding for Garmin ecosystems: Connect IQ / FIT developer
fields on the `record` message (ids 0–4, names as above). Other platforms must
preserve the same semantics even if the container differs, and should prefer
writing original FIT when feasible so existing ingest tools keep working.

---

## 5. Tag taxonomy

### 5.1 `poi_type` (append-only)

| Code | Name | Grid behaviour |
| --- | --- | --- |
| 0 | NONE | — |
| 1 | DANGER | Direct tag |
| 2 | SCENERY | Direct tag |
| 3 | WATER | Resupply leaf |
| 4 | OTHER | Direct tag |
| 5 | CLOSURE | Opens duration picker |
| 6 | SURFACE | Opens surface picker (segment channel) |
| 7 | FOOD | Resupply leaf |
| 8 | MECHANICAL | Resupply leaf (“REPAIR” on UI) |

UI-only codes (never written): `254` = RESUPPLY folder, `255` = BACK.

### 5.2 Closure duration (`poi_type == 5`) → `poi_detail`

| Code | Label |
| --- | --- |
| 0 | NONE (unused on a committed closure) |
| 1 | TODAY |
| 2 | DAYS |
| 3 | WEEKS |
| 4 | MONTHS |
| 5 | UNKNOWN |

Exact end dates are **not** entered on the bike; coarse duration + sample
timestamp hydrates a real range later in a web UI.

### 5.3 Surface type (`poi_type == 6`) → `poi_detail`

Aligned to OSM `surface=` values, smooth → rough:

| Code | Label | OSM alignment |
| --- | --- | --- |
| 0 | NONE / unspecified | — |
| 1 | ASPHALT | asphalt |
| 2 | CONCRETE | concrete |
| 3 | PAVING | paving_stones |
| 4 | SETT | sett |
| 5 | COBBLES | cobblestone |
| 6 | GRAVEL | gravel |
| 7 | DIRT | ground |
| 8 | SAND | sand |
| 9 | END | stretch ends; road back to normal |

Surface is a **segment channel**, not a point channel (see §7).

### 5.4 Resupply encoding

RESUPPLY is a **menu folder**, not a written code. Leaves are distinct
`poi_type`s with `poi_detail = 0`:

- WATER = 3  
- FOOD = 7  
- MECHANICAL = 8  

---

## 6. Interaction model (normative timings)

Primary UI is a **full-screen (or largest practical) touch grid**. Hit testing uses
the grid area only; a bottom radar strip is readout, not a dead zone that drops
taps — taps on the strip resolve to the tile above.

### 6.1 Main grid (2 columns)

```
┌──────────┬───────────┐
│  DANGER  │  CLOSURE  │
├──────────┼───────────┤
│ SURFACE  │ RESUPPLY  │
├──────────┼───────────┤
│  SCENERY │  OTHER    │
└──────────┴───────────┘
```

Tile colours (RGB, for visual parity):

| Tile | Colour |
| --- | --- |
| DANGER | `#D1421F` |
| CLOSURE | `#8E44AD` |
| SURFACE | `#8E5A2B` |
| RESUPPLY | `#1E7FC0` |
| SCENERY | `#2E8B57` |
| OTHER | `#B58900` |
| BACK (pickers) | `#444444` |

### 6.2 Direct tags (DANGER, SCENERY, OTHER)

1. Tap → enqueue `(type, detail=0)`, update tallies, haptic/tone confirm.
2. Tile stays lit for the **undo window** (3 s) as a “tap again to cancel” cue.
3. Same type again inside the window → both taps are written; live tally
   decrements (parser will cancel the pair). Distinct undo feedback (double
   pulse / reset tone).

### 6.3 Two-tap flows (CLOSURE, SURFACE, RESUPPLY)

Opening a picker does **not** write a tag yet.

| Constant | Value | Role |
| --- | --- | --- |
| `PICK_MS` | 12 000 | Auto-timeout with no pick |
| `CORRECT_MS` | 3 000 | After a pick, window to re-pick |
| `FLASH_MS` | 1 500 | Brief flash for undo or surface commit |
| `UNDO_MS` | 3 000 | Base undo window (display + parser) |
| `QUEUE_MAX` | 16 | Tag FIFO backstop |

**Pick held, not committed:** choosing a subitem lights that tile and starts
`CORRECT_MS`. Another subitem replaces the pending choice (only the last is
ever written). When `CORRECT_MS` elapses → commit type+detail, beep, return to
grid. BACK during the window → abort, no tag.

**Timeout with no pick:**

| Mode | On timeout |
| --- | --- |
| CLOSURE | Commit `CLOSURE` + `UNKNOWN` |
| SURFACE | Commit `SURFACE` + `NONE` (unspecified point) |
| RESUPPLY | Drop; return to grid with no tag |

Picker titles: CLOSURE → `CLOSED FOR?`; RESUPPLY → `WHAT KIND?`; SURFACE needs
no header (tiles name themselves).

### 6.4 Duration picker

`TODAY · DAYS · WEEKS · MONTHS · UNKNOWN · BACK`

### 6.5 Resupply picker

`WATER · FOOD · REPAIR · BACK`

### 6.6 Surface picker (5×2)

`ASPHALT · CONCRETE · PAVING · SETT · COBBLES · GRAVEL · DIRT · SAND · END · BACK`

### 6.7 Per-tile tallies

- Shown on the main grid when > 0 (`"DANGER 3"`); untouched tiles show no number.
- Counts mirror parser undo: same type within undo window annihilates for
  display; both taps still go to the file.
- RESUPPLY tile shows sum of WATER + FOOD + MECHANICAL.
- SURFACE tally counts only stretch **starts** (detail in `ASPHALT..SAND`), not
  END.

### 6.8 Undo windows (must match parser)

| Tag class | Window |
| --- | --- |
| Direct (DANGER, SCENERY, OTHER) | 3 s |
| Two-tap leaves (CLOSURE, WATER, FOOD, MECHANICAL) | 6 s |
| SURFACE | **Exempt** — second surface tag is a transition, never an undo |

### 6.9 Confirmation feedback

Eyes-on-road: short vibration if available and enabled; else tone if available
and enabled. Undo uses a distinct pattern. Failure to buzz/beep must not block
tagging.

---

## 7. Surface stretches

Surface is recorded as **transition points**; the parser joins them into runs.

| Event | Written |
| --- | --- |
| Pick a type | `poi_type=6`, `poi_detail=type` → stretch starts (or previous ends and new starts) |
| Pick END | `poi_type=6`, `poi_detail=9` → stretch ends; road untagged |
| Timeout / unspecified | `poi_type=6`, `poi_detail=0` |

Parser (`buildSurfaceSegments`): type opens; switch closes-and-opens; END
closes; unterminated stretch closes at ride end and is flagged. Accidental
start → END immediately (near-zero stretch). No double-tap undo on surface.

---

## 8. Radar

### 8.1 Purpose

Optional rear radar logging for crowd-sourced road-feel (overtaking volume and
closing speed). **Only vehicles behind / overtaking** — not oncoming or
crossing, not total road traffic volume.

### 8.2 Transport selection (normative for multi-platform)

Ports **must** obtain the same logical observations regardless of radio:

```
                    ┌─────────────────────┐
                    │  RadarSession API   │
                    │  state + targets[]  │
                    └──────────┬──────────┘
           ┌───────────────────┼───────────────────┐
           ▼                   ▼                   ▼
    Native ANT+          BLE bike radar      (future adapters)
    (if hardware         (if no native ANT+
     available)           or user prefers)
```

**Policy:**

1. If the platform/device has **native ANT+** (or a usable ANT+ adapter the
   product supports) **and** a compatible radar is available that way, use ANT+.
2. Otherwise use **Bluetooth LE** pairing/connection to a compatible radar
   (e.g. Varia models that expose BLE radar data).
3. Tagging must work if neither path is available (`radar_* = 255`).
4. The recorder never cares which transport was used — only the normalized
   `RadarSample` below.

Platforms may expose a settings UI to pick transport, forget a device, or
re-pair. Pairing UX is platform-specific; observation semantics are not.

### 8.3 Normalized radar model

**Device state** (map from transport-specific status):

| Logical state | Write radar fields? |
| --- | --- |
| TRACKING | Yes — real values (count may be 0) |
| Searching / connecting / closed / dead / absent | No — write invalid `255` |

**Targets** (up to 8 logical slots, matching ANT+ bike radar):

| Field | Meaning |
| --- | --- |
| occupied | Threat / presence above “no threat”; empty slots are not targets |
| range_m | Distance to target (metres) |
| speed_mps | **Closing** speed (target toward rider), not ground speed |

Occupied target count = number of occupied slots. Nearest = min `range_m` among
occupied; its closing speed becomes `radar_speed` (kph, clamped).

### 8.4 What is written vs what is displayed

**FIT / file (raw):**

- `radar_count` = occupied targets this second  
- `radar_near` = nearest range (m)  
- `radar_speed` = nearest **closing** speed (kph)  
- All `255` when not TRACKING  

**On-screen strip (derived, live):**

- Show `"no radar"` when not TRACKING (never show `0 cars` for that).
- When TRACKING: show corroborated vehicle tally + last car **ground** speed
  (closing + rider ground speed), unit-aware (kph / mph), with ±5 kph / ±3 mph
  tolerance note (radar quantisation ~3 m/s).
- Speed appears only after the two-read corroboration gate (same as counting).

### 8.5 Vehicle counting rule (device mirror + parser)

Shared rule — reference: `countVehicles` / `writeRadar` in the Garmin tree.

1. On an **increase** in simultaneous target count, hold the rise for one second
   (`pendingRise`); do not credit yet.
2. Next second: if **any** target is still present (`count > 0`), add
   `pendingRise` to the ride tally; if the road is empty, discard the rise
   (false blip).
3. Falling counts are the same vehicles finishing a pass, not new ones.
4. Corroborate on **presence**, not on the peak repeating (keeps convoy last-car).
5. On dropout from TRACKING, clear pending rise and previous count — do not
   credit across a gap.
6. Parser additionally drops an arrival in the final second of the file (nothing
   follows to confirm) and reports `coverage` = fraction of samples with valid
   radar.

**None of this mutates the file.** The file stays a second-by-second log; counting
is re-runnable interpretation.

### 8.6 Pairing requirements (BLE path)

When using Bluetooth:

- Discover / pair / reconnect to a user-selected radar peripheral.
- Persist identity of the preferred device across sessions.
- Request only the permissions needed for BLE scan/connect on that OS.
- Surface clear states: scanning, connecting, tracking, disconnected, no device.
- Losing BLE mid-ride → leave TRACKING → write `255`s; do not fabricate empty-road
  zeros.

When using native ANT+:

- Prefer the OS / stack’s standard bike-radar association if one exists.
- Same TRACKING vs not-TRACKING invalidation rules.

---

## 9. Live UI chrome (parity)

| Element | Spec |
| --- | --- |
| Recording dot | Top-right (or equivalent): red = timer running; grey = paused/stopped |
| Radar strip | Bottom of tagging surface; separator line; auto-shrink font to fit |
| Strip copy | `"no radar"` \| `"{n} cars"` optional `"{speed} ±5 kph"` / mph |
| Grid layout | 2 columns; rows = ceil(nTiles / 2); tiles fill grid height above strip |

Roadmap (not required for v1 parity): indicator of currently open surface
stretch on the strip.

---

## 10. Data lifecycle & interoperability

1. During ride: append samples to the activity container.
2. After ride: rider owns the file. Scout does not upload.
3. Inspect: reference viewer / parser (`fit-viewer.html` logic) or any tool that
   understands the Scout channels.
4. Contribute (optional): upload original file to a project that ingests Scout
   data (e.g. Cycling Commons). Prefer paths that preserve developer fields /
  Scout channels — re-encoded GPX/TCX/Strava copies typically **drop** them.

**Integrator rules** (unchanged from DATA-FORMAT):

- Consume original FIT (or documented equivalent), not stripped exports.
- Implement `applyUndoRule`, `buildSurfaceSegments`, `countVehicles`.
- Treat `radar_* == 255` as no coverage, not empty road.

---

## 11. Functional requirements checklist

### Must

- [ ] Full-screen (or dedicated) touch tagging UI with the six-tile grid and three
      pickers, timings, and colours in §6.
- [ ] ~1 Hz samples while recording; pause/stop writes nothing.
- [ ] FIFO tag queue; one tag per sample; queue capacity ≥ 16.
- [ ] Stable `poi_type` / `poi_detail` codes (§5).
- [ ] Surface as transitions + END; no surface double-tap undo.
- [ ] Double-tap undo semantics for point types; live tallies match parser.
- [ ] Haptic or tone confirmation; distinct undo feedback when possible.
- [ ] Recording indicator.
- [ ] Optional radar via **ANT+ if available, else BLE** (§8); normalized samples;
      invalid `255` when not tracking.
- [ ] Live car tally + ground speed using the shared corroboration rule.
- [ ] Activity export readable by the reference Scout parser (FIT preferred).
- [ ] Works fully without radar.
- [ ] No mandatory network / account for recording.

### Should

- [ ] Persist preferred radar device and transport preference.
- [ ] Unit-aware speed display from system locale / settings.
- [ ] Foreground-friendly ride mode (screen reachable without deep menus).

### Must not

- [ ] Write `0` radar count when radar is absent/disconnected.
- [ ] Collapse multiple taps into one sample.
- [ ] Renumber existing type/detail codes.
- [ ] Put undo / vehicle identity / surface joining solely on-device without a
      matching parser rule (display mirrors are OK; file stays raw).

---

## 12. Non-functional

| Area | Expectation |
| --- | --- |
| Eyes on road | Large hit targets; confirmation without looking |
| Reliability | Tagging survives radar failure; radar failure never corrupts tags |
| Battery | Prefer connection strategies that allow ride-length radar use |
| Localization | English labels acceptable for v1; strings should be externalizable |
| License | MIT, consistent with Garmin Scout |

---

## 13. Platform notes

Shared contract only here. Platform implementation notes and any **deltas** live
in that platform’s folder; keep a one-line pointer below when a delta exists.

| Platform | Folder | Notes / deltas |
| --- | --- | --- |
| Garmin Connect IQ | [`Garmin/`](../Garmin/) | Reference implementation. Data field + FitContributor; ANT+ via `Toybox.AntPlus.BikeRadar`; touch Edge only; picker pages are field repaints (no `pushView`). Publishing: [`Garmin/docs/PUBLISHING.md`](../Garmin/docs/PUBLISHING.md). |
| Android phone | [`Android/`](../Android/) | Standalone ride app. Radar: native ANT+ when present, else BLE (§8). Prefer FIT with the same developer field names/ids. *(no deltas yet)* |
| Hammerhead Karoo | [`Hammerhead-Karoo/`](../Hammerhead-Karoo/) | *(not started — no deltas yet)* |
| iPhone | [`iPhone/`](../iPhone/) | *(not started — no deltas yet)* |

### Shared test assets

Reuse / port:

- Code tables and timings from this spec
- Parser tests from [`Garmin/tools/test-fit-parser.mjs`](../Garmin/tools/test-fit-parser.mjs)
- Scenario FIT from [`Garmin/tools/make-test-fit.mjs`](../Garmin/tools/make-test-fit.mjs)

A port is “done” for v1 when its files pass the reference parser assertions for
tags, undo, surfaces, and radar coverage/counting.

---

## 14. Glossary

| Term | Meaning |
| --- | --- |
| Sample / record | One timed GPS (+ Scout channels) row ≈ 1 s |
| Direct tag | Commits on first tap |
| Two-tap / picker | Intermediate UI before commit |
| TRACKING | Radar session actively delivering target data |
| Closing speed | Relative speed of approach (car − bike), as reported by radar |
| Ground speed (display) | Closing + rider speed |
| Invalid / NA | uint8 `255` — no data, not zero |

---

## 15. Document control

| Item | Value |
| --- | --- |
| Spec version | 1.0 |
| Parity baseline | Garmin Scout 1.0.0 (2026-07-22) |
| Normative data format | [DATA-FORMAT.md](DATA-FORMAT.md) |
| Reference parser | [`Garmin/tools/fit-viewer.html`](../Garmin/tools/fit-viewer.html) (`===PARSER-*===` block) |

Changes that affect codes, timings, or interpretation rules require a spec
bump and coordinated parser/device updates.
