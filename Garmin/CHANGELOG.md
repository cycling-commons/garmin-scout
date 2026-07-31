# Changelog — Scout

Semantic versioning; this file is the source of truth for the version string
entered in the Connect IQ store at upload time.

## 1.0.0 — 2026-07-22 (first store release)
- One-tap tagging on a full-screen touch data field: **danger, closure** (with
  duration), **surface** (8 OSM-aligned types), **resupply** (water/food/repair),
  **scenery, other**.
- **Surface stretches**: a type starts a stretch, another type switches it, END
  closes it; the parser joins the transitions into segments and the viewer draws
  them as coloured lines along the track with measured lengths.
- Two-tap pickers with a 3 s correction window (re-pick to replace before it
  commits; only the final choice is written), plus double-tap undo — 3 s for
  direct tiles, 6 s for the two-tap tiles.
- Per-tile ride tally and a tap-tone confirmation (distinct tone on undo).
- Optional Varia-compatible radar: per-second vehicle count, nearest range, and
  per-pass speed (closing speed plus the rider's own speed, unit-aware, ±5 kph).
- Everything written to the FIT as developer fields, in an open, documented format.
- Compatible with touch Edge units (1030/1030 Plus/1040/1050, 830/840/850, 820,
  Explore/Explore 2).
- Companion FIT inspector (`tools/fit-viewer.html`) with a ride map of all tags.
