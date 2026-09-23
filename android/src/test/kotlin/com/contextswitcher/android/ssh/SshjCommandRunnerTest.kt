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
// [utest->dsn~android-ssh-runner~1]
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
        server.publickeyAuthenticator = { _, offered, _ -> offered == keyPair.public }
        server.commandFactory = org.apache.sshd.server.command.CommandFactory { _, command -> ShellCommand(command) }
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
