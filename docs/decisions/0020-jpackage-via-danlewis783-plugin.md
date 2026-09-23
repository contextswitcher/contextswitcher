---
status: accepted
date: 2026-07-23
decision-makers: Oliver Kopp
---

# jpackage via the danlewis783 Plugin, Not Hand-Written and Not gradlex java-module-packaging

## Context and Problem Statement

Packaging ran through 25 hand-written lines in `app/build.gradle.kts`: an `Exec` task deleting the previous image and calling `jpackage --type app-image` (MADR 0012).
It has no declared inputs or outputs, so it re-ran on every invocation and cleared the image itself — and `File.deleteRecursively()` reports a locked file only in its return value, which was discarded.
The visible symptom was `Error: Application destination directory …\jpackage\ContextSwitcher already exists` on a `just run` while the previously launched app image was still running.
Which plugin should take over the jpackage call?

## Considered Options

* Hand-written `jpackage` `Exec` task (status quo, MADR 0012)
* [`org.gradlex.java-module-packaging`](https://github.com/gradlex-org/java-module-packaging) 1.3
* [`io.github.danlewis783.jpackage`](https://github.com/danlewis783/jpackage-plugin) 0.1.0

## Decision Outcome

Chosen: **`io.github.danlewis783.jpackage`**, because it is the only option that packages a *classpath* application and hands the image directory to Gradle as a task output.

`org.gradlex.java-module-packaging` is out for a structural reason, not a preference: it packages **Java Modules** only (`jpackage --module` over a jlink'ed module path), while this app is deliberately non-modular — JavaFX and everything else sit on the classpath, which is why `--add-modules java.se` and `--enable-native-access=ALL-UNNAMED` exist at all (MADR 0001).
Adopting it means modularizing the app first: a `module-info.java`, `org.gradlex.extra-java-module-info` for **pty4j** (no descriptor, no `Automatic-Module-Name`) and **jna** + **jna-platform** (automatic modules, which jlink rejects) — the only 3 of 27 runtime jars that are not already modules — and a module-path-aware setup for the 76 test classes plus the TestFX UI tests (MADR 0014).
pty4j is the risk, the same dependency that blocked GraalVM in MADR 0018.
Its payoff — installers, per-target packaging, and replacing the hand-rolled host-OS/arch attribute forcing — is not needed while MADR 0012 only asks for a zipped app image, and it cannot cross-build either (target tasks run only on a matching runner, so `build.yml`'s `windows-latest` job stays regardless).

The chosen plugin is a drop-in: it packages the `jar` plus the whole `runtimeClasspath`, needs no `application` plugin, and maps one-to-one onto the flags the `Exec` task passed (`javaOptions`, `addModules`, `appName`, `mainClass`).
What it adds over the hand-written task:

* `jpackageImage` **owns `build/jpackage/image`**, so a stale image is deleted by Gradle before the tool runs — the reported failure cannot recur. A genuinely locked file now fails with Gradle's own `Unable to delete directory … a process has files open` naming the offending files, instead of jpackage's misleading "already exists" (both verified).
* Declared inputs/outputs: unchanged sources leave it `UP-TO-DATE` instead of re-jlinking a ~120 MB image every time, and the build is Configuration-Cache-clean (verified with `--configuration-cache`).
* `jpackageInstaller` (MSI/EXE, WiX) is there when the zip is no longer enough — the reason the *other* plugin looked attractive, without modularizing anything.

The price: a 0.1.0, three-week-old, single-author plugin with no adoption (Apache-2.0, on the Plugin Portal, requires Gradle 9.6+ — the wrapper is 9.6.1).
Acceptable because it is a build-time-only dependency that wraps a JDK tool: if it stalls, the replacement is the `Exec` task it deleted, recoverable from this record's git history.

One sharp edge, hit and fixed during the switch: `jpackageJdkVersion` defaults to *the JDK running Gradle*, not the project toolchain, and jpackage jlinks the bundled runtime from that JDK.
With Gradle on 21, the build stayed green and produced an image that died at launch with `UnsupportedClassVersionError` (class file 69 on a Java 21 runtime).
It is now bound to `java.toolchain.languageVersion`.

### Consequences

* Good, because the image directory is a build output like any other: no hand-written delete, no "already exists", up-to-date checks, config-cache support.
* Good, because installers are one `installer { }` block away if the zip stops being enough.
* Neutral, because the image moved from `build/jpackage/` to `build/jpackage/image/` and the task from `:app:jpackage` to `:app:jpackageImage` (`justfile`, `README.md`, the registration scripts' fallback path follow); `packageApp` still builds the zip, since the plugin's own `jpackageZip` cannot place the registration scripts *beside* the image.
* Bad, because the build now depends on an unproven 0.1.0 plugin.
* Bad, because multi-target packaging and the host-OS attribute forcing stay hand-rolled; that is what java-module-packaging would fix, and it stays available at the price of modularizing the app.
