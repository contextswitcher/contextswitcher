package com.contextswitcher.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.contextswitcher.android.settings.RepoSettings
import com.contextswitcher.android.settings.SettingsStore
import com.contextswitcher.android.update.AppUpdates
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// The task list offers *Update* exactly when the `android-dev` tag names a
/// commit other than the running build's.
// [utest->dsn~android-update-hint~3]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateHintTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val tagLookups = java.util.concurrent.atomic.AtomicInteger()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { server ->
        server.createContext("/repos/o/r/git/ref/tags/android-dev") { exchange ->
            tagLookups.incrementAndGet()
            val body = """{"object":{"sha":"newer","type":"commit"}}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun showTaskList(runningCommit: String) {
        val settings = SettingsStore(ApplicationProvider.getApplicationContext())
        // No reachable repo: the sync fails at once, and the update check follows it.
        settings.save(RepoSettings("file:///nonexistent/repo.git", "me", "secret"))
        val updates = AppUpdates({ "secret" }, runningCommit, "http://127.0.0.1:${server.address.port}", "o/r")
        composeRule.setContent {
            MaterialTheme {
                TaskListScreen(settings, File(tmpFolder.root, "taskrepo"), onOpenSettings = {}, onOpenTask = {}, updates = updates)
            }
        }
    }

    @Test
    fun `a newer build on android-dev shows Update`() {
        showTaskList(runningCommit = "older")

        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("Update").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Update").assertExists()
    }

    @Test
    fun `the running build itself shows no Update`() {
        showTaskList(runningCommit = "newer")

        composeRule.waitUntil(10_000) { tagLookups.get() > 0 }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Update").assertDoesNotExist()
    }
}
