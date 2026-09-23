package com.contextswitcher.ui;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.contextswitcher.switching.ActionStatus;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The status bar's transient hover text — a row's PR under the pointer
/// (`dsn~pr-state-indicator~3`). Drives the bar directly instead of booting
/// [com.contextswitcher.Main] and hunting for an icon: the defect this pins
/// was entirely in the bar (a fresh one has no children, so the hover text
/// went to a label outside the scene and nothing showed until an unrelated
/// switch or message had put it there).
///
/// Each test builds its own bar, so none depends on what another left behind —
/// the fresh-bar case in particular is only meaningful on an untouched bar.
/// The app exists to boot the FX toolkit; a `Label` cannot be built without it.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `xvfb-run -a` headless.
// [utest->dsn~pr-state-indicator~3]
@Tag("ui")
@TestFxApplication(SwitchStatusBarUiTest.TestApp.class)
class SwitchStatusBarUiTest {

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            stage.setScene(new Scene(new Pane(), 400, 40));
            stage.show();
        }
    }

    /// The regression: on an untouched bar (no switch, no message yet) the
    /// hover text must still show.
    @Test
    void showsHoverTextOnAFreshBar() {
        List<String> texts = withBar(bar -> bar.hover("https://github.com/o/r/pull/1"));

        assertThat(texts).contains("https://github.com/o/r/pull/1");
    }

    @Test
    void putsThePreviousMessageBackOnLeave() {
        List<String> texts = withBar(bar -> {
            bar.message("Opening …");
            bar.hover("https://github.com/o/r/pull/1");
            bar.hover(null);
        });

        assertThat(texts).contains("Opening …").doesNotContain("https://github.com/o/r/pull/1");
    }

    /// A switch running while the user hovers keeps reporting its actions —
    /// only the label is swapped, never the chips.
    @Test
    void keepsTheActionChipsWhileHovering() {
        List<String> texts = withBar(bar -> {
            bar.beginSwitch("T");
            bar.update("tmux", ActionStatus.OK, "");
            bar.hover("https://github.com/o/r/pull/1");
        });

        assertThat(texts).contains("tmux ✓", "https://github.com/o/r/pull/1");
    }

    /// A message arriving mid-hover wins: the leave must not resurrect the
    /// text the message replaced.
    @Test
    void aMessageDuringHoverIsNotUndoneByTheLeave() {
        List<String> texts = withBar(bar -> {
            bar.message("first");
            bar.hover("https://github.com/o/r/pull/1");
            bar.message("second");
            bar.hover(null);
        });

        assertThat(texts).contains("second").doesNotContain("first");
    }

    /// A message too long for the bar is ellipsized inside it — the label
    /// shrinks, the chip beside it keeps its size — and stays readable in the
    /// tooltip.
    // [utest->dsn~status-bar-one-line~2]
    @Test
    void aLongMessageIsCutToTheBarAndKeptInTheTooltip() {
        String message = "Cannot start Claude: " + "x".repeat(500);
        AtomicReference<Label> label = new AtomicReference<>();
        AtomicReference<Label> chip = new AtomicReference<>();
        onFxThread(bar -> {
            bar.message(message);
            bar.update("tmux", ActionStatus.OK, "");
            bar.resize(400, 28);
            bar.applyCss();
            bar.layout();
            label.set((Label) bar.getChildren().getFirst());
            chip.set((Label) bar.getChildren().getLast());
        });

        assertThat(label.get().getWidth()).as("label width").isLessThan(400);
        assertThat(chip.get().getWidth()).as("chip width").isEqualTo(chip.get().prefWidth(-1));
        assertThat(label.get().getTooltip().getText()).isEqualTo(message);
    }

    /// Builds a fresh bar on the FX thread, runs `actions` on it, and returns
    /// the texts of the labels it then holds — controls may only be touched
    /// there, and the assertions must not race that run.
    private static List<String> withBar(Consumer<SwitchStatusBar> actions) {
        AtomicReference<List<String>> texts = new AtomicReference<>();
        onFxThread(bar -> {
            actions.accept(bar);
            texts.set(bar.getChildren().stream()
                    .filter(Label.class::isInstance)
                    .map(node -> ((Label) node).getText())
                    .toList());
        });
        return texts.get();
    }

    /// Runs `actions` on a fresh bar on the FX thread and waits for it.
    private static void onFxThread(Consumer<SwitchStatusBar> actions) {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                actions.accept(new SwitchStatusBar());
            } finally {
                done.countDown();
            }
        });
        try {
            assertThat(done.await(10, TimeUnit.SECONDS)).as("FX thread ran the actions").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
