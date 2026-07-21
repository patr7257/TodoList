# TodoList Management System

Multi-module Maven project with a JavaFX desktop client and an HTTP API backed
by Postgres.

## Modules

- `shared/`: shared constants/config + JSON models (used by both client and api)
- `api/`: headless HTTP API (Javalin + JDBI + HikariCP over Postgres); main class
  `dk.dtu.api.ApiMain`. Packages to a self-contained fat jar (`todolist-api.jar`).
- `client/`: JavaFX desktop client that talks to the API over HTTPS/JSON.

## How it works (high level)

- The API exposes JSON endpoints for auth, lists, tasks, and a snapshot/state
  endpoint, persisting everything to Postgres.
- The client authenticates against the API, then reads and writes lists and tasks
  over HTTP and polls the state endpoint to refresh the current view.

## Run locally (development)

Prerequisites: JDK 21 and Maven. Build the whole reactor first:

```powershell
mvn -q install -DskipTests
```

1) Start the API. It needs `DATABASE_URL` (a Postgres connection string) and
   `TODO_SESSION_SECRET` in the environment; the `api` module can start an
   embedded Postgres for local dev when no external database is configured:

```powershell
mvn -pl api exec:java
```

2) Start the client (in a separate terminal):

```powershell
mvn -pl client javafx:run
```

Point the client at an API base URL from the in-app connect dialog (it also
remembers the last one used).

## Download the client

Pre-built client installers are attached to every tagged release:

1. Go to the [Releases page](../../releases)
2. Download the installer for your platform:
   - **Windows**: `TodoList Client-<version>.msi` (or the stable
     `TodoList-Client-Windows.msi`)
   - **macOS**: `TodoList Client-<version>.dmg` (or the stable
     `TodoList-Client-macOS.dmg`)

### Installation

**Windows:**
1. Right-click the `.msi` file, then Run as Administrator
2. Follow the installation wizard
3. Launch from the Start Menu shortcut

**macOS:**
1. Double-click the `.dmg` file
2. Drag the app to the Applications folder
3. Launch from Applications or Spotlight

> No Java required. The installer bundles everything needed to run the client.

## API hosting

The API is deployed on a Dokploy VPS, built from `Dockerfile.api`, and exposed
publicly behind Dokploy's Traefik reverse proxy with Let's Encrypt TLS at
`https://api.todolist.patrickrobel.dk`. `DATABASE_URL` and
`TODO_SESSION_SECRET` are provided as Dokploy service environment variables.

## Building the client installer

### Automated builds (GitHub Actions)

`.github/workflows/build-installers.yml` builds the Windows and macOS client
installers on every push of a `v*` tag (and on manual `workflow_dispatch`) and
attaches them to a GitHub Release, including stable versionless asset names.

**To trigger a build:**

```bash
git tag v1.0.1
git push origin v1.0.1
```

### Manual local build (Windows)

Prerequisites:
- JDK 21 (https://adoptium.net/temurin/releases/?version=21)
- Maven (https://maven.apache.org/download.cgi)
- WiX Toolset for MSI (https://wixtoolset.org/)

```powershell
.\build-installers.ps1
```

Output: `dist\run-<timestamp>\TodoList Client-1.0.0.msi`.
