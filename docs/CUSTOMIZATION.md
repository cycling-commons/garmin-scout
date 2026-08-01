# Customizing a Scout build

How forks, regional instances, and self-hosters tailor Scout **without forking
recording semantics**. Product behaviour and on-disk format stay in [SPEC.md](SPEC.md)
and [DATA-FORMAT.md](DATA-FORMAT.md); this page is only **branding, copy, and
which Atlas URL a build talks to**.

---

## Quick reference (Android)

| What | Committed template | Your copy (gitignored) | Rebuild needed? |
| --- | --- | --- | --- |
| In-app help text & links | `Android/help/help.example.json` | `Android/help/help.json` | yes |
| Legal / safety overlay | `Android/help/legal.example.json` (empty) | `Android/help/legal.json` | yes |
| Brand logos (Scout lockup, CC wordmark) | — | `Brand/Scout-logo-white.svg` + exported `.webp` copies | yes |
| Atlas instance URL & label | `Android/.env.example` | `Android/.env.dev.local` | yes |
| App package / Play listing | — | change `applicationId`, resources, store assets | yes (+ code) |

Both override files are listed in the repo root [`.gitignore`](../.gitignore).

---

## 1. In-app help

Rider help is a single JSON file bundled at build time. **No Kotlin changes**
unless you extend the schema.

### Setup

```powershell
cd Android/help
Copy-Item help.example.json help.json
# Edit help.json — sections, paragraphs, links
```

```sh
cd Android/help
cp help.example.json help.json
# edit help.json
```

Rebuild the app (`assembleDebug` / release). Gradle task `prepareHelpContent`
bundles `help.json` if it exists, otherwise `help.example.json`, into
`assets/help.json`.

### Schema

```json
{
  "title": "How Scout works",
  "sections": [
    {
      "heading": "Section title",
      "body": ["One paragraph per string."],
      "links": [{ "label": "Link text", "url": "https://…" }]
    }
  ]
}
```

- `links` is optional on any section.
- Links open in the **system browser** (`HelpScreen` → `onOpenLink`).
- Shown from **Settings → About → How Scout works** and the **?** icon on the ride screen.

### Typical sections for an instance build

| Section | Purpose |
| --- | --- |
| Recording / Tagging / Radar / Your ride file | Generic Scout behaviour (edit lightly) |
| **Your project** (e.g. Cycling Commons) | What optional sharing means; link to your site |
| **Source code** | GitHub (or fork) repository link |
| **Sharing** (optional) | Explain Atlas draft flow; link to your Atlas home |

Legal / safety / policy copy lives in **`legal.json`** (gitignored), not in the
committed help example. See [`Android/docs/LEGAL.md`](../Android/docs/LEGAL.md).

Full detail: [`Android/help/README.md`](../Android/help/README.md).

---

## 2. Brand logos

Source artwork lives in `Brand/` (gitignored). Gradle task `prepareBrandAssets`
overrides bundled drawables when these files exist:

| Brand file | Bundled as | Used for |
| --- | --- | --- |
| `Scout-logo-white.svg` | `assets/scout-logo-white.svg` | Welcome screen — copied verbatim from `Brand/` (text as outlines) |
| `logo-cycling-commons.webp` | `logo_cycling_commons.webp` | Intro “powering” line + help CC section |

The Scout welcome lockup stays as `Scout-logo-white.svg` with **text converted to
outlines** in Affinity (or equivalent) — do not rasterize it.

---

## 3. Atlas instance URL (sharing)

When a port implements [SHARING.md](SHARING.md), each build is wired to **one**
Atlas base URL at compile time. OAuth and upload endpoints are **not** hard-coded —
the app fetches them from:

```
GET {instance_url}/.well-known/scout-upload.json
```

### Setup

```powershell
cd Android
Copy-Item .env.example .env.dev.local
# Set SCOUT_INSTANCE_URL and SCOUT_INSTANCE_NAME
```

```sh
cd Android
cp .env.example .env.dev.local
# edit SCOUT_INSTANCE_URL and SCOUT_INSTANCE_NAME
```

| Variable | Role |
| --- | --- |
| `SCOUT_INSTANCE_URL` | Base URL (no trailing slash), e.g. `https://cyclingcommons.org` |
| `SCOUT_INSTANCE_NAME` | Label in UI, e.g. `Cycling Commons` — not shown as raw URL to riders |

Gradle `prepareInstanceConfig` writes `assets/instance.json`; runtime loader:
`InstanceConfigLoader` (`Android/app/.../instance/InstanceConfig.kt`).

**Recording does not use this file.** No network or account is required to ride.

What the server must implement: [ATLAS-SERVER.md](ATLAS-SERVER.md).

---

## 4. What still requires code or store changes

| Change | Where |
| --- | --- |
| App name, icon, notification icon | `Android/app/src/main/res/` |
| Application id / package name | `app/build.gradle.kts` `applicationId` |
| Brand colours (optional) | `ui/theme/Theme.kt` |
| Translated UI strings | `res/values/strings.xml` (+ `values-xx`) |
| Garmin Connect IQ store identity | [Garmin/docs/PUBLISHING.md](../Garmin/docs/PUBLISHING.md) — **new app id per fork** |

Help and instance URL are the main **no-code** customisation paths on Android.

---

## 5. Other platforms

| Platform | Instance / help pattern today |
| --- | --- |
| **Android** | `help.json` + `.env.dev.local` (this doc) |
| **Garmin** | No bundled help JSON yet; store fork rules in `Garmin/docs/PUBLISHING.md` |
| **Karoo / iPhone** | Not started — should follow the same idea: one committed example, one gitignored override, one bundled config |

---

## 6. What not to customise locally

Without updating the shared specs (and all ports), do **not** change:

- Tag codes, undo timings, surface semantics → [SPEC.md](SPEC.md), [DATA-FORMAT.md](DATA-FORMAT.md)
- FIT developer field layout
- “Client stays dumb” upload contract → [SHARING.md](SHARING.md)

Platform-specific deltas belong in that platform’s docs with a short pointer in the
root spec (see README **Doc rule**).

---

## 7. See also

| Doc | Role |
| --- | --- |
| [Android/README.md](../Android/README.md) | Android contracts table |
| [Android/docs/TECHNICAL.md §8.3–8.4](../Android/docs/TECHNICAL.md) | Help + instance build pipeline |
| [SHARING.md](SHARING.md) | Client/server upload contract |
| [ATLAS-SERVER.md](ATLAS-SERVER.md) | What your Atlas backend must implement |
