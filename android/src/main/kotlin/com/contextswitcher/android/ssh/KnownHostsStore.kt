package com.contextswitcher.android.ssh

import java.io.File
import java.util.Properties

/// Trust-on-first-use host key store: `host -> fingerprint` persisted as a
/// flat `.properties` file under the app's private storage. First contact
/// with a host records its fingerprint; every later contact must match
/// exactly, or [verify] fails — this is the only host-key check the app
/// does, so it must never be bypassed with an always-accept verifier.
// [impl->dsn~android-ssh-runner~1]
class KnownHostsStore(private val file: File) {

    sealed interface Result {
        data object FirstContact : Result
        data object Match : Result
        data class Mismatch(val known: String, val offered: String) : Result
    }

    /// Checks `fingerprint` against the stored one for `host`, recording it
    /// on first contact.
    @Synchronized
    fun verify(host: String, fingerprint: String): Result {
        val known = load()
        val existing = known.getProperty(host)
        if (existing == null) {
            known.setProperty(host, fingerprint)
            save(known)
            return Result.FirstContact
        }
        if (existing == fingerprint) {
            return Result.Match
        }
        return Result.Mismatch(existing, fingerprint)
    }

    /// Forgets every recorded host — the settings screen's "Clear known
    /// hosts" action; the next connection to any host is a fresh first
    /// contact.
    @Synchronized
    fun clear() {
        file.delete()
    }

    private fun load(): Properties {
        val props = Properties()
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
        return props
    }

    private fun save(props: Properties) {
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, "ContextSwitcher known SSH hosts (TOFU)") }
    }
}
