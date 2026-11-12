package com.contextswitcher.android

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/// The screens draw their own top bars; a window action bar would lay the app
/// name over the settings form under Android 15's edge-to-edge.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @Test
    fun `window has no action bar`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNull(activity.actionBar)
    }

    // [utest->dsn~android-deep-link~1]
    @Test
    fun `task and switch links name the task, percent-decoded`() {
        fun id(url: String) = requestedTaskId(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        assertEquals("jabref/fix npe", id("contextswitcher://task/jabref/fix%20npe/"))
        assertEquals("jabref/fix-npe", id("contextswitcher://switch/jabref/fix-npe"))
        assertNull(id("contextswitcher://category/jabref"))
        assertNull(id("contextswitcher://task/"))
    }
}
