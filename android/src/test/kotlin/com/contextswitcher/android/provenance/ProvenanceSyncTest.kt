package com.contextswitcher.android.provenance

import com.contextswitcher.provenance.ProvenanceRecord
import com.contextswitcher.provenance.ProvenanceStore
import java.io.File
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/// Records from the phone reach an empty provenance repository, survive a round
/// without network, and merge with records another machine pushed meanwhile.
// [utest->dsn~provenance-repository~1]
class ProvenanceSyncTest {

    private fun record(copy: File, sender: String, at: String) = ProvenanceStore.write(copy.toPath(),
        ProvenanceRecord(Instant.parse(at), sender, "work/fix", "Fix", null, "me@box", "@1", null, null, null, "hi"))

    @Test
    fun `records reach an empty remote and merge with another machine's`(@TempDir tmp: File) {
        val bare = File(tmp, "log.git")
        Git.init().setBare(true).setDirectory(bare).setInitialBranch("main").call().close()
        val phone = File(tmp, "phone")
        val desktop = File(tmp, "desktop")
        val sync = ProvenanceSync()

        assertThat(sync.sync(phone, bare.absolutePath, "u", "t")).isNull()
        record(phone, "android-pixel", "2026-09-17T10:00:00Z")
        assertThat(sync.sync(phone, bare.absolutePath, "u", "t")).isNull()

        assertThat(sync.sync(desktop, bare.absolutePath, "u", "t")).isNull()
        record(desktop, "desktop-box", "2026-09-17T10:05:00Z")
        assertThat(sync.sync(desktop, bare.absolutePath, "u", "t")).isNull()

        record(phone, "android-pixel", "2026-09-17T10:06:00Z")
        val gone = File(tmp, "gone.git")
        bare.renameTo(gone)
        assertThat(sync.sync(phone, bare.absolutePath, "u", "t")).`as`("offline").isNotNull()
        gone.renameTo(bare)
        assertThat(sync.sync(phone, bare.absolutePath, "u", "t")).isNull()

        val check = File(tmp, "check")
        Git.cloneRepository().setURI(bare.absolutePath).setDirectory(check).call().close()
        assertThat(File(check, "work/fix").list()!!.sorted()).containsExactly(
            "20260917T100000.000Z-android-pixel.md", "20260917T100500.000Z-desktop-box.md", "20260917T100600.000Z-android-pixel.md")
    }
}
