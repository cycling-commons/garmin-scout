# Publishing Scout to the Connect IQ store

Publishing is optional — the app runs sideloaded (see
[CONTRIBUTING.md](../CONTRIBUTING.md) for build & sideload). This covers pushing a
build to the Connect IQ store, whether that's the canonical Cycling Commons build
or a fork under your own name.

**Versioning:** the Connect IQ *manifest* carries no app version — the version
string is entered in the store portal at upload time. Track it with semantic
versioning in [CHANGELOG.md](../CHANGELOG.md) (the source of truth) and bump it on
every store upload.

> **Order of operations for the canonical build: publish, then open-source.** The
> committed `id` is only protected once the first store upload claims it. Publish
> the official Scout from the Cycling Commons account **before** making the repo
> public, so the `id` can't be claimed by someone else's upload first. (Recoverable
> by regenerating the `id`, but trivially avoided by ordering it this way.)

## Account

- **If you publish as an organisation, use a dedicated Garmin account on a role
  email, not a personal one** (a solo maintainer can just use their own account).
  Garmin has no "company account" type; the publisher identity shown as "by …" on
  every app page comes from the **developer display name** you set in the developer
  dashboard profile.
- **Apps cannot be moved between accounts.** Whichever account uploads owns the app
  for life, including all future updates — so start on the right account from day
  one.

## The `id` and the signing key

They are independent of the account, and live in very different places:

- **`id`** (in `manifest.xml`) is a public identifier — fine to commit, and keep
  it **stable** across updates (changing it makes a new, separate store app). A
  fork generates its own new id.
- **The signing key** is a private RSA key — treat it like a password. Never
  commit it (git-ignored); keep it outside the repo, and for a team in a secrets
  vault with a backup. **Store updates must be signed with the same key**, so
  losing it means you can't update the published app — back it up.

## Forks

- **A fork must generate its own `id` before publishing** (Monkey C: *Generate a
  Connect IQ App ID*, then replace `id` in `manifest.xml`), plus its own name and
  listing — so riders aren't confused about which build is which. If you
  forget, the store simply rejects the upload: the official `id` is owned by the
  Cycling Commons account and can't be published from another (the signing key
  wouldn't match either). Harmless, but you'll have to regenerate and re-upload.
- A fork published to the store must be **clearly distinct** from the official
  build — its own name, id, and listing.

## Release steps

1. Bump the version + changelog entry.
2. Build the store package (all products, release):
   `monkeyc -e -r -f monkey.jungle -o bin/Scout.iq -y <key>`
3. Upload `bin/Scout.iq` at [apps.garmin.com](https://apps.garmin.com) from the
   right account; paste the listing copy from
   [store/store-listing.md](../store/store-listing.md); add screenshots (shot-list
   is in that file).
