# Brand fallbacks (placeholders only)

These files are **generic stand-ins** so a clean checkout still builds. They are
**not** the Scout / BikeCoders / Cycling Commons logos.

| File | Role |
| --- | --- |
| `splash-icon.svg` (+ `.webp`) | API 31+ splash |
| `welcome-logo.svg` | Intro / welcome lockup |
| `instance-logo.webp` | “Powered by” / help instance mark |

## Your real artwork

Put overrides in gitignored **`Brand/`** at the repo root (same file names).
Gradle prefers `Brand/` over this folder. See [docs/CUSTOMIZATION.md](../../docs/CUSTOMIZATION.md).

## Forks / other instances

You **must not** ship BikeCoders or Cycling Commons marks in a fork or store
build. Replace these placeholders (and `Brand/` if you use it) with **your own**
artwork, and replace launcher / in-app vectors under `app/src/main/res/` as
needed.
