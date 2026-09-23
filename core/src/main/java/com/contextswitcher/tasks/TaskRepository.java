package com.contextswitcher.tasks;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Loads all `*.md` task files from a directory and its immediate
/// subdirectories (one level deep; a subfolder is a task group) and keeps the
/// entry list in sync with external file changes via a `WatchService`.
/// List mutations run on the given executor (the JavaFX application thread in
/// production, a direct executor in tests).
// [impl->dsn~task-repository-watching~6]
public class TaskRepository implements AutoCloseable {

    public static final String TEMPLATE_FILE_NAME = "TEMPLATE.md";

    /// Per-group defaults file (https://github.com/contextswitcher/contextswitcher-private/issues/45); configuration, never a task.
    // [impl->dsn~group-config-create~9]
    public static final String GROUP_CONFIG_FILE_NAME = "CONTEXTSWITCHER.md";

    private final Path tasksDir;
    private final TaskFileParser parser;
    private final Executor uiExecutor;
    private final List<TaskEntry> entries;
    private final Set<String> folders;
    private volatile @Nullable WatchService watchService;
    private final Map<WatchKey, Path> watchedDirs = new ConcurrentHashMap<>();

    /// Notified on the configured executor when a group's `CONTEXTSWITCHER.md`
    /// changes: the file is not a task, so the entry list stays untouched, but
    /// the UI renders its defaults (group remote glyph, tags) and must re-read.
    private volatile Runnable onGroupConfigChange = () -> { };

    /// `entries`/`folders` are mutated only on `uiExecutor`; the caller passes
    /// the live collections it wants those mutations to land on (desktop hands
    /// in `FXCollections` observable ones so the UI reacts without polling).
    public TaskRepository(Path tasksDir, TaskFileParser parser, Executor uiExecutor,
            List<TaskEntry> entries, Set<String> folders) {
        this.tasksDir = tasksDir;
        this.parser = parser;
        this.uiExecutor = uiExecutor;
        this.entries = entries;
        this.folders = folders;
    }

    /// The live entry list; mutated only on the configured executor.
    public List<TaskEntry> entries() {
        return entries;
    }

    /// Registers the group-config change listener (replacing any previous one).
    public void setOnGroupConfigChange(Runnable listener) {
        onGroupConfigChange = listener;
    }

    /// The live set of task subfolder names (project groups) — including
    /// folders without any task file, so a freshly created group is visible
    /// before its first task exists. Mutated only on the configured executor.
    // [impl->dsn~task-create-ui~15]
    public Set<String> folders() {
        return folders;
    }

    /// Scans the task directory (creating it if absent) and its immediate
    /// subdirectories, loading all task files. A directory without any task
    /// file gets a commented `TEMPLATE.md` to copy from.
    public void scan() throws IOException {
        Files.createDirectories(tasksDir);
        seedTemplateIfEmpty();
        scanDirectory(tasksDir, uiExecutor::execute);
        try (Stream<Path> subdirs = Files.list(tasksDir)) {
            for (Path dir : subdirs.filter(TaskRepository::isProjectGroup).sorted().toList()) {
                String name = dir.getFileName().toString();
                uiExecutor.execute(() -> folders.add(name));
                scanDirectory(dir, uiExecutor::execute);
            }
        }
    }

    /// A dot-prefixed directory is app-internal, not a project group: it must
    /// not be scanned, watched, or listed as a folder. This covers `.git` (the
    /// optional task-backup repo, whose writes would otherwise churn the
    /// watcher) and `.queues` (the per-task message queues + qodo sidecar,
    /// stored here so they ride the task-dir git backup — `dsn~message-queue-store~2`).
    private static boolean isProjectGroup(Path dir) {
        return Files.isDirectory(dir) && !dir.getFileName().toString().startsWith(".");
    }

