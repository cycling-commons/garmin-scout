# In-app help content

Rider-facing help is bundled into the APK at build time. Forks customise it
without touching Kotlin.

## Files

| File | In git? | Purpose |
| --- | --- | --- |
| `help.example.json` | yes | Generic operational help (recording, tagging, …) |
| `help.json` | **no** | Your instance copy — copy the example and edit |
| `legal.example.json` | yes | Empty legal overlay (`sections: []`) |
| `legal.json` | **no** | Safety, liability, privacy/terms links — **your machine only** |

If `help.json` exists it is bundled as `help.json`; otherwise `help.example.json`.
Same for `legal.json` / `legal.example.json`. At runtime both are merged (legal
sections insert before **Source code**).

## Customise

```powershell
cd Android/help
Copy-Item help.example.json help.json
Copy-Item legal.example.json legal.json
# edit help.json — instance name, URLs, optional sharing section
# edit legal.json — safety / liability / policy links (not committed)
```

Rebuild the app. No code changes required unless you extend the schema (please
extend `HelpContent.kt` and the examples together).

## Schema

**help** (`help.example.json` / `help.json`):

```json
{
  "title": "How Scout works",
  "sections": [
    {
      "heading": "Section title",
      "body": ["One paragraph per string."],
      "image": "cycling_commons",
      "links": [{ "label": "Link text", "url": "https://…" }]
    }
  ]
}
```

**legal** (`legal.example.json` / `legal.json`) — sections only:

```json
{
  "sections": [
    {
      "heading": "Safety & responsibility",
      "body": ["Your paragraphs…"],
      "links": [{ "label": "Privacy policy", "url": "https://…" }]
    }
  ]
}
```

`links` and `image` are optional on any section. URLs open in the system browser.

## See also

- [docs/LEGAL.md](../docs/LEGAL.md) — legal overlay pattern (no policy text in git)
- [docs/CUSTOMIZATION.md](../../docs/CUSTOMIZATION.md) — full fork / instance guide
- Atlas URL: copy `../.env.example` to `../.env.dev.local`
