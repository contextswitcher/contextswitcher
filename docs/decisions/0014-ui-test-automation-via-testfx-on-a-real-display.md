---
status: accepted
date: 2026-07-20
decision-makers: Oliver Kopp
---

# UI Test Automation via TestFX on JavaFX's Headless Glass Platform (Xvfb as Fallback)

Updated 2026-10-22: the tests moved from Xvfb to JavaFX 26's headless glass platform; the display-based recipe stays as fallback (below).

## Context and Problem Statement

The manual test checklist (`docs/manual-test.md`) keeps growing; many items need a real remote host or a human eye, but a growing share is pure UI behavior (dialog key chords, list filtering, sort modes) that a machine could check.
How do we automate UI-level tests for a JavaFX app so the checklist stops growing for pure-UI behavior, and the tests run repeatably on a headless Linux dev box and in CI?

## Considered Options

* TestFX driving the real app on a real display — Xvfb when headless (fx-labs fork; originally `org.testfx:testfx-junit5` 4.0.18)
* TestFX + Monocle headless glass platform (no display at all)
* TestFX (fx-labs fork) on JavaFX 26's built-in headless glass platform (`-Dglass.platform=headless`, no display, no extra artifact) — added 2026-10-22
* Hand-rolled harness on JavaFX's own `javafx.scene.robot.Robot` public API
* Agent-driven checks (Claude launches the app under Xvfb and inspects screenshots)
* Status quo: unit tests for extracted logic + manual checklist for everything else

## Decision Outcome

