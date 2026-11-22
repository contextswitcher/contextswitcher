package com.contextswitcher.android.update

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import kotlin.io.path.createTempDirectory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/// Against a local stand-in for GitHub: the tag lookup, and the release
/// download with its redirect to storage.
// [utest->dsn~android-update-hint~4]
class AppUpdatesTest {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val updates = { running: String -> AppUpdates(running, base, base, "o/r") }

    @AfterEach
    fun stop() = server.stop(0)

    private fun respond(path: String, handler: (HttpExchange) -> Unit) = server.createContext(path) { exchange ->
        exchange.use(handler)
    }

    private fun HttpExchange.send(status: Int, body: String = "") {
        val bytes = body.toByteArray()
        sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.write(bytes)
    }

    private fun tagAt(commit: String) = respond("/repos/o/r/git/ref/tags/android-dev") {
        it.send(200, """{"ref":"refs/tags/android-dev","object":{"sha":"$commit","type":"commit"}}""")
    }

    @Test
    fun `the same commit is up to date, another one is an update`() {
        tagAt("bbb")

        assertThat(updates("bbb").check()).isEqualTo(AppUpdates.Check.UpToDate)
        assertThat(updates("aaa").check()).isEqualTo(AppUpdates.Check.Available("bbb"))
    }

    @Test
    fun `a failing lookup names the status`() {
        respond("/repos/o/r/git/ref/tags/android-dev") { it.send(403) }

        val check = updates("aaa").check()

        assertThat(check).isInstanceOf(AppUpdates.Check.Failed::class.java)
        assertThat((check as AppUpdates.Check.Failed).message).contains("GitHub answered 403")
    }

    @Test
    fun `a build that does not know its commit never offers an update`() {
        tagAt("bbb")

        assertThat(updates("unknown").check()).isInstanceOf(AppUpdates.Check.Failed::class.java)
    }

    @Test
    fun `download follows the redirect to storage`() {
        respond("/o/r/releases/download/android-dev/android-debug.apk") {
            it.responseHeaders.add("Location", "$base/storage/apk")
            it.send(302)
        }
        respond("/storage/apk") { it.send(200, "APK BYTES") }
        val target = File(createTempDirectory("update").toFile(), "update.apk")

        updates("aaa").download(target)

        assertThat(target.readText()).isEqualTo("APK BYTES")
    }

    @Test
    fun `a release without the APK fails the download`() {
        assertThatThrownBy { updates("aaa").download(File(createTempDirectory("update").toFile(), "x.apk")) }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("GitHub answered 404")
    }
}
