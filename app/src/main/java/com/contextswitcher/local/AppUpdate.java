package com.contextswitcher.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Restart-to-update: there is no hot reload (a running JVM cannot swap its own
/// JavaFX classes, and the packaged app image is rebuilt as a whole), so the
/// update is a *restart* — the app leaves with [#RESTART_EXIT_CODE] and the
/// wrapper script (`scripts/run-loop.sh` / `.cmd`) pulls, rebuilds and starts
/// it again. Any other exit code ends the script's loop, so a normal quit
/// stays a quit.
///
/// A process-wide flag like `EnergySaver`: the app has one window, and
/// threading a callback through the `MainWindow` constructor for a one-shot
/// "leave now" would buy nothing.
// [impl->dsn~restart-to-update~10]
public final class AppUpdate {

    /// The exit code that means "rebuild and relaunch me". Chosen far away
    /// from the codes a JVM or a shell produces on its own (0, 1, 130, 137).
    public static final int RESTART_EXIT_CODE = 55;

    /// `fetch` talks to the network; the default 15 s is too tight for a
    /// slow line, and nothing waits on this thread.
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(60);

    /// Set to `1` by `scripts/run-loop.sh` / `.cmd` before they start the app:
    /// only then does anything act on [#RESTART_EXIT_CODE]. The environment,
    /// because it is the one thing a wrapper can vary per start — a Gradle
    /// property is fixed at build time and the packaged launcher never runs
    /// Gradle, and a `-D` option lands in jpackage's baked-in `java-options`.
    public static final String RUN_LOOP_ENV = "CONTEXTSWITCHER_RUN_LOOP";

    private static final boolean STARTED_BY_LOOP = startedByLoop(System::getenv);

    private static volatile boolean restartRequested;

    /// The commit the running app started from, or null while unknown. Not the
    /// checkout's `HEAD` at check time: a `git pull` outside the app — or
    /// another session fast-forwarding the checkout — moves `HEAD` up to
    /// upstream while the old build keeps running, and a count from `HEAD` then
    /// said "up to date" and took the restart away (field report 2026-09-13).
    private static volatile @Nullable String runningCommit;

    private AppUpdate() {
    }

    /// Records the commit the app runs, once at startup, before any check.
    // [impl->dsn~restart-to-update~10]
    public static void rememberRunningCommit(Path repo) {
        runningCommit = runningCommit(repo, new LocalCommandRunner(GIT_TIMEOUT)::run);
    }

    /// The full sha `HEAD` names in `repo` now, or null when git cannot say.
    static @Nullable String runningCommit(Path repo,
            Function<List<String>, LocalCommandRunner.LocalResult> git) {
        LocalCommandRunner.LocalResult head =
                git.apply(List.of("git", "-C", repo.toString(), "rev-parse", "HEAD"));
        String sha = head.stdout().strip();
        return head.ok() && !sha.isEmpty() ? sha : null;
    }

    /// The running commit, or `HEAD` while it is not recorded (the first
    /// moments of a start, or git could not say).
    public static String runningCommitOrHead() {
        String commit = runningCommit;
        return commit == null ? "HEAD" : commit;
    }

    /// The git checkout the app runs out of: the nearest ancestor of `start`
    /// holding a `.git` (a directory in a clone, a file in a worktree), or
    /// null when there is none — the app was unzipped somewhere, and there is
    /// nothing to update from.
    public static @Nullable Path repositoryRoot(Path start) {
        for (Path dir = start.toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(".git"))) {
                return dir;
            }
        }
        return null;
    }

    /// How many commits the running app is behind its upstream branch, after a
    /// fetch — counted from [#runningCommitOrHead], not the checkout's current
    /// `HEAD`. Every failure — no network, no upstream, no git — is 0: the
    /// check is an offer, never an error the user has to dismiss.
    // [impl->dsn~restart-to-update~10]
    public static int commitsBehind(Path repo) {
        return commitsBehind(repo, runningCommitOrHead(), new LocalCommandRunner(GIT_TIMEOUT)::run);
    }

    /// As [#commitsBehind(Path)] from `base`, with the git runner injected for tests.
    static int commitsBehind(Path repo, String base,
            Function<List<String>, LocalCommandRunner.LocalResult> git) {
        String dir = repo.toString();
        if (!git.apply(List.of("git", "-C", dir, "fetch", "--quiet")).ok()) {
            return 0;
        }
        LocalCommandRunner.LocalResult behind =
                git.apply(List.of("git", "-C", dir, "rev-list", "--count", base + "..@{u}"));
        if (!behind.ok()) {
            return 0;
        }
        try {
            return Integer.parseInt(behind.stdout().strip());
        } catch (NumberFormatException e) {
            Logger.warn("Cannot read the commit count behind upstream: {}", behind.stdout().strip());
            return 0;
        }
    }

    /// The commit the checkout is on, as `<short sha> (<committer date and
    /// time>)` — what the running app was built from, shown in the status
    /// bar. Null when git cannot say (no git, no commit yet): the line is an
    /// informational one, so it is then simply absent.
    // [impl->dsn~running-commit~3]
    public static @Nullable String headCommit(Path repo) {
        return describe(repo, "HEAD");
    }

    /// As [#headCommit(Path)], with the git runner injected for tests.
    static @Nullable String headCommit(Path repo,
            Function<List<String>, LocalCommandRunner.LocalResult> git) {
        return describe(repo, "HEAD", git);
    }

    /// Any commit in the status bar's `<short sha> (<date> <time>)` form.
    public static @Nullable String describe(Path repo, String commit) {
        return describe(repo, commit, new LocalCommandRunner(GIT_TIMEOUT)::run);
    }

    static @Nullable String describe(Path repo, String commit,
            Function<List<String>, LocalCommandRunner.LocalResult> git) {
        LocalCommandRunner.LocalResult head = git.apply(List.of("git", "-C", repo.toString(),
                "show", "--no-patch", "--date=format:%Y-%m-%d %H:%M", "--format=%h (%cd)", commit));
        String text = head.stdout().strip();
        return head.ok() && !text.isEmpty() ? text : null;
    }


    /// Leaves the application the ordinary way — `Application.stop` still runs,
    /// so the task git sync and the window geometry are saved — and makes the
    /// exit code [#RESTART_EXIT_CODE].
    public static void requestRestart() {
        Logger.info("Restart for update requested");
        restartRequested = true;
        Platform.exit();
    }

    /// Whether a run loop started this process, read once at startup.
    // [impl->dsn~restart-label-by-launch~1]
    public static boolean startedByLoop() {
        return STARTED_BY_LOOP;
    }

    /// As [#startedByLoop()], with the environment lookup injected for tests.
    static boolean startedByLoop(Function<String, @Nullable String> env) {
        return "1".equals(env.apply(RUN_LOOP_ENV));
    }

    /// The update window's action button: the exit is 55 either way, but only
    /// a run loop turns it into a restart.
    // [impl->dsn~restart-label-by-launch~1]
    public static String actionLabel(boolean looped) {
        return looped ? "Restart to update" : "Exit to update";
    }

    /// What pressing [#actionLabel] does, for the window's body and the
    /// toolbar tooltip. Keep in step with the `run` task's exit-55 message in
    /// `app/build.gradle.kts`.
    // [impl->dsn~restart-label-by-launch~1]
    public static String actionHint(boolean looped) {
        return looped
                ? "The app quits, and `just run-loop` pulls, rebuilds and starts it again."
                : "The app quits and does not come back by itself — use `just run-loop`,"
                        + " which pulls, rebuilds and starts it again.";
    }

    /// Whether [#requestRestart] was called — read by `Launcher` once the FX
    /// toolkit is down.
    public static boolean restartRequested() {
        return restartRequested;
    }
}
