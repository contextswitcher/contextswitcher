package com.contextswitcher.android

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
}
