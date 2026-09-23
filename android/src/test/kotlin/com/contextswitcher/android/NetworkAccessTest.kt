package com.contextswitcher.android

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// The sync and SSH need the network: the manifest must declare it, and a
/// failed first sync must leave a way to try again instead of a blank screen.
// [utest->dsn~android-task-list~1]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkAccessTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `manifest requests internet access`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().contains(Manifest.permission.INTERNET))
    }

    @Test
    fun `failed sync with no tasks shows the error and a retry button`() {
        var retries = 0
        composeRule.setContent {
            EmptyTaskList(syncing = false, error = "Sync failed: timeout", onRetry = { retries++ })
        }

        composeRule.onNodeWithText("Sync failed: timeout").assertExists()
        composeRule.onNodeWithText("Retry").assertIsEnabled().performClick()
        assertEquals(1, retries)
    }
}