    /// Reads and parses every task file on the **calling** thread, then hands
    /// the whole directory to the UI in one mutation: publishing task by task
    /// made the list rebuild once per file, which stalled the FX thread for
    /// seconds on a directory with many tasks. `sink` takes that mutation —
    /// straight to the executor for the startup scan, into the current batch
    /// when the watcher is the caller.
    private void scanDirectory(Path dir, Consumer<Runnable> sink) throws IOException {
        checkGroupConfig(dir.resolve(GROUP_CONFIG_FILE_NAME));
        List<TaskEntry> batch;
        try (Stream<Path> files = Files.list(dir)) {
            batch = files.filter(TaskRepository::isTaskFile)
                    .sorted()
                    .map(this::load)
                    .toList();
        }
        if (!batch.isEmpty()) {
            sink.accept(() -> publishAll(batch));
        }
    }

    /// Repairs a OneNote clipboard pasted into a category config, then logs
    /// what is still wrong with it: why it does not load — the category then
    /// has no defaults at all — and its duplicate keys. The repaired write
    /// makes the watcher deliver the file once more, which finds nothing left
    /// to repair.
    // [impl->dsn~frontmatter-duplicate-keys~1]
    // [impl->dsn~group-config-parse-error~1]
    // [impl->dsn~onenote-paste-repair~1]
    private void checkGroupConfig(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String content = TextFiles.read(file);
            String repaired = parser.repairPastedOneNoteLink(content);
            if (!repaired.equals(content)) {
                TextFiles.write(file, repaired);
                Logger.info("Repaired a pasted OneNote link into note: in {}", file);
                return;
            }
            String error = parser.groupConfigError(content);
            if (error != null) {
                Logger.warn("Cannot parse category config {}: {}", file, error);
            }
        } catch (IOException | RuntimeException e) {
            Logger.debug("Cannot check {}: {}", file, e.getMessage());
        }
        logDuplicateKeys(file);
    }

    /// Logs the file's duplicate frontmatter keys, with the file and the line
    /// of each repeat. SnakeYAML notices them too, but warns through
    /// `java.util.logging` — bridged nowhere here, so it reaches no log file —
    /// and from `loadYaml`, which sees only YAML text and can name no file. A
    /// missing or unreadable file is silence: it is not this method's report.
    /// Called where a file is read from disk, not on every render, so a broken
    /// config does not repeat itself into the log a few times a second.
    // [impl->dsn~frontmatter-duplicate-keys~1]
    private void logDuplicateKeys(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            var duplicates = parser.duplicateFrontmatterKeys(TextFiles.read(file));
            if (!duplicates.isEmpty()) {
                Logger.warn("Duplicate frontmatter keys in {}: {} — YAML keeps the last of each",
                        file, String.join(", ", duplicates));
            }
        } catch (IOException | RuntimeException e) {
            Logger.debug("Cannot check {} for duplicate keys: {}", file, e.getMessage());
        }
    }

    /// The bundled `TEMPLATE.md` as a string — the commented reference of every
    /// task-file field with copy-and-pastable placeholders, shown by the
    /// editor's F1 help. Same resource seeded by [#seedTemplateIfEmpty()].
    // [impl->dsn~task-field-help~1]
    public static String templateReference() {
        try (var template = TaskRepository.class.getResourceAsStream("TEMPLATE.md")) {
            if (template == null) {
                throw new IOException("TEMPLATE.md resource missing");
            }
            return new String(template.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // [impl->dsn~task-template-file~1]
    private void seedTemplateIfEmpty() throws IOException {
        try (Stream<Path> files = Files.list(tasksDir)) {
            if (files.anyMatch(file -> file.getFileName().toString().endsWith(".md"))) {
                return;
            }
        }
        try (var template = TaskRepository.class.getResourceAsStream("TEMPLATE.md")) {
            if (template == null) {
                throw new IOException("TEMPLATE.md resource missing");
            }
            Files.copy(template, tasksDir.resolve(TEMPLATE_FILE_NAME));
            Logger.info("Seeded {} into {}", TEMPLATE_FILE_NAME, tasksDir);
        }
    }

    /// Starts watching the task directory and its immediate subdirectories on
    /// a daemon thread. Call after [#scan()].
    public void startWatching() throws IOException {
        WatchService service = FileSystems.getDefault().newWatchService();
        watchService = service;
        registerDirectory(service, tasksDir);
        try (Stream<Path> subdirs = Files.list(tasksDir)) {
            for (Path dir : subdirs.filter(TaskRepository::isProjectGroup).toList()) {
                registerDirectory(service, dir);
            }
        }
        Thread watcher = new Thread(() -> watchLoop(service), "task-dir-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void registerDirectory(WatchService service, Path dir) throws IOException {
        WatchKey key = dir.register(service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        watchedDirs.put(key, dir);
    }

    /// How long one wake-up keeps collecting further watch events before
    /// handing the batch to the UI. A single logical change is several
    /// events landing milliseconds apart, so this is what actually merges
    /// them; short enough that no edit feels delayed.
    private static final long SETTLE_MILLIS = 50;

    /// One wake-up collects the events of every signalled key — plus any
    /// arriving during the settle window — and applies them to the entry list
    /// as a **single** mutation. One logical change fires several events (a
    /// save is several MODIFYs, a rename a DELETE plus a CREATE, a group
    /// delete a burst), and one list mutation per event means one full task
    /// list rebuild per event: rows blinked (a renamed task vanished and came
    /// back) and a busy write stalled the FX thread.
    private void watchLoop(WatchService service) {
        try {
            while (true) {
                WatchKey key = service.take();
                List<Runnable> batch = new ArrayList<>();
                boolean watching = true;
                do {
                    watching &= drain(service, key, batch);
                    key = service.poll(SETTLE_MILLIS, TimeUnit.MILLISECONDS);
                } while (key != null);
                if (!batch.isEmpty()) {
                    uiExecutor.execute(() -> batch.forEach(Runnable::run));
                }
                if (!watching) {
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException e) {
            // repository closed - normal shutdown
        }
    }

    /// Collects one key's pending events into `batch`. Returns false when the
    /// task directory itself became unwatchable — the loop ends after
    /// flushing what it has.
    private boolean drain(WatchService service, WatchKey key, List<Runnable> batch) {
        Path dir = watchedDirs.get(key);
        if (dir != null) {
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.context() instanceof Path relative) {
                    handleEvent(service, dir, event.kind(), dir.resolve(relative), batch);
                }
            }
        }
        if (!key.reset()) {
            watchedDirs.remove(key);
            if (tasksDir.equals(dir)) {
                Logger.warn("Task directory {} became unwatchable", tasksDir);
                return false;
            }
        }
        return true;
    }

    /// A subfolder created directly under the task directory becomes a new
    /// task group: it is watched and scanned in place (one level deep, no
    /// further recursion into it). Every entry-list mutation goes into
    /// `batch`, which the caller applies in one go.
    private void handleEvent(WatchService service, Path dir, WatchEvent.Kind<?> kind, Path file,
            List<Runnable> batch) {
        if (tasksDir.equals(dir) && kind == StandardWatchEventKinds.ENTRY_CREATE && isProjectGroup(file)) {
            try {
                registerDirectory(service, file);
                String name = file.getFileName().toString();
                batch.add(() -> folders.add(name));
                scanDirectory(file, batch::add);
            } catch (IOException e) {
                Logger.warn("Cannot watch new task subfolder {}: {}", file, e.getMessage());
            }
            return;
        }
        // A group folder removed under the task directory: ENTRY_DELETE fires
        // on the parent carrying the folder's name. The path is already gone
        // (isDirectory can't tell), and its own WatchKey may have invalidated
        // first — deleting a non-empty folder races the key's removal from
        // watchedDirs against this event — so we cannot rely on containsValue.
        // Any non-task-file delete directly under tasksDir is treated as a
        // possible group removal: drop the folder and its tasks (a no-op when
        // the name was never a tracked group).
        if (tasksDir.equals(dir) && kind == StandardWatchEventKinds.ENTRY_DELETE
                && !isTaskFile(file)) {
            String name = file.getFileName().toString();
            watchedDirs.values().remove(file);
            batch.add(() -> {
                folders.remove(name);
                entries.removeIf(entry -> entry.id().startsWith(name + "/"));
            });
            return;
        }
        if (GROUP_CONFIG_FILE_NAME.equals(file.getFileName().toString())) {
            checkGroupConfig(file);
            batch.add(() -> onGroupConfigChange.run());
            return;
        }
        if (!isTaskFile(file)) {
            return;
        }
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            String id = relativeId(file);
            batch.add(() -> entries.removeIf(entry -> entry.id().equals(id)));
        } else {
            upsert(file, batch);
        }
    }

    /// Reads and parses the file on the **calling** thread (the startup scan's
    /// background thread or the watcher thread) and hops to the configured
    /// executor only for the list mutation: the read+parse is disk IO that
    /// stalled the FX thread on a slow drive when it rode `Platform.runLater`.
    private void upsert(Path file, List<Runnable> batch) {
        if (!Files.exists(file)) {
            String id = relativeId(file);
            batch.add(() -> entries.removeIf(entry -> entry.id().equals(id)));
            return;
        }
        TaskEntry entry = load(file);
        batch.add(() -> publish(entry));
    }

    private void publish(TaskEntry entry) {
        int existing = indexOf(entry.id());
        if (existing < 0) {
            entries.add(entry);
        } else if (!entries.get(existing).equals(entry)) {
            // Unchanged content is not republished: the watcher fires several
            // MODIFY events per write (and for pure mtime touches), and every
            // set() triggers a full task-list rebuild in the UI.
            entries.set(existing, entry);
        }
    }

    /// Applies a task-file rename (title adoption, drag'n'drop move) as a
    /// single in-place list change, on the calling thread — which must be the
    /// UI one. The watcher reports a rename as an unrelated delete plus a
    /// create: applied in that order the task vanishes from the list for a
    /// moment and pops back, a visible flicker. Replacing the entry under its
    /// new id first makes both watch events no-ops (the old id is gone, the
    /// new one already published with identical content).
    // [impl->dsn~claude-title-sync~3]
    // [impl->dsn~task-move-dnd~6]
    public void renamed(String fromId, String toId) {
        int existing = indexOf(fromId);
        Path file = tasksDir.resolve(toId + ".md");
        if (existing < 0 || !Files.exists(file)) {
            return;
        }
        entries.set(existing, load(file));
    }

    /// Publishes a whole scanned directory with a single list change (the
    /// additions), so the UI rebuilds once instead of once per task.
    private void publishAll(List<TaskEntry> batch) {
        List<TaskEntry> added = new ArrayList<>();
        for (TaskEntry entry : batch) {
            int existing = indexOf(entry.id());
            if (existing < 0) {
                added.add(entry);
            } else if (!entries.get(existing).equals(entry)) {
                entries.set(existing, entry);
            }
        }
        if (!added.isEmpty()) {
            entries.addAll(added);
        }
    }

    private TaskEntry load(Path file) {
        // [impl->dsn~frontmatter-duplicate-keys~1]
        logDuplicateKeys(file);
        try {
            return new TaskEntry.Loaded(parser.parse(tasksDir, file));
        } catch (TaskParseException e) {
            Logger.warn("Cannot parse task file {}: {}", file, e.getMessage());
            return new TaskEntry.Failed(relativeId(file), relativePath(file), e.getMessage());
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /// `TEMPLATE.md` is documentation to copy from and `CONTEXTSWITCHER.md`
    /// holds a group's defaults — neither is a task.
    // [impl->dsn~group-config-create~9]
    private static boolean isTaskFile(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".md")
                && !name.equals(TEMPLATE_FILE_NAME)
                && !name.equals(GROUP_CONFIG_FILE_NAME);
    }

    private String relativeId(Path file) {
        return TaskFileParser.relativeId(tasksDir, file);
    }

    private String relativePath(Path file) {
        return tasksDir.relativize(file).toString().replace(File.separatorChar, '/');
    }

    @Override
    public void close() {
        WatchService service = watchService;
        if (service != null) {
            try {
                service.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
