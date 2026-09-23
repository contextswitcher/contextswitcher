package com.contextswitcher.android.queue

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

// [utest->dsn~android-message-drafts~3]
class DraftStoreTest {

    private val store = DraftStore(createTempDirectory("drafts"))

    @Test
    fun `kept drafts survive a new store on the same directory, oldest first`() {
        val dir = createTempDirectory("drafts")
        DraftStore(dir).add("jabref/fix-npe", "first")
        DraftStore(dir).add("jabref/fix-npe", "second\nwith a second line")

        assertThat(DraftStore(dir).load("jabref/fix-npe")).containsExactly("first", "second\nwith a second line")
    }

    @Test
    fun `drafts are per task`() {
        store.add("a/one", "for one")
        store.add("a/two", "for two")

        assertThat(store.load("a/one")).containsExactly("for one")
        assertThat(store.load("a/two")).containsExactly("for two")
    }

    @Test
    fun `blank text and a text already kept are not added again`() {
        store.add("t", "same")
        store.add("t", "same")
        store.add("t", "  \n")

        assertThat(store.load("t")).containsExactly("same")
    }

    @Test
    fun `remove drops only that draft`() {
        store.add("t", "keep")
        store.add("t", "drop")

        assertThat(store.remove("t", "drop")).containsExactly("keep")
        assertThat(store.load("t")).containsExactly("keep")
        assertThat(store.remove("t", "not there")).containsExactly("keep")
    }

    @Test
    fun `the message box's text is stored per task, and a blank box removes it`() {
        val dir = createTempDirectory("drafts")
        DraftStore(dir).saveUnsent("jabref/fix-npe", "half-typed\nsecond line")
        DraftStore(dir).saveUnsent("jabref/other", "elsewhere")

        assertThat(DraftStore(dir).loadUnsent("jabref/fix-npe")).isEqualTo("half-typed\nsecond line")

        DraftStore(dir).saveUnsent("jabref/fix-npe", "")
        assertThat(DraftStore(dir).loadUnsent("jabref/fix-npe")).isEmpty()
        assertThat(DraftStore(dir).loadUnsent("jabref/other")).isEqualTo("elsewhere")
    }
}
