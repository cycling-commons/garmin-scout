# Scout — Sharing & upload contract

Status: **normative for any Scout port that offers “share to an Atlas”**
Extends: [SPEC](SPEC.md) §2.4 (privacy) and §10 (data lifecycle) ·
Payload: [DATA-FORMAT](DATA-FORMAT.md) ·
Server implementers: [ATLAS-SERVER](ATLAS-SERVER.md) ·
Fork / instance setup: [CUSTOMIZATION](CUSTOMIZATION.md)

How a finished ride gets from the rider’s device into **their own account** on a
Cycling Commons–style Atlas, where they preview it and decide whether to submit
it for curation. Recording behaviour is unchanged and still needs no account and
no network.

This is a **two-sided contract**: the client half is implemented by each Scout
port, the server half by the Atlas instance. Nothing here is specific to
cyclingcommons.org — that is simply the default instance.

## 0. The client stays dumb

SPEC §2.1 already says the recorder logs raw observations and the **ingest layer
interprets them**. Sharing works the same way: a client authenticates, posts the
original FIT, and shows the rider a link. That is the whole job.

A Scout client therefore **does not**: parse its own ride files, extract or
summarise tags, trim or reshape the trace, validate that a ride is “worth”
uploading, render a preview, or track review state. Every one of those belongs
to the Atlas, where the rules can change once and apply to rides already
uploaded — instead of being reimplemented, slightly differently, in every port.

The practical test for anything proposed for this document: *could a head unit
with no parser and 20 kB of free memory do it?* If not, it belongs server-side.

---

## 1. Goals & non-goals

### Goals

- A rider can send a finished ride to their account with one deliberate tap.
- The rider **previews it on the Atlas before anything is submitted**, and a
  curator reviews it before anything is published.
- A different port, a fork, or a self-hosted instance changes **one URL** — no
  code, no rebuild of the protocol layer.
- Works on devices with no browser (head units), not just phones.
- Recording without an account stays a first-class path forever.

### Non-goals (v1)

- Curator tooling, in-app editing of recorded tags, map rendering in the app.
- Publishing to the public Atlas from the app (see §2 — the app *cannot*).
- Automatic or background upload of every ride.
- Multiple simultaneous accounts or instances.
- Downloading Atlas data into the app.

---

## 2. Trust model

The rider’s requirement — *preview before it goes to a curator* — is enforced by
the **token scope**, not by app politeness. A hostile fork holding a valid token
still cannot publish anything.

| Action | App | Rider on the web | Curator |
| --- | --- | --- | --- |
| Create a private draft from a ride file | ✅ | ✅ | — |
| Preview it, edit it, delete it | ❌ | ✅ | ✅ |
| **Submit a draft for review** | ❌ | ✅ | — |
| Publish / accept / reject | ❌ | ❌ | ✅ |
| Read review status | ❌ | ✅ | ✅ |
| See other riders’ rides | ❌ | ❌ | per instance policy |

The app holds exactly one scope: **`rides:create`**. It cannot read, edit,
delete, submit or publish anything, including its own uploads. Instances **must
not** issue a Scout client anything wider.

That is deliberately narrower than it needs to be. It means a leaked or
misused token can only add a private draft to the rider’s own account, and it
removes review state from the client entirely — no polling, no cached statuses
to go stale, no “published” badge that lies after a curator changes their mind.
The rider follows the link to see where a ride stands.

---

## 3. Lifecycle

```
   local .fit on the device
        │  rider taps “Share to <instance>”  (explicit, per ride)
        ▼
   draft            private to the rider; visible only in their account
        │  rider opens web_url, checks the map and the tags
        ▼
   in_review        rider submitted it — on the web, not from the app
        │  curator
        ├──────────► published     facts land in the Atlas (ODbL)
        └──────────► rejected      with a reason the rider can read
```

| Status | Set by | Visible where | Public |
| --- | --- | --- | --- |
| `draft` | app upload | app (once, in the upload response) and web | no |
| `in_review` | rider, on the web | web | no |
| `published` | curator | web + the Atlas | yes |
| `rejected` | curator | web | no |

