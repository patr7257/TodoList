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
- `shared/` (`todolist-shared`): shared constants, config, and JSON models used
  by both client and api.
  - `dk.dtu.shared.Config`: runtime configuration read from system properties
    with environment variable fallback.
  - `dk.dtu.shared.TaskStatus`: task status enum with a completion percentage
    per status.
  - `dk.dtu.shared.models`: `SessionData`, `TaskData`, `TodoListData`,
    `UserData` (Gson-serializable JSON models).
  - NOTE: `shared` still carries some now-unused legacy jSpace constants
    (`dk.dtu.shared.TupleSpaces` and the jSpace URI methods on `Config`). They
    compile fine as dead String constants and are scheduled for removal in
    issue #29; do not build new features on them.
- `api/` (`todolist-api`): headless HTTP API. Main class `dk.dtu.api.ApiMain`.
  Built with Javalin (HTTP), JDBI + HikariCP over Postgres, and Gson for JSON.
  Packages to a self-contained shaded fat jar (`todolist-api.jar`).
  - `dk.dtu.api.web`: `ApiServer` and the controllers (`AuthController`,
    `ListsController`, `ItemsController`, `StateController`), plus `Backend`,
    `RateLimiter`, and JSON/error helpers.
  - `dk.dtu.api.auth`: token auth (`AuthFilter`, `AuthService`, `Token`,
    `Scrypt`).
  - `dk.dtu.api.db`: `DataSources` (Hikari pool) and `Migrations`.
  - `dk.dtu.api.domain`: `TodoService` and the row/value types it maps.
- `client/` (`todolist-client`): JavaFX desktop client. Main class
  `dk.dtu.ClientApp`.
  - `dk.dtu.net`: the HTTP transport. `TodoApiClient` (raw HTTP + JSON),
    `ApiSession` (process-wide session: client, bearer token, signed-in user,
    user set), `StatePoller` (polls the API state endpoint to refresh the view),
    `ApiModels`, `ApiException`.
  - `dk.dtu.scenes`: `A_WelcomeScreen`, `B_LoginScreen`, `C_MainMenu`,
    `D_TodoListView` (letter-prefixed to show screen flow order).
  - `dk.dtu.methods`: `DataManagement`, `Lists`, `Tasks`, `Users`, `Helpers`,
    the client-side operations that call the API via `ApiSession` /
    `TodoApiClient`. (Some of these still carry now-ignored `requestsUri` /
    `responsesUri` parameters left over from the jSpace era; removing that dead
    threading is tracked in issue #29.)
  - `dk.dtu.collumns`: JavaFX `TableView` column/cell classes for the lists and
    tasks tables (note the module name keeps this spelling).
  - `dk.dtu.update`: on-launch and Settings-tab auto-update.
  - `ClientConnectDialog`, `SettingsDialog`, `MainUserConfig`,
    `DarkModeManager`: connection setup, settings, and dark mode support.

Both `api` and `client` depend on `todolist-shared`. Nothing depends on jSpace
anymore.

## Build and run

Prerequisites: JDK 21 and Maven. No jSpace install is needed anymore.

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
`TODO_SESSION_SECRET` from the environment; for local dev the `api` module can
start an embedded Postgres when no external database is configured:

```powershell
mvn -pl api exec:java
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

- `shared`: `TaskStatusTest`, `TupleSpacesTest`.
- `api`: HTTP/service tests for the api module.
- `client`: `HelpersTest`, `ListsTest`, `NotificationListenerTest`, `TasksTest`,
  and `net/StatePollerTest`.

Run all tests from the repo root with `mvn test`.

## Packaging installers

`build-installers.ps1` (Windows) and the macOS steps in `README.md` use
`jpackage` to build a native CLIENT installer (MSI on Windows, DMG on macOS)
that bundles a JRE, so end users do not need Java installed.
`.github/workflows/build-installers.yml` builds the Windows and macOS client
installers automatically on every push of a `v*` tag (and on manual
`workflow_dispatch`), and attaches them to a GitHub Release. Only the client is
packaged: the API is a hosted service, not an installer.

- The jlinked runtime must include every JDK module the app touches, not just
  the JavaFX ones. The CI `--add-modules` list therefore names `java.logging`
  (Ikonli and JNA use `java.util.logging`), `java.naming`, `java.sql`,
  `java.management`, `jdk.crypto.ec`, `java.instrument`, etc., plus the JavaFX
  modules. Adding a client dependency that needs a new JDK module means adding
  that module here too: a missing one makes the packaged app crash silently at
  startup (`NoClassDefFoundError`) even though `mvn javafx:run` works fine.
- The client jpackage step also passes
  `--add-opens javafx.controls/javafx.scene.control.skin=ALL-UNNAMED` so the
  "Auto-fit columns" reflection into the TableView skin works in the packaged
  build (the same option is in `client/pom.xml` for `mvn javafx:run`).

Release conventions (do not break these):
- The version comes from the git tag: a `Compute version` step strips the
  leading `v` and feeds it to `--app-version` AND `--java-options
  -Dtodolist.version=...` (macOS rejects a leading-zero major, so the non-tag
  fallback is `1.0.0`).
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

## Auto-update

The client checks for updates on launch (a dismissible banner) and via a Settings
"Updates" tab (`dk.dtu.update.*`): it queries the public GitHub Releases API
anonymously, compares the running version
(`System.getProperty("todolist.version", "dev")`, so no nagging when run from
source) to the latest tag, downloads the platform installer, and runs it
(`msiexec /i` on Windows, `open` on macOS), upgrading in place.

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
- The API persists state to Postgres via JDBI; schema is applied by
  `dk.dtu.api.db.Migrations`.
