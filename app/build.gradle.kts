import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily

plugins {
    application
    id("org.gradlex.jvm-dependency-conflict-resolution") version "2.5"
    id("io.github.danlewis783.jpackage") version "0.1.0"
}

version = "0.2.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    // ShellFX 2.x (the main window's shell, MADR 0032) exists only as
    // snapshots in techsenger's repsy repository; restricted to that group so
    // nothing else can resolve from it.
    maven("https://repo.repsy.io/mvn/techsenger/snapshots") {
        mavenContent {
            snapshotsOnly()
            includeGroupByRegex("com\\.techsenger(\\..*)?")
        }
    }
}

// JavaFX loading as in JabRef (build-logic dependency-rules): the plain Maven
// jars are empty; patch target-platform variants onto the modules so Gradle
// picks the right classified jar. The incubator modules (RichTextArea) have no
// support in org.openjfx.javafxplugin, hence this route.
// javafx-swing: pulled in by shellfx-core.
listOf("javafx-base", "javafx-controls", "javafx-graphics", "javafx-swing",
        "jfx-incubator-input", "jfx-incubator-richtext").forEach { jfxModule ->
    addJfxTarget(jfxModule, "", "none", "none") // matches the empty jars: to get better errors
    addJfxTarget(jfxModule, "linux", OperatingSystemFamily.LINUX, MachineArchitecture.X86_64)
    addJfxTarget(jfxModule, "linux-aarch64", OperatingSystemFamily.LINUX, MachineArchitecture.ARM64)
    addJfxTarget(jfxModule, "mac", OperatingSystemFamily.MACOS, MachineArchitecture.X86_64)
    addJfxTarget(jfxModule, "mac-aarch64", OperatingSystemFamily.MACOS, MachineArchitecture.ARM64)
    addJfxTarget(jfxModule, "win", OperatingSystemFamily.WINDOWS, MachineArchitecture.X86_64)
}

fun addJfxTarget(jfxModule: String, name: String, os: String, arch: String) {
    jvmDependencyConflicts.patch.module("org.openjfx:$jfxModule") {
        addTargetPlatformVariant(name, os, arch)
    }
}

// Resolve for the host platform (packaging for other targets comes later)
val hostOs: String = System.getProperty("os.name").lowercase().let {
    when {
        it.contains("windows") -> OperatingSystemFamily.WINDOWS
        it.contains("mac") -> OperatingSystemFamily.MACOS
        else -> OperatingSystemFamily.LINUX
    }
}
val hostArch: String = when (System.getProperty("os.arch")) {
    "aarch64" -> MachineArchitecture.ARM64
    else -> MachineArchitecture.X86_64
}
configurations.configureEach {
    if (isCanBeResolved) {
        attributes {
            attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named(hostOs))
            attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named(hostArch))
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation("org.openjfx:javafx-controls:26.0.2")
    implementation("org.openjfx:jfx-incubator-richtext:26.0.2")
    // Spike https://github.com/contextswitcher/contextswitcher-private/issues/34 / MADR 0007: embedded terminal. JediTermFX is dual-licensed
    // LGPLv3/Apache-2.0 — we elect Apache-2.0; pty4j is EPL-1.0.
    implementation("com.techsenger.jeditermfx:jeditermfx-core:1.1.0")
    implementation("com.techsenger.jeditermfx:jeditermfx-ui:1.1.0")
    implementation("org.jetbrains.pty4j:pty4j:0.13.12")

    implementation("io.github.mkpaz:atlantafx-base:2.1.0")
    // Info center for background-work notifications (MADR 0034). Apache-2.0;
    // pulls ikonli, jsvg, pickerfx, validatorfx (all Apache-2.0).
    implementation("com.dlsc.gemsfx:gemsfx:4.4.5")
    // ShellFX application shell hosting the main window's panes (MADR 0032).
    // Pinned to one timestamped snapshot build, and so are its transitive
    // techsenger -SNAPSHOT dependencies (the constraints below, the builds of
    // the same day): floating, PatternFX moved its ports to another package on
    // 2026-09-18 and the pinned ShellFX stopped compiling against it.
    // Bump all of them together.
    implementation("com.techsenger.shellfx:shellfx-core:2.0.0-20260909.144252-100")
    implementation("com.techsenger.shellfx:shellfx-layout:2.0.0-20260909.144252-99")
    implementation("com.techsenger.shellfx:shellfx-material:2.0.0-20260909.144252-99")
    implementation("com.techsenger.shellfx:shellfx-icons:2.0.0-20260909.144252-99")
    constraints {
        listOf(
            "com.techsenger.shellfx:shellfx-shared:2.0.0-20260909.144252-100",
            "com.techsenger.patternfx:patternfx-core:2.0.0-20260909.092200-71",
            "com.techsenger.patternfx:patternfx-mvp:2.0.0-20260909.092200-43",
            "com.techsenger.patternfx:patternfx-mvvm:2.0.0-20260909.092200-51",
            "com.techsenger.annotations:annotations:1.0.0-20260317.165229-5",
            "com.techsenger.tabpanepro:tabpanepro-core:2.0.0-20260829.195212-3",
            "com.techsenger.toolkit:toolkit-core:1.2.0-20260731.193956-22",
            "com.techsenger.toolkit:toolkit-fx:1.2.0-20260731.193956-22",
        ).forEach { pinned ->
            implementation(pinned) { version { strictly(pinned.substringAfterLast(':')) } }
        }
    }
    // SVG icons (MADR 0010) — unicode symbol glyphs do not render in JavaFX
    // on Windows
    implementation("tools.maran:svgnode:2.0.0")
    implementation("tools.maran:svg-materialdesign:1.0.0")
    implementation("org.jspecify:jspecify:1.0.1")
    implementation("org.yaml:snakeyaml:2.7")
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")

    implementation("org.tinylog:tinylog-api:2.8.0")
    runtimeOnly("org.tinylog:tinylog-impl:2.8.0")
    // Bridge for third-party libraries speaking SLF4J (e.g. Java-WebSocket)
    runtimeOnly("org.tinylog:slf4j-tinylog:2.8.0")

    // JUnit 6: required by the TestFX fork below; plain 5-style Jupiter API.
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // UI automation (MADR 0014): drives the real app on a display — a real
    // desktop, or Xvfb on headless Linux (`xvfb-run -a ./gradlew :app:uiTest`).
    // fx-labs fork of TestFX (maintained, JavaFX-25-era, AssertJ instead of
    // hamcrest). EUPL-1.2 (weak copyleft), test-scope only.
    testImplementation("io.gitlab.fx-labs:testfx-junit:0.2.0")
}

