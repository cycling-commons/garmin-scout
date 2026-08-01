# Scout Atlas — server requirements

Status: **normative for any backend that accepts Scout ride uploads**  
Audience: Atlas / ingest implementers (not app developers)  
Client contract: [SHARING.md](SHARING.md) · FIT payload: [DATA-FORMAT.md](DATA-FORMAT.md) ·
recording behaviour: [SPEC.md](SPEC.md)

A Scout client does almost nothing beyond OAuth and a single `POST` of the original
FIT file. **Everything else is server-side**: discovery, auth, parsing, preview,
privacy trimming, submission, curation, publication, and telling the rider what
happened. This document is what that server must provide.

Nothing here is specific to cyclingcommons.org — that is simply the reference
instance name in examples.

---

## 1. Responsibilities

| Layer | Server owns | Client owns |
| --- | --- | --- |
| Discovery | Descriptor at `/.well-known/scout-upload.json` | Fetch + cache |
| Auth | OAuth 2.0 public client, `rides:create` scope only | Sign-in UI, secure token storage |
| Upload | Accept raw FIT, idempotency, size limits | `POST` bytes + `Idempotency-Key` |
| Parse & preview | FIT parser per DATA-FORMAT; map + tag UI | — |
| Privacy | Account-level hidden radius applied to all drafts | — |
| Lifecycle after upload | `draft` → `in_review` → `published` / `rejected` | Show `web_url` once; local outbox only |
| Notifications | Accepted / rejected / more detail needed | — |

The practical rule from [SHARING §0](SHARING.md#0-the-client-stays-dumb): if a head
unit with no FIT parser could not do it, the server must.

---

## 2. Instance discovery

### 2.1 Endpoint

```
GET {instance_base_url}/.well-known/scout-upload.json
```

- **HTTPS required** in production. Plain HTTP is allowed only for `localhost`
  development.
- Respond with `Content-Type: application/json`.
- Honour **`ETag`** and **`Cache-Control`**; clients re-fetch at most daily and
  on `404`/`409` from dependent endpoints.
- Unknown JSON fields **must be ignored** by clients; servers may add fields
  without breaking old apps.

### 2.2 Descriptor schema (`spec_version` 1)

| Field | Required | Description |
| --- | --- | --- |
| `spec_version` | yes | Integer. `1` today. Bump only for breaking changes. |
| `name` | yes | Human-readable instance name (e.g. `"Cycling Commons Atlas"`). |
| `terms_url` | yes | Where riders accept terms before first upload. |
| `privacy_url` | yes | Privacy policy URL. |
| `contribution_license` | yes | SPDX id for published facts (e.g. `"ODbL-1.0"`). |
| `authorization_endpoint` | yes* | OAuth 2.0 authorization endpoint (PKCE). |
| `device_authorization_endpoint` | yes | RFC 8628 device authorization endpoint. |
| `token_endpoint` | yes | OAuth 2.0 token endpoint. |
| `revocation_endpoint` | yes | RFC 7009 token revocation. |
| `registration_endpoint` | no | RFC 7591 dynamic client registration. If absent, clients use a pre-issued `client_id`. |
| `grant_types_supported` | yes | Must include `urn:ietf:params:oauth:grant-type:device_code`. Should include `authorization_code` and `refresh_token`. |
| `scopes_supported` | yes | Must include exactly `rides:create` for Scout clients. |
| `upload_endpoint` | yes | Absolute HTTPS URL for ride upload (`POST`). |
| `accepted_media_types` | yes | Must include `application/vnd.ant.fit`. |
| `max_upload_bytes` | yes | Maximum request body size (e.g. `26214400` for 25 MiB). |

\*Required if the instance advertises `authorization_code` in
`grant_types_supported`.

Example (illustrative URLs):

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
  "grant_types_supported": [
    "urn:ietf:params:oauth:grant-type:device_code",
    "authorization_code",
    "refresh_token"
  ],
  "scopes_supported": ["rides:create"],

  "upload_endpoint": "https://cyclingcommons.org/api/v1/rides",
  "accepted_media_types": ["application/vnd.ant.fit"],
  "max_upload_bytes": 26214400
}
```

### 2.3 Instance base URL vs descriptor URLs

Forks configure clients with **one** build-time base URL (see
[CUSTOMIZATION.md](CUSTOMIZATION.md)). All OAuth and upload URLs in the descriptor
may be on the same host or on related hosts, but they **must** be absolute HTTPS
URLs. Clients do not construct paths beyond appending
`/.well-known/scout-upload.json` to the configured base.

---

## 3. Authentication

OAuth 2.0 **public client** ([RFC 8252](https://www.rfc-editor.org/rfc/rfc8252)) —
no client secret.

### 3.1 Required grant: device authorization (RFC 8628)

Mandatory for head units and as the universal fallback.

1. Client `POST`s to `device_authorization_endpoint` with `client_id`, `scope=rides:create`.
2. Server returns `device_code`, `user_code`, `verification_uri` (and optionally
   `verification_uri_complete`), `expires_in`, `interval`.
3. Rider completes auth on another device; client polls `token_endpoint` with
   `grant_type=urn:ietf:params:oauth:grant-type:device_code`.
4. Server issues `access_token` (+ `refresh_token`).

### 3.2 Recommended grant: authorization code + PKCE (RFC 7636)

For phones and anything with a system browser and custom URL scheme / app link
redirect.

### 3.3 Token rules

| Rule | Requirement |
| --- | --- |
| Scope | **`rides:create` only** for Scout clients. Wider scopes must not be issued to Scout ports. |
| Refresh tokens | **Required.** Rotation allowed; clients must handle rotated refresh tokens. |
| Access token lifetime | Short-lived (instance policy; suggest ≤ 1 h). |
| Client identity | Dynamic registration via `registration_endpoint` **or** manually issued `client_id` per port/fork. Official and fork builds must not share the same `client_id`. |
| Revocation | `POST` to `revocation_endpoint` on sign-out; clients wipe local tokens even if revocation fails. |
| Terms | Upload `403` if account has not accepted current `terms_url` (client sends rider to web). |

### 3.4 What a `rides:create` token can do

| Action | Allowed via API token? |
| --- | --- |
| `POST` a new ride (create `draft`) | ✅ |
| Read, update, or delete any ride | ❌ |
| Submit draft for review | ❌ (web only) |
| Publish / curate | ❌ |

Enforcement is by **token scope**, not client politeness — a modified app with a
valid token still cannot publish.

---

## 4. Upload API

### 4.1 Request

```
POST {upload_endpoint}
Authorization: Bearer <access_token>
Content-Type: application/vnd.ant.fit
Idempotency-Key: <lowercase hex sha256 of request body>
Scout-Client: <platform>/<version>   # e.g. android/1.0.0
```

Body: **the original FIT file, byte for byte** — no sidecar JSON, no re-encoding,
no client-side trimming or tag extraction.

The only value the client derives from the file is SHA-256 for the idempotency
key.

### 4.2 Success

```
HTTP 201 Created
Content-Type: application/json

