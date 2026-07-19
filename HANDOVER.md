# HANDOVER

## Date, branch, PR, CI

- Date: 2026-07-19
- Branch: main (clean, synced with origin)
- PR: #22 "feat: Soft Warm Minimal UI restyle + rename cleanup" MERGED into main
- CI: no new release tag this session; latest release remains v1.3.2

## TLDR of session outcome

- Done: repo rename to `patr7257/TodoList` finished everywhere (git remote,
  `UpdateChecker.REPO`, HANDOVER/script paths). CI workflow needed no changes.
- Done: full client restyle to "Soft Warm Minimal" (picked from 5 artifact
  mockups): warm-paper light + warm-charcoal dark. `common.css` holds structure
  plus LIGHT tokens; new `theme-warm-dark.css` holds DARK token re-overrides;
  `DarkModeManager.applyBrand` swaps them and dialogs delegate to it. Serif
  (Georgia) display titles, status pills, theme-aware row tints.
- Done: verified live on Windows in BOTH themes: welcome, login, main menu
  (screenshots `screenshots/ui4-*.png`, gitignored). `mvn test` and full
  `mvn install` green.
- Not verified: task view status pills + row tints in the running app
  (synthetic double-clicks never registered, could not open a list remotely).

## Prioritized next steps

1. Open a todo list in the app and eyeball status pills + row tints in both
   themes; tweak `-status-*` tokens in `common.css` / `theme-warm-dark.css` if
   contrast feels off.
2. Delete the 5 mockup artifacts at claude.ai/code/artifacts (CLI cannot).
3. Tag a release (`v*`) when the new look is confirmed so installers pick it up.
4. Optional: bundle a real serif/display font (Lora) instead of Georgia if the
   serif should look identical on macOS.

## Verbatim resume commands

```powershell
cd "C:\Users\pr\repos\1-Personal\TodoList"; mvn -q install -DskipTests
```

```powershell
cd "C:\Users\pr\repos\1-Personal\TodoList"; mvn -pl server exec:java
```

```powershell
cd "C:\Users\pr\repos\1-Personal\TodoList"; mvn -pl client javafx:run
```

```powershell
cd "C:\Users\pr\repos\1-Personal\TodoList"; powershell -ExecutionPolicy Bypass -File scripts\snap.ps1
```

## Gotchas discovered this session

- Do NOT use `-am` with `exec:java` / `javafx:run`: the goal also runs on the
  parent aggregate and fails on missing `mainClass`. Install first, then run
  the single module (CLAUDE.md updated).
- `-Dtodolist.server.ip` cannot be passed through `mvn javafx:run`: the
  javafx-maven-plugin 0.0.6 execution config ignores CLI `-Djavafx.run.jvmArgs`
  and `-Djavafx.options`, and the client auto-connects to the last good server
  saved by `ServerPrefs` (registry `HKCU:\Software\JavaSoft\Prefs\dk\dtu`).
  Use the in-app Change Server dialog for dev.
- The dev client on this machine auto-connects to the PRODUCTION Tailscale
  server (100.100.220.67). "List created/deleted" lines flooding the console at
  startup are the notification listener replaying the server's notification
  tuple backlog, not live changes.
- `scripts/snap.ps1` captures at the wrong size on high-DPI (not DPI-aware);
  PrintWindow + `SetProcessDPIAware` fixes it and also works with the window in
  the background (pattern lived in this session's scratchpad drive.ps1).
- Posted window messages (PostMessage) reach JavaFX for single clicks but
  never produce clickCount 2, so double-click row opening cannot be driven
  that way.

## Open decisions waiting on Patrick

- Confirm the task view (pills/tints) looks right in both themes: yes/no.
- Tag a new release now or after more UI polish?

## Environment state

- Dev server and client processes: stopped. No Docker used. No worktrees.
- Local demo server data: `%USERPROFILE%\.todolist-data` may now contain a
  seeded demo `session.json` from this session's local server run.
- Theme state in app: not persisted; next launch starts light as before.
- Feature branch `feat/ui-pep-up`: deleted local + origin after merge.