// One raster of the target-rings mark for the whole product: the Chrome
// extension's icon is copied into the app jar next to `AppIcon`, which loads
// it for the window and the Settings dialog. [impl->dsn~app-icon~4]
tasks.processResources {
    from(rootProject.file("extension/chrome/icon-128.png")) {
        into("com/contextswitcher/ui")
        rename { "icon.png" }
    }
}

application {
    mainClass = "com.contextswitcher.Launcher"
    // JavaFX loads its native libraries (glass, prism) via System.load from
    // the classpath (unnamed module); Java 25 warns on such restricted native
    // access and will block it in a future release (JEP 472) unless granted.
    // The "Unsupported JavaFX configuration" warning itself is inherent to the
    // deliberate classpath loading (incubator modules, see above) and stays.
    // The console log writer prints through System.out, whose charset is the
    // platform's (`stdout.encoding`) — on Windows a legacy code page, in which
    // the em dashes and arrows our log messages use cannot be written. Pinning
    // both streams to UTF-8 makes what the app prints match what it writes to
    // the rolling log file, so a line copied out of a terminal is the line the
    // file holds (field report 2026-09-12: an em dash arriving as "´┐¢").
    // A terminal still has to *read* UTF-8 — `chcp 65001` on a legacy Windows
    // console — but that is the reader's half, and this is ours.
    // -Djavafx.enablePreview: ShellFX's window needs JavaFX's HeaderBar preview
    // API (MADR 0032); jpackage takes these arguments over for the packaged app.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED",
            "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Djavafx.enablePreview=true")
}

// `gradlew run` is the from-source development launch, and it cannot restart the
// app the way `just run-loop` does — but it should at least say so. The
// "Restart to update" button exits with 55 (`AppUpdate.RESTART_EXIT_CODE`), which
// JavaExec otherwise reports as `finished with non-zero exit value 55` on top of a
// red BUILD FAILED, giving no hint that the app did exactly what it was told
// (field report 2026-09-12).
tasks.named<JavaExec>("run") {
    isIgnoreExitValue = true
    doLast {
        // getOrElse, not get: a missing result must not break `gradlew run` itself.
        val code = executionResult.map { it.exitValue }.getOrElse(0)
        if (code == 55) {
            logger.lifecycle("")
            // Same words as `AppUpdate.actionHint(false)`, the update window's line.
            logger.lifecycle("The app quit for an update (exit 55) and does not come back by itself —")
            logger.lifecycle("use `just run-loop`, which pulls, rebuilds and starts it again.")
        } else if (code != 0) {
            throw GradleException("The app exited with $code")
        }
    }
}

