package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.tinylog.Logger;

import com.contextswitcher.tasks.title.TaskTitleSummarizer;
import com.contextswitcher.tasks.title.TaskTitles;

/// A task title from the locally installed, logged-in `claude` CLI in print
/// mode — the system-tool approach of MADR 0003, as `gh` is for PR titles
/// (MADR 0035). No API key and no SDK: the user's own Claude login pays for it.
///
/// The description goes in on **stdin**, never as an argument: on Windows
/// the call runs through `cmd`, which would re-parse quotes, `%VAR%`, `<`,
/// `>` and `&` in it, and newlines survive a pipe but not a command line.
// [impl->dsn~task-create-local-title~1]
public final class ClaudeCliSummarizer implements TaskTitleSummarizer {

    /// A cold `claude -p` start plus a small model's answer is a few seconds;
    /// the margin covers a slow login refresh without leaving a hung process.
    static final Duration TIMEOUT = Duration.ofSeconds(45);

    static final String INSTRUCTION = "Reply with only a short task title (3 to 6 words)"
            + " for the task description on stdin. No quotes, no trailing punctuation, nothing else.";

    private final LocalCommandRunner runner;
    private final boolean windows;
    private final Path directory;

    /// Runs in the temp directory, not a workspace, so no project `CLAUDE.md`
    /// steers the answer.
    public ClaudeCliSummarizer() {
        this(new LocalCommandRunner(TIMEOUT), LocalCommandRunner.onWindows(),
                Path.of(System.getProperty("java.io.tmpdir")));
    }

    ClaudeCliSummarizer(LocalCommandRunner runner, boolean windows, Path directory) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.windows = windows;
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    @Override
    public Optional<String> summarize(String description) {
        byte[] input = Objects.requireNonNull(description, "description").strip()
                .getBytes(StandardCharsets.UTF_8);
        LocalCommandRunner.LocalResult result = runner.run(command(windows), directory, input);
        if (!result.ok()) {
            Logger.info("No task title from claude (exit {}): {}",
                    result.exitCode(), result.stderr().strip());
            return Optional.empty();
        }
        return TaskTitles.sanitize(result.stdout());
    }

    /// `cmd /c` on Windows because `claude` may be an npm `.cmd` shim, which
    /// `ProcessBuilder` cannot start without a shell. Elsewhere the absolute
    /// path, because a GUI-launched app may not have `~/.local/bin` on `PATH`.
    /// `--setting-sources ""` keeps user hooks and memory files out of the
    /// answer; `--tools ""` leaves the model nothing to do but reply.
    static List<String> command(boolean windows) {
        List<String> command = new ArrayList<>(windows
                ? List.of("cmd", "/c", "claude")
                : List.of(RequiredTools.resolve("claude")));
        command.addAll(List.of("-p", "--model", "haiku", "--tools", "",
                "--setting-sources", "", "--no-session-persistence",
                "--output-format", "text", INSTRUCTION));
        return List.copyOf(command);
    }
}
