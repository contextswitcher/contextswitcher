package com.contextswitcher.android.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// Renders the task-list body (via [buildTaskListModel], the same pure
/// function [TaskListModelTest] exercises headlessly) over a real Compose
/// tree with Robolectric, so the group headers, task titles, and the
/// corrupt-file row are checked as they actually render, not just as data.
// [utest->dsn~android-task-list~1]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskListScreenTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders group headers, task titles and a red failed row`() {
        val tasksDir = tmpFolder.newFolder("tasks")
        File(tasksDir, "alpha").mkdirs()
        File(tasksDir, "beta").mkdirs()
        File(tasksDir, "alpha/one.md").writeText(task("One", "active"))
        File(tasksDir, "alpha/two.md").writeText(task("Two", "suspended"))
        File(tasksDir, "beta/three.md").writeText(task("Three", "active"))
        File(tasksDir, "beta/broken.md").writeText("not valid frontmatter at all")

        val groups = buildTaskListModel(tasksDir.toPath())

        composeRule.setContent {
            MaterialTheme {
                Surface {
                    TaskListBody(groups)
                }
            }
        }

        composeRule.onNodeWithText("alpha").assertExists()
        composeRule.onNodeWithText("beta").assertExists()
        composeRule.onNodeWithText("One").assertExists()
        composeRule.onNodeWithText("Two").assertExists()
        composeRule.onNodeWithText("Three").assertExists()
        composeRule.onNodeWithText("broken.md", substring = true).assertExists()
    }

    // [utest->dsn~android-task-list~1]
    @Test
    fun `the scroll position survives the list leaving the screen for an open task`() {
        val tasksDir = tmpFolder.newFolder("many")
        File(tasksDir, "work").mkdirs()
        for (i in 10..69) {
            File(tasksDir, "work/t$i.md").writeText(task("Task $i", "active"))
        }
        val groups = buildTaskListModel(tasksDir.toPath())
        var showList by mutableStateOf(true)
        lateinit var state: LazyListState
        composeRule.setContent {
            state = rememberLazyListState()
            MaterialTheme {
                Surface {
                    if (showList) TaskListBody(groups, listState = state) else Text("detail")
                }
            }
        }

        composeRule.runOnIdle { runBlocking { state.scrollToItem(40) } }
        showList = false
        composeRule.onNodeWithText("detail").assertExists()
        showList = true
        composeRule.waitForIdle()

        assertThat(state.firstVisibleItemIndex).isEqualTo(40)
        composeRule.onNodeWithText("Task 49").assertExists()
    }

    private fun task(title: String, status: String) = """
        ---
        title: $title
        status: $status
        ---
    """.trimIndent()
}
