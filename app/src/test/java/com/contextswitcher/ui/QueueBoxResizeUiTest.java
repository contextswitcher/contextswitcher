package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;

import com.contextswitcher.tasks.TaskEntry;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// Dragging a queue box's bottom grip downwards makes that box taller
/// (`dsn~message-queue-ui~27`) — the `<textarea>` gesture. Only a live scene
/// has the grip's real height to grow from, hence a TestFX test rather than a
/// unit test. Fired at the add box's grip; every card wires the identical
/// handler through the shared row skeleton.
// [utest->dsn~message-queue-ui~27]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class QueueBoxResizeUiTest {

    @Test
    void draggingTheGripDownMakesTheBoxTaller() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.writeString(dir.resolve("alpha.md"), "---\ntitle: alpha\nstatus: active\n---\nnotes\n");

        ListView<Object> list = awaitListView();
        awaitFx(() -> indexOf(list, "alpha") >= 0, "the alpha row to load");
        runFx(() -> list.getSelectionModel().select(indexOf(list, "alpha")));

        // The add box is the queue pane's first — and, with an empty queue,
        // only — text box, so its grip is the first one beside a text area.
        Region grip = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(Region.class, ".resize-grip").fromAll()
                        .filter(g -> g.getParent().getChildrenUnmodifiable().getFirst() instanceof TextArea)
                        .findFirst(),
                "a queue resize grip");
        TextArea box = (TextArea) grip.getParent().getChildrenUnmodifiable().getFirst();
        double before = callDouble(box::getHeight);

        runFx(() -> {
            Event.fireEvent(grip, mouse(MouseEvent.MOUSE_PRESSED, 0));
            Event.fireEvent(grip, mouse(MouseEvent.MOUSE_DRAGGED, 60));
        });

        awaitFx(() -> box.getHeight() > before + 50, "the box to grow with the drag");
        assertThat(callDouble(box::getPrefHeight)).isGreaterThan(before + 50);
    }

    /// The grip under the sent area makes the last-sent box taller, and the
    /// history viewport follows it (`dsn~last-sent-message~5`).
    // [utest->dsn~last-sent-message~5]
    @Test
    void draggingTheSentGripDownMakesTheHistoryViewportTaller() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve(".queues"));
        Files.writeString(dir.resolve(".queues/sentgrip.sent.txt"), "hello");
        Files.writeString(dir.resolve("sentgrip.md"), "---\ntitle: sentgrip\nstatus: active\n---\nnotes\n");

        ListView<Object> list = awaitListView();
        awaitFx(() -> indexOf(list, "sentgrip") >= 0, "the sentgrip row to load");
        runFx(() -> list.getSelectionModel().select(indexOf(list, "sentgrip")));

        Region grip = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(Region.class, ".resize-grip").fromAll()
                        .filter(g -> g.getParent().getChildrenUnmodifiable().get(1) instanceof ScrollPane)
                        .filter(Node::isVisible).filter(g -> g.getParent().isVisible())
                        .findFirst(),
                "the sent-area resize grip");
        ScrollPane sent = (ScrollPane) grip.getParent().getChildrenUnmodifiable().get(1);
        awaitFx(() -> sent.getHeight() > 0, "the sent area to lay out");
        double before = callDouble(sent::getHeight);

        runFx(() -> {
            Event.fireEvent(grip, mouse(MouseEvent.MOUSE_PRESSED, 0));
            Event.fireEvent(grip, mouse(MouseEvent.MOUSE_DRAGGED, 60));
        });

        awaitFx(() -> sent.getHeight() > before + 50, "the sent viewport to grow with the drag");

        runFx(() -> Event.fireEvent(grip, mouse(MouseEvent.MOUSE_RELEASED, 60)));
        assertThat(Preferences.userNodeForPackage(QueuePane.class).getDouble(QueuePane.SENT_HEIGHT, -1))
                .isGreaterThan(before + 50);
    }

    /// A press/drag at screen y `screenY` — the handler reads screen
    /// coordinates, since the grip itself moves with the resize it causes.
    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double screenY) {
        return new MouseEvent(type, 5, 5, 0, screenY, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }

    private static ListView<Object> awaitListView() {
        return UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(v -> (ListView<Object>) v).findFirst(),
                "the task ListView");
    }

    private static int indexOf(ListView<Object> list, String id) {
        for (int i = 0; i < list.getItems().size(); i++) {
            if (list.getItems().get(i) instanceof TaskEntry.Loaded loaded && loaded.id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static double callDouble(java.util.function.DoubleSupplier body) {
        double[] result = {0};
        runFx(() -> result[0] = body.getAsDouble());
        return result[0];
    }

    private static void awaitFx(BooleanSupplier condition, String what) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (callFx(condition)) {
                return;
            }
            UiTestSupport.sleep();
        }
        throw new AssertionError("Timed out waiting for " + what);
    }

    private static void runFx(Runnable action) {
        callFx(() -> {
            action.run();
            return false;
        });
    }

    private static boolean callFx(BooleanSupplier body) {
        if (Platform.isFxApplicationThread()) {
            return body.getAsBoolean();
        }
        boolean[] result = {false};
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result[0] = body.getAsBoolean();
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("FX action did not complete");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        return result[0];
    }
}
