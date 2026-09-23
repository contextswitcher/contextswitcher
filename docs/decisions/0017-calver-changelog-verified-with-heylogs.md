---
status: accepted
date: 2026-07-22
decision-makers: Oliver Kopp
---

# CalVer Changelog, Verified with heylogs

## Context and Problem Statement

Session history lives in commit messages (decision 2026-07-17), which is fine for Claude sessions but unreadable as "what changed for the user".
The project ships continuously — several pushes to `main` per day, no release planning — so a semantic version number would be invented, not earned.
How do we keep a human-readable change history that costs one bullet per push and cannot silently rot?

## Considered Options

* CalVer date sections (`YYYY-MM-DD`) in `CHANGELOG.md`, tagged `v<date>` at day end, format checked by [heylogs](https://github.com/nbbrd/heylogs)
* Keep a Changelog with SemVer versions and an `Unreleased` section
* Generated changelog from commit messages (git-cliff, release-please, Conventional Commits)
* Status quo: GitHub releases / commit log only

## Decision Outcome

Chosen option: **CalVer date sections checked by heylogs**.

A day is the natural release unit here: work lands continuously, so "what changed on 2026-07-22" is a question with an answer, while "what is in 0.3.0" is not.
The topmost section is the day in progress and links to `.../compare/v<previous date>...main`; when the next day's first entry is written, the finished day is tagged `v<date>` on GitHub and its link is rewritten to a tag-to-tag compare.
That keeps the "current" link useful at all times without a release ceremony, and the tag is created exactly once, from the day's last commit.

heylogs is the enforcement half — without it the file drifts into inconsistent headings and orphan link refs within a week, which is how changelogs die.
It is a Keep a Changelog linter with first-class CalVer support (`versioning=calver:YYYY-0M-0D`) and a `heylogs.properties` config file, so the local run (`jbang heylogs@nbbrd check CHANGELOG.md`) and the CI job share one rule set.
It runs via jbang — no build-system integration, nothing added to the Gradle classpath, no dependency in the shipped app.
Its `--format=github-actions` output annotates the offending lines in the PR.

`Unreleased` + SemVer was rejected because the version number would be arbitrary: nothing here is versioned against an API contract, and `v0.1` was a milestone tag, not a release train.
Generated changelogs were rejected because they need Conventional Commits, which conflicts with the existing convention that commit bodies carry the session narrative — and a generated list of commit subjects is the commit log with extra steps.

### Consequences

* Good, because the cost is one bullet per behavior-changing push, written by whoever (or whatever) made the change and knows why it matters.
* Good, because heylogs makes the format a build failure rather than a review comment; the same check runs locally and in CI.
* Bad, because the day-rollover ritual (tag + rewrite one link) is manual and easy to forget; the miss is cheap and self-correcting — the stale link still points at `main`.
* Neutral, because the pre-CalVer `v0.1` tag stays as-is; the first CalVer section links to `.../commits/main` since heylogs rejects a SemVer base ref under CalVer.
* Neutral, because jbang must be on PATH locally; CI installs it with `jbangdev/setup-jbang`.
