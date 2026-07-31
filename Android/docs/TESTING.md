# Scout — Android testing

How to verify the Android port before a ride and before calling a build “good
enough.” Behaviour contracts live in the shared specs; this page is the
**test plan**.

| Related doc | Role |
| --- | --- |
| [SETUP.md](SETUP.md) | Tooling / build |
| [PERMISSIONS.md](PERMISSIONS.md) | System permission dialogs (location / Bluetooth / notifications) |
| [TECHNICAL.md](TECHNICAL.md) | Stack, battery §5, phases |
| [Product SPEC](../../docs/SPEC.md) | What must happen |
| [DATA-FORMAT](../../docs/DATA-FORMAT.md) | On-disk / parser rules |

---

## 1. Layers

| Layer | Where | When |
| --- | --- | --- |
| **A. JVM unit tests** | `:domain`, `:fit` | Every change to tagging / FIT / radar decode |
| **B. FIT viewer check** | Node + `fit-viewer.html` | After FIT encoder changes; after a real ride |
| **C. Device smoke** | Physical phone (or emulator for non-radar) | Before a field ride |
| **D. Field ride** | Bar-mount, real GPS (± radar) | Before calling v1 “done” |
| **E. Battery** | TECHNICAL §5 measure table | Before shipping a battery-sensitive build |

There are **no instrumented UI tests** yet. GPS, BLE/ANT+, FGS, and permissions
are covered by C–E.

---

## 2. Automated (A + B)

### 2.1 Unit tests

