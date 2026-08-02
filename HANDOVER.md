# HANDOVER

## Date, branch, PR, CI
- 2026-08-03. Branch: `main`, clean. Everything from this session is merged and live.
- **TodoList**: PR #48 (issues #45 + #46) squash-merged as `6c8c35e`, which auto-released **v2.0.4** (all four assets, both permanent `releases/latest/download` URLs verified HTTP 200) and triggered the Dokploy API redeploy. PR #49 (docs) merged as `a33d52b` and correctly SKIPPED the release (`version: success`, both build jobs `skipped`, no v2.0.5).
- **PatrickRobelWeb**: PR #166 (issue #165) squash-merged as `fe59929`, Vercel production deploy succeeded.
- All worktrees removed and all feature branches deleted, local and remote, in both repos except where noted under Environment state.

## TLDR of session outcome
Two features shipped, both halves of a two-client product only half covered:

- **#45**: `lists.owner` was free text while `items.assignee_id` was already a real FK. It now has `lists.owner_id uuid REFERENCES users(id)` (`V3`) with an idempotent, ambiguity-safe backfill (`V4`). The legacy `owner` text column is KEPT and dual-written as a display name. `POST/PATCH /api/todo/lists` accept `ownerId` (validated to an existing user, so a bogus id is a 400 rather than a 500 from an FK violation). The desktop owner ComboBox now compares and writes by user id instead of round-tripping a display name, and there are new always-visible "Only my lists" / "Only my tasks" checkboxes persisted per user.
- **#46**: a new `B2_Dashboard` front page loads after login before the lists view, with six live stats derived client-side from the existing state payload, plus a new shared `fun_counters` table (`V5`) and a full CRUD resource at `/api/todo/counters` with relative bumps, reorder and a tile UI.
- **The web edition shipped too**, so both clients now have the feature: `/todo` on the website is the dashboard, the lists app moved to `/todo/lists`, and counters have full CRUD through new passthrough proxy routes (`patr7257/PatrickRobelWeb#165`). Verified live: `/todo` and `/todo/lists` gate to login, and `GET /api/todo/counters` returns 401 from the upstream Java API, which proves the proxy reaches the real API.
- **#43** (multi-agent battle-test) ran for real as part of this: two `implementer` agents in isolated worktrees, then a headless `integration-verifier`. Metrics, verdict and the agent-definition gaps are in the retro comment on #43, with a cross-repo summary on `patr7257/RoboRally02162#27`. Issue closed.
- **#47** remains open as the desktop-side tracker for the web parity work now that it has shipped; close it or repurpose it.

## Prioritized next steps
1. **Click-test both UIs once.** Verification was deliberately headless after an incident (see gotchas), so nothing exercised the desktop owner ComboBox, the two "only mine" checkboxes, or the dashboard tiles rendering, and the web side was verified through the accessibility tree plus screenshots rather than by a human. Unit tests and static review cover the logic; your eyes are still the only evidence for the UI.
3. Consider **TestFX** (test-scope, headless via Monocle) so desktop UI can be verified without a human and without touching the desktop. Nothing automated can drive the JavaFX GUI today.
4. Follow-ups worth issues: `ItemsController.readAssignee` still accepts any non-empty string as `assigneeId`, so a bogus id becomes a 500 (the same bug `ownerId` just fixed); `ClientApp` copies the saved `ServerPrefs` URL into the `todolist.api.url` system property before `Config` is read, so `TODOLIST_API_URL` can never win once a URL has been saved, which is how a local test silently talks to production; and on the web side, dashboard list rows link to `/todo/lists` without preselecting the clicked list (needs a `?list=` read in `todo-app.tsx`).

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
- **Vercel blocks a preview deploy when the commit's git AUTHOR email is not a Vercel project member.** A commit authored `patr7257 <pr@zrm.dk>` failed the `Vercel` status with "Git author przrm must have access to the project on Vercel to create deployments", because that ZRM email maps to the `przrm` GitHub account. Every working commit on `PatrickRobelWeb` uses `Patrick Røbel <patr7257@gmail.com>`, which is that repo's configured identity. Fix is to re-author (`git commit --amend --author=...`) and force-push; better, never override the repo's own git config when committing.
- **`gh issue create` does not support `--json`.** Using it makes the command fail, and a `|| gh issue list ...` fallback then prints the newest EXISTING issue, which reads exactly like success. That is how work briefly got attached to an unrelated pre-existing issue this session. Always capture the URL `gh issue create` prints and verify the number belongs to the issue you meant.

## Open decisions waiting on Patrick
- Whether to add TestFX (test scope, headless via Monocle) so desktop UI can be verified without a human and without touching the desktop. Right now the JavaFX GUI has no automated coverage at all.
- Whether to close or repurpose #47 now that the web parity work has shipped.
- Whether to delete the merged remote branch `feat/todo-web-dashboard` in `PatrickRobelWeb` (remote deletion is always an explicit ask).

## Environment state
- Nothing of ours is running: no JavaFX client, no API on 8080, no dev server. Ports 3000, 3001, 5173, 8080 and 5433 all free.
- The throwaway Docker Postgres container was removed; its named volume `todolist-dev-db-data` was kept. Its data had drifted from the fixture during the session, so start with `.\scripts\dev-db.ps1 -Reset` then `-Fixture` if you need the exact backfill cases.
- Docker Desktop was started manually by Patrick, so the user-level `docker-desktop` skill deliberately leaves it running: it only stops what Claude started, tracked by a per-session marker.
- TodoList: `main` only, no worktrees, all feature branches deleted local and remote.
- `.claude/.codev-ack` is locally modified as usual (one appended line per session).
- New this session at user level, not in this repo: a `docker-desktop` skill plus a `SessionEnd` hook that stops Docker Desktop only when Claude started it, and MANDATORY rules in the global `CLAUDE.md` banning synthetic keyboard and mouse input and preferring accessibility-tree navigation over screenshots.
