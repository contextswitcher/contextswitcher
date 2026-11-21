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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.graphics.Color
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskStatus
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

    // [utest->dsn~android-task-list-navigation~1]
    @Test
    fun `tapping a group header lists the groups and picking one scrolls to it`() {
        val tasksDir = tmpFolder.newFolder("jump")
        for (folder in listOf("alpha", "omega")) {
            File(tasksDir, folder).mkdirs()
            for (i in 10..39) File(tasksDir, "$folder/t$i.md").writeText(task("$folder $i", "active"))
        }
        val groups = buildTaskListModel(tasksDir.toPath())
        lateinit var state: LazyListState
        composeRule.setContent {
            state = rememberLazyListState()
            MaterialTheme { Surface { TaskListBody(groups, listState = state) } }
        }

        composeRule.onNodeWithText("alpha").performClick()
        composeRule.onNodeWithText("Go to category").assertExists()
        composeRule.onAllNodesWithText("omega").onLast().performClick()
        composeRule.waitForIdle()

        assertThat(state.firstVisibleItemIndex).isEqualTo(headerIndex(groups, "omega"))
        composeRule.onNodeWithText("Go to category").assertDoesNotExist()
    }

    // [utest->dsn~android-recent-categories~2]
    @Test
    fun `go to category lists recent categories on top and reports the pick`() {
        val tasksDir = tmpFolder.newFolder("recent")
        for (g in listOf("alpha", "omega")) {
            File(tasksDir, g).mkdirs()
            File(tasksDir, "$g/one.md").writeText(task("One $g", "active"))
        }
        val groups = buildTaskListModel(tasksDir.toPath())
        val picked = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme { Surface { TaskListBody(groups, recentCategories = listOf("omega", "gone"), onPickCategory = { picked += it }) } }
        }

        composeRule.onNodeWithText("alpha").performClick()
        assertThat(composeRule.onAllNodesWithText("omega").fetchSemanticsNodes()).hasSize(3)
        composeRule.onNodeWithText("gone").assertDoesNotExist()
        composeRule.onAllNodesWithText("omega").onLast().performClick()

        assertThat(picked).containsExactly("omega")
    }

    // [utest->dsn~android-task-delete~2]
    @Test
    fun `a long press offers deleting the task, after a confirmation`() {
        val tasksDir = tmpFolder.newFolder("delete")
        File(tasksDir, "g").mkdirs()
        File(tasksDir, "g/one.md").writeText(task("One", "active"))
        val groups = buildTaskListModel(tasksDir.toPath())
        val deleted = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme { Surface { TaskListBody(groups, onDeleteTask = { deleted += it.id() }) } }
        }

        composeRule.onNodeWithText("One").performTouchInput { longClick() }
        composeRule.onNodeWithText("Delete task…").performClick()
        assertThat(deleted).isEmpty()
        composeRule.onNodeWithText("Delete").performClick()

        assertThat(deleted).containsExactly("g/one")
    }

    // [utest->dsn~android-task-cancel~1]
    @Test
    fun `the long-press menu cancels a task with a remote window, and only such a task`() {
        val tasksDir = tmpFolder.newFolder("cancel")
        File(tasksDir, "g").mkdirs()
        File(tasksDir, "g/live.md").writeText("---\ntitle: Live\nstatus: active\nremote: host\ntmux:\n  session: s\n  window: \"@3\"\n---\n")
        File(tasksDir, "g/plain.md").writeText(task("Plain", "active"))
        val groups = buildTaskListModel(tasksDir.toPath())
        val cancelled = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme { Surface { TaskListBody(groups, onDeleteTask = {}, onCancelTask = { cancelled += it.id() }) } }
        }

        composeRule.onNodeWithText("Live").performTouchInput { longClick() }
        composeRule.onNodeWithText("Cancel").performClick()
        assertThat(cancelled).containsExactly("g/live")

        composeRule.onNodeWithText("Plain").performTouchInput { longClick() }
        composeRule.onNodeWithText("Delete task…").assertExists()
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
    }

    // [utest->dsn~android-running-dot~1]
    @Test
    fun `a row's dot follows its window's status like the desktop's`() {
        val task = Task("g/t", "t", TaskStatus.ACTIVE, "host", Task.TmuxConfig("s", "@3"),
            null, null, null, null, null, emptyList(), emptyList(), emptyList(), null, null, "")
        assertThat(sessionStatus(task, mapOf("host @3" to "working"))).isEqualTo("working")
        assertThat(sessionStatus(task, mapOf("other @3" to "working"))).isNull()
        assertThat(runningDotColor("working")).isEqualTo(Color(0xFFFF8A00))
        assertThat(runningDotColor("attention")).isEqualTo(runningDotColor("waiting")).isEqualTo(runningDotColor("limit"))
        assertThat(runningDotColor("interrupted")).isEqualTo(Color(0xFFEE3377))
        assertThat(runningDotColor(null)).isNull()
    }

    private fun task(title: String, status: String) = """
        ---
        title: $title
        status: $status
        ---
    """.trimIndent()
}
