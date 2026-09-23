package com.contextswitcher.tasks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-repository-watching~6]
class TaskRepositoryTest {

    private static TaskRepository repositoryFor(Path dir) {
        return new TaskRepository(dir, new TaskFileParser(), Runnable::run,
                new ArrayList<>(), new HashSet<>());
    }

    @Test
    void scanLoadsValidTasksAndReportsBrokenOnes(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("alpha.md"), """
                ---
                title: Alpha
                ---
                notes
                """);
        Files.writeString(dir.resolve("beta.md"), """
                ---
                title: Beta
                status: done
                ---
                """);
        Files.writeString(dir.resolve("broken.md"), "no frontmatter here\n");
        Files.writeString(dir.resolve("ignored.txt"), "not a task\n");

        try (TaskRepository repository = repositoryFor(dir)) {
            repository.scan();

            assertThat(repository.entries()).hasSize(3);
            assertThat(repository.entries().stream().filter(e -> e instanceof TaskEntry.Loaded).toList())
                    .extracting(TaskEntry::id)
                    .containsExactlyInAnyOrder("alpha", "beta");
            assertThat(repository.entries().stream().filter(e -> e instanceof TaskEntry.Failed).toList())
                    .singleElement()
                    .satisfies(entry -> {
                        TaskEntry.Failed failed = (TaskEntry.Failed) entry;
                        assertThat(failed.fileName()).isEqualTo("broken.md");
                        assertThat(failed.message()).contains("frontmatter");
                    });
        }
    }

    /// A rename must keep the task in the list — same slot, new id — instead
    /// of the watcher's delete+create blinking the row away and back.
    // [utest->dsn~claude-title-sync~3]
    @Test
    void renameReplacesTheEntryInPlace(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("alpha.md"), """
                ---
                title: Alpha
                ---
                """);
        Files.writeString(dir.resolve("beta.md"), """
                ---
                title: Beta
                ---
                """);

        try (TaskRepository repository = repositoryFor(dir)) {
            repository.scan();
            int slot = repository.entries().stream().map(TaskEntry::id).toList().indexOf("alpha");
            Files.move(dir.resolve("alpha.md"), dir.resolve("2026-09-12-alpha-renamed.md"));

            repository.renamed("alpha", "2026-09-12-alpha-renamed");

            assertThat(repository.entries()).hasSize(2);
            assertThat(repository.entries().get(slot).id()).isEqualTo("2026-09-12-alpha-renamed");

            // The watch events for the same rename arrive afterwards and must
            // not undo it.
            repository.renamed("alpha", "2026-09-12-alpha-renamed");
            assertThat(repository.entries()).hasSize(2);
        }
    }

    @Test
    void scanCreatesMissingTasksDirectory(@TempDir Path dir) throws Exception {
        Path tasksDir = dir.resolve("tasks");

        try (TaskRepository repository = repositoryFor(tasksDir)) {
            repository.scan();

            assertThat(tasksDir).isDirectory();
            assertThat(repository.entries()).isEmpty();
        }
    }

    // [utest->dsn~task-template-file~1]
    @Test
    void scanSeedsTemplateIntoEmptyDirButNeverListsIt(@TempDir Path dir) throws Exception {
        try (TaskRepository repository = repositoryFor(dir)) {
            repository.scan();

            assertThat(dir.resolve("TEMPLATE.md")).exists();
            assertThat(repository.entries()).isEmpty();
        }
    }

    // [utest->dsn~task-template-file~1]
    @Test
    void scanDoesNotSeedTemplateNextToExistingTasks(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("alpha.md"), """
                ---
                title: Alpha
                ---
                """);

        try (TaskRepository repository = repositoryFor(dir)) {
            repository.scan();

            assertThat(dir.resolve("TEMPLATE.md")).doesNotExist();
            assertThat(repository.entries()).hasSize(1);
        }
    }

    @Test
    void seededTemplateItselfParses(@TempDir Path dir) throws Exception {
        try (TaskRepository repository = repositoryFor(dir)) {
            repository.scan();

            Task task = new TaskFileParser().parse(dir.resolve("TEMPLATE.md"));
            assertThat(task.tmux()).isNotNull();
            assertThat(task.intellij()).isNotNull();
            assertThat(task.browser()).isNotNull();
        }
    }

    @Test
    void scanLoadsTasksFromOneLevelSubdirectoriesAsGroups(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("root-task.md"), """
                ---
                title: Root task
                ---
                """);
        Files.createDirectory(dir.resolve("jabref"));
        Files.writeString(dir.resolve("jabref/fix-npe.md"), """
                ---
                title: Fix NPE
                ---
                """);
        Files.createDirectory(dir.resolve("contextswitcher"));
        Files.writeString(dir.resolve("contextswitcher/folders.md"), """
                ---
                title: Folders
                ---
                """);

        try (TaskRepository repository = repositoryFor(dir)) {
            repository.scan();

            assertThat(repository.entries()).hasSize(3);
            assertThat(repository.entries()).extracting(TaskEntry::id).containsExactlyInAnyOrder(
                    "root-task", "jabref/fix-npe", "contextswitcher/folders");
            assertThat(repository.entries()).extracting(TaskEntry::group).containsExactlyInAnyOrder(
                    "", "jabref", "contextswitcher");
        }
    }

    // [utest->dsn~group-config-create~9]
    @Test
    void groupConfigFileIsNeverListedAsTask(@TempDir Path dir) throws Exception {
        Files.createDirectory(dir.resolve("jabref"));
        Files.writeString(dir.resolve("jabref/CONTEXTSWITCHER.md"), """
                ---
                # host: devbox
                ---
                """);
        Files.writeString(dir.resolve("jabref/fix-npe.md"), """
                ---
                title: Fix NPE
                ---
                """);

        try (TaskRepository repository = repositoryFor(dir)) {
            repository.scan();

            assertThat(repository.entries()).extracting(TaskEntry::id).containsExactly("jabref/fix-npe");
        }
    }

    // [utest->dsn~task-create-ui~15]
    /// A OneNote clipboard pasted into a category config is repaired on disk
    /// where the file is read, so the category keeps its keys.
    // [utest->dsn~onenote-paste-repair~1]
    @Test
    void scanRepairsAPastedOnenoteLinkInTheCategoryConfig(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("kaplan").resolve(TaskRepository.GROUP_CONFIG_FILE_NAME);
        Files.createDirectories(config.getParent());
        Files.writeString(config, """
                ---
                https://onedrive.live.com/view.aspx?resid=X&end
                onenote:https://d.docs.live.net/x/Doc.one#section-id={A}&end
                desktop: FOKUS2
                ---
                """);

        try (TaskRepository repository = repositoryFor(dir)) {
            repository.scan();
        }

        assertThat(new TaskFileParser().parseGroupConfig(Files.readString(config)).desktop())
                .isEqualTo("FOKUS2");
    }

    @Test
    void scanPublishesSubfoldersIncludingEmptyOnes(@TempDir Path dir) throws Exception {
        Files.createDirectory(dir.resolve("jabref"));
        Files.writeString(dir.resolve("jabref/fix-npe.md"), """
                ---
                title: Fix NPE
                ---
                """);
        Files.createDirectory(dir.resolve("empty-group"));

        try (TaskRepository repository = repositoryFor(dir)) {
            repository.scan();

            assertThat(repository.folders()).containsExactlyInAnyOrder("jabref", "empty-group");
        }
    }

    // A dot-prefixed directory is app-internal (`.git`, `.queues`) — never a
    // category, never scanned for tasks. [utest->dsn~message-queue-store~2]
    @Test
    void scanIgnoresDotPrefixedDirectories(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("root-task.md"), """
                ---
                title: Root task
                ---
                """);
        // A queue file under `.queues/` must not surface as a category or a task.
        Files.createDirectories(dir.resolve(".queues"));
        Files.writeString(dir.resolve(".queues/root-task.yaml"), "- a queued message\n");
        Files.createDirectory(dir.resolve(".git"));

        try (TaskRepository repository = repositoryFor(dir)) {
            repository.scan();

            assertThat(repository.folders()).isEmpty();
            assertThat(repository.entries()).extracting(TaskEntry::id).containsExactly("root-task");
        }
    }

    // [utest->dsn~task-repository-watching~6]
    @Test
    void groupConfigChangeNotifiesListener(@TempDir Path dir) throws Exception {
        Files.createDirectory(dir.resolve("jabref"));
        Files.writeString(dir.resolve("jabref/fix-npe.md"), """
                ---
                title: Fix NPE
                ---
                """);

        try (TaskRepository repository = repositoryFor(dir)) {
            repository.scan();
            var notified = new java.util.concurrent.CountDownLatch(1);
            repository.setOnGroupConfigChange(notified::countDown);
            repository.startWatching();

            Files.writeString(dir.resolve("jabref/CONTEXTSWITCHER.md"), """
                    ---
                    remote: devbox
                    ---
                    """);

            assertThat(notified.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    .as("group-config change fires the listener")
                    .isTrue();
            assertThat(repository.entries()).extracting(TaskEntry::id).containsExactly("jabref/fix-npe");
        }
    }

    /// A rename is a DELETE plus a CREATE, and a save several MODIFYs: the
    /// watcher must hand them to the UI as one mutation, or the task list
    /// rebuilds once per event and the renamed row blinks away and back.
    // [utest->dsn~task-repository-watching~6]
    @Test
    void oneRenameReachesTheUiAsOneMutation(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("alpha.md"), """
                ---
                title: Alpha
                ---
                """);
        java.util.concurrent.atomic.AtomicInteger mutations = new java.util.concurrent.atomic.AtomicInteger();
        List<TaskEntry> entries = java.util.Collections.synchronizedList(new ArrayList<>());
        try (TaskRepository repository = new TaskRepository(dir, new TaskFileParser(),
                task -> {
                    mutations.incrementAndGet();
                    task.run();
                }, entries, new HashSet<>())) {
            repository.scan();
            repository.startWatching();
            mutations.set(0);

            Files.writeString(dir.resolve("alpha.md"), """
                    ---
                    title: Alpha renamed
                    ---
                    """);
            Files.move(dir.resolve("alpha.md"), dir.resolve("gamma.md"));

            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline
                    && entries.stream().noneMatch(entry -> entry.id().equals("gamma"))) {
                Thread.sleep(20);
            }
            assertThat(entries).extracting(TaskEntry::id).containsExactly("gamma");
            assertThat(mutations.get())
                    .as("write + rename reach the UI as a single batch")
                    .isEqualTo(1);
        }
    }

    @Test
    void scanDoesNotDescendIntoSubSubdirectories(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("jabref/nested"));
        Files.writeString(dir.resolve("jabref/nested/too-deep.md"), """
                ---
                title: Too deep
                ---
                """);

        try (TaskRepository repository = repositoryFor(dir)) {
            repository.scan();

            assertThat(repository.entries()).isEmpty();
        }
    }

    @Test
    void reloadOfUnchangedFilePublishesNoListChange(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("alpha.md");
        Files.writeString(file, """
                ---
                title: Alpha
                ---
                """);

        List<String> mutations = new ArrayList<>();
        List<TaskEntry> entries = new ArrayList<>() {
            @Override
            public boolean add(TaskEntry entry) {
                mutations.add("add");
                return super.add(entry);
            }

            @Override
            public TaskEntry set(int index, TaskEntry entry) {
                mutations.add("set");
                return super.set(index, entry);
            }
        };
        Set<String> folders = new HashSet<>();
        try (TaskRepository repository = new TaskRepository(
                dir, new TaskFileParser(), Runnable::run, entries, folders)) {
            repository.scan();
            mutations.clear();

            // A watcher MODIFY with unchanged content (mtime touch, duplicate
            // event) re-runs the load — the list must not republish, since
            // every set() costs a full task-list rebuild in the UI.
            repository.scan();
            assertThat(mutations).isEmpty();

            Files.writeString(file, """
                    ---
                    title: Alpha renamed
                    ---
                    """);
            repository.scan();
            assertThat(mutations).hasSize(1);
        }
    }
}