{
  "id": "r_8fc21",
  "status": "draft",
  "web_url": "https://cyclingcommons.org/rides/r_8fc21"
}
```

| Field | Requirement |
| --- | --- |
| `id` | Stable server identifier for this upload. |
| `status` | Must be `"draft"` on create. |
| `web_url` | HTTPS URL where **this rider** opens the draft in a browser. Must work without the app. |

### 4.3 Idempotency

- Same `Idempotency-Key` + same authenticated user → **`409 Conflict`** with the
  existing ride’s `id` and `web_url` in the body (client treats as success).
- Idempotency covers **retries of identical bytes only**. Two different files
  from two devices are two drafts; the server must not deduplicate by route or
  time.

### 4.4 Error responses

Return JSON where practical:

```json
{
  "error": "payload_too_large",
  "message": "Ride file exceeds the 25 MB limit."
}
```

| HTTP | Meaning | Client behaviour |
| --- | --- | --- |
| `401` | Expired or revoked token | Refresh once, then re-auth |
| `403` | Wrong scope or terms not accepted | Rider must use web; no retry |
| `409` | Duplicate idempotency key | Return existing `id` / `web_url` |
| `413` | Body > `max_upload_bytes` | Explain; no retry |
| `415` | Wrong `Content-Type` | No retry |
| `429` | Rate limited | Honour `Retry-After` |
| `5xx` | Server error | Backoff retry; keep local file |

`error` is a machine-readable code; `message` is rider-facing plain language.

### 4.5 Ingest processing (server-side, synchronous or async)

On successful store of the raw FIT:

1. **Parse** per [DATA-FORMAT.md](DATA-FORMAT.md) — Scout developer fields,
   undo rules, surface stretches, radar events.
2. **Associate** with the authenticated account.
3. **Set status** `draft` (private to that account).
4. **Apply privacy zone** if the rider has configured one (see §6) — server-side
   only; never require the client to pre-trim.
5. **Expose preview** at `web_url` (map, tags, metadata sufficient for the rider
   to decide whether to submit).

The raw FIT remains private; only curated facts may later become public Atlas
data (ODbL).

---

## 5. Ride lifecycle (after upload)

```
draft ──(rider, web)──► in_review ──(curator)──► published
                              └──► rejected
