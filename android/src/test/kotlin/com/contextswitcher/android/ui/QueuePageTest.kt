package com.contextswitcher.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.contextswitcher.android.queue.DraftStore
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskStatus
import kotlin.io.path.createTempDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// Unsent text is never lost: it survives Android killing or updating the app,
/// and it is kept as a draft when the user turns to something else.
// [utest->dsn~android-queue-send~2]
// [utest->dsn~android-message-drafts~3]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueuePageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val task = Task("alpha/one", "One", TaskStatus.ACTIVE, null, null, null, null, null, null, null,
        emptyList(), emptyList(), emptyList(), null, null, "")
    private val draftsDir = createTempDirectory("drafts")
    private val drafts = DraftStore(draftsDir)

    @Test
    fun `unsent draft survives activity recreation`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            QueuePage(task, emptyList(), drafts, send = { null }, snackbarHostState = SnackbarHostState())
        }

        composeRule.onNodeWithText("Message").performTextInput("half-typed")
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("half-typed").assertExists()
    }

    @Test
    fun `tapping the page's empty space keeps the text as a draft`() {
        composeRule.setContent {
            QueuePage(task, emptyList(), drafts, send = { null }, snackbarHostState = SnackbarHostState())
        }

        composeRule.onNodeWithText("Message").performTextInput("look this up first")
        composeRule.onNodeWithText("Queued on the desktop").performTouchInput { click() }
        composeRule.waitForIdle()

        assertThat(drafts.load(task.id())).containsExactly("look this up first")
        composeRule.onNodeWithText("Drafts on this phone").assertExists()
        composeRule.onAllNodesWithText("look this up first").assertCountEquals(1)
    }

    @Test
    fun `save draft keeps the text and empties the box`() {
        composeRule.setContent {
            QueuePage(task, emptyList(), drafts, send = { null }, snackbarHostState = SnackbarHostState())
        }

        composeRule.onNodeWithText("Message").performTextInput("later")
        composeRule.onNodeWithText("Save draft").performClick()
        composeRule.waitForIdle()

        assertThat(drafts.load(task.id())).containsExactly("later")
        assertThat(drafts.loadUnsent(task.id())).isEmpty()
        composeRule.onAllNodesWithText("later").assertCountEquals(1)
    }

    @Test
    fun `typed text is on disk at once, so a restart of the app cannot lose it`() {
        composeRule.setContent {
            QueuePage(task, emptyList(), drafts, send = { null }, snackbarHostState = SnackbarHostState())
        }

        composeRule.onNodeWithText("Message").performTextInput("typed before the update")
        composeRule.waitForIdle()

        // No disposal, no saved instance state: what an APK update or a force stop leaves behind.
        assertThat(DraftStore(draftsDir).loadUnsent(task.id())).isEqualTo("typed before the update")
    }

    @Test
    fun `typed text is back in the box after leaving and reopening the page`() {
        composeRule.setContent {
            var open by remember { mutableStateOf(true) }
            Column {
                TextButton(onClick = { open = !open }) { Text("Toggle") }
                if (open) {
                    QueuePage(task, emptyList(), drafts, send = { null }, snackbarHostState = SnackbarHostState())
                }
            }
        }

        composeRule.onNodeWithText("Message").performTextInput("do not lose me")
        composeRule.onNodeWithText("Toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Toggle").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("do not lose me").assertCountEquals(1)
    }

    @Test
    fun `sending a draft removes it, a failed send keeps it`() {
        drafts.add(task.id(), "try me")
        val attempts = mutableListOf<String>()
        composeRule.setContent {
            QueuePage(task, emptyList(), drafts,
                send = { text -> attempts.add(text); if (attempts.size == 1) "offline" else null },
                snackbarHostState = SnackbarHostState())
        }

        // The draft's Send comes first; the box's own Send is disabled while the box is empty.
        composeRule.onAllNodesWithText("Send")[0].performClick()
        composeRule.waitUntil(5_000) { attempts.size == 1 }
        composeRule.waitForIdle()
        assertThat(drafts.load(task.id())).containsExactly("try me")

        composeRule.onAllNodesWithText("Send")[0].performClick()
        composeRule.waitUntil(5_000) { drafts.load(task.id()).isEmpty() }

        assertThat(attempts).containsExactly("try me", "try me")
    }

    @Test
    fun `Edit moves a draft into the box and focuses it`() {
        drafts.add(task.id(), "edit me")
        composeRule.setContent {
            QueuePage(task, emptyList(), drafts, send = { null }, snackbarHostState = SnackbarHostState())
        }

        composeRule.onNodeWithText("Edit").performClick()
        composeRule.waitForIdle()

        assertThat(drafts.load(task.id())).isEmpty()
        assertThat(drafts.loadUnsent(task.id())).isEqualTo("edit me")
        composeRule.onNodeWithText("Drafts on this phone").assertDoesNotExist()
        composeRule.onNodeWithText("Message").assertIsFocused()
    }

    // [utest->dsn~android-queue-delete~1]
    @Test
    fun `Delete on a desktop message asks first, then deletes that message`() {
        val deleted = mutableListOf<String>()
        composeRule.setContent {
            QueuePage(task, listOf("first", "second"), drafts, send = { null }, snackbarHostState = SnackbarHostState(),
                deleteQueued = { message -> deleted += message; null })
        }

        composeRule.onAllNodesWithText("Delete")[1].performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertThat(deleted).isEmpty()

        composeRule.onAllNodesWithText("Delete")[1].performClick()
        composeRule.onNodeWithText("Delete queued message?").assertExists()
        composeRule.onAllNodesWithText("Delete").onLast().performClick()
        composeRule.waitForIdle()
        assertThat(deleted).containsExactly("second")
    }

    @Test
    fun `Edit on a desktop message copies it into the box and keeps the desktop's`() {
        composeRule.setContent {
            QueuePage(task, listOf("queued on the desktop"), drafts, send = { null }, snackbarHostState = SnackbarHostState())
        }

        composeRule.onNodeWithText("Edit").performClick()
        composeRule.waitForIdle()

        assertThat(drafts.loadUnsent(task.id())).isEqualTo("queued on the desktop")
        composeRule.onAllNodesWithText("queued on the desktop").assertCountEquals(2)
        composeRule.onNodeWithText("Message").assertIsFocused()
    }

    @Test
    fun `Edit keeps what the box held as a draft`() {
        composeRule.setContent {
            QueuePage(task, listOf("from the desktop"), drafts, send = { null }, snackbarHostState = SnackbarHostState())
        }

        composeRule.onNodeWithText("Message").performTextInput("half-typed")
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.waitForIdle()

        assertThat(drafts.load(task.id())).containsExactly("half-typed")
        assertThat(drafts.loadUnsent(task.id())).isEqualTo("from the desktop")
    }

    @Test
    fun `tapping a draft moves it back into the box`() {
        drafts.add(task.id(), "edit me")
        composeRule.setContent {
            QueuePage(task, emptyList(), drafts, send = { null }, snackbarHostState = SnackbarHostState())
        }

        composeRule.onNodeWithText("edit me").performClick()
        composeRule.waitForIdle()

        assertThat(drafts.load(task.id())).isEmpty()
        composeRule.onNodeWithText("Drafts on this phone").assertDoesNotExist()
        composeRule.onNodeWithText("edit me").assertExists()
    }

    // [utest->dsn~android-enqueue~1]
    @Test
    fun `Enqueue moves the typed text into the phone queue`() {
        val enqueued = DraftStore(kotlin.io.path.createTempDirectory("enqueued"))
        composeRule.setContent {
            QueuePage(task, emptyList(), drafts, send = { null }, snackbarHostState = SnackbarHostState(), enqueuedStore = enqueued)
        }

        composeRule.onNodeWithText("Message").performTextInput("after this turn")
        composeRule.onNodeWithText("Enqueue").performClick()
        composeRule.waitForIdle()

        assertThat(enqueued.load(task.id())).containsExactly("after this turn")
        composeRule.onNodeWithText("Sent when Claude is idle").assertExists()
        assertThat(drafts.loadUnsent(task.id())).isEmpty()
    }
}
