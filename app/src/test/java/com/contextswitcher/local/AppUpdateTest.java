package com.contextswitcher.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~restart-to-update~11]
class AppUpdateTest {

    /// A git that answers `stdout` to every call and records what it was asked.
    private static Function<List<String>, LocalCommandRunner.LocalResult> git(
            List<List<String>> calls, int exitCode, String stdout) {
        return command -> {
            calls.add(command);
            return new LocalCommandRunner.LocalResult(exitCode, stdout, "");
        };
    }

    @Test
    void fetchesAndCountsTheCommitsBehindUpstream(@TempDir Path repo) {
        List<List<String>> calls = new ArrayList<>();

        assertThat(AppUpdate.commitsBehind(repo, "HEAD", git(calls, 0, "3\n"))).isEqualTo(3);
        assertThat(calls).containsExactly(
                List.of("git", "-C", repo.toString(), "fetch", "--quiet"),
                List.of("git", "-C", repo.toString(), "rev-list", "--count", "HEAD..@{u}"));
    }

    /// The count starts at the commit the app runs, so a checkout pulled
    /// outside the app still counts the commits the running build lacks.
    // [utest->dsn~restart-to-update~11]
    @Test
    void countsFromTheRunningCommitNotTheCheckoutHead(@TempDir Path repo) {
        List<List<String>> calls = new ArrayList<>();

        assertThat(AppUpdate.commitsBehind(repo, "71111303abc", git(calls, 0, "2\n"))).isEqualTo(2);
        assertThat(calls.getLast())
                .containsExactly("git", "-C", repo.toString(), "rev-list", "--count", "71111303abc..@{u}");
    }

    /// The running commit is the full sha `HEAD` names at startup, or nothing
    /// when git cannot say — the check then falls back to `HEAD`.
    // [utest->dsn~restart-to-update~11]
    @Test
    void runningCommitIsTheShaHeadNamesAtStartup(@TempDir Path repo) {
        List<List<String>> calls = new ArrayList<>();

        assertThat(AppUpdate.runningCommit(repo, git(calls, 0, "71111303abcdef\n"))).isEqualTo("71111303abcdef");
        assertThat(calls).containsExactly(List.of("git", "-C", repo.toString(), "rev-parse", "HEAD"));
        assertThat(AppUpdate.runningCommit(repo, git(new ArrayList<>(), 128, ""))).isNull();
    }

    /// No network, no upstream, no git: the check is an offer, so every
    /// failure reads as "up to date" instead of bothering the user.
    @Test
    void failingGitIsUpToDate(@TempDir Path repo) {
        List<List<String>> calls = new ArrayList<>();
        assertThat(AppUpdate.commitsBehind(repo, "HEAD", git(calls, 1, ""))).isZero();
        assertThat(calls).hasSize(1);

        assertThat(AppUpdate.commitsBehind(repo, "HEAD", git(new ArrayList<>(), 0, "not a number"))).isZero();
    }

    /// The status-bar line: the short sha and the commit date and time, and
    /// nothing at all when git cannot answer (an app unzipped somewhere).
    // [utest->dsn~running-commit~3]
    @Test
    void headCommitIsTheShortShaAndDate(@TempDir Path repo) {
        List<List<String>> calls = new ArrayList<>();

        assertThat(AppUpdate.headCommit(repo, git(calls, 0, "c2cbf7d (2026-09-10 14:32)\n")))
                .isEqualTo("c2cbf7d (2026-09-10 14:32)");
        assertThat(calls).containsExactly(List.of("git", "-C", repo.toString(),
                "show", "--no-patch", "--date=format:%Y-%m-%d %H:%M", "--format=%h (%cd)", "HEAD"));

        assertThat(AppUpdate.headCommit(repo, git(new ArrayList<>(), 1, ""))).isNull();
        assertThat(AppUpdate.headCommit(repo, git(new ArrayList<>(), 0, " \n"))).isNull();
    }

    /// The label follows how the process was started; nothing else does.
    // [utest->dsn~restart-label-by-launch~1]
    @Test
    void labelFollowsTheRunLoopVariable() {
        assertThat(AppUpdate.startedByLoop(Map.of(AppUpdate.RUN_LOOP_ENV, "1")::get)).isTrue();
        assertThat(AppUpdate.startedByLoop(Map.<String, String>of()::get)).isFalse();
        assertThat(AppUpdate.startedByLoop(Map.of(AppUpdate.RUN_LOOP_ENV, "0")::get)).isFalse();

        assertThat(AppUpdate.actionLabel(true)).isEqualTo("Restart to update");
        assertThat(AppUpdate.actionLabel(false)).isEqualTo("Exit to update");
        assertThat(AppUpdate.actionHint(false)).contains("does not come back", "just run-loop");
    }

    @Test
    void repositoryRootIsTheNearestAncestorWithAGitEntry(@TempDir Path dir) throws IOException {
        Path deep = Files.createDirectories(dir.resolve("app/build/image"));
        assertThat(AppUpdate.repositoryRoot(deep)).isNull();

        // A worktree's `.git` is a file, a clone's a directory — both count.
        Files.writeString(dir.resolve(".git"), "gitdir: /elsewhere\n");
        assertThat(AppUpdate.repositoryRoot(deep)).isEqualTo(dir);
        assertThat(AppUpdate.repositoryRoot(dir)).isEqualTo(dir);
    }
}