From `Android/`:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :domain:test :fit:test
```

```sh
export JAVA_HOME="…/Android Studio…/jbr…"
./gradlew :domain:test :fit:test
```

**Expect:** `BUILD SUCCESSFUL`, 0 failures.

What they cover today:

| Suite | Checks |
| --- | --- |
| `TagTalliesTest` | Undo windows, surface never undoes, END not tallied, resupply sum |
| `ScoutControllerTest` | Tag only when RUNNING, picker commit/timeout/back, FIFO double-tap |
| `VehicleCounterTest` | 1 s blip discarded, corroborated arrival, dropout clears pending |
| `VariaV1DecoderTest` | BLE V1 threats, speed/flag, FIT 255 vs empty-road 0 |
| `AntPlusBikeRadarDecoderTest` | Pages 48/49 range & closing speed |
| `ScoutFitWriterTest` | Header/CRC, scenario encode, flush file |

What they **do not** cover: location hardware, GATT/ANT Radio Service, Compose UI,
Settings share/delete, FGS, permissions dialogs.

### 2.2 Synthetic FIT → reference parser

After `:fit:test` (writes `fit/build/scout-scenario.fit`):

```sh
# from Scout repo root
node Android/tools/validate-scout-fit.mjs Garmin/tools/fit-viewer.html Android/fit/build/scout-scenario.fit
```

**Expect:** CRC ok, 60 records, tags/undo, surface segments, 2 vehicles.

Optional: open `Garmin/tools/fit-viewer.html` in a browser and drop the same file.

Shared CI-style parser suite (Garmin reference):

```sh
node Garmin/tools/test-fit-parser.mjs Garmin/tools/fit-viewer.html
```

---

## 3. Device smoke (C)

Install debug build (`:app:assembleDebug` or Studio **Run**).

Grant permissions as described in **[PERMISSIONS.md](PERMISSIONS.md)**
(**Precise** location, **Allow** notifications; **Allow** nearby devices if
testing BLE radar).

### 3.1 Without radar

| # | Step | Pass if |
| --- | --- | --- |
| 1 | Idle → **Start** | Red recording dot; FGS notification “recording” |
| 2 | Wait ~10 s outdoors / with fake GPS | Status shows a lat/lon (not stuck on `no fix` forever outdoors) |
| 3 | Tap DANGER, SCENERY, OTHER | Flash + tallies increment |
| 4 | Tap DANGER twice quickly | Tally undoes (SPEC undo) |
| 5 | CLOSURE → pick TODAY → wait 3 s | Beep/haptic; grid CLOSURE tally up; reopen picker → TODAY shows `1` |
| 5b | CLOSURE → MONTHS (later, outside undo) → reopen | MONTHS shows its own count; CLOSURE total = sum of durations |
| 6 | SURFACE → COBBLES → later END | Banner `surface open: COBBLES`; SURFACE tile lit with type; clears on END |
| 7 | RESUPPLY → leave 12 s | No resupply tag |
| 8 | **Pause** | Grey dot; GPS stops; taps do not enqueue |
| 9 | **Resume** → **Stop** | Notification gone; “saved scout-….fit” |
| 10 | **Share FIT** or Settings → ride → Share | Share sheet opens |
| 11 | Drop `.fit` on fit-viewer | Track + tags visible; radar coverage ~0 / all 255 |

### 3.2 Settings

| # | Step | Pass if |
| --- | --- | --- |
| 1 | Settings → mph | Strip uses mph (±3) when radar live later |
| 2 | Keep screen on **off** (default) | Screen can sleep while recording |
| 3 | Keep screen on **on** → Start | Screen stays awake while RUNNING only |
| 4 | Rides list | Past FITs listed without sharing first |
| 5 | Delete a ride | Gone from list; file removed |

### 3.3 Radar (optional hardware)

**BLE (Varia-family):** Settings → Pair / change radar → transport **BLE** → Scan → select → Done → Start.

**ANT+:** ANT Radio Service (or USB stick) installed → transport **ANT+** or **Auto** → Search ANT+ → Start.

| # | Step | Pass if |
| --- | --- | --- |
| 1 | Not paired / not TRACKING | Strip: `no radar` (never `0 cars`) |
| 2 | TRACKING, empty road | Strip live; FIT `radar_count=0`, near/speed 255 |
| 3 | Car overtakes | Count rises after corroboration; speed ±5 kph / ±3 mph |
| 4 | Pause | Radar disconnects; strip back to `no radar` |
| 5 | Viewer on ride file | `radar_*` populated while tracking; coverage & vehicle count sane |

Emulator: GPS fake ok; **do not** rely on emulator for BLE/ANT+.

---

## 4. Field ride (D)

Bar mount, real outdoor path, 15–60+ minutes.

Checklist:

- [ ] Tags land near the feature (GPS lag is normal; SPEC accepts picker delay)
- [ ] Pause mid-ride, resume — file continues, no invented radar zeros
- [ ] Stop → Settings → Share → open in fit-viewer
- [ ] With radar: strip matches “feel”; viewer vehicle count ≈ on-screen tally
- [ ] Without radar: all `radar_*` invalid (255); tagging still works

Acceptance (SPEC / TECHNICAL): file passes the same viewer expectations as a
Garmin Scout ride for tags, undo, surfaces, and radar coverage when radar was used.

---

## 5. Battery (E)

From [TECHNICAL.md](TECHNICAL.md) §5 — fill in on a real device:

| Test | Duration | Start SoC % | End SoC % | Notes |
| --- | --- | --- | --- | --- |
| Idle force-stop | 2 h | | | baseline |
| Ride **paused** | 2 h | | | GPS/radar must be off |
| Recording, no radar | 2 h | | | keep-screen-on **off** |
| Recording + radar | 2 h | | | transport: ANT+ / BLE |

Treat large idle/paused regressions as bugs.

Quick hygiene checks (no 2 h needed):

- [ ] Paused: location updates stopped (fix freezes / “no fix” path)
- [ ] Paused: radar disconnected
- [ ] Idle: no BLE scan loop (scan only on pair screen)
- [ ] Keep-screen-on off by default

---

## 6. Suggested order before a release

1. `:domain:test :fit:test`
2. `validate-scout-fit.mjs` on scenario FIT
3. Device smoke §3.1 + §3.2
4. Radar §3.3 if hardware available
5. One outdoor ride §4
6. Battery §5 when changing radios / GPS / wake behaviour

---

## 7. Capturing a failure

Include:

- App version (`versionName`, e.g. `0.5.0-p5`)
- Phone model + Android version
- Radar transport (none / BLE / ANT+) and device name
- Steps + approx time
- The `.fit` if recording-related (Settings → Share)
- Logcat slice if crash: `adb logcat --pid=$(adb shell pidof -s org.cyclingcommons.scout)`
