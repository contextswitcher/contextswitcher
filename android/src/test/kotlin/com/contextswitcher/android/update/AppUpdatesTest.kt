package com.contextswitcher.android.update

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/// Against a local stand-in for GitHub and nightly.link: the newest day
/// release and dev run, which of them wins, and both downloads.
// [utest->dsn~android-update-hint~5]
class AppUpdatesTest {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val updates = { running: String, time: Long -> AppUpdates(running, time, base, base, "o/r") }

    // 2026-11-26T04:00:00Z and 2026-11-26T10:00:00Z
    private val releaseTime = 1795665600L
    private val devTime = 1795687200L

    @AfterEach
    fun stop() = server.stop(0)

    private fun respond(path: String, handler: (HttpExchange) -> Unit) = server.createContext(path) { exchange ->
        exchange.use(handler)
    }

    private fun HttpExchange.send(status: Int, body: ByteArray = ByteArray(0)) {
        sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) responseBody.write(body)
    }

    private fun release() {
        respond("/repos/o/r/releases") {
            it.send(200, """[{"tag_name":"android-dev","assets":[]},
                {"tag_name":"v2026-11-26","assets":[{"name":"contextswitcher-2026-11-26.apk","browser_download_url":"$base/dl/rel.apk"}]}]""".toByteArray())
        }
        respond("/repos/o/r/commits/v2026-11-26") {
            it.send(200, """{"sha":"rel","commit":{"committer":{"date":"2026-11-26T04:00:00Z"}}}""".toByteArray())
        }
    }

    private fun devRun() = respond("/repos/o/r/actions/workflows/android-dev.yml/runs") {
        it.send(200, """{"workflow_runs":[{"id":42,"head_sha":"dev4567890","head_commit":{"timestamp":"2026-11-26T10:00:00Z"}}]}""".toByteArray())
    }

    private fun available(check: AppUpdates.Check) = (check as AppUpdates.Check.Available).build

    @Test
    fun `the newer of release and dev build wins`() {
        release()
        devRun()

        assertThat(available(updates("old", releaseTime - 1).check()).name).isEqualTo("dev dev4567")
        assertThat(available(updates("mid", releaseTime + 1).check()).commit).isEqualTo("dev4567890")
    }

    @Test
    fun `a release alone is offered to an older build only`() {
        release()

        assertThat(available(updates("old", releaseTime - 1).check()).name).isEqualTo("2026-11-26")
        assertThat(updates("rel", releaseTime).check()).isEqualTo(AppUpdates.Check.UpToDate)
        assertThat(updates("later", devTime).check()).isEqualTo(AppUpdates.Check.UpToDate)
    }

    @Test
    fun `the running dev build itself is up to date`() {
        release()
        devRun()

        assertThat(updates("dev4567890", devTime).check()).isEqualTo(AppUpdates.Check.UpToDate)
    }

    @Test
    fun `both lookups failing names the status`() {
        val check = updates("aaa", 0).check()

        assertThat(check).isInstanceOf(AppUpdates.Check.Failed::class.java)
        assertThat((check as AppUpdates.Check.Failed).message).contains("answered 404")
    }

    @Test
    fun `a build that does not know its commit never offers an update`() {
        release()

        assertThat(updates("unknown", 0).check()).isInstanceOf(AppUpdates.Check.Failed::class.java)
    }

    @Test
    fun `a release download follows the redirect to storage`() {
        release()
        respond("/dl/rel.apk") {
            it.responseHeaders.add("Location", "$base/storage/apk")
            it.send(302)
        }
        respond("/storage/apk") { it.send(200, "APK BYTES".toByteArray()) }
        val target = File(createTempDirectory("update").toFile(), "update.apk")

        updates("old", 0).run { download(available(check()), target) }

        assertThat(target.readText()).isEqualTo("APK BYTES")
    }

    @Test
    fun `a dev build download unpacks the artifact's APK`() {
        devRun()
        val zip = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use {
                it.putNextEntry(ZipEntry("contextswitcher-dev-dev4567.apk"))
                it.write("DEV APK".toByteArray())
            }
        }.toByteArray()
        respond("/o/r/actions/runs/42/contextswitcher-dev.zip") { it.send(200, zip) }
        val target = File(createTempDirectory("update").toFile(), "update.apk")

        updates("old", 0).run { download(available(check()), target) }

        assertThat(target.readText()).isEqualTo("DEV APK")
    }
}
