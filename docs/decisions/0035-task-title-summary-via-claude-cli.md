---
status: accepted
date: 2026-09-15
decision-makers: Carl Christian Snethlage
---

# Task Title Summary via the Local Claude CLI

## Context and Problem Statement

A task created with `Add local Claude` carries the typed description as its title, and a description is routinely a paragraph, which the task row can only ellipsize.
Remote tasks already get a short title: the launch prompt asks Claude to publish one as the `@cs_title` tmux option.
Local sessions have no such channel — the Windows session runs in the app's own ConPTY without tmux, and the local tmux window is not asked for one.
The title is to be generated once at creation, may be changed by the user afterwards, has to work on Windows and Linux, and must not change anything about tmux.
Where does the short title come from?

## Considered Options

* The locally installed `claude` CLI in print mode (`claude -p`), called by the app
* Ask the session itself, as the remote flow does with `@cs_title`
* The Anthropic Java SDK, calling the Messages API directly
* A rule without a model: the description's first words

## Decision Outcome

Chosen option: "The locally installed `claude` CLI in print mode", because it is the system-tool approach of MADR 0003 (as `gh` is for PR titles), needs no API key and no dependency, works identically for both local session kinds, and is already installed and logged in wherever a local Claude session can start.
The first-words rule stays as the fallback when the CLI cannot answer.

### Consequences

* Good, because every local creation path gets the same title, whether or not the session starts.
* Good, because the model sits behind `TaskTitleSummarizer`; another source is a new implementation, not a change to the adopter.
* Bad, because the description is sent to Anthropic once more, outside the session (it is sent with the prompt anyway).
* Bad, because a cold `claude -p` takes seconds, so the short title arrives after the row appears.
* Neutral, because the CLI's flags (`--tools ""`, `--setting-sources ""`) are its interface now; a CLI release that renames them degrades to the fallback, not to an error.

## Pros and Cons of the Options

### Ask the session itself

* Good, because the machinery exists for remote tasks.
* Bad, because the Windows session has no tmux to publish into, and a new channel (a file, a deep link) would be needed.
* Bad, because it depends on Claude following an instruction, and it costs the session a turn before the real task.

### The Anthropic Java SDK

* Good, because no process start and a precise model choice.
* Bad, because it needs an API key in `settings.yaml`, separate from the user's Claude login, and a new dependency.

### A rule without a model

* Good, because instant and offline.
* Bad, because the first sentence of a brain-dump is rarely a good title.
