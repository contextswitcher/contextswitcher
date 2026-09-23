package com.contextswitcher.tasks.title;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

import com.contextswitcher.tasks.TaskFileParser;
import com.contextswitcher.tasks.TaskFileReadWrite;
import com.contextswitcher.tasks.TaskParseException;

/// Replaces a just-created task's description-as-title with a short summary —
/// once, and only while nobody has changed the title since.
///
/// The summary runs on `background` (a model round-trip takes seconds); the
/// read, the check and the write run on `fileThread`, the one thread every
/// other task-file edit of the app happens on, so a rename in between is
/// either fully before the check or fully after the write.
/// The replaced description is kept at the end of the body under `# Notes`.
// [impl->dsn~task-create-local-title~1]
public final class InitialTitleAdopter {

    private final TaskTitleSummarizer summarizer;
    private final TaskFileReadWrite files;
    private final Executor background;
    private final Executor fileThread;
    private final Consumer<String> onAdopted;

    /// `onAdopted` receives the file name after a title was written — the app
    /// reloads an editor showing that file, so a stale buffer is not saved
    /// over the new title.
    public InitialTitleAdopter(TaskTitleSummarizer summarizer, TaskFileReadWrite files,
            Executor background, Executor fileThread, Consumer<String> onAdopted) {
        this.summarizer = Objects.requireNonNull(summarizer, "summarizer");
        this.files = Objects.requireNonNull(files, "files");
        this.background = Objects.requireNonNull(background, "background");
        this.fileThread = Objects.requireNonNull(fileThread, "fileThread");
        this.onAdopted = Objects.requireNonNull(onAdopted, "onAdopted");
    }

    /// Starts summarizing the title the task `taskId` has right now, when that
    /// title reads as a description ([TaskTitles#looksLikeDescription]).
    /// Call on `fileThread`, right after the task file was written.
    public void adopt(String taskId) {
        String fileName = Objects.requireNonNull(taskId, "taskId") + ".md";
        String content = files.read(fileName);
        String original = content == null ? null : titleOf(taskId, content);
        if (original == null || !TaskTitles.looksLikeDescription(original)) {
            return;
        }
        background.execute(() -> summarizeSafely(original).ifPresent(title ->
                fileThread.execute(() -> apply(taskId, fileName, original, title))));
    }

    /// A summarizer that breaks its never-throw contract must not vanish
    /// silently inside an executor: logged, and the task keeps its title.
    private Optional<String> summarizeSafely(String description) {
        try {
            return summarizer.summarize(description);
        } catch (RuntimeException e) {
            Logger.warn(e, "Cannot summarize a task title");
            return Optional.empty();
        }
    }

    private void apply(String taskId, String fileName, String original, String title) {
        String content = files.read(fileName);
        if (content == null || title.equals(original)) {
            return;
        }
        if (!original.equals(titleOf(taskId, content))) {
            Logger.info("Not adopting \"{}\" for {}: its title changed meanwhile", title, fileName);
            return;
        }
        String updated = TaskFileParser.withNotesAppended(
                TaskFileParser.withTitle(content, title), original);
        String error = files.save(fileName, updated);
        if (error != null) {
            Logger.warn("Cannot write the summarized title of {}: {}", fileName, error);
            return;
        }
        Logger.info("Adopted summarized title for {}: {}", fileName, title);
        onAdopted.accept(fileName);
    }

    /// Null for a file that does not parse — nothing to compare against, so
    /// nothing is adopted.
    private static @Nullable String titleOf(String taskId, String content) {
        try {
            return new TaskFileParser().parse(taskId, content).title();
        } catch (TaskParseException e) {
            Logger.warn("Cannot read the title of {}: {}", taskId, e.getMessage());
            return null;
        }
    }
}
