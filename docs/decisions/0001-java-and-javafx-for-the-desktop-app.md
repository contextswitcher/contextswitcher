---
status: accepted
date: 2026-07-12
decision-makers: Oliver Kopp
---

# Java 25 and JavaFX for the Desktop App

## Context and Problem Statement

ContextSwitcher needs a desktop UI (task list, switch actions, status feedback, later an embedded terminal) on Windows first and Linux (GNOME) later.
Which language and UI toolkit should the application use?

## Decision Drivers

* Maintainer expertise: the maintainer works on JabRef (Java/JavaFX) daily; review and debugging comfort is highest there.
* Cross-platform path to Linux/GNOME without a rewrite.
* Availability of an embeddable terminal widget for the planned Claude-chat mirror (v0.2).
* A hobby project resumed in bursts favors boring, stable tooling over novel stacks.

## Considered Options

* Java 25 (LTS) + JavaFX
* Tauri 2 (Rust backend + web UI, xterm.js terminal)
* C# / .NET (WinUI 3 or Avalonia)
* Python + Qt

## Decision Outcome

Chosen option: "Java 25 (LTS) + JavaFX", because maintainer expertise dominates for a burst-mode project, JavaFX is proven cross-platform, and JediTermFX/JediTerm plus pty4j provide a Java-native terminal path ([0007](0007-jeditermfx-for-the-embedded-terminal.md)).

Build: Gradle (Kotlin DSL), Java toolchain 25, package `com.contextswitcher`, JavaFX via `org.openjfx.javafxplugin`.

### Consequences

* Good, because every line of the app is in the maintainer's strongest ecosystem.
* Good, because the Linux port is a packaging problem, not a rewrite.
* Bad, because JavaFX distribution (jlink/jpackage) is more involved than a single-binary stack; explicitly out of scope until after v0.1 (`gradlew :app:run` is the delivery vehicle).
* Bad, because web-technology UI niceties (e.g. xterm.js) are not directly available.

## Pros and Cons of the Options

### Tauri 2

* Good, because xterm.js is the industry-proven embedded terminal.
* Good, because UI iteration in web tech is fast.
* Bad, because Rust + web stack is outside the maintainer's daily toolset — highest resumption cost.

### C# / .NET

* Good, because Windows-native look and tooling.
* Bad, because no expertise advantage, and Avalonia would be needed for GNOME later anyway.

### Python + Qt

* Good, because fast scripting of OS integration.
* Bad, because packaging desktop apps and long-term maintainability are weak points.
