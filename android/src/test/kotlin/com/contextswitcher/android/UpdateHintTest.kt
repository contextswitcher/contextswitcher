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

/// The task list offers *Update* exactly when the newest dev build is another
/// commit than the running build's (the release lookup fails here).
// [utest->dsn~android-update-hint~5]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateHintTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val runLookups = java.util.concurrent.atomic.AtomicInteger()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { server ->
        server.createContext("/repos/o/r/actions/workflows/android-dev.yml/runs") { exchange ->
            runLookups.incrementAndGet()
            val body = """{"workflow_runs":[{"id":1,"head_sha":"newer","head_commit":{"timestamp":"2026-11-26T10:00:00Z"}}]}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun showTaskList(runningCommit: String) {
        val settings = SettingsStore(ApplicationProvider.getApplicationContext())
        // No reachable repo and no token: the sync fails at once, and the update check follows it anyway.
        settings.save(RepoSettings("file:///nonexistent/repo.git", "me", ""))
        val updates = "http://127.0.0.1:${server.address.port}".let { AppUpdates(runningCommit, 1795665600L, it, it, "o/r") }
        composeRule.setContent {
            MaterialTheme {
                TaskListScreen(settings, File(tmpFolder.root, "taskrepo"), onOpenSettings = {}, onOpenTask = {}, updates = updates)
            }
        }
    }

    @Test
    fun `a newer dev build shows Update`() {
        showTaskList(runningCommit = "older")

        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("Update").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Update").assertExists()
    }

    @Test
    fun `the running build itself shows no Update`() {
        showTaskList(runningCommit = "newer")

        composeRule.waitUntil(10_000) { runLookups.get() > 0 }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Update").assertDoesNotExist()
    }
}
