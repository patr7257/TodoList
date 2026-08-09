# HANDOVER

## Date, branch, PR, CI

- 2026-08-09. Branch: `docs/session-close-2026-08-09` (this file's own PR). Everything
  else from this session is merged and live on `main` at `fe3b88d`.
- **TodoList**: #53 (`52b9f67`), #54 (`9b66997`), #55 (`82c6fa8`), #60 (`fe3b88d`), all
  squash-merged with CI green. Latest release **v2.0.8**, all four assets present and
  both permanent `releases/latest/download` URLs verified HTTP 200.
- **PatrickRobelWeb**: #171 (`6a79332`) and #172 (`a800323`), both merged, both Vercel
  production deploys succeeded.
- Follow-ups live in **#61**, not only in closed-issue comments.

## TLDR of session outcome

The entire open backlog was cleared. Every issue that existed at session start is closed.

- **#47** closed as already shipped last session. Its two genuinely unfinished leftovers
  were filed on the website board as `PatrickRobelWeb#169` and `#170`.
- **#52 public share links** shipped across all three surfaces. A list can be handed to
  someone outside the app as a live read-only link at `patrickrobel.dk/s/<token>`. New
  `list_shares` table (V6), one public API route, three authenticated management routes,
  a share dialog in the desktop client and a share sheet in the web edition.
- **#51 passkeys plus magic link** shipped. Sign-in moved off email and password onto
  passkeys plus a ZeptoMail magic link. The website is now the **issuer** of the
  `todo_session` token, not just a verifier, so the JavaFX client can obtain a working
  API token from a browser ceremony it cannot run itself (RFC 8252 with PKCE, token
  returned over a `127.0.0.1` loopback listener). Released as v2.0.8.
- **#44 TodoTinder** designed, not built, which is what the issue itself instructs. All
  nine questions answered, the spec is a comment on #44, and it is split into #56 to #59.
- Six PRs, seven implementation subagents, two repos, six production merges.

## Prioritized next steps

1. **Sign in on the desktop client after updating to v2.0.8.** This is the ONE path
   nobody has exercised against the live website. It is thoroughly unit tested (the real
   loopback listener is driven over HTTP including wrong-state, missing-state and
   double-callback cases) but has never talked to production. Your currently installed
   client keeps using its saved token until you update, so there is no rush, but do not
   rely on it until you have seen it work.
2. **Create a share link and open it.** Never done on live data this session, because
   `TODO_SESSION_SECRET` is not in the local environment so no token could be minted.
   The happy path is covered by `SharesIntegrationTest` against a real Postgres and a
   real Javalin, but a live click is still the only proof.
3. **Work through #61**, which holds every follow-up this session created or inherited.
   The highest-value one is retiring password login, and it has a strict order.
4. **Start TodoTinder from #56**, which is the foundation the other three build on.

## Verbatim resume commands (PowerShell)

Start a throwaway local Postgres (the API does NOT start one, see the Migrations section
in CLAUDE.md):
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; .\scripts\dev-db.ps1
```
Run the API against it:
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; $env:DATABASE_URL='postgres://postgres:todo@localhost:5433/todo'; $env:TODO_SESSION_SECRET='dev-secret'; mvn -pl api exec:java
```
Run the desktop client from source:
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; mvn -q install -DskipTests; mvn -pl client javafx:run
```
Run the full test suite:
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; mvn -B clean verify
```
Run the website locally (passkeys work against localhost, rpID falls back to `localhost` in dev):
```
cd "C:\Users\pr\repos\1-Personal\PatrickRobelWeb\website"; pnpm dev
```
Tear the local database down:
```
cd "C:\Users\pr\repos\1-Personal\TodoList"; .\scripts\dev-db.ps1 -Stop
```

## Gotchas discovered this session

- **`SELECT s.id, ..., l.*` across a join is a silent data bug.** The first draft of the
  share resolver selected the share's `id` alongside `lists.*`, and both tables have `id`
  and `created_at`. JDBC `getString("id")` returns the FIRST matching column, so the
  mapped list id would silently have been the share id, and the follow-up items query
  would have looked up a list that does not exist. Always alias explicitly when a join
  selects a wildcard.
- **`.claude/.codev-ack` is TRACKED in this repo and not gitignored**, contrary to what
  the co-development-workflow skill states. It accumulates one line per session in the
  working tree. A `git restore` while tidying a commit wiped three sessions' lines; they
  had to be re-appended by hand. Never `git restore` that file, and never `Write` it.
- **A `git diff origin/main` showing your own migration as DELETED means a stale base**,
  not a real deletion. A branch cut before another migration merged will show that
  migration as removed. Rebase before reading the diff or reviewing the PR.
- **Vercel preview deployments are behind SSO**, so `curl -I` against a preview returns a
  302 to the auth gate and the headers you see belong to Vercel's own SSO page, not to
  your app. Response headers can only be verified against production. Note that the
  `noindex` on the SSO page looks superficially like a passing check; ours is
  `noindex, nofollow`, which is how the difference was caught.
- **PKCE only protects the party that CHOSE the challenge.** The typed fallback code in
  the desktop sign-in is therefore phishable: anyone can link a victim to
  `/todo/login?desktop=1&challenge=<theirs>`, let them sign in for real, and ask for the
  code back. No server-side check can distinguish that from a genuine desktop sign-in.
  The mitigation is the anti-phishing warning inside the code panel. Do not tidy it away.
- **Amadeus Self-Service shut down on 17 July 2026 and Kiwi Tequila went invite-only.**
  Both free flight-price APIs the TodoTinder epic named are gone, which is why Rejsemål
  ships curated price bands instead. Do not plan around either API.
- **`patrickrobel.dk` 308-redirects to `www`.** Share links composed against the apex
  work fine (browsers follow it) but there is an extra hop. `TODO_SHARE_BASE_URL`
  defaults to the apex; change it only if the hop ever matters.
- The two `DATABASE_URL`s (website and Java API) point at the **same** Neon database.
  Confirmed by the website's connection seeing `flyway_schema_history`. This is load
  bearing for #51: the website reads `users` and writes `todo_credentials` directly.

## Open decisions waiting on Patrick

- Delete the merged local branches? `feat/list-shares-api`,
  `feat/passkey-credentials-migration`, `feat/share-dialog-desktop` in TodoList, and
  `feat/todo-passkey-auth`, `feat/todo-share-web` in PatrickRobelWeb. Their remotes were
  already deleted on merge.
- Delete the stale remote branch `origin/feat/todo-web-dashboard` in PatrickRobelWeb?
  Still outstanding from the 2026-08-03 session.
- Should the `/todo` web app copy be Danish? Today the emails and the magic-link landing
  page are Danish; the login card, passkey prompt and dashboard banner are English, to
  match the rest of the app. Both are defensible, it is a consistency call.
- Gitignore `.claude/.codev-ack` to match the skill's assumption, or keep it tracked and
  document the divergence?

## Environment state

- Nothing of ours is running. No API on 8080, no dev server, no JavaFX client. Ports
  3000, 3001, 5173, 5174, 8080 and 5433 all free.
- Docker Desktop is DOWN and no session marker exists, so nothing will be torn down at
  session end.
- No cron or scheduled jobs were created this session.
- Keep-awake is not active; the PC sleeps per its normal power settings.
- TodoList: `main` plus this docs branch. All worktrees removed
  (`TodoList-52-api`, `TodoList-52-client`, `TodoList-51-client` are gone).
- PatrickRobelWeb: on `main`, clean.
- `.claude/.codev-ack` is locally modified as usual (one appended line per session).
