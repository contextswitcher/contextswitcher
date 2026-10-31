# ContextSwitcher — Related Work

When working with code in Eclipse, one has opened many files by the time.
Often, not all opened files are required for a specific project.
Moreover, when looking deeper into a project, there are different "tasks" to do.
Currently, such tasks are called "issues".
See the GitHub guidelines on issues for more information on issues: <https://guides.github.com/features/issues>.

## Windows

* Windows Timeline. See <https://winaero.com/blog/enable-use-timeline-windows-10/>, <https://www.digitaltrends.com/computing/how-to-use-windows-timeline/>, <http://winfuture.de/videos/Software/Windows-Timeline-So-funktioniert-die-Zeitleiste-von-Windows-10-18708.html>, and [Windows Timeline Introduction Video](https://youtu.be/jV09HpVj4gg?t=123)
* [Windows 10 Sets](https://insider.windows.com/de-de/articles/introducing-sets/).
  [Sundown in April 2019](https://www.heise.de/newsticker/meldung/Bedienkonzept-Microsoft-beerdigt-Sets-fuer-Windows-10-4404211.html).
* [bug.n](https://github.com/fuhsjr00/bug.n) - can provide views on opened windows.
* [Workspacer](https://github.com/rickbutton/workspacer) - tiling Window management on Windows - with multiple desktops.
* [Display Fusion](https://www.displayfusion.com/) - offers profiles, where screen resolution and window position is stored. No app starting, closing windows, ...

## Mac OS X

* [Switch](https://github.com/numist/Switch)

## Linux

* [Plasma Activities](https://docs.kde.org/trunk5/en/plasma-desktop/plasma-desktop/activities-interface.html)

## Browser

* [Session Buddy](https://chrome.google.com/webstore/detail/session-buddy/edacconmaakjimmfgnblocblbcdcpbko) Allows for grouping tabs, store the group for a later opening.
* Light Table as new IDE concept: <http://www.chris-granger.com/2012/04/12/light-table-a-new-ide-concept/>
* [ZenHub's Workspaces](https://help.zenhub.com/support/solutions/articles/43000495219). This concept is bound to GitHub.
* Firefox supports [profiles](https://developer.mozilla.org/en-US/docs/Mozilla/Firefox/Multiple_profiles) where separate Firefox configurations could be done (e.g., family or work).
  However, it requires much effort to create a Firefox profile for each context

## Special Features

* IntelliJ 2017.3 restores the current editors when changing branches in git.
  See <https://blog.jetbrains.com/idea/2017/10/intellij-idea-2017-3-vcs-enhancements-and-more/>.

  > IntelliJ IDEA saves your context (a set of opened files, the current run configuration, and breakpoints) provided that the Restore workspace on branch switching option is enabled in the Settings/Preferences dialog Ctrl+Alt+S under Version Control | Confirmation. When you switch to a branch, IntelliJ IDEA automatically restores your context associated with that branch.
  
* The [Bento Browser](https://bentobrowser.com/) by the [Carnegie Mellon Human Computer Interaction Institute](https://hcii.cmu.edu/) groups browser tabs into projects.
* [Marketer Browser](https://www.marketerbrowser.com/) supports multiple accounts for the same web page
  * Similar to [Session Box](https://sessionbox.io/)
  * Similar to [BiscuitBrowser](https://eatbiscuit.com/)
* [Workona](https://workona.com/) - workspaces in the browser
* Chrome Plugin [Simple Window Saver](https://chrome.google.com/webstore/detail/simple-window-saver/fpfmklldfnlcblofkhdeoohfppdoejdc)

  > Simple Window Saver makes it super easy to save and restore windows. Keep one window for work tabs, one for Gmail and Facebook, and one for your vacation planning or the research on that new TV you want to buy.

## Concept of "Task-Focused Interface"

The concept of a “[task-focused interface](https://en.wikipedia.org/wiki/Task-focused_interface)” has been invented and turned into software as Eclipse [Mylyn](https://www.eclipse.org/mylyn/).
It answers following questions:
What if only the relevant files are opened and even the most touched lines are highlighted?
What if one can give a colleague a reference to the current state of the IDE to enable a simpler reproduction of an issue?
One can read more about Mylyn at the [Mylyn Tutorial](https://web.archive.org/web/20170929190100/http://www.tasktop.com/mylyn/tutorial).
Concerning the availability of the task-focused interface across multiple applications, the [Tasktop Dev Standalone Application](http://www.tasktop.com/node/1176/) is available.
It was announced as [complete desktop task-focused interface for everyone](https://www.infoq.com/news/2008/02/tasktop-10), but it does not support arbitrary applications.

## Agent Orchestration

* [Orca](https://github.com/stablyai/orca) - desktop app for running several coding agents (Claude Code, Codex, ...) side by side, each in its own git worktree, tracked in one place, with a mobile companion.
  It covers the agent-and-worktree slice of a task only; browser tabs, IDE windows, and other applications stay outside.
* [Paradev](https://github.com/paradevai/paradev) (formerly Superset) - Electron desktop app (macOS first, Elastic License 2.0) that runs many CLI coding agents in parallel, one git worktree per workspace, with a built-in diff viewer, in-app browser for dev servers, PR review comments sent straight to an agent, scheduled automations, a CLI/SDK/MCP server, and an iPhone app.
  Agent status comes from hooks it installs into each agent's config; remote hosts, mobile and automations go through its relay and a paid account.
  Like Orca it stays inside the app: the context it switches is the worktree, its terminals and its browser pane, not the desktop around them.

### Paradev in detail

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

## Research Papers

* Focusing knowledge work with task context. PhD thesis. Available at: <https://www.researchgate.net/publication/235350419_Focusing_knowledge_work_with_task_context>
* What You See Is What You Need: [WYSIWYN: Using Task Focus to Ease Collaboration](http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.99.3548). [[PDF](http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.99.3548&rep=rep1&type=pdf)]
* [Beyond Integrated Development Environments: Adding Context to Software Development](https://doi.org/10.1109/ICSE-NIER.2019.00027). This paper reasons on an "Automated Assistant" and calls for a deeper investigation of the concept of "context". In contrast, the ContextSwitcher assumes that the user knows about his context and defines queries for the context by themselves.