The app’s job ends at `draft` + a link. Everything after that is the Atlas’s UI,
which means no port has to reimplement preview, submission, or curation — and no
port can drift from the others in how it displays them.

The only state a client keeps is **local**: whether *it* has uploaded a given
file yet (`not shared` / `queued` / `uploaded`, with the returned link). That is
bookkeeping about its own outbox, not a mirror of the Atlas.

Telling the rider what happened afterwards is the Atlas’s job too. Accepted,
rejected, more detail needed — those are announced wherever the instance already
talks to its riders, and a client neither polls for them nor relays them. So a
port needs no push infrastructure and no notification channel beyond the
recording one, and it cannot show a verdict that a curator has since changed.

---

## 4. Instance discovery — the one URL

A port is configured with **one instance base URL**, supplied at build time. The
client fetches everything else from the descriptor; nothing else is
instance-specific in source code.

### Android (and the reference pattern)

| File | In git? | Role |
| --- | --- | --- |
| `Android/.env.example` | yes | Template — `SCOUT_INSTANCE_URL`, `SCOUT_INSTANCE_NAME` |
| `Android/.env.dev.local` | **no** | Your instance copy |

If `.env.dev.local` exists it is bundled; otherwise `.env.example` is used.
Gradle writes `assets/instance.json`; `InstanceConfigLoader` reads it at runtime.
Forks copy the example and change one URL — no Kotlin edits.

Other ports should follow the same idea: one committed example, one gitignored
local override, one bundled value (`instance_url`). The key names are not
Android-specific.

Everything else is fetched at runtime:

```
GET {instance_url}/.well-known/scout-upload.json
```

```json
{
  "spec_version": 1,
  "name": "Cycling Commons Atlas",
  "terms_url": "https://cyclingcommons.org/terms",
  "privacy_url": "https://cyclingcommons.org/privacy",
  "contribution_license": "ODbL-1.0",

  "authorization_endpoint": "https://cyclingcommons.org/oauth/authorize",
  "device_authorization_endpoint": "https://cyclingcommons.org/oauth/device",
  "token_endpoint": "https://cyclingcommons.org/oauth/token",
  "revocation_endpoint": "https://cyclingcommons.org/oauth/revoke",
  "registration_endpoint": "https://cyclingcommons.org/oauth/register",
  "grant_types_supported": ["urn:ietf:params:oauth:grant-type:device_code",
                            "authorization_code", "refresh_token"],
  "scopes_supported": ["rides:create"],

  "upload_endpoint": "https://cyclingcommons.org/api/v1/rides",
  "accepted_media_types": ["application/vnd.ant.fit"],
  "max_upload_bytes": 26214400
}
```

Rules:

- **HTTPS only.** A plain-HTTP instance is refused, except `localhost` for
  development.
- Unknown fields are ignored, so the descriptor can grow without breaking old
  clients. `spec_version` is bumped only for breaking changes; a client that
  does not know the version refuses politely rather than guessing.
- Cache the descriptor (honour `ETag` / `Cache-Control`), re-fetch at most daily
  and whenever a request fails with `404`/`409`.
- **No hard-coded fallback endpoints.** If discovery fails, sharing is
  unavailable and says so. This is what keeps a fork honest: there is exactly
  one place the instance is named.

