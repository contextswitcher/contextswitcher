package com.contextswitcher.android.ssh

import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.ssh.SshCommandRunner.SshResult
import java.security.PublicKey
import java.security.Security
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider

/// Thrown by [SshjCommandRunner]'s [HostKeyVerifier] when a host's offered
/// key fingerprint does not match the one recorded on first contact — a
/// possible MITM, never silently accepted.
class HostKeyMismatchException(message: String) : RuntimeException(message)

/// In-process SSH client for the Android app (MADR 0027): the phone has no
/// `ssh` binary, so this implements [SshCommandRunner] with sshj instead of
/// [com.contextswitcher.ssh.ProcessSshRunner]'s `ProcessBuilder`. One
/// connected [SSHClient] per `host` is kept in [clients], reconnecting when
/// a stored one has died; a command is a fresh exec channel on that
/// connection.
///
/// `host` must be `user@hostname` (optionally `:port`) — unlike the
/// desktop's system `ssh`, there is no `~/.ssh/config` to resolve an alias
/// or a bare hostname's default user.
///
/// Auth is a single OpenSSH private key pasted into settings, held in
/// app-private storage exactly like the task-repo sync's PAT
/// ([com.contextswitcher.android.settings.SettingsStore] — known ceiling,
/// move both to Android Keystore before a wider release) with no passphrase
/// support (ceiling: a passphrase-protected key fails auth; add a
/// [net.schmizz.sshj.userauth.password.PasswordFinder] prompt later).
///
/// Host keys are verified TOFU-style through [KnownHostsStore]: the first
/// connection to a host records its fingerprint, every later one must match
/// exactly or the connection hard-fails via [HostKeyMismatchException] —
/// never an always-accept verifier, this is a security boundary.
// [impl->dsn~android-ssh-runner~1]
class SshjCommandRunner(
    private val privateKeyPem: () -> String?,
    private val knownHosts: KnownHostsStore,
    private val timeout: Duration = Duration.ofSeconds(10),
) : SshCommandRunner {

    companion object {
        // Android's built-in "AndroidOpenSSL"/BC provider is a stripped
        // subset (missing algorithms sshj's DefaultConfig probes for, e.g.
        // some KEX/signature schemes) and, on some OEM images, shadows a
        // same-named "BC" provider added later. Registering a full
        // BouncyCastle at the highest priority once, before any SSHClient
        // is built, makes sshj's provider lookups resolve to the complete
        // implementation instead — the standard workaround for sshj (and
        // most JCE-heavy libraries) on Android.
        init {
            if (Security.getProvider("BC") != null) {
                Security.removeProvider("BC")
            }
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
    }

    private val clients = HashMap<String, SSHClient>()
    private val io: ExecutorService = Executors.newCachedThreadPool()

    override fun run(host: String, remoteCommand: List<String>): SshResult =
        execute(host, remoteCommand, null)

    override fun runWithInput(host: String, remoteCommand: List<String>, input: ByteArray): SshResult =
        execute(host, remoteCommand, input)

    private fun execute(host: String, remoteCommand: List<String>, input: ByteArray?): SshResult {
        val client = try {
            clientFor(host)
        } catch (e: Exception) {
            return SshResult(-1, "", "Cannot connect to $host: ${e.message}")
        }
        return try {
            client.startSession().use { session ->
                val command = session.exec(remoteCommand.joinToString(" "))
                val stdout = io.submit<String> { command.inputStream.readBytes().toString(Charsets.UTF_8) }
                val stderr = io.submit<String> { command.errorStream.readBytes().toString(Charsets.UTF_8) }
                if (input != null) {
                    command.outputStream.use { it.write(input) }
                } else {
                    command.outputStream.close()
                }
                command.join(timeout.seconds, TimeUnit.SECONDS)
                val exit = command.exitStatus ?: -1
                SshResult(exit, stdout.get(timeout.seconds, TimeUnit.SECONDS),
                    stderr.get(timeout.seconds, TimeUnit.SECONDS))
            }
        } catch (e: Exception) {
            // The connection may be wedged (a dropped network, a timed-out
            // channel); drop it so the next call reconnects rather than
            // reusing a client stuck in a bad state.
            synchronized(clients) { clients.remove(host) }
            runCatching { client.disconnect() }
            SshResult(-1, "", "Command on $host failed: ${e.message}")
        }
    }

    private fun clientFor(host: String): SSHClient {
        synchronized(clients) {
            val existing = clients[host]
            if (existing != null && existing.isConnected) {
                return existing
            }
            existing?.let { runCatching { it.disconnect() } }
            val client = connect(host)
            clients[host] = client
            return client
        }
    }

    private fun connect(host: String): SSHClient {
        val (username, hostname, port) = parseDestination(host)
        val key = privateKeyPem()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No SSH private key configured in settings")
        val client = SSHClient(DefaultConfig())
        client.connectTimeout = timeout.toMillis().toInt()
        client.timeout = timeout.toMillis().toInt()
        client.addHostKeyVerifier(tofuVerifier())
        client.connect(hostname, port)
        client.authPublickey(username, client.loadKeys(key, null, null))
        return client
    }

    private fun tofuVerifier(): HostKeyVerifier = object : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val fingerprint = SecurityUtils.getFingerprint(key)
            return when (val result = knownHosts.verify("$hostname:$port", fingerprint)) {
                is KnownHostsStore.Result.FirstContact, KnownHostsStore.Result.Match -> true
                is KnownHostsStore.Result.Mismatch -> throw HostKeyMismatchException(
                    "Host key for $hostname:$port changed: known ${result.known}, offered ${result.offered}. " +
                        "If this is expected (host reinstalled), clear known hosts in settings.")
            }
        }

        // No known-hosts file to consult for a "try these algorithms first"
        // hint — TOFU has nothing until the first successful [verify].
        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
    }

    private data class Destination(val username: String, val hostname: String, val port: Int)

    private fun parseDestination(host: String): Destination {
        val at = host.indexOf('@')
        require(at > 0) { "SSH destination must be user@host (got \"$host\"); ~/.ssh/config aliases are not resolved" }
        val username = host.substring(0, at)
        val rest = host.substring(at + 1)
        val colon = rest.lastIndexOf(':')
        return if (colon > 0) {
            Destination(username, rest.substring(0, colon), rest.substring(colon + 1).toInt())
        } else {
            Destination(username, rest, 22)
        }
    }
}
