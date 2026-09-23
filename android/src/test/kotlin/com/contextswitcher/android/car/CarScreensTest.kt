package com.contextswitcher.android.car

import android.content.Context
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.contextswitcher.android.watch.TaskWatchService
import com.contextswitcher.queue.MessageSender
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// A canned reply on the head unit reaches the session, and one that cannot
/// be sent stays on the phone.
// [utest->dsn~android-auto-message~1]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CarScreensTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val task = Task("jabref/fix-npe", "Fix the NPE", TaskStatus.ACTIVE, "koppor@example.org",
        Task.TmuxConfig("jabref", "@17"), null, null, null, null, null, emptyList(), emptyList(), emptyList(), null, null, "")
    private val original = TaskWatchService.messageSender

    @After
    fun tearDown() {
        TaskWatchService.messageSender = original
    }

    private fun tapContinue(sendResult: String?): List<String> {
        val sent = mutableListOf<String>()
        TaskWatchService.messageSender = { _, ssh ->
            object : MessageSender(ssh, context.filesDir.toPath()) {
                override fun send(remote: String, target: String, text: String): String? {
                    sent += "$remote $target $text"
                    return sendResult
                }
            }
        }
        val screen = CarTaskScreen(TestCarContext.createCarContext(context), task, Runnable::run)
        val rows = (screen.onGetTemplate() as ListTemplate).singleList!!.items.map { it as Row }
        assertThat(rows.map { it.title!!.toString() }).startsWith("Dictate message", "Send “continue”")
        rows[1].onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        return sent
    }

    @Test
    fun `a canned reply is sent to the task's window`() {
        assertThat(tapContinue(null)).containsExactly("koppor@example.org @17 continue")
        assertThat(TaskWatchService.enqueuedStore(context).load(task.id())).isEmpty()
    }

    @Test
    fun `a reply that cannot be sent is kept in the phone's queue`() {
        tapContinue("no route to host")
        assertThat(TaskWatchService.enqueuedStore(context).load(task.id())).containsExactly("continue")
    }
}
