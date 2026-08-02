# GitHub Projects CLI cookbook

Exact `gh` commands for the co-development workflow. Nothing here is hardcoded to one repo:
every value is discovered at runtime. Where a concrete example helps, the MW Service Tool
values are shown as "example for this repo"; rediscover them for any other repo.

All `gh project` commands need the `project` token scope. If a command returns
`your authentication token is missing required scopes [read:project]`, run once:

```
gh auth refresh -s project
```

The browser device flow must be completed by a human; an agent cannot complete it.

---

## 1. Detect owner, repo, and the integration branch

```
gh repo view --json owner,name,defaultBranchRef
```

Example output for this repo:

```
{"owner":{"login":"ZRM-ApS"},"name":"MW_service_tool","defaultBranchRef":{"name":"main"}}
```

So `REPO_OWNER=ZRM-ApS`, `REPO=MW_service_tool`. Note: issue commands use the repo owner
(`--repo ZRM-ApS/MW_service_tool`), but the board can be owned by a different account. For this
repo the board is a personal user project owned by `ss-zrm`, so every `gh project` command uses
`--owner ss-zrm`, not the repo owner. Confirm the board owner before assuming it matches the
repo.

The default branch is `main`, which is **production**, so it is NOT the integration branch.
Determine the integration branch:

```
git ls-remote --heads origin dev
```

If that prints a ref, use `dev` as the integration branch (the case for this repo). Otherwise
fall back to the default branch. Always base feature branches on `origin/<integration-branch>`.

---

## 2. Find the board (project)

```
gh project list --owner ss-zrm
```

This lists the owner's projects with their NUMBER, title, and state. Pick the project for this
repo (for this repo: title "MW Task Tool"). If several projects exist and it is ambiguous, ask
the user which board, then cache the choice in the repo instructions file.

Get the project's node ID (needed as `--project-id` for edits):

```
gh project view <NUMBER> --owner ss-zrm --format json --jq '.id'
```

---

## 3. Read the Status column and its option IDs

Column moves are a single-select field edit. You need the Status field ID and the option ID of
the target column. Read them once per board (IDs are stable, so cache them):

```
gh project field-list <NUMBER> --owner ss-zrm --format json
```

Pull just the Status field and its options:

```
gh project field-list <NUMBER> --owner ss-zrm --format json \
  --jq '.fields[] | select(.name=="Status") | {fieldId:.id, options:.options}'
```

This yields the Status `fieldId` and an `options` array of `{id, name}` for Backlog, Ready,
In progress, In review, Done. Record `fieldId` and each option `id` in the repo instructions
file so later sessions skip this lookup.

> Field caveat: GitHub's BUILT-IN `Status` field can be customised with extra options and is
> CLI-movable (the MW board above does this). But on some boards the CLI cannot edit/delete the
> built-in `Status`, so a **separate custom single-select field (often named `Stage`)** is used
> for the columns instead. Whichever field the board's view groups by is the one the move
> commands in section 6 must target - discover it, don't assume `Status`.

---

## 3b. The board (Kanban) VIEW layout is UI-ONLY - instruct the user to set it up once

The `gh` CLI (and the ProjectV2 API) can create issues, add items, and MOVE cards, but it
**cannot create or configure a *view*** - not the layout, not which field defines the columns,
not the column set. A board view is a UI-only artifact. So a freshly created board opens as a
**Table grouped by the built-in `Status`** (Todo / In Progress / Done) - which on a `Stage`-based
board is the WRONG field. Symptom: you move cards on `Stage` via the CLI, but the board still
groups by `Status`, so every card piles into "Todo" and the five real columns never appear
(exactly what a confused user will screenshot).

Because the agent cannot do this, **instruct the user to do it once from the start**, and ask them
to paste back the view URL (`https://github.com/orgs/<OWNER>/projects/<N>/views/<VIEW_ID>`) so it
can be cached in the repo instructions file:

