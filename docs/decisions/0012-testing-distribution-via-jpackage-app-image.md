---
status: accepted
date: 2026-07-17
decision-makers: Oliver Kopp
---

# Testing Distribution via jpackage App Image and Unsigned XPI

## Context and Problem Statement

Testers should be able to run ContextSwitcher on Windows without installing a JDK, and install the Firefox extension without loading a directory in `about:debugging`.
How do we package the app as a self-contained executable and the extension as an installable file, with minimal build machinery?

## Considered Options

* jpackage `--type app-image`, zipped (JDK's own packager, bundled jlink'ed JRE)
* jpackage `--type exe` installer (same, plus WiX, produces a single installer `.exe`)
* GraalVM native-image (single native `.exe`)
* Plain jlink runtime + start scripts (hand-rolled layout)

For the extension: an unsigned `.xpi` zip vs. AMO-signed distribution.

## Decision Outcome

Chosen option: **jpackage app-image, zipped**, built by the existing `windows-latest` CI job and uploaded as a workflow artifact together with an **unsigned `.xpi`** of the extension.

jpackage ships with the JDK we already build on — no new dependency, no license question.
The app image contains `ContextSwitcher.exe` plus a jlink'ed JRE (`java.se,jdk.unsupported`; the app stays non-modular with JavaFX on the classpath, so no module surgery): unzip and double-click, nothing to install, nothing touching the registry — right for test builds that change often.
jpackage cannot cross-build, so the Windows artifact is produced by CI (`build.yml` already runs on `windows-latest`); `gradlew :app:packageApp` produces the same image for whatever OS it runs on.

An installer `.exe` is deliberately deferred: it is `--type exe` plus WiX on the build machine, one flag away, and only worth it once there is something to install permanently (file associations, the `contextswitcher://` handler of [#47](https://github.com/contextswitcher/contextswitcher-private/issues/47) — which will also need the stable install path an installer provides).
GraalVM native-image was rejected for now: JavaFX + reflection-heavy dependencies (SnakeYAML, Jackson) make it a project of its own, for a startup-time benefit testers do not need — this line is superseded by MADR 0018, which records the full reasoning (pty4j and AWT are the decisive blockers).
Hand-rolled jlink + scripts is jpackage minus the launcher, so strictly worse.

The `.xpi` is a plain zip of `extension/firefox/` (Gradle `Zip` task, `gradlew packageExtension`).
It stays unsigned: release Firefox refuses permanent installs of unsigned extensions, but testing installs work via `about:debugging` (load the `.xpi` as temporary add-on) or permanently on Firefox ESR/Developer Edition/Nightly with `xpinstall.signatures.required=false`.
AMO signing (even unlisted) is a submission pipeline with review latency — added when distribution goes beyond testers.

### Consequences

* Good, because zero new build dependencies: jpackage and `Zip` are already on every build machine.
* Good, because every green CI run yields downloadable testing artifacts — no separate release process.
* Bad, because the zip is ~50–80 MB (bundled JRE); acceptable for testing builds.
* Bad, because unzip-and-run has no stable install path yet — the `contextswitcher://` URL handler ([#47](https://github.com/contextswitcher/contextswitcher-private/issues/47)) stays blocked until an installer exists.
* Neutral, because the unsigned `.xpi` needs a signature-lenient Firefox for permanent install; temporary install works everywhere.
