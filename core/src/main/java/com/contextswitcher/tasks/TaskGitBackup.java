package com.contextswitcher.tasks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

import com.contextswitcher.local.LocalCommandRunner;

/// Best-effort git sync of the task directory: [#pullOnStartup] merges in what
/// another machine pushed while this one was closed, [#syncWhileRunning] does a
/// full round every few minutes (so the phone sees a recreated window or a
/// queued message soon, and this machine what the phone added), and
/// [#syncOnClose] commits everything this run changed and pushes it.
/// It only acts when the task directory is already a git repository (`.git`
/// present): the user runs the one-time `git init`, `git remote add`, and
/// initial push, so the app reuses the existing remote **and the user's
/// existing git credentials** (system `git`, MADR 0003's spirit — no embedded
/// git library, no separate auth to wire up).
///
/// A merge conflict is resolved automatically, in order: a file deleted on
/// either side stays deleted (**deletion wins**), a task file is merged
/// semantically per frontmatter key ([TaskMerge]), and anything left is handed
/// to a headless `claude -p` run in the task directory ([#claudeResolve]).
/// Only when all of that fails is the merge aborted and the local state left
/// for manual resolution.
///
/// Everything runs on a single daemon executor and never throws into the
/// caller: a missing remote, no network, or no `git` on PATH only logs. A
/// stale `.git/index.lock` (a git process killed mid-write) is cleared before
/// each sync so it cannot permanently wedge the backup ([#clearStaleLock]).
// [impl->dsn~task-git-backup~5]
public class TaskGitBackup {

    /// A `.git/index.lock` older than this is treated as **stale** and removed
    /// before a sync. The sync serializes its own git on one thread and
    /// `add`/`commit` are fast, so a lock this old is a leftover from a git
    /// process that was killed mid-write (app force-closed, timeout) — it
    /// otherwise blocks every future `add`/`commit` and silently kills backup.
    static final long STALE_LOCK_MS = 30_000;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path tasksDir;
    /// Runs one git argv and returns its result — injected so tests need no
    /// real git. In the app it is a `LocalCommandRunner` with a network-tolerant
    /// timeout (push).
    private final Function<List<String>, LocalCommandRunner.LocalResult> git;
    /// Runs one headless Claude prompt in the task directory — injected so
    /// tests need no real `claude`.
    private final Function<String, LocalCommandRunner.LocalResult> claude;
    /// Serializes startup pull and close sync so they never interleave.
    private final ExecutorService executor;

    public TaskGitBackup(Path tasksDir) {
        this(tasksDir, new LocalCommandRunner(Duration.ofSeconds(60))::run);
    }

    /// The backup of `tasksDir` running its git through `git` — how a sync
    /// group's clone gets the same conflict resolution (`dsn~task-sync-groups~1`).
    TaskGitBackup(Path tasksDir, Function<List<String>, LocalCommandRunner.LocalResult> git) {
        this(tasksDir, git,
                prompt -> new LocalCommandRunner(Duration.ofMinutes(5)).run(
                        List.of("claude", "-p", "--dangerously-skip-permissions", prompt), tasksDir),
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "task-git-backup");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    TaskGitBackup(Path tasksDir, Function<List<String>, LocalCommandRunner.LocalResult> git,
            Function<String, LocalCommandRunner.LocalResult> claude, ExecutorService executor) {
        this.tasksDir = tasksDir;
        this.git = git;
        this.claude = claude;
        this.executor = executor;
    }

    /// Pulls the remote into the task directory once at startup, off the UI
    /// thread, so a machine picks up tasks another machine pushed while it was
    /// closed. A no-op when the directory is not a git repo; an offline remote
    /// or an unresolvable conflict only logs (the local state is kept, the
    /// watcher shows whatever landed).
    // [impl->dsn~task-git-backup~5]
    public void pullOnStartup() {
        if (!Files.exists(tasksDir.resolve(".git"))) {
            return;
        }
        executor.execute(() -> {
            try {
                clearStaleLock();
                if (pullAndResolve()) {
                    Logger.info("Task directory synced from remote on startup.");
                }
            } catch (RuntimeException e) {
                Logger.warn("Task startup sync failed: {}", e.toString());
            }
        });
    }

    /// One round while the app runs, on the sync executor: commit what changed,
    /// pull (merge, with the automatic conflict resolution), push — the
    /// sync-group round ([#syncNow]) applied to the personal task directory.
    /// A no-op completing with false when the directory is not a git repo;
    /// failures only log.
    // [impl->dsn~task-git-backup~5]
    public CompletableFuture<Boolean> syncWhileRunning() {
        return syncWhileRunning("ContextSwitcher auto-backup");
    }

    /// As [#syncWhileRunning()], committing with `message` (plus a timestamp).
    public CompletableFuture<Boolean> syncWhileRunning(String message) {
        if (!Files.exists(tasksDir.resolve(".git"))) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> syncNow(message), executor);
    }

