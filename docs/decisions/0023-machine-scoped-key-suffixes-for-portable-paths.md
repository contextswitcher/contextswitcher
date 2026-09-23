---
status: accepted
date: 2026-07-29
decision-makers: Oliver Kopp
---

# Machine-Scoped Key Suffixes for Values That Differ per Machine

## Context and Problem Statement

The task directory is one git-synchronised tree shared between machines (`req~task-git-backup~2`), but most of what it stores is a path — `folders`, `workdir`, `workspacesRoot`, `mainCheckout`, `intellij.projectPath`.
The same checkout is `C:\git\jabref` on the Windows desktop and `/data/koppor/jabref` on the Linux box, so a single value is wrong on one of them, and keeping two diverging copies of the file defeats the sync.
How does one file carry a value per machine, for *any* key, without a rule per key?

## Considered Options

* Key suffix: `folders-windows:`, `mainCheckout-devbox:` — the shape [JabRef](https://github.com/JabRef/jabref) uses for machine-scoped preferences (`comment-<user>-<host>:`)
* Platform block: a `windows:` / `linux:` / `<hostname>:` mapping whose keys override the top-level ones, like [`just`](https://just.systems)'s `[windows]` attributes
* Nested per-key map: `folders: {windows: […], linux: […]}`

## Decision Outcome

Chosen: **key suffixes**, `<key>-<hostname>` beating `<key>-<os>` beating `<key>`.

It is the only option that costs nothing per key.
The fold happens once in `TaskFileParser.loadYaml` (`dsn~machine-key-variants~1`), so every key that exists today and every key added later is scoped-able, in task files and in `CONTEXTSWITCHER.md` alike, while each parse rule keeps reading its one plain key.
The other two put the variant *inside* the value: a platform block duplicates the file's structure one level down (and a key would then have two places to live, with `section.key` overrides needing the block to restate the section), and a nested per-key map changes the type of every key that wants a variant — `folders` would be a list here and a map there, which each parser must then handle.

Host name and OS in the same flat namespace is what makes the suffix carry both cases: two Linux boxes with different layouts are as expressible as Windows-vs-Linux, and the specificity order needs no extra syntax — the host token is simply applied after the OS token.
JabRef's user part is omitted: a machine is used by one person here, and it can be added later as a third token without changing anything already written.

The cost taken on knowingly: a suffixed key is invisible to a reader who does not know the scheme, and a typo (`folders-windwos:`) silently does nothing rather than failing — the same failure mode as any other misspelled optional key in these files, which are hand-edited and validated by what the app then does.
Keys of *another* machine deliberately survive the fold as unknown keys instead of being dropped, so nothing has to distinguish them from a genuine unknown key.

### Consequences

* Good, because one synchronised file works on every machine, for every key, with no per-key code.
* Good, because the scheme is opt-in and invisible until used: a file with no suffixed key parses exactly as before.
* Bad, because the resolution is implicit — reading a file does not show which value *this* machine gets.
* Neutral, because the host name has to be discovered (`COMPUTERNAME`, `HOSTNAME`, then `InetAddress.getLocalHost()`); an unresolvable one leaves the OS token working.
