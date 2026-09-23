---
# Title shown in the task list
title: "Example: review JabRef PR"
# active | suspended | done — suspending from the app ends the tmux window (confirmed); resuming recreates it
status: active
# Tags to filter the task list by (names without spaces). Configure the
# available tags and their colors as a tags: palette in settings.yaml.
# tags: [phone, jabref]
# ssh destination (alias from ~/.ssh/config or user@host); default for all remote actions
remote: devbox
# Focus this tmux window on <host> when switching (section optional)
tmux:
  session: jabref
  window: claude
# Open this project via JetBrains Gateway when switching (section optional).
# A bare `intellij:` is enough when the task has a claude section — the
# project path then falls back to claude.workspace, then claude.cwd.
intellij:
  # projectPath: /home/user/repos/jabref   # only needed without a claude section (or for a different project)
  # remote: otherbox   # optional override of the task remote
  # ide: /home/user/.cache/JetBrains/RemoteDev/dist/ideaIU-2026.1   # optional pinned IDE build path on the remote; omit to use the newest installed backend
# Focus (or open) this URL in Firefox when switching (section optional)
browser:
  urls:
    - https://github.com/JabRef/jabref/pull/12345
# Focus (or open) local folders in File Explorer when switching (optional)
# folders:
#   - C:\Users\me\nextcloud\SE2
# Open this note when switching (optional). Use the onenote:… link form so the
# OneNote desktop app opens (the onedrive.live.com web URL opens the browser).
# Without a task note, the category's note (the group's CONTEXTSWITCHER.md note:) is used.
# note: onenote:https://d.docs.live.net/…/Projects.one#Page&section-id={…}&page-id={…}&end
# The intellij:, browser:, and folders: sections above default to the
# category's (the group's CONTEXTSWITCHER.md); a section set here wins.
# Any key can be scoped to one machine by suffixing it with this computer's
# name or its OS — the most specific one wins:
# folders-windows:
#   - C:\Users\me\nextcloud\SE2
# folders-devbox:
#   - /data/koppor/se2
# Share this task with sync groups (configured from the toolbar's sync-groups
# button; set via right-click › Sync groups). Only title, tags, browser, chat,
# note and the notes are shared; syncId is the task's file name in the groups.
# sync: [team]
# syncId: review-jabref-pr
---

# Notes

Free-form Markdown notes for this task. Copy this file, give it a speaking
name (the file name is the task id), and adjust the frontmatter. Every action
section is optional — only configured actions run on switch.
This TEMPLATE.md itself is ignored by ContextSwitcher.

## Worktrees / PR stack

When one Claude session works across several git worktrees (e.g. a stacked-PR
series), keep it as **one task** and list the stack here in the body — the
frontmatter stays on the single tmux window. Add the PR links to
`browser.urls` if you want them focused on switch. Note: the `claude.workspace`
shown in the task row is whichever worktree Claude touched last.

| Worktree | Branch | PR |
| --- | --- | --- |
| ../2026-07-12-conversion-table | conversion-table | https://github.com/example/repo/pull/2 |
