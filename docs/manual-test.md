# Manual test checklist — v0.1

End-to-end checks that cannot be covered by unit tests: they need a real
remote host, a live tmux/Claude session, JetBrains Gateway, Firefox, and a
human eye on the UI. Run before tagging a release; individual sections also
serve as regression checks after touching the corresponding area.

**Setup**

* Remote host reachable via `~/.ssh/config` alias (reference host: `devbox`),
  `tmux` running there with at least one window hosting a Claude session.
* Remote self-reporting configured per README ("Let Claude report its state"):
  `@cs_status` hooks in `~/.claude/settings.json`, `@cs_workspace` line in the
  remote `CLAUDE.md`.
* JetBrains Gateway installed locally; Firefox installed locally.
* Start the app with `gradlew :app:run`; tasks live in
  `%USERPROFILE%\.contextswitcher\tasks\`.

Record results by checking items off; note deviations inline. Logs for
diagnosis: `%USERPROFILE%\.contextswitcher\logs\`.

**Automated UI tests (MADR 0014).**
Items that need no remote host and no human eye move into TestFX tests
(`gradlew :app:uiTest`; headless Linux: `xvfb-run -a ./gradlew :app:uiTest`, CI runs them on every push).
An item covered there is marked *(automated: `<TestClass>`)* — re-check it manually only when the note says a variant stays manual.
New checklist items for pure-UI behavior (dialogs, key chords, list filtering) should start as a UI test instead of growing this list.

## Import ([#37](https://github.com/contextswitcher/contextswitcher-private/issues/37))

- [x] **Import tmux windows…** against the remote creates one task per window;
      destination folder defaults to the last one used (survives app restart).
      *(2026-07-14, devbox.)*
- [x] Imported task title is the pane title when Claude published one, else
      the plain tmux window name; session/host context appears in the row
      subtitle, not the title. *(2026-07-14.)*
- [x] For a window running Claude: `claude.cwd` and `claude.sessionId` are
      filled in the task file; `claude.workspace` appears once the remote
      session has published `@cs_workspace`; non-Claude windows get **no**
      `claude:` section (fix `e49b0ba`). *(2026-07-14; working `--resume`
      on resurrect confirms the captured sessionId.)*
- [x] Re-import → already-covered windows are skipped, listed one per line in
      the summary dialog; **suspended** tasks do not block the import of a
      matching window. *(2026-07-14, devbox.)*

## Switch: tmux focus + status chips ([#27](https://github.com/contextswitcher/contextswitcher-private/issues/27), [#32](https://github.com/contextswitcher/contextswitcher-private/issues/32))

- [x] Click the row's **play icon** (and separately: double-click the row) on a task with a
      `tmux` section → the remote tmux window gains focus (visible in an open
      terminal attached to that session). *(2026-07-14.)*
- [x] Status chips appear per configured action, transition pending → running
      → OK, and are legible (chip visual check). *(2026-07-14.)*
- [x] Failure path: task pointing at a non-existent host → chip shows FAIL
      with a readable message; other actions still run. *(2026-07-14: red
      tmux chip, snapshot pane relays "No such host is known".)*

## Resurrect after window loss ([#43](https://github.com/contextswitcher/contextswitcher-private/issues/43))

- [x] Kill the task's tmux window on the remote (`tmux kill-window`), click
      the **play icon** → window is recreated in the recorded working directory,
      `claude --resume <sessionId>` is issued, and the new window id is
      written back to the task file. *(2026-07-14; exercised repeatedly via
      the suspend/resume flow, which shares the resurrect path.)*

## Open a window on play for a remote-only task (`dsn~remote-window-choice~6`)

- [ ] A task with only `remote:` set and **no** live `tmux:` section (commented
      template block is fine) → press the **play icon** → a dialog offers
      **tmux** / **claude** / Cancel, with **model** and **effort** pickers
      pre-selected with the last pick.
- [ ] Pick **tmux** → a plain shell window is created on the remote, started in
      the task's `workspacesRoot:`/`workdir:` (else the group config's, else
      home), and the file gains a real `tmux:` section. Play again focuses it.
- [ ] Pick **claude** → a window is created in the same workspace with a Claude
      session launched (context prompt from the `# Notes` body, else the title);
      the file gains both `tmux:` and `claude:` (`cwd`), and the published
      `sessionId` is backfilled shortly after (as for a live task).
      The session runs at the picked model and effort (`/model`, `/effort` ahead
      of the prompt), and the next start is pre-selected with them.
- [ ] Cancel → nothing happens.
- [ ] The same task's terminal pane reads "No tmux configured for this task."
      with **tmux** / **claude** buttons under it; **tmux** creates the plain
      window straight away, **claude** opens the dialog above, and a failure
      leaves the buttons pressable.
- [ ] Cancelling that dialog starts nothing and leaves both placeholder buttons
      pressable.
- [ ] "Open in IntelliJ" on a remote-only task opens IntelliJ **without** the
      tmux/claude dialog.
- [ ] After either choice, the task file is **visibly** shown with its new
      `tmux:` section in the editor lane (not the pre-play commented template),
      and a later "Sync tmux windows…" does **not** import the new window as a
      duplicate root-level task. *(Regression: the editor lane's stale clean
      buffer used to auto-save over the written section → duplicate import.)*

## JetBrains Gateway ([#28](https://github.com/contextswitcher/contextswitcher-private/issues/28), [#29](https://github.com/contextswitcher/contextswitcher-private/issues/29))

- [x] Task with an `intellij:` section, click the **play icon** → Gateway
      launches and connects to the right host and project path (directly, no
      browser confirmation in between). *(2026-07-14, devbox; needed
      `port=22` + always-`idePath` + `realpath`-canonical idePath — see
      MADR 0006 amendments.)*
- [x] `intellij.projectPath` omitted → falls back to `claude.workspace`, then
      `claude.cwd`. *(2026-07-14: the test task has a bare `intellij:`;
      the launched URL carried the claude workspace path.)*
- [x] Project already open in a JetBrains Client window → the play icon
      focuses that window, also across virtual desktops (no second window,
      no version prompt, no Gateway at all); with the client closed, the URL
      reattaches to the running backend cleanly. *(2026-07-14; closes [#28](https://github.com/contextswitcher/contextswitcher-private/issues/28).
      JetBrains' own SSH-agent consent dialog remains — use "Allow any
      request in this session".)*

## Firefox extension ([#30](https://github.com/contextswitcher/contextswitcher-private/issues/30), [#31](https://github.com/contextswitcher/contextswitcher-private/issues/31))

Load per [extension/firefox/README.md](../extension/firefox/README.md)
(temporary add-on, options page gets `wsPort`/`wsToken` from `settings.yaml`).

- [x] Extension connects (no browser chip timeout on the next switch).
      *(2026-07-14.)*
- [x] Switch, task URL already open in some tab → that tab is focused and its
      Firefox window raised (no duplicate tab). *(2026-07-14.)*
- [x] Switch, task URL not open → new tab opens with the first
      `browser.urls` entry. *(2026-07-14.)*
- [x] Suspend the task → **all** `browser.urls` tabs close. *(2026-07-14.)*
- [x] Resume → regular switch reopens/focuses the first URL. *(2026-07-14.)*
- [x] Restart the ContextSwitcher app while Firefox stays open → extension
      reconnects by itself (within backoff, ≤ 30 s); next switch works.
      *(2026-07-14.)*
- [x] No extension connected (disable it) → browser chip fails after the
      short timeout; remaining actions unaffected. *(2026-07-14.)*

## Suspend / resume (MADR 0009)

- [x] Suspend a task whose Claude is **waiting** → NO dialog; the tmux
      window is killed on the remote immediately; status flips to suspended;
      the task file's `window:` line is removed (the id is dead).
      *(2026-07-14, devbox.)*
- [x] Suspend while Claude is **working** (`@cs_status` = working) → dialog
      with **Force terminate** wording (the only two dialog cases left).
      *(2026-07-14; note: the window needs recent output — on a >90 s idle
      window the safety net downgrades `working` and suspend is silent.)*
- [x] Suspend a task without a `claude:` section → dialog warns (resume
      recreates a plain window; nothing running inside comes back).
      *(2026-07-14; the arbitrary-window resume this exposed is fixed —
      no-claude tasks now resurrect a plain window, `dsn~tmux-resurrect~7`.)*
- [ ] Suspend a `claude:` task whose file has **no `sessionId:`** yet (create a
      live task and pause within the first few seconds) → dialog warns "No Claude
      session id recorded yet…", buttons **Suspend nevertheless** / **Return**;
      Return keeps it active. Wait ~30 s for the backfill to record the id, then
      suspend again → silent (no dialog). *(dsn~task-suspend~6)*
- [x] Selecting a suspended task → snapshot pane states the task is suspended
      (no "no server running" / "can't find window" tmux error).
      *(2026-07-14.)*
- [x] Resume (click status label on a suspended task, or its play icon) →
      regular switch runs: window resurrected, `claude --resume` issued,
      status back to active, fresh `window:` line inserted into the task file.
      *(2026-07-14, via play icon, incl. automatic snapshot re-capture.)*

## Running indicator ([#41](https://github.com/contextswitcher/contextswitcher-private/issues/41))

- [x] Prompt Claude on the remote → row dot turns orange (working) within one
      poll (~5 s); when Claude finishes → green (waiting). *(2026-07-14.
      Follow-up shipped: `Notification` hook → blue `attention` dot when
      Claude asks a question / waits for a permission — add the hook on the
      remote per README.)*
- [x] Safety net: force `@cs_status` to working without output
      (`tmux set -w @cs_status working` on an idle window) → dot downgrades
      to waiting after ~90 s. *(2026-07-14: on an already-idle window the
      downgrade was immediate — suspend stayed silent instead of escalating
      to Force terminate, proving the poller reported `waiting`.)*
- [x] Dot updates do not flicker other rows or collapse expanded groups.
      *(2026-07-14.)*
- [x] A suspended task never shows a dot, even when a live window on the host
      carries the same (reused) window id. *(2026-07-14.)*
- [ ] Usage limit: on a Claude window, make the pane show a limit message
      (hit the real limit, or `echo 'You have reached your Opus limit'` in that
      window) → within one poll the row's dot turns **red**, its tooltip reads
      "usage limit reached — pay attention", the task counts in "Awaits input only"
      and sorts first under "Action needed". Prompt Claude there again (after
      `/model`) → the dot goes orange, not red, while it works.

## Awaits-input filter

- [ ] The "Awaits input only" toggle leads the toolbar, before "Add task…", and its label shows
      the count of active tasks with a green (waiting) or blue (attention) dot,
      updating within one poll as statuses change.
- [ ] Toggling it on narrows the list to exactly those rows (groups force-expand,
      empty groups and the Done section disappear); toggling off restores the full
      list. Prompt Claude on one of the shown tasks → its dot goes orange
      (working) and the row drops out within one poll.
- [ ] The filter is not persisted — restart starts with it off.

## Link opening on the category's desktop (dsn~pr-open-on-category-desktop~3)

- [ ] Category with a `desktop:` (e.g. `vs.code`), a task in it with a PR URL, and
      **no** tab for that PR anywhere. From another desktop, click the PR icon →
      the view switches to `vs.code`, a Firefox window there comes to the front,
      and the PR opens **in it** (not in the window last used on the other
      desktop). Status bar: `Firefox: opened new tab — <url>`.
- [ ] Two Firefox windows on `vs.code`: the one used last there gets the tab.
- [ ] Click it again from another desktop (the tab now exists) → the view switches
      to `vs.code`, the tab **moves** into the Firefox window there and is focused;
      the screen stays on `vs.code`. Status bar:
      `Firefox: focused existing tab (moved to this desktop) — <url>`.
- [ ] Click it once more while already on `vs.code` → the tab is already in that
      window, so nothing moves and the detail is the plain `focused existing tab`.
- [ ] `vs.code` with no Firefox window on it → the click opens a *new* Firefox
      window there carrying the PR (not a tab in the window on the other desktop);
      status bar: `Firefox: opened a window on desktop "vs.code"`.
- [ ] `vs.code` with no Firefox window on it **and** the PR already open in a window
      elsewhere → no second copy: that tab is focused where it is (the view follows
      it), status bar `Firefox: focused existing tab — <url>`.
- [ ] The queue pane's review-comment link button behaves the same: with the task in
      `vs.code` and no tab for the comment open, clicking it switches to `vs.code`
      and opens the comment there.
- [ ] Task in a category without `desktop:`, and a root-level task → unchanged
      behaviour (focus-or-open, no desktop switch) for PR, link, and review-comment buttons.
- [ ] Browser extension not connected → the status bar still reports
      `Cannot show in <configured browser>: Browser extension not connected`,
      with no desktop switch and no browser launched.

## Active-desktop filter (dsn~active-desktop-filter~6)

- [ ] Give two categories different `desktop:` values in their `CONTEXTSWITCHER.md`
      (e.g. `vs.code` and `Compilation Result`, both named in Task View). The
      "Show active desktop only" toggle sits right of "Awaits input only".
- [ ] On the `vs.code` desktop, toggle it on → only the `vs.code` category shows
      (groups force-expand, others and ungrouped tasks disappear); the button
      label reads `Show active desktop only (vs.code)`.
- [ ] Switch to the other desktop (Ctrl+Win+←/→, or a category's ▶ button) with the
      toggle on → within well under a second the list follows: now only that
      desktop's category shows, and the label updates. (Needs ContextSwitcher
      visible on all desktops to watch it live; otherwise re-toggle after switching.)
- [ ] Switch via an external hotkey tool (WindowsVirtualDesktopHelper) with the
      toggle on → same: the list and label follow within well under a second,
      not seconds later (the persistent watcher, not the old 2 s poll, must
      pick this up).
- [ ] On an **unnamed** desktop (never renamed in Task View) with the toggle on →
      the filter is a no-op (full list shown), label reads
      `Show active desktop only (unknown)`.
- [ ] Fresh login (no desktop switch since sign-in), toggle on while on the first
      desktop → the filter still narrows to that desktop's category (the registry
      has no `CurrentVirtualDesktop` yet; the script falls back to desktop 0).
- [ ] Switch desktops via a category's ▶ button (the direct COM switch) with the
      toggle on → the filter follows promptly and the label shows the desktop
      you are actually on. (The registry mirror lags behind programmatic
      switches; the read asks the shell itself, so a stale mirror must not
      narrow to the previous desktop.)
- [ ] Mixed-case check: desktop renamed `JabRef`, config `desktop: jabref` →
      matches (comparison is case-insensitive).
- [ ] With "Show running tasks only" also ticked → the list is the running tasks *of
      the active desktop* (AND), not all running tasks.
- [ ] Non-Windows / no `powershell`: the toggle is a harmless no-op (full list,
      label `(unknown)`), no crash; no `powershell` is spawned while the toggle
      is off.
- [ ] The filter is not persisted — restart starts with it off.

## Task list operations

- [ ] A group whose `CONTEXTSWITCHER.md` sets `remote:` shows a muted terminal
      icon after the header name (tooltip names the host); a group without
      one (or with only the settings.yaml fallback) shows no icon.
- [x] **Rename task** (context menu) → title changes in list and file, and the
      remote tmux window is renamed (check `tmux list-windows`).
      *(2026-07-14, devbox.)*
- [x] Drag a task row onto a group header → file moves into the folder, id
      becomes `group/name`; drop onto a root-level row → ungroups. No
      overwrite when the target name exists — the moved file gets a
      `-2`/`-3`… suffix. *(2026-07-14, devbox setup: verified incl. drag
      starting from an unselected row — fix `89a14bd`; suffix-instead-of-
      refusal added after a collision during live use.)*
- [x] With more tasks than fit the list, drag a row and hold the cursor near
      the list's bottom (then top) edge → the list scrolls on its own, faster
      the closer to the edge, and stops when the cursor moves back inward or
      leaves the list; the drop on a scrolled-in row still moves the task.
      *(2026-07-19, verified live by Oliver.)*
- [x] **Move to category** (task context menu) lists every category sorted
      (current one omitted) plus "(no category)" on a filed task → picking one
      moves the file exactly like a drop; a root task shows no "(no
      category)" entry. *(2026-07-19, verified live by Oliver.)*
