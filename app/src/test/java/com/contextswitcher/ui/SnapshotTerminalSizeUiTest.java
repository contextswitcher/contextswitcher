package com.contextswitcher.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.contextswitcher.ssh.ProcessSshRunner;
import com.contextswitcher.ssh.SshCommandRunner;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The suspend snapshot keeps the size it was captured with
/// (`dsn~terminal-suspend-snapshot~3`): a stored screen is already wrapped at
/// the remote pane's width, so letting the window resize the emulator re-wrapped
/// it — a line filling the last column merged with its successor and re-split
/// mid-word.
///
/// Drives [TerminalPane] directly rather than booting
/// [com.contextswitcher.Main]: the defect is in one pane's layout, and a real
/// snapshot would need a real suspended task on a real remote. The window
/// resize is played as what it is for the pane — its root growing and
/// shrinking, laid out each time.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~terminal-suspend-snapshot~3]
@Tag("ui")
@TestFxApplication(SnapshotTerminalSizeUiTest.TestApp.class)
class SnapshotTerminalSizeUiTest {

    /// A screen of lines exactly 100 columns wide — the case that re-wrapped.
    private static final String SNAPSHOT = ("x".repeat(100) + "\n").repeat(20);

    private static final AtomicReference<Stage> STAGE = new AtomicReference<>();

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            stage.setScene(new Scene(new Pane(), 1600, 900));
            stage.show();
            STAGE.set(stage);
        }
    }

    @Test
    void keepsTheCapturedSizeWhenThePaneGrows() {
        assertUnchangedAcross(700, 700, 1500, 850);
    }

    @Test
    void keepsTheCapturedSizeWhenThePaneShrinks() {
        assertUnchangedAcross(1500, 850, 400, 300);
    }

    private static void assertUnchangedAcross(double width, double height,
            double newWidth, double newHeight) {
        AtomicReference<Region> canvasPane = new AtomicReference<>();
        AtomicReference<double[]> before = new AtomicReference<>();
        AtomicReference<double[]> after = new AtomicReference<>();
        onFxThread(() -> {
            TerminalPane pane = new TerminalPane(
                    Executors.newSingleThreadExecutor(), new ProcessSshRunner());
            Region root = showSnapshot(pane);
            layoutAt(root, width, height);
            Region terminal = canvasOwner(root);
            canvasPane.set(terminal);
            before.set(new double[]{terminal.getWidth(), terminal.getHeight()});
            layoutAt(root, newWidth, newHeight);
            after.set(new double[]{terminal.getWidth(), terminal.getHeight()});
            // Stop the widget's redraw timer — an emulator left running past
            // the test paints into a scene that is on its way out.
            pane.disconnect();
        });

        assertThat(before.get()).as("the terminal has a size to keep").doesNotContain(0.0);
        assertThat(after.get()).as("terminal size after the resize").isEqualTo(before.get());
        assertThat(canvasPane.get().getScene()).as("still in the scene").isNotNull();
    }

    /// `pane` showing [#SNAPSHOT], put into the stage's scene.
    private static Region showSnapshot(TerminalPane pane) {
        pane.showSnapshot("Task is suspended.", SNAPSHOT);
        Region root = (Region) pane.getRoot();
        ((Pane) STAGE.get().getScene().getRoot()).getChildren().setAll(root);
        return root;
    }

    /// Sizes the pane's root as a window resize would and runs the layout pass
    /// that follows it.
    private static void layoutAt(Region root, double width, double height) {
        root.resize(width, height);
        // The ScrollPane around the snapshot only builds its viewport — and so
        // only takes its content into the scene graph — once its skin exists.
        root.applyCss();
        root.layout();
    }

    /// The parent of the first [Canvas] below `root` — jeditermfx 1.1.0's
    /// package-private `CanvasPane`, the region whose size drives the emulator.
    private static Region canvasOwner(Parent root) {
        Deque<Node> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Node node = pending.removeFirst();
            if (node instanceof Canvas canvas && canvas.getParent() instanceof Region owner) {
                return owner;
            }
            if (node instanceof Parent parent) {
                pending.addAll(parent.getChildrenUnmodifiable());
            }
        }
        throw new AssertionError("The snapshot pane holds no terminal canvas");
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
