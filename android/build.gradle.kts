// Android dex spike: proves D8 dexes :core's records,
// sealed interfaces, and pattern-matching switches. Not real UI (P4).
// AGP 9's built-in Kotlin support compiles the Kotlin sources below without
// applying org.jetbrains.kotlin.android; the Compose compiler still needs
// its own plugin (Kotlin 2.3.21, matching the Kotlin embedded in Gradle
// 9.6.1 and above AGP 9.4's "2.3.0+ for best experience" line).
//
// P4 (task list, docs/decisions/0028-jgit-task-repo-clone-on-android.md)
// adds JGit for the read-only repo sync and a JUnit5/Robolectric unit-test
// stack (no device/emulator on this box, so `:android:testDebugUnitTest` is
// the only local verification).
plugins {
    id("com.android.application") version "9.4.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
}

// The commit this APK is built from, compared by the in-app update hint with
// the commit the `android-dev` pre-release's tag points at. "unknown" outside
// a git checkout (then the app never offers an update).
val gitCommit: String = runCatching {
    providers.exec {
        commandLine("git", "rev-parse", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrDefault("").ifEmpty { "unknown" }

// The line the app shows for its build, in the desktop's format (MainWindow's
// commit label): `<short sha> (<yyyy-MM-dd HH:mm>)`; empty outside a checkout.
val gitCommitLine: String = runCatching {
    providers.exec {
        commandLine("git", "show", "--no-patch", "--date=format:%Y-%m-%d %H:%M", "--format=%h (%cd)", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrDefault("")

android {
    namespace = "com.contextswitcher.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.contextswitcher.android"
        minSdk = 26
        targetSdk = 36
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        buildConfigField("String", "GIT_COMMIT_LINE", "\"$gitCommitLine\"")
    }

    // One committed debug key for every machine: each box otherwise signs with
    // its own ~/.android/debug.keystore, and Android refuses to update an app
    // signed by a different key ("App not installed"). A debug key guards
    // nothing, so it may live in the repo.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The three BC jars (bcprov/bcutil/bcpkix) each ship the same
    // META-INF/LICENSE*.md — harmless duplicates, not a licensing conflict
    // (same project, same license text); keep the first copy.
    packaging {
        resources.pickFirsts.add("META-INF/LICENSE.md")
        resources.pickFirsts.add("META-INF/LICENSE-notice.md")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.useJUnitPlatform() }
        }
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(project(":core"))

    // Held at the last BOM that compiles against compileSdk 36: from
    // 2026.08.00 on, Compose requires API 37 or later.
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")

    // JGit 7.8.0 is the current stable line and dexes clean (verified by the
    // P4 spike: `:android:assembleDebug` D8 output carries no
    // org.eclipse.jgit missing-class/API-modeling warning) — see
    // docs/decisions/0028-jgit-task-repo-clone-on-android.md.
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.8.0.202609011348-r")
    // JGit logs via slf4j; without a binding it falls back to its own NOP,
    // which is fine here (tinylog is the desktop's logger, not wired here).
    implementation("org.slf4j:slf4j-api:2.0.19")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // The Android Auto head-unit app (car/, Apache-2.0); app-projected is the
    // part that talks to the Android Auto host on the phone.
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-projected:1.7.0")

    // Reads the GitHub API answers of the update hint; already in the APK
    // through :core, declared because the app now uses it directly.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")

    // sshj (Apache-2.0) is the in-process SSH client for P5 (MADR 0027): the
    // phone has no `ssh` binary. bcprov-jdk18on (Bouncy Castle License,
    // MIT-style, permissive) replaces Android's stripped built-in BC
    // provider — see SshjCommandRunner's class comment for why.
    implementation("com.hierynomus:sshj:0.40.0")
    // bcprov has a 1.85.2 point release that bcutil/bcpkix never got; pin
    // all three BC artifacts to the shared 1.85 line — a mismatched pair
    // (bcutil's ASN1 OID classes predating bcprov's composite-signature
    // mappings) fails with NoSuchFieldError at provider init, hit during the
    // MINA sshd test-server round-trip below. sshj pulls bcutil/bcpkix
    // transitively for OpenSSH key parsing.
    implementation("org.bouncycastle:bcprov-jdk18on:1.85")
    implementation("org.bouncycastle:bcutil-jdk18on:1.85")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.85")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Robolectric's Compose UI test is JUnit4-based; the vintage engine lets
    // it run alongside the Jupiter tests above under one useJUnitPlatform().
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("androidx.car.app:app-testing:1.7.0")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // In-JVM SSH server (Apache-2.0, test-scope only) for SshjCommandRunnerTest's
    // round-trip: a real exec channel against a real (if embedded) sshd.
    testImplementation("org.apache.sshd:sshd-core:2.19.0")
}
