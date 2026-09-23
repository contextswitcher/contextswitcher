package com.contextswitcher.android

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// A crash leaves its stack trace for the next start, which shows it until
/// closed; the previous handler still runs, so Android's own crash handling
/// is unchanged.
// [utest->dsn~android-crash-report~1]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashReportTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a crash is written, shown at the next start and deleted on Close`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var delegated: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, e -> delegated = e }
        CrashReport.file(context).delete()
        CrashReport.install(context)
        val boom = IllegalStateException("boom on the tablet")

        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), boom)

        assertSame(boom, delegated)
        assertTrue(CrashReport.file(context).readText().contains("IllegalStateException: boom on the tablet"))
        composeRule.setContent { LastCrashDialog { androidx.compose.material3.Text("the app") } }
        composeRule.onNodeWithText("The app crashed last time").assertExists()
        composeRule.onNodeWithText("the app").assertDoesNotExist()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("The app crashed last time").assertDoesNotExist()
        composeRule.onNodeWithText("the app").assertExists()
        assertFalse(CrashReport.file(context).exists())
    }
}
