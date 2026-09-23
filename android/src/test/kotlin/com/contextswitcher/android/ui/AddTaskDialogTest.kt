package com.contextswitcher.android.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// The picked category and the typed title reach [AddTaskDialog]'s `onAdd`,
/// and a failure keeps the title in the dialog for another try.
// [utest->dsn~android-task-create~1]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddTaskDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `adds the typed title to the picked category and keeps it after a failure`() {
        val added = mutableListOf<Pair<String, String>>()
        var error by mutableStateOf<String?>(null)
        composeRule.setContent {
            AddTaskDialog(
                categories = listOf("jabref", "private"),
                initialCategory = "jabref",
                adding = false,
                error = error,
                onAdd = { category, title -> added += category to title },
                onDismiss = {},
            )
        }

        composeRule.onNodeWithText("Add").assertIsNotEnabled()
        composeRule.onNodeWithText("jabref").performClick()
        composeRule.onNodeWithText("private").performClick()
        composeRule.onNodeWithText("Title").performTextInput("Buy tickets ")
        composeRule.onNodeWithText("Add").performClick()
        assertEquals(listOf("private" to "Buy tickets"), added)

        error = "Push rejected: offline"
        composeRule.onNodeWithText("Push rejected: offline").assertExists()
        composeRule.onNodeWithText("Buy tickets ").assertExists()
    }

    // [utest->dsn~android-live-task~1]
    @Test
    fun `a category with a remote starts Claude with the picked model`() {
        val started = mutableListOf<String>()
        val added = mutableListOf<String>()
        composeRule.setContent {
            AddTaskDialog(
                categories = listOf("work", "notes"),
                initialCategory = "work",
                adding = false,
                error = null,
                onAdd = { _, title -> added += title },
                onDismiss = {},
                remoteOf = { if (it == "work") "me@box" else null },
                onStartClaude = { category, title, mode, skip -> started += "$category|$title|${mode.model()}|${mode.effort()}|$skip" },
            )
        }

        composeRule.onNodeWithText("Start Claude on me@box").assertExists()
        composeRule.onNodeWithText("Model: as is").performClick()
        composeRule.onNodeWithText("opus").performClick()
        composeRule.onNodeWithText("Skip permission prompts").assertExists()
        composeRule.onNodeWithText("Title").performTextInput("Fix it")
        composeRule.onNodeWithText("Start").performClick()
        assertEquals(listOf("work|Fix it|opus|null|false"), started)

        composeRule.onNodeWithText("work").performClick()
        composeRule.onNodeWithText("notes").performClick()
        composeRule.onNodeWithText("Start Claude on me@box").assertDoesNotExist()
        composeRule.onNodeWithText("Add").performClick()
        assertEquals(listOf("Fix it"), added)
    }
}
