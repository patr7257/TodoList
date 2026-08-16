# CLAUDE.md

## What this is

TodoList Management System: a multi-user task manager. This repo holds the
headless HTTP API and the shared enum it exposes. Clients talk to it over
HTTPS/JSON, join shared to do lists, create and reassign tasks, and update task
status (NOT_STARTED / IN_PROGRESS / DELAYED / NEED_HELP / DONE). The API keeps
state consistent under concurrent access and persists everything to Postgres
(Neon in production).

**There is no front end in this repo.** Since issue #66 the only client is the
web edition at `/todo` in `patr7257/PatrickRobelWeb`, which is also the phone
experience via its installable PWA. See "UI work happens in the website repo"
below, which replaced an older rule about keeping two clients in lockstep.

This started as a DTU course project ("Project 13, To do list", see the course
template still embedded in old commits) built around tuple space coordination
concepts. It was later moved into this personal repo, cleaned up into a generic
installable app, migrated off the original jSpace tuple space transport onto the
HTTP API described here (the jSpace module was retired in issue #25), and finally
shed its JavaFX desktop client in issue #66.

## Module layout (Maven multi-module, groupId `com.patr7257`)

- `pom.xml`: parent POM, packaging `pom`, Java 21 (`maven.compiler.release`),
  declares the two modules below plus the shared JUnit 5.11.4 version.
- `shared/` (`todolist-shared`): one class, `dk.dtu.shared.TaskStatus`, the task
  status enum with a completion percentage per status. It is a separate module
  only because it predates the client's removal; folding it into `api/` would be
  a fine future cleanup but nothing depends on it happening.
- `api/` (`todolist-api`): headless HTTP API. Main class `dk.dtu.api.ApiMain`.
  Built with Javalin (HTTP), JDBI + HikariCP over Postgres, and Gson for JSON.
  Packages to a self-contained shaded fat jar (`todolist-api.jar`).
  - `dk.dtu.api.web`: `ApiServer` and the controllers (`AuthController`,
    `ListsController`, `ItemsController`, `StateController`,
    `CountersController`, `ListSharesController` for authenticated share
    management, `ShareController` for the one public route, and
    `TinderController` for TodoTinder), plus `Backend`, `RateLimiter`,
    `ClientIp` (shared rate-limit key resolution), and JSON/error helpers
    (`Views` for lists/items, `CounterViews` for counters, `ShareViews` for
    shares, which deliberately never calls `Views`, `TinderViews` for the
    tinder shapes, and `TinderPrompts`, the pure composer of the refill prompt
    a drained deck hands over).
  - `dk.dtu.api.auth`: token auth, and nothing else since #61 retired password
    login. `AuthFilter` (the before-handler and its allowlist), `Token` (mint
    and verify the `todo_session` value), `Hex` (the lowercase hex rendering the
    token signature is made of, which used to live on the deleted `Scrypt`).
  - `dk.dtu.api.db`: `DataSources` (Hikari pool) and `Migrations`.
  - `dk.dtu.api.domain`: `TodoService` and the row/value types it maps, plus
    `CountersService` / `CounterRow` for the fun counters and
    `SharesService` / `ShareRow` / `ShareTokens` for the public share links,
    and `TinderService` / `TinderDeckRow` / `TinderEntryRow` /
    `TinderSwipeRow` / `TinderMatchRow` for TodoTinder (all three deliberately
    their own services: `TodoService` mirrors the website's queries and is the
    hottest file in the repo, while counters have no website counterpart,
    shares own an unauthenticated read path that must not be coupled to it, and
    tinder is a whole resource family that only borrows
    `TodoService.insertItem` to land a right swipe).

`api` depends on `todolist-shared`.

## Build and run

Prerequisites: JDK 21 and Maven.

Build everything from the repo root:

```powershell
mvn clean install
```

(use `install`, not just `package`, so the `shared` module's jar is resolvable
by `api`).

Run the API from the repo root. Do NOT add `-am`: with `-am` the direct
`exec:java` goal also runs on the parent aggregate module and fails
("parameters 'mainClass' ... are missing"). Run `mvn -q install -DskipTests`
first so dependencies resolve.

It reads `DATABASE_URL` (Postgres connection string) and `TODO_SESSION_SECRET`
from the environment.

`mvn -pl api exec:java` does NOT start an embedded Postgres. The embedded
Postgres in this repo (`io.zonky.test`) is a TEST-scope dependency used only by
the api module's integration tests. With no `DATABASE_URL` the server still
starts, logs a warning, and every data route answers 503. So local work needs a
real database, and pointing one at production Neon is not an option because
startup runs Flyway and would apply unmerged migrations there.

Use the throwaway Docker Postgres instead (see "Local dev database" below):

```powershell
.\scripts\dev-db.ps1
$env:DATABASE_URL='postgres://postgres:todo@localhost:5433/todo'; $env:TODO_SESSION_SECRET='dev-secret'; mvn -pl api exec:java
```

To exercise the UI against a local API, run the website from
`patr7257/PatrickRobelWeb` (`cd website; pnpm dev`) with `TODO_API_BASE_URL`
pointed at `http://localhost:8080`.

## Tests

JUnit 5 (Jupiter 5.11.4) tests live under each module's `src/test/java`:

- `shared`: `TaskStatusTest`.
- `api`: HTTP/service tests for the api module (`TodoApiIntegrationTest`,
  `CountersIntegrationTest`, `SharesIntegrationTest`, `TinderIntegrationTest`,
  `web/ViewsTest`, `web/ShareViewsTest`, `web/TinderViewsTest`,
  `web/TinderPromptsTest`, `domain/ShareTokensTest`, `TokenTest`, `HexTest`,
  `CompletionTest`, `DataSourcesTest`). The four integration tests each start
  their own `EmbeddedPostgres` and also drive a real Javalin instance on an
  ephemeral port, so routes and auth are asserted rather than assumed.
  `ViewsTest` pins the EXACT key set and order of the state payload's list
  object: that is the regression guard for the website client.
  `ShareViewsTest` plus `SharesIntegrationTest` do the same for the public
  share payload, and additionally assert what is ABSENT from it, which is the
  contract that matters for the API's only unauthenticated output.
  `TinderIntegrationTest` is ordered on purpose and the ordering is
  load-bearing: the match rule is "every row in `users` swiped right", so it
  creates the SECOND user part way through, in the test that first proves a
  lone user cannot match with themselves. `TinderPromptsTest` pins the refill
  prompt as text, including that it carries no placeholder for anyone to fill
  in, which no integration test would notice being broken.

Run all tests from the repo root with `mvn test`.

## Local dev database

`mvn -pl api exec:java` needs a real `DATABASE_URL` (see "Build and run"). Two
committed helpers exist so nobody improvises, and above all so nobody points a
local API at production Neon (startup runs Flyway, which would apply unmerged
migrations to production):

- `scripts/dev-db.ps1` / `.sh` plus `scripts/dev-db-fixture.sql`: start, stop
  (`-Stop`) or reset (`-Reset`) a throwaway Docker Postgres on port 5433, waiting
  for readiness with a bounded poll. `-Fixture` loads users and lists whose
  free-text `owner` values deliberately cover the exact, differently cased,
  whitespace padded, ambiguous (two users share a name), unmatched and null
  cases, so the `owner_id` backfill can be exercised without production data.
  `-Fixture` needs the API to have started once, because Flyway lives in the API.
- `scripts/neon-branch.ps1` / `.sh`: create or delete a disposable Neon branch, a
  copy-on-write clone of production, to rehearse a migration against real data.
  Prompts once for a Neon API key, never stores it, refuses to delete the default
  branch.
- `scripts/tinder-refill.ps1` / `.sh`: POSTs a generated batch of TodoTinder
  cards to the import endpoint (#59). This is the one line a drained deck's
  refill prompt hands over. It prompts for the session token, dry-runs what it
  will send, confirms, and never leaves the token behind in the shell, which is
  why the prompt itself can be free of secrets and placeholders.

## Migrations

Flyway, from `dk.dtu.api.db.Migrations`, files in
`api/src/main/resources/db/migration`. Current head is `V8`.

**Version register.** Because `outOfOrder` is false (see below), migration
numbers are pre-assigned per issue and recorded here BEFORE the branch merges:

| Version | Issue | What |
|---|---|---|
| V1 to V4 | earlier | baseline, desktop superset, `lists.owner_id`, its backfill |
| V5 | #46 | `fun_counters` |
| V6 | #52 | `list_shares` (public share links) |
| V7 | #51 | `todo_credentials` (passkeys) + `users.pw_hash` made nullable |
| V8 | #56 | `tinder_decks`, `tinder_entries`, `tinder_swipes` (TodoTinder) |

- `baselineOnMigrate=true` with `baselineVersion=1`, because production Neon
  already held the V1 schema when Flyway was introduced.
- **`outOfOrder` is at its default `false`, and this has teeth.** If a higher
  version reaches production before a lower one, `flyway.migrate()` throws at
  boot, the Dokploy container crash-loops, and the live API is down for the
  website. This was proven, not assumed: applying V5 before V3 on a scratch
  database fails with
  `FlywayValidateException: Detected resolved migration not applied to database: 3`.
  Consequence: when two branches each add a migration, pre-assign the numbers and
  land them in ONE merge, or renumber the second branch's own file before it
  merges. Never hand-edit `flyway_schema_history`.
- Every migration must be additive and idempotent (`ADD COLUMN IF NOT EXISTS`,
  `CREATE TABLE IF NOT EXISTS`, backfills guarded by `WHERE ... IS NULL`), and
  must never DROP, rename or retype anything. After a migration has been applied
  to production, NEVER edit it: a checksum mismatch crash-loops the container.
  Corrections ship as a new version.

## CI and releases

One workflow, `.github/workflows/ci.yml`: a full reactor build plus tests on
every pull request. Main only receives verified merges.

There is **no release workflow and no installer any more** (issue #66). Merging
to `main` redeploys the API container on Dokploy, and nothing else. The last
desktop installer release, v2.0.8, stays on the Releases page; an installed copy
keeps working against this API but no longer receives updates.

## Sign in (passkeys + magic link, issue #51)

**There is no password form anywhere in the product any more.** Sign in happens
on the website, which is now also the only client.

- The web edition (`/todo` in `patr7257/PatrickRobelWeb`) offers a passkey
  (`@simplewebauthn`, discoverable credentials, so login is usernameless) or a
  magic link mailed via ZeptoMail. It also **mints** the `todo_session` token,
  in the byte-identical HMAC format `dk.dtu.api.auth.Token` uses. `TokenTest.java`
  here and `session.test.ts` there pin the SAME hand-computed vector from their
  own side; that pair is the only thing standing between "the website mints" and
  "the Java API accepts", because a mismatch shows up solely as a 401 in the
  merged system.
- The website also still serves the **desktop handoff** endpoints
  (`/api/todo/auth/desktop-code`, `/api/todo/auth/desktop-exchange`), the
  RFC 8252 plus PKCE ceremony the retired JavaFX client used. They are harmless
  and are what lets an already-installed v2.0.8 copy sign in at all, so do not
  remove them from that repo as part of anything happening here.
- **The password path is gone (#61).** `POST /api/todo/login`, `AuthService` and
  `Scrypt` were deleted, along with the login rate limiter and its
  `API_RATE_LIMIT_*` settings, and `/api/todo/login` came off `AuthFilter`'s
  allowlist. `POST /api/todo/logout` stays: it only expires the cookie, which is
  as useful for a website-minted session as for a locally-minted one.
  `users.pw_hash` remains as a NULLABLE legacy column that no code reads; it is
  not dropped, because an applied migration is immutable and removing the column
  buys nothing.
- **Accounts are created with `dk.dtu.api.tools.SeedUser`** (`scripts/seed-user.ps1`
  / `.sh`), which is the ONLY way an account comes into existence and is
  therefore kept, not deleted. It writes a passwordless row (`pw_hash` NULL) and
  prompts for email and name only. Nothing self-signs-up: the website's
  magic-link route mails a link only to an address that already has a `users`
  row and is on `allowlist.ts`, and the passkey path is usernameless, so it can
  only resolve a credential enrolled from an existing session. A new person
  therefore needs BOTH a `SeedUser` row and an allowlist entry.
- Revocation is still "rotate `TODO_SESSION_SECRET` in Dokploy AND Vercel, then
  redeploy both". It logs everyone out everywhere. Per-user revocation would be a
  breaking wire-format change that `TokenTest` pins on purpose.

## Hosting the API

The API runs headless via `dk.dtu.api.ApiMain` and is built from `Dockerfile.api`
(a Maven build stage produces `todolist-api.jar`, then a slim JRE runtime stage
runs it). It is deployed on a Dokploy VPS and exposed publicly behind Dokploy's
Traefik reverse proxy with Let's Encrypt TLS at
`https://api.todolist.patrickrobel.dk`. `DATABASE_URL` (Neon Postgres) and
`TODO_SESSION_SECRET` are provided as Dokploy service environment variables;
`API_HTTP_PORT` defaults to 8080 inside the container.

`TODO_SHARE_BASE_URL` (default `https://patrickrobel.dk`, trailing slashes
stripped) is the origin a public share link is browsed at. The API composes the
full share URL as `<base>/s/<token>` and hands it to clients ready made, so set
it to whatever origin actually serves the `/s/:token` page. The public share
route also has its own limiter, `API_SHARE_RATE_LIMIT_MAX` (default 60) per
`API_SHARE_RATE_LIMIT_WINDOW_SECONDS` (default 60), keyed on client IP.

`API_PUBLIC_BASE_URL` (default `https://api.todolist.patrickrobel.dk`, trailing
slashes stripped) is where THIS API is reachable from outside. It is a separate
setting from `TODO_SHARE_BASE_URL` on purpose: one is the website a share link
is browsed on, the other is the API host, and conflating them produces either a
share URL nobody can open or a TodoTinder refill prompt that posts into the
website. It exists because the refill prompt (#59) is pasted into a Claude
session that has no idea where this API lives, so it has to name an absolute
endpoint.

## TodoTinder (epic #44)

A mobile-first swipe app: multiple decks (AcTindervitivities, VacayTinderation,
SwoppingSwiper, DateNighTinders), right swipe creates an item in the deck's
linked todo list via the existing API, gated by the existing token auth (the two
account holders only). Grocery deck recycles staples every run; idea decks
deplete per user and offer a copyable line to have a Claude session refill the
deck.

The design session is DONE: the settled spec is a comment on #44 and the work
split into #56 (V8 schema plus API), #57 (the four datasets), #58 (the swipe
PWA) and #59 (refill import endpoint). This supersedes the Activity Tinder note
in `patr7257/BoredAPIActivityWheel`.

**The API and the datasets live here; the swipe UI does NOT.** The epic
originally had #58 served as static files by Javalin at
`tinder.todolist.patrickrobel.dk`, and that was abandoned on 2026-08-16 for a
concrete reason: the `todo_session` cookie belongs to `patrickrobel.dk`, so an
app on the API's own origin could not authenticate at all without a second
sign-in handoff being invented for it. Served from the website at
`patrickrobel.dk/tinder` it inherits the existing session and the existing
same-origin proxy, so there is no new auth, no CORS and no DNS record. That also
keeps this API at exactly ONE unauthenticated route, since no static mount and
no `AuthFilter` change were needed.

**#56 and #59 have landed**, as the backend below. #57 and #58 are still open,
so nothing swipes yet: V8 seeds NO deck rows on purpose (a deck's target list
has to be resolved against real lists, which a migration cannot do sensibly),
and until #57 lands every deck endpoint answers an empty list rather than a
404.

Routes, all authenticated, all under `/api/todo/tinder/`, with `{deck}` always
the deck KEY and never its uuid:

| Method | Path | Body / query | Answers |
|---|---|---|---|
| GET | `/decks` | none | `{decks:[{key, displayName, recycleMode, datasetKey, targetListId, total, remaining, needsRefill, refillPrompt}]}` |
| GET | `/decks/{deck}/cards` | `?limit=` (default 20, clamped at 100) | `{deck:{...}, cards:[{id, text, metadata, source}]}` |
| POST | `/decks/{deck}/swipes` | `{entryId, direction}` | `{swipe:{entryId, direction, createdAt}, created, item, match}` |
| POST | `/decks/{deck}/entries` | `{source?, entries:[{text, metadata?, source?}]}` | `{deck, received, inserted, skipped, total}` |
| GET | `/matches` | none | `{matches:[{entryId, deckKey, deckDisplayName, text, metadata, matchedAt}]}` |

The five rules that are easy to break and expensive to get wrong:

- **`recycle_mode` is the only behavioural difference between decks**, and it
  shows up in exactly two places: the card query (a `deplete` deck filters out
  everything the CALLER already swiped either way, a `recycle` deck filters
  nothing) and the swipe write. Nothing may branch on a specific deck key.
- **A swipe is an UPSERT on `(user_id, entry_id)`, never an insert.** The spec
  asked for that uniqueness on non-recycling decks only, which a partial index
  cannot express (its predicate cannot reach the deck's mode across a join), so
  the index is plain and the recycling deck upserts. `tinder_swipes` is
  therefore a "latest swipe" table, not a swipe log; counting how often
  something was swiped needs a new append-only table, not a relaxed index.
- **A right swipe creates an item through `TodoService.insertItem`**, deduped
  against any OPEN (status not `DONE`) item in the target list with the same
  text, trimmed and case-insensitively. That guard is what lets the grocery deck
  ask about milk every week without stacking up five items, and ticking an item
  off correctly lets the next one be created.
- **Matches are a QUERY over `tinder_swipes`**, never a stored flag, with a
  "at least two rows in `users`" guard so a lone user cannot match with
  themselves. Unswipe one side and the match is simply gone from the next
  answer.
- **The refill prompt is composed by the API** (`TinderPrompts`) and names the
  deck, its metadata keys (derived from the cards actually in the deck), the
  card count and the absolute endpoint. It hands over exactly one runnable line,
  `scripts/tinder-refill.ps1` (`.sh` for mac/Linux), which prompts for the
  session token. Never put a token, or an angle-bracket placeholder, into
  anything meant to be pasted.

## MANDATORY: UI work happens in the website repo, not here

This repo has no user interface. Any change a person can see ships in
`patr7257/PatrickRobelWeb`, as the `/todo` route (desktop browser and, via the
installable PWA, the phone). That is one front end, not two: issue #66 retired
the JavaFX desktop client precisely so a product change never has to be built
twice again.

- A UI change usually still means an issue on each board (this repo's board is
  GitHub Project #7, the website's is #2) whenever the API has to move too.
- Backend work is done ONCE here and consumed there. Two behaviour rules used to
  drift between the clients and are worth keeping written down even with one
  client left: the overdue rule (due date before today AND status not `DONE`) and
  the completion math (average of per-status percentages over ALL items, not an
  average of per-list averages).
- There is no exception. The TodoTinder swipe app (#58) is a separate product
  surface but it too is built in the website repo, at `patrickrobel.dk/tinder`,
  consuming this API through the same same-origin proxy the todo UI uses. This
  repo owns tinder's schema, API and datasets, and none of its pixels.

## Notable conventions

- Package root is `dk.dtu` for both modules (`dk.dtu.shared.*` for the shared
  module, `dk.dtu.api.*` for the api module), a holdover from the project's DTU
  course origin; the Maven `groupId` is `com.patr7257`.
- Client/API communication is HTTP + JSON. There is no tuple space and no direct
  RPC. Clients refresh by re-reading the state endpoint and refetching the
  current view rather than applying incremental updates.
- **`GET /api/todo/state` is APPEND-ONLY.** The website client parses that exact
  payload, so keys may be added at the end of an object but never removed,
  renamed, retyped or reordered. `api/src/test/java/dk/dtu/api/web/ViewsTest.java`
  pins the list object's key set and order as the regression guard. A new
  resource family gets its OWN endpoint instead of being bolted into `/state`,
  which is why the fun counters live at `/api/todo/counters` and TodoTinder
  lives at `/api/todo/tinder/`.
- **Public share links (#52) live at `/api/todo/share/{token}`, and that is the
  ONLY unauthenticated route in the API.** Management is separate and
  authenticated: `GET`/`POST /api/todo/lists/{id}/shares` and
  `DELETE /api/todo/lists/{id}/shares/{shareId}`. The singular/plural split is
  load-bearing, not cosmetic: `dk.dtu.api.auth.AuthFilter` holds an explicit
  allowlist, which since #61 is exactly two entries (exact `/api/todo/logout`,
  prefix `/api/todo/share/`), and `share` singular appears in exactly one path in the
  whole API, so the prefix cannot open a management route. Never add a second
  route under `/api/todo/share/`.
- **The public share payload must NEVER be built by reusing `Views`.**
  `dk.dtu.api.web.ShareViews` writes every field out by hand precisely so that
  the next field appended to `/state` does not silently become world readable.
  Nothing there may expose a `users.id` value, `location` (it can be a home
  address), item `priority` (on a wishlist it ranks the presents), or the list
  id. `ShareViewsTest` and `SharesIntegrationTest` pin both the exact key order
  and the absence of every forbidden key and value.
- **The share `url` is composed only by the API**, as
  `TODO_SHARE_BASE_URL + "/s/" + token` in `ShareViews.share`. No client builds a
  share URL from a token. Share tokens come from `dk.dtu.api.domain.ShareTokens`
  (24 SecureRandom bytes, URL-safe base64 without padding: 32 chars, 192 bits).
  Every share failure (unknown, malformed, revoked, expired) answers a
  byte-identical 404.
- `lists.owner_id uuid REFERENCES users(id)` is the real owner; the legacy
  free-text `lists.owner` column is KEPT and kept in sync as a denormalized
  display name, because the website may read that column directly. `users.name`
  is NOT unique, so the V4 backfill resolved only unambiguous matches and
  deliberately left the rest NULL to be re-picked by hand.
- Counter bumps are relative in SQL (`value = value + :delta`), not a
  read-modify-write, so two people clicking at once both land. Any future tally
  follows the same rule.
- The API persists state to Postgres via JDBI; schema is applied by
  `dk.dtu.api.db.Migrations`.
