package com.contextswitcher.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.terminal.TmuxMirrorCommands;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// For a tmux mirror, the terminal bar's **Clear input** and **Jump to bottom**
/// send the side-channel commands [TmuxMirrorCommands] builds, to the mirrored
/// host — pinned against a recording runner, so the remote route is covered
/// without a remote at hand.
///
/// The pane's executor only queues. `show` hands the attach (the mirror repair
/// over ssh, then the pty) to it as one task; leaving that task unrun keeps the
/// test from starting `ssh`. The tasks the buttons queue are run by hand.
///
/// **Files** lists over its own `ProcessSshRunner`, not the injected runner, so
/// it is not covered here.
///
/// Drives [TerminalPane] directly; needs a display: `gradlew :app:uiTest` on a
/// desktop, `just uitest` headless.
// [utest->dsn~terminal-clear-input~3]
// [utest->dsn~terminal-jump-to-bottom~1]
// [utest->dsn~terminal-interrupt~1]
@Tag("ui")
@TestFxApplication(MirrorBarCommandsUiTest.TestApp.class)
class MirrorBarCommandsUiTest {

    private static final String REMOTE = "devbox";
    private static final Task.TmuxConfig TMUX = new Task.TmuxConfig("work", "@7");

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            stage.setScene(new Scene(new Pane(), 400, 300));
            stage.show();
        }
    }

    @Test
    void clearInputAndJumpToBottomSendTheMirrorCommandsToTheRemote() {
        QueueingExecutor executor = new QueueingExecutor();
        RecordingRunner ssh = new RecordingRunner();
        AtomicInteger beforeButtons = new AtomicInteger();
        onFxThread(() -> {
            TerminalPane pane = new TerminalPane(executor, ssh);
            pane.show(REMOTE, TMUX);
            beforeButtons.set(executor.size());
            button(pane.getRoot(), "Clear input").fire();
            button(pane.getRoot(), "Jump to bottom").fire();
            button(pane.getRoot(), "Accept suggestion").fire();
            button(pane.getRoot(), "Ctrl+C").fire();
        });

        executor.runFrom(beforeButtons.get());

        assertThat(ssh.sent()).containsExactly(
                new Sent(REMOTE, TmuxMirrorCommands.clearInputCommand(TMUX)),
                new Sent(REMOTE, TmuxMirrorCommands.cancelCopyModeCommand(TMUX)),
                new Sent(REMOTE, TmuxMirrorCommands.acceptSuggestionCommand(TMUX)),
                new Sent(REMOTE, TmuxMirrorCommands.interruptCommand(TMUX)));
    }

    /// One command the pane handed to the runner.
    private record Sent(String host, List<String> command) {
    }

    /// Records every command and answers success, touching no network.
    private static final class RecordingRunner implements SshCommandRunner {

        private final List<Sent> sent = new CopyOnWriteArrayList<>();

        List<Sent> sent() {
            return sent;
        }

        @Override
        public SshResult run(String host, List<String> remoteCommand) {
            sent.add(new Sent(host, List.copyOf(remoteCommand)));
            return new SshResult(0, "", "");
        }

        @Override
        public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
            return run(host, remoteCommand);
        }
    }

    /// Queues every task instead of running it; the test picks what runs.
    private static final class QueueingExecutor extends AbstractExecutorService {

        private final List<Runnable> queued = new CopyOnWriteArrayList<>();

        int size() {
            return queued.size();
        }

        /// Runs the tasks queued from position `index` on, on this thread.
        void runFrom(int index) {
            List<Runnable> snapshot = List.copyOf(queued);
            snapshot.subList(index, snapshot.size()).forEach(Runnable::run);
        }

        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    /// The button labelled `text` below `root`.
    private static Button button(Node root, String text) {
        Deque<Node> pending = new ArrayDeque<>(List.of(root));
        while (!pending.isEmpty()) {
            Node node = pending.removeFirst();
            if (node instanceof Button button && text.equals(button.getText())) {
                return button;
            }
            if (node instanceof Parent parent) {
                pending.addAll(parent.getChildrenUnmodifiable());
            }
        }
        throw new AssertionError("No button labelled " + text);
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