Chosen option (2026-07-20): **TestFX on a real display, Xvfb when headless**; since 2026-10-22 **the same TestFX tests on JavaFX 26's headless glass platform**, Xvfb kept as the fallback.
The fork stays: its robot is a thin layer over the public `javafx.scene.robot.Robot`, which the headless platform implements, so nothing needed replacing.
Original choice — since 2026-07-20 the maintained **fx-labs fork** (`io.gitlab.fx-labs:testfx-junit`, Oliver's pointer), which replaced the initial, barely-maintained `org.testfx:testfx-junit5` 4.0.18 the same day the harness landed.

TestFX is the de-facto JavaFX test framework: node lookup and robot input driving would otherwise be re-implemented by hand.
The fork (<https://gitlab.com/fx-labs/TestFX>) modernizes exactly the parts that were stale: current-JavaFX-era glass access via native calls instead of reflection, AssertJ instead of hamcrest (which 4.0.18 compiled against but did not even pull in), JUnit 6 extension (`@TestFxApplication` + injected `FX_ROBOT`) instead of the `ApplicationTest` base class — and it drops headless/Monocle and AWT-robot support, which this decision had already rejected anyway.
It pulls JUnit Jupiter 6, so the whole project moved from the JUnit 5.13 BOM to 6.x — a version bump only, all existing unit tests unchanged.
Verified working here empirically with JavaFX 26 / Java 25 under `xvfb-run` (bare Xvfb, no window manager needed).
Tests boot the real `Main` against a throwaway config dir (`-Dcontextswitcher.configDir`, own `tasksDir`, free WebSocket port, no remotes), so they exercise the app wiring, not extracted fragments.
Two traps encoded in the seed test (`AddTaskDialogUiTest`): the tasks dir must be non-empty or startup blocks in the modal clone-offer dialog, and each launch needs its own config dir/port because TestFX boots the app once per test method.

Mechanics: UI tests live in the normal test source set tagged `@Tag("ui")`; `test` excludes the tag (so `gradlew build` stays green anywhere, including headless), the new `:app:uiTest` task includes it.
Run `gradlew :app:uiTest` (= `just uitest`) on any OS, no display needed; CI runs it on `ubuntu-latest` on every push.
Fallback on a display: `just uitest-xvfb` (`xvfb-run -a -s "-screen 0 1920x1200x24" ./gradlew :app:uiTest -Dglass.platform=gtk`), or `-Dglass.platform=gtk`/`win` on a desktop.

### Headless glass platform (2026-10-22)

JavaFX 26 (JDK 24+) ships a pure-Java glass platform, `-Dglass.platform=headless`: a 1000x1000 screen rendered into a buffer, with its own `Robot`.
It needs no artifact beyond `javafx-graphics`, so the Monocle objection (artifacts trailing releases) is gone, and the suite no longer depends on `xvfb-run`, its distribution-dependent default screen size, or a desktop the robot could disturb.
Surveyed on swkgpu1 with the Xvfb-green suite of 128 tests: 113 passed as they were; the 15 failures came down to two quirks of the platform, which is a prototype in 26.
First, `HeadlessWindow._requestFocus` only tells the gaining window it has focus — the previous one never hears it lost it, and nothing hands focus back when a window closes (verified against the `jfx26` and `master` sources).
So after any dialog opened, two stages reported `isFocused()` and the fork's `print` refused to pick one (13 tests, one more depending on them).
`UiTestSupport.emulateWindowFocusChanges` patches this at the FX level with `WindowHelper.setFocused`: one focused stage at a time, the owner regains focus when a stage hides.
Popups are left alone — a popup and its stage both report focused on a real display too, and unfocusing the stage broke the suggestion and category-jump tests.
Second, `HeadlessRobot.mousePress` sends every press with `isPopupTrigger=true`; `RichTextArea`'s behavior ignores such a press, so a robot click no longer focuses the editor (`PlayFocusesTaskListUiTest` requests focus directly instead; `TextArea`/`TextField` clicks are unaffected).
Both deserve upstream reports; until fixed, the two workarounds stay and are the reason the Xvfb fallback stays documented.
The screen is a fixed 1000x1000 (no property): the 1100-wide main window hangs 50px off its left edge, which the robot's window-bounds hit test does not mind.
Caveat: the headless platform is marked a prototype; if it regresses in a later JavaFX, switch `glass.platform` back to the native platform and run on Xvfb — the tests themselves do not change.

Monocle headless was rejected (2026-07-20): the `openjfx-monocle` artifacts trail JavaFX releases (nothing matching JavaFX 26), and glass-level headless rendering is exactly the fragile part — while Xvfb ships with every Linux distro and CI image, and Windows/desktop runs need no display substitute at all.
JavaFX 26's own headless platform (above) removed the artifact problem; the fragility showed as the two quirks listed, both small enough to patch test-side.
A hand-rolled `javafx.scene.robot.Robot` harness would avoid the dependency but re-implements lookup and event-settling, the error-prone core; TestFX is test-scope only (EUPL-1.2, weak copyleft — acceptable per the license policy, and it never ships with the app).
Agent-driven screenshot checks are not repeatable as a regression net — no assertion artifact survives the session; the agent's place is writing and running these tests, plus visual one-offs.
The status quo is what produced the 700-line checklist.

What stays manual: anything needing the real remote/tmux/Claude chain, JetBrains Gateway, Firefox, Windows virtual desktops, or a human judgment of rendering.
The checklist header says so; automated items are marked *(automated: `<TestClass>`)* there.

### Consequences

* Good, because pure-UI regressions (key chords, dialogs, filters) get caught by `:app:uiTest` instead of a human replaying a checklist.
* Good, because since 2026-10-22 the suite needs no display anywhere — one command on every OS, nothing on the developer's desktop is clicked, no `xvfb-run` screen-size trap.
* Bad, because the headless platform is a JavaFX 26 prototype with a fixed 1000x1000 screen and two patched quirks (focus bookkeeping, popup-trigger presses); the Xvfb recipe stays as fallback.
* Good, because the tests run the real `Main` — startup wiring (settings load, watcher, seeding) is covered for free.
* Bad, because the fork is young (0.2.0, created late 2025) with a small maintainer base; if it stalls or breaks, the fallback is the same tests on `javafx.scene.robot.Robot` with a small local harness.
* Neutral, because the fork ties the test classpath to JUnit 6 — fine today (drop-in for our 5-style tests), a constraint if anything else ever pins JUnit 5.
* Bad, because robot tests are timing-sensitive by nature; keep them few, coarse, and polling-based (no fixed sleeps) — the fine-grained logic stays in plain unit tests per the existing convention.
* Neutral, because UI tests are not part of `build`: they run on demand and in the Linux CI job, not in every local build.
