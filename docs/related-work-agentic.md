# ContextSwitcher — Related Work: Agent Orchestration

Tools that run several coding agents (Claude Code, Codex, ...) side by side.
The general related work — window managers, browser workspaces, Mylyn, research papers — is in [related-work.md](related-work.md).

The recurring difference: these tools own the agent's terminal and the window around it.
ContextSwitcher owns neither; it drives tmux over ssh and the desktop around it — IDE, real browser tabs, notes, chat, folders, virtual desktops.

* [Orca](https://github.com/stablyai/orca) - desktop app for running several coding agents (Claude Code, Codex, ...) side by side, each in its own git worktree, tracked in one place, with a mobile companion.
  It covers the agent-and-worktree slice of a task only; browser tabs, IDE windows, and other applications stay outside.
* [Paradev](https://github.com/paradevai/paradev) (formerly Superset) - Electron desktop app (macOS first, Elastic License 2.0) that runs many CLI coding agents in parallel, one git worktree per workspace, with a built-in diff viewer, in-app browser for dev servers, PR review comments sent straight to an agent, scheduled automations, a CLI/SDK/MCP server, and an iPhone app.
  Agent status comes from hooks it installs into each agent's config; remote hosts, mobile and automations go through its relay and a paid account.
  Like Orca it stays inside the app: the context it switches is the worktree, its terminals and its browser pane, not the desktop around them.
* [Herdr](https://herdr.dev/) - three Apache-2.0 Rust programs from one YC-backed company: **Herdr TUI** (`herdr`, the original, ~48k stars), a tmux-like server that keeps agent terminals running and detects 24 agent CLIs by their foreground process and output; **herdr-core** (`hc`), its successor runtime with no UI, an OpenRPC API and a status escape sequence (OSC 7501) that the agent emits itself; and the **Herdr app**, a native desktop window on top of herdr-core whose whole interface — layouts, sidebar, dialogs, panels — is JavaScript files that the user's agent edits.
  Remote machines join over plain ssh; a hosted relay ("Herdr Cloud") is announced.
  The context it switches is a *space* (a repo on a machine) with its *tabs* (one per task) of terminals; everything outside the terminal stays outside.

## Paradev in detail

A feature-by-feature comparison, from Paradev's README and docs as of 2026-10-29.
The one-line difference: Paradev is an agent IDE — the context it manages is a worktree with its terminals, diff and browser pane, all inside one Electron window.
ContextSwitcher manages the desktop around the agent: tmux window on a remote, IntelliJ, real browser tabs, notes, chat, folders, virtual desktops.
Paradev replaces your terminal; ContextSwitcher drives the tools you already have.

| Area | Paradev | ContextSwitcher |
|---|---|---|
| Platform, stack | Electron/React/Bun; macOS primary, Linux experimental, no Windows | JavaFX; Windows, Linux, NixOS; desktop-integration features are Windows-only |
| License, business | Elastic License 2.0, YC-backed; Pro (paid, per user) for remote access, mobile, automations; Team for cloud workspaces | MIT, one-person project, nothing paid |
| Agents | ~20 CLI agents with status hooks, model and effort picker, custom agents, built-in chat pane | Claude Code only (model and effort picker, fork session, `--resume` after reboot) |
| Where agents run | Locally in the app's own terminals; remote hosts via its relay and account | Anywhere tmux runs, over plain ssh; local is just another host |
| Task state | Database behind the app, restore for 30 days | One Markdown file per task, hand-editable, git-synced between machines with conflict merging |
| Grouping | Projects, board lanes driven by PR state | Categories as folders with inherited defaults, tags, pin, Done section, machine-specific keys |
| Worktree handling | App creates the worktree and branch, setup/teardown scripts, import existing worktrees | Claude creates the worktree per the category's `CLAUDE.md`; app records workspace and commit via tmux options |
| Status | Hooks installed into each agent's config; sound, macOS dock badge, Clear Status | Claude Code hooks publish tmux window options; traffic-light dots, usage-limit and API-error detection from the pane, 90 s stale-hook safety net, auto-retry on API error |
| Terminal | Native xterm tabs, splits, presets, rich prompt editor with @-file mentions | Live ssh mirror of the tmux window; Markdown copy of Claude's replies; clickable issue numbers; file download from the scratchpad |
| Message queue | Prompt editor, send to running agent | Per-task persistent queue, delayed send on idle, drains one per turn, attachments, Read section, fed from PR review threads |
| Diff and review | Built-in diff viewer with inline comments and editing, commit and push from the app | Show diff through the remote's git pager (delta), RefactoringMiner AST diff and refactoring count, Open in IntelliJ |
| Pull requests | GitHub only: PR list, CI checks, activity stream, select diff lines and send to an agent, PR-checkout workspaces | GitHub and GitLab state icons, review comments synced into the queue, auto categories from a PR search, auto-suspend and cleanup of merged PRs |
| Browser | In-app browser previewing the workspace's dev-server ports, Design mode to send a UI element to the agent | Firefox and Chrome extensions: focus or open the task's real tabs, tab groups, close on suspend, extension badge shows which tasks list the current page |
| Beyond the agent | Open in Cursor or VS Code; Slack and Linear create workspaces | IntelliJ via Gateway, OneNote page, Matrix room, Explorer folders, Windows virtual desktops with complete control |
| Automation | Scheduled and event-driven agent runs (Sentry, Slack, PR review requests) | Auto categories (poll a PR search every 10 min), auto-suspend idle tasks, merged cleanup; no scheduled agent runs |
| Scripting | CLI, TypeScript SDK, MCP server | `contextswitcher://` deep links only |
| Mobile | iPhone and iPad app, Pro only, via relay or cloud | Android app over ssh and git, plus Android Auto, no account |
| Collaboration | Organizations, shared hosts, Pages for team review | None, single user by design |

**Paradev has, ContextSwitcher lacks:**

* Multi-agent support — Codex, Gemini, OpenCode and others with one status mechanism; our hooks and pane grep are Claude-Code-specific.
* In-app diff viewer with line comments sent to the agent; we page a diff in the terminal and sync PR comments, but have no local select-and-send.
* Scheduled and event-triggered agent runs; auto categories only add tasks, they do not start sessions.
* CLI, SDK and MCP server, so agents can create tasks themselves; we have deep links and the task files.
* Dev-server preview per worktree with port detection.
* Terminal presets and splits; we mirror one tmux window per task.
* Team features: shared hosts, Pages, Linear and Slack.

**ContextSwitcher has, Paradev lacks:**

* Whole-desktop switching — IDE window, real browser tabs, notes, chat, folders, virtual desktops; Paradev stops at its own window and an "open in IDE" button.
* Plain ssh and tmux, no relay, no account; remote hosts and the phone need no cloud component.
* Files as state: readable, editable, git-backed, conflict-merged.
* Review workflow: auto-filled review categories, review threads into the message queue, GitLab support, PR-state-driven suspend and cleanup.
* A delayed queue that sends on idle.
* Resilience: usage-limit and API-error detection, auto `continue`, auto-suspend of idle sessions, restart all tasks after a host reboot.
* RefactoringMiner view — semantic "what was refactored" instead of a text diff.
* Windows support, and Android instead of iOS.

**Worth stealing:** select diff lines and send to the session; an MCP server or small CLI so a session can create sibling tasks; status hooks for other agents (the tmux-option protocol is agent-agnostic, only the wizard's hook installer is Claude-specific); scheduled sessions as a category-level cron with a canned prompt.

## Herdr in detail

A feature-by-feature comparison, from herdr.dev, the three READMEs and the app's shipped `AGENTS.md` as of 2026-11-04.
The one-line difference: Herdr replaces tmux and the terminal emulator with an agent-aware runtime and a window built for it.
ContextSwitcher keeps tmux and the terminal, and switches the desktop around them.
Herdr is the closest relative so far, because it is the only one that, like ContextSwitcher, treats a terminal on any ssh host as the home of an agent and reads the agent's status out of that terminal.

| Area | Herdr | ContextSwitcher |
|---|---|---|
| Platform, stack | Rust; the app draws with GPUI (ghostty-vt for the terminal), no Electron; macOS (Apple silicon), Linux x86_64 and arm64, Windows x86_64 | JavaFX; Windows, Linux, NixOS; desktop-integration features are Windows-only |
| License, business | Apache 2.0, free; YC S26, $6M seed (Bessemer, 2026-09); planned paid piece is Herdr Cloud, a relay that links your machines, not hosting; usage telemetry on by default | MIT, one-person project, nothing paid, no telemetry |
| Agents | TUI: 24 kinds detected (`claude`, `codex`, `gemini`, `cursor`, `copilot`, `opencode`, `grok`, ...), started with `herdr agent start --kind`; core: anything in a terminal, status only if it emits OSC 7501 | Claude Code only (model and effort picker, fork session, `--resume` after reboot) |
| Where agents run | In herdr-core or the TUI server on each machine; `hc machine add` adds an ssh host, its terminals show in the same sidebar; agents keep running when the laptop sleeps or the app is closed | Anywhere tmux runs, over plain ssh; local is just another host; the tmux window keeps running when the app is closed |
| After a reboot | TUI restores the layout and resumes supported agent sessions; the app brings back a shell in the terminal's last folder, the agent is not restarted | *Restart all tasks*: a new tmux window per task with `claude --resume` of the recorded session |
| Task state | Per machine, a layout document inside that machine's core; newest write wins, no merging; a space is named after its repo, a tab is a number until renamed | One Markdown file per task, hand-editable, git-synced between machines with conflict merging |
| Grouping | Spaces (one repo on one machine) hold tabs (one task); agents sidebar sorted by priority or grouped by space; gallery mods add kanban boards and queues | Categories as folders with inherited defaults, tags, pin, Done section, machine-specific keys |
| Worktree handling | Shipped `worktree` dialog: new, open, remove a checkout under `~/.herdr/worktrees/<repo>/<branch>`, one space per worktree; several gallery mods do the same | Claude creates the worktree per the category's `CLAUDE.md`; app records workspace and commit via tmux options |
| Status | Core: the agent emits `OSC 7501 state=working|blocked|done|error`, nothing scraped, no agent list; TUI: foreground process plus output patterns, states `working`, `blocked`, `idle`, `done`, `unknown`; a tab's status is its most urgent agent (blocked > done > working > idle); sounds; ntfy phone push as a mod; agents inside tmux cannot report | Claude Code hooks publish tmux window options; traffic-light dots, usage-limit and API-error detection from the pane, 90 s stale-hook safety net, auto-retry on API error |
| Terminal | Its own GPU terminal, tabs and splits, *peek* overlay for any terminal by address (`t7`, `vps/t7`), ctrl-click a terminal address in output to open it | Live ssh mirror of the tmux window; Markdown copy of Claude's replies; clickable issue numbers; file download from the scratchpad |
| Message queue | `hc send <terminal> "..."` and `herdr agent prompt --wait` from scripts; no queue in the UI | Per-task persistent queue, delayed send on idle, drains one per turn, attachments, Read section, fed from PR review threads |
| Diff and review | Shipped *Changes* panel: working-tree diff per file and a commit box; gallery mods add PR-style review with line comments | Show diff through the remote's git pager (delta), RefactoringMiner AST diff and refactoring count, Open in IntelliJ |
| Pull requests | None built in; gallery mods list PRs and issues (GitHub, Linear, OneDev) | GitHub and GitLab state icons, review comments synced into the queue, auto categories from a PR search, auto-suspend and cleanup of merged PRs |
| Browser | None; the only in-app content beside terminals are mod panels | Firefox and Chrome extensions: focus or open the task's real tabs, tab groups, close on suspend, extension badge shows which tasks list the current page |
| Beyond the agent | Nothing; no IDE hand-off, no notes, no chat, no desktop integration | IntelliJ via Gateway, OneNote page, Matrix room, Explorer folders, Windows virtual desktops with complete control |
| Agents running agents | First-class: `hc run --bg --name review codex "..."` starts a helper owned by the caller, `hc wait`, `hc read`, `hc send`; helpers report back into the parent's terminal; `hc templates` for canned roles; sub-agents appear under their parent in the sidebar | None; Claude Code's own subagents are invisible to the app |
| Automation | None built in; gallery mods for schedulers; everything scriptable via `hc` or `herdr` CLI | Auto categories (poll a PR search every 10 min), auto-suspend idle tasks, merged cleanup; no scheduled agent runs |
| Scripting | `hc` and `herdr` CLIs, OpenRPC socket API (`hc schema`) with streams and events, `herdr-app do <command>`; `hc --skill` hands the agent its manual | `contextswitcher://` deep links only |
| Customization | The window is files in `~/.config/herdr-app`: `layouts/*.json`, `keys.json`, `settings.json`, `mods/<name>/main.js` (GPUI elements from JavaScript, no HTML or DOM); 289 community mods and layouts; `herdr-app shot` screenshots the window for the agent | Settings dialog, task-file front matter, per-category `CLAUDE.md`; no plugin surface |
| Mobile | None; the API is "open to a phone app" | Android app over ssh and git, plus Android Auto, no account |
| Collaboration | None; shared layouts and mods via the gallery | None, single user by design |

**Herdr has, ContextSwitcher lacks:**

* Agent-agnostic status — 24 detected CLIs in the TUI, and a protocol (OSC 7501) any program can speak; our hooks and pane grep know only Claude Code.
* Agents running agents with ownership, wait and report-back, and sub-agents shown under their parent.
* A real scripting surface: CLI, OpenRPC socket API with events, and a manual the agent reads with one call.
* The whole UI as agent-editable files; a user asks their agent for a kanban board and gets one.
* A worktree dialog and a working-tree diff with a commit box inside the app.
* Peek at any terminal, local or remote, by address.
* Native GPU terminal with splits; no ssh mirror lag.

**ContextSwitcher has, Herdr lacks:**

* Whole-desktop switching — IDE window, real browser tabs, notes, chat, folders, virtual desktops; Herdr ends at the terminal and its own panels.
* Files as state: readable, editable, git-backed, conflict-merged; Herdr's layout document is newest-write-wins per machine.
* Review workflow: auto-filled review categories, review threads into the message queue, GitHub and GitLab state, PR-state-driven suspend and cleanup.
* A delayed queue that sends on idle.
* Resilience: usage-limit and API-error detection, auto `continue`, auto-suspend of idle sessions, `--resume` of every task after a host reboot (the app gives you a shell back, not the agent).
* RefactoringMiner view — semantic "what was refactored" instead of a text diff.
* Android app with no cloud piece.
* Plain tmux underneath: any ssh client, any phone terminal, still reaches the session; a Herdr terminal needs a Herdr client.

**Worth stealing:** OSC 7501 — make the terminal mirror recognize it, so a future Claude Code (or any agent) that emits it gets a status without hooks, and the tmux-option protocol becomes the fallback; `hc`-style sub-agent visibility (a session's helper windows listed under the task); a small CLI or MCP server with `--skill` output so a session can create sibling tasks and queue messages; a worktree dialog for "open an existing checkout as a task".

**Worth considering:** ContextSwitcher as a herdr-core client. A task's terminal would live in `hc` instead of a tmux window; labels (`hc run --label`) replace tmux options for workspace, commit and status; the Android app would speak the OpenRPC API instead of parsing tmux. The price is a second daemon per host and losing plain-ssh reach to the session.
