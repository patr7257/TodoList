# HANDOVER

## Date, branch, PR, CI

- 2026-09-19. Branch: `docs/handover-2026-09-18` (this file's own branch, kept
  local and unpushed by Patrick's decision).
- **Everything is merged, deployed and live.** No open PRs and no open issues in
  either repo.
- **TodoList**: PR #80 (#74, #77, #78, #79, #81) and PR #83 (#82) merged. `main`
  is `fb67e08`. The API redeployed on Dokploy with V9 and V10 applied.
- **PatrickRobelWeb**: PRs #185, #187, #189, #190, #191 merged. Production serves
  the current `main`, confirmed by `/api/version`.
- Remote branches: `main` only in TodoList; `main` plus the other session's
  handover branch in PatrickRobelWeb.

## TLDR of session outcome

Two sessions, two repos, ten issues closed. The product now does what it was
supposed to do, including the two things that had never actually worked.

- **The phone lockout is fixed and shipped.** The cause was never the email: the
  enrol prompt only rendered when `passkeyCount === 0`, so once you had one
  passkey there was no way in the UI to add a second, and a Windows Hello
  credential cannot reach a phone. `/todo/settings` fixes it and is live.
- **TodoTinder works for the first time.** It had never worked in production: the
  database was never seeded, and `V8` seeds no decks on purpose. Now seeded with
  4 decks and 425 entries.
- **Per-user ordering is live end to end** (V9 plus the website write path), so
  the two accounts no longer overwrite each other's arrangement.
- **Per-user session revocation is live** (V10, the `tv` claim).
- **Both repos now have real CI.** PatrickRobelWeb had no workflows at all.
- **Two self-inflicted traps were caught before they shipped**: a dry run that
  would have bound a tinder deck to the wrong list silently, and a static route
  whose deletion would have failed silently.

## Prioritized next steps

Nothing is blocking. These are the unverified edges.

1. Enrol a passkey on the phone at `https://patrickrobel.dk/todo/settings`, using
   "Use a phone or tablet" in the browser dialog. Still the one thing that ends
   the lockout permanently.
2. Verify per-user ordering with two accounts: you and Eline drag the same list
   to different positions and both survive a reload. Only provable with two real
   sessions, so it is still unproven.
3. Verify revocation on a session created AFTER the API deploy:
   `UPDATE users SET token_version = token_version + 1 WHERE id = '<uuid>';`
   A token minted before the deploy carries no `tv` claim and survives by design.
4. Watch the Dokploy log over a day or two for the Hikari
   `Failed to validate connection` warning to stop appearing.
5. Swipe through a deck and confirm a right swipe creates the item in the right
   list. `DateNighTinders` binds to list `c5e39a4a-6f49-49d0-93a2-7b2cd098d863`,
   the one holding the five real ideas.

## Outstanding, not from this work

- **Plaintext GitHub 2FA recovery codes** were found in a task description on a
  list called `PWs&Keys`. Patrick is rotating them. Two angles worth checking:
  whether that list was ever shared (the public share payload includes task
  `text` and `description`), and that the values are also in the Neon database
  and any backup or branch of it.

## Verbatim resume commands (PowerShell)

Is production running what was merged (JSON means yes, HTML means no):
```
curl -sL https://www.patrickrobel.dk/api/version
```
What Vercel thinks it has deployed:
```
vercel ls patrickrobelweb
```
Run the full Java test suite:
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; mvn -B clean verify
```
Re-seed or top up the tinder decks (idempotent, dry-runs and confirms first):
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; .\scripts\seed-tinder.ps1
```
Start a throwaway local Postgres (the API does NOT start one):
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; .\scripts\dev-db.ps1
```
Tear it down:
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; .\scripts\dev-db.ps1 -Stop
```

## Gotchas discovered

- **A merge is not a deploy, and nothing said so.** A merge landed with zero
  check runs and zero deployments while production served a five day old build.
  `GET /api/version` now reports the build's commit, so the next occurrence costs
  one request instead of an hour.
- **An outage and a broken integration are indistinguishable from the API side.**
  Both produce a pending commit status, zero check runs and no deployment. Two
  confident wrong diagnoses were made before that was established. Waiting was
  the only safe move, and changing the Git settings would have broken a working
  configuration.
- **"It is empty" is not "it broke".** The tinder investigation hunted a
  regression for a while because one session turned a user's report into a
  regression claim. The question that settled it was "has it ever worked".
- **An exact-name lookup can bind to the wrong row without any duplicate.**
  `Date nights` (new, empty) versus `Datenights` (old, real) are not name
  duplicates, so the duplicate counter read 1 and the dry run looked clean. The
  seed script now prints the resolved list id, created_at and item count.
- **A static route segment beats a dynamic sibling, and deleting it fails
  SILENTLY.** `/api/todo/lists/order` falls through to `[id]` and becomes a PATCH
  against a list whose id is the string `"order"`. Pinned by a test.
- **Commits came out authored `pr@zrm.dk` while `git config user.email` was
  correct.** The committing agent supplies the author. Only
  `git log --format='%an <%ae>'` is evidence.
- **`git fetch` in PatrickRobelWeb needs `GH_CONFIG_DIR` exported** to
  `C:/Users/pr/.config/gh-personal`, because that repo's `.git/config` overrides
  the global pin with a bare `!gh auth git-credential`.
- **Git Bash rewrites unix-looking paths** inside `curl -w` format strings AND in
  `docker exec ... -f /path` arguments. `MSYS_NO_PATHCONV=1` fixes it, but then
  `docker cp` needs a Windows source path. This cost three separate mistakes.
- **`grep -P` errors on this locale and prints nothing**, so "no output means
  clean" silently passes. Prove a detector fires before trusting its silence.
- **`ls` sorts `V10` before `V2`.** Use `sort -V` on migrations.
- **The Java token parser tolerates unknown payload fields**, so verify-side
  tests stay green through a real divergence. Only the mint-side byte-for-byte
  round trip catches it.
- **`defaults.run.working-directory` does not apply to a `uses:` action's
  inputs.** `pnpm/action-setup` needs `package_json_file: website/package.json`.
- **`next build` rewrites `website/next-env.d.ts`.** Keep it out of commits.
- **PowerShell renders UTF-8 psql output in the console codepage**, so `Indkøb`
  appears as `Indk├╕b`. Display only. The seed script sends SQL through a UTF-8
  file and its shortfall check is what catches a real encoding drop.

## Open decisions

- Should revocation get an authenticated admin route, or stay a hand-run SQL
  statement? #74 deliberately shipped without one.
- `AuthFilter.versionCurrent` currently accepts a token with no `tv` claim. That
  branch can be tightened once every live session carries one, which the 30 day
  TTL does on its own.
- The magic-link fix makes an allowlisted account distinguishable from a stranger
  WHILE the mail provider is down (502 versus 200). Accepted as the right trade
  on a two person allowlist.
- Should `shared/` (one enum, `TaskStatus`) be folded into `api/`?
- The `.ps1` plus `.sh` twin scripts under `scripts/` duplicate their SQL and
  must be kept identical by hand. Converting them to one Node `.mjs` each is the
  documented preference and has not been done.

## Environment state

- **Nothing is running.** No dev server, no local database, no container. The
  throwaway `seedcheck` Postgres used to verify #82 was removed.
- **Docker Desktop was started by this session** and carries an ownership marker,
  so the `SessionEnd` hook stops it automatically.
- **No cron jobs, scheduled tasks or wake timers were created.** The existing
  `ClaudeWeeklyRetro` task is pre-existing and was left alone.
- Worktrees: only the main checkouts plus `TodoList-docs`, which holds this file.
- This handover is committed on `docs/handover-2026-09-18` and NOT pushed. The
  commit lives in the repository's object store, so removing the worktree does
  not lose it; deleting the branch would.
