package com.contextswitcher.android.ssh

import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.ssh.SshCommandRunner.SshResult
import java.net.UnknownHostException
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
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.UserAuthException
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
/// or a bare hostname's default user. What `ProxyJump` does there is the
/// *Jump hosts* setting here ([JumpHosts]): a remote listed with a jump host
/// is reached through an ssh-level `direct-tcpip` channel on a connection to
/// the jump host (same key, same TOFU verification), and the jump host
/// resolves the remote's name — a short LAN name the phone cannot.
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
// [impl->dsn~android-ssh-runner~2]
class SshjCommandRunner(
    private val privateKeyPem: () -> String?,
    private val knownHosts: KnownHostsStore,
    private val timeout: Duration = Duration.ofSeconds(10),
    private val jumpHosts: () -> String = { "" },
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

    /// The jump-host connection a client in [clients] runs through, by the
    /// same key; dropped together with it.
    private val jumps = HashMap<String, SSHClient>()
    private val io: ExecutorService = Executors.newCachedThreadPool()

    override fun run(host: String, remoteCommand: List<String>): SshResult =
        execute(host, remoteCommand, null)

    override fun runWithInput(host: String, remoteCommand: List<String>, input: ByteArray): SshResult =
        execute(host, remoteCommand, input)

    private fun execute(host: String, remoteCommand: List<String>, input: ByteArray?): SshResult {
        val client = try {
            clientFor(host)
        } catch (e: UnknownHostException) {
            // The exception's message is just the bare name (field report
            // 2026-10-27: "Cannot connect to koppor@devbox: devbox"); say what
            // it means — on a phone, nearly always the VPN that resolved it.
            return SshResult(-1, "", "Cannot connect to $host: the name \"${e.message}\" does not resolve on this network" +
                " (VPN such as Tailscale off? else use the full host name or IP)")
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
            synchronized(clients) { drop(host) }
            SshResult(-1, "", "Command on $host failed: ${e.message}")
        }
    }

    private fun clientFor(host: String): SSHClient {
        synchronized(clients) {
            val existing = clients[host]
            if (existing != null && existing.isConnected) {
                return existing
            }
            drop(host)
            val client = connect(host)
            clients[host] = client
            return client
        }
    }

    private fun drop(host: String) {
        clients.remove(host)?.let { runCatching { it.disconnect() } }
        jumps.remove(host)?.let { runCatching { it.disconnect() } }
    }

    private fun connect(host: String): SSHClient {
        val key = SshKeys.parse(privateKeyPem() ?: "")
        val jump = JumpHosts.forRemote(jumpHosts(), host)?.let { via ->
            val (user, hostname, port) = parseDestination(via)
            newClient().also { it.connect(hostname, port); authenticate(it, user, key, "jump host $via") }
        }
        val (username, hostname, port) = parseDestination(host)
        val client = newClient()
        try {
            if (jump == null) client.connect(hostname, port) else client.connectVia(jump.newDirectConnection(hostname, port))
            authenticate(client, username, key, if (jump == null) host else "$host behind the jump host")
        } catch (e: Exception) {
            jump?.let { runCatching { it.disconnect() } }
            throw e
        }
        jump?.let { jumps[host] = it }
        return client
    }

    /// sshj's "Exhausted available authentication methods" names neither the
    /// host nor the cure (field report 2026-10-27, first try with a jump
    /// host): say which hop refused the settings key and where its public
    /// half has to be.
    private fun authenticate(client: SSHClient, user: String, key: KeyProvider, where: String) {
        try {
            client.authPublickey(user, key)
        } catch (e: UserAuthException) {
            throw IllegalStateException("$where rejected the SSH key from settings" +
                " — its public key must be in ~/.ssh/authorized_keys of $user there (${e.message}" +
                (e.cause?.message?.let { "; $it" } ?: "") + ")", e)
        }
    }

    private fun newClient(): SSHClient {
        val client = SSHClient(DefaultConfig())
        client.connectTimeout = timeout.toMillis().toInt()
        client.timeout = timeout.toMillis().toInt()
        client.addHostKeyVerifier(tofuVerifier())
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

/// The *Jump hosts* setting: one remote per line, `user@host via user@jump[:port]`
/// — the phone's stand-in for `~/.ssh/config`'s `ProxyJump`, per remote and
/// Android-only (the desktop's system `ssh` reads the config). A line whose
/// left side names only the host (`devbox via …`) matches any user on it.
// [impl->dsn~android-ssh-runner~2]
object JumpHosts {
    private val LINE = Regex("""^\s*(\S+)\s+via\s+(\S+)\s*$""")

    fun parse(text: String): Map<String, String> =
        text.lines().mapNotNull { LINE.matchEntire(it) }.associate { it.groupValues[1] to it.groupValues[2] }

    /// The settings screen's *Check connections*: runs `true` on every
    /// remote the (unsaved) text names, through its jump host, with the
    /// (unsaved) key — one line per entry, "reachable" or the runner's
    /// error. Saving is allowed regardless: a host may well be unreachable
    /// from where the phone is right now.
    fun check(text: String, key: String, knownHosts: KnownHostsStore): List<String> {
        val table = parse(text)
        if (table.isEmpty()) return listOf("No jump hosts entered")
        val runner = SshjCommandRunner({ key }, knownHosts, jumpHosts = { text })
        return table.map { (remote, via) ->
            val target = if ('@' in remote) remote else "${via.substringBefore('@')}@$remote"
            val result = runner.run(target, listOf("true"))
            "$remote via $via: " + if (result.ok()) "reachable" else result.stderr().trim()
        }
    }

    fun forRemote(text: String, remote: String): String? {
        val table = parse(text)
        return table[remote] ?: table[remote.substringAfter('@')] ?: table[remote.substringBefore(':')]
            ?: table[remote.substringAfter('@').substringBefore(':')]
    }
}

/// The private key as pasted into the settings. A phone paste picks up
/// indentation, `\r` and stray blanks, and sshj parses the key only *at auth
/// time*, where a broken one hides behind "Exhausted available
/// authentication methods" (field report 2026-10-27: "maybe during fiddling
/// around, something got messy? (linebreaks?)"). So: whitespace per line
/// normalized, parsed up front with a message that says so, and the settings
/// screen shows [describe] live under the field.
// [impl->dsn~android-ssh-runner~2]
object SshKeys {
    private val config = DefaultConfig()

    fun normalize(pem: String): String = pem.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    /// The parsed key, or an `IllegalStateException` in the user's words.
    fun parse(pem: String): KeyProvider {
        val text = normalize(pem)
        if (text.isEmpty()) throw IllegalStateException("No SSH private key configured in settings")
        return try {
            SSHClient(config).loadKeys(text, null, null).also { it.public }
        } catch (e: Exception) {
            throw IllegalStateException("The SSH private key in settings does not parse (${e.message ?: e.javaClass.simpleName})" +
                " — paste it again, from -----BEGIN to -----END, e.g. from `cat ~/.ssh/id_ed25519`", e)
        }
    }

    /// What the settings screen shows under the key field: the key's type
    /// when it parses, else [parse]'s complaint; null for an empty field.
    fun describe(pem: String): String? {
        if (normalize(pem).isEmpty()) return null
        return try {
            "Key parses: ${parse(pem).type}"
        } catch (e: IllegalStateException) {
            e.message
        }
    }
}
