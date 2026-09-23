package com.contextswitcher.android.car

import com.contextswitcher.android.ui.TaskGroupUi
import com.contextswitcher.discovery.TmuxStatusPoller
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskEntry
import com.contextswitcher.tasks.TaskStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// [utest->dsn~android-auto-tasks~1]
class CarTasksTest {

    private fun task(title: String, window: String?, status: TaskStatus = TaskStatus.ACTIVE, remote: String? = "host") =
        TaskEntry.Loaded(Task("g/$title", title, status, remote, window?.let { Task.TmuxConfig("s", it) },
            null, null, null, null, null, emptyList(), emptyList(), emptyList(), null, null, ""))

    @Test
    fun `tasks waiting for the user come first, unreachable and done ones are left out`() {
        val groups = listOf(TaskGroupUi("g", listOf(
            task("busy", "@1"), task("asks", "@2"), task("finished turn", "@3"), task("unknown", "@4"),
            task("no window", null), task("no remote", "@5", remote = null), task("done", "@6", TaskStatus.DONE),
            TaskEntry.Failed("g/broken", "broken.md", "bad yaml"))))
        val statuses = mapOf(
            TmuxStatusPoller.key("host", "@1") to "working",
            TmuxStatusPoller.key("host", "@2") to "attention",
            TmuxStatusPoller.key("host", "@3") to "waiting")

        assertThat(carTasks(groups, statuses).map { "${it.task.title()}=${it.status}" })
            .containsExactly("asks=attention", "finished turn=waiting", "busy=working", "unknown=null")
    }
}