```

| Status | Set by | API token can set? | Visible to rider |
| --- | --- | --- | --- |
| `draft` | upload | — (created by upload) | app (once) + web |
| `in_review` | rider on web | ❌ | web |
| `published` | curator | ❌ | web + public Atlas |
| `rejected` | curator | ❌ | web (+ reason) |

The upload response is the **only** time the app needs server state. Clients keep
a local outbox (`not shared` / `queued` / `uploaded` + stored `web_url`); they
**do not** poll review status or mirror the Atlas.

---

## 6. Rider web experience

The server **must** provide a logged-in web UI (at minimum at each `web_url`) that
lets the rider:

1. **Preview** the parsed ride (map, tags, radar if present).
2. **Edit or delete** the draft before submission (optional but expected).
3. **Submit for review** (`draft` → `in_review`) — not available via Scout API token.
4. **Read outcome** when curated (`published` / `rejected` with human-readable reason).

### 6.1 Privacy zones

- Configured **once per account** on the web, not in Scout.
- Applied by the server to **every** draft from every device.
- Must be re-derivable from the stored original FIT (do not rely on clients to
  upload pre-trimmed traces).

Whether zones apply retroactively to drafts already stored is **instance policy**
(document it for riders).

### 6.2 Notifications

Accepted, rejected, or “more detail needed” are announced through whatever channel
the instance already uses (email, in-app web notifications, etc.). Scout clients
do not implement push for curation outcomes.

---

## 7. Curation & publication

Server-side only:

- Curator tools to review `in_review` rides.
- On **publish**: extract **facts** per DATA-FORMAT and contribution rules;
  publish under `contribution_license`. **Do not** publish the raw GPS trace as
  open data — the FIT is about the rider; published Atlas data is about the world.
- On **reject**: store a reason the rider can read on the web.

Parser and curation rules live in [DATA-FORMAT.md](DATA-FORMAT.md); this document
does not duplicate tag semantics.

---

## 8. Security & operations

| Topic | Requirement |
| --- | --- |
| Transport | TLS 1.2+ in production |
| Storage | Raw FITs encrypted at rest; access tied to account + curator roles |
| Rate limits | Per-account upload limits; return `429` + `Retry-After` |
| Size | Enforce `max_upload_bytes` consistently with descriptor |
| Audit | Log uploads with `id`, account, `Scout-Client`, timestamp — not file contents in logs |
| Availability | Descriptor and upload endpoint should be independently monitorable |

---

## 9. Conformance checklist

A minimal conforming Atlas instance provides:

- [ ] `GET /.well-known/scout-upload.json` (§2)
- [ ] Device authorization grant (§3.1)
- [ ] Refresh tokens + revocation endpoint (§3.3)
- [ ] `rides:create` scope only for Scout clients (§3.4)
- [ ] `POST` upload accepting `application/vnd.ant.fit` (§4)
- [ ] Idempotency on `Idempotency-Key` (§4.3)
- [ ] FIT parse + private `draft` + `web_url` (§4.5)
- [ ] Web UI: preview, submit, read outcome (§6)
- [ ] Account-level privacy zone support (§6.1)
- [ ] Curator path to `published` / `rejected` (§7)

Optional but recommended for phone ports: authorization code + PKCE (§3.2),
dynamic client registration (§2.2).

Local testing: [SHARING §11](SHARING.md#11-testing) — `tools/mock-atlas.mjs` (to be
written) should implement the same surface for CI.

---

## 10. Instance policy (not fixed by Scout)

These are server decisions; clients do not need updates when they change:

| Question | Notes |
| --- | --- |
| Dynamic registration vs manual `client_id` | Either works if descriptor is honest |
| Draft retention before purge | Publish a rider-visible policy |
| Per-rider upload quota | Use `429` or `403` + clear `message` |
| Retroactive privacy zones | Document on privacy settings page |
| Notification channel | Email, web, etc. — Scout never polls |

---

## 11. Doc control

| Item | Value |
| --- | --- |
| Document | Atlas server requirements |
| Owns | Discovery, OAuth, upload ingest, post-upload lifecycle, web obligations |
| Does not own | Client UX ([SHARING.md](SHARING.md)), tag codes ([DATA-FORMAT.md](DATA-FORMAT.md)), recording ([SPEC.md](SPEC.md)) |
| Fork setup | [CUSTOMIZATION.md](CUSTOMIZATION.md) |
