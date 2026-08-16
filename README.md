# TodoList Management System

The headless HTTP API behind the TodoList product, backed by Postgres.

The front end is **not** in this repo. Since issue #66 the only client is the web
edition at `/todo` in `patr7257/PatrickRobelWeb`, which is also the phone
experience via its installable PWA. This repo owns the data model, the API and
the migrations; that repo owns everything a user looks at.

## Modules

- `shared/`: `dk.dtu.shared.TaskStatus`, the task status enum plus its completion
  percentage per status.
- `api/`: headless HTTP API (Javalin + JDBI + HikariCP over Postgres); main class
  `dk.dtu.api.ApiMain`. Packages to a self-contained fat jar (`todolist-api.jar`).

## How it works (high level)

- The API exposes JSON endpoints for auth, lists, tasks, counters, share links,
  and a snapshot/state endpoint, persisting everything to Postgres (Neon in
  production).
- Sign in happens on the website (passkeys or a mailed magic link). The website
  mints the `todo_session` token in the byte-identical HMAC format
  `dk.dtu.api.auth.Token` verifies, so the two halves stay interchangeable.
- Clients read and write over HTTPS/JSON and re-read the state endpoint to
  refresh, rather than applying incremental updates.

## Run locally (development)

Prerequisites: JDK 21 and Maven. Build the whole reactor first:

```powershell
mvn -q install -DskipTests
```

The API needs `DATABASE_URL` (a Postgres connection string) and
`TODO_SESSION_SECRET` in the environment. It does NOT start a database for you:
the embedded Postgres here is test-scope only, so without `DATABASE_URL` every
data route answers 503. Never point it at production Neon, because startup runs
Flyway and would apply unmerged migrations there. Start a throwaway local
Postgres first, then the API:

```powershell
.\scripts\dev-db.ps1
$env:DATABASE_URL='postgres://postgres:todo@localhost:5433/todo'; $env:TODO_SESSION_SECRET='dev-secret'; mvn -pl api exec:java
```

Tear it down again with `.\scripts\dev-db.ps1 -Stop`.

Run the tests with `mvn test` from the repo root.

## Hosting

The API is deployed on a Dokploy VPS, built from `Dockerfile.api`, and exposed
publicly behind Dokploy's Traefik reverse proxy with Let's Encrypt TLS at
`https://api.todolist.patrickrobel.dk`. `DATABASE_URL` and
`TODO_SESSION_SECRET` are provided as Dokploy service environment variables.

Every merge to `main` redeploys that container, which runs Flyway against
production Neon on boot. A merge is a ship.

## The retired desktop client

A JavaFX desktop client used to live in `client/`, packaged with jpackage into an
MSI and a DMG and auto-updated from GitHub Releases. Issue #66 removed it so that
product changes only ever have to be made once, on the web edition.

- The last installer release, **v2.0.8**, stays on the
  [Releases page](../../releases). An already-installed copy keeps working
  against this API, which did not change; it simply stops receiving updates.
- The code is not gone, only untracked from `main`: `git log -- client/` and
  `git show 7a874a2:client/...` still reach it.
