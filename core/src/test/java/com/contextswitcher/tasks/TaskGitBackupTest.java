package com.contextswitcher.tasks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.contextswitcher.local.LocalCommandRunner.LocalResult;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-git-backup~5]
class TaskGitBackupTest {

    private final List<List<String>> calls = new ArrayList<>();
    private final List<String> claudePrompts = new ArrayList<>();

    /// The git subcommand of each recorded call (e.g. status, add, commit, push).
    private List<String> subcommands() {
        return calls.stream().map(call -> call.get(3)).toList();
    }

    /// A recording backup over `dir`: each git subcommand answers from
    /// `responses` (a function from subcommand+args to result), defaulting to
    /// success with empty output; `claudeExit` answers the Claude runs.
    private TaskGitBackup backup(Path dir, Function<List<String>, LocalResult> responses,
            int claudeExit) {
        Function<List<String>, LocalResult> git = command -> {
            calls.add(command);
            LocalResult result = responses.apply(command);
            return result != null ? result : new LocalResult(0, "", "");
        };
        return new TaskGitBackup(dir, git, prompt -> {
            claudePrompts.add(prompt);
            return new LocalResult(claudeExit, "", "");
        }, Executors.newSingleThreadExecutor());
    }

    /// Response function answering per subcommand name only.
    private static Function<List<String>, LocalResult> bySubcommand(
            Function<String, LocalResult> responses) {
        return command -> responses.apply(command.get(3));
    }

    @Test
    void cleanTreeStillPushes(@TempDir Path dir) {
        boolean synced = backup(dir, bySubcommand(sub -> null), 0).closeNow();

        // A previous close may have left a commit unpushed (offline) — a
        // clean tree still pushes, but commits nothing.
        assertThat(synced).isTrue();
        assertThat(subcommands()).containsExactly("status", "push");
    }

    @Test
    void dirtyTreeStagesCommitsAndPushes(@TempDir Path dir) {
        boolean synced = backup(dir, bySubcommand(sub ->
                sub.equals("status") ? new LocalResult(0, " M debug.md\n", "") : null), 0).closeNow();

        assertThat(synced).isTrue();
        assertThat(subcommands()).containsExactly("status", "add", "commit", "push");
        assertThat(calls.get(0)).containsExactly("git", "-C", dir.toString(), "status", "--porcelain");
        assertThat(calls.get(2)).startsWith("git", "-C", dir.toString(), "commit", "-m");
    }

