# Architectural Decision Records

Decisions follow [MADR](https://adr.github.io/madr/). New records: copy the current MADR template, next free number, `NNNN-short-title.md`.

| # | Decision | Status |
|---|----------|--------|
| [0000](0000-use-markdown-architectural-decision-records.md) | Use Markdown Architectural Decision Records | accepted |
| [0001](0001-java-and-javafx-for-the-desktop-app.md) | Java 25 and JavaFX for the desktop app | accepted |
| [0002](0002-one-markdown-file-with-yaml-frontmatter-per-task.md) | One Markdown file with YAML frontmatter per task | accepted |
| [0003](0003-system-ssh-for-remote-access.md) | System ssh for remote access | accepted |
| [0004](0004-websocket-loopback-for-browser-extension-transport.md) | WebSocket on loopback for browser extension transport | accepted |
| [0005](0005-requirements-tracing-with-openfasttrace.md) | Requirements tracing with OpenFastTrace | accepted |
| [0006](0006-intellij-remote-via-jetbrains-gateway-url.md) | IntelliJ remote via JetBrains Gateway URL | accepted |
| [0007](0007-jeditermfx-for-the-embedded-terminal.md) | JediTermFX for the embedded terminal | accepted |
| [0008](0008-local-windows-terminal-focus-via-ui-automation.md) | Local Windows Terminal focus via UI Automation | accepted |
| [0009](0009-suspend-ends-live-context.md) | Suspend ends the task's live context | accepted |
| [0010](0010-svgnode-materialdesign-for-ui-icons.md) | SvgNode + SVG-MaterialDesign for UI icons | accepted |
| [0011](0011-task-backup-via-system-git.md) | Task directory backup via the system git CLI | accepted |
| [0012](0012-testing-distribution-via-jpackage-app-image.md) | Testing distribution via jpackage app image and unsigned XPI | accepted |
| [0013](0013-virtual-desktop-focus-via-keyboard-cycle.md) | Focus a named virtual desktop via registry lookup + keyboard cycle | superseded by [0019](0019-direct-virtual-desktop-switch-with-hotkey-walk-fallback.md) |
| [0014](0014-ui-test-automation-via-testfx-on-a-real-display.md) | UI test automation via TestFX on a real (or Xvfb) display | accepted |
| [0015](0015-catalog-driven-settings-form.md) | Catalog-driven settings form, raw YAML as the source of truth | accepted |
| [0016](0016-deep-link-registration-and-ipc-via-extension-server.md) | Deep-link registration via per-OS scripts, single-instance IPC via the extension server | accepted |
| [0017](0017-calver-changelog-verified-with-heylogs.md) | CalVer changelog, verified with heylogs | accepted |
| [0018](0018-no-graalvm-native-image.md) | No GraalVM native image; jpackage app image stays the distribution | accepted |
| [0019](0019-direct-virtual-desktop-switch-with-hotkey-walk-fallback.md) | Jump directly to a virtual desktop via `IVirtualDesktopManagerInternal`, falling back to the hotkey walk | accepted |
| [0020](0020-jpackage-via-danlewis783-plugin.md) | jpackage via the danlewis783 plugin, not hand-written and not gradlex java-module-packaging | accepted |
| [0021](0021-per-desktop-window-position-remembered-by-the-app.md) | Remember the window position per virtual desktop in the app, not via PowerToys FancyZones | accepted |
| [0022](0022-refactoringminer-plain-jar-with-stdin-watchdog-tunnel.md) | Refactoring insight via the plain RefactoringMiner JAR, tunnelled with an ssh stdin-watchdog, diffing worktree vs base branch | accepted |
| [0023](0023-machine-scoped-key-suffixes-for-portable-paths.md) | Machine-scoped key suffixes (`key-<host>` / `key-<os>`) for values that differ per machine | accepted |
| [0024](0024-everforest-palette-for-the-terminal-mirror.md) | Everforest (medium dark/light) for the terminal mirror, AtlantaFX Nord for the UI | accepted |
| [0025](0025-everforest-ui-as-an-atlantafx-variable-override.md) | Everforest UI by appending a `.root` override to the AtlantaFX stylesheet (supersedes the UI half of 0024) | accepted |
| [0026](0026-android-companion-app-on-shared-java-core.md) | Android companion app on a shared plain-Java core | accepted |
| [0027](0027-sshj-for-ssh-on-android.md) | sshj for SSH on Android (scoped exception to 0003) | proposed |
| [0028](0028-jgit-task-repo-clone-on-android.md) | JGit clone of the task backup repo as the Android task source (scoped exception to 0011) | proposed |
| [0029](0029-one-mirror-client-per-tmux-session-verified-by-its-own-select.md) | One mirror client per tmux session, verified by its own select | accepted |
| [0030](0030-chrome-extension-as-a-second-copy-not-a-shared-source.md) | Chrome extension as a second copy, not a shared source | accepted |
| [0031](0031-ghq-layout-for-checkouts-workspaces-root-beside-it.md) | ghq layout for checkouts, the workspaces root beside it | proposed |
| [0032](0032-shellfx-docking-shell-for-the-main-window.md) | ShellFX docking shell for the main window | accepted |
| [0033](0033-sync-groups-as-flat-git-repositories-of-shared-task-content.md) | Sync groups as flat git repositories of shared task content | accepted |
| [0034](0034-gemsfx-info-center-for-background-work-notifications.md) | GemsFX info center for background-work notifications | accepted |
| [0035](0035-task-title-summary-via-claude-cli.md) | Task title summary via the local Claude CLI | accepted |
| [0036](0036-android-app-state-stays-on-the-device.md) | Android app state stays on the device | accepted |
| [0037](0037-message-provenance-records.md) | Message provenance records | accepted |
