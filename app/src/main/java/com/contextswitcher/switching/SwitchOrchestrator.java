package com.contextswitcher.switching;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import com.contextswitcher.tasks.Task;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Runs the configured actions of a task concurrently on the background
/// executor and reports per-action transitions (pending → running → ok/failed)
/// through the listener on the UI executor. One failing action never blocks
/// the others. A caller can further narrow the run to a subset of the
/// configured actions by name (the delete dialog's per-target checkboxes,
/// `dsn~claude-session-kill~6`) via the `only` overload.
// [impl->dsn~switch-orchestrator~3]
public class SwitchOrchestrator {

    /// Receives transitions on the UI executor.
    public interface Listener {
        void onUpdate(String action, ActionStatus status, String detail);
    }

    private final List<SwitchAction> actions;
    private final Executor background;
    private final Executor uiExecutor;

    public SwitchOrchestrator(List<SwitchAction> actions, Executor background, Executor uiExecutor) {
        this.actions = List.copyOf(actions);
        this.background = background;
        this.uiExecutor = uiExecutor;
    }

    /// Names of the actions the given task configures, in registration order.
    public List<String> configuredActions(Task task) {
        return actions.stream().filter(action -> action.isConfigured(task)).map(SwitchAction::name).toList();
    }

    public void switchTo(Task task, Listener listener) {
        switchTo(task, listener, () -> { });
    }

    /// `onAllDone` runs on the UI executor once every configured action has
    /// reported its final state (ok or failed) — e.g. to re-capture the
    /// terminal snapshot after a resume resurrected the window.
    public void switchTo(Task task, Listener listener, Runnable onAllDone) {
        switchTo(task, null, listener, onAllDone);
    }

    /// Like [#switchTo(Task,Listener,Runnable)], but runs only the configured
    /// actions named in `only` — the rest are skipped as if unconfigured, with
    /// no PENDING/RUNNING/OK notification at all. `null` runs every configured
    /// action (the unrestricted overloads' behavior).
    public void switchTo(Task task, @Nullable Set<String> only, Listener listener, Runnable onAllDone) {
        Logger.info("Switching to task {}", task.id());
        List<SwitchAction> configured = actions.stream()
                .filter(action -> action.isConfigured(task))
                .filter(action -> only == null || only.contains(action.name()))
                .toList();
        if (configured.isEmpty()) {
            uiExecutor.execute(onAllDone);
            return;
        }
        AtomicInteger remaining = new AtomicInteger(configured.size());
        for (SwitchAction action : configured) {
            notify(listener, action.name(), ActionStatus.PENDING, "");
        }
        for (SwitchAction action : configured) {
            background.execute(() -> {
                notify(listener, action.name(), ActionStatus.RUNNING, "");
                ActionResult result;
                try {
                    result = action.run(task);
                } catch (RuntimeException e) {
                    Logger.warn(e, "Action {} failed unexpectedly", action.name());
                    result = ActionResult.failure(e.toString());
                }
                notify(listener, action.name(),
                        result.ok() ? ActionStatus.OK : ActionStatus.FAILED, result.detail());
                if (remaining.decrementAndGet() == 0) {
                    uiExecutor.execute(onAllDone);
                }
            });
        }
    }

    private void notify(Listener listener, String action, ActionStatus status, String detail) {
        uiExecutor.execute(() -> listener.onUpdate(action, status, detail));
    }
}
