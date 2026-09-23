package com.contextswitcher.android.watch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// [utest->dsn~android-finish-notification~1]
class FinishWatchTest {

    private fun alerts(vararg statuses: String?): List<Alert?> {
        val watch = FinishWatch()
        return statuses.map { watch.update(it) }
    }

    @Test
    fun `working then waiting alerts once, however long it stays waiting`() {
        assertThat(alerts("working", "working", "waiting", "waiting", "waiting"))
            .containsExactly(null, null, Alert.FINISHED, null, null)
    }

    @Test
    fun `a task that is already idle when the watch starts stays silent`() {
        assertThat(alerts("waiting", "done", null, "waiting")).containsOnlyNulls()
    }

    @Test
    fun `a question mid-turn alerts, and the turn's end still alerts after it`() {
        assertThat(alerts("working", "attention", "attention", "waiting"))
            .containsExactly(null, Alert.NEEDS_YOU, null, Alert.FINISHED)
    }

    @Test
    fun `every new turn alerts again`() {
        assertThat(alerts("working", "done", "working", "waiting"))
            .containsExactly(null, Alert.FINISHED, null, Alert.FINISHED)
    }

    @Test
    fun `the usage limit ends the turn with its own alert`() {
        assertThat(alerts("working", "limit", "waiting")).containsExactly(null, Alert.LIMIT, null)
    }

    @Test
    fun `a poll without a status changes nothing`() {
        assertThat(alerts("working", null, "waiting")).containsExactly(null, null, Alert.FINISHED)
    }
}
