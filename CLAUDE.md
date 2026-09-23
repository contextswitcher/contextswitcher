# ContextSwitcher — Instructions for Claude

## Session protocol

* **Session history lives in git commit messages.**
  Write commit bodies that carry the narrative: what was done, why, what was learned or blocked.

## Conventions

* Build: `gradlew build traceRequirements` must be green before every commit.
  On NixOS, run it inside `nix-shell` (`shell.nix`) — anything that starts JavaFX (the app, the UI tests) needs the GTK/X11 libraries it puts on `LD_LIBRARY_PATH`.
* Branches: work is committed and pushed to `experimental`; once it works, `experimental` is merged into `main` (a merge commit, `git merge --no-ff`).
  CalVer day tags (below) go on `main` only.
* Git: when the push is rejected, `git pull --no-rebase` (merge) — **never rebase**.
  Same rationale as MADR 0011: the merge commit keeps each session's state visible; concurrent Claude sessions push to `main`, and rebasing rewrites what another session may already have seen.
* Changelog: `CHANGELOG.md` in Keep a Changelog format, CalVer date releases (MADR 0017).
  Every push to `main` that changes user-visible behavior adds a bullet under **today's** section (create it if the last section is an older day).
  Today's section links to `.../compare/v<previous date>...main`; when a new day starts, retag the finished day — push `main` first (pull/merge if rejected), *then* `git tag v<that date> <last commit of that day> && git push origin v<that date>` — and rewrite its link to `.../compare/v<day before>...v<that date>`.
  Tagging before the push can land the tag on a commit another concurrent session's push then diverges from.
  Verify before committing: `jbang heylogs@nbbrd check CHANGELOG.md` (rules come from `heylogs.properties`); CI runs the same check.
* Requirements: OpenFastTrace in `docs/requirements/` — chain `feat → req → dsn → [impl->dsn~…~1]`/`[utest->dsn~…~1]` tags in code. A `dsn` item gains `Needs: impl` (and `utest` where sensible) in the same commit as the covering code. New behavior = new/updated spec item + tags in the same commit.
  Platform tags (`docs/requirements/README.md`, *Platform targets*): a `dsn` names the targets it implements (`Tags: windows, linux` / `Tags: android`), a `req` holds per target `<t>`, `<t>_later`, `<t>_no` or nothing (undecided) — underscores, never hyphens; `gradlew requirementReportAndroid` lists what is still missing there.
