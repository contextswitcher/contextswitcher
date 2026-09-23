package com.contextswitcher.android

import androidx.compose.runtime.saveable.SaverScope
import com.contextswitcher.android.ui.buildTaskListModel
import com.contextswitcher.tasks.TaskEntry
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/// The open screen survives Android killing the app: saved as a string,
/// restored to the same screen — a task detail re-read from the repo.
// [utest->dsn~android-task-list~1]
class ScreenSaverTest {

    private val scope = SaverScope { true }

    @Test
    fun `settings and task detail round-trip, a vanished task falls back to the list`(@TempDir tmp: File) {
        File(tmp, "alpha").mkdirs()
        File(tmp, "alpha/one.md").writeText("---\ntitle: One\nstatus: active\n---\n")
        val task = buildTaskListModel(tmp.toPath()).flatMap { it.entries }
            .filterIsInstance<TaskEntry.Loaded>().single().task()
        val saver = screenSaver(tmp)

        fun roundTrip(screen: Screen) = saver.restore(with(saver) { scope.save(screen) }!!)

        assertThat(roundTrip(Screen.Settings)).isEqualTo(Screen.Settings)
        assertThat(roundTrip(Screen.TaskDetail(task))).isEqualTo(Screen.TaskDetail(task))

        val saved = with(saver) { scope.save(Screen.TaskDetail(task)) }!!
        File(tmp, "alpha/one.md").delete()
        assertThat(saver.restore(saved)).isEqualTo(Screen.TaskList)
    }
}