    @Test
    void roundWhileRunningCommitsPullsAndPushes(@TempDir Path dir) throws Exception {
        java.nio.file.Files.createDirectory(dir.resolve(".git"));

        boolean synced = backup(dir, bySubcommand(sub ->
                sub.equals("status") ? new LocalResult(0, " M debug.md\n", "") : null), 0)
                .syncWhileRunning().get(10, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(synced).isTrue();
        assertThat(subcommands()).containsExactly("status", "add", "commit", "pull", "push");
    }

    @Test
    void roundWhileRunningIsANoOpWithoutRepository(@TempDir Path dir) throws Exception {
        assertThat(backup(dir, bySubcommand(sub -> null), 0).syncWhileRunning().get()).isFalse();
        assertThat(subcommands()).isEmpty();
    }

    @Test
    void failedCommitDoesNotPush(@TempDir Path dir) {
        boolean synced = backup(dir, bySubcommand(sub -> switch (sub) {
            case "status" -> new LocalResult(0, " M debug.md\n", "");
            case "commit" -> new LocalResult(1, "", "boom");
            default -> null;
        }), 0).closeNow();

        assertThat(synced).isFalse();
        assertThat(subcommands()).containsExactly("status", "add", "commit");
    }

    @Test
    void rejectedPushPullsAndPushesAgain(@TempDir Path dir) {
        List<String> pushResults = new ArrayList<>(List.of("reject", "ok"));
        boolean synced = backup(dir, bySubcommand(sub -> switch (sub) {
            case "status" -> new LocalResult(0, " M debug.md\n", "");
            case "push" -> new LocalResult(pushResults.remove(0).equals("ok") ? 0 : 1, "", "rejected");
            default -> null;
        }), 0).closeNow();

        assertThat(synced).isTrue();
        assertThat(subcommands()).containsExactly("status", "add", "commit", "push", "pull", "push");
        assertThat(calls.get(4)).containsExactly("git", "-C", dir.toString(), "pull", "--no-rebase", "--no-edit");
    }

    @Test
    void unresolvableConflictAbortsAndSkipsSecondPush(@TempDir Path dir) {
        boolean synced = backup(dir, bySubcommand(sub -> switch (sub) {
            case "status" -> new LocalResult(0, " M debug.md\n", "");
            case "push", "pull" -> new LocalResult(1, "", "conflict");
            // No conflicted files listed — nothing the resolution can take over.
            default -> null;
        }), 0).closeNow();

        assertThat(synced).isFalse();
        assertThat(subcommands()).containsExactly("status", "add", "commit", "push", "pull", "diff", "merge");
        assertThat(calls.get(6)).containsExactly("git", "-C", dir.toString(), "merge", "--abort");
    }

    /// A conflict on a task file that [TaskMerge] can resolve is committed and
    /// pushed instead of aborted.
    // [utest->dsn~task-merge-resolution~2]
    @Test
    void resolvableTaskFileConflictIsMergedAndPushed(@TempDir Path dir) throws Exception {
        String base = "---\ntitle: T\nstatus: active\n---\n";
        List<String> pushResults = new ArrayList<>(List.of("reject", "ok"));
        boolean synced = backup(dir, command -> switch (command.get(3)) {
            case "status" -> new LocalResult(0, " M debug.md\n", "");
            case "push" -> new LocalResult(pushResults.remove(0).equals("ok") ? 0 : 1, "", "rejected");
            case "pull" -> new LocalResult(1, "", "conflict");
            case "diff" -> new LocalResult(0, "debug.md\0", "");
            case "show" -> new LocalResult(0, switch (command.get(4)) {
                case ":1:debug.md" -> base;
                case ":2:debug.md" -> base.replace("status: active", "status: suspended");
                default -> base;
            }, "");
            case "log" -> new LocalResult(0, "1", "");
            default -> new LocalResult(0, "", "");
        }, 0).closeNow();

        assertThat(synced).isTrue();
        assertThat(subcommands()).containsExactly("status", "add", "commit", "push", "pull", "diff",
                "show", "show", "show", "log", "log", "add", "commit", "push");
        assertThat(Files.readString(dir.resolve("debug.md"))).contains("status: suspended");
    }

    /// A file deleted on one side of the merge (a missing merge stage) is
    /// resolved as deleted — deletion wins.
    // [utest->dsn~task-merge-resolution~2]
    @Test
    void deletionWinsOverModification(@TempDir Path dir) {
        boolean synced = backup(dir, command -> switch (command.get(3)) {
            case "status" -> new LocalResult(0, "", "");
            case "push" -> new LocalResult(subcommands().contains("rm") ? 0 : 1, "", "rejected");
            case "pull" -> new LocalResult(1, "", "conflict");
            case "diff" -> new LocalResult(0, "debug.md\0", "");
            // Stage 2 (ours) missing: we deleted, the other machine modified.
            case "show" -> command.get(4).startsWith(":2:")
                    ? new LocalResult(1, "", "does not exist")
                    : new LocalResult(0, "content", "");
            default -> new LocalResult(0, "", "");
        }, 0).closeNow();

        assertThat(synced).isTrue();
        assertThat(subcommands()).containsExactly("status", "push", "pull", "diff",
                "show", "show", "rm", "commit", "push");
        assertThat(calls.get(6)).containsExactly("git", "-C", dir.toString(), "rm", "-f", "--", "debug.md");
        assertThat(claudePrompts).isEmpty();
    }

    /// A conflict neither deletion-wins nor [TaskMerge] can decide goes to a
    /// headless Claude run; its in-place edit (no markers left) is staged and
    /// the merge completes.
    // [utest->dsn~task-merge-resolution~2]
    @Test
    void remainingConflictIsResolvedByClaude(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("notes.txt"), "resolved by claude");
        boolean synced = backup(dir, command -> switch (command.get(3)) {
            case "status" -> new LocalResult(0, "", "");
            case "push" -> new LocalResult(subcommands().contains("pull") ? 0 : 1, "", "rejected");
            case "pull" -> new LocalResult(1, "", "conflict");
            // Not a .md file — the semantic task merge does not apply.
            case "diff" -> new LocalResult(0, "notes.txt\0", "");
            case "show" -> new LocalResult(0, "content", "");
            default -> new LocalResult(0, "", "");
        }, 0).closeNow();

        assertThat(synced).isTrue();
        assertThat(claudePrompts).hasSize(1);
        assertThat(claudePrompts.get(0)).contains("notes.txt");
        assertThat(subcommands()).containsExactly("status", "push", "pull", "diff",
                "show", "show", "add", "commit", "push");
    }

