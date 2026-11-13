package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.contextswitcher.tasks.Task;

/// Opens a new VS Code window (`code -n`) on the desktop in view, at the
/// task's project directory — the same one the IntelliJ action opens
/// (`Task.intellijProjectPath()`); a task with a host opens it through
/// Remote-SSH (`--remote ssh-remote+<host>`). Enabled by `vscode: true`.
// [impl->dsn~vscode-action~1]
public class VsCodeAction implements SwitchAction {

    private final Predicate<Task> enabled;
    /// Runs the command; returns null on success, else the failure detail.
    private final Function<List<String>, @Nullable String> launcher;

    public VsCodeAction(Predicate<Task> enabled, Function<List<String>, @Nullable String> launcher) {
        this.enabled = enabled;
        this.launcher = launcher;
    }

    @Override
    public String name() {
        return "vscode";
    }

    @Override
    public boolean isConfigured(Task task) {
        return enabled.test(task);
    }

    /// The `code` argv (without the Windows `cmd /c` the launcher adds).
    static List<String> command(Task task) {
        List<String> argv = new ArrayList<>(List.of("code", "-n"));
        String path = task.intellijProjectPath();
        String remote = task.intellijRemote();
        if (path != null) {
            if (remote != null) {
                argv.add("--remote");
                argv.add("ssh-remote+" + remote);
            }
            argv.add(path);
        }
        return argv;
    }

    @Override
    public ActionResult run(Task task) {
        List<String> argv = command(task);
        @Nullable String failure = launcher.apply(argv);
        return failure == null
                ? ActionResult.success(String.join(" ", argv))
                : ActionResult.failure(failure);
    }
}