// A failing test must be diagnosable from the console alone: CI uploads no test
// report (artifact budget), so Gradle's default one-line
// "java.lang.AssertionError at FooTest.java:117" is all a red build leaves
// behind — and an assertion's *message* is exactly where its diagnosis lives.
fun Test.logFailuresInFull() {
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

tasks.test {
    // UI tests (@Tag("ui")) need a display and boot the whole app — kept out
    // of `build` so it stays green headless; run them via :app:uiTest.
    useJUnitPlatform {
        excludeTags("ui")
    }
    logFailuresInFull()
}

// UI automation (MADR 0014): TestFX tests driving the real app against a temp
// config dir. Needs a display: any desktop, or `xvfb-run -a ./gradlew :app:uiTest`.
tasks.register<Test>("uiTest") {
    group = "verification"
    description = "Runs the TestFX UI tests (needs a display or Xvfb)"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("ui")
    }
    // The tests boot the real main window, whose ShellFX shell needs the
    // HeaderBar preview API, as the app does (QueueTabFocusUiTest and every other UI test).
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Djavafx.enablePreview=true")
    // The app's remembered state (filters, sort, model/effort pick) goes through
    // Java Preferences; an in-memory store keeps the tests off the developer's
    // own on every OS (a `userRoot` redirect does not reach the Windows registry).
    jvmArgs("-Djava.util.prefs.PreferencesFactory=com.contextswitcher.ui.InMemoryPreferencesFactory")
    // Headless AWT (JavaFX does not need it) makes `Desktop.isDesktopSupported()`
    // false, so no test click starts the machine's real file manager or URL
    // handler. Such a child inherits the test JVM's output pipe and outlives
    // it, and Gradle then waits for the pipe to close after the last test —
    // the six-hour CI hangs.
    systemProperty("java.awt.headless", "true")
    // Every test class boots the whole app in this one JVM; at Gradle's 512 MB
    // default the suite ran out of memory (it peaks above 1 GB) and failed at
    // random.
    maxHeapSize = "2g"
    logFailuresInFull()
}

// Forward -Dcontextswitcher.* from the Gradle invocation to the app JVM
// (e.g. -Dcontextswitcher.configDir=<dir> to run against a test config).
tasks.named<JavaExec>("run") {
    System.getProperties().stringPropertyNames()
        .filter { it.startsWith("contextswitcher.") }
        .forEach { systemProperty(it, System.getProperty(it)) }
}

// Self-contained testing build (MADR 0012): jpackage app image with a bundled
// JRE for the host OS, zipped by packageApp. jpackage cannot cross-build, so
// the Windows artifact comes from the windows-latest CI run (build.yml).
// The jpackage call itself is the io.github.danlewis783.jpackage plugin's
// (MADR 0020): it packages the runtimeClasspath, and owns build/jpackage/image
// as a task output — so a stale image is Gradle's problem, not ours.
jpackage {
    appName = "ContextSwitcher"
    mainClass = application.mainClass
    // The same JVM options the `run` task and the start scripts use — the
    // packaged launcher is how the app is actually started (`just run`), so a
    // second, drifting list here would mean the zip behaves unlike the
    // development run.
    javaOptions = application.applicationDefaultJvmArgs.toList()
    // Must be the project toolchain, not the JDK running Gradle: jpackage jlinks
    // the bundled runtime from its own JDK, so a Gradle on 21 would silently
    // package a Java 21 runtime that cannot load our Java 25 classes.
    jpackageJdkVersion = java.toolchain.languageVersion.map { it.asInt() }
    // Non-modular app (JavaFX on the classpath, see above): jlink a plain JRE;
    // jdk.unsupported for Unsafe (prism, pty4j); jdk.management for the
    // extended OperatingSystemMXBean (MemoryLog) — not part of java.se.
    addModules = listOf("java.se", "jdk.unsupported", "jdk.management")
}
val jpackageImage = tasks.named("jpackageImage")

tasks.register<Zip>("packageApp") {
    group = "distribution"
    description = "Zips the jpackage app image for distribution"
    from(jpackageImage)
    // Protocol registration next to the app image (https://github.com/contextswitcher/contextswitcher-private/issues/47): the scripts default
    // to the ContextSwitcher/ directory beside them.
    from(rootProject.layout.projectDirectory.dir("scripts"))
    archiveFileName = "ContextSwitcher-$version-$hostOs.zip"
}

// One command to test deep links (https://github.com/contextswitcher/contextswitcher-private/issues/47) from a source checkout without the CI
// zip: build the host-OS app image and register the contextswitcher:// handler
// against that exe. The registration scripts are reused (the exe path is passed
// explicitly), so this and the packaged zip register identically.
tasks.register<Exec>("registerUrlHandler") {
    group = "distribution"
    description = "Builds the app image and registers the contextswitcher:// URL handler (current user)"
    dependsOn(jpackageImage)
    val image = layout.buildDirectory.dir("jpackage/image/ContextSwitcher").get()
    val scripts = rootProject.layout.projectDirectory.dir("scripts")
    if (hostOs == OperatingSystemFamily.WINDOWS) {
        commandLine("cmd", "/c",
            scripts.file("register-url-handler.cmd").asFile.absolutePath,
            image.file("ContextSwitcher.exe").asFile.absolutePath)
    } else {
        commandLine("sh",
            scripts.file("register-url-handler.sh").asFile.absolutePath,
            image.file("bin/ContextSwitcher").asFile.absolutePath)
    }
}

// Spike https://github.com/contextswitcher/contextswitcher-private/issues/34: `gradlew :app:spikeTerminal [-Dspike.remote=user@host] [-Dspike.session=0]`
tasks.register<JavaExec>("spikeTerminal") {
    group = "application"
    description = "Runs the JediTerm + pty4j embedded-terminal spike (#34, MADR 0007)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.contextswitcher.spike.SpikeLauncher"
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    System.getProperties().stringPropertyNames()
        .filter { it.startsWith("spike.") }
        .forEach { systemProperty(it, System.getProperty(it)) }
}
