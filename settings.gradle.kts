// google() is only needed to resolve the Android/Compose plugins that
// :android's build script applies; harmless to declare even when :android
// is excluded below.
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "contextswitcher"

include(":app")
include(":core")

// :android needs an Android SDK. Skip it on machines that don't have one
// (e.g. Oliver's Windows box) so the desktop build stays untouched there
//; the dex spike only needs to run where the SDK exists.
val androidSdkDir = System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: file("local.properties").takeIf { it.exists() }
        ?.let { java.util.Properties().apply { it.inputStream().use(::load) } }
        ?.getProperty("sdk.dir")

if (!androidSdkDir.isNullOrBlank()) {
    include(":android")
}