1. Open the board → **＋ New view** (or reuse the default) → set its layout to **Board**.
2. **Name** it (e.g. "ISSUE STATUS VIEW").
3. Set the **column field** to the workflow's status field - the custom **`Stage`**, NOT the
   built-in `Status`. Click the **"View"** button at the **top-right of the board** (gear/funnel
   icon, beside the search bar) → in the panel choose **"Column by"** → select **`Stage`** → then
   click **"Save view"** (REQUIRED - without Save the change is not persisted for the team). The
   columns ARE that field's options (Backlog · Ready · In progress · In review · Done). ⚠️ The
   **`＋ New column`** button on the board only adds an option to the *currently selected* field -
   if the board is still on `Status`, it adds Status columns (the wrong field). Switch "Column by"
   to `Stage` FIRST; the five columns then appear automatically (no manual column creation needed).
4. Optionally drag the columns into Backlog → Ready → In progress → In review → Done order, then
   **Save view** again.
5. **Always add a one-line description to each column** so the board reads clearly (the grey
   subtitle under each column title). These are the **single-select option descriptions on the
   `Stage` field** - edit them in the UI: on the board click a column's **`⋯` → "Edit details"**
   → fill **Description** → Save (repeat per column). Standard text:
   - Backlog - `This item hasn't been started`
   - Ready - `This is ready to be picked up`
   - In progress - `This is actively being worked on`
   - In review - `This item is in review`
   - Done - `This has been completed`
   ⚠️ Do this in the UI, NOT via the API: `updateProjectV2Field` can only set option
   descriptions by replacing the WHOLE option set, which reassigns option IDs and orphans every
   card's `Stage` value (and breaks the cached option IDs). UI edits are in-place and safe.
6. Copy the view URL back to the agent to cache.

If the five Stage options don't yet exist as columns, they come from the `Stage` field's options
(section 3) - add/rename them on the **field**, not via the view's `＋ New column`.

---

## 4. See what is currently In progress (the clash check)

```
gh project item-list <NUMBER> --owner ss-zrm --format json
```

Filter to active work and show title, assignees, and status:

```
gh project item-list <NUMBER> --owner ss-zrm --format json \
  --jq '.items[] | select(.status=="In progress" or .status=="In review")
        | {title:.content.title, number:.content.number, status, assignees}'
```

Compare the new task against these before doing anything. Overlap means STOP and surface it.

Each item also carries its own `id` (the project item ID), which you need as `--id` to move it.

---

## 5. Create an issue and add it to the board (Mode B, and any new issue)

Create a real repo issue (needs only the `repo` scope, works without the project scope):

```
gh issue create --repo ZRM-ApS/MW_service_tool \
  --title "feat: short imperative title" \
  --body "Intent and scope in a sentence or two. Acceptance in a line."
```

The command prints the issue URL. Add it to the board:

```
gh project item-add <NUMBER> --owner ss-zrm --url <ISSUE_URL>
```

Do NOT use `gh project item-create`: that makes a board-only draft, not a tracked repo issue.

Find the project item ID for the issue you just added (to move it next):

```
gh project item-list <NUMBER> --owner ss-zrm --format json \
  --jq '.items[] | select(.content.number==<ISSUE_NUMBER>) | .id'
```

---

## 6. Move a card to a column, and assign it

Move the item to a target column (for example, In progress):

```
gh project item-edit \
  --id <ITEM_ID> \
  --project-id <PROJECT_NODE_ID> \
  --field-id <STATUS_FIELD_ID> \
  --single-select-option-id <OPTION_ID_OF_TARGET_COLUMN>
```

`--single-select-option-id` is exactly the mechanism for status columns; no hand-written
GraphQL is needed. Edit one field per invocation.

Assign the issue so the team sees who owns it:

```
gh issue edit <ISSUE_NUMBER> --repo ZRM-ApS/MW_service_tool --add-assignee @me
```

The same `item-edit` call moves the card to In review (on PR open) and Done (after merge): just
change `--single-select-option-id` to that column's option ID.

After claiming (moving to In progress + assigning), re-read the item to confirm the claim won
the race against any parallel session:

```
gh project item-list <NUMBER> --owner ss-zrm --format json \
  --jq '.items[] | select(.content.number==<ISSUE_NUMBER>) | {status, assignees}'
```

If another assignee now appears, stop and surface it instead of proceeding.

---

## 7. Worktree and branch

After fetching, create one isolated worktree per issue off the integration branch
(`<INTEGRATION_BRANCH>` resolved in section 1; for this repo it is `dev`):

```
git fetch origin
git worktree add ../MW_service_tool-<ISSUE_NUMBER> -b feat/<topic> origin/<INTEGRATION_BRANCH>
```

