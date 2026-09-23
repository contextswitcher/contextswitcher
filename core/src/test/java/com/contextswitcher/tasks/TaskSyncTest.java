package com.contextswitcher.tasks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.local.LocalCommandRunner.LocalResult;

import static org.assertj.core.api.Assertions.assertThat;

/// Two members sharing one group through a local bare repository, with the
/// real system git (MADR 0011).
// [utest->dsn~task-sync-groups~1]
class TaskSyncTest {

    /// System git with a fixed identity, so commits work on a machine (CI)
    /// without a global `user.name`.
    private static final Function<List<String>, LocalResult> GIT = command -> {
        List<String> argv = new ArrayList<>(List.of("git", "-c", "user.name=Test",
                "-c", "user.email=test@example.org", "-c", "init.defaultBranch=main"));
        argv.addAll(command.subList(1, command.size()));
        return new LocalCommandRunner(Duration.ofSeconds(30)).run(argv);
    };

    @TempDir
    Path temp;

    private TaskSync member(String name) throws Exception {
        Path tasks = temp.resolve(name).resolve("tasks");
        Files.createDirectories(tasks.resolve("misc"));
        Files.writeString(tasks.resolve("misc/unrelated.md"), "---\ntitle: Unrelated\n---\n");
        TaskSync sync = new TaskSync(tasks, temp.resolve(name).resolve("sync"), GIT);
        sync.saveGroups(List.of(new TaskSync.Group("team", temp.resolve("team.git").toString())));
        return sync;
    }

    private Path tasks(String name) {
        return temp.resolve(name).resolve("tasks");
    }

    @Test
    void sharesContentKeepsPrivateKeysAndTracksLeaveAndDelete() throws Exception {
        assertThat(GIT.apply(List.of("git", "init", "--bare", temp.resolve("team.git").toString())).ok()).isTrue();
        TaskSync alice = member("alice");
        TaskSync bob = member("bob");

        Path aliceFile = tasks("alice").resolve("work/fix.md");
        Files.createDirectories(aliceFile.getParent());
        Files.writeString(aliceFile, """
                ---
                title: Fix the bug
                status: active
                tmux: {session: main, window: "@3"}
                sync: [team]
                syncId: fix
                ---
                # Notes
                """);
        alice.syncAll();

        // Bob sees the shared part only, and sorts it into a category of his own.
        List<TaskSync.Incoming> incoming = bob.syncAll();
        assertThat(incoming).singleElement().satisfies(task -> {
            assertThat(task.title()).isEqualTo("Fix the bug");
            assertThat(task.content()).doesNotContain("tmux").doesNotContain("status");
        });
        Path bobFile = tasks("bob").resolve("mine/fix.md");
        Files.createDirectories(bobFile.getParent());
        Files.writeString(bobFile, TaskSync.sortInContent(incoming.getFirst()));
        assertThat(bob.incoming()).isEmpty();
        bob.syncAll(); // the app runs a round right after a sort-in

        // Bob's note reaches Alice; her tmux window stays hers.
        Files.writeString(bobFile, Files.readString(bobFile) + "Repro: open the dialog twice.\n");
        bob.syncAll();
        alice.syncAll();
        assertThat(Files.readString(aliceFile))
                .contains("Repro: open the dialog twice.")
                .contains("tmux:")
                .contains("status: active");

        // Bob leaves: the task is set aside for him, until Alice changes it.
        Files.writeString(bobFile, TaskSync.withGroup(Files.readString(bobFile), "team", false, "fix"));
        assertThat(bob.syncAll()).singleElement().satisfies(task -> {
            assertThat(task.ignored()).isTrue();
            assertThat(task.localFile()).isEqualTo("mine/fix.md");
        });
        Files.writeString(aliceFile, Files.readString(aliceFile).replace("Fix the bug", "Fix the dialog bug"));
        alice.syncAll();
        assertThat(bob.syncAll()).singleElement().satisfies(task -> assertThat(task.ignored()).isFalse());

        // Bob rejoins; Alice deletes the task: gone for everyone, Bob's file kept out of the group.
        Files.writeString(bobFile, TaskSync.withGroup(Files.readString(bobFile), "team", true, "fix"));
        bob.syncAll();
        Files.delete(aliceFile);
        alice.syncAll();
        assertThat(alice.incoming()).isEmpty();
        bob.syncAll();
        assertThat(Files.readString(bobFile)).doesNotContain("sync:").contains("title: Fix the dialog bug");
        assertThat(bob.incoming()).isEmpty();
    }

    @Test
    void sharedPartDropsPrivateKeys() {
        String shared = TaskSync.shared("""
                ---
                title: T
                remote: box
                tags: [a]
                folders: [/x]
                ---
                body
                """);
        assertThat(shared).isEqualTo("---\ntitle: T\ntags: [a]\n---\nbody\n");
    }
}
