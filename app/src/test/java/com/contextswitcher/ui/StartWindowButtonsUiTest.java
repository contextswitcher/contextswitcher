package com.contextswitcher.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.contextswitcher.ssh.ProcessSshRunner;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The terminal pane's "No tmux configured" placeholder carries the same
/// **tmux** / **claude** window choice play offers (`dsn~remote-window-choice~6`),
/// so a task creation that died before it got a window can be finished from
/// where it is seen.
///
/// Drives [TerminalPane] directly — the buttons are fired rather than clicked,
/// which is what a robot would end up doing to a placeholder that owns no
/// remote.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~remote-window-choice~6]
@Tag("ui")
@TestFxApplication(StartWindowButtonsUiTest.TestApp.class)
class StartWindowButtonsUiTest {

    private static final AtomicReference<Stage> STAGE = new AtomicReference<>();

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            stage.setScene(new Scene(new Pane(), 800, 600));
            stage.show();
            STAGE.set(stage);
        }
    }

    @Test
    void claudeButtonStartsAClaudeWindowAndBothButtonsGoDead() {
        List<String> started = new ArrayList<>();
        AtomicReference<List<Button>> buttons = new AtomicReference<>();
        onFxThread(() -> {
            TerminalPane pane = new TerminalPane(
                    Executors.newSingleThreadExecutor(), new ProcessSshRunner());
            pane.setWindowStarter((task, withClaude) ->
                    started.add(task.id() + (withClaude ? " claude" : " tmux")));
            Region root = showPlaceholder(pane);
            List<Button> found = buttonsOf(root);
            found.stream().filter(button -> "claude".equals(button.getText()))
                    .forEach(Button::fire);
            buttons.set(found);
        });

        assertThat(buttons.get()).extracting(Button::getText)
                .as("the placeholder's recovery buttons")
                .containsExactly("tmux", "claude");
        assertThat(started).containsExactly("t claude");
        assertThat(buttons.get()).as("dead for the round-trip").allMatch(Button::isDisabled);
    }

    @Test
    void barButtonsAreDisabledUnderThePlaceholder() {
        AtomicReference<Region> root = new AtomicReference<>();
        onFxThread(() -> root.set(showPlaceholder(new TerminalPane(
                Executors.newSingleThreadExecutor(), new ProcessSshRunner()))));

        assertThat(root.get().lookup("#show-diff-button"))
                .as("nothing connected, so the bar is dead")
                .matches(Node::isDisabled);
    }

    /// `pane` showing the placeholder of a remote-only task, in the scene.
    private static Region showPlaceholder(TerminalPane pane) {
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "devbox",
                null, null, null, null, null, null, "");
        pane.showStartWindowMessage("No tmux configured for this task.", task);
        Region root = (Region) pane.getRoot();
        ((Pane) STAGE.get().getScene().getRoot()).getChildren().setAll(root);
        root.applyCss();
        root.layout();
        return root;
    }

    /// The placeholder's own buttons below `root`, in scene-graph order — the
    /// pane's bar (Clear input, Jump to bottom, …) holds buttons too.
    private static List<Button> buttonsOf(Parent root) {
        List<Button> found = new ArrayList<>();
        Deque<Node> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Node node = pending.removeFirst();
            if (node instanceof Button button
                    && List.of("tmux", "claude").contains(button.getText())) {
                found.add(button);
            }
            if (node instanceof Parent parent) {
                pending.addAll(parent.getChildrenUnmodifiable());
            }
        }
        return found;
    }

    /// Runs `work` on the FX thread and waits for it — controls may only be
    /// touched there, and the assertions must not race the run.
    private static void onFxThread(Runnable work) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                work.run();
            } catch (Throwable e) {
                failure.set(e);
            } finally {
                done.countDown();
            }
        });
        try {
            assertThat(done.await(20, TimeUnit.SECONDS)).as("FX thread ran the work").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        if (failure.get() != null) {
            throw new AssertionError("The FX work failed", failure.get());
        }
    }
}
