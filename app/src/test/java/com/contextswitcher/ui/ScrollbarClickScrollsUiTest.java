package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.contextswitcher.ssh.ProcessSshRunner;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.event.Event;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/// The terminal's scrollbar scrolls when clicked, and is only there when it
/// has something to scroll (`dsn~terminal-mouse-scroll~9`).
/// The local-selection filter sits on the widget's pane, which holds the
/// scrollbar too, and while the program has mouse reporting on it turned the
/// click into a text selection on the canvas — the scrollbar never saw it.
///
/// A real app-owned session, because only a live pty gets JediTermFX into
/// mouse-reporting mode: a PowerShell script prints enough lines for a
/// scrollback, then switches reporting on the way Claude Code does. Windows
/// only — an owned session is a ConPTY.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop.
// [utest->dsn~terminal-mouse-scroll~9]
@Tag("ui")
@EnabledOnOs(OS.WINDOWS)
@TestFxApplication(ScrollbarClickScrollsUiTest.TestApp.class)
class ScrollbarClickScrollsUiTest {

    private static final AtomicReference<Stage> STAGE = new AtomicReference<>();

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            stage.setScene(new Scene(new StackPane(), 900, 600));
            stage.show();
            STAGE.set(stage);
        }
    }

    @Test
    void clickingTheScrollbarTrackScrollsTheScrollback() throws Exception {
        // Scrollback first, then xterm mouse reporting on, as a TUI like Claude Code does.
        Path script = Files.createTempFile("scrollback", ".ps1");
        Files.writeString(script, """
                1..300 | ForEach-Object { Write-Host "line $_" }
                Write-Host -NoNewline "$([char]27)[?1000h$([char]27)[?1006h"
                Start-Sleep -Seconds 600
                """);
        AtomicReference<TerminalPane> pane = new AtomicReference<>();
        onFx(() -> {
            pane.set(new TerminalPane(Executors.newSingleThreadExecutor(), new ProcessSshRunner()));
            ((StackPane) STAGE.get().getScene().getRoot()).getChildren().setAll(pane.get().getRoot());
            pane.get().startOwned("scrollbar", List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", script.toString()), System.getProperty("user.home"));
        });
        try {
            ScrollBar scrollBar = await(() -> fx(() -> scrollBar(pane.get().getRoot())), "the terminal scrollbar");
            await(() -> fx(() -> scrollBar.getMin() < -100 ? Boolean.TRUE : null), "a scrollback of the script's lines");
            // Reporting is switched on after the lines; give the pty a moment to deliver it.
            Thread.sleep(1500);
            double before = fx(scrollBar::getValue);
            // The lowest value reached, not the value a moment later: output still
            // arriving can snap the view back to the bottom before the check runs.
            AtomicReference<Double> lowest = new AtomicReference<>(before);
            // The press is fired at the track node rather than clicked with the
            // robot: on a desktop in use the robot's click lands on whichever
            // window is in front. A fired event still travels the whole dispatch
            // chain, through the pane's selection filter this test is about.
            onFx(() -> {
                scrollBar.valueProperty().addListener((o, was, now) ->
                        lowest.accumulateAndGet(now.doubleValue(), Math::min));
                Node track = scrollBar.lookup(".track");
                Bounds bounds = track.getLayoutBounds();
                // The upper quarter of the track, clear of the thumb at the bottom: a page up.
                double x = bounds.getCenterX();
                double y = bounds.getMinY() + bounds.getHeight() / 4;
                javafx.geometry.Point2D scene = track.localToScene(x, y);
                javafx.geometry.Point2D screen = track.localToScreen(x, y);
                for (var type : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
                    Event.fireEvent(track, new MouseEvent(type, scene.getX(), scene.getY(), screen.getX(), screen.getY(),
                            MouseButton.PRIMARY, 1, false, false, false, false,
                            type == MouseEvent.MOUSE_PRESSED, false, false, false, false, true,
                            new PickResult(track, scene.getX(), scene.getY())));
                }
            });
            Thread.sleep(500);
            assertThat(lowest.get()).as("lowest scrollbar value after clicking its track").isLessThan(before);
        } finally {
            onFx(() -> pane.get().disconnect());
        }
    }

    /// The scrollbar is only shown while there is a scrollback to scroll:
    /// hidden on a fresh screen, shown once lines have scrolled off, hidden
    /// again in the alternate buffer Claude Code and tmux run in.
    @Test
    void theScrollbarIsOnlyShownWhileThereIsAScrollback() throws Exception {
        Path script = Files.createTempFile("alternate", ".ps1");
        Files.writeString(script, """
                Start-Sleep -Seconds 3
                1..300 | ForEach-Object { Write-Host "line $_" }
                Start-Sleep -Seconds 3
                Write-Host -NoNewline "$([char]27)[?1049h"
                Start-Sleep -Seconds 600
                """);
        AtomicReference<TerminalPane> pane = new AtomicReference<>();
        onFx(() -> {
            pane.set(new TerminalPane(Executors.newSingleThreadExecutor(), new ProcessSshRunner()));
            ((StackPane) STAGE.get().getScene().getRoot()).getChildren().setAll(pane.get().getRoot());
            pane.get().startOwned("autohide", List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", script.toString()), System.getProperty("user.home"));
        });
        try {
            ScrollBar scrollBar = await(() -> fx(() -> scrollBar(pane.get().getRoot())), "the terminal scrollbar");
            assertThat(fx(scrollBar::isVisible)).as("scrollbar before any scrollback").isFalse();
            await(() -> fx(() -> scrollBar.isVisible() ? Boolean.TRUE : null), "the scrollbar once lines scrolled off");
            await(() -> fx(() -> scrollBar.isVisible() ? null : Boolean.TRUE), "the scrollbar gone in the alternate buffer");
        } finally {
            onFx(() -> pane.get().disconnect());
        }
    }

    private static ScrollBar scrollBar(Node root) {
        if (root instanceof ScrollBar bar && bar.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
            return bar;
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                ScrollBar found = scrollBar(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T> T await(Supplier<T> query, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            T value = query.get();
            if (value != null) {
                return value;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + what);
    }

    private static <T> T fx(Supplier<T> query) {
        AtomicReference<T> result = new AtomicReference<>();
        try {
            onFx(() -> result.set(query.get()));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return result.get();
    }

    private static void onFx(Runnable work) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                work.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(20, TimeUnit.SECONDS)) {
            throw new AssertionError("FX timeout");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
