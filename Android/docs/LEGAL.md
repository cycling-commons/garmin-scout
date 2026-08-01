# Scout Android — legal / safety help overlay

Instance-specific **safety, liability, privacy, and terms** copy is **not** in
git. Same pattern as `help.json` and `.env.dev.local`.

| File | In git? | Purpose |
| --- | --- | --- |
| `help/legal.example.json` | yes | Empty `sections` array — structure only |
| `help/legal.json` | **no** | Your wording and policy URLs |

## Setup

```powershell
cd Android/help
Copy-Item legal.example.json legal.json
# edit legal.json — add sections (e.g. Safety & responsibility, privacy links)
```

At runtime `HelpContent` loads `assets/help.json` and merges `assets/legal.json`
sections **before** any **Source code** section (otherwise appends at end).

If `legal.json` is missing, the build bundles `legal.example.json` (empty → no
extra sections).

## Schema

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

Operational help (recording, tagging, …) stays in `help.example.json` /
`help.json` — see [help/README.md](../help/README.md).

**Not legal advice.** Have a lawyer review text before Play Store submission.
