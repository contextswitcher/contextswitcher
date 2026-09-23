package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~switch-orchestrator~3]
class SwitchOrchestratorTest {

    private record FakeAction(String name, boolean configured, ActionResult result) implements SwitchAction {
        @Override
        public boolean isConfigured(Task task) {
            return configured;
        }

        @Override
        public ActionResult run(Task task) {
            return result;
        }
    }

    private static final Task TASK =
            new Task("t", "t", TaskStatus.ACTIVE, null, null, null, null, null, null, null, "");

    @Test
    void runsOnlyConfiguredActionsAndReportsIndependentOutcomes() {
        SwitchOrchestrator orchestrator = new SwitchOrchestrator(List.of(
                new FakeAction("good", true, ActionResult.success("done")),
                new FakeAction("bad", true, ActionResult.failure("boom")),
                new FakeAction("skipped", false, ActionResult.success(""))),
                Runnable::run, Runnable::run);

        List<String> transitions = new ArrayList<>();
        orchestrator.switchTo(TASK, (action, status, detail) ->
                transitions.add("%s:%s%s".formatted(action, status, detail.isEmpty() ? "" : ":" + detail)));

        assertThat(orchestrator.configuredActions(TASK)).containsExactly("good", "bad");
        assertThat(transitions).containsExactly(
                "good:PENDING",
                "bad:PENDING",
                "good:RUNNING",
                "good:OK:done",
                "bad:RUNNING",
                "bad:FAILED:boom");
    }

    @Test
    void completionCallbackRunsOnceAfterAllActionsFinished() {
        SwitchOrchestrator orchestrator = new SwitchOrchestrator(List.of(
                new FakeAction("good", true, ActionResult.success("done")),
                new FakeAction("bad", true, ActionResult.failure("boom"))),
                Runnable::run, Runnable::run);

        List<String> events = new ArrayList<>();
        orchestrator.switchTo(TASK, (action, status, detail) -> events.add(action + ":" + status),
                () -> events.add("all-done"));

        assertThat(events.getLast()).isEqualTo("all-done");
        assertThat(events.stream().filter("all-done"::equals)).hasSize(1);
    }

    @Test
    void completionCallbackRunsForTaskWithoutConfiguredActions() {
        SwitchOrchestrator orchestrator = new SwitchOrchestrator(
                List.of(new FakeAction("skipped", false, ActionResult.success(""))),
                Runnable::run, Runnable::run);

        List<String> events = new ArrayList<>();
        orchestrator.switchTo(TASK, (action, status, detail) -> events.add(action),
                () -> events.add("all-done"));

        assertThat(events).containsExactly("all-done");
    }

    // [utest->dsn~switch-orchestrator~3]
    @Test
    void onlyRestrictsToTheNamedConfiguredActions() {
        SwitchOrchestrator orchestrator = new SwitchOrchestrator(List.of(
                new FakeAction("tmux", true, ActionResult.success("")),
                new FakeAction("browser", true, ActionResult.success(""))),
                Runnable::run, Runnable::run);

        List<String> transitions = new ArrayList<>();
        orchestrator.switchTo(TASK, Set.of("browser"),
                (action, status, detail) -> transitions.add(action + ":" + status), () -> { });

        assertThat(transitions).containsExactly("browser:PENDING", "browser:RUNNING", "browser:OK");
    }

    // [utest->dsn~switch-orchestrator~3]
    @Test
    void onlyWithNoMatchingActionRunsCompletionCallbackAlone() {
        SwitchOrchestrator orchestrator = new SwitchOrchestrator(
                List.of(new FakeAction("tmux", true, ActionResult.success(""))),
                Runnable::run, Runnable::run);

        List<String> events = new ArrayList<>();
        orchestrator.switchTo(TASK, Set.of("browser"),
                (action, status, detail) -> events.add(action), () -> events.add("all-done"));

        assertThat(events).containsExactly("all-done");
    }

    @Test
    void throwingActionBecomesFailedNotCrash() {
        SwitchAction throwing = new SwitchAction() {
            @Override
            public String name() {
                return "explosive";
            }

            @Override
            public boolean isConfigured(Task task) {
                return true;
            }

            @Override
            public ActionResult run(Task task) {
                throw new IllegalStateException("kaboom");
            }
        };
        SwitchOrchestrator orchestrator =
                new SwitchOrchestrator(List.of(throwing), Runnable::run, Runnable::run);

        List<String> last = new ArrayList<>();
        orchestrator.switchTo(TASK, (action, status, detail) -> last.add(status + ":" + detail));

        assertThat(last.getLast()).startsWith("FAILED:").contains("kaboom");
    }
}
