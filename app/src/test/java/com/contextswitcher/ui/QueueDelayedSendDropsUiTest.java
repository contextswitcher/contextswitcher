package com.contextswitcher.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.contextswitcher.discovery.TmuxStatusPoller;
import com.contextswitcher.queue.QueueFile;
import com.contextswitcher.queue.SendResult;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/// A message armed for the delayed send (`dsn~message-queue-delayed-send~4`)
/// leaves the queue once it went out — it used to stay there next to its own
/// "Last sent" record.
// [utest->dsn~message-queue-delayed-send~4]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class QueueDelayedSendDropsUiTest {

    @TempDir
    Path queues;

    @Test
    void deliveredMessageLeavesTheQueue() throws Exception {
        Task task = new Task("delayed", "delayed", TaskStatus.ACTIVE, "host",
                new Task.TmuxConfig("0", "@1"), null, null, null, null, null, "");
        QueueFile.save(QueueFile.file(queues, task.id()), List.of("follow-up"));
        onFx(() -> {
            QueuePane pane = new QueuePane(queues, queues.resolve("qodo"), queues.resolve("att"), queues.resolve("armed.yaml"),
                    (t, text, progress) -> new SendResult.Sent(), t -> Map.of(),
                    (t, url) -> {}, (t, text) -> {}, Runnable::run, () -> {}, t -> {}, id -> task);
            pane.showTask(task);
            // The cards sit in a ScrollPane, whose content joins the tree only with its skin.
            Parent root = (Parent) pane.getRoot();
            new Scene(root);
            root.applyCss();
            ((ToggleButton) root.lookup(".toggle-button")).fire();
            pane.sendDelayed(Map.of(TmuxStatusPoller.key("host", "@1"), "waiting"));
        });
        // The delivery's callback is posted back to the FX thread.
        onFx(() -> {});
        assertThat(QueueFile.load(QueueFile.file(queues, task.id()))).isEmpty();
    }

    /// The last armed message going out used to leave its task blocked for
    /// good: the busy tick that lifts the block was only looked at for tasks
    /// with armed messages, so a message armed later on the idle chat never
    /// went out (field report 2026-09-14).
    @Test
    void messageArmedOnIdleChatAfterEarlierDelayedSendGoesOut() throws Exception {
        Task task = new Task("delayed", "delayed", TaskStatus.ACTIVE, "host",
                new Task.TmuxConfig("0", "@1"), null, null, null, null, null, "");
        QueueFile.save(QueueFile.file(queues, task.id()), List.of("first", "second"));
        Map<String, String> waiting = Map.of(TmuxStatusPoller.key("host", "@1"), "waiting");
        QueuePane[] pane = new QueuePane[1];
        onFx(() -> {
            pane[0] = pane(queues.resolve("armed-blocked.yaml"), task);
            pane[0].showTask(task);
            Parent root = (Parent) pane[0].getRoot();
            new Scene(root);
            root.applyCss();
            ((ToggleButton) root.lookup(".toggle-button")).fire();
            pane[0].sendDelayed(waiting);
        });
        onFx(() -> {
            pane[0].sendDelayed(Map.of(TmuxStatusPoller.key("host", "@1"), "working"));
            pane[0].sendDelayed(waiting);
            Parent root = (Parent) pane[0].getRoot();
            root.applyCss();
            ((ToggleButton) root.lookup(".toggle-button")).fire();
            pane[0].sendDelayed(waiting);
        });
        onFx(() -> {});
        assertThat(QueueFile.load(QueueFile.file(queues, task.id()))).isEmpty();
    }

    /// An armed message survives an app restart: a fresh pane on the same
    /// armed file still delivers it once the chat falls idle.
    @Test
    void armedMessageSurvivesARestart() throws Exception {
        Task task = new Task("restart", "restart", TaskStatus.ACTIVE, "host",
                new Task.TmuxConfig("0", "@2"), null, null, null, null, null, "");
        QueueFile.save(QueueFile.file(queues, task.id()), List.of("after restart"));
        Path armedFile = queues.resolve("armed.yaml");
        onFx(() -> {
            QueuePane before = pane(armedFile, task);
            before.showTask(task);
            Parent root = (Parent) before.getRoot();
            new Scene(root);
            root.applyCss();
            ((ToggleButton) root.lookup(".toggle-button")).fire();
            // The "restart": a new pane that was never shown the task.
            pane(armedFile, task).sendDelayed(Map.of(TmuxStatusPoller.key("host", "@2"), "waiting"));
        });
        onFx(() -> {});
        assertThat(QueueFile.load(QueueFile.file(queues, task.id()))).isEmpty();
        assertThat(armedFile).doesNotExist();
    }

    private QueuePane pane(Path armedFile, Task task) {
        return new QueuePane(queues, queues.resolve("qodo"), queues.resolve("att"), armedFile,
                (t, text, progress) -> new SendResult.Sent(), t -> Map.of(),
                (t, url) -> {}, (t, text) -> {}, Runnable::run, () -> {}, t -> {}, id -> task);
    }

    private static void onFx(Runnable action) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                action.run();
                done.complete(null);
            } catch (Throwable e) {
                done.completeExceptionally(e);
            }
        });
        done.get(10, TimeUnit.SECONDS);
    }
}
