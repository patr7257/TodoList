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