package com.contextswitcher.switching;

import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

import com.contextswitcher.tasks.Task;

/// Opens the task's note (a OneNote `onenote:` link or any other URL) on
/// switch, so activating a task brings up its project page in the note tool.
/// The effective note is the task's own `note:` field, falling back to the
/// note of its category — the group's `CONTEXTSWITCHER.md` `note:` (resolved
/// by the injected group-note resolver). The URL is launched via the injected
/// opener (`Main::openUrl`: OS protocol handler → the OneNote desktop app for
/// `onenote:` links), which keeps this action free of AWT/JavaFX and thus
/// unit-testable.
// [impl->dsn~note-focus-action~1]
public class NoteFocusAction implements SwitchAction {

    private final Consumer<String> opener;
    /// Resolves a group's `CONTEXTSWITCHER.md` `note:` URL by group key;
    /// null when the group has no config file or no note.
    private final Function<String, @Nullable String> groupNoteResolver;

    public NoteFocusAction(Consumer<String> opener,
            Function<String, @Nullable String> groupNoteResolver) {
        this.opener = opener;
        this.groupNoteResolver = groupNoteResolver;
    }

    @Override
    public String name() {
        return "note";
    }

    @Override
    public boolean isConfigured(Task task) {
        return effectiveNote(task) != null;
    }

    @Override
    public ActionResult run(Task task) {
        boolean fromTask = task.note() != null;
        String note = effectiveNote(task);
        if (note == null) {
            return ActionResult.failure("no note configured on the task or its category");
        }
        Logger.debug("Opening note {} ({})", note, fromTask ? "task" : "category");
        try {
            opener.accept(note);
        } catch (RuntimeException e) {
            return ActionResult.failure("cannot open note: " + e.getMessage());
        }
        return ActionResult.success(fromTask ? "opening task note" : "opening category note");
    }

    /// The task's own `note:`, or — when it has none — its category note.
    /// A root-level task (no group) has no category, so the resolver is
    /// consulted only for a grouped task.
    private @Nullable String effectiveNote(Task task) {
        if (task.note() != null) {
            return task.note();
        }
        String group = group(task);
        return group.isEmpty() ? null : groupNoteResolver.apply(group);
    }

    /// The task's group key (its subfolder path), or "" for a root-level task
    /// — mirrors `TaskEntry.group()` without needing the entry wrapper.
    private static String group(Task task) {
        int slash = task.id().lastIndexOf('/');
        return slash < 0 ? "" : task.id().substring(0, slash);
    }
}
