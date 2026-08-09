# CLAUDE.md

## What this is

TodoList Management System: a multi-user task manager with a JavaFX desktop
client and a headless HTTP API backed by Postgres. Multiple clients talk to one
API over HTTPS/JSON, join shared to do lists, create and reassign tasks, and
update task status (NOT_STARTED / IN_PROGRESS / DELAYED / NEED_HELP / DONE). The
API keeps state consistent under concurrent access and persists everything to
Postgres (Neon in production).

This started as a DTU course project ("Project 13, To do list", see the course
template still embedded in old commits) built around tuple space coordination
concepts. It was later moved into this personal repo, cleaned up into a generic
installable app, and then migrated off the original jSpace tuple space transport
onto the HTTP API described here. The jSpace server module has been retired
(issue #25).

## Module layout (Maven multi-module, groupId `com.patr7257`)

- `pom.xml`: parent POM, packaging `pom`, Java 21 (`maven.compiler.release`),
  declares the three modules below plus shared versions for JUnit 5.11.4 and
  JavaFX 21.0.5.
- `shared/` (`todolist-shared`): shared constants and config used by both
  client and api.
  - `dk.dtu.shared.Config`: runtime configuration read from system properties
    with environment variable fallback (API base URL, connection-error
    handling).
  - `dk.dtu.shared.TaskStatus`: task status enum with a completion percentage
    per status.
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
- `client/` (`todolist-client`): JavaFX desktop client. Main class
  `dk.dtu.ClientApp`.
  - `dk.dtu.net`: the HTTP transport. `TodoApiClient` (raw HTTP + JSON),
    `ApiSession` (process-wide session: client, bearer token, signed-in user,
    user set), `StatePoller` (polls the API state endpoint to refresh the view),
    `ApiModels`, `ApiException`.
  - `dk.dtu.scenes`: `A_WelcomeScreen`, `B_LoginScreen`, `B2_Dashboard`,
    `C_MainMenu`, `D_TodoListView` (letter-prefixed to show screen flow order).
    `B2_Dashboard` is the post-login landing page and sits between login and the
    lists view; it is named `B2_` rather than renumbering `C_`/`D_`, which would
    have churned two large scene files for nothing.
  - `dk.dtu.methods`: `Lists`, `Tasks`, `Users`, `Helpers`, the client-side
    operations that call the API via `ApiSession` / `TodoApiClient`, plus
    `Counters` (counter CRUD), `Dashboard` (pure stat derivation, "now" injected
    so it is unit-testable) and `Filters` (the "only mine" predicate, which fails
    OPEN when no signed-in user is resolvable so an empty table never
    masquerades as lost data).
  - `dk.dtu.collumns`: JavaFX `TableView` column/cell classes for the lists and
    tasks tables (note the module name keeps this spelling).
  - `dk.dtu.update`: on-launch and Settings-tab auto-update.
  - `ClientConnectDialog`, `SettingsDialog`, `MainUserConfig`,
    `DarkModeManager`: connection setup, settings, and dark mode support.

Both `api` and `client` depend on `todolist-shared`.

## Build and run

Prerequisites: JDK 21 and Maven.

Build everything from the repo root:

```powershell
mvn clean install
```

(use `install`, not just `package`, so the `shared` module's jar is resolvable
by `api` / `client`).

Run locally, from the repo root, in two terminals. Do NOT add `-am`: with `-am`
the direct `exec:java` / `javafx:run` goal also runs on the parent aggregate
module and fails ("parameters 'mainClass' ... are missing"). Run
`mvn -q install -DskipTests` first so each module's dependencies resolve, then:

Start the API. It reads `DATABASE_URL` (Postgres connection string) and
`TODO_SESSION_SECRET` from the environment.

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

Start the client (separate terminal):

```powershell
mvn -pl client javafx:run
```

Point the client at an API base URL via the in-app connect dialog; it also
remembers the last one used, persisted via `ServerPrefs` (Java Preferences,
registry key `HKCU:\Software\JavaSoft\Prefs\dk\dtu`).

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
  object: that is the regression guard for the separate website client.
  `ShareViewsTest` plus `SharesIntegrationTest` do the same for the public
  share payload, and additionally assert what is ABSENT from it, which is the
  contract that matters for the API's only unauthenticated output.
- `client`: `HelpersTest`, `ListsTest`, `TasksTest`, `ViewPrefsTest`,
  `FiltersTest`, `DashboardStatsTest`, `CountersTest`, `net/StatePollerTest`,
  `net/TodoApiClientTest`, `net/CounterClientTest`. No JavaFX is instantiated in
  tests; scene logic is extracted into pure helpers so it can be tested at all.

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

- `baselineOnMigrate=true` with `baselineVersion=1`, because production Neon
  already held the V1 schema when Flyway was introduced.
- **`outOfOrder` is at its default `false`, and this has teeth.** If a higher
  version reaches production before a lower one, `flyway.migrate()` throws at
  boot, the Dokploy container crash-loops, and the live API is down for the
  desktop clients AND the website. This was proven, not assumed: applying V5
  before V3 on a scratch database fails with
  `FlywayValidateException: Detected resolved migration not applied to database: 3`.
  Consequence: when two branches each add a migration, pre-assign the numbers and
  land them in ONE merge, or renumber the second branch's own file before it
  merges. Never hand-edit `flyway_schema_history`.
- Every migration must be additive and idempotent (`ADD COLUMN IF NOT EXISTS`,
  `CREATE TABLE IF NOT EXISTS`, backfills guarded by `WHERE ... IS NULL`), and
  must never DROP, rename or retype anything. After a migration has been applied
  to production, NEVER edit it: a checksum mismatch crash-loops the container.
  Corrections ship as a new version.

## Packaging installers

`build-installers.ps1` (Windows) and the macOS steps in `README.md` use
`jpackage` to build a native CLIENT installer (MSI on Windows, DMG on macOS)
that bundles a JRE, so end users do not need Java installed. Only the client is
packaged: the API is a hosted service, not an installer.

CI (two workflows):
- `.github/workflows/ci.yml` runs on every pull request: full reactor build +
  tests plus the installer-module guard. Main only receives verified merges.
- `.github/workflows/build-installers.yml` RELEASES. Auto: every push to `main`
  (i.e. every merged PR) bumps the PATCH of the latest GitHub release, builds
  both installers with that version baked in, and publishes a GitHub Release;
  the in-app updater then offers it to users. Docs-only merges (`*.md`,
  `docs/`, `.claude/`) skip the release. Manual: pushing a `v*` tag releases
  exactly that version (use for minor/major bumps; the next auto release bumps
  from it). Runs are queued via a concurrency group so two merges cannot
  compute the same next version.

- The jlinked runtime must include every JDK module the app touches, not just
  the JavaFX ones (`java.logging` for Ikonli/JNA, `java.naming`, `java.sql`,
  `java.net.http`, `jdk.jfr`, etc.). The list is SINGLE-SOURCED in
  `scripts/installer-modules.txt`, read by both workflow jobs, by
  `build-installers.ps1`, and by the guard
  (`scripts/check-installer-modules.ps1`, jdeps-based, runs in PR CI and before
  every release build). Adding a client dependency that needs a new JDK module
  means adding it THERE: a missing one makes the packaged app crash silently at
  startup (`NoClassDefFoundError`) even though `mvn javafx:run` works fine.
- The client jpackage step also passes
  `--add-opens javafx.controls/javafx.scene.control.skin=ALL-UNNAMED` so the
  "Auto-fit columns" reflection into the TableView skin works in the packaged
  build (the same option is in `client/pom.xml` for `mvn javafx:run`).

Release conventions (do not break these):
- The version comes from the `version` job in `build-installers.yml`: a `v*`
  tag ref is used verbatim (stripped of the `v`), otherwise the latest GitHub
  release's patch is bumped. It feeds `--app-version` AND `--java-options
  -Dtodolist.version=...` (macOS rejects a leading-zero major, so the fallback
  with no releases and no tags is `1.0.0`).
- Each release also gets STABLE, versionless asset copies
  (`TodoList-Client-Windows.msi`, `TodoList-Client-macOS.dmg`) so
  `releases/latest/download/<name>` is a permanent URL the website and the
  in-app updater rely on.
- `--win-upgrade-uuid` (client `c70294f3-...`) and `--mac-package-identifier`
  (`com.patr7257.todolist.client`) are PERMANENT; changing one orphans installed
  copies. App icon is `client/src/main/resources/Icons/appicon.{ico,png}`.

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

## Auto-update

The client checks for updates on launch (a dismissible banner) and via a Settings
"Updates" tab (`dk.dtu.update.*`): it queries the public GitHub Releases API
anonymously, compares the running version
(`System.getProperty("todolist.version", "dev")`, so no nagging when run from
source) to the latest tag, downloads the platform installer, and runs it
(`msiexec /i` on Windows, `open` on macOS), upgrading in place.

## Planned: TodoTinder (issue #44, idea stage, nothing built)

A mobile-first swipe app living in THIS repo: multiple decks (AcTindervitivities,
VacayTinderation, SwoppingSwiper, DateNighTinders), right swipe creates an item in the
deck's linked todo list via the existing API, gated by the existing token auth (the two
account holders only). Grocery deck recycles staples every run; idea decks deplete per
user and offer a copyable POST URL to have a Claude session refill the deck. The full
spec draft and the "Questions for Patrick" checklist live in issue #44; run a design
session against that issue before writing any code. This supersedes the Activity Tinder
note in `patr7257/BoredAPIActivityWheel`.

## MANDATORY: UI work covers BOTH clients

Any UI change to the TodoList product ships for the JavaFX desktop client AND for
the web edition (which is also the phone experience, via the installable PWA).
Do not deliver desktop-only UI and file the web side as a follow-up unless Patrick
explicitly says desktop only.

The two front ends live in DIFFERENT repos: the desktop client is `client/` here,
the web edition is the `/todo` route in `patr7257/PatrickRobelWeb`. So a UI change
usually means two issues on two boards (this repo's board is GitHub Project #7,
the website's is #2) and two pull requests. Plan for that up front.

Backend work is shared and done ONCE in `api/`, then consumed by both. Keep
behaviour rules identical across clients or the two UIs will disagree about the
same data: the overdue rule (due date before today AND status not `DONE`) and the
completion math (average of per-status percentages over ALL items, not an average
of per-list averages) are the two that have already caused divergence.

## Notable conventions

- Package root is `dk.dtu` for all three modules (`dk.dtu.shared.*` for the
  shared module, `dk.dtu.api.*` for the api module), a holdover from the
  project's DTU course origin; the Maven `groupId` is `com.patr7257`.
- Client/API communication is HTTP + JSON via `dk.dtu.net.TodoApiClient`, driven
  through the process-wide `dk.dtu.net.ApiSession`. There is no tuple space and
  no direct RPC. The client refreshes by polling the API state endpoint
  (`dk.dtu.net.StatePoller`) and refetching the current view rather than applying
  incremental updates.
- The client visual layer is AtlantaFX (global Primer theme, swapped
  light/dark) + the "Soft Warm Minimal" brand overlay: `common.css` (structure
  + warm-paper LIGHT tokens, overriding the AtlantaFX `-color-*` looked-up
  colors; serif Georgia display titles, status pills, warm status/band tokens)
  and `theme-warm-dark.css` (warm-charcoal DARK token re-overrides only).
  `DarkModeManager.applyBrand(List<String>)` attaches them in the right order
  and is the ONE way to attach brand styling (dialogs delegate to it). Keep the
  two token blocks in lockstep when adding a color. Vector icons come from
  Ikonli via `dk.dtu.ui.Icons`; the lists/tasks tables are real `TableView`s
  built by the `dk.dtu.ui.Tables` adapter from the `dk.dtu.collumns.*` `Column`
  classes; `dk.dtu.ui.WindowChrome` darkens the native Windows title bar via the
  Win32 DWM API (JNA).
- Per-user view state (filters, column visibility/order/width, sort, manual
  reordering) auto-persists via `dk.dtu.ViewPrefs` (Java Preferences, keyed by
  the signed-in user, local to this machine) and restores on open. New
  view-affecting UI state should go through `ViewPrefs`, not ad-hoc storage.
  Namespacing matters: the "only mine" toggles live as the key `onlyMine` INSIDE
  the existing per-view `filters` map of view ids `lists` and `tasks`, and any
  dashboard state uses `dashboard`-prefixed keys. A save must `load` the view
  first and overwrite only its own slice, or it clobbers column widths and sort.
- **`GET /api/todo/state` is APPEND-ONLY.** The separate website client parses
  that exact payload, so keys may be added at the end of an object but never
  removed, renamed, retyped or reordered. `api/src/test/java/dk/dtu/api/web/ViewsTest.java`
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
  `TODO_SHARE_BASE_URL + "/s/" + token` in `ShareViews.share`. Neither client
  builds a share URL from a token, which makes it structurally impossible for
  the desktop app and the website to show different links for the same share.
  Share tokens come from `dk.dtu.api.domain.ShareTokens` (24 SecureRandom bytes,
  URL-safe base64 without padding: 32 chars, 192 bits). Every share failure
  (unknown, malformed, revoked, expired) answers a byte-identical 404.
- `lists.owner_id uuid REFERENCES users(id)` is the real owner; the legacy
  free-text `lists.owner` column is KEPT and kept in sync as a denormalized
  display name. Two reasons, both load-bearing: the website may read that column
  directly, and because every merge publishes a client installer separately from
  the API redeploy, a freshly auto-updated client can briefly talk to an older
  API, where an `ownerId`-only patch would produce an empty column set and a 400.
  `users.name` is NOT unique, so the backfill resolves only unambiguous matches
  and deliberately leaves the rest NULL to be re-picked by hand.
- Counter bumps are relative in SQL (`value = value + :delta`), not a
  read-modify-write, so two people clicking at once both land.
- Every dialog must be prepared via `DarkModeManager.prepareDialog(dialog,
  owner)`: it sets the owner window + `WINDOW_MODAL` (fixes the macOS "app
  slides away" bug) and attaches brand + dark-mode styling. Never show a bare
  `Dialog`/`Alert` without it.
- The API persists state to Postgres via JDBI; schema is applied by
  `dk.dtu.api.db.Migrations`.
