---
status: accepted
date: 2026-07-20
decision-makers: Oliver Kopp
---

# UI Test Automation via TestFX on a Real (or Xvfb) Display

## Context and Problem Statement

The manual test checklist (`docs/manual-test.md`) keeps growing; many items need a real remote host or a human eye, but a growing share is pure UI behavior (dialog key chords, list filtering, sort modes) that a machine could check.
How do we automate UI-level tests for a JavaFX app so the checklist stops growing for pure-UI behavior, and the tests run repeatably on a headless Linux dev box and in CI?

## Considered Options

* TestFX driving the real app on a real display — Xvfb when headless (fx-labs fork; originally `org.testfx:testfx-junit5` 4.0.18)
* TestFX + Monocle headless glass platform (no display at all)
* Hand-rolled harness on JavaFX's own `javafx.scene.robot.Robot` public API
* Agent-driven checks (Claude launches the app under Xvfb and inspects screenshots)
* Status quo: unit tests for extracted logic + manual checklist for everything else

## Decision Outcome

Chosen option: **TestFX on a real display, Xvfb when headless** — since 2026-07-20 the maintained **fx-labs fork** (`io.gitlab.fx-labs:testfx-junit`, Oliver's pointer), which replaced the initial, barely-maintained `org.testfx:testfx-junit5` 4.0.18 the same day the harness landed.

TestFX is the de-facto JavaFX test framework: node lookup and robot input driving would otherwise be re-implemented by hand.
The fork (<https://gitlab.com/fx-labs/TestFX>) modernizes exactly the parts that were stale: current-JavaFX-era glass access via native calls instead of reflection, AssertJ instead of hamcrest (which 4.0.18 compiled against but did not even pull in), JUnit 6 extension (`@TestFxApplication` + injected `FX_ROBOT`) instead of the `ApplicationTest` base class — and it drops headless/Monocle and AWT-robot support, which this decision had already rejected anyway.
It pulls JUnit Jupiter 6, so the whole project moved from the JUnit 5.13 BOM to 6.x — a version bump only, all existing unit tests unchanged.
Verified working here empirically with JavaFX 26 / Java 25 under `xvfb-run` (bare Xvfb, no window manager needed).
Tests boot the real `Main` against a throwaway config dir (`-Dcontextswitcher.configDir`, own `tasksDir`, free WebSocket port, no remotes), so they exercise the app wiring, not extracted fragments.
Two traps encoded in the seed test (`AddTaskDialogUiTest`): the tasks dir must be non-empty or startup blocks in the modal clone-offer dialog, and each launch needs its own config dir/port because TestFX boots the app once per test method.

Mechanics: UI tests live in the normal test source set tagged `@Tag("ui")`; `test` excludes the tag (so `gradlew build` stays green anywhere, including headless), the new `:app:uiTest` task includes it.
Run `gradlew :app:uiTest` on a desktop, `xvfb-run -a ./gradlew :app:uiTest` headless; CI runs the Xvfb variant on `ubuntu-latest` on every push.

Monocle headless was rejected: the `openjfx-monocle` artifacts trail JavaFX releases (nothing matching JavaFX 26), and glass-level headless rendering is exactly the fragile part — while Xvfb ships with every Linux distro and CI image, and Windows/desktop runs need no display substitute at all.
A hand-rolled `javafx.scene.robot.Robot` harness would avoid the dependency but re-implements lookup and event-settling, the error-prone core; TestFX is test-scope only (EUPL-1.2, weak copyleft — acceptable per the license policy, and it never ships with the app).
Agent-driven screenshot checks are not repeatable as a regression net — no assertion artifact survives the session; the agent's place is writing and running these tests, plus visual one-offs.
The status quo is what produced the 700-line checklist.

What stays manual: anything needing the real remote/tmux/Claude chain, JetBrains Gateway, Firefox, Windows virtual desktops, or a human judgment of rendering.
The checklist header says so; automated items are marked *(automated: `<TestClass>`)* there.

### Consequences

* Good, because pure-UI regressions (key chords, dialogs, filters) get caught by `xvfb-run … :app:uiTest` instead of a human replaying a checklist.
* Good, because the tests run the real `Main` — startup wiring (settings load, watcher, seeding) is covered for free.
* Bad, because the fork is young (0.2.0, created late 2025) with a small maintainer base; if it stalls or breaks, the fallback is the same tests on `javafx.scene.robot.Robot` with a small local harness.
* Neutral, because the fork ties the test classpath to JUnit 6 — fine today (drop-in for our 5-style tests), a constraint if anything else ever pins JUnit 5.
* Bad, because robot tests are timing-sensitive by nature; keep them few, coarse, and polling-based (no fixed sleeps) — the fine-grained logic stays in plain unit tests per the existing convention.
* Neutral, because UI tests are not part of `build`: they run on demand and in the Linux CI job, not in every local build.
