package com.contextswitcher.android.ssh

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPair
import java.time.Duration
import kotlin.io.path.createTempDirectory
import org.apache.sshd.common.util.security.SecurityUtils as MinaSecurityUtils
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/// Round-trips [SshjCommandRunner] against a real (embedded) sshd — Apache
/// MINA sshd, test-scope only. Proves the exec channel and stdin-feeding
/// [SshjCommandRunner.runWithInput] actually work end to end, not just
/// against mocks.
// [utest->dsn~android-ssh-runner~2]
class SshjCommandRunnerTest {

    private lateinit var server: SshServer
    private lateinit var keyPair: KeyPair
    private lateinit var hostKeysFile: File
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        keyPair = MinaSecurityUtils.getKeyPairGenerator("RSA").generateKeyPair()
        server = SshServer.setUpDefaultServer()
        server.keyPairProvider = SimpleGeneratorHostKeyProvider(
            File(createTempDirectory("sshd-hostkey").toFile(), "hostkey.ser").toPath())
        // "stranger" has no authorized key anywhere — the rejected-key tests.
        server.publickeyAuthenticator = { user, offered, _ -> offered == keyPair.public && user != "stranger" }
        server.commandFactory = org.apache.sshd.server.command.CommandFactory { _, command -> ShellCommand(command) }
        server.forwardingFilter = org.apache.sshd.server.forward.AcceptAllForwardingFilter.INSTANCE
        server.start()
        port = server.port
        hostKeysFile = File(createTempDirectory("known-hosts").toFile(), "known_hosts.properties")
    }

    @AfterEach
    fun stopServer() {
        server.stop(true)
    }

    private fun privateKeyPem(): String {
        val writer = java.io.StringWriter()
        org.bouncycastle.util.io.pem.PemWriter(writer).use { pem ->
            pem.writeObject(org.bouncycastle.util.io.pem.PemObject("PRIVATE KEY", keyPair.private.encoded))
        }
        return writer.toString()
    }

    @Test
    fun `run executes a remote command and captures stdout`() {
        val runner = SshjCommandRunner({ privateKeyPem() }, KnownHostsStore(hostKeysFile),
            Duration.ofSeconds(5))
        val result = runner.run("tester@127.0.0.1:$port", listOf("echo", "hello"))
        assertThat(result.ok()).isTrue()
        assertThat(result.stdout().trim()).isEqualTo("hello")
    }

    @Test
    fun `a remote with a jump host is reached through it`() {
        // The embedded sshd is its own jump host: the runner opens a
        // direct-tcpip channel on the "jump" connection to the "remote".
        val runner = SshjCommandRunner({ privateKeyPem() }, KnownHostsStore(hostKeysFile),
            jumpHosts = { "me@127.0.0.1:$port via me@localhost:$port" })
        val result = runner.run("me@127.0.0.1:$port", listOf("echo", "via"))

        assertThat(result.ok()).isTrue()
        assertThat(result.stdout().trim()).isEqualTo("via")
    }

    @Test
    fun `jump hosts are looked up per remote, by full destination or host`() {
        val text = "koppor@devbox via koppor@gate.example.org\nlab via me@gate.example.org:2222\nnonsense line\n"
        assertThat(JumpHosts.forRemote(text, "koppor@devbox")).isEqualTo("koppor@gate.example.org")
        assertThat(JumpHosts.forRemote(text, "other@devbox")).isNull()
        assertThat(JumpHosts.forRemote(text, "anyone@lab")).isEqualTo("me@gate.example.org:2222")
        assertThat(JumpHosts.forRemote(text, "anyone@lab:22")).isEqualTo("me@gate.example.org:2222")
        assertThat(JumpHosts.forRemote("", "koppor@devbox")).isNull()
    }

    @Test
    fun `a rejected key names the host and the authorized_keys cure`() {
        val runner = SshjCommandRunner({ privateKeyPem() }, KnownHostsStore(hostKeysFile))
        val result = runner.run("stranger@localhost:$port", listOf("true"))

        assertThat(result.ok()).isFalse()
        assertThat(result.stderr()).startsWith("Cannot connect to stranger@localhost:$port: stranger@localhost:$port rejected the SSH key from settings")
            .contains("~/.ssh/authorized_keys of stranger")
    }

    @Test
    fun `a jump host that rejects the key is named as the one that did`() {
        val runner = SshjCommandRunner({ privateKeyPem() }, KnownHostsStore(hostKeysFile),
            jumpHosts = { "me@127.0.0.1:$port via stranger@localhost:$port" })
        val result = runner.run("me@127.0.0.1:$port", listOf("true"))

        assertThat(result.stderr()).contains("jump host stranger@localhost:$port rejected the SSH key")
    }

    @Test
    fun `check connections reports every jump-host line, reachable or why not`() {
        val text = "me@127.0.0.1:$port via me@localhost:$port\nlab via stranger@localhost:$port\n"
        val lines = JumpHosts.check(text, privateKeyPem(), KnownHostsStore(hostKeysFile))

        assertThat(lines).hasSize(2)
        assertThat(lines[0]).isEqualTo("me@127.0.0.1:$port via me@localhost:$port: reachable")
        assertThat(lines[1]).startsWith("lab via stranger@localhost:$port: Cannot connect to stranger@lab:")
        assertThat(JumpHosts.check("", privateKeyPem(), KnownHostsStore(hostKeysFile))).containsExactly("No jump hosts entered")
    }

    @Test
    fun `a name that does not resolve is explained, not echoed`() {
        val runner = SshjCommandRunner({ privateKeyPem() }, KnownHostsStore(hostKeysFile))
        val result = runner.run("me@nowhere.invalid", listOf("true"))

        assertThat(result.ok()).isFalse()
        assertThat(result.stderr()).startsWith("Cannot connect to me@nowhere.invalid: the name \"nowhere.invalid\" does not resolve")
    }

    @Test
    fun `runWithInput feeds bytes to the remote command's stdin`() {
        val runner = SshjCommandRunner({ privateKeyPem() }, KnownHostsStore(hostKeysFile),
            Duration.ofSeconds(5))
        val result = runner.runWithInput("tester@127.0.0.1:$port", listOf("cat"), "piped-input".toByteArray())
        assertThat(result.ok()).isTrue()
        assertThat(result.stdout().trim()).isEqualTo("piped-input")
    }

    /// Runs `command` (a single already-space-joined shell command line, as
    /// [SshjCommandRunner] sends it) via the local `sh`, wiring its streams
    /// straight to the channel's — good enough for a round-trip test.
    private class ShellCommand(private val command: String) : Command {
        private lateinit var input: InputStream
        private lateinit var output: OutputStream
        private lateinit var error: OutputStream
        private lateinit var callback: org.apache.sshd.server.ExitCallback
        private var process: Process? = null

        override fun setInputStream(input: InputStream) {
            this.input = input
        }

        override fun setOutputStream(output: OutputStream) {
            this.output = output
        }

        override fun setErrorStream(error: OutputStream) {
            this.error = error
        }

        override fun setExitCallback(callback: org.apache.sshd.server.ExitCallback) {
            this.callback = callback
        }

        override fun start(channel: ChannelSession, env: org.apache.sshd.server.Environment) {
            val proc = ProcessBuilder("sh", "-c", command).redirectErrorStream(false).start()
            process = proc
            val copyIn = Thread { input.copyTo(proc.outputStream); proc.outputStream.close() }
            val copyOut = Thread { proc.inputStream.copyTo(output); output.flush() }
            val copyErr = Thread { proc.errorStream.copyTo(error); error.flush() }
            copyIn.start(); copyOut.start(); copyErr.start()
            Thread {
                val exit = proc.waitFor()
                copyOut.join(); copyErr.join()
                callback.onExit(exit)
            }.start()
        }

        override fun destroy(channel: ChannelSession) {
            process?.destroyForcibly()
        }
    }
}
