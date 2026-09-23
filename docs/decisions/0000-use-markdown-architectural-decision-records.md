---
status: accepted
date: 2026-07-12
decision-makers: Oliver Kopp
---

# Use Markdown Architectural Decision Records

## Context and Problem Statement

ContextSwitcher is a long-running project worked on in bursts, sometimes with months between sessions.
Design decisions and their rationale must survive these gaps, otherwise every resumption re-litigates old choices.
The 2021 prototype already showed this failure mode: it left one half-filled ADR template behind, and the reasoning behind its gRPC/Envoy architecture was lost.

## Considered Options

* MADR (Markdown Architectural Decision Records)
* Free-form design notes in the wiki or README
* No records, decisions only in Git history and issues

## Decision Outcome

Chosen option: "MADR", because decisions are versioned with the code, reviewable in PRs, and the template forces the "considered options" and "consequences" sections that make resumption cheap. Records live in `docs/decisions/NNNN-title.md` using the current MADR template.

### Consequences

* Good, because a fresh session (human or AI) can reconstruct why the architecture looks the way it does.
* Good, because superseding a decision is explicit (status change + new record) instead of silent drift.
* Bad, because writing a proper record takes effort for each significant decision.