    /// Commits everything this run changed and pushes it, synchronously —
    /// called from the application's `stop()`, so closing the app is the
    /// commit point. A rejected push (another machine pushed while this one
    /// ran) is retried once after a pull with automatic conflict resolution.
    /// A no-op when the directory is not a git repo; blocks close at most
    /// [#CLOSE_TIMEOUT_MINUTES] minutes, failures only log.
    // [impl->dsn~task-git-backup~5]
    public void syncOnClose() {
        if (!Files.exists(tasksDir.resolve(".git"))) {
            return;
        }
        try {
            executor.submit(this::closeNow).get(CLOSE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            Logger.warn("Task close sync did not finish within {} minutes — closing anyway.",
                    CLOSE_TIMEOUT_MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException e) {
            Logger.warn("Task close sync failed: {}", e.getCause().toString());
        }
    }

    /// Upper bound on how long [#syncOnClose] may hold the closing app: the
    /// individual git/claude commands have their own timeouts, this is the
    /// last-resort cap over the whole sequence.
    static final long CLOSE_TIMEOUT_MINUTES = 10;

    /// The close-time commit+push, on the executor. Returns true when the
    /// remote ends up holding the local state. Package-private for tests.
    boolean closeNow() {
        try {
            if (!commit("ContextSwitcher auto-backup " + LocalDateTime.now().format(STAMP))) {
                return false;
            }
            // Always push, even with a clean tree: a previous close may have
            // left a commit unpushed (offline). Up to date = cheap no-op.
            if (git.apply(cmd("push")).ok()) {
                return true;
            }
            // Rejected (another machine pushed) or offline: integrate the
            // remote, resolve what conflicts, push again.
            if (!pullAndResolve()) {
                return false;
            }
            LocalCommandRunner.LocalResult push = git.apply(cmd("push"));
            if (!push.ok()) {
                Logger.info("Task close sync committed but push failed (offline / no upstream?): {}",
                        push.stderr().strip());
            }
            return push.ok();
        } catch (RuntimeException e) {
            Logger.warn("Task close sync failed: {}", e.toString());
            return false;
        }
    }

    /// One full round for a sync-group clone (`dsn~task-sync-groups~1`):
    /// commit the working tree, merge the remote in (with the same automatic
    /// conflict resolution), push. Unlike [#closeNow] the pull always runs —
    /// the point is to *receive* the other members' tasks, and pushing an
    /// unchanged tree succeeds without ever fetching. A failed pull (offline,
    /// a still-empty remote) does not stop the push; a conflict nothing could
    /// resolve leaves the merge aborted and the push rejected. Runs on the
    /// caller's thread; true when the push went through.
    // [impl->dsn~task-sync-groups~1]
    boolean syncNow(String message) {
        try {
            if (!commit(message + " " + LocalDateTime.now().format(STAMP))) {
                return false;
            }
            pullAndResolve();
            LocalCommandRunner.LocalResult push = git.apply(cmd("push"));
            if (!push.ok()) {
                Logger.info("Sync of {}: push failed: {}", tasksDir, push.stderr().strip());
            }
            return push.ok();
        } catch (RuntimeException e) {
            Logger.warn("Sync of {} failed: {}", tasksDir, e.toString());
            return false;
        }
    }

    /// Commits whatever the task directory currently holds, without pushing —
    /// the close sync's commit half, also called on its own before the janitor
    /// deletes a task file, so the archived last screen reaches git even
    /// though the file itself is gone by the next push
    /// (`dsn~merged-task-cleanup~2`). True when the tree ended up committed
    /// (a clean tree counts); a failure only logs.
    private boolean commit(String message) {
        clearStaleLock();
        if (git.apply(cmd("status", "--porcelain")).stdout().isBlank()) {
            return true;
        }
        git.apply(cmd("add", "-A"));
        LocalCommandRunner.LocalResult commit = git.apply(cmd("commit", "-m", message));
        if (!commit.ok()) {
            // Warn, not debug: a persistent commit failure (e.g. a lock we
            // could not clear, or a broken identity) silently stops all
            // backup, so it must be visible in the log.
            Logger.warn("Task commit failed: {}", commit.stderr().strip());
            return false;
        }
        return true;
    }

    /// Runs [#commit] on the sync executor and waits for it — for a caller
    /// that is about to delete a task file and needs the version before the
    /// delete to exist as a commit. A no-op when the task directory is not a
    /// git repository; never throws.
    // [impl->dsn~merged-task-cleanup~2]
    public void commitNow(String message) {
        if (!Files.exists(tasksDir.resolve(".git"))) {
            return;
        }
        try {
            executor.submit(() -> commit(message)).get(COMMIT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            Logger.warn("Task commit did not finish within {} minutes.", COMMIT_TIMEOUT_MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException e) {
            Logger.warn("Task commit failed: {}", e.getCause().toString());
        }
    }

    /// Upper bound on how long [#commitNow] waits for the sync executor —
    /// generous, since a startup pull may be running ahead of it.
    static final long COMMIT_TIMEOUT_MINUTES = 2;

    /// `git pull --no-rebase --no-edit` — a **merge** (never rebase), so the
    /// history records what each machine knew at reconciliation; `--no-edit`
    /// keeps it non-interactive and the explicit strategy dodges git's
    /// "divergent branches" prompt. A conflict goes through [#resolveConflicts];
    /// when that gives up too, the merge is aborted (never strand the repo
    /// mid-merge) and false is returned.
    private boolean pullAndResolve() {
        LocalCommandRunner.LocalResult pull = git.apply(cmd("pull", "--no-rebase", "--no-edit"));
        if (pull.ok() || resolveConflicts()) {
            return true;
        }
        git.apply(cmd("merge", "--abort")); // no-op when no merge started; result ignored
        Logger.info("Task sync: pull (merge) failed, leaving the local state for manual "
                + "resolution: {}", pull.stderr().strip());
        return false;
    }

    /// Finishes a conflicted merge by resolving every conflicted file
    /// ([#resolve]) and committing the merge; false — leaving the merge in
    /// progress for the caller to abort — as soon as one file cannot be
    /// resolved. Half-written files are harmless then: the abort restores them.
    // [impl->dsn~task-merge-resolution~2]
    private boolean resolveConflicts() {
        LocalCommandRunner.LocalResult conflicted = git.apply(cmd("diff", "-z", "--name-only", "--diff-filter=U"));
        if (!conflicted.ok() || conflicted.stdout().isEmpty()) {
            return false;
        }
        for (String file : conflicted.stdout().split("\0")) {
            if (!file.isEmpty() && !resolve(file)) {
                return false;
            }
        }
        LocalCommandRunner.LocalResult commit = git.apply(cmd("commit", "--no-edit"));
        if (commit.ok()) {
            Logger.info("Task sync: merge conflict resolved automatically.");
        }
        return commit.ok();
    }

    /// Resolves one conflicted file, in order: **deletion wins** — a file
    /// deleted on either side (a missing "ours"/"theirs" merge stage) is
    /// removed; a task file is merged semantically from its three merge stages
    /// ([TaskMerge], last-writer-wins tie-break read off git history); whatever
    /// remains is handed to Claude ([#claudeResolve]).
    // [impl->dsn~task-merge-resolution~2]
    private boolean resolve(String file) {
        String ours = stage(2, file);
        String theirs = stage(3, file);
        if (ours == null || theirs == null) {
            return git.apply(cmd("rm", "-f", "--", file)).ok();
        }
        if (file.endsWith(".md")) {
            // An unrelated pair of files added under the same name has no base.
            String base = stage(1, file);
            Optional<String> merged = TaskMerge.merge(base == null ? "" : base, ours, theirs,
                    lastCommitTime("MERGE_HEAD", file) > lastCommitTime("HEAD", file));
            if (merged.isPresent()) {
                try {
                    TextFiles.write(tasksDir.resolve(file), merged.get());
                    return git.apply(cmd("add", "--", file)).ok();
                } catch (java.io.IOException e) {
                    Logger.warn("Cannot write the merged task file {}: {}", file, e.getMessage());
                    return false;
                }
            }
        }
        return claudeResolve(file);
    }

    /// Hands one conflicted file to a headless `claude -p` run in the task
    /// directory: the working tree holds the file with git's conflict markers,
    /// Claude edits it in place. Accepted when Claude exits cleanly and the
    /// file afterwards either is gone (staged as a deletion) or carries no
    /// conflict markers (staged as resolved).
    // [impl->dsn~task-merge-resolution~2]
    private boolean claudeResolve(String file) {
        LocalCommandRunner.LocalResult result = claude.apply(("The file '%s' in the current "
                + "directory has a git merge conflict. Edit it in place so no conflict markers "
                + "remain, keeping both sides' intent; when both sides changed the same thing, "
                + "prefer the more recent change. If the conflict is best resolved by deleting "
                + "the file, delete it. Do not touch any other file, do not run git.")
                .formatted(file));
        if (!result.ok()) {
            Logger.warn("Claude could not resolve the conflict in {}: {}", file, result.stderr().strip());
            return false;
        }
        Path path = tasksDir.resolve(file);
        try {
            if (!Files.exists(path)) {
                return git.apply(cmd("rm", "-f", "--", file)).ok();
            }
            if (TextFiles.read(path).contains("<<<<<<<")) {
                Logger.warn("Claude left conflict markers in {} — giving up on the merge.", file);
                return false;
            }
        } catch (java.io.IOException e) {
            Logger.warn("Cannot read {} after Claude's resolution: {}", file, e.getMessage());
            return false;
        }
        return git.apply(cmd("add", "--", file)).ok();
    }

    /// The content of one merge stage (1 = base, 2 = ours, 3 = theirs), or null
    /// when the stage does not exist.
    private @Nullable String stage(int stage, String file) {
        LocalCommandRunner.LocalResult show = git.apply(cmd("show", ":%d:%s".formatted(stage, file)));
        return show.ok() ? show.stdout() : null;
    }

    /// Commit timestamp (epoch seconds) of the last commit on `ref` touching
    /// `file`; 0 when unknown, which makes an unreadable side lose the tie-break.
    private long lastCommitTime(String ref, String file) {
        try {
            return Long.parseLong(git.apply(cmd("log", "-1", "--format=%ct", ref, "--", file)).stdout().strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /// Removes a **stale** `.git/index.lock` (older than [#STALE_LOCK_MS]) so a
    /// leftover from a killed git process cannot block every future commit. A
    /// fresh lock (a git command genuinely in flight) is left alone.
    private void clearStaleLock() {
        Path lock = tasksDir.resolve(".git").resolve("index.lock");
        try {
            if (!Files.exists(lock)) {
                return;
            }
            long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(lock).toMillis();
            if (ageMs >= STALE_LOCK_MS) {
                Files.deleteIfExists(lock);
                Logger.warn("Removed a stale .git/index.lock ({} ms old) blocking task sync.", ageMs);
            }
        } catch (java.io.IOException e) {
            Logger.debug("Could not check/clear index.lock: {}", e.getMessage());
        }
    }

    private List<String> cmd(String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-C", tasksDir.toString()));
        command.addAll(List.of(args));
        return command;
    }
}
