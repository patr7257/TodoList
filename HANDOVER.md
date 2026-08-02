# HANDOVER

## Date, branch, PR, CI
- 2026-08-03. Branch: `main`. PR #48 (issues #45 + #46) squash-merged as `6c8c35e`; the auto-release for that merge published a new client installer and Dokploy redeployed the API. This handover rides in the follow-up docs-only PR (which skips the release by design).
- Worktrees `TodoList-45` and `TodoList-46` and the branches `feat/list-owner-fk`, `feat/dashboard-front-page`, `feat/owner-fk-and-dashboard` still exist, deliberately: branch and worktree deletion needs an explicit go-ahead from Patrick.

## TLDR of session outcome
Two features shipped, both halves of a two-client product only half covered:

- **#45**: `lists.owner` was free text while `items.assignee_id` was already a real FK. It now has `lists.owner_id uuid REFERENCES users(id)` (`V3`) with an idempotent, ambiguity-safe backfill (`V4`). The legacy `owner` text column is KEPT and dual-written as a display name. `POST/PATCH /api/todo/lists` accept `ownerId` (validated to an existing user, so a bogus id is a 400 rather than a 500 from an FK violation). The desktop owner ComboBox now compares and writes by user id instead of round-tripping a display name, and there are new always-visible "Only my lists" / "Only my tasks" checkboxes persisted per user.
- **#46**: a new `B2_Dashboard` front page loads after login before the lists view, with six live stats derived client-side from the existing state payload, plus a new shared `fun_counters` table (`V5`) and a full CRUD resource at `/api/todo/counters` with relative bumps, reorder and a tile UI.
- **#43** (multi-agent battle-test) ran for real as part of this: two `implementer` agents in isolated worktrees, then a headless `integration-verifier`. Metrics and verdict are in the retro comment on #43.
- **#47** opened for the desktop-only gap, and the actual web implementation is tracked in `patr7257/PatrickRobelWeb#162`.

## Prioritized next steps
1. **Finish the web edition** (`patr7257/PatrickRobelWeb#162`, branch `feat/todo-web-dashboard`, worktree `../PatrickRobelWeb-162`): dashboard at `/todo` with the lists app moved to `/todo/lists`, the same six stats, and counter CRUD through new proxy routes. The API side needs nothing.
2. **Click-test the desktop UI once.** The headless verifier explicitly could not cover the owner ComboBox, the two "only mine" checkboxes, or the dashboard tiles rendering. Unit tests and static review cover the logic; a human click-through is still the only evidence for the UI itself.
3. Consider **TestFX** (test-scope, headless via Monocle) so desktop UI can be verified without a human and without touching the desktop. Nothing automated can drive the JavaFX GUI today.
4. Follow-ups worth issues: `ItemsController.readAssignee` still accepts any non-empty string as `assigneeId`, so a bogus id becomes a 500 (the same bug `ownerId` just fixed); and `ClientApp` copies the saved `ServerPrefs` URL into the `todolist.api.url` system property before `Config` is read, so `TODOLIST_API_URL` can never win once a URL has been saved, which is how a local test silently talks to production.

## Verbatim resume commands (PowerShell)
Start a throwaway local Postgres (the API does NOT start one, see the Migrations section in CLAUDE.md):
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; .\scripts\dev-db.ps1
```
Run the API against it:
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; $env:DATABASE_URL='postgres://postgres:todo@localhost:5433/todo'; $env:TODO_SESSION_SECRET='dev-secret'; mvn -pl api exec:java
```
Run the desktop client (defaults to the live API; the connect dialog sets and remembers a different base URL):
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; mvn -q install -DskipTests; mvn -pl client javafx:run
```
Load the owner-backfill fixture into the local database (needs the API to have started once, so Flyway has created the schema):
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; .\scripts\dev-db.ps1 -Fixture
```
Tear the local database down:
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; .\scripts\dev-db.ps1 -Stop
```

## Gotchas discovered this session
- **`mvn -pl api exec:java` does NOT start an embedded Postgres.** CLAUDE.md, README.md and the previous HANDOVER.md all claimed it did. The embedded Postgres is test-scope only; with no `DATABASE_URL` the API starts and every data route answers 503. All three docs are corrected in this PR.
- **Flyway runs with `outOfOrder=false`, and that is a production-outage hazard**, not a style note. Applying V5 before V3 on a scratch database fails with `FlywayValidateException: Detected resolved migration not applied to database: 3`, which at boot means the Dokploy container crash-loops. Two parallel branches adding migrations must have their versions pre-assigned and land in ONE merge.
- **A test that cannot compile in the worktree that owns it is not coverage, it is a promissory note.** #46's `DashboardStatsTest` needed #45's `ListDto` fields, so it never ran in its own worktree, and on the first merged run it immediately caught a real bug: `Dashboard.flattenItems` counted null items, inflating both the task total and the completion divisor. `totalLists` had the same flaw.
- **A live smoke catches what tests do not.** `CountersService.insert` never set `sort`, so every new counter got the column default 0 instead of `max+1`. Its own test asserted key order and defaults but never `sort`.
- **Never pipe a build through `tail`.** `mvn -B clean verify | tail -60` reported exit 0 while the build was `BUILD FAILURE`; only reading the output caught it.
- **Never drive the GUI with synthetic input.** An agent used `AppActivate` + `SendKeys` to log into the JavaFX client; Windows refused the foreground activation, and the keystrokes went into the YouTube video Patrick was watching, toggling captions, theater mode, mute, pause and seek. Use `PrintWindow` screenshots (no focus needed) or a headless path, and note that a forced `SetWindowPos` resize does not trigger a JavaFX re-layout, so a resized capture can show clipping that is an artifact rather than a bug.
- Screenshots had been silently accumulating in `screenshots/` from earlier sessions (24 files, gitignored so never committed). Removed; keep captures in temp.

## Open decisions waiting on Patrick
- Whether to delete the three merged feature branches and the two worktrees (`TodoList-45`, `TodoList-46`). Not done without an explicit instruction.
- Whether to add TestFX for headless desktop UI tests.

## Environment state
- Nothing of ours is running against the desktop: no JavaFX client, no API on 8080, ports 8080 and 3000 free.
- The throwaway Docker Postgres container `todolist-dev-db` (port 5433) may still be up; `.\scripts\dev-db.ps1 -Stop` removes it. Note its data has drifted from the fixture across sessions, so re-run with `-Reset -Fixture` if you need the exact backfill cases.
- Docker Desktop was started manually by Patrick this session, so the new user-level `docker-desktop` skill deliberately leaves it running (it only stops what Claude started).
- `.claude/.codev-ack` is locally modified as usual (one appended line per session).
