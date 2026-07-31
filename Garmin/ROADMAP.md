# Scout — Roadmap

Post-v1.0 ideas, specced enough to pick up later. Nothing here is built yet.

> **Shipped in v1.0** — surface stretches (begin/END segments). See
> [Surface stretches](../docs/DATA-FORMAT.md#surface-stretches).

## Show the open surface on the device

**Now normative** — [SPEC §7.1](../docs/SPEC.md#71-open-stretch-indicator-all-ports).
Android shows a banner + lit SURFACE tile; Garmin still needs the same reminder
(e.g. strip: `cobbles ↑`) when a stretch is open.

## Internationalisation (i18n) — if a non-English audience shows up

English-only is fine for launch. To localise later:
1. Move the hardcoded labels out of `source/ScoutView.mc` (tile labels, "no radar",
   "CLOSED FOR?", "WHAT KIND?", "kph"/"mph", "±5 kph") into
   `resources/strings/strings.xml` as `@Strings` ids. **This refactor is the real
   cost** — do it first.
2. Add `resources-<lang>/strings/strings.xml` per language (e.g. `resources-dut`)
   and list the language in the manifest.
3. The **store listing** localises independently in the portal — a Dutch
   description needs no code change and can happen any time.
