package com.contextswitcher.ssh;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Runs a command on a remote host via the system `ssh` executable, so the
/// user's `~/.ssh/config` (aliases, keys, agent, ControlMaster) applies as-is.
/// `BatchMode=yes` fails fast instead of prompting for a password.
///
/// On Windows the built-in OpenSSH client (`%SystemRoot%\System32\OpenSSH`)
/// is addressed by absolute path: `ssh` from PATH may resolve to an MSYS
/// build (Git for Windows, Cygwin), whose runtime re-parses the argv with
/// POSIX quoting rules and strips the single quotes protecting remote
/// arguments — a `tmux -F '#{…}'` format then reaches the remote shell
/// unquoted, `#` starts a comment there, and tmux answers in its default
/// format (exit 0, silently useless).
// [impl->dsn~ssh-command-runner~6]
public class ProcessSshRunner implements SshCommandRunner {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /// Upper bound on `ssh` processes alive at once, across every runner
    /// instance. sshd's default `MaxStartups 10:30:100` starts refusing
    /// connections at ten unauthenticated ones, and a burst of local
    /// `ssh.exe` starts (each a key exchange) starves the FX thread — the
    /// 2026-08-27 startup freeze was ~60 per-task lookups fired in one tick.
    /// Six leaves room for the terminal mirror and the user's own sessions;
    /// callers that need many answers batch them into one call instead.
    static final int MAX_CONCURRENT = 6;
    private static final Semaphore SLOTS = new Semaphore(MAX_CONCURRENT, true);

    private static final String SSH = resolveSsh(
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"),
            Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"),
                    "System32", "OpenSSH", "ssh.exe"));

    /// The Windows OpenSSH client by absolute path when this is Windows and
    /// it is installed (see class comment: MSYS ssh builds mangle quoting),
    /// else the platform's PATH-resolved `ssh`.
    static String resolveSsh(boolean windows, Path windowsOpenSsh) {
        if (windows && Files.exists(windowsOpenSsh)) {
            return windowsOpenSsh.toString();
        }
        return "ssh";
    }

    /// The resolved ssh executable — also for local argv built outside this
    /// class (the terminal mirror's pty attach), which must not fall back to
    /// a PATH-resolved MSYS ssh either: it eats the `\;` escaping and its
    /// output stalls under a ConPTY, freezing the mirror on a stale frame.
    public static String sshExecutable() {
        return SSH;
    }

    private final Duration timeout;

    public ProcessSshRunner() {
        this(DEFAULT_TIMEOUT);
    }

    public ProcessSshRunner(Duration timeout) {
        this.timeout = timeout;
    }

    /// The full local argv for `ssh <host> <remoteCommand…>`; `-n` keeps ssh
    /// away from stdin.
    ///
    /// Remote command parts must not contain double quotes: Java's Windows
    /// argv encoding wraps space-containing arguments in `"…"` without
    /// escaping embedded quotes, so ssh.exe's C-runtime parser swallows them
    /// and the remote shell sees a different command. Use single quotes (kept
    /// verbatim) or quote-free constructs instead.
    public static List<String> buildCommand(String host, List<String> remoteCommand) {
        return buildCommand(host, remoteCommand, false);
    }

    /// As [#buildCommand(String, List)], but without `-n` when the remote
    /// command reads stdin (`tmux load-buffer -`, `base64 -d`).
    static List<String> buildCommand(String host, List<String> remoteCommand,
            boolean withStdin) {
        remoteCommand.stream().filter(part -> part.contains("\"")).forEach(part ->
                Logger.warn("Remote command part contains a double quote, "
                        + "which Windows argv encoding will mangle: {}", part));
        List<String> command = new ArrayList<>();
        command.add(SSH);
        if (!withStdin) {
            command.add("-n");
        }
        command.add("-o");
        command.add("BatchMode=yes");
        command.add(host);
        command.addAll(remoteCommand);
        return command;
    }

    @Override
    public SshResult run(String host, List<String> remoteCommand) {
        return execute(buildCommand(host, remoteCommand, false), null);
    }

    /// Runs a remote command with `input` piped to its stdin — the channel
    /// for payloads (message text, base64 image data) that must not appear
    /// in an argv, where quoting rules would mangle them.
    @Override
    public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
        return execute(buildCommand(host, remoteCommand, true), input);
    }

    private SshResult execute(List<String> command, byte @Nullable [] input) {
        try {
            SLOTS.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SshResult(-1, "", "Interrupted");
        }
        try {
            return executeNow(command, input);
        } finally {
            SLOTS.release();
        }
    }

    private SshResult executeNow(List<String> command, byte @Nullable [] input) {
        Logger.debug("Running: {}", String.join(" ", command));
        // Fixed pool, not virtual threads with try-with-resources: Android's
        // runtime (a :core consumer, MADR 0026) has neither Thread.ofVirtual
        // nor ExecutorService.close().
        ExecutorService io = Executors.newFixedThreadPool(3);
        try {
            Process process = new ProcessBuilder(command).start();
            if (input != null) {
                // Written on a worker: input larger than the pipe buffer
                // would otherwise deadlock against the undrained streams.
                io.submit(() -> {
                    try (var stdin = process.getOutputStream()) {
                        stdin.write(input);
                    }
                    return null;
                });
            } else {
                process.getOutputStream().close();
            }
            // Streams must be drained WHILE the process runs: output larger
            // than the OS pipe buffer would otherwise deadlock waitFor.
            Future<String> stdout = io.submit(() ->
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            Future<String> stderr = io.submit(() ->
                    new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                Logger.warn("Timeout after {} for: {}", timeout, String.join(" ", command));
                return new SshResult(-1, "", "Timeout after %s".formatted(timeout));
            }
            SshResult result = new SshResult(process.exitValue(), stdout.get(), stderr.get());
            if (result.ok()) {
                Logger.debug("Exit 0 for: {}", String.join(" ", command));
            } else {
                Logger.warn("Exit {} for: {} — stderr: {}", result.exitCode(),
                        String.join(" ", command), result.stderr().strip());
            }
            return result;
        } catch (IOException e) {
            Logger.warn("Cannot start ssh: {}", e.getMessage());
            return new SshResult(-1, "", "Cannot start ssh: " + e.getMessage());
        } catch (ExecutionException e) {
            Logger.warn("Cannot read ssh output: {}", e.getCause().getMessage());
            return new SshResult(-1, "", "Cannot read ssh output: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SshResult(-1, "", "Interrupted");
        } finally {
            io.shutdown();
        }
    }
}
