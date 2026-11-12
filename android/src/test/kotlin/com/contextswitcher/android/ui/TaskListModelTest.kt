package com.contextswitcher.android.ui

import com.contextswitcher.tasks.TaskEntry
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/// Two groups, three tasks, one corrupt file — the fixture the Compose UI
/// test (`TaskListScreenTest`, Robolectric) also renders, so both prove the
/// same directory reads the same way at the model layer and at the pixel
/// layer.
// [utest->dsn~android-task-list~1]
class TaskListModelTest {

    @Test
    fun `groups tasks by folder and keeps a corrupt file as a Failed entry`(@TempDir tmp: File) {
        File(tmp, "alpha").mkdirs()
        File(tmp, "beta").mkdirs()
        File(tmp, "alpha/one.md").writeText(task("One", "active"))
        File(tmp, "alpha/two.md").writeText(task("Two", "suspended"))
        File(tmp, "beta/three.md").writeText(task("Three", "active"))
        File(tmp, "beta/broken.md").writeText("not valid frontmatter at all")

        val groups = buildTaskListModel(tmp.toPath())

        assertThat(groups.map { it.name }).containsExactly("alpha", "beta")
        val alpha = groups.first { it.name == "alpha" }.entries
        assertThat(alpha).hasSize(2)
        val beta = groups.first { it.name == "beta" }.entries
        assertThat(beta).hasSize(2)
        assertThat(beta.filterIsInstance<com.contextswitcher.tasks.TaskEntry.Failed>()).hasSize(1)
    }

    // [utest->dsn~android-task-create~2]
    @Test
    fun `groupAtTop maps the list's first visible item to its group`() {
        val entry = com.contextswitcher.tasks.TaskEntry.Failed("x", "x.md", "broken")
        val groups = listOf(
            TaskGroupUi("alpha", listOf(entry, entry)),
            TaskGroupUi("beta", listOf(entry)),
        )

        assertThat((0..4).map { groupAtTop(groups, it) }).containsExactly("alpha", "alpha", "alpha", "beta", "beta")
        assertThat(groupAtTop(groups, 99)).isEqualTo("beta")
        assertThat(groupAtTop(emptyList(), 0)).isEmpty()
    }

    // [utest->dsn~android-task-list-navigation~1]
    @Test
    fun `headerIndex is the inverse of groupAtTop`() {
        val groups = listOf(TaskGroupUi("alpha", listOf(failed("a"), failed("b"))), TaskGroupUi("beta", listOf(failed("c"))))
        assertThat(headerIndex(groups, "alpha")).isEqualTo(0)
        assertThat(headerIndex(groups, "beta")).isEqualTo(3)
        assertThat(groupAtTop(groups, headerIndex(groups, "beta"))).isEqualTo("beta")
        assertThat(headerIndex(groups, "missing")).isEqualTo(0)
    }

    // [utest->dsn~android-task-list-navigation~1]
    @Test
    fun `filterTaskList matches title, tag and group name and drops empty groups`(@TempDir tmp: File) {
        File(tmp, "alpha").mkdirs()
        File(tmp, "beta").mkdirs()
        File(tmp, "alpha/one.md").writeText(task("Fix Login", "active"))
        File(tmp, "alpha/two.md").writeText(task("Other", "active").replace("status: active", "status: active\ntags: [urgent]"))
        File(tmp, "beta/three.md").writeText(task("Third", "active"))
        val groups = buildTaskListModel(tmp.toPath())

        fun titles(query: String) = filterTaskList(groups, query).map { g -> g.name to g.entries.map { (it as TaskEntry.Loaded).task().title() } }
        assertThat(titles("login")).containsExactly("alpha" to listOf("Fix Login"))
        assertThat(titles("URG")).containsExactly("alpha" to listOf("Other"))
        assertThat(titles("bet")).containsExactly("beta" to listOf("Third"))
        assertThat(titles("nothing")).isEmpty()
        assertThat(filterTaskList(groups, " ")).isSameAs(groups)
    }

    private fun failed(name: String) = TaskEntry.Failed(name, "$name.md", "broken")

    private fun task(title: String, status: String) = """
        ---
        title: $title
        status: $status
        ---
    """.trimIndent()
}
