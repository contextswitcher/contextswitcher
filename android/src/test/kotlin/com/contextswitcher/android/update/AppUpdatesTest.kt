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

/// Against a local stand-in for the GitHub API: the tag lookup, the asset
/// lookup, and the storage redirect that must not receive the token.
// [utest->dsn~android-update-hint~3]
class AppUpdatesTest {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val authOnStorage = mutableListOf<String?>()

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
        if (it.requestHeaders.getFirst("Authorization") == "Bearer secret") {
            it.send(200, """{"ref":"refs/tags/android-dev","object":{"sha":"$commit","type":"commit"}}""")
        } else {
            it.send(404, """{"message":"Not Found"}""")
        }
    }

    private fun updates(running: String, token: String = "secret") = AppUpdates({ token }, running, base, "o/r")

    @Test
    fun `the same commit is up to date, another one is an update`() {
        tagAt("bbb")

        assertThat(updates("bbb").check()).isEqualTo(AppUpdates.Check.UpToDate)
        assertThat(updates("aaa").check()).isEqualTo(AppUpdates.Check.Available("bbb"))
    }

    @Test
    fun `a token that cannot read the private repo says so`() {
        tagAt("bbb")

        val check = updates("aaa", token = "other").check()

        assertThat(check).isInstanceOf(AppUpdates.Check.Failed::class.java)
        assertThat((check as AppUpdates.Check.Failed).message).contains("token cannot read o/r")
    }

    @Test
    fun `a build that does not know its commit never offers an update`() {
        tagAt("bbb")

        assertThat(updates("unknown").check()).isInstanceOf(AppUpdates.Check.Failed::class.java)
    }

    @Test
    fun `download follows the storage redirect without the token`() {
        respond("/repos/o/r/releases/tags/android-dev") {
            it.send(200, """{"assets":[{"id":7,"name":"notes.txt"},{"id":42,"name":"android-debug.apk"}]}""")
        }
        respond("/repos/o/r/releases/assets/42") {
            assertThat(it.requestHeaders.getFirst("Accept")).isEqualTo("application/octet-stream")
            it.responseHeaders.add("Location", "$base/storage/apk")
            it.send(302)
        }
        respond("/storage/apk") {
            authOnStorage.add(it.requestHeaders.getFirst("Authorization"))
            it.send(200, "APK BYTES")
        }
        val target = File(createTempDirectory("update").toFile(), "update.apk")

        updates("aaa").download(target)

        assertThat(target.readText()).isEqualTo("APK BYTES")
        assertThat(authOnStorage).containsExactly(null)
    }

    @Test
    fun `a release without the APK fails the download`() {
        respond("/repos/o/r/releases/tags/android-dev") { it.send(200, """{"assets":[]}""") }

        assertThatThrownBy { updates("aaa").download(File(createTempDirectory("update").toFile(), "x.apk")) }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("no android-debug.apk")
    }
}
