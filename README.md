# TodoList Management System

Multi-module Maven project with a JavaFX client and a jSpace (tuple space) server.

## Modules

- `shared/`: shared constants/config + JSON models (used by both client and server)
- `server/`: jSpace server (opens a TCP gate and persists session data)
- `client/`: JavaFX desktop client (connects to server via jSpace `RemoteSpace`)

## How it works (high level)

- The server creates tuple spaces (`requests`, `responses`, `users`, `tasks`, `todoLists`, `notifications`) and opens a TCP gate.
- The client connects to the server over TCP and uses request/response tuples for operations.
- The server broadcasts a simple `data_changed` notification; the client listens and refreshes the current view.

## Run locally (development)

From the repo root:

1) Start the server:

```powershell
mvn -pl server -am exec:java
```

2) Start the client (in a separate terminal):

```powershell
mvn -pl client -am javafx:run
```

## Configuration (important for packaging)

These can be set as JVM system properties (or environment variables):

- `todolist.server.ip` (env: `TODOLIST_SERVER_IP`) – where the client connects (default: `127.0.0.1`)
- `todolist.port` (env: `TODOLIST_PORT`) – TCP port for jSpace (default: `9001`)
- `todolist.bind.host` (env: `TODOLIST_BIND_HOST`) – server bind host (default: `0.0.0.0`)
- `todolist.data.dir` – where the server stores `session.json` (default: `%USERPROFILE%\.todolist-data`)

Example (client connects to a server on another PC):

```powershell
mvn -pl client -am javafx:run -Djavafx.run.jvmArgs="-Dtodolist.server.ip=192.168.0.168"
```

## Download Installers

**Pre-built installers are automatically created for every release:**

1. Go to the [Releases page](../../releases)
2. Download the installer for your platform:
   - **Windows**: `TodoList Client-1.0.0.msi` and `TodoList Server-1.0.0.msi`
   - **macOS**: `TodoList Client-1.0.0.dmg` and `TodoList Server-1.0.0.dmg`

### Installation

**Windows:**
1. Right-click the `.msi` file → Run as Administrator
2. Follow the installation wizard
3. Launch from Start Menu shortcuts

**macOS:**
1. Double-click the `.dmg` file
2. Drag the app to Applications folder
3. Launch from Applications or Spotlight

> **No Java required!** The installers bundle everything needed to run the application.

---

## Building Installers Locally

### Automated Builds (GitHub Actions)

The project uses GitHub Actions to automatically build installers for both Windows and macOS. Every push to `main` creates artifacts that can be downloaded from the Actions tab. Tagged releases (e.g., `v1.0.0`) automatically create a GitHub Release with installers attached.

**To trigger a build:**
```bash
git tag v1.0.1
git push origin v1.0.1
```

### Manual Local Builds

#### Windows

**Prerequisites:**
- JDK 21 (download from https://adoptium.net/temurin/releases/?version=21)
- Maven (download from https://maven.apache.org/download.cgi)
- WiX Toolset for MSI (download from https://wixtoolset.org/)

**Build MSI installers:**
```powershell
.\build-installers.ps1
```

Output: `dist\run-<timestamp>\TodoList Client-1.0.0.msi` and `TodoList Server-1.0.0.msi`

**Build with debug mode** (for troubleshooting):
```powershell
.\build-installers.ps1 -WinConsole -Debug
```
This enables console windows and detailed logging to `%LOCALAPPDATA%\TodoList\logs\`.

**Test installed applications:**
```powershell
# Run as Administrator
.\install-and-test.ps1 -App server
.\install-and-test.ps1 -App client
```

**Diagnose MSI structure** (without installing):
```powershell
.\diagnose-installers.ps1
```

**Diagnose installed applications** (after installation):
```powershell
.\diagnose-installed-apps.ps1
```

**If apps don't launch, check error logs:**
1. Run from PowerShell to see console output:
   ```powershell
   cd "C:\Program Files\TodoList Server"
   ."TodoList Server.exe"
   ```

2. Check Windows Event Viewer:
   - Press `Win+X` → **Event Viewer**
   - Navigate to **Windows Logs** → **Application**
   - Look for errors from TodoList applications

> **Note**: Release installers now include `--win-console` flag so you'll see a console window with error messages if something goes wrong.

#### macOS

**Prerequisites:**
- JDK 21
- Maven

**Build DMG installers:**
```bash
mvn clean install -DskipTests

jpackage \
  --input server/target \
  --name "TodoList Server" \
  --main-jar todolist-server-1.0.0.jar \
  --main-class dk.dtu.ServerApp \
  --type dmg \
  --app-version 1.0.0

jpackage \
  --input client/target \
  --name "TodoList Client" \
  --main-jar todolist-client-1.0.0.jar \
  --main-class dk.dtu.ClientApp \
  --type dmg \
  --app-version 1.0.0
```
