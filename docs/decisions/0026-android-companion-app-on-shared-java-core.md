---
status: accepted
date: 2026-09-07
decision-makers: Oliver Kopp
---

# Android Companion App on a Shared Plain-Java Core

## Context and Problem Statement

ContextSwitcher should also run on a phone — the desktop's three columns (task list, terminal mirror, editor + queue) as swipeable pages with Android navigation.
JavaFX does not run on Android; how much of the code base carries over, and does the project need Kotlin (Multiplatform)?

Measured coupling (2026-08-27): ~11.7k LOC are JavaFX-bound (`ui/`, `Main`, `spike/`) and would be new screens on any toolkit; ~11k LOC (`tasks/`, `queue/`, `config/`, `ssh/`, `discovery/`, remote half of `switching/`, terminal command builders) are plain Java whose only JavaFX leak is `TaskRepository`'s observable collection types.

## Decision Drivers

* The phone must work when the desktop is off — reading task state and sending Claude a message from the couch is the use case.
* One-man project, strongest ecosystem is Java (MADR 0001); no iOS target.
* Proprietary license — no GPL dependencies (Termux's terminal view is out, for example).

## Considered Options

* Standalone Android app (Kotlin/Compose UI) depending on the existing Java core as a plain library module
* Thin client: phone talks WebSocket to the *running* desktop app (extending the extension server, MADR 0004)
* Kotlin Multiplatform / Compose Multiplatform rewrite
* JavaFX on Android via GluonFX (GraalVM)

## Decision Outcome

Chosen option: "Standalone Android app on the shared Java core", because it works without the desktop running, and Android consumes plain-Java modules directly — Kotlin is needed only for the Compose UI layer, which is new code either way.
Scoped platform exceptions: sshj instead of system ssh ([0027](0027-sshj-for-ssh-on-android.md)), JGit instead of system git ([0028](0028-jgit-task-repo-clone-on-android.md)).

### Consequences

* Good, because the parsing/protocol/command-building core stays one code base with one set of unit tests.
* Good, because the desktop app is untouched apart from a `:core`/`:app` module split.
* Bad, because the app must now manage SSH keys itself on Android — `~/.ssh/config`, agent and ControlMaster (MADR 0003's rationale) do not exist there.
* Bad, because the task directory gains a second writer (phone via git backup); read-only on the phone until `TaskMerge` proves the write-back.
* Bad, because core code must stay inside Android's API budget: no `java.net.http`, no virtual threads, no `SequencedCollection` methods (ART is OpenJDK-17-level).

## Pros and Cons of the Options

### Thin WebSocket client of the running desktop app

* Good, because it is the smallest app: no ssh, no git, no Java-version constraints on the phone.
* Good, because the desktop stays the single writer of task files.
* Bad, because it only works while the desktop runs and is reachable — rejected for exactly the couch/travel case the phone is for.

### Kotlin Multiplatform / Compose Multiplatform

* Good, because one UI could serve desktop and phone.
* Bad, because `commonMain` must be Kotlin: the 11k-LOC Java core would be rewritten, the working JavaFX UI discarded, and no Compose terminal widget exists.
* Bad, because it only pays off for an iOS target, which there is none.

### GluonFX (JavaFX on Android)

* Good, because the toolkit would carry over.
* Bad, because every MADR 0018 blocker (pty4j, AWT, reflection-heavy Jackson/SnakeYAML under SubstrateVM) applies, and the phone screens are new regardless — the carry-over buys almost nothing.
