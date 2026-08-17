# HANDOVER

## Date, branch, PR, CI

- 2026-08-16 (closing edits 2026-08-17). Branch: `docs/handover-tinder-link`
  (this file's own PR). Everything else from this session is merged and live.
- **TodoList**: #69 (#66), #70 (#61), #71 (#56 + #59), #72 (#61), #73 (#57), all
  squash-merged with CI green. `main` is at the #73 merge plus this docs branch.
- **PatrickRobelWeb**: #173 (the `/todo` UX batch), #174 (TodoTinder) and #176
  (the dashboard link into TodoTinder), all merged, all Vercel production
  deploys verified live.
- **Every issue that existed at session start is closed.** The only open issue in
  either repo's TodoList scope is **#74**, which this session filed on purpose.

## TLDR of session outcome

The desktop client is gone, TodoTinder is built and live, and the whole `/todo`
UX backlog shipped. Product changes now only ever have to be made once.

- **#66 desktop client retired.** `client/`, `build-installers.ps1`, the release
  workflow, the jlink module list and its guard, and `dk.dtu.shared.Config` are
  all deleted. The repo is `api` + `shared`. v2.0.8 stays on the Releases page,
  so an installed copy keeps working against the unchanged API and simply stops
  receiving updates.
- **TodoTinder (#44) is finished and live** at `https://patrickrobel.dk/tinder`:
  V8 schema, swipe/match/import API, four curated decks totalling 440 entries,
  and an installable swipe PWA, linked from the `/todo` dashboard on both
  desktop and phone. The epic and all four sub-issues are closed.
- **The `/todo` UX backlog shipped**: #63, #64, #65, #67, #68 here plus
  PatrickRobelWeb #169 and #170.
- **Password login is gone** from the API. `SeedUser` deliberately survived.
- Six implementation subagents, ten PRs, two repos, nine production merges.

**Five bugs were found that CI could not have caught**, three of them in code
that was already typechecking, unit-testing and building green. They are in the
gotchas section below, because each is a repeatable class of mistake.

## Prioritized next steps

1. **Open `https://patrickrobel.dk/tinder` on your phone and install it.** It was
   verified end to end headlessly against a live API, but never on a real phone,
   and the swipe gesture's feel (an 8px tap slop and a 96px commit distance) is
   the kind of thing only a thumb can judge.
2. **Read the four decks and prune them.** 440 entries were authored to a brief
   and verified structurally (exact counts, no duplicates, correct Danish), but
   nobody has read them all for taste. `scripts/data/tinder-*.json`, then re-run
   the seed script; it is idempotent.
3. **Seed the four target lists in production before using TodoTinder for real.**
   The decks point at lists named `Aktiviteter`, `Rejsemål`, `Indkøb` and
   `Date nights`, resolved BY NAME. If they do not exist in production the seed
   reports a shortfall and exits non-zero rather than half-seeding.
4. **Work #74** (per-user session revocation) when it matters. It is a breaking
   wire-format change pinned from both repos, so it needs a coordinated deploy.

## Verbatim resume commands (PowerShell)

Start a throwaway local Postgres (the API does NOT start one):
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; .\scripts\dev-db.ps1
```
Run the API against it:
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; $env:DATABASE_URL='postgres://postgres:todo@localhost:5433/todo'; $env:TODO_SESSION_SECRET='dev-secret'; mvn -pl api exec:java
```
Run the full Java test suite:
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; mvn -B clean verify
```
Seed the four TodoTinder decks (dry-runs and asks before writing; borrows psql
from a container, so nothing needs installing):
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; .\scripts\seed-tinder.ps1
```
Create an account (passwordless; it can then sign in with a passkey or magic link):
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; .\scripts\seed-user.ps1
```
Run the website against that local API, on a port that does not collide with the
MW Service Tool's :3000:
```
cd "C:\Users\pr\repos\1-Personal\PatrickRobelWeb\website"; $env:TODO_API_BASE_URL='http://localhost:8080'; $env:TODO_SESSION_SECRET='dev-secret'; npx next dev -p 3100
```
Run the website tests:
```
cd "C:\Users\pr\repos\1-Personal\PatrickRobelWeb\website"; pnpm test
```
Tear the local database down:
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; .\scripts\dev-db.ps1 -Stop
```

## Gotchas discovered this session

- **A React state updater must be PURE, and StrictMode keeps the SECOND call.**
  The `?list=` deep link never worked because its updater flipped a "already
  applied" ref inside itself: the first call resolved the list and set the flag,
  the second saw the flag and returned null. Green types, green tests, feature
  entirely dead. Mutate refs outside the updater.
- **Two features can each be correct and still cancel each other out.** Task
  dragging required the status filter to be `all`; the same batch made `open`
  the default. Neither issue was wrong on its own and nothing failed, but
  dragging was unreachable out of the box. Check new defaults against existing
  gates.
- **NFD folding does nothing for `æ` and `ø`.** They are atomic code points with
  no decomposition, unlike `å`, which does decompose. So list search could not
  find `Indkøb` when the `ø` was typed as its two-letter digraph, nor when it
  was typed as a bare `o`. Fold the Danish letters explicitly, both ways.
- **PowerShell encodes a pipe to a native process in the console codepage**,
  which varies by how the script was launched. The seed script silently dropped
  the two decks whose list names carry Danish letters, 2 of 4 decks and 140 of
  440 entries, while printing "Seed complete. Done." Send SQL through a UTF-8
  file with no BOM, never a pipe, and add a post-write count check: a step that
  skips rows instead of erroring can always half-succeed.
- **`boolean::text` is `true`, not `t`.** `t` is psql's aligned DISPLAY. A
  readiness check comparing against `'t'` could never pass.
- **`lists.name` is not unique**, so joining a deck to its list on the name can
  emit two rows for one key and make `ON CONFLICT DO UPDATE` fail outright with
  "cannot affect row a second time". Use `LATERAL ... LIMIT 1`.
- **"Every row in `users`" is the FRAGILE form of a quorum**, not the general
  one. Matches required all users to have swiped right; `users` is shared with
  the website and `SeedUser` can add a row, so a third account would have
  silently wiped every existing match. A quorum of two cannot fail that way.
- **An end-to-end script lies in four standard ways**, all of which happened
  here: fixed sleeps racing a dev-server route compile, a count assertion that
  fails precisely because dedupe works, the wrong baseline for a per-user
  number, and a selector loose enough to match a different control. Wait for
  the thing, assert the invariant, not the delta.
- **`\b` in a Python string is a BACKSPACE.** A patch script wrote a literal
  U+0008 into a JS regex: invisible in review, fatal at runtime. Same hazard as
  a literal combining character.
- **Git Bash rewrites unix-looking paths in a command line**, so a container
  mount `-v host:/sql` becomes `C:/Program Files/Git/sql`. Guard with
  `MSYS_NO_PATHCONV=1` and `cygpath -m`.
- **The `todo_session` cookie is host-only to `patrickrobel.dk`.** That single
  fact killed the plan to serve TodoTinder from the API's own subdomain: the app
  could not have authenticated at all. Check cookie scope before choosing an
  origin.
- **Curl from Git Bash mangles non-ASCII in `-d`.** Two "encoding bugs" this
  session were the test harness, not the app. Send a UTF-8 file with
  `--data-binary @file` before believing the product is at fault.

## Open decisions waiting on Patrick

- Delete the merged remote branches `origin/feat/todo-ux-integration` and
  `origin/feat/todo-tinder-pwa` in PatrickRobelWeb, and the stale
  `origin/feat/todo-web-dashboard` still outstanding from 2026-08-03? All local
  branches and worktrees are already gone.
- Should `/todo` get a visible link into `/tinder`, or does the installed app
  icon cover it?
- The `/tinder` UI copy is Danish throughout while `/todo` is mostly English.
  Deliberate for a Danish deck app, but it is a consistency call.
- Should `shared/` (now one enum, `TaskStatus`) be folded into `api/` and the
  module dropped? Nothing depends on it happening.

## Environment state

- **Nothing of mine is running.** The dev database, the API container and the
  dev server are all stopped; ports 3100, 18080, 5433 and 8080 are free.
- **Docker Desktop: this session started it, and session end will stop it.**
  Worth knowing why that took two decisions rather than one. Mid-session it was
  left up on purpose, because `mw-postgres` (MW Service Tool, a different repo)
  had come up with it and that project's dev server was live on **:3000**;
  stopping Docker would have pulled the database out from under it. That server
  has since stopped and nothing is using the container, so the ownership marker
  was left in place and the normal cleanup applies. If you are ever mid-MW-work
  when a TodoList session ends, check `:3000` before letting Docker go.
- All worktrees removed in both repos. Both are on `main` and clean.
- No cron jobs or scheduled tasks were created. Keep-awake is not active.
- `.claude/.codev-ack` is now gitignored and untracked, so it no longer shows as
  permanently modified. All six historical session lines were preserved.
