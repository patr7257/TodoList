# CLAUDE.md

## What this is

TodoList Management System: a multi-user task manager with a JavaFX desktop
client and a server built on jSpace (a Java tuple space coordination library
from the pSpaces project). Multiple clients connect to one server over TCP,
join shared to do lists, create and reassign tasks, and update task status
(NOT_STARTED / IN_PROGRESS / DELAYED / NEED_HELP / DONE). The server keeps
state consistent under concurrent access from multiple clients and persists
sessions to a JSON file.

This started as a DTU course project ("Project 13, To do list", see the
course template still embedded in old commits) built around tuple space
coordination concepts, and was later moved into this personal repo and
cleaned up into a generic, installable client/server app (public v1 release,
generic defaults, no course-specific branding left in the current README).

## Module layout (Maven multi-module, groupId `com.patr7257`)

- `pom.xml`: parent POM, packaging `pom`, Java 21 (`maven.compiler.release`),
  declares the three modules below plus shared versions for JUnit 5.11.4 and
  JavaFX 21.0.5.
- `shared/` (`todolist-shared`): shared constants, config, and JSON models
  used by both client and server.
  - `dk.dtu.shared.Config`: runtime configuration (server IP/host/port, data
    directory) read from system properties with environment variable
    fallback, plus the jSpace gate/client URIs.
  - `dk.dtu.shared.TupleSpaces`: tuple space names (`requests`, `responses`,
    `lists`, `tasks`, `users`, `notifications`) and the command/response
    string constants used as the first field of every request/response
    tuple (fixed arity 6: `(cmd/status, requestId, a1, a2, a3, a4)`).
  - `dk.dtu.shared.TaskStatus`: task status enum with a completion percentage
    per status.
  - `dk.dtu.shared.models`: `SessionData`, `TaskData`, `TodoListData`,
    `UserData` (Gson-serializable JSON models).
- `server/` (`todolist-server`): jSpace server. Main class `dk.dtu.ServerApp`.
  - `ServerEngine`: starts the jSpace `SpaceRepository`, creates the tuple
    spaces, opens a TCP gate (`Config.getServerGateUri()`), loads or seeds a
    session, and runs a request loop thread; `stop()`/`close()` saves the
    session and shuts the gate down cleanly.
  - `ServerHandlerService`: the request loop, reads request tuples and
    writes response tuples, and broadcasts a `data_changed` notification.
  - `PersistenceService`: loads/saves `session.json`.
  - `Database`: seeds a small demo dataset when no session exists yet.
  - `ServerMain`/`ServerWelcomeScreen`/`ServerConfigDialog`: server-side GUI
    for starting/stopping and configuring the server.
- `client/` (`todolist-client`): JavaFX desktop client. Main class
  `dk.dtu.ClientApp`.
  - `dk.dtu.scenes`: `A_WelcomeScreen`, `B_LoginScreen`, `C_MainMenu`,
    `D_TodoListView` (letter-prefixed to show screen flow order).
  - `dk.dtu.methods`: `DataManagement`, `Lists`, `Tasks`, `Users`, `Helpers`,
    the client-side operations that build request tuples, send them via
    jSpace `RemoteSpace`, and read the matching response tuples.
  - `dk.dtu.collumns`: JavaFX `TableView` column/cell classes for the lists
    and tasks tables (note the module name keeps this spelling).
  - `NotificationListener`: listens for `data_changed` and refreshes the
    current view. `SceneNavigator` switches between scenes.
  - `ClientConnectDialog`, `SettingsDialog`, `MainUserConfig`,
    `DarkModeManager`: connection setup, settings, and dark mode support.

Both `server` and `client` depend on `todolist-shared` and on
`io.github.pspaces.jspace:common:1.0-SNAPSHOT` (jSpace).

## Build and run

Prerequisites: JDK 21, Maven, and jSpace built locally, since jSpace is not
published to Maven Central and the parent POM has no extra repository
declared for it. Before the first build, clone and install it into the local
Maven repository:

```powershell
git clone https://github.com/pSpaces/jSpace.git
cd jSpace
mvn -B install -DskipTests
```

(the CI workflow, `.github/workflows/build-installers.yml`, does exactly
this before every build).

Build everything from the repo root:

```powershell
mvn clean install
```

