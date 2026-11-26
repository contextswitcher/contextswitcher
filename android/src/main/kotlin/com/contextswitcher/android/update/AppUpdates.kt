package com.contextswitcher.android.update

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.zip.ZipInputStream

/// Whether a build newer than the running one is out, and fetching it. Two
/// sources: the newest day release (`v<date>`, its `.apk` asset) and the
/// newest successful run of `android-dev.yml` (its `contextswitcher-dev`
/// artifact, fetched through nightly.link since GitHub hands out artifacts
/// only with a token). The newer of the two by commit time wins; "newer" means
/// another commit, committed after the running one. The repository is public:
/// no call needs a token.
// [impl->dsn~android-update-hint~5]
class AppUpdates(
    private val runningCommit: String,
    /// Commit time of the running build, epoch seconds.
    private val runningCommitTime: Long,
    private val apiBase: String = "https://api.github.com",
    private val nightlyBase: String = "https://nightly.link",
    private val repo: String = "contextswitcher/contextswitcher",
) {
    /// A build to update to: `name` is the release's date or "dev <short sha>".
    data class Build(val name: String, val commit: String, val commitTime: Long, val url: String, val zipped: Boolean)

    sealed interface Check {
        data object UpToDate : Check
        data class Available(val build: Build) : Check
        data class Failed(val message: String) : Check
    }

    fun check(): Check {
        if (runningCommit == "unknown") {
            return Check.Failed("this build does not know its commit")
        }
        val found = listOf(::latestRelease, ::latestDevBuild).map { runCatching { it() } }
        if (found.all { it.isFailure }) {
            val e = found.first().exceptionOrNull()!!
            return Check.Failed(e.message ?: e.javaClass.simpleName)
        }
        return found.mapNotNull { it.getOrNull() }
            .filter { it.commit != runningCommit && it.commitTime > runningCommitTime }
            .maxByOrNull { it.commitTime }
            ?.let { Check.Available(it) } ?: Check.UpToDate
    }

    /// Writes the build's APK to `target`, following the redirect to storage and unpacking an artifact's zip.
    fun download(build: Build, target: File) {
        open(build.url).inputStream.use { input ->
            if (!build.zipped) {
                target.outputStream().use { input.copyTo(it) }
                return
            }
            val zip = ZipInputStream(input)
            while (true) {
                val entry = zip.nextEntry ?: throw IOException("no APK in ${build.url}")
                if (entry.name.endsWith(".apk")) {
                    target.outputStream().use { zip.copyTo(it) }
                    return
                }
            }
        }
    }

    /// Releases come newest first; `android-dev` (the old fixed pre-release) is skipped.
    private fun latestRelease(): Build? {
        val release = api("releases?per_page=10").firstOrNull { it.path("tag_name").asText().startsWith("v") } ?: return null
        val tag = release.path("tag_name").asText()
        val apk = release.path("assets").firstOrNull { it.path("name").asText().endsWith(".apk") } ?: return null
        val commit = api("commits/$tag")
        return Build(tag.removePrefix("v"), commit.path("sha").asText(), epoch(commit.path("commit").path("committer").path("date")),
            apk.path("browser_download_url").asText(), zipped = false)
    }

    private fun latestDevBuild(): Build? {
        val run = api("actions/workflows/android-dev.yml/runs?status=success&per_page=1").path("workflow_runs").firstOrNull() ?: return null
        val commit = run.path("head_sha").asText()
        return Build("dev ${commit.take(7)}", commit, epoch(run.path("head_commit").path("timestamp")),
            "$nightlyBase/$repo/actions/runs/${run.path("id").asText()}/$DEV_ARTIFACT.zip", zipped = true)
    }

    private fun api(path: String): JsonNode =
        open("$apiBase/repos/$repo/$path", "Accept" to "application/vnd.github+json").inputStream.use { ObjectMapper().readTree(it) }

    private fun epoch(date: JsonNode): Long = Instant.parse(date.asText()).epochSecond

    /// An answer other than 200 throws, naming its status.
    private fun open(url: String, vararg headers: Pair<String, String>): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "ContextSwitcher-Android")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }.also {
            if (it.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("${URL(url).host} answered ${it.responseCode} for $url")
            }
        }

    private companion object {
        const val DEV_ARTIFACT = "contextswitcher-dev"
    }
}