* Android dev build: the `android-dev` pre-release (what the app's *Update* installs) is published **only when Oliver asks** — `gh workflow run android-dev.yml` (from `main`; `--ref experimental` for the working branch) — never as a side effect of a push (Oliver, 2026-09-17: many pushes, no misuse flags from GitHub).
* Decisions: MADR in `docs/decisions/` (current MADR template, honest Considered Options). Significant technology or architecture choices get a record; superseding is explicit.
* Java 25, JavaFX, package `com.contextswitcher`, Gradle Kotlin DSL. UI code plain Java (no FXML), themed with AtlantaFX (PrimerLight); the main window's panes live in a ShellFX docking shell (`ShellFxHost`, MADR 0032). No Java SSH libraries on the desktop — system `ssh` only (MADR 0003); the Android app is the scoped exception, using sshj (MADR 0027).
* UI sizing: every new `Button`/`ComboBox`/`MenuButton`/`ToggleButton` gets `Styles.SMALL` — the app is uniformly small-sized, and AtlantaFX's default size towers over its neighbours.
  Icon-only buttons additionally get `Styles.BUTTON_ICON, Styles.FLAT`.
  Controls AtlantaFX has no `.small` rule for are patched in `app/src/main/resources/com/contextswitcher/ui/main.css`.
* Doc comments in Markdown Javadoc form (JEP 467, `///`), not classic `/** … */`.
* Nullness: JSpecify — every package gets `@NullMarked` in `package-info.java`; anything nullable is annotated `@Nullable` (type-use position, e.g. `Task.@Nullable TmuxConfig`).
* Logging: tinylog 2 API directly (`org.tinylog.Logger` static methods, no logger fields). The `slf4j-tinylog` bridge exists only for third-party libraries. Config in `app/src/main/resources/tinylog.properties` (console + rolling file in `~/.contextswitcher/logs/`).
* Portability: keep all code OS-portable (no Windows-only APIs, paths via `user.home`, plain `ssh` from PATH). Windows is the primary verification target for v0.1; Linux/GNOME follows without a rewrite.
* License is MIT — no strong-copyleft dependencies (GPL); weak copyleft is fine as a normal library dependency (LGPL, EPL, MPL — Oliver's call, 2026-07-14), as are Apache/MIT/BSD. GPL-2.0 *with Classpath Exception* is accepted too (ShellFX's `tabpanepro`, 2026-09-13, MADR 0032). Check licenses of new dependencies.
* Unit tests for real logic (parsing, argv/URL building, protocol encoding), not for UI.
  Pure-UI behavior (dialogs, key chords, filters) gets a TestFX test instead (`@Tag("ui")`, `gradlew :app:uiTest`; headless: `just uitest`; MADR 0014) — not part of `build`.
  Use `just uitest`, not a bare `xvfb-run -a`: the wrapper's default screen size is the distribution's (nixpkgs ships 640x480), and on a screen too small for the window a robot click lands on nothing and the test fails for no reason of its own.
  Manual E2E checklists live in `docs/manual-test.md` (from M1-17 on) for what needs a real remote or a human eye; prefer a UI test over a new checklist item.
  A UI behavior shared by two places (the compose chords, installed on the queue add box *and* the Add-task dialog field) gets its test at **each** call site — a test at one of them leaves the other free to regress unnoticed.
* Docs drift: `CONSISTENCY.md` lists what prose must agree with code (spec-id revisions, settings keys, shortcuts, links); `scripts/consistency.sh` runs the mechanical half. Run it when touching README.md or `docs/`.
* Colors: never hard-code one in code — no `Color.web`/hex strings, no color property in a `setStyle` (Oliver, 2026-09-13).
  Give the node a style class instead: AtlantaFX's own (`Styles.TEXT_MUTED`, `Styles.DANGER`, `Styles.WARNING`, …) or one in `main.css` built on the theme's `-color-*` variables, so every theme — and a live theme switch — recolors it; `MainWindow` adds `main.css` to every window, dialogs included.
  A deliberate constant (the brand blue, claude-semaphore's dot colors) is a named looked-up color in `main.css`'s `.root`, never a literal at a call site.
  Exceptions: colors that are data (a tag's palette entry) and palettes of widgets CSS does not reach (the JediTermFX terminal, the Android ANSI palette); on Android use `MaterialTheme.colorScheme`.
* Markdown: one sentence per line (semantic line breaks) in all `*.md`, so diffs stay readable.
  Applies to new and edited text; do not mass-reflow untouched paragraphs (keeps blame useful).
* Async single-shot buttons (ssh/tmux round-trips, browser/desktop focus — anything that isn't the multi-action task switch already covered by the status-bar chips): disable the button for the round-trip and re-enable it on completion, success or failure, so a slow remote never reads as a dead button; pair with a `statusBar().message("Doing X …")` → outcome message, the pattern `focusPr`/`Main.startScratchWindow` use.

## Architecture summary

JavaFX task-list app. Tasks = Markdown files with YAML frontmatter in `%USERPROFILE%\.contextswitcher\tasks\` (watched). Switch actions run concurrently, report per-action status chips: tmux focus via system ssh, IntelliJ via `jetbrains-gateway://` URL, Firefox via own WebExtension connected to the app's loopback WebSocket (port 17872, token in `settings.yaml`). v0.2 adds an embedded JediTermFX terminal mirroring the remote tmux/Claude session via pty4j + `ssh -t`.
