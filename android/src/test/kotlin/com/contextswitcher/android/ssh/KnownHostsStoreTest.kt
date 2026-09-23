package com.contextswitcher.android.ssh

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

// [utest->dsn~android-ssh-runner~1]
class KnownHostsStoreTest {

    private fun store(): KnownHostsStore =
        KnownHostsStore(File(createTempDirectory("known-hosts").toFile(), "known_hosts.properties"))

    @Test
    fun `first contact records the fingerprint and reports FirstContact`() {
        val store = store()
        val result = store.verify("example.com", "SHA256:aaaa")
        assertThat(result).isEqualTo(KnownHostsStore.Result.FirstContact)
    }

    @Test
    fun `a matching second contact reports Match`() {
        val store = store()
        store.verify("example.com", "SHA256:aaaa")
        val result = store.verify("example.com", "SHA256:aaaa")
        assertThat(result).isEqualTo(KnownHostsStore.Result.Match)
    }

    @Test
    fun `a changed fingerprint reports Mismatch naming both`() {
        val store = store()
        store.verify("example.com", "SHA256:aaaa")
        val result = store.verify("example.com", "SHA256:bbbb")
        assertThat(result).isEqualTo(KnownHostsStore.Result.Mismatch("SHA256:aaaa", "SHA256:bbbb"))
    }

    @Test
    fun `clear forgets every host, next contact is first contact again`() {
        val store = store()
        store.verify("example.com", "SHA256:aaaa")
        store.clear()
        val result = store.verify("example.com", "SHA256:bbbb")
        assertThat(result).isEqualTo(KnownHostsStore.Result.FirstContact)
    }

    @Test
    fun `entries survive across store instances backed by the same file`() {
        val file = File(createTempDirectory("known-hosts-2").toFile(), "known_hosts.properties")
        KnownHostsStore(file).verify("host-a", "SHA256:one")
        val result = KnownHostsStore(file).verify("host-a", "SHA256:one")
        assertThat(result).isEqualTo(KnownHostsStore.Result.Match)
    }
}
