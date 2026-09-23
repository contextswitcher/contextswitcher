---
status: accepted
date: 2026-07-23
decision-makers: Oliver Kopp
---

# No GraalVM Native Image; jpackage App Image Stays the Distribution

## Context and Problem Statement

Could ContextSwitcher ship as a GraalVM native-image binary instead of the jpackage app-image (MADR 0012) — for faster startup and a single native executable?
MADR 0012 already rejected native-image in one line ("JavaFX + reflection-heavy dependencies make it a project of its own"), but that was the *packaging* decision and predates the embedded terminal (MADR 0007) and the AWT-based URL launching (`onenote:`, `jetbrains-gateway://`, `contextswitcher://` — MADR 0016).
The two decisive blockers today are therefore unrecorded; this record supersedes 0012's native-image sentence and captures the real reasoning, so the question does not get re-opened from scratch.

## Considered Options

* jpackage `--type app-image` with a bundled jlink'ed JRE (status quo, MADR 0012)
* GraalVM native-image via the Gluon `gluonfx` plugin (SubstrateVM + Gluon's JavaFX static libraries)

Stock OpenJFX does not AOT-compile under native-image, so "GraalVM" here necessarily means the Gluon toolchain, not a plain `native-image` flag on the existing build.

## Decision Outcome

Chosen: **keep the jpackage app-image; do not pursue GraalVM native-image.**

The payoff is marginal and the cost is a parallel, fragile build toolchain fighting three of the app's own dependencies:

* **pty4j** (`org.jetbrains.pty4j:pty4j`, the embedded terminal, MADR 0007) loads native pty helper binaries and uses reflection/JNA. Native binary loading is among the hardest cases under SubstrateVM — a spike of its own, and it guts the v0.2 terminal if it fails.
* **`java.awt.Desktop`** (`Main.openUrl`: `Desktop.browse` → `HostServices.showDocument`) opens every external URL — notes, Gateway, deep links. AWT is barely supported under SubstrateVM and would have to be rewritten to raw `ShellExecute` / `xdg-open`.
* **Jackson + SnakeYAML** are reflection-heavy: tractable with reachability metadata, but every serialized type needs config and a missed one fails silently at runtime.

Against that: a JavaFX native binary still bundles the JavaFX + graphics runtime, so it lands in the same ~50–80 MB range as today's app-image — no meaningful size win. The only real gain is startup latency, which does not matter for a desktop app opened once and left running. Native-image also cannot cross-compile (same CI-per-OS constraint as jpackage) while building far slower and more memory-hungrily.
jpackage already delivers what testers needed: no JDK, unzip and double-click.

Revisit only if startup latency becomes a real complaint **and** pty4j and the AWT URL path have been removed or replaced; the entry point is then a throwaway spike (JavaFX + Jackson/SnakeYAML compiling native, no pty4j, no AWT) before any commitment.

### Consequences

* Good, because it keeps one build toolchain (jpackage, already on every build machine) instead of adding Gluon/SubstrateVM.
* Good, because the strongest blockers (pty4j, AWT) are now on record, so the decision is not re-derived each time native-image comes up.
* Neutral, because startup stays JVM-paced (a few seconds); acceptable for a long-running desktop app.
* Bad, because a single self-contained native `.exe` (no JRE folder) is deferred; the app-image zip remains the shape.