**Forking / self-hosting:** copy `Android/.env.example` to `.env.dev.local` (or
your port's equivalent), set `SCOUT_INSTANCE_URL`, and implement the seven
endpoints below. Nothing else in the client is instance-aware.

---

## 5. Authentication

OAuth 2.0 **public client**, no client secret ([RFC 8252][rfc8252]). Two grants;
the descriptor says which the instance supports.

| Grant | When | Required of instances |
| --- | --- | --- |
| **Device authorization** ([RFC 8628][rfc8628]) | Any device — including head units with no browser and no redirect scheme | **Yes** |
| **Authorization code + PKCE** ([RFC 7636][rfc7636]) | Phones and anything that can open a system browser and receive a redirect | Recommended |

Device grant is mandatory because it is the only one a Garmin or Karoo port can
realistically implement, and it is also the easiest to test. Phones should
prefer PKCE for a smoother flow and fall back to device grant.

- Refresh tokens are required; rotation is allowed and clients must handle it.
- **Client identity:** if the descriptor exposes `registration_endpoint`, a
  client with no `client_id` registers itself once ([RFC 7591][rfc7591]) and
  stores the result. Otherwise it uses a configured `client_id`. Forks must not
  reuse the official app’s id.
- **Token storage** must use the platform’s secure store (Android: Keystore-backed
  encrypted preferences; iOS: Keychain). Never plain preferences, never the
  ride file, never a log line.
- Sign-out revokes at `revocation_endpoint` *and* wipes local tokens. If
  revocation fails, still wipe locally and tell the rider to revoke on the web.

[rfc8252]: https://www.rfc-editor.org/rfc/rfc8252
[rfc8628]: https://www.rfc-editor.org/rfc/rfc8628
[rfc7636]: https://www.rfc-editor.org/rfc/rfc7636
[rfc7591]: https://www.rfc-editor.org/rfc/rfc7591

---

## 6. Upload

```
POST {upload_endpoint}
Authorization: Bearer <access token>
Content-Type: application/vnd.ant.fit
Idempotency-Key: <lowercase hex sha256 of the request body>
Scout-Client: android/1.1.0
```

Success:

```
201 Created
{ "id": "r_8fc21", "status": "draft",
  "web_url": "https://cyclingcommons.org/rides/r_8fc21" }
```

### 6.1 The body is the ride file, unmodified

One payload: **the original FIT, byte for byte**, exactly as the recorder wrote
it. Not re-encoded, not filtered, not summarised, not accompanied by a manifest
of what the client thinks is in it.

This is the point of §0. The Scout channels only survive in the original file
(DATA-FORMAT — re-encoded GPX/TCX copies drop developer fields), the server has
the reference parser already, and a client that never opens the file cannot
disagree with the parser about what a ride contains.

The only thing the client computes from the bytes is the SHA-256 for the
idempotency key, which is a hash, not an opinion.

Nothing else is uploaded: no device identifier, no location history, no other
files, no usage data.

### 6.2 Errors

| Status | Meaning | Client behaviour |
| --- | --- | --- |
| `401` | Token expired/revoked | Refresh once, then ask the rider to sign in again |
| `403` | Missing scope, or terms not accepted | Send the rider to the web; do not retry |
| `409` | Same `Idempotency-Key` already uploaded | Treat as success and return the existing `id` |
| `413` | Larger than `max_upload_bytes` | Explain; do not retry |
| `429` | Rate limited | Honour `Retry-After` |
| `5xx` | Instance trouble | Exponential backoff, cap the attempts, keep the ride |

Idempotency matters more than it looks: mobile uploads get killed mid-flight and
retried, and a duplicated ride is work for a human curator.

### 6.3 Same ride, two devices

The key covers **retries of the same bytes and nothing else**. A rider recording
one ride on both a head unit and a phone produces two different files, so they
get two drafts, and neither the client nor the Atlas tries to match them up.

That is a deliberate non-problem. A draft is not a submission: the rider authors
the ride in the Atlas before sending it to a curator, so throwing the spare away
is one action in a screen they are already looking at. Guessing which of two
traces of the same road is the good one is exactly the judgement call §0 keeps
out of the client — and here, out of the server too. The setup is also
uncommon: most riders run a head unit *or* a phone, and a phone on the bars is
mostly a city-riding pattern (very welcome, rarely alongside a computer).

---

## 7. Rider-facing rules

These are normative UX, not suggestions.

1. **Opt-in per ride.** No “upload all future rides” switch in v1, no background
   sweep, no upload the rider did not ask for by name.
2. **Say what leaves the device**, once, before the first upload: the complete
   ride file — the GPS trace including where the ride started and ended, the
   tags, and radar observations if radar was used.
3. **Never upload while recording.** Sharing is an after-the-ride action
   (SPEC §12.1 — the recording path pays for nothing else).
4. **Never delete the local file** because it uploaded. The rider owns it.
5. **Be honest about visibility.** After upload the app says the ride is a
   private draft and that nothing reaches the Atlas until the rider submits it
   and a curator accepts it. It links to `web_url`.
6. **Fail in plain language.** Offline, expired token, instance down — say
   which, keep the ride, offer to try again.

---

## 8. Queue & retry

- The queue is visible and cancellable; a ride waiting to upload is shown as
  waiting, not as sent.
- Constraints: network available, **not while a ride is recording**, and not on
  a metered connection unless the rider allowed it.
- Retry with backoff; the idempotency key makes retries free of duplicates.
- A queued upload must survive an app restart.

---

## 9. Privacy

SPEC §2.4 says the recorder never phones home. That stays true with one
narrow exception, which this document defines: **a ride the rider explicitly
shares**. Everything else is unchanged — no analytics, no crash
reporting, no network in the recording path.

- An uploaded ride is **private to the rider’s account** until they submit it
  and a curator publishes it. Only the curated facts become public Atlas data
  (ODbL); the trace is not published.
- The Atlas’s own principle is “open data about the world, never about you” — a
  raw FIT is the one artefact in this system that *is* about the rider, which is
  why it lands in a private draft that only they can see.
- **Privacy zones are the Atlas’s job, not the client’s.** A rider who does not
  want their front door in an uploaded trace sets a hidden radius once on their
  account, and the Atlas applies it to every ride from every device. Trimming in
  the client would mean six ports each implementing geodesy slightly
  differently, a rider whose zone works on the phone but not the head unit, and
  no way to fix a bad trim after the fact. It also cannot be undone: the server
  can always re-derive a trimmed view from the original, but a client that
  uploads a pre-trimmed file has destroyed the evidence a curator may need.

---

## 10. Porting checklist

A new port needs, and needs nothing else:

- [ ] Descriptor fetch + cache (§4)
- [ ] Device grant (§5); PKCE too if the platform has a browser
- [ ] Secure token storage + sign-out that revokes
- [ ] SHA-256 of a file
- [ ] One `POST` with bearer auth and an idempotency key (§6)
- [ ] The six rider-facing rules (§7)
- [ ] A visible queue if the platform can retry in the background (§8)

No FIT parser, no geo maths, no state machine mirroring the Atlas, no push
notifications, no duplicate detection. A port that
finds itself needing one of those has drifted from §0 — or the protocol has, and
this document should be fixed rather than worked around.

A fork or self-hosted instance needs: **one changed URL**, plus the seven
endpoints in §4.

---

## 11. Testing

`tools/mock-atlas.mjs` (to be written) serves the descriptor, both grants, and
the upload endpoint locally, so every port can be built and tested before any
real instance exists — and so CI never talks to production. It must be able to
simulate `401`, `409`, `413`, `429` and `5xx` on demand.

---

## 12. Open questions for the Atlas side

None of this exists on cyclingcommons.org yet (`/api` returns 404 today). Server
requirements and instance-policy choices are documented in
[ATLAS-SERVER.md](ATLAS-SERVER.md) §10. Keeping the client dumb means those
decisions can change without a client release.

---

## 13. Changes required in other docs

- **SPEC §2.4** — done: optional in-app sharing per this document; recorder never
  phones home while recording.
- **SPEC §10.2** — done: rider owns the file; sharing is explicit opt-in.
- **SPEC §11** — done: no mandatory network/account for recording; sharing
  removable without touching the recording path.
- **README** — add this doc to the shared-docs table when sharing ships.

---

## 14. Doc control

| Item | Value |
| --- | --- |
| Document | Sharing & upload contract |
| Owns | Discovery, auth grants, upload request/response, rider-facing sharing rules |
| Does not own | Tag codes and parser rules (DATA-FORMAT), recording behaviour (SPEC) |
| Applies to | Every port that offers sharing; ports may omit sharing entirely |
