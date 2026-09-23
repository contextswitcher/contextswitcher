---
status: accepted
date: 2026-07-12
decision-makers: Oliver Kopp
---

# Requirements Tracing with OpenFastTrace

## Context and Problem Statement

The project vision (README) is large; implementation happens in small bursts over years.
Scope drift and forgotten requirements are the main risks.
How are requirements captured and connected to code?

## Considered Options

* OpenFastTrace (OFT): spec items in Markdown, coverage tags in code, tracing in the build
* GitHub issues only
* Requirements documents without tracing

## Decision Outcome

Chosen option: "OpenFastTrace", because the `feat → req → dsn → impl/utest` chain makes untouched scope visible mechanically (`gradlew traceRequirements` fails on defects), the artifacts are plain Markdown in the repo, and the Gradle plugin (`org.itsallcode.openfasttrace`) integrates it into CI.

Details: `docs/requirements/features.md` holds vision-level `feat~` items; per-area files hold `req~` (user stories, linking the matching GitHub idea issues) and `dsn~` (design) items; code carries `[impl->dsn~…~1]` / `[utest->dsn~…~1]` tags. Spec items are introduced in the same commit as their first covering code, so the trace stays green throughout.

### Consequences

* Good, because "what is missing for v0.1" is a build report, not archaeology.
* Good, because requirements survive suspend/resume gaps verbatim.
* Bad, because every feature commit touches docs + code + tags — friction by design.
