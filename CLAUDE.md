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
    management and `ShareController` for the one public route), plus `Backend`,
    `RateLimiter`, `ClientIp` (shared rate-limit key resolution), and JSON/error
    helpers (`Views` for lists/items, `CounterViews` for counters,
    `ShareViews` for shares, which deliberately never calls `Views`).
  - `dk.dtu.api.auth`: token auth (`AuthFilter`, `AuthService`, `Token`,
    `Scrypt`).
  - `dk.dtu.api.db`: `DataSources` (Hikari pool) and `Migrations`.
  - `dk.dtu.api.domain`: `TodoService` and the row/value types it maps, plus
    `CountersService` / `CounterRow` for the fun counters and
    `SharesService` / `ShareRow` / `ShareTokens` for the public share links
    (both deliberately their own services: `TodoService` mirrors the website's
    queries and is the hottest file in the repo, while counters have no website
    counterpart and shares own an unauthenticated read path that must not be
    coupled to it).

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
  `CountersIntegrationTest`, `SharesIntegrationTest`, `web/ViewsTest`,
  `web/ShareViewsTest`, `domain/ShareTokensTest`, `ScryptTest`, `TokenTest`,
  `CompletionTest`, `DataSourcesTest`). The three integration tests each start
  their own `EmbeddedPostgres` and also drive a real Javalin instance on an
  ephemeral port, so routes and auth are asserted rather than assumed.
  `ViewsTest` pins the EXACT key set and order of the state payload's list
  object: that is the regression guard for the website client.
  `ShareViewsTest` plus `SharesIntegrationTest` do the same for the public
  share payload, and additionally assert what is ABSENT from it, which is the
  contract that matters for the API's only unauthenticated output.

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

## Migrations

Flyway, from `dk.dtu.api.db.Migrations`, files in
`api/src/main/resources/db/migration`. Current head is `V7`.

**Version register.** Because `outOfOrder` is false (see below), migration
numbers are pre-assigned per issue and recorded here BEFORE the branch merges:

| Version | Issue | What |
|---|---|---|
| V1 to V4 | earlier | baseline, desktop superset, `lists.owner_id`, its backfill |
| V5 | #46 | `fun_counters` |
| V6 | #52 | `list_shares` (public share links) |
| V7 | #51 | `todo_credentials` (passkeys) + `users.pw_hash` made nullable |
| V8 | #56 | RESERVED: `tinder_decks`, `tinder_entries`, `tinder_swipes` |

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
- **Password login still exists in the API, deliberately unused.** Clients
  installed before v2.0.8 rely on it. The kill switch is `UPDATE users SET
  pw_hash = NULL` (V7 made the column nullable and `Scrypt.verify` returns false
  for a null hash, so it is a clean 401 not a 500). Deleting the code is tracked
  in #61, and `Token.sign` calls `Scrypt.bytesToHex`, so that helper moves first.
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

## Planned: TodoTinder (issue #44)

A mobile-first swipe app living in THIS repo: multiple decks (AcTindervitivities,
VacayTinderation, SwoppingSwiper, DateNighTinders), right swipe creates an item in the
deck's linked todo list via the existing API, gated by the existing token auth (the two
account holders only). Grocery deck recycles staples every run; idea decks deplete per
user and offer a copyable POST line to have a Claude session refill the deck.

The design session is DONE: the settled spec is a comment on #44 and the work is
split into #56 (V8 schema plus API), #57 (the four datasets), #58 (the swipe PWA
served by Javalin at `tinder.todolist.patrickrobel.dk`) and #59 (refill import
endpoint). Read those before writing code. This supersedes the Activity Tinder
note in `patr7257/BoredAPIActivityWheel`.

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
- The one exception is the TodoTinder swipe app (#58), which is served as static
  files by this API on its own subdomain. It is a separate product surface, not
  the todo UI.

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
  which is why the fun counters live at `/api/todo/counters`.
- **Public share links (#52) live at `/api/todo/share/{token}`, and that is the
  ONLY unauthenticated route in the API.** Management is separate and
  authenticated: `GET`/`POST /api/todo/lists/{id}/shares` and
  `DELETE /api/todo/lists/{id}/shares/{shareId}`. The singular/plural split is
  load-bearing, not cosmetic: `dk.dtu.api.auth.AuthFilter` holds an explicit
  allowlist (exact `/api/todo/login`, exact `/api/todo/logout`, prefix
  `/api/todo/share/`), and `share` singular appears in exactly one path in the
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
