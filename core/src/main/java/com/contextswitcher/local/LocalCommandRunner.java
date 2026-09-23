package com.contextswitcher.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Runs a command on the local machine (no shell), draining stdout/stderr
/// concurrently so output larger than the OS pipe buffer cannot deadlock
/// `waitFor`. The local analog of the ssh runner, for desktop-integration
/// actions that drive local tools such as `powershell`.
// [impl->dsn~local-terminal-focus~5]
public class LocalCommandRunner {

    /// Outcome of one local command; `stderr` is the debugging story on failure.
    public record LocalResult(int exitCode, String stdout, String stderr) {

        public boolean ok() {
            return exitCode == 0;
        }
    }

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    /// Programs that exist on Windows only. Off Windows they are not
    /// attempted at all: the desktop integrations that drive them are
    /// Windows-only by design, and one of them (the active-desktop read)
    /// polls — a spawn attempt per tick filled the log with
    /// `Cannot run program "powershell"` and told the user nothing.
    private static final Set<String> WINDOWS_ONLY =
            Set.of("powershell", "rundll32", "explorer", "wt", "wsl.exe");

    /// Whether this is Windows — the switch every desktop integration that
    /// has one behaviour here and another elsewhere reads.
    public static boolean onWindows() {
        return ON_WINDOWS;
    }

    private static final boolean ON_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private final Duration timeout;

    public LocalCommandRunner() {
        this(DEFAULT_TIMEOUT);
    }

    public LocalCommandRunner(Duration timeout) {
        this.timeout = timeout;
    }

    public LocalResult run(List<String> command) {
        return run(command, null);
    }

    /// As [#run(List)], but with `directory` as the process's working
    /// directory — for tools that pick their subject from the current
    /// directory rather than from an argument (`gh` has no `-C`).
    public LocalResult run(List<String> command, @Nullable Path directory) {
        return run(command, directory, null);
    }

    /// As [#run(List, Path)], but with `input` piped to the process's stdin
    /// (the local half of [com.contextswitcher.ssh.SshCommandRunner#runWithInput]
    /// — `tmux load-buffer -`, `base64 -d`). The write runs on the same pool
    /// as the output drains: a payload larger than the OS pipe buffer would
    /// otherwise block here while the process blocks on output nobody reads.
    public LocalResult run(List<String> command, @Nullable Path directory,
            byte @Nullable [] input) {
        String program = command.isEmpty() ? "" : command.getFirst();
        if (!ON_WINDOWS && WINDOWS_ONLY.contains(program)) {
            Logger.debug("Not running the Windows-only {} on this OS", program);
            return new LocalResult(-1, "", program + " is Windows-only");
        }
        Logger.debug("Running local: {}", String.join(" ", command));
        // Fixed pool, not virtual threads with try-with-resources: Android's
        // runtime (a :core consumer, MADR 0026) has neither Thread.ofVirtual
        // nor ExecutorService.close().
        ExecutorService io = Executors.newFixedThreadPool(3);
        try {
            Process process = new ProcessBuilder(command).directory(
                    directory == null ? null : directory.toFile()).start();
            if (input == null) {
                process.getOutputStream().close();
            } else {
                io.submit(() -> {
                    try (java.io.OutputStream stdin = process.getOutputStream()) {
                        stdin.write(input);
                    } catch (IOException e) {
                        // A process that exited before reading it all: its
                        // exit code and stderr tell the story, not this.
                        Logger.debug("Cannot write stdin: {}", e.getMessage());
                    }
                });
            }
            Future<String> stdout = io.submit(() ->
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            Future<String> stderr = io.submit(() ->
                    new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                Logger.warn("Timeout after {} for local: {}", timeout, String.join(" ", command));
                return new LocalResult(-1, "", "Timeout after %s".formatted(timeout));
            }
            LocalResult result = new LocalResult(process.exitValue(), stdout.get(), stderr.get());
            if (!result.ok()) {
                Logger.warn("Exit {} for local: {} — stderr: {}", result.exitCode(),
                        String.join(" ", command), result.stderr().strip());
            }
            return result;
        } catch (IOException e) {
            Logger.warn("Cannot start local process: {}", e.getMessage());
            return new LocalResult(-1, "", "Cannot start process: " + e.getMessage());
        } catch (ExecutionException e) {
            Logger.warn("Cannot read local process output: {}", e.getCause().getMessage());
            return new LocalResult(-1, "", "Cannot read output: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new LocalResult(-1, "", "Interrupted");
        } finally {
            io.shutdown();
        }
    }
}
