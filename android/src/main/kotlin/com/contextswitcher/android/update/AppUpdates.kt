package com.contextswitcher.android.update

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/// Whether a newer debug APK than the running one is on the `android-dev`
/// pre-release, and fetching it. CI replaces that release's APK on every push
/// to main and moves its tag to the built commit; the running app knows its
/// own commit from the build, so "the tag names another commit" means an
/// update. The repo is private: the API calls carry the settings token, which
/// needs read access to this repository's contents.
// [impl->dsn~android-update-hint~3]
class AppUpdates(
    private val token: () -> String,
    private val runningCommit: String,
    private val apiBase: String = "https://api.github.com",
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
            val commit = getJson("$apiBase/repos/$repo/git/ref/tags/$TAG").path("object").path("sha").asText()
            when {
                commit.isEmpty() -> Check.Failed("the $TAG tag names no commit")
                commit == runningCommit -> Check.UpToDate
                else -> Check.Available(commit)
            }
        } catch (e: IOException) {
            Check.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /// Downloads the release's APK to `target`.
    fun download(target: File) {
        val asset = getJson("$apiBase/repos/$repo/releases/tags/$TAG").path("assets")
            .firstOrNull { it.path("name").asText() == ASSET }
            ?: throw IOException("the $TAG release has no $ASSET")
        val connection = open("$apiBase/repos/$repo/releases/assets/${asset.path("id").asText()}", "application/octet-stream")
        connection.instanceFollowRedirects = false
        // GitHub answers with a redirect to a pre-signed storage URL, which
        // rejects a second credential — follow it without the token.
        val download = when (connection.responseCode) {
            HttpURLConnection.HTTP_OK -> connection
            in 300..399 -> (URL(connection.getHeaderField("Location")).openConnection() as HttpURLConnection).also {
                connection.disconnect()
                if (it.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("APK download answered ${it.responseCode}")
                }
            }
            else -> throw IOException(failure(connection))
        }
        download.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    private fun getJson(url: String): JsonNode {
        val connection = open(url, "application/vnd.github+json")
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException(failure(connection))
        }
        return connection.inputStream.use { ObjectMapper().readTree(it) }
    }

    private fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "ContextSwitcher-Android")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            token().takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

    /// A private repo answers 404, not 403, to a token that cannot read it.
    private fun failure(connection: HttpURLConnection): String = when (connection.responseCode) {
        HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
            "the token cannot read $repo (GitHub answered ${connection.responseCode})"
        else -> "GitHub answered ${connection.responseCode}"
    }

    private companion object {
        const val TAG = "android-dev"
        const val ASSET = "android-debug.apk"
    }
}
