package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.local.LocalFolderFocus;
import com.contextswitcher.tasks.Task;

/// Focuses (or opens) the task's local `folders` in File Explorer — a window
/// already showing a folder is brought forward rather than opening another.
/// Every folder is handled, in file order; the last one ends up in front.
// [impl->dsn~explorer-folder-focus~3]
public class ExplorerFolderAction implements SwitchAction {

    private final LocalFolderFocus focus;

    public ExplorerFolderAction(LocalFolderFocus focus) {
        this.focus = focus;
    }

    @Override
    public String name() {
        return "folder";
    }

    @Override
    public boolean isConfigured(Task task) {
        return !task.folders().isEmpty();
    }

    @Override
    public ActionResult run(Task task) {
        return open(task.folders());
    }

    /// Focuses (or opens) each of the given folders, the switch action's own
    /// body — also called for a whole category, whose header button opens its
    /// `folders:` without switching to any of its tasks.
    // [impl->dsn~category-folders-button~1]
    public ActionResult open(List<String> folders) {
        if (folders.isEmpty()) {
            return ActionResult.failure("folder not configured");
        }
        // Every folder is attempted even after one fails — the others are
        // independent windows, and the detail names the ones that did not open.
        List<String> failed = new ArrayList<>();
        String lastDetail = "";
        for (String folder : folders) {
            LocalFolderFocus.FocusResult result = focus.focus(folder);
            lastDetail = result.detail();
            if (!result.ok()) {
                failed.add(folder);
            }
        }
        if (failed.isEmpty()) {
            return ActionResult.success(folders.size() == 1 ? lastDetail
                    : "%d folders: %s".formatted(folders.size(), String.join(", ", folders)));
        }
        return ActionResult.failure(failed.size() == folders.size() && folders.size() == 1
                ? lastDetail
                : "%d of %d failed: %s".formatted(failed.size(), folders.size(),
                        String.join(", ", failed)));
    }
}
