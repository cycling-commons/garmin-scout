# Scout — Connect IQ store listing copy

Paste-ready text for the Garmin Connect IQ store (apps.garmin.com). The store
renders plain text with line breaks — the dashes below are literal bullets, safe
to paste as-is. The publisher shown next to the app is whatever your Connect IQ
developer/account name is; if you fork and publish your own build, swap the app
name and any wording below to match.

---

## App name
Scout

## App type
Data Field (full-screen, touch)

## One-line tagline (subtitle / social share)
Tag road conditions from the saddle — straight into your ride's FIT file.

## Short description (summary / first paragraph)
Scout turns your Edge into a one-tap field recorder. Tap the screen while you
ride to mark hazards, closures, rough surfaces, and resupply stops — and, with a
Varia-compatible radar paired, automatically count the vehicles that overtake
you. Every tag is written straight into your activity's FIT file, in an open,
documented format.

## Full description

Scout is a full-screen data field: swipe to its page and tap a coloured tile to
stamp what you see onto the current GPS point. No fiddling, no menus, eyes on the
road — each tap flashes, beeps, and shows a running tally so you know it landed.

WHAT YOU CAN TAG
- Danger — a bad corner, junction, loose dog, anything sketchy
- Closure — road shut or detour, with how long it's expected to last
- Surface — mark a rough or unusual stretch (asphalt, concrete, paving, sett,
  cobbles, gravel, dirt, sand — aligned to OpenStreetMap surface values) with a
  start and an END, so it's recorded as a segment, not just a point
- Resupply — water, food, or a bike-repair/pump stop
- Scenery — a view worth remembering
- Other — everything else

Picked the wrong option? Just tap the right one — a pick stays open for three
seconds so you can correct it before it commits, and a quick double-tap undoes a
tag entirely. Only your final choice is ever written.

AUTOMATIC VEHICLE COUNTING (optional)
Pair a Garmin Varia or compatible ANT+ radar and Scout logs, every second, how
many vehicles are behind you, how close the nearest is, and the closing speed of
each pass — the overtaking data that matters for cyclist safety. No radar?
Everything else works exactly the same; the radar features simply stay idle.

WHERE YOUR DATA GOES
Everything is recorded into your ride's FIT file as standard developer fields —
no separate app, no account on the device, no upload. Your tags travel with your
activity, and they're yours: open the file in any tool that reads FIT developer
fields, or in Scout's own browser viewer, which draws every tag and surface
stretch on your track. The field codes and the reference parser are open source
(MIT), so any platform you choose can ingest the data.

SETUP
Add Scout as the ONLY field on one of your Edge's data pages (it owns the whole
screen for tapping). Swipe to that page whenever you want to tag something.

PRIVACY
Scout writes only to your own FIT file and reads a paired radar. It has no
network access and sends nothing anywhere — no account, no telemetry, no
sign-in. Nothing leaves your device unless you choose to share the file.

## Compatible devices
Touch-screen Edge units: Edge 1030 / 1030 Plus / 1040 / 1050, Edge 830 / 840 /
850, Edge 820, Edge Explore / Explore 2 (auto-populated from the manifest).

## What's New — v1.0.0
First release.
- One-tap tagging: danger, closure (with duration), surface (8 types), resupply,
  scenery, other
- Surface stretches: mark a rough section with a start type and an END; the FIT
  viewer joins them into segments drawn along your track
- Correction window on the two-tap pickers, plus double-tap undo
- Per-tile ride tally and tap-tone confirmation
- Optional Varia-compatible radar: vehicle count, nearest range, and per-pass closing speed
- Everything logged to the activity's FIT file in an open, documented format

## Permissions (shown to users — explain them)
- ANT / ANT+ : to read a paired Varia-compatible radar (vehicle count, range,
  closing speed). Optional — Scout runs fine with no radar.
- FIT Contributor : to write your tags and the radar readings into the activity's
  FIT file. This is how Scout records anything at all.

## Suggested tags / keywords
cycling, bike, gravel, touring, bikepacking, safety, Varia, radar, road
conditions, surface, OpenStreetMap, data field

## Screenshot shot-list (store needs at least one)
1. The main grid (DANGER / CLOSURE / SURFACE / RESUPPLY / SCENERY / OTHER) with a
   couple of tallies showing ("DANGER 2").
2. The surface picker (ASPHALT … SAND) mid-selection, one tile lit.
3. The FIT inspector's map view of a finished ride, tags plotted on the track.
   (from tools/fit-viewer.html — good for showing what the data becomes)
