TodoList Management System

Modules
- shared/: shared constants/config + JSON models (used by both client and server)
- server/: jSpace server (opens a TCP gate and persists session data)
- client/: JavaFX desktop client (connects to server via jSpace RemoteSpace)

Run (Java)
From the repo root:
1) Start server UI:
   mvn -pl server -am javafx:run
2) Start client UI (new terminal):
   mvn -pl client -am javafx:run

Build executables (short)
Windows (from repo root):
- EXE installers:
  & .\build-installers.ps1 -Type exe
- MSI installers (requires WiX Toolset):
  & .\build-installers.ps1 -Type msi
Output is in: dist\

macOS (build on a Mac):
1) Build:
   mvn clean package -DskipTests
2) Package (examples):
   jpackage --input server/target --name "TodoList Server" --main-jar todolist-server-ui.jar --main-class dk.dtu.ServerApp --type dmg
   jpackage --input client/target --name "TodoList Client" --main-jar todolist-client-ui.jar --main-class dk.dtu.ClientApp --type dmg

Configuration (short)
- todolist.server.ip  (client connects to this; default 127.0.0.1)
- todolist.port       (default 9001)
- todolist.bind.host  (server bind host; default 0.0.0.0)
- todolist.data.dir   (server session.json location)
