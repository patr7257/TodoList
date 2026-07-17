# Hosting the TodoList server

This guide covers running the headless TodoList server in a container, for
example on a VPS managed with Dokploy.

## What gets hosted

Only the server is hosted. It opens a raw TCP jSpace gate on port `9001` and
persists all state to `session.json` inside its data directory. Clients (the
JavaFX desktop app) connect to it over that TCP port.

The container runs `dk.dtu.ServerMain`, the headless entry point. It starts the
server engine directly and never initializes JavaFX, so it runs on a plain
headless JRE with no display.

## Security model: Tailscale only, no auth, no TLS

The server has NO authentication and NO TLS. Anyone who can reach port `9001`
has full read/write access to every list and task. Because of that:

- The server must be reachable ONLY over a private Tailscale tailnet.
- Never publish the port on a public interface (`0.0.0.0`).
- Dokploy's Traefik reverse proxy does not apply here: jSpace is raw TCP, not
  HTTP, so there is no HTTP route to protect. The port is reached directly.

Clients connect by pointing `todolist.server.ip` (or `TODOLIST_SERVER_IP`) at
the server's tailnet IP. The installers can bake a default server host in at
build time (see the repo's build tooling), and the client's connect dialog can
still override it at runtime.

## Running with docker-compose (Dokploy)

1. Install Tailscale on the VPS and bring it up, then find the VPS's own tailnet
   IP:

   ```bash
   tailscale ip -4
   ```

   This prints a `100.x.y.z` address. That is the value for `TAILSCALE_IP`.

2. Set `TAILSCALE_IP` for the compose deployment. In Dokploy, add it as an
   environment variable for the service. Locally you can put it in a `.env` file
   next to `docker-compose.yml`:

   ```
   TAILSCALE_IP=100.x.y.z
   ```

3. Build and start the server:

   ```bash
   docker compose up -d --build
   ```

   The port publish is `"${TAILSCALE_IP}:9001:9001"`, so the gate binds only to
   the tailnet IP. Other tailnet devices can connect; the public internet cannot.

4. Check the logs. You should see the server start and begin waiting for
   requests:

   ```bash
   docker compose logs -f server
   ```

## Running with plain docker

```bash
docker build -t todolist-server .
docker run -d --name todolist-server \
  -p 100.x.y.z:9001:9001 \
  -v todolist-data:/data \
  todolist-server
```

Replace `100.x.y.z` with the VPS's Tailscale IP. Never use `-p 9001:9001`
(that binds `0.0.0.0` and exposes the unauthenticated server publicly).

## Configuration

The container sets sensible defaults; override with environment variables if
needed:

- `TODOLIST_BIND_HOST` (default `0.0.0.0`): interface the server binds inside
  the container. Leave at `0.0.0.0`; the host port publish is what limits
  exposure.
- `TODOLIST_PORT` (default `9001`): TCP port for the jSpace gate.
- `TODOLIST_DATA_DIR` (default `/data`): where `session.json` is stored.

## Data and backups

All state lives in a single file: `session.json` inside the data directory
(`/data/session.json` in the container, on the `todolist-data` named volume).

On first start with no existing session, the server seeds a small demo dataset
and writes it out. After that it loads the saved session on every start.

To back up, copy `session.json` off the volume, for example:

```bash
docker cp todolist-server:/data/session.json ./session-backup.json
```

To restore, stop the server, copy a `session.json` back into `/data`, and start
the server again. To reset to demo data, delete `/data/session.json` and
restart.
