package com.contextswitcher.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.contextswitcher.ssh.ProcessSshRunner;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// A click on a path Claude Code hard-wrapped across two rows opens the whole
/// path, not nothing (the first row has no extension) or the cut-off piece.
///
/// No session is mirrored, so opening a path that does not exist here ends in
/// the "No readable file …" alert — which names the path the click resolved.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~terminal-link-copy~3]
@Tag("ui")
@TestFxApplication(WrappedLinkClickUiTest.TestApp.class)
class WrappedLinkClickUiTest {

    private static final String HEAD = "/no-such-dir/" + "a".repeat(87);
    private static final String TAIL = "b/report.zip";

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
    void clickOnAWrappedPathOpensTheWholePath() throws Exception {
        assertThat(HEAD).hasSize(100);
        AtomicReference<TerminalPane> pane = new AtomicReference<>();
        onFxThread(() -> {
            TerminalPane terminal = new TerminalPane(Executors.newSingleThreadExecutor(), new ProcessSshRunner());
            pane.set(terminal);
            terminal.showSnapshot("Task is suspended.", HEAD + "\n" + TAIL + "\n");
            Region root = (Region) terminal.getRoot();
            ((Pane) STAGE.get().getScene().getRoot()).getChildren().setAll(root);
            root.resize(1500, 850);
            root.applyCss();
            root.layout();
        });
        // The emulator reads the snapshot on its own thread.
        Thread.sleep(1000);
        onFxThread(() -> {
            Canvas canvas = canvas((Parent) pane.get().getRoot());
            // Row 0, a few columns in: on the extension-less first piece.
            click(canvas, MouseEvent.MOUSE_PRESSED, 30, 5);
            click(canvas, MouseEvent.MOUSE_RELEASED, 30, 5);
            click(canvas, MouseEvent.MOUSE_CLICKED, 30, 5);
        });

        FutureTask<String> alertText = new FutureTask<>(() -> Window.getWindows().stream()
                .filter(window -> window.isShowing() && window.getScene().getRoot() instanceof DialogPane)
                .map(window -> ((DialogPane) window.getScene().getRoot()).getContent())
                .filter(Label.class::isInstance)
                .map(content -> ((Label) content).getText())
                .findFirst().orElse(""));
        Platform.runLater(alertText);
        try {
            assertThat(alertText.get(20, TimeUnit.SECONDS)).isEqualTo("No readable file " + HEAD + TAIL + ".");
        } finally {
            onFxThread(() -> {
                Window.getWindows().stream().filter(window -> window != STAGE.get()).toList().forEach(Window::hide);
                pane.get().disconnect();
            });
        }
    }

    private static void click(Canvas canvas, javafx.event.EventType<MouseEvent> type, double x, double y) {
        Point2D scene = canvas.localToScene(x, y);
        Point2D screen = canvas.localToScreen(x, y);
        Event.fireEvent(canvas, new MouseEvent(type, scene.getX(), scene.getY(), screen.getX(), screen.getY(),
                MouseButton.PRIMARY, 1, false, false, false, false, type == MouseEvent.MOUSE_PRESSED,
                false, false, false, false, true, null));
    }

    private static Canvas canvas(Parent root) {
        Deque<Node> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Node node = pending.removeFirst();
            if (node instanceof Canvas canvas) {
                return canvas;
            }
            if (node instanceof Parent parent) {
                pending.addAll(parent.getChildrenUnmodifiable());
            }
        }
        throw new AssertionError("The snapshot pane holds no terminal canvas");
    }

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