- [x] After a move (drag or menu, incl. into a **collapsed** category) the
      moved row is selected and scrolled into view — the target category
      expands if needed; a background file change while scrolled elsewhere
      still does not yank the viewport.
      *(Automated: `MoveToCategoryVisibleUiTest` covers the off-screen-category
      select+scroll case; verified 2026-07-23.)*
- [x] Status label click toggles active ↔ suspended (suspend flow above);
      **Mark done** and **Delete task** (confirmed) work from the context
      menu. *(2026-07-14.)*
- [ ] Switching a task with IntelliJ on a **cold** Gateway start → the
      `intellij` chip stays the blue hourglass (⏳) while Gateway uploads the
      worker binary / connects, and only turns green (✓) once the project's
      JetBrains Client window actually appears; a very slow start eventually
      goes green on timeout rather than red.
- [ ] **Open in IntelliJ** (context menu): on a task whose path resolves
      (a `claude:` session or explicit `intellij.projectPath`) a bare
      `intellij:` line is written and the switch opens the project (as play
      does); on a task with no derivable path (e.g. PR-created, no `claude:`)
      it prompts for the project path and writes `intellij.projectPath`
      (cancel/blank aborts, file untouched); an existing `intellij:` section is
      left untouched and it just switches. The acted-on row is (and stays)
      highlighted afterwards — the `intellij:` write's watcher rebuild must
      not move the selection to another row or drop it.
- [x] Delete an **active** task with a tmux window → dialog announces the
      teardown; confirming kills the window / closes tabs (chips), then
      removes the file; with Claude working the button reads
      **Force terminate and delete**. Afterwards the neighbouring task is
      selected — the snapshot pane no longer shows the deleted session.
      *(2026-07-14, incl. selection surviving the rebuild — fix `dd3e211`.)*
- [x] Hovering a task row shows the icon buttons (icons render, no layout
      shift); play switches (resumes a suspended task), pause — active tasks
      only — suspends with the usual confirmations, trash deletes
      (confirmed); buttons vanish when the mouse leaves. There is no separate
      Switch button; the context menu carries a `Switch` item instead.
      *(2026-07-14, after the SvgNode swap + explicit fill color.)*
- [x] **Add task…** with `folder/title` input → slugged file in the group,
      opens in the editor lane. *(2026-07-14.)*
- [x] **Add category…** creates an empty group folder, visible immediately
      with an `Add…` row. *(2026-07-14.)*
- [ ] Group header right-click → **Delete category…** → confirm names the task
      count and irreversibility; on **Delete** the folder and its tasks vanish
      from the list. Cancel leaves everything. An open editor lane showing a
      file in the folder clears.
- [ ] Delete a category holding a running task → after the category confirm,
      the normal task-delete dialog appears for that task (end window /
      transcript / workdir). **Delete** it → tears down and continues; the
      folder goes. Repeat with two running tasks and **Cancel** the second
      dialog → deletion stops, the first (already deleted) task stays gone, the
      rest of the category remains.
- [x] Group header right-click → **Add CS config…** writes the skeleton
      `CONTEXTSWITCHER.md` and the menu entry flips to **Edit CS config…**.
      *(2026-07-14.)*
- [x] Group `note:` URL (onenote: form) → **Open note** (menu and header
      note icon) opens desktop OneNote. *(2026-07-14; needed the raw
      ShellExecute dispatch `13e60d6` — onenote page links are no valid
      `java.net.URI` — and click-time URL resolution `1818f2d`.)*
- [x] Narrow the window → only title/subtitle ellipsize; status label and
      action icons keep their size; group rows stay edge-aligned.
      *(2026-07-14.)*

## Local Windows Terminal focus (MADR 0008)

- [x] Task with `terminal.tabTitle` matching a pinned WT tab title → Switch
      focuses that tab and raises the window, also from another virtual
      desktop. *(2026-07-14; cross-desktop needed the EnumWindows
      title-match jump `deabd3b`. Accepted limitation: a non-active tab in
      a window on another desktop is unreachable — cloaked windows expose
      no tab list.)*

## Category desktop focus (dsn~category-desktop-focus~4)

- [ ] Name a virtual desktop in Windows Task View (e.g. `vs.code`), then set
      `desktop: vs.code` in a category's `CONTEXTSWITCHER.md` → the group header
      shows an always-visible monitor icon ("Jump to desktop") right of the
      category's terminal icon; clicking it switches Windows to that desktop and
      does **nothing else** (no task switch).
      Status bar reads `Desktop: focused desktop "vs.code"`.
- [ ] From a desktop several positions away (e.g. desktop 1 → desktop 5), the
      switch **jumps** — one transition, no visible walk through the desktops in
      between — and the status bar carries **no** `(hotkey walk)` suffix.
      A `(hotkey walk)` suffix means this Windows build moved
      `IVirtualDesktopManagerInternal`; the walk still switches correctly, and
      the fix is a second interface declaration for that build (MADR 0019).
- [ ] A category with no `desktop:` shows **no** header monitor icon.
- [ ] `desktop:` naming a desktop that does not exist → status bar reads
      `Cannot focus desktop: desktop not found: "…"`; nothing switches.
- [ ] Edit `desktop:` in the right-lane editor (unsaved), then click the header
      monitor icon → the just-typed name is honored (editor is flushed first).
- [ ] Non-Windows / no `powershell`: the button is a graceful no-op with a
      logged status message, no crash.

## Editor lane

- [x] Edit a task file, click elsewhere (focus loss) → auto-saved; watcher
      does not churn (no reload loop). *(2026-07-14; exercised throughout
      the session.)*
- [x] **Revert** reloads from disk; Ctrl+Z / Ctrl+Y undo/redo within the
      session; Ctrl+S still saves manually. *(2026-07-14; Revert needed the
      focus-traversal fix `dd3e211` — clicking it used to auto-save first.)*
- [x] A file with broken YAML shows as an error row but opens in the editor
      and can be fixed there. *(2026-07-14.)*
- [ ] **No self-corruption on a vanished file** (`dsn~richtext-markdown-editor~9`):
      rename or delete a task's `.md` on disk, then trigger a frontmatter write
      for it (toggle status, run Sync while its session id is unrecorded, adopt a
      published title) → the write is skipped with a `file unreadable` log line;
      **no** file is (re)created holding the `Cannot read …` error text as content.
- [ ] **Field help (F1)** (`dsn~task-field-help~1`): with a task file open, press
      **F1** → a non-modal popup shows the commented `TEMPLATE.md` field reference;
      select a section, Ctrl+C, paste into the editor; **Copy all** copies the whole
      thing and the popup stays open. Open a category `CONTEXTSWITCHER.md` and press
      F1 → the popup shows the group-config fields (`remote`, `workspacesRoot`,
      `workdir`, `repo`, `tags`, `note`) instead. The type label's tooltip mentions F1.
- [x] **Settings field help (F1)** (`dsn~settings-editor~4`): open `Edit settings…`
      (header mentions F1), press **F1** in the text area → a non-modal popup shows
      the commented settings.yaml reference (`tasksDir`, `wsPort`, `wsToken`, `hints`,
      `claudeAuto`, `remotes`, `tags`); **Copy all** works and the popup stays open
      while the settings dialog remains usable.
- [ ] **Editor auto-save fence guard** (`dsn~richtext-markdown-editor~9`):
      open a task, switch the **Configuration** pane to **Raw YAML** and clear
      it, click away (focus-loss auto-save) → the file label reads `not saved
      (would drop '---' fence)`, a warn line is logged, and the file on disk
      keeps its fence (the task does **not** turn into a red `must start with
      '---' fence` row). A normal edit in either pane still saves.
- [ ] **Notes and Configuration panes** (`dsn~richtext-markdown-editor~9`):
      select a task → **Notes** shows the body, **Raw YAML** in
      **Configuration** the YAML without `---` lines; edit both, click away →
      the file on disk has both edits with the fences intact. With
      **Configuration** in front, select another task → Configuration stays in
      front and shows the new task. Open a `CONTEXTSWITCHER.md` → the
      Configuration pane comes to the front.
- [ ] **Delete a corrupt file from its error row** (`dsn~corrupt-task-delete~2`):
      hand-break a task file's frontmatter (remove the opening `---`) so it shows
      as a red error row → hovering the row reveals a trash button (and a context
      menu with "Copy file path" and "Delete file…"); clicking the trash confirms,
      deletes the file, and the red row disappears. If that file was open in the
      editor, the editor clears. "Copy file path" puts the file's absolute path on
      the clipboard.

### Add task targets the selected category (`dsn~task-create-ui~15`)

- [ ] Select a category (group header, or any task inside it), click the toolbar
      **Add task…** → the dialog's **Category** combo shows `<name>` and the new
      task file lands in that category's folder (inheriting its workdir/remote
      defaults). With nothing / a root task selected, it adds to the root as before.
- [ ] Same with **Ctrl+T**, also while the terminal pane has the focus.
- [ ] In the dialog, pick another category in the combo → the remote/local hints and
      the default button switch to that category's, and the task lands there.

### Add local Claude in a local category (`dsn~task-create-local~4`)

- [ ] In a **local** category — its `CONTEXTSWITCHER.md` sets `workspacesRoot:`
      (or `workdir:`) and **no** `remote:` — click its **+** → the dialog shows
      **Add local Claude** right of **Add remote Claude**, enabled, as the
      default button, and the hint names the directory. Type a description,
      **Ctrl+Enter three times** → the task file appears in that category (with `folder:`
      set), the **terminal pane** shows Claude running in that directory with
      the description as its first prompt, and the status bar reports
      `Claude started in <dir>.`
      Watch for the prompt arriving intact — a `"` in the description is the
      risky part (`cmd` re-parses the quotes).
- [ ] Same on **Linux**: the dialog's hint reads `Local Claude opens a tmux
      window in "<dir>" (group default); attach with tmux attach -t 0.`, no
      window opens, the status bar reports
      `Claude started in <dir> (tmux attach -t 0).`, and `tmux attach -t 0`
      shows a window named after the directory with Claude running in it and
      the description as its first prompt. Repeat once with no tmux server
      running at all (`tmux kill-server` first) — the session is created.
- [ ] **Linux, the rest of the task** (`dsn~terminal-local-mirror~2`): the same
      task's queue sends — type a message, `➤` → it arrives in the local chat;
      drop an image in and send → the chat reads it from where it lies (no copy
      is uploaded anywhere). The row shows the running dot while Claude works.
      Delete the task → the dialog offers **End the tmux window** (and **Ask
      Claude to tidy up first**); with it ticked, `tmux list-windows` no longer
      shows the window. Suspend does the same and keeps the last screen.
- [ ] **Linux, the mirror** (`dsn~terminal-local-mirror~2`): the same task's file
      gains a `tmux:` section (session `0`, the new window id) and no `remote:`,
      and the terminal pane shows that Claude session live — typing into the pane
      reaches it. `Show diff` and `Files` are disabled while it is shown.
      Selecting another local task of the same session switches the pane to its
      window without disturbing what a `tmux attach -t 0` in a terminal of your
      own has selected.
- [ ] In a **remote** category (its config sets `remote:`) the local button is
      disabled and **Add remote Claude** is the default; in a category with no
      config (and in the root) both Claude buttons are non-default and
      **Add plain task** is the default — even when `settings.yaml` configures a
      fallback remote (the remote button stays clickable).

### A local Claude task gets a short title (`dsn~task-create-local-title~1`)

- [ ] **Windows**, local category: add a local Claude task with a two-sentence description.
      Within about ten seconds the row shows a 3–6 word title, the task file's `# Notes` ends with the description, the file name is unchanged, and the session keeps running in the pane.
- [ ] Same on **Linux** (local tmux): the row's title changes, the tmux window keeps its directory name.
- [ ] A short title typed instead (`Fix login`) stays exactly as typed.
- [ ] A long description renamed right away, before the title arrives, keeps your rename.
- [ ] **Linux** with `claude` off `PATH` and out of `~/.local/bin`: the title becomes the description's first words.

### Windows: the app owns the local Claude session (`dsn~terminal-owned-session~3`)

Windows only — ConPTY exists nowhere else, so none of this can be checked on Linux.