Use `fix/` or `chore/` prefixes as appropriate. Work happens inside that directory, leaving
every other checkout and session untouched.

`gh issue develop <ISSUE_NUMBER> --base dev --name feat/<topic>` also creates an issue-linked
branch, but it pushes the branch to the remote, which is an outward write. Default to the local
`git worktree` form above and only use `gh issue develop` when a human has authorized a push.

---

## 8. Re-sync, open the PR, watch CI

Before review, make the branch current (run inside the worktree):

```
git fetch origin
git rebase origin/<INTEGRATION_BRANCH>   # or: git merge origin/<INTEGRATION_BRANCH>
```

If the repo uses ordered migrations, regenerate them after the rebase (for this repo, see the
`drizzle` skill: `pnpm --filter @mw/api db:generate`).

Only on an explicit human go-ahead, push and open the PR against the integration branch:

```
git push -u origin feat/<topic>
gh pr create --base <INTEGRATION_BRANCH> --title "feat: ..." --body "..."
```

Then move the card to In review (section 6). Watch the checks:

```
gh pr checks <PR_NUMBER> --watch
```

For this repo the single required check is the job "Lint, typecheck, test, build". If it is
red, fix on the same branch and push again. When it is green, ask a human to review, approve,
and merge. Never merge yourself, never push to `main`.

After the merge, move the card to Done (section 6) and clean up:

```
git worktree remove ../MW_service_tool-<ISSUE_NUMBER>
```

---

## Cached values for this repo

Discovered with the `project` scope on 20 Jun 2026 (re-run sections 2 and 3 if the board
changes):

- Repo (issues live here): `ZRM-ApS/MW_service_tool`
- Integration branch: `dev`
- Board owner: `ss-zrm` (a personal USER project, not the org; use `--owner ss-zrm` for all
  `gh project` commands even though the repo is org-owned)
- Project number: `1`
- Project node ID: `PVT_kwHODb3s5c4BaIbb`
- Status field ID: `PVTSSF_lAHODb3s5c4BaIbbzhVCKcg`
- Status option IDs: Backlog `f75ad846`, Ready `61e4505c`, In progress `47fc9ee4`,
  In review `df73e18b`, Done `98236657`

---

## 9. Battle-tested gotchas (CLI quirks that bite)

- **`item-add` → `item-list` propagation race.** Right after `gh project item-add`, the new item may
  not be queryable yet, so the `item-list` id lookup (section 5) can return an **empty** string. A blank
  `--id` then makes `item-edit` fail with `Could not resolve to a node with the global id of ''`. If the
  id comes back empty, wait a moment and re-query (or just re-run `item-add` - it's idempotent), then
  move the card.
- **Always read field/option IDs fresh.** A stale or truncated `--field-id` /
  `--single-select-option-id` pasted from a handoff or another board fails the same way
  (`Could not resolve to a node with the global id of '<id>'`). Re-discover them per board via
  `field-list` (section 3); never reuse another repo's IDs.
- **Pruning a merged branch.** A **squash- or rebase-merged** PR leaves a branch whose commits are NOT
  ancestors of the integration branch, so `git branch --merged` will NOT list it - don't rely on
  `--merged` to decide what's safe to delete; go by merged-PR status. GitHub often doesn't auto-delete
  the head branch either, so prune explicitly: `git push origin --delete <branch>` (remote) +
  `git branch -D <branch>` (local), then `git fetch origin --prune`.
- **Project *views* are UI-only (see §3b).** The CLI can create issues, add items, and move cards, but
  cannot create or configure a board view (layout / column field / columns) - that's a one-time human
  step; instruct the user and cache the resulting view URL.
- **Auditing "did we miss a change?" - check the INTEGRATION branch, not just default.** Work (incl.
  skill upgrades) usually lands on `dev`, not `main`/the default branch, so a commit/path query against
  the default branch silently misses it. Query each repo's integration branch **and** `main` explicitly
  - `gh api "repos/<owner>/<repo>/commits?sha=<branch>&path=<path>&since=<ts>"` per branch - and
  cross-check merged PRs (`gh pr list --state merged --search "merged:>=<date>"`), since a PR's changes
  live on its base branch. GitHub's global commit *search* also lags and only matches commits authored
  by you, so don't rely on it alone.
