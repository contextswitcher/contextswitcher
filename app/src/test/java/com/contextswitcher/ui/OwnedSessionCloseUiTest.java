package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.contextswitcher.ssh.ProcessSshRunner;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/// Closing an app-owned session ends its whole process tree
/// (`dsn~terminal-process-close~1`): the pty's `cmd.exe` and the program it
/// runs. A real ConPTY session, because only a live pty shows how the
/// widget's close, the connector's close and the process interact — the
/// connector is closed twice from two threads, and the process must still end
/// exactly once. Windows only — an owned session is a ConPTY.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop.
// [utest->dsn~terminal-process-close~1]
@Tag("ui")
@EnabledOnOs(OS.WINDOWS)
@TestFxApplication(OwnedSessionCloseUiTest.TestApp.class)
class OwnedSessionCloseUiTest {

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
    void closingTheSessionEndsTheShellAndTheProgramItRuns() throws Exception {
        AtomicReference<TerminalPane> pane = new AtomicReference<>();
        onFx(() -> {
            pane.set(new TerminalPane(Executors.newSingleThreadExecutor(), new ProcessSshRunner()));
            ((StackPane) STAGE.get().getScene().getRoot()).getChildren().setAll(pane.get().getRoot());
            pane.get().startOwned("close", List.of("cmd.exe", "/k", "ping -t 127.0.0.1"),
                    System.getProperty("user.home"));
        });
        List<ProcessHandle> tree = awaitTree(pane.get());
        onFx(() -> pane.get().closeOwned());
        long deadline = System.currentTimeMillis() + 5_000;
        while (tree.stream().anyMatch(ProcessHandle::isAlive) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        try {
            assertThat(tree.stream().filter(ProcessHandle::isAlive).map(ProcessHandle::pid))
                    .as("processes of the closed session still running").isEmpty();
        } finally {
            tree.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        }
    }

    /// The session's pty process and its descendants, once `ping` has started.
    private static List<ProcessHandle> awaitTree(TerminalPane pane) throws Exception {
        AtomicReference<Long> pid = new AtomicReference<>();
        onFx(() -> {
            try {
                var owned = TerminalPane.class.getDeclaredField("owned");
                owned.setAccessible(true);
                var session = (OwnedSession) ((java.util.Map<?, ?>) owned.get(pane)).get("close");
                var process = OwnedSession.class.getDeclaredField("process");
                process.setAccessible(true);
                pid.set(((Process) process.get(session)).pid());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        ProcessHandle root = ProcessHandle.of(pid.get()).orElseThrow();
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            List<ProcessHandle> tree = new ArrayList<>();
            tree.add(root);
            root.descendants().forEach(tree::add);
            if (tree.size() > 1) {
                return tree;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("ping never started under the session's shell");
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