- [ ] **Typing reaches Claude.** After the local creation above, the terminal
      pane shows the live session (not "nothing to mirror here"); type into it
      and Claude answers. The task file gains `claude.cwd` and **no** `tmux:`
      section and no `remote:`.
- [ ] **The bar works locally.** With that session shown, all four buttons are
      enabled and the row shows **no** running dot. Type a draft over two lines,
      **Clear input** → the box is empty. Scroll up, **Jump to bottom** → back at
      the live output. **Show diff** → a window with the workspace's colored
      `git diff HEAD` (or `git show` when clean). **Files** → the newest files
      under `%TEMP%\claude\…\scratchpad`; picking one opens it, and
      **Custom path…** opens a typed local path.
- [ ] **Another task and back.** Select a different task, then the local one
      again → the same session comes back with its scrollback, still running.
- [ ] **Resume after a restart.** Close the app (the session ends with it),
      reopen it and select the task → the pane offers **resume**; clicking it
      continues the *same* conversation. Check the transcript id: the newest
      `.jsonl` under `%USERPROFILE%\.claude\projects\<encoded cwd>` is the one
      that keeps growing, and no new one appears.
- [ ] **A directory with no transcript yet** offers **start** instead, and the
      session it opens is a fresh conversation.
- [ ] **No remote offer.** A task of a local category never shows the **tmux** /
      **claude** buttons under the placeholder — those open a window on a
      remote, which it has none of. A plain task added to the same category
      (never started) offers **start** in its `folder:`; a task with neither a
      `claude.cwd` nor a `folder:` keeps the plain
      `No tmux configured for this task.`

### Add-task field is multi-line, Enter does not submit (`dsn~task-create-ui~15`)

- [ ] **Add task…**, type a sentence, press plain **Enter** mid-description →
      a newline appears, no task is created, the dialog stays open. Continue
      typing a second line, press **Ctrl+Enter three times** → the field greens
      up a shade per press and the third one fires the default button (remote
      Claude when a remote resolves); the task carries the full multi-line
      description. One Ctrl+Enter alone does nothing but colour. Regression: a
      plain Enter used to fire the default button and create the task from the
      half-typed text, and a single Ctrl+Enter used to send it off mid-thought.
      *(Automated: `AddTaskDialogUiTest`, MADR 0014 — manual re-check only
      needed for the remote-default-button variant.)*
- [x] **Queue chords work in the dialog too** (`req~compose-key-conventions~5`):
      **Add task…**, type a description, press **Enter** three times in a row →
      the default button fires and the created task's description carries no
      trailing blank lines. **Shift+Enter** inserts a newline like plain Enter.
      Three Enters into an empty field do nothing.
      *(2026-07-20, fully automated: `AddTaskDialogUiTest`, MADR 0014.)*

### Add-task field takes attachments (`dsn~task-create-ui~15`, `dsn~task-from-pr~6`)

- [x] **Add task…**, type a description, drag a file (e.g. a PDF) from the OS onto
      the field → a `[file: …]` marker appears at the caret. **Ctrl+Enter three times** →
      `Add remote Claude`: the file is uploaded to the remote's
      `~/.contextswitcher/attachments/` and Claude's initial prompt carries the
      remote path (in backticks) instead of the marker — ask Claude to read it.
      Ctrl+V with a screenshot on the clipboard inserts an `[image: …]` marker
      that travels the same way.
      *Verified live by Oliver, 2026-07-19 (devbox).*

### PR-task creation does not steal a queue box (`dsn~task-from-pr~6`)

- [ ] Select task A, start **Add from PR…** with a PR URL, and while the window
      is being created type a message into A's queue add box → the created PR
      task appears in the list but the selection stays on A and the typed text
      keeps its caret. With focus anywhere else (the task list, say) the new PR
      task is selected as before.

### Failed creation retry keeps the text (`dsn~task-create-retry-prefill~1`)

