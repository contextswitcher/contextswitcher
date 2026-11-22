package com.contextswitcher.android.update

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/// Whether a newer debug APK than the running one is on the `android-dev`
/// pre-release, and fetching it. The `android-dev.yml` workflow replaces that
/// release's APK and moves its tag to the built commit; the running app knows
/// its own commit from the build, so "the tag names another commit" means an
/// update. The repository is public: neither call needs a token.
// [impl->dsn~android-update-hint~4]
class AppUpdates(
    private val runningCommit: String,
    private val apiBase: String = "https://api.github.com",
    private val webBase: String = "https://github.com",
    private val repo: String = "contextswitcher/contextswitcher",
) {
    sealed interface Check {
        data object UpToDate : Check
        data class Available(val commit: String) : Check
        data class Failed(val message: String) : Check
    }

    fun check(): Check {
        if (runningCommit == "unknown") {
            return Check.Failed("this build does not know its commit")
        }
        return try {
            val connection = open("$apiBase/repos/$repo/git/ref/tags/$TAG", "Accept" to "application/vnd.github+json")
            val commit = connection.inputStream.use { ObjectMapper().readTree(it) }.path("object").path("sha").asText()
            when {
                commit.isEmpty() -> Check.Failed("the $TAG tag names no commit")
                commit == runningCommit -> Check.UpToDate
                else -> Check.Available(commit)
            }
        } catch (e: IOException) {
            Check.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /// Downloads the release's APK to `target`, following GitHub's redirect to its storage.
    fun download(target: File) {
        open("$webBase/$repo/releases/download/$TAG/$ASSET").inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    /// An answer other than 200 throws, naming its status.
    private fun open(url: String, vararg headers: Pair<String, String>): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "ContextSwitcher-Android")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }.also {
            if (it.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub answered ${it.responseCode} for $url")
            }
        }

    private companion object {
        const val TAG = "android-dev"
        const val ASSET = "android-debug.apk"
    }
}