    /// Claude leaving conflict markers behind means the merge is not resolved —
    /// abort, keep the local commit unpushed.
    // [utest->dsn~task-merge-resolution~2]
    @Test
    void claudeLeavingMarkersAbortsTheMerge(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("notes.txt"), "<<<<<<< HEAD\nours\n=======\ntheirs\n>>>>>>>\n");
        boolean synced = backup(dir, command -> switch (command.get(3)) {
            case "status" -> new LocalResult(0, "", "");
            case "push", "pull" -> new LocalResult(1, "", "conflict");
            case "diff" -> new LocalResult(0, "notes.txt\0", "");
            case "show" -> new LocalResult(0, "content", "");
            default -> new LocalResult(0, "", "");
        }, 0).closeNow();

        assertThat(synced).isFalse();
        assertThat(subcommands()).containsExactly("status", "push", "pull", "diff",
                "show", "show", "merge");
    }

    @Test
    void staleIndexLockIsClearedBeforeSync(@TempDir Path dir) throws Exception {
        Path lock = dir.resolve(".git").resolve("index.lock");
        Files.createDirectories(lock.getParent());
        Files.writeString(lock, "");
        Files.setLastModifiedTime(lock,
                FileTime.fromMillis(System.currentTimeMillis() - TaskGitBackup.STALE_LOCK_MS - 1000));

        backup(dir, bySubcommand(sub -> null), 0).closeNow();

        assertThat(Files.exists(lock)).isFalse();
    }

    @Test
    void freshIndexLockIsLeftAlone(@TempDir Path dir) throws Exception {
        Path lock = dir.resolve(".git").resolve("index.lock");
        Files.createDirectories(lock.getParent());
        Files.writeString(lock, "");

        backup(dir, bySubcommand(sub -> null), 0).closeNow();

        assertThat(Files.exists(lock)).isTrue();
    }

    @Test
    void pullOnStartupMergesTheRemoteWhenRepoPresent(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve(".git"));
        List<List<String>> pullCalls = new ArrayList<>();
        Function<List<String>, LocalResult> git = command -> {
            synchronized (pullCalls) {
                pullCalls.add(command);
            }
            return new LocalResult(0, "", "");
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();

        new TaskGitBackup(dir, git, prompt -> new LocalResult(0, "", ""), executor).pullOnStartup();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(pullCalls).hasSize(1);
        assertThat(pullCalls.get(0))
                .containsExactly("git", "-C", dir.toString(), "pull", "--no-rebase", "--no-edit");
    }

    @Test
    void pullOnStartupIsNoOpWithoutGitDir(@TempDir Path dir) throws Exception {
        List<List<String>> pullCalls = new ArrayList<>();
        Function<List<String>, LocalResult> git = command -> {
            synchronized (pullCalls) {
                pullCalls.add(command);
            }
            return new LocalResult(0, "", "");
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();

        new TaskGitBackup(dir, git, prompt -> new LocalResult(0, "", ""), executor).pullOnStartup();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(pullCalls).isEmpty();
    }
}