- [ ] **Add task…** on a group whose remote is unreachable (e.g. temporarily
      break the `remote` in the group's CS config), type or edit a multi-line
      description, **Ctrl+Enter three times** → the `Cannot create a tmux window…` error
      appears. **Add task…** again without copying anything new → the field
      holds the full failed description, not the raw clipboard pre-fill.
      Copy some other text before reopening instead → the field holds that
      new clipboard content (changed clipboard wins over the failed text).

### Live-task creation (`dsn~task-create-live~8`)

- [ ] **A new remote task takes the selection and shows its launch**
      (`dsn~task-create-live~8`): select some task A, then `Add task…` →
      `Add remote Claude` and create a live task B. B is selected the moment its
      row appears, and the terminal lane reads the current step ("Creating the
      tmux window …", "Starting Claude …") instead of "No tmux configured for
      this task."; once the window exists the mirror attaches and Claude is seen
      coming up. After Claude publishes `@cs_title`, B's file is renamed to the
      adopted title and stays intact (the editor lane follows the rename).
- [ ] **A queue box that had the keyboard does not cost B its selection**
      (`dsn~task-create-live~8`): click into the queue message box of task A,
      then `Add task…` → `Add remote Claude` and create B. B is selected as its
      row appears — the guard that protects a half-typed queue message applies
      to the pull-request flow only.

## Post-v0.1 wave (M2, awaiting live E2E)

Code-complete + unit-tested + trace-green; **not yet run against devbox.**

### Live terminal mirror ([#35](https://github.com/contextswitcher/contextswitcher-private/issues/35))

- [ ] Selecting a live task mirrors its remote tmux/Claude window in the
      embedded terminal (JediTermFX, `ssh -t`, grouped `cs-mirror` session).
- [ ] Switching between two tasks of the **same** tmux session → the
      terminal briefly shows a "Switching to window …" blank instead of the
      previous window's content, then reveals the new window — no ambiguity
      about which task's terminal is shown.
- [ ] Typing reaches the shell; the cursor is a steady block; the pane
      resizes with the window.
- [ ] **Ctrl+C** interrupts, **Ctrl+A/Ctrl+E** move to line start/end,
      **Shift+Tab** cycles Claude's mode (reverse-tab `ESC [ Z`).
- [ ] Clicking the terminal keeps the left-rail row highlight (so the
      current task is still obvious).
- [ ] A suspended task shows the "suspended — Switch resumes it" hint, not
      a tmux error.
- [ ] **Auto-reconnect ([#36](https://github.com/contextswitcher/contextswitcher-private/issues/36)):** kill the remote `ssh`/pty (or drop the
      network) → the pane shows "reconnecting (n/N)…" and re-attaches on its
      own; selecting another task during the wait cancels it; a suspend does
      **not** trigger a reconnect.

### Group config + task creation ([#45](https://github.com/contextswitcher/contextswitcher-private/issues/45))

- [ ] Clicking a group header (not the arrow) opens its `CONTEXTSWITCHER.md`
      in the editor (creating the skeleton first when missing); no cog icon
      appears on hover.
      Clicking the header's ▸/▾ arrow only collapses/expands the group.
- [ ] `Add category…` creates the folder **and** opens its
      `CONTEXTSWITCHER.md`, labelled "Category config file"; `remote` is
      pre-filled from the first `settings.yaml` remote.
- [ ] With a task selected (editor/terminal mirroring it) and having just
      created a new tmux window manually, `Add category…` leaves the new
      category focused — the config stays in the editor, its header is
      selected, the terminal shows the "Select a task…" placeholder — and does
      **not** flip back to the previously selected task a moment later.
- [ ] A new task in a group inherits `remote` and `claude.cwd` =
      `workspacesRoot` (verbatim, no task suffix) / `workdir`.
- [ ] `Add task…` shows three buttons; **Add remote Claude** is enabled +
      default only when the group sets a `remote`, disabled otherwise (hint
      explains why).
- [ ] **Add remote Claude** creates a tmux window + Claude session in the
      workspace, primes the context prompt (submitted), writes the task
      file, and mirrors it in the terminal.
      *(Note: `workspacesRoot` must exist on the remote — no `mkdir` yet.)*

### PR-state indicator + capture ([#49](https://github.com/contextswitcher/contextswitcher-private/issues/49), [#46](https://github.com/contextswitcher/contextswitcher-private/issues/46))

- [ ] A task with a GitHub PR URL shows a coloured icon: open (green),
      draft (muted), merged (purple), closed (red), or muted `?` when `gh`
      cannot resolve it; tooltip names the state and shows the URL.
- [ ] Clicking the icon focuses (or opens) the PR tab in Firefox and the
      status bar shows `Opening <url> …` then the outcome (`Firefox: focused
      existing tab` / `focused related tab` / `opened new tab`, or
      `Cannot show in Firefox: …` when the extension is not connected).
- [ ] With the PR already open in Firefox on a **sub-path** (e.g. the
      PR's `/files` tab) → clicking the icon focuses that existing tab
      (reports `focused related tab`) instead of opening a duplicate.
      *(needs the updated extension reloaded in Firefox.)*
- [ ] With Firefox behind the app window → clicking the PR icon raises the
      Firefox window to the front (not just a flashing taskbar button).
- [ ] With **many** Firefox windows open → clicking the PR icon raises the
      *specific* window holding the PR tab (matched by the tab's title), not
      an arbitrary Firefox window.
- [ ] PR **not** open in any tab → clicking opens it in a new **foreground**
      tab and (once the page title loads, within ~3 s) raises that window;
      a very slow page just opens in the background.
- [ ] A PR Claude opens **after** import (published as `@cs_pr`) is written
      into the task file within a poll interval and then shows its state.
- [ ] A session that opened **two** PRs (a code PR and a docs PR, one `PR:`
      footer line each) gets **both** URLs in `browser.urls` within a poll
      interval, and the row shows **two** icons — each with its own state,
      its own hover URL, and its own `Open PR` / `Copy URL` menu.
- [ ] A second PR opened **later**, while the task already has one, is
      appended too (the scrape does not stop at the first PR).
- [ ] **Review comments sync** on a two-PR task: the first PR with open review
      comments wins; a PR with none does not stop the search.
- [ ] A sync also queues **human** reviewers' inline comments, each prefixed
      `Review comment by @<login> on <path>:<line> (<comment URL>):` — while your own replies,
      other bots' comments, and every thread you already answered stay out.
      Edit such a card and send it like any other message.
- [ ] Ticking a card's box below its 🗑 moves it into the collapsed
      **Read (N)** section at the pane's bottom; unticking brings it back, and
      the mark survives a task switch and an app restart.
- [ ] After a sync, each review-sourced card shows an open-in-browser button
      below its send button → clicking it focuses the review comment's tab in
      Firefox (opens one if none), scrolled to that very suggestion; a second
      click focuses the same tab instead of opening another. Hand-typed cards
      show no such button, and the rows stay aligned.
- [ ] With the Firefox extension **not** connected: right-click that button
      → **Copy URL** puts the bare comment URL on the clipboard (status line
      confirms), pasteable into any browser.

### List / dialog operations

- [ ] Rename a project group from its header → folder renamed, task ids
      follow, editor/collapsed state follow.
- [ ] Delete offers **Delete** / **Terminate and delete** / **Cancel**;
      plain Delete keeps the remote session, Terminate runs the teardown.
- [ ] **Sync tmux windows** reconciles active/suspended state from the
      configured `settings.yaml` `remotes` and imports new windows.
- [ ] **Edit settings…** opens the raw `settings.yaml`, focuses the text
      area, saves back.
- [ ] `Add link…` shows the live interpretation label (OneNote note vs
      browser URL); an `onenote:` link (or OneNote clipboard) becomes the
      note, an http(s) URL a `browser.urls` entry with an optional title.
- [ ] No status text column; the leading bubble carries the status —
      a suspended task shows a pause glyph, a done task a check glyph,
      an active task the live running dot. Suspend/resume works via the
      hover pause/play buttons and the context menu.
- [ ] Deleting a group folder in Explorer drops its header from the list.

### Status net

- [ ] A `working` window that goes silent downgrades to `waiting` after
      ~15 s (was 90 s).

### Task tags + filter

Setup: add a `tags:` palette to `settings.yaml` and restart, e.g.
```yaml
tags:
  - name: phone
    color: "#2da44e"
  - name: jabref
    color: "#8250df"
```

- [x] A task's `tags: [phone, jabref]` frontmatter renders as small colored
      chips on the row's second line (below the title), in the palette colors;
      a tag not in the palette, or a palette tag with no `color:`, shows as a
      muted gray chip.
- [x] A group's `CONTEXTSWITCHER.md` `tags: [jabref]` shows a chip on the group
      header and on every task in the group (inherited); filtering by that tag
      shows the whole group even though the tasks don't list it themselves.
- [x] Row context menu → **Tags** submenu toggles a tag on/off (checkmark
      reflects current state); the file's `tags:` line is written/removed and
      the chip appears/disappears.
- [x] Group header context menu → **Tags** submenu toggles the group's own
      tags (creating its `CONTEXTSWITCHER.md` if missing); the header + child
      chips update.
- [x] The toolbar **Tags** button matches the other toolbar buttons in size.
- [x] Editing the `tags:` palette in **Edit settings…** refreshes the Tags
      menu and chip colors immediately (no restart); a selected tag that is
      removed drops out of the active filter.
- [x] Toolbar **Tags** button lists the palette tags; selecting one narrows
      the list to tasks carrying it, force-expanding groups + Done and hiding
      empty groups (like Ctrl+F). The button shows the active count.
- [x] Selecting a **second** tag narrows further (AND — only tasks with both
      show).
- [x] While filtering, each visible row shows **only the selected tags**, not
      the task's other tags.
- [x] **Clear filter** (and unchecking all) restores the full list and shows
      every tag again.
- [x] The filter resets on restart (never persisted).
- [x] Ctrl+F also finds a task by a tag name.
- [ ] Select task A, Ctrl+F, search for a *different* task B and Enter (B
      selected); clearing the query / Escape keeps **B** selected — the list
      does not jump back to A.
- [x] With no `tags:` palette configured, the toolbar **Tags** button is
      disabled with a hint tooltip.

### Task directory git backup (MADR 0011)

Setup (one-time, in `%USERPROFILE%\.contextswitcher\tasks`):
`git init`, `git remote add origin <private-repo>`, `git add -A`,
`git commit -m init`, `git push -u origin main`.

- [ ] **Fresh machine:** delete/rename the tasks dir → start the app → the
      setup wizard opens; its third page takes the clone URL. A repo URL there
      clones the tasks in (no TEMPLATE seeded, tasks appear); a bad URL shows
      an error and starts empty; an empty field starts fresh (TEMPLATE seeded).
- [ ] **Wizard, remote page:** an unknown host → *Next* shows the ssh error and
      stays; a host without `claude` → "Missing on host: Claude Code …" and
      stays; a real host → "OK: tmux x.y, Claude Code x.y.z", the settings'
      `remotes` gain it, and the first project's root is proposed under that
      host's home.
- [ ] **Wizard, tools page (remote):** three rows probe within seconds; on a
      host without them each says "Not installed"; *Install* on codegraph →
      "OK: 1.6.0", `codegraph --version` works in a fresh login shell there and
      `~/.claude/skills/codegraph/SKILL.md` exists; *Install* on RefactoringMiner
      → after Finish, `refactoringMinerHome` is in settings.yaml.
- [ ] **Wizard, tools page (this machine, Linux):** two rows, no
      RefactoringMiner; on Windows the page is skipped.
- [ ] **Wizard, extension page:** the token matches settings.yaml, *Copy*
      puts it on the clipboard, the install-notes button opens the README of
      the configured browser; on a re-run with the extension connected the
      page says so.
- [ ] **Wizard, this machine:** the first page shows "OK: … on this machine"
      (or what is missing) within a second of opening; with `claude` off the
      PATH, *Next* with "On this machine" stays on the page.
- [ ] With tasks already present (dir non-empty) → no wizard on start.
- [ ] **Wizard re-run:** add menu → *Setup wizard…* → the host field holds
      the first configured remote, the tools page finds RefactoringMiner at
      the configured directory, a project root is proposed next to the
      existing ones; no clone page; a new
      remote appears in Settings without restart; installing RefactoringMiner
      there puts "restart for the refactoring badge" on the status bar.

- [ ] With the tasks dir a git repo → make a change in the app (edit a task,
      toggle status, add a tag, create/delete/move) → within ~3 s a commit
      `ContextSwitcher auto-backup <timestamp>` appears and is pushed
      (`git log`, and the remote updates).
- [ ] A burst of changes (a switch rewriting the `window:` line, several
      quick edits) coalesces into a **single** commit, not one per file.
- [ ] Push from the *other* machine first, then change something here →
      the backup **merges** the remote commit in (a merge commit appears in
      `git log --graph`) and pushes; the pulled files appear in the list, no
      non-fast-forward rejection.
- [ ] Force a same-file conflict (edit the same task on both machines, other
      pushed first) → the local commit is made but **not** pushed, the merge
      is aborted (repo not left mid-merge), and it only logs.
- [ ] Offline / no upstream → the app still works; the commit is made and the
      failed pull/push only logs (no dialog, no freeze).
- [ ] With the tasks dir **not** a git repo → nothing happens (no error).
- [ ] `.git` never appears as a project group in the list, and git's writes
      don't cause the list to flicker/rebuild.
- [ ] **Startup pull** (`dsn~task-git-backup~5`): push a task change from the
      *other* machine while this app is closed → start the app → the pushed
      task appears within a second or two (no button press), log line
      `Task directory synced from remote on startup.`.
- [ ] **Close-time commit + push** (`dsn~task-git-backup~5`): change a task,
      close the app → `git log` in the task directory shows one
      `ContextSwitcher auto-backup …` commit, pushed to the remote. No commits
      appear while the app runs.
- [ ] **Stale-lock recovery** (`dsn~task-git-backup~5`): with the app closed,
      `touch %USERPROFILE%\.contextswitcher\tasks\.git\index.lock`, wait > 30 s,
      start and close the app with a change pending → the sync removes the
      stale lock (log `Removed a stale .git/index.lock …`) and commits normally
      instead of silently stalling. (A *fresh* lock < 30 s old is left alone.)
- [ ] **Automatic conflict resolution** (`dsn~task-merge-resolution~2`): with
      both machines on the same task, set its status on one and add a tag on
      the other while they are apart, then close both → the file ends up with
      *both* changes, no `<<<<<<<` markers, log line
      `Task sync: merge conflict resolved automatically.`.
      Change the **same** key differently on both → the later commit's value
      wins, still without markers.
      Delete the task on one machine, edit it on the other → the task stays
      deleted (deletion wins).
      Rewrite the notes text differently on both → a headless `claude -p` run
      resolves the file (needs `claude` on PATH; without it, the merge is
      aborted and the local commit stays unpushed for manual resolution).

### Empty categories tracked with .gitkeep (dsn~empty-category-gitkeep~1)

- [ ] Create a new empty category (**Add category…**) → the folder on disk
      gets a `.gitkeep`, so it commits + pushes and appears on the other
      machine as an empty category (with its "add first task" affordance).
- [ ] Add the first task to that category → the `.gitkeep` disappears (folder
      now holds a real task file); the category still shows.
- [ ] Delete the last task of a category (or move it out) → the folder gets a
      `.gitkeep` again so the empty category is not lost on the next machine.
- [ ] The `.gitkeep` never shows as a task/error row in the list.

### Skeleton hints (compact mode)

Precondition: `hints: false` in `settings.yaml`, app restarted.

- [ ] **Import tmux windows** → the new task file has no commented example
      lines (`# Fill in when ready`, `# intellij:`, `# browser:`) and nothing
      below `# Notes`; a captured `claude:` section / PR URL is still written.
- [ ] **Add plain task** in a group with defaults → frontmatter carries only
      `title`/`status` plus the real `remote`/`claude.cwd` (or `folder`), no
      comments, empty Notes body.
- [ ] **Add from PR** / **Add remote Claude** → no `Created …` line below
      `# Notes`.
- [ ] Reset `hints: true`, restart → import skeleton carries the hints and
      the provenance note again.

## Message queue (feat~message-queue~1)

Precondition: a task with `remote` + `tmux` pointing at a live Claude window; the queue pane shows below the editor.

- [ ] Type into the top (empty) box, click elsewhere → a message card appears
      below; restart the app → the queue is still there
      (`<tasksDir>/.queues/<task>.yaml`).
- [ ] **`.queues/` is invisible in the app** (`dsn~message-queue-store~2`): with
      queued messages present, the `.queues` folder never shows up as a category
      row and its `*.yaml` never shows as a task.
- [ ] **Queues ride the git backup** (`dsn~message-queue-store~2`, needs the
      task-dir backup repo): queue a message → within a few seconds a
      `ContextSwitcher auto-backup` commit includes `.queues/<task>.yaml`; on a
      second machine, pull and open the app → the queued message is there.
- [ ] **One-time migration** (`dsn~message-queue-store~2`): upgrading with an
      existing `<configDir>/queues/` (and `/qodo/`) → on first launch they move
      to `.queues/` (and `.queues/qodo/`); the queues still show for each task.
- [ ] Type into the top box, press Ctrl+Enter **once** → nothing leaves the box,
      it just turns a light green; click into it again → the green is gone and
      the text is still there.
- [ ] Type into the top box, press Enter three times → card appears with the
      trailing blank lines stripped, caret back in the emptied add box;
      pressing Enter three times into an empty box does nothing.
- [ ] **Ctrl+Enter three times sends** (`dsn~message-queue-ui~26`): type into the
      add box, press Ctrl+Enter three times in a row → the message stays in the
      box for the first two presses (light green, then a shade more), and the
      third queues **and** sends it — it lands in the Claude input, is submitted,
      and leaves the queue again; the strongest green flashes for about a second.
      Typing anything between two presses starts the run over (the green clears).
      On a task without `remote`/`tmux` the third press queues the message and
      the status line says "Queued — this task has no tmux window to
      send to."; on a **suspended** task it arms the message instead and says
      the chat is being resumed.
      *(Automated up to the send: `QueueComposeKeysUiTest`, MADR 0014.)*
- [ ] **The sending box is locked** (`dsn~message-queue-ui~26`): send a card to a
      slow remote → while "Sending…" stands, typing into that card does nothing
      and its send button is disabled; on success the card is gone, and after a
      failure the box takes edits again for a retry.
      *(Automated on the after-failure half: `QueueComposeKeysUiTest`, MADR 0014.)*
- [ ] Edit a middle card, click elsewhere → reopen the task → the edit stuck.
- [ ] **A send survives a task switch** (`dsn~message-queue-ui~26`): click a
      card's send button and immediately select another task in the list →
      once the send finishes, switch back → the message is gone from the queue
      and shows in the last-sent box (it used to stay queued forever).
- [ ] Edit a middle card, press Ctrl+Enter three times → the card greens up a
      shade per press, and the third saves the edit **and** sends that card;
      on a task without `remote`/`tmux` the edit is saved and the status line
      says "Saved — this task has no tmux window to send to."
      Pressing Enter three times in a card only inserts the blank lines —
      a card never submits on Enter.
      *(Automated up to the send: `QueueComposeKeysUiTest`, MADR 0014.)*
- [ ] In the add box and in a card, press Shift+Enter → a newline is inserted
      at the caret (no commit); with text selected it replaces the selection.
- [ ] All text boxes (add box and cards, including the bottom one with the
      send button) start at the same x position; the send arrow points left.
- [ ] Hover a card → ✕ appears; click → card gone (file updated; deleting the
      last message removes the file).
- [ ] Drag a card by its ≡ handle onto another card → order changes and
      persists.
- [ ] Click ➤ on the top card → the message lands in the Claude input and
      is submitted (multi-line arrives as one block); the card leaves the
      queue.
- [ ] Every card shows its own ➤; click ➤ on a **middle** card → exactly that
      message is sent and leaves the queue, the others keep their order.
- [ ] **Last sent stays visible** (`dsn~last-sent-message~5`): send a queued
      message → a read-only "Last sent" box (muted caption, different
      background) appears at the top of the pane showing exactly that message; a later
      send replaces it; restart the app → still there
      (`.queues/<task>.sent.txt`); switch to a task never sent to → no box.
- [ ] Scroll up to an older sent box, right-click → **Resend** → exactly that
      message is sent again; the queue is unchanged.
- [ ] Screenshot to clipboard, Ctrl+V into a box → `[image: …png]` marker
      inserted, PNG exists under `<configDir>/attachments/`; send → file
      exists on the remote under `~/.contextswitcher/attachments/`, the chat
      message carries the absolute remote path in backticks, Claude can read the image.
- [ ] Drag a file **whose name contains spaces** (e.g. a PDF) from the file
      manager onto a box → `[file: …]` marker inserted, a timestamp-prefixed
      copy with spaces replaced by `_` exists under
      `<configDir>/attachments/`; send → file exists on the remote, the chat
      message carries the absolute remote path in backticks, Claude can read the file.
      Dragging a card by its ≡ handle still reorders (no file dialog
      interference); dropping a folder does nothing.
- [ ] Task without `remote`/`tmux` → queue editable, ➤ disabled with tooltip.
- [ ] Send with the remote unreachable → error in the status line, message
      stays queued.

## Live task: description prompt + title sync (req~claude-title-sync~1)

Precondition: a group with a `CONTEXTSWITCHER.md` naming a `remote` (worktree bootstrap optional).

- [ ] `Add task…` → type a task description **with quotes** (e.g. `rename "foo" to 'bar' everywhere`) → `Add remote Claude` → Claude receives the prompt with the description verbatim between `-----` lines.
- [ ] With `claudeAuto: true` in `settings.yaml`: the session starts with `--dangerously-skip-permissions` and the worktree bootstrap runs with **no** permission prompts (very first such session per remote: accept Claude's one-time bypass dialog by hand). A resurrect (kill window, play) resumes with the flag too. With `claudeAuto: false` (default): plain `claude`, prompts as before.
- [ ] Claude runs `tmux set -w -t "$TMUX_PANE" @cs_title …` → within ~15 s the task row and the file's `title:` show the short title; the original description still sits under `# Notes`.
- [ ] **Focus another window** (switch to a different task) immediately after creating the task, before the title is derived → the title still lands on the *creating* task, not the focused one (regression guard: bare `set -w` targets the active window).
- [ ] The new task's file starts as `<ISO date>-<slugged description>.md`; with the title adopted it is renamed to `<ISO date>-<title-slug>.md` (check the tasks folder) — the row stays selected, an open editor lane follows, and the remote tmux window shows the short name.
- [ ] A message queued before the title arrived is still in the queue pane after the rename (its `queues/` file followed the id).
- [ ] `tmux show -w -t <window> @cs_title` on the remote → option is unset again (one-shot mailbox).
- [ ] Rename the task manually afterwards → the manual title stays (no revert on later polls).

## Markdown copy (dsn~terminal-markdown-copy~1)

- [ ] In a mirrored Claude chat, select a paragraph with bold or `code` in it → after a moment the status bar says "Selection copied as Markdown", and pasting shows `**`/backticks and no terminal line breaks.
- [ ] Select a list or heading line → the paste starts with `- ` / `## `.
- [ ] Double-click a single word → it is pasted as is.
- [ ] Press *Copy reply* → the paste is the whole last reply as Markdown.
- [ ] Turn on *Copy each finished Claude reply as Markdown*, send a message, wait for the reply → it is on the clipboard; a reply in a task not shown in the terminal is not.

## Terminal focus on task click (dsn~terminal-pane~14)

- [ ] Click a task row whose mirror is already live (same session, fast path) → typing immediately lands in the task's tmux window, no extra click into the terminal needed.
- [ ] Click a task on another remote/session → after the attach completes, typing lands in the terminal (existing on-attach focus).
- [ ] Click a suspended / tmux-less task → placeholder shows, focus not stolen anywhere odd.
- [ ] Arrow keys through the task list → the list keeps focus (selection moves, no focus jump to the terminal).

## Terminal cursor visible on dark theme (dsn~terminal-pane~14)

- [x] Select a task mirroring a Claude session, click into the terminal → the
      block cursor in Claude's input box is **light** (like the text), not a
      black block swallowed by the dark background. Type a few characters —
      the light block follows. Regression: JediTermFX 1.1.0 seeded the cursor
      fill from its black-on-white default style, painting the cursor black.
      *Verified live by Oliver, 2026-07-19; the unfocused hollow-box cursor is
      deliberately kept — he finds it helpful.*

## Everforest UI prototype (dsn~everforest-theme~2)

Verdict checklist — this is here to decide whether Everforest stays, not only
whether it works.

- [ ] Settings → theme **everforest-dark**, Save → the whole window recolours
      at once, no restart: cream text on `#2d353b`, green accent on the
      selected task row and the default button.
- [ ] Open **every dialog** — Settings, Add task, a delete confirmation, the
      diff window — each is Everforest too, none is left in Nord blue. (This
      is the one thing a per-scene stylesheet would have got wrong.)
- [ ] **everforest-light** → cream `#fdf6e3` ground, dark green buttons, and
      white-on-green button text is comfortably readable, not washed out.
- [ ] Switch **everforest-light → light → everforest-light** a few times →
      each takes effect on Save, nothing keeps a stale colour, the terminal
      follows along.
- [ ] **everforest** (bare) → follows the Windows light/dark setting; flip
      Windows to dark with the app open, reopen Settings and Save → follows.
- [ ] Status chips (ok green / failed red), the ⚠ on a category header, and
      the tag colours are still distinguishable from the green accent at a
      glance — the accent is green here, so this is where it could confuse.
- [ ] Verdict: does the UI still look right next to the Everforest terminal,
      day to day? If yes → compiled theme in its own repo. If no → say which
      part, or drop the entries again (nothing else depends on them).

## Terminal follows the theme (dsn~terminal-theme~2)

- [ ] Settings → theme **everforest-light**, Save → the open mirror re-attaches
      within a second and comes back on the Everforest light ground (`#fdf6e3`),
      with no dark band left around the canvas, behind the placeholder, or
      behind the "Switching…" cover. **everforest-dark** → `#2d353b`, same check.
- [ ] Theme **light** → plain white ground, **dark** → plain black ground, no
      Everforest cream or green-grey anywhere in the pane.
- [ ] The re-attach loses nothing: the window shown afterwards is the same one,
      with its scrollback still reachable by the wheel (it lives in tmux).
- [ ] On light, a shell's `ls`/`git status` colors and a tmux status line using
      *named* colors are legible — nothing washed out into the ground.
- [ ] On light, a **Claude Code** window drawn in its own *dark* theme still
      shows dark blocks (expected — 24-bit color the app cannot repaint).
      Set Claude's theme to a light one on the remote → the pane matches;
      with its ANSI-only light variant it picks up the palette above exactly.
- [ ] Select a suspended task on light → the snapshot renders on the light
      ground too (it re-renders on selection, not on the theme switch itself).

## Terminal copy and wheel (dsn~terminal-mouse-scroll~9)

- [ ] **Plain drag** (no modifier) over terminal text → selection highlight persists on release, and the text is in the Windows clipboard (paste into Notepad to verify) — no extra keystroke needed.
- [ ] **Double-click** a word → the word is highlighted and in the clipboard; **triple-click** → the whole (wrapped) line is.
- [ ] `Ctrl+Shift+C` on a still-highlighted selection also copies it.
- [ ] `Shift`+drag behaves the same as a plain drag.
- [ ] In a **Claude** session and in a **mirror**, the terminal shows **no scrollbar** (a thin empty strip stays at the right edge); the wheel still scrolls.
- [ ] Exit Claude in an owned session so the plain shell prompt remains, and print enough lines to scroll (e.g. `dir /s C:\Windows\System32 | more` quit early, or any long listing) → the **scrollbar appears**, and clicking or dragging it scrolls without selecting text.
- [ ] Mouse-wheel scrollback still works (buttons are local now, the wheel is not).
- [ ] Wheel **up** over the terminal scrolls the tmux scrollback towards **older** output, wheel down towards newest — not inverted (Windows mouse).
- [ ] In a **Claude Code** window (mouse reporting on), the wheel scrolls the tmux scrollback, *not* Claude's own message history — anywhere in the pane, input box included; wheel down at the newest line leaves copy-mode without paging Claude.
- [ ] **Jump to bottom** (`dsn~terminal-jump-to-bottom~1`): scroll up with the wheel (mirror enters copy-mode), click the button below the terminal — the view returns to the live output and the keyboard is back in the terminal. Clicking it while already at the bottom, and while a placeholder shows instead of a terminal, does nothing visible.
- [ ] **Accept suggestion** (`dsn~terminal-accept-suggestion~1`): wait for Claude Code to show a greyed-out suggestion in its input box, click the button — the suggestion is sent as the next prompt, and typing continues in the terminal.
- [ ] **Clear input** (`dsn~terminal-clear-input~3`): type a half-finished line in a shell and in Claude Code's input box, click the button — the line is gone in both, and typing continues in the terminal. Nothing happens while a placeholder shows instead of a terminal.
- [ ] **Clear input after scrolling**: type a draft in Claude Code's input box, scroll up with the wheel (mirror enters copy-mode), click **Clear input** — the view returns to the live output and the draft is gone (in copy-mode a `^U` used to scroll instead of clearing).
- [ ] In a plain **PuTTY/terminal attach to the same tmux server** (not the mirror), the wheel still behaves as before: over a mouse-reporting application it reaches the application, elsewhere it enters copy-mode.
- [ ] **App-owned local session** (Windows, a task the app started itself): the wheel scrolls Claude Code's view — up towards older output, down towards the newest — and the cursor in Claude's input box does **not** move, nor does the input jump to a previous message.

## Terminal zoom (dsn~terminal-zoom~4)

- [ ] `Ctrl`+wheel over the terminal → the font grows and shrinks by a point per notch, the pane reflows, and the size stops at a readable minimum and a usable maximum.
- [ ] `Ctrl`+`0` → back to the starting size.
- [ ] **Claude session, zoom out to the smallest font, then zoom in several notches** → after every notch the terminal fits the pane: no text cut off at the right edge, Claude Code's input box and footer visible at the bottom, without resizing the window.
- [ ] In a **mirror**, the zoomed pane's tmux window reflows to the new geometry (the remote status line and Claude's box follow the new column count).
- [ ] Zoom, switch to another task with a live terminal, come back → both terminals show the new size, and it survives an app restart.

## Show diff (dsn~terminal-diff-window~2)

Precondition: a mirrored task whose `claude:` workspace/cwd is a git worktree.

- [ ] With **uncommitted tracked changes**: click **Show diff** right of "Jump
      to bottom" → a `diff` window opens in the mirror showing `git diff HEAD`
      through the remote's pager; scrolling works; `q` (then Enter at the
      `Enter closes this window...` prompt — rendered verbatim, no stray `n` or
      missing quotes) closes it and the mirror returns to the previously
      selected window. Task-list selection unchanged.
- [ ] With a **clean worktree** (Claude committed): the same button shows the
      **last commit** (`git show`, message + diff).
- [ ] With [delta](https://github.com/dandavison/delta) as `core.pager` in the
      remote's `~/.gitconfig`: the diff arrives syntax-highlighted (n/N jump
      between files with `navigate = true`); without delta, stock `less` pages.
- [ ] A task whose window has no `claude:` section → info alert "No workspace
      recorded…", no window opened. While a placeholder shows instead of a
      terminal, the button does nothing.
- [ ] A **short** diff (fits one screen) stays visible until Enter — the
      window must not flash away unread.
- [ ] A task whose recorded cwd is **not a git worktree** (e.g. the workspaces
      root, no published `@cs_workspace`) → one line `Not a git repository:
      <path>` and the Enter prompt — not git's multi-screen usage dump.

## Deep links (dsn~deep-link-url~1, dsn~deep-link-forward~1, MADR 0016)

Precondition: protocol registered once via `scripts/register-url-handler.cmd` (Windows) or `.sh` (Linux), pointing at an unzipped app image.

- [ ] App **running**: open `contextswitcher://task/<group>/<id>` in Firefox → the running app comes to the front with that task selected; no second instance or window appears.
- [ ] `contextswitcher://switch/<id>` → same, plus the full switch runs (status-bar chips).
- [ ] App **not running**: the same link starts the app, which then selects the task.
- [ ] Category context menu → **Copy link** → the clipboard holds `contextswitcher://category/<name>`; opening it selects that category's header (expanding it when collapsed) and raises the app.
- [ ] A link to a nonexistent id → app raises and the status bar reports the unknown task; a malformed link (`contextswitcher://focus/x`) reports too.

## Delete-task kill dialog (dsn~claude-session-kill~6)

Precondition: a live remote-Claude task with `claude.sessionId` and a published `@cs_workspace`.

- [ ] Delete a live task with 2 PRs and 1 other link, **all three tabs open** → dialog shows four checkboxes ("End the tmux window", "Close 3 browser tabs", transcript, workdir); the workdir box shows the task's workspace path and starts unticked; window/transcript ticks match the previous run.
- [ ] Same task with only **one** of its three tabs open → the box reads "Close 1 browser tab"; it counts what is open, not what the task lists.
- [ ] Close every tab of the task and delete it → "Close browser tabs — none open", disabled and unticked.
- [ ] Delete a task with `browser:` URLs but none of them a PR → the label still says "browser tab(s)" (no "PR" wording).
- [ ] Have one of the task's PRs open on its `/files` sub-path only → that tab is **not** counted (the close would not touch it either).
- [ ] Disconnect the browser extension and delete a task with URLs → "Close browser tabs — no browser extension connected", disabled — not "none open".
- [ ] Watch the box as the dialog opens → it starts disabled reading "checking…" and settles to the count; pressing Delete before it settles closes nothing (and that is what the disabled tick says).
- [ ] Delete a task with no `browser:` section at all → no browser checkbox shown.
- [ ] Delete a task whose `browser:` section is present but empty (hand-edited file) → checkbox shown, disabled, unticked, "none open"; no browser round-trip is made.
- [ ] Tick all four, Delete → window gone (chip), browser tabs closed (chip), transcript `.jsonl` gone on the remote, worktree directory gone; status bar shows transcript/workdir results after the chips.
- [ ] Tick only the browser box (leave "End the tmux window" off), Delete → tabs close, window keeps running.
- [ ] Delete a task while its tabs are closed (box forced off), then delete another whose tabs *are* open → the second box is ticked again: the forced "no" was not remembered.
- [ ] Delete with everything unticked → only the task file disappears; window keeps running, tabs stay open (old "Delete only").
- [ ] Delete a **suspended** task that still has `sessionId`/workspace → no window checkbox, transcript/workdir removal still works.
- [ ] While Claude is `working` → dialog carries the mid-task warning line.
- [ ] "Ask Claude to tidy up first" → prompt arrives in the window's Claude input and is submitted; task file still there; status line confirms; Claude actually merges + removes its worktree, then Delete finishes the rest.
- [ ] Task without claude section → plain Delete/Cancel dialog, no checkboxes.
- [ ] Let a session remove its own worktree and step back into the category's primary clone (`<workspacesRoot>/<repo>`, published as `@cs_workspace`), then delete the task → the workdir box reads "Remove the working directory — not available: refusing to remove the category's own directory: …", unticked and disabled — also when the category's `CONTEXTSWITCHER.md` spells no `mainCheckout:`.

## Category selection clears the panes (dsn~group-config-create~9)

- [ ] With a live task selected (mirror running, queue populated), click another group's header → editor shows the category config, terminal shows the "Select a task…" placeholder, queue lane is empty, the header row is selected.
- [ ] `Add category…` while a task is selected → same: the new config opens, terminal and queue cleared (header row selected once the list picks the folder up).
- [ ] Click a task again → mirror attaches and queue returns as usual.

## Selection survives list rebuilds (dsn~task-folder-grouping-ui~4)

- [ ] Select a task, then cause a background change to **another** task's file (e.g. let a live Claude session publish `@cs_title`, or edit another task file externally) → the selected row stays highlighted.
- [ ] Collapse/expand an unrelated group while a task is selected → highlight stays.
- [ ] Let the **selected** live task adopt a published title (file rename) → the row stays selected under its new name; the editor lane follows to the renamed file.

## Session-id backfill during sync (dsn~tmux-sync~7)

- [ ] Pick a task whose file has a `claude:` section with `cwd:` but no `sessionId:` and whose tmux window runs a Claude session that published `@cs_session_id` (check with `tmux show -w -t <window> @cs_session_id`; live-created tasks from before the hook ran qualify).
- [ ] Click **Sync tmux windows…** → the summary alert reports `session ids backfilled 1` and the task file gained `sessionId: <the published id>` under `claude:` (comments in the section untouched).
- [ ] Sync again → no further backfill (count absent from the summary), the id unchanged.
- [ ] Suspend the task, then Switch → the resurrect resumes the Claude session (`claude --resume <id>`).
- [ ] **Backfill updates the open editor**: select that task so its file shows in the editor lane, then click Sync → the editor lane's frontmatter shows the new `sessionId:` immediately (no manual reselect); type nothing and click away → the id is not overwritten.
- [ ] **Auto-sync after live create**: create a live task with a workspace, then leave it untouched → within ~30 s (no button press) the task file gains `claude.sessionId`, and if the task is still selected the editor lane shows it. No summary alert pops up for this automatic sync (log line `Tmux sync (auto): …` only).

## Workspace refresh during sync (dsn~claude-workspace-capture~2)

- [ ] Create a live task in a group with a `workspacesRoot` and let Claude bootstrap its worktree and publish it (`tmux set -w -t "$TMUX_PANE" @cs_workspace "$PWD"`) → within ~60 s (no button press) the task file's `claude:` section gains `workspace: <the worktree>` beside the unchanged `cwd:` (the workspaces root), and the row's `wd …` shows the worktree.
- [ ] Then click **Show diff** in the terminal → the diff opens in the **worktree**, not in the workspaces root (no `Not a git repository: …/…-workspaces` line). Same for **Open in IntelliJ**.
- [ ] In that session `cd` to a second worktree and publish it again → the next sync replaces the `workspace:` line (summary alert reports `workspaces refreshed 1`); syncing once more reports no refresh and leaves the file untouched.

## Show diff after the worktree was removed (dsn~diff-after-worktree-removal~1)

Needs a category with `mainCheckout:` pointing at a permanent checkout of its repo, and a live Claude task whose worktree is a separate directory.

- [ ] In the session, commit something and publish it (`tmux set -w -t "$TMUX_PANE" @cs_commit "$(git rev-parse HEAD)"`) → within ~60 s (no button press) the task file's `claude:` section gains `commit: <sha>`; the sync summary reports `commits recorded 1`. Syncing again reports none and leaves the file untouched.
- [ ] **Worktree still there** → **Show diff** behaves exactly as before: uncommitted changes if any, else the worktree's last commit. No "Worktree … is gone" line.
- [ ] Now `git worktree remove` it (the workspace path no longer exists) and press **Show diff** → a window opens (it must not fail silently), headed `Worktree <path> is gone - showing commit <sha>`, showing **that task's** commit — not merely whatever is newest on main.
- [ ] **Only half configured**: remove `mainCheckout:` from the category (or test a task with no `commit:`) with the worktree deleted → the old behavior returns (the window fails to open), i.e. the fallback never half-engages.

## A hand-started Claude session is adopted (dsn~teardown-claude-resync~1)

Needs an imported plain-shell task — a tmux window that carried no Claude when **Sync tmux windows…** created its task, so the file has no `claude:` section.

- [ ] Start `claude` **by hand** in that window and let it boot (`tmux show -w -t <window> @cs_session_id` prints an id) → within ~60 s (no button press) the task file grows a `claude:` section with the window's `cwd:` and that `sessionId:`; the log reports `session ids backfilled 1`. The `workspace:`/`commit:` lines follow on a later pass once the session publishes them.
- [ ] Now the teardown case: repeat with a fresh plain-shell task, start `claude` in its window, and press **Pause** within a few seconds — before any periodic reconcile could run → the status bar briefly names the checked window, the task file is adopted right there, and the dialog does **not** claim "No claude: section"; it either stays silent or warns only about Claude being `working`.
- [ ] Suspend it, then Switch → the resurrect resumes that very session (`claude --resume <id>`), i.e. the adopted id was the right one.
- [ ] **Nothing to adopt**: press Pause on a plain-shell task whose window runs no Claude → after the check the old "No claude: section" dialog appears unchanged.
- [ ] **Host unreachable**: pull the network (or point the task at a dead host) and press Pause → the status bar reports it cannot check the window, and the dialog still appears (the suspend is delayed, never swallowed).
- [ ] **No round-trip in the normal case**: press Pause on a task whose `claude:` section already carries a `sessionId` → the dialog decision is instant, no "Checking …" message.

## Dead window: auto-suspend / auto-delete (dsn~tmux-sync~7)

- [ ] **Disposable shell auto-deleted**: in a category with `autoDelete: true`, import a plain `bash` window (no Claude, empty Notes), then close it on the remote (`Ctrl+D` / `tmux kill-window`) → within ~60 s (no button press) the task row disappears on its own; its file and queue file are gone from `~/.contextswitcher/tasks/`. Log line `Tmux sync (auto): … deleted 1`.
- [ ] **Task with content is suspended, not deleted**: same, but the task carries a `claude:` section (or user notes) → the window closing flips it to **suspended** (kept, resurrectable), not deleted; Switch resumes it.
- [ ] **Manual Sync still imports; auto does not**: open a new plain window on the remote → it does NOT auto-appear as a task on the periodic reconcile; it appears only after **Sync tmux windows…**.
- [ ] **No spurious alert**: with no remotes configured, the periodic reconcile runs silently (no "No remotes to sync" popup every minute); only the manual button shows it.

## Tag pickers offer in-use tags too (dsn~tag-selection-union~2)

- [ ] Hand-write a tag that is **not** in the settings.yaml palette into a task's `tags:` → it appears (auto-colored, in alphabetical position) in the toolbar **Tags** filter menu and in the row/group **Tags** submenus; toggling it onto another task works.
- [ ] Filter by that tag, then remove it from the only task carrying it (edit the file) → the filter selection is dropped and the full list returns on its own.
- [ ] Empty palette but a tagged task → the filter button is enabled and offers the task's tags; no tags anywhere → disabled with the hint.
- [ ] A task tag spelled differently from its palette entry (e.g. `JabRef` configured, `jabref` in the file) → one menu entry only, the configured spelling.

## Sort modes and label grouping (dsn~task-sort-modes~2, dsn~task-label-grouping~3)

- [ ] Toolbar **Sort: Alphabetical** menu → pick **Last update** → within each group the most recently changed task file sorts first; editing any task file moves it to the top of its group.
- [ ] Pick **Action needed** → tasks whose Claude asks/awaits (`attention`, then `waiting`) sort above `working` and idle ones; when a shown task's status changes (e.g. Claude finishes its turn), the row reorders within a few seconds without any click.
- [ ] Suspended tasks stay below active ones and error rows stay on top in every mode.
- [ ] Restart the app → the chosen sort mode is still active (button label shows it).
- [ ] Group menu → **Labels** → the list groups under colored tag chips (task count per label); a task with two tags appears under both; untagged tasks stay in the top ungrouped block; the Done section is unchanged.
- [ ] With **Labels** grouping on, a grouped task's second line leads with its muted category name (`jabref ·`) before the chips; a root-level task shows none; back to **Folder** → the category vanishes from the rows again.
- [ ] Click a label header → it collapses/expands; no context menu, and dropping a task row onto it does nothing.
- [ ] Back to **Folder** → folder grouping returns, including empty folders with their "Add task…" row.
- [ ] Group menu → **PR status** (`dsn~pr-status-grouping~1`) → JabRef tasks group under `status: …` labels, tasks of repos without such labels under draft / changes requested / ready; merge queue, merged, closed are their own groups, in workflow order; tasks without a PR stay on top.
- [ ] Upgrade from a build with the old toggle ticked → the group menu starts on **Labels**.
- [ ] ⋮ menu → open tasks directory, sync tmux windows, restart running tasks, refresh now each still work; sync and restart grey out while running.
- [ ] Click the terminal icon on a remote category's header → a new empty tmux window named `scratch` opens on that host (in the category's workspace root) and shows up in the app's terminal with the keyboard, ready to type in; no new task row appears. Afterwards "Sync tmux windows…" offers the window for import.
- [ ] Same click, watched live: the icon grays out immediately and the status bar shows "Opening terminal on `<host>` …"; once the window appears the icon re-enables and the status bar switches to "Terminal opened on `<host>`."

### Model and effort pickers (`dsn~claude-mode-select~3`)

- [ ] `Add task…`: pick a model/effort, `Add remote Claude` → the fresh session runs `/model`, then `/effort`, before the context prompt. Same with a PR URL in the field.
- [ ] Leave both on "as is" → no slash command reaches the fresh session, just the prompt.
- [ ] The queue pane has no pickers at all: a card's ➤ sends the message and nothing else.
- [x] Re-open `Add task…` → both pickers come up pre-selected with that last pick (survives an app restart).
      *(2026-07-20, automated: `ClaudeModeMemoryUiTest` — the TestFX extension's per-test relaunch is the restart; prefs redirected into the build dir.)*

### Reported model and effort (`dsn~claude-mode-report~2`)

- [ ] With the README's `cs-report-mode.sh` status line installed on the remote, select a live task → within a poll tick the terminal pane shows `<model> · <effort>` left of `Jump to bottom` for the mirrored session.
- [ ] Run `/model sonnet` **inside** the chat → the label follows within a few seconds (statusLine `refreshInterval`).
- [ ] Switch to another task → the label blanks at once and refills (or stays blank, for a session that publishes nothing) on the next tick.
- [ ] `Add plain task` with a pick set → no chat, no commands, task file created as before.

## Suspend snapshot (dsn~terminal-suspend-snapshot~3)

- [ ] Suspend a task whose terminal shows Claude output → select it again: the pane shows the placeholder plus the window's **last screen**, not just the placeholder text.
- [ ] The snapshot text can be selected and copied; a file appeared under `~/.contextswitcher/snapshots/`.
- [ ] Complete a task (right-click → Done) → same snapshot behaviour.
- [ ] Resume the task → the live mirror replaces the snapshot; suspending again overwrites it with the newer screen.
- [ ] With the snapshot showing, resize the application window (wider and narrower, taller and shorter) → the screen keeps its captured layout, lines never re-wrap or merge; a window too small for it scrolls (scrollbars, or drag-pan) instead.
- [ ] A task suspended by the sync because its window vanished (nothing left to capture) still shows the plain placeholder, and the suspend itself does not fail.

## Per-desktop window position (dsn~window-position-per-desktop~2)

- [ ] With `showOnAllDesktops: true`, place the window differently on two named desktops (switch, drag, switch, drag) → switching between them moves the window to each desktop's place within ~2 s.
- [ ] Restart the app → on the desktop it opens on, the window comes up at that desktop's remembered position and size.
- [ ] Move the window, close the app **without** switching desktops, restart → the moved position is restored (the exit save).
- [ ] `~/.contextswitcher/window-positions.properties` holds one `name=x,y,w,h` line per desktop that was placed, plus the `!startup` last-exit entry.
- [ ] Edit an entry to coordinates on no screen (e.g. `x=20000`) → returning to that desktop leaves the window where it is and the entry is removed (monitor-change reset — the git gui failure mode).
- [ ] With `showOnAllDesktops: false`, switching desktops moves nothing and no per-desktop entries appear.
- [ ] With `showOnAllDesktops: false`, move/resize the window, restart → it opens at the moved position and size (the startup restore works without the pin; also verify once on Linux).

## Refactoring view and badge ([#50](https://github.com/contextswitcher/contextswitcher-private/issues/50) | `dsn~refactoring-web-view~1` / `dsn~refactoring-analysis-poller~1` | MADR 0022)

Precondition: RefactoringMiner unzipped on the remote (`/data/koppor/RefactoringMiner-3.1.4`), `refactoringMinerHome` set in settings, a live remote-Claude task with a published `@cs_workspace` whose branch has at least one commit with a refactoring (an extract/rename/move).

- [ ] Settings → **Set up** next to *RefactoringMiner directory* on a machine without an install → the button grays out, the status bar reports the install, and the field ends up at `~/.contextswitcher/RefactoringMiner-<version>` on the remote; **Save**, then the view below works. A second click is a fast no-op.
- [ ] Hover the task row → a compare icon shows next to the other hover actions; click it → the button grays out, the status bar reports "Opening refactoring view …", and within ~30 s the browser opens `http://127.0.0.1:6789/list` showing the Monaco AST diff (worktree vs `origin/main`).
- [ ] An **uncommitted** edit in the worktree is visible in the view (directory mode).
- [ ] Open the view for a *second* task → the first view's browser tab stops answering (single view; the old JVM was killed), the new task's diff shows.
- [ ] Close the app while a view is open → on the remote, no `RefactoringMiner` java process is left and port 6789 is free (`ss -ltn | grep 6789`), no `/tmp/cs-rm-*`/`tmp.*` base directory remains.
- [ ] After Claude finishes a task (row dot turns green/waiting), the row shows a circled refactoring count within ~2 poll minutes when its commits contain refactorings; clicking the count opens the same view.
- [ ] A task whose commits contain no refactorings shows no badge (not a `⓪`).
- [ ] With `refactoringMinerHome` empty, the hover button answers with the status-bar hint naming the setting, and no badge ever appears.
- [ ] Category with `baseBranch: <other>` in its `CONTEXTSWITCHER.md` → the view diffs against that branch.

## Bash cursor keys in text fields (`dsn~readline-keys~1`)

Only the parts a robot cannot reach — the chords in a plain text box are covered by `ReadlineKeysUiTest`.

- [ ] With `readlineKeys` off (the default), `Ctrl+A` in a message box still selects all — nothing changed for anyone who did not ask for this.
- [ ] Tick "Bash cursor keys in text fields" in the settings dialog and Save → the chords work **immediately**, without a restart.
- [ ] In the **notes editor** (the rich text pane): `Ctrl+A`/`Ctrl+E` go to the line's start/end, `Alt+B`/`Alt+F` walk words, `Ctrl+W` and `Alt+D` delete a word, `Ctrl+U` clears back to the line start. (`Ctrl+K` has no effect there — the editor has no kill-line function; `Ctrl+Z` still undoes.)
- [ ] In a **dialog** field (Add task…, the settings dialog's own text fields): the chords work there too.
- [ ] In a **text input**, `Ctrl+F` moves the caret one character on instead of opening the find bar; with the focus on the task list or the terminal, `Ctrl+F` still opens it.
- [ ] In the **embedded terminal**, `Ctrl+A`, `Ctrl+E`, `Ctrl+W` reach the remote shell/tmux unchanged (tmux prefix `Ctrl+A` still works if that is your binding).

## Category action defaults, folder list, machine suffixes (`dsn~category-action-defaults~1`, `dsn~explorer-folder-focus~3`, `dsn~machine-key-variants~1`)

The parsing and merging are unit-tested; what needs a real box is Explorer, Firefox, and a second machine.

- [ ] Put `intellij:`, `browser: urls:`, and `folders:` into a category's `CONTEXTSWITCHER.md`, then switch a task of that category that configures **none** of them → the chips `intellij`, `browser`, `folder` appear and open the category's project, page(s), and directories.
- [ ] Give that task its own `browser.urls` and switch again → only the task's URL opens; the category's `intellij` and `folders` still run (the fallback is per section).
- [ ] Suspend the task → only its own tabs close; the category's pages stay open.
- [ ] Edit the category's `folders:` while the app runs and switch again → the change takes effect without a restart.
- [ ] A task with two `folders:` → both Explorer windows are focused/opened; make one path bogus → the chip fails naming it, the other still opens.
- [ ] Add `folders-windows:` and `folders-<the Linux host>:` next to a plain `folders:` in the same file, sync the tasks dir to the other machine → each machine opens its own list, neither sees the other's.
- [ ] (`dsn~category-folders-button~1`) The category's header shows a folder icon right of the desktop button; its tooltip names the folder (or the count and the list). Clicking it opens exactly those Explorer windows, switches to no task, and reports `Folders: …` in the status bar.
      A category without `folders:` shows no such icon.
- [ ] Edit the category's `folders:` in the editor lane **without saving**, then click the folder icon → the just-edited list opens (the click flushes the editor first), and the button comes back from grey.

## Complete-control desktop (`req~complete-control-desktop~1`)

The capture/store/reopen logic and the scripts' COM shape are unit-tested; what needs a real box is the desktop-to-window matching, the pin exemption, and the reopen landing on the right desktop.

- [ ] Give a category `desktop: {name: <a real desktop>, completeControl: true}`, open two Firefox windows with a few tabs on that desktop plus one window on another desktop, then suspend a task of the category → the browser chip reports `stored N tab(s), closed M window(s)`, both windows on the desktop are gone, the other desktop's window is untouched, and the task file carries the URLs under `storedTabs:`.
- [ ] Switch manually to the desktop → it is clean.
- [ ] Resume the task from another desktop → Windows switches to the category's desktop, a Firefox window opens (or is raised) there, the stored tabs come back, and `storedTabs:` is gone from the file.
- [ ] Leave one stored tab open elsewhere before resuming → that tab is focused where it is, not closed and reopened, and no duplicate appears.
- [ ] Pin a Firefox window to all desktops (Task View → "Show this window on all desktops"), suspend again → the pinned window survives with all its tabs.
- [ ] Suspend with Firefox closed (extension disconnected) → the chip fails with the extension detail, nothing is stored; the tmux teardown still runs.
- [ ] On the category with the plain scalar `desktop:` form, suspend → behavior unchanged (only the task's own `browser.urls` close).

## Download a generated file (`dsn~generated-file-download~2`)

The remote half is verified (the `find` listing and the base64 round-trip were run against devbox while building this); what needs a real desktop is the menu, the download folder, and the OS handing the file to an application — on Windows in particular, where `~/Downloads` is `%USERPROFILE%\Downloads`.

- [ ] Mirror a task whose Claude session wrote a file into its scratchpad, click **Files** → the button greys for the round-trip and a menu opens above it: **Custom path…**, a separator, then the files newest first, each entry reading `name — size, age ago`.
- [ ] Pick a text file → it lands in the Downloads folder and opens in the registered editor; the local name is the remote one.
- [ ] Pick the same file again → the second copy is `name-2.<ext>`, the first is untouched.
- [ ] Pick a generated `.png` → it opens in the image viewer, not as text (byte-exact transfer).
- [ ] Click **Files** while a placeholder shows (suspended task) → nothing happens, no error.
- [ ] Click **Files** against a host whose sessions wrote nothing → the menu still opens with **Custom path…** and one greyed "no files" line, not an alert.
- [ ] Generate a file whose name carries a space or an umlaut → it is offered, downloads, and the local name has the odd characters replaced by `_`.
- [ ] **Custom path…** with an absolute path outside any scratchpad (e.g. a file in the task's workspace) → it downloads and opens like a listed one.
- [ ] **Custom path…** with `~/…` and with a path relative to the remote home → both resolve to the same file.
- [ ] **Custom path…** with a typo → `No readable file … on <host>.` and nothing is transferred; the same for a **directory** path.
- [ ] **Custom path…** with a path containing a `'` → refused with the quoting reason; cancelling the dialog or leaving it empty does nothing at all.

## Delete-dialog content is fully visible (`dsn~task-delete~6`)

Reported on Windows: the delete dialog cut off "This cannot be undone." and the last checkbox's wrapped path.
The pane is now sized to its content (`Alerts.withContent`) and the dialog is resizable — but the mis-sizing does not reproduce on Linux/GTK (the pane comes out taller than its content there), so only a Windows run can confirm the fix.

- [ ] Delete a task whose Claude session is live, has a transcript and a per-task working directory, so the dialog shows all three checkboxes → every line of the message and all three checkbox labels are readable, none clipped, no scrollbar.
- [ ] The same with a long working-directory path → the label wraps onto a second line and the dialog grows to show it.
- [ ] Delete a category with tasks in it → both lines of its confirmation are readable.

## Browser tab selects task (`dsn~browser-tab-selects-task~5`)

Needs a real Firefox with the extension loaded — the reporting side lives in the browser.

- [ ] Click the tab of task A's PR, then the tab of task B's PR → the app's selection follows each click, without the window being raised.
- [ ] Open the PR's **Files changed** sub-page in one of those tabs → the same task stays/gets selected.
- [ ] Activate a tab belonging to no task (a search result) → the selection stays where it was.
- [ ] Switch between two Firefox windows showing different tasks' tabs → the selection follows the window switch.
- [ ] Activate the tab of a **suspended** task's PR → the task is selected *and* resumed: its status goes back to `active`, its tmux window is recreated and its Claude session resumed, as if its play button had been pressed.
- [ ] Activate that same tab again once it is running → nothing is switched a second time, only the selection.
- [ ] Activate the tab of a **done** task → it is selected and stays done.
- [ ] With task A selected, move between the tabs of A's own tab group → the task list does not move at all: no scroll, no repaint, and a category collapsed over A's row stays collapsed.

## Task tabs in a tab group (`dsn~browser-tab-group~2`)

Needs Firefox 139+ with the updated extension (`tabGroups` permission) loaded.

- [ ] Switch to a task with two `browser.urls` → both tabs sit in one tab group named after the task's tmux window number (`@339` → `339`).
- [ ] Switch to a task without a `tmux:` section → its tabs are grouped under `l:<task id>`.
- [ ] Switch to the task again → the tabs stay in the same group; no second group of the same name appears.
- [ ] Switch to a second task → its tabs land in that task's own group, the first task's group is left intact.
- [ ] The tabs in the group stand in the order of the task's `browser.urls`, and the first URL is the active tab — the others opened behind it, without the browser jumping to each in turn.
- [ ] Re-order two URLs in the task file and switch again → the already-open tabs move into the new order.

## No second tab for an open page (`dsn~browser-no-duplicate-tab~1`)

- [ ] With a task's PR open in its tab group, open the same PR URL in a new tab → the new tab closes, the grouped tab is focused.
- [ ] Middle-click a link to a page already open in another tab → the background tab closes, the current tab stays in view.
- [ ] Click a link inside a tab that leads to a page open elsewhere → the tab navigates normally and is kept.

## Toolbar icon counts the tasks a tab belongs to (`dsn~browser-tab-context-count~2`)

Needs the updated extension loaded and the app running.

- [ ] The extension shows a blue target icon in the toolbar (add it from the overflow menu if Firefox parked it there).
- [ ] Activate a tab whose address exactly one task lists → the icon carries the badge `1`, its tooltip says "in 1 task".
- [ ] List the same URL in a second task, open the popup → the badge becomes `2`.
- [ ] Activate a tab no task lists → no badge at all, tooltip "this page is in no task".
- [ ] Quit and restart the app; once the extension reconnects, the badge of the tab in view is right again without touching anything.

## The app's globe opens the selected task's tabs (`dsn~extension-connection-indicator~2`)

- [ ] With a task selected that has `browser.urls`, click the globe left of the settings gear → only the `browser` chip runs; no tmux window is focused, no IntelliJ opens.
- [ ] Click it with a task that has no `browser:` section → "nothing configured" with "Task has no browser configuration".
- [ ] Click it with a category header (or nothing) selected → the status bar asks to select a task.

## The toolbar icon says whether the extension reaches the app (`dsn~extension-state-icon~3`)

Do this in both browsers — Firefox from `about:debugging`, Chrome from `chrome://extensions`; the three states must look and read the same.

- [ ] Load the extension freshly with no token saved yet — the icon is red, hovering it says "no token configured", and the popup says a token has to be set in the options.
- [ ] Click **Options** in that popup — the popup closes and the options page opens.
- [ ] Open the options and save a token while the app is stopped — the icon turns orange, the tooltip and popup say it is not connected to the app.
- [ ] Start the app — the icon turns grey within the reconnect interval, without touching the options again; tooltip and popup say connected.
- [ ] Switch to a tab one task lists — the icon turns blue; switch to a page no task lists — it is grey again.
- [ ] Stop the app again — the icon goes back to orange on its own.
- [ ] Save a token that does not match the app's `wsToken` while the app is running — the icon stays orange (the app closes the rejected socket), never grey or blue.
- [ ] Clear the token in the options — the icon goes red.
- [ ] Through all of that, the per-tab task count badge keeps working: with the app running, a tab one task lists still carries `1`.
- [ ] Stop the app while such a tab is in view — the `1` disappears and the icon turns orange with the connection and the tooltip says "not connected to the app"; it does not sit there as a count nothing is refreshing.

## The extension can be switched off from its popup (`dsn~extension-enable-toggle~1`)

Do this in both browsers.

- [ ] With the app running, click the icon → the popup shows *✓ Enabled*; click it → the popup closes, the icon fades, the app's globe shows no extension connected.
- [ ] Open the popup again → *✗ Disabled*, the state line says disabled; activating a task's tab selects nothing in the app, and opening an already open page a second time keeps both tabs.
- [ ] Restart the browser → still disabled.
- [ ] Click *✗ Disabled* → the icon turns grey (or blue on a listed page) within a second or two and the app sees the extension again.

## The icon's popup names the tasks and acts on one (`dsn~browser-tab-task-popup~1`)

- [ ] Click the icon on a page two tasks list → the popup names both, the better match first, statuses shown for the ones that are not active.
- [ ] Click a task's title → the popup closes, ContextSwitcher comes to the front with that task selected; nothing else is switched.
- [ ] Click a task's `▶` → the full switch runs, as the row's play button in the app does.
- [ ] Click the icon on a page no task lists → "No task lists this address.", no rows.
- [ ] Quit the app and click the icon → "ContextSwitcher is not running.", not an empty task list.
- [ ] With the popup open, add the page's URL to a task and save → the list picks the new task up on the next open (the count comes from the app, not the popup).

## Android companion app (P4) (`dsn~android-task-repo-sync~1` / `dsn~android-task-list~1`)

Needs an Android device or emulator — no KVM on the dev box, so this never ran locally; a real device install is the first verification.
Install the debug APK (`:android:assembleDebug`), a task backup repo (MADR 0011) reachable over HTTPS, and a GitHub PAT with read access to it.

- [ ] First launch with no settings saved → the settings screen shows, not the task list.
- [ ] Enter the repo URL, any username, and the PAT, tap **Save + Sync now** → the task list screen shows and a sync starts.
- [ ] Once the sync finishes → the list mirrors the desktop's task directory: same groups, same task titles, same statuses and tags.
- [ ] Tap **Sync** again with no remote change → the button greys for the round-trip and the list stays the same.
- [ ] Push a task change to the backup repo from the desktop, tap **Sync** on the phone → the change appears after the sync completes.
- [ ] Corrupt one task file in the backup repo (e.g. remove the closing `---` fence), push, sync → that file shows as a red row naming the file and the parse error, the rest of the list is unaffected.
- [ ] Sync with the phone offline, or with a wrong token → a Snackbar reports the failure and the previously synced list stays on screen, unchanged.
- [ ] Tap **Settings** from the task list, change the URL, save → the app returns to the task list and re-syncs against the new repo.

## Android tablet layout (`dsn~android-large-screen~1`)

Needs the debug APK on a tablet (e.g. Lenovo Idea Tab) with synced tasks.

- [ ] Landscape → the task list on the left, *Select a task* on the right.
- [ ] Tap a task → its detail opens on the right, the list stays; Terminal and Queue show side by side, no tabs.
- [ ] Tap another task → the right side switches to it; the Queue message box is empty.
- [ ] Rotate to portrait → the phone layout: the open task full screen with Terminal/Queue tabs; *Back* returns to the list.

## Android Auto head unit (`dsn~android-auto-tasks~1` / `dsn~android-auto-message~1`)

Needs the debug APK on a phone with synced tasks, and a car or the Desktop Head Unit; in Android Auto's settings tap the version ten times and enable *Unknown sources* in the developer settings.

- [ ] ContextSwitcher is in the head unit's launcher; it lists the tasks with a Claude session, a waiting one above a working one.
- [ ] Pick a task, *Send “continue”* → toast *Sent*, and the text arrives in the session.
- [ ] *Dictate message* → the first time the phone asks for the microphone; then *Listening …*, the spoken sentence is shown, *Send* delivers it, *Retry* listens again.
- [ ] Without network, *Send “yes”* → toast *Not sent, kept on the phone*; the phone lists it under *Sent when Claude is idle*.
- [ ] After picking a task, the phone shows *Watching: <task>* (if not: Android refused the service start from the car — note it).

## Android update hint (`dsn~android-update-hint~3`)

Needs the debug APK on a phone, an *Update token* (or a *Token*) with read access to the ContextSwitcher repository, and a finished run of *Publish Android dev build* (`gh workflow run android-dev.yml`) for a newer commit.

- [ ] Open the app on a build older than `android-dev` → after the sync, **Update** is in the top bar.
- [ ] Tap it → it reads *Updating…*; the first time, Android asks to allow ContextSwitcher to install apps and to confirm; the app restarts on the new build and **Update** is gone.
- [ ] Push to main without starting the workflow → the `android-dev` release and tag do not change, and the app offers no update.
- [ ] Publish again and update on Android 12 or later → it installs without a confirmation.
- [ ] Leave *Update token* blank with a *Token* that reads both repositories → **Update** still appears.
- [ ] Use a token without access to the ContextSwitcher repository → a Snackbar says the token cannot read it, once per start; the task list works as before.

## Android finish notification (`dsn~android-finish-notification~1`)

Needs the debug APK on a phone and a task whose Claude session can be given a prompt.

- [ ] Open the task → Android asks for notification permission (first time only); a silent *Watching: <task>* notification appears with the last status and time.
- [ ] Send Claude a prompt that takes a minute, press Home → when Claude stops, a *Claude finished* notification with the task's title arrives.
- [ ] Tap it → the app opens on that task.
- [ ] Stay on the task's screen while Claude finishes → no alert.
- [ ] Give a prompt that makes Claude ask for a permission, leave the app → *Claude needs you*; answer it on the desktop → *Claude finished* when the turn ends.
- [ ] Run a silent command through Claude (e.g. `sleep 60`) → no *Claude finished* before it returns.
- [ ] Open another task → the *Watching* notification names that task; tap **Stop** → the notification is gone and no more alerts come.

## Restart Claude on the phone (`dsn~android-claude-restart~1`)

Needs a Claude session showing "Update installed · Restart to update" in its footer, started with `--dangerously-skip-permissions`, and the desktop app closed (it would restart the session itself).

- [ ] Open the task's Terminal page → **Restart Claude** is in the bar next to **Ctrl+C**; a session without the footer shows no such button.
- [ ] While Claude is answering, tap it → "Not restarted: Claude is working …", nothing typed into the session.
- [ ] Once the turn is over, tap it → *Restarting…*; the snapshot shows `/exit`, then Claude comes back on the same conversation, still without permission prompts; the Snackbar says it restarted; the button is gone.
- [ ] Leave the app while Claude works on a session with a pending update → the *Claude finished* notification ends with "update pending: Restart Claude".

## Android build commit (`dsn~android-running-commit~1`)

- [ ] Open the app → the bottom of the task list shows `<sha> (<date time>)` of the installed build, matching the commit `android-dev` was published from; the settings screen shows the same line.
- [ ] Long-press the line → a toast says `Copied <sha>`, and a paste gives the bare sha.

## Ctrl+C button (`dsn~terminal-interrupt~1` / `dsn~android-terminal-interrupt~1`)

Needs a task whose Claude is busy with a long answer or command.

- [ ] Desktop: click **Ctrl+C** in the terminal bar → Claude stops ("Interrupted"), the session stays open, the keyboard is back in the terminal.
- [ ] Desktop, scrolled up in the mirror first → the click still interrupts (copy-mode is left first).
- [ ] Desktop, Windows app-owned local session → **Ctrl+C** interrupts it too.
- [ ] Phone: on the Terminal page tap **Ctrl+C** → the button greys out briefly, the snapshot refreshes and shows the interruption.
- [ ] Tap **Ctrl+C** once on an idle Claude → Claude asks to press Ctrl+C again to exit and does not quit.

## Android drafts and terminal end (`dsn~android-message-drafts~3` / `dsn~android-terminal-snapshot~3`)

Needs the debug APK on a phone, a synced task with a reachable `remote` and `tmux` window.

- [ ] Open the task → the Terminal page shows the bottom of the Claude window, the prompt visible without scrolling.
- [ ] Scroll up and wait two refreshes → the view stays where you scrolled; scroll back to the bottom → new output is followed again.
- [ ] Scroll to the very top → older output appears above, the line you were reading stays in view; keep going → your last message to Claude comes into view; at the start of tmux's history **Earlier** greys out.
- [ ] Scroll back to the bottom → the page follows new output again (and loads only a screenful per refresh).
- [ ] On the Queue page, type a message and tap the empty space above the box → the keyboard closes, the box is empty, and the text is listed under **Drafts on this phone**.
- [ ] Type another message, swipe to Terminal and back → it is still in the box; tap **Back** and reopen the task → still in the box.
- [ ] Rotate the phone → the text is still in the box and not listed as a draft as well.
- [ ] With text in the box, install a newer APK over the app (or force-stop it) and reopen the task → the text is back in the box.
- [ ] Tap into the message box → the keyboard opens and **Save draft** and **Send** sit right above it; type more than six lines → the box scrolls, the buttons stay visible.
- [ ] Tap a draft's text → it moves into the box; tap **Send** on another draft → it arrives in the Claude window and disappears from the list.
- [ ] Tap a draft's **Edit** → it leaves the list, its text is in the box and the keyboard is open; tap **Edit** on a message queued on the desktop → a copy is in the box, the desktop's message stays listed.
- [ ] Force-stop the app and reopen the task → the drafts are still there.

## Restart to update (`dsn~restart-to-update~10`)

Needs a checkout whose upstream has a commit the local branch does not (push one from another clone, or reset the branch one commit back).

- [ ] Start with `just run-loop` (`scripts/run-loop.cmd` on Windows) → the app pulls, builds and comes up; the update button is in the toolbar in the same gray as its neighbours.
- [ ] Wait for the check (or reset the branch back before starting) → the update glyph turns blue, the status bar says a new version is available, and — when the new commits add changelog bullets — a red badge at its lower right counts those pending changes (no icon beside it moves); the tooltip names the commit count.
- [ ] Click it → the "What's new" window opens with a bar and "Checking remote …" in its button row and **Restart to update** disabled; seconds later the bar is gone, the button is live, and the title's count matches the badge (`N pending changes since you last looked — now at <sha> …`); the badge is gone, the glyph stays blue.
- [ ] Push another commit from the other clone between the tick and this click → the window lists that bullet too: the click fetched, it did not reuse the five-minute-old answer.
- [ ] Press **Later** instead → the window closes and nothing restarts; the update button is still there and opens it again.
- [ ] With the app up to date, click it → after the check the window has no **Restart to update**, and the other button reads **Close**.
- [ ] Press **Restart to update** → the app closes, the script pulls and rebuilds, and the app comes up again on the new commit; the window is where it was left.
- [ ] Quit that new instance normally → the script's loop ends, the terminal is back at the prompt.
- [ ] Started through `just run-debug` or the packaged exe by hand, open the window with an update waiting → the button reads **Exit to update**, the line above it says the app will not come back by itself, the toolbar tooltip agrees; pressing it quits (Gradle prints the exit-55 note).
- [ ] Start the app any other way (`just run`, unzipped app image) → nothing changes: the update button stays gray, no `git fetch` in the log.
- [ ] The bottom right corner, left of the open-log button, names the commit
      the app runs on — short sha and commit date and time, matching
      `git log -1` in the checkout (`dsn~running-commit~3`); an unzipped app
      image shows nothing there.
- [ ] Double-click that commit line → the status bar says the sha was copied,
      and a paste gives the bare sha, no date (`dsn~running-commit~3`).

## Auto category (`dsn~auto-pr-category~3` / `dsn~auto-pr-lookup~2`)

Needs a locally authenticated `gh` and a repository with open pull requests.

- [ ] Add a category, open its `CONTEXTSWITCHER.md` config form and fill **PR search** (e.g. `repo:JabRef/jabref review-requested:@me`), **Size limit** `50`, **Delete after** `24` → the file gets an `auto:` section.
- [ ] Wait for the next round (or press the toolbar refresh) → the matching pull requests appear as tasks in that category, titled like the PR, each with the PR as its only browser URL; the status bar counts them.
- [ ] Check a PR the search matches but that another task already carries → no second row for it.
- [ ] Raise the size limit and refresh → the bigger pull requests appear too; lower it again and refresh → the ones already added stay active (the limit admits, it does not evict).
- [ ] Drag a task carrying a pull request the search does *not* match into the category, refresh twice → it stays active, and the log shows one extra state call for it.
- [ ] Merge or close one of the matched PRs and refresh → its task flips to suspended with a `⏸` timestamp and its file carries `autoPrClosed: true`; the same for the dragged-in one once its PR is closed.
- [ ] Reopen that PR and refresh → the task is active again and both `suspended:` and `autoPrClosed:` are gone from the file.
- [ ] Pause a row whose PR is open (the dismissal gesture) and refresh twice → it stays paused, is not deleted, and no second task appears for that PR; its file carries no `autoPrClosed:`.
- [ ] Set **Delete after** to `0`, refresh → the suspended task stays; set it back to `24` and hand-edit that task's `suspended:` stamp to more than a day ago, refresh → the file is gone and the row with it.
- [ ] Start a Claude session in one of the auto tasks, then take its PR out of the search and refresh → the task is left alone (no suspend, no delete).
- [ ] Stop `gh` from working (e.g. `gh auth logout`) and refresh → nothing is suspended or deleted; the log says the search failed.

## Chrome as the task browser (`dsn~chrome-extension-mv3~1` / `dsn~browser-choice~2`)

Needs Windows and Chrome; the Firefox extension may stay installed (only one browser connects at a time).

- [ ] `chrome://extensions` → Developer mode → **Load unpacked** → `extension/chrome` → the extension loads, and it is still there after a Chrome restart.
- [ ] Enter `wsPort`/`wsToken` in its options → the app's log says `Extension connected: chrome`.
- [ ] Switch to a task with browser URLs → the tabs are focused or opened, collected in the task's tab group, only the first activated, and its Chrome window comes to the front.
- [ ] Set `browser: chrome` in Settings, switch to a task of a category with a `desktop:` whose page is not open anywhere → the tab opens in a Chrome window **on that desktop**; with no Chrome window there, a new one is launched on it.
- [ ] Activate a tab belonging to a task → its row is selected in the app; the toolbar badge shows how many tasks list the page, and the popup names them (title selects and raises, `▶` switches).
- [ ] Leave the browser untouched for five minutes, then activate a task tab → it is still reported (the service worker's keepalive/alarm held or restored the connection).
- [ ] Restart the app with Chrome running, and Chrome with the app running → the connection re-establishes either way.
- [ ] With `browser: chrome`, open VS Code (or another Electron app) on a complete-control desktop and suspend a task of that category → only the Chrome windows are stored and closed; the Electron window survives.
- [ ] Leave `browser: firefox` with only the Chrome extension connected → the Chrome window is still raised, and the status bar says `Chrome:` — the connected extension outranks the setting.
- [ ] With both extensions connected, right-click the toolbar globe → Firefox and Chrome are offered, the setting's browser selected; choose the other → the tooltip names it, a task's tabs open there, and `settings.yaml` says so (`dsn~prefer-chosen-browser~1`).
- [ ] Restart the non-chosen browser → the chosen one stays in use.
- [ ] Connect the Firefox extension too (both browsers open), then close Firefox → Chrome keeps working and the app still reports an extension connected (the client role goes back rather than to nobody).
- [ ] Close both browsers and click a PR link on a category desktop → a window of the **configured** `browser:` is launched; this is the one job the setting has left.

## A merged task removes itself (`dsn~merged-task-cleanup~2`)

Needs a real remote with a Claude worktree task, a locally authenticated `gh`, and a task directory that is a git repository.

- [ ] Merge the pull request of a task whose window sits at the Claude prompt → within a merged-PR poll round the window is gone, the transcript and the worktree are removed on the remote, and the row disappears.
- [ ] `git log` in the task directory → the commit before the deletion carries the task file with a `## Last screen before the auto-cleanup` block holding what the window showed.
- [ ] Do the same with a task whose window shows a pending question (e.g. a permission prompt) → nothing is removed, and the log says the last screen still wants a human.
- [ ] Pause that task, hand-edit its `suspended:` stamp to more than `mergedCleanupDays` ago, and wait for the next round → it is removed too, its question archived in the commit.
- [ ] Set `mergedCleanupDays` to `0` → no task is removed any more, whatever its pull requests say.
- [ ] Select the row of a task with a merged PR and leave it selected → it is not removed while it is the selected row.
- [ ] Merge the PR of a task whose window is `working` → it is left alone until the session goes idle.

## Task list redesign (2026-09-16: `dsn~category-search~3`, `dsn~task-row-hover-actions~7`, `dsn~pr-state-indicator~3`, `dsn~running-task-accent~1`, `dsn~category-header-path~1`, `dsn~task-create-ui~15`)

- [ ] Press a category header's **name** → the name appears as a chip in the find field, only that category stays in the list, and typing searches it; the rest of the header still opens the config.
- [ ] The chip's × (or Backspace at the start of the field) → every category is back, the typed query still applies.
- [ ] Hover a task row → the gutter (toggle · link · [refactorings] | delete) appears at the right end, over the title's end; the title does not move or re-truncate, and the link/note icons right of it stay clickable.
- [ ] The toggle is an accent-coloured pause on an active task (suspends, confirmed) and play on a suspended one (resumes and switches).
- [ ] Every active task has the accent bar on its left edge; a collapsed category shows the number of them left of its header buttons (nothing while expanded).
- [ ] A task with a PR shows `#<number>` under its title: before the first `gh` poll without icon, chip-shaped without colour; afterwards with the open/draft/merged/closed icon. Click opens the PR, right-click offers Open/Copy URL.
- [ ] A category with `workspacesRoot` shows that path on the header's second line; its tasks' `wd` shows only the directory below it.
- [ ] Each hand-filled category has a plus at the header's right end and a slim `Add task…` row after its last task; an auto category has neither.

## Fork a task (`dsn~task-fork~1`)

Needs a real remote with a task whose Claude session id is recorded (`claude.sessionId`, e.g. after "Sync tmux windows…").

- [ ] Select a plain task (no remote, or no recorded session id) → the terminal bar's **Fork…** is disabled, its tooltip names what is missing.
- [ ] Select a forkable task and press **Fork…** → a dialog titled "Fork &lt;task title&gt;" opens with a text field, model/effort pickers and **Back**/**Fork**; **Fork** is disabled while the field is blank.
- [ ] **Back** → the dialog closes, no new task appears.
- [ ] Type an instruction, pick a model, press **Fork** → the dialog closes immediately, a new task appears in the same category (not selected — the source task stays shown), its status bar messages read "Forking …" then "Forked …".
- [ ] Switch to the new task's terminal → Claude greets as a fork of the source task's conversation, checks the source's workspace, then works on the typed instruction with the picked model.
- [ ] The new task's `# Notes` names the source task ("Forked from &lt;title&gt; (`&lt;id&gt;`)").
- [ ] Three plain Enters or three Ctrl+Enters in the dialog's field fire **Fork** like clicking it (`req~compose-key-conventions~5`).

## Results log

| Date | Host | Scope | Result / deviations |
|------|------|-------|---------------------|
| 2026-07-13/14 | devbox (koppor@) | Full v0.1 checklist | **All items pass.** The run surfaced and fixed: Gateway 2026.1 link validation (`port`, `idePath`, realpath-canonical path), browser interstitial on protocol URLs, JetBrains Client focus via process (cloaked-window enumeration), suspend/resume rework (silent suspend, status-based resurrect, no-claude plain windows), import fixes (pane titles as task titles, spinner strip, no bogus `claude:` sections, exact session ids via `@cs_session_id`, free-name import), stale-task-object fixes (`freshTask`, click-time note URL), DnD from unselected rows, Revert focus trap, onenote raw dispatch, WT cross-desktop jump. UI reworked: hover action icons (SvgNode/MDI, MADR 0010), play = switch, delete teardown + neighbour selection, group header icons, `attention` dot. |
| 2026-07-15 | local (Windows) | Task tags + filter | **All items pass.** Verified with Oliver: palette chips (incl. color-less → gray), task/group **Tags** submenus, toolbar button sizing, live palette refresh on settings edit, AND filtering + only-active-tag chips + Clear, narrow-list filtering. Fixes during verify: Tags menu-button sizing via bundled `main.css` (`.menu-button.small > .label`), and chips moved to the row's second line so a long title never truncates in a narrow pane. |
|      |      |       |                     |