(the README's own troubleshooting note says to use `install`, not just
`package`, so the `shared` module's jar is resolvable by `server`/`client`).

Run locally, from the repo root, in two terminals. Do NOT add `-am`: with
`-am` the direct `exec:java` / `javafx:run` goal also runs on the parent
aggregate module and fails ("parameters 'mainClass' ... are missing"). Run
`mvn -q install -DskipTests` first so the module's dependencies resolve, then:

```powershell
mvn -pl server exec:java
```

```powershell
mvn -pl client javafx:run
```

Connecting to a server on another machine: use the in-app "Change Server"
dialog. Passing `-Dtodolist.server.ip` through `javafx:run` does NOT work: the
javafx-maven-plugin 0.0.6 execution config ignores both `-Djavafx.run.jvmArgs`
and `-Djavafx.options` from the CLI, and the client also prefers the last
successfully used server, persisted via `ServerPrefs` (Java Preferences,
registry key `HKCU:\Software\JavaSoft\Prefs\dk\dtu`, values `server.ip` /
`server.port`); an explicit system property would win over the saved value,
but only if it actually reaches the JVM (it does in the packaged installers,
which bake `--java-options` in).

Configuration, via JVM system properties or environment variables (see
`shared`'s `Config` class):

- `todolist.server.ip` / `TODOLIST_SERVER_IP`: where the client connects
  (default `127.0.0.1`).
- `todolist.port` / `TODOLIST_PORT`: TCP port for jSpace (default `9001`).
- `todolist.bind.host` / `TODOLIST_BIND_HOST`: server bind host (default
  `0.0.0.0`).
- `todolist.data.dir`: where the server stores `session.json` (default
  `%USERPROFILE%\.todolist-data`).

On first run, with no existing `session.json`, the server seeds a small demo
dataset (`Database.loadDatabase`). To reset to demo data on Windows: stop the
server, delete `%USERPROFILE%\.todolist-data` (or just `session.json` inside
it), and start the server again.

## Tests

JUnit 5 (Jupiter 5.11.4) tests live under each module's `src/test/java`:

- `shared`: `TaskStatusTest`, `TupleSpacesTest`.
- `server`: `PersistenceServiceTest`, `ServerHandlerServiceTest`,
  `ServerMainTest`, `ServerRemotePingTest`.
- `client`: `HelpersTest`, `ListsTest`, `NotificationListenerTest`,
  `TasksTest`.

Run all tests from the repo root with `mvn test` (jSpace must already be
installed locally, see Build above).

## Packaging installers

`build-installers.ps1` (Windows) and the macOS steps in `README.md` use
`jpackage` to build native installers (MSI on Windows, DMG on macOS) that
bundle a JRE, so end users do not need Java installed. `.github/workflows/
build-installers.yml` builds Windows and macOS installers automatically on
every push of a `v*` tag (and on manual `workflow_dispatch`), and attaches
them to a GitHub Release.

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
  (`TodoList-Client-Windows.msi`, `TodoList-Client-macOS.dmg`, and the server
  ones) so `releases/latest/download/<name>` is a permanent URL the website and
  the in-app updater rely on.
- Client installers default their server host to the `TODOLIST_SERVER_HOST`
  Actions repo variable (the runtime connect dialog still overrides).
- `--win-upgrade-uuid` (client `c70294f3-...`, server `d4b1957b-...`) and
  `--mac-package-identifier` are PERMANENT; changing one orphans installed
  copies. App icon is `client/src/main/resources/Icons/appicon.{ico,png}`.

## Hosting the server

The server can run headless via `dk.dtu.ServerMain` (no JavaFX), which is how it
is hosted. `Dockerfile` + `docker-compose.yml` build and run it; see
`docs/HOSTING.md`. It is deployed on a Dokploy VPS and reachable ONLY over
Tailscale (the app has no auth and no TLS), with the container port bound to the
tailnet IP. `TODOLIST_DATA_DIR` maps to the property `todolist.data.dir`; state
persists in a `/data` volume.

## Auto-update

The client checks for updates on launch (a dismissible banner) and via a
Settings "Updates" tab (`dk.dtu.update.*`): it queries the public GitHub
Releases API anonymously, compares the running version
(`System.getProperty("todolist.version", "dev")`, so no nagging when run from
source) to the latest tag, downloads the platform installer, and runs it
(`msiexec /i` on Windows, `open` on macOS), upgrading in place.

## Notable conventions

- Package root is `dk.dtu` for all three modules (`dk.dtu.shared.*` for the
  shared module), a holdover from the project's DTU course origin; the Maven
  `groupId` is `com.patr7257`.
- Client/server communication is entirely tuple-based: the client writes a
  6-tuple to the `requests` space and blocks on a matching response tuple in
  `responses`; there is no direct RPC. All command/response string constants
  live in `dk.dtu.shared.TupleSpaces`, so grep there first when tracing a
  feature end to end.
- The client pools jSpace connections in `dk.dtu.methods.Spaces`: one open
  `RemoteSpace` per space URI, reused for the session, with a global `IO_LOCK`
  serializing operations (the app is single-user at a time). Use `Spaces.get(uri)`
  instead of `new RemoteSpace(uri)`; the notification listener keeps its own
  dedicated connection OUTSIDE that lock. `Users.getUsersCached` caches the user
  list so owner dropdowns do one query per view, not one per row.
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
  classes; `dk.dtu.ui.WindowChrome` darkens the native Windows title bar via
  the Win32 DWM API (JNA).
- Data changes are pushed to clients as a single generic `data_changed`
  notification (no per-entity notification types); clients react by
  refetching the current view rather than applying incremental updates.
- Session state is persisted as JSON (`session.json`) via Gson, not a
  database.
- The `lists` tuple space also has a backward-compatible alias `todoLists`,
  registered by `ServerEngine`, for older clients/configs.
