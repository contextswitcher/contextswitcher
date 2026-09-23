---
name: codegraph
description: Query a CodeGraph index (a SQLite graph of symbols, call edges and files) to locate and understand code. Use when you need to find where a symbol lives, who calls it, what it calls, what breaks if you change it, which tests cover a change, or to read a symbol's verbatim source with its call paths — instead of a grep/read loop. Only applies to repos that have a .codegraph/ directory.
---

# CodeGraph

A local index of symbols, call edges and files. One query returns verbatim,
line-numbered source **plus** the call paths between symbols — including
dynamic-dispatch hops grep cannot follow.

## When to use

Reach for it **before** grep/find/Read whenever the repo (or the subtree you
are working in) has a `.codegraph/` directory. If it does not, use the normal
search tools — indexing is the user's decision, don't run `codegraph init`
unasked.

## How to call it

Prefer the MCP tools when present (`codegraph_explore`, `codegraph_node`);
otherwise the CLI prints identical output:

```bash
codegraph explore "<symbol names or question>"   # symbols' source + call paths
codegraph node <symbol|file>                     # one symbol's source + caller/callee trail
codegraph query <search>                         # find symbols by name
codegraph callers <symbol>                       # who calls it
codegraph callees <symbol>                       # what it calls
codegraph impact <symbol>                        # blast radius of a change
codegraph affected <files...>                    # test files touched by these changes
codegraph files                                  # indexed project structure
codegraph status                                 # index freshness/statistics
```

Add `[path]` / `--project <path>` (or MCP `projectPath`) to target another
repo or a subproject; CodeGraph resolves the nearest `.codegraph/` at or above
it. Multiple projects in one session are fine.

## Notes

- Naming a file or symbol in an `explore` query makes it print that file's
  current line-numbered source — use it as a smarter `Read`.
- The index syncs in the background. If results look stale after large
  external changes (branch switch, rebase), run `codegraph sync`; a blocked
  index is cleared with `codegraph unlock`.
- `codegraph upgrade` updates the tool; `codegraph install` (re)registers the
  MCP server with the agent.
