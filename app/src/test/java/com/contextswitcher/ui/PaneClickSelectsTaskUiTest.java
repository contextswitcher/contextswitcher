package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.contextswitcher.tasks.TaskEntry;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import jfx.incubator.scene.control.richtext.RichTextArea;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// A click in a pane that belongs to the previewed task re-selects that task's
/// row (`dsn~pane-click-selects-task~1`). The interesting case is *drift*: the
/// list selection can be cleared (a rebuild's transient `null` is a deliberate
/// no-op, "Add task…" moves focus off the list) while the editor/terminal/queue
/// keep previewing the task — so the user works in a pane with no highlighted
/// row. Only a running scene can prove a real click re-selects it, hence a
/// TestFX test. Fires the click at the editor lane; the terminal and queue
/// wire the identical filter.
// [utest->dsn~pane-click-selects-task~1]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class PaneClickSelectsTaskUiTest {

    @Test
    void clickingTheEditorReselectsThePreviewedTaskAfterSelectionDrift() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.writeString(dir.resolve("alpha.md"), taskFile("alpha"));
        Files.writeString(dir.resolve("beta.md"), taskFile("beta"));

        ListView<Object> list = awaitListView();
        awaitFx(() -> indexOfId(list, "alpha") >= 0, "the alpha row to load");

        // Select alpha — the editor lane now previews it.
        runFx(() -> list.getSelectionModel().select(indexOfId(list, "alpha")));
        awaitFx(() -> selectedId(list).equals("alpha"), "alpha to be selected");

        // Drift: clear the selection. A null selection is a no-op for the panes,
        // so they keep previewing alpha while the list highlights nothing.
        runFx(() -> list.getSelectionModel().clearSelection());
        awaitFx(() -> selectedId(list).isEmpty(), "the list selection to clear");

        // A click anywhere in the editor lane re-selects the previewed task.
        RichTextArea editor = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(RichTextArea.class).fromAll().findFirst(),
                "the editor");
        runFx(() -> Event.fireEvent(editor, mousePress()));

        awaitFx(() -> selectedId(list).equals("alpha"), "alpha to be re-selected by the click");
    }

    /// The re-selection must not double as a scroll command
    /// (`dsn~task-list-scroll-into-view~1`): clicking into a pane's input while
    /// the row is on screen has to leave the viewport alone, and a row that
    /// really is off-screen has to land away from the edges.
    // [utest->dsn~task-list-scroll-into-view~1]
    @Test
    void clickingTheEditorKeepsAVisibleRowInPlaceAndLandsAnOffscreenOneAThirdDown() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        for (int i = 0; i < 60; i++) {
            Files.writeString(dir.resolve("task-%02d.md".formatted(i)), taskFile("task-" + i));
        }

        ListView<Object> list = awaitListView();
        awaitFx(() -> indexOfId(list, "task-40") >= 0, "the rows to load");
        AtomicInteger target = new AtomicInteger();
        runFx(() -> target.set(indexOfId(list, "task-40")));
        int row = target.get();
        runFx(() -> list.getSelectionModel().select(row));
        awaitFx(() -> selectedId(list).equals("task-40"), "the target row to be selected");

        RichTextArea editor = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(RichTextArea.class).fromAll().findFirst(),
                "the editor");

        // Park the row two below the top edge — on screen, but not the clipped
        // topmost cell. A click in the editor must leave the viewport untouched.
        runFx(() -> flow(list).scrollToTop(row - 2));
        awaitFx(() -> firstVisibleIndex(list) == row - 2, "the viewport to settle");
        awaitFx(() -> lastVisibleIndex(list) > row,
                "the viewport to show rows past the target (list must be tall enough)");
        runFx(() -> Event.fireEvent(editor, mousePress()));
        // A scroll would happen on the click's own pulse; give it a few to be
        // sure nothing moved late.
        for (int i = 0; i < 5; i++) {
            UiTestSupport.sleep();
        }
        assertThat(callFx(() -> firstVisibleIndex(list) == row - 2))
                .as("the viewport stayed where it was")
                .isTrue();

        // Now the same click with the row far off-screen: it must be revealed,
        // and not flush against an edge.
        runFx(() -> flow(list).scrollToTop(0));
        awaitFx(() -> firstVisibleIndex(list) == 0, "the viewport back at the top");
        runFx(() -> Event.fireEvent(editor, mousePress()));
        awaitFx(() -> {
            ListCell<?> cell = cellForId(list, "task-40");
            if (cell == null) {
                return false;
            }
            double top = cell.localToScene(cell.getBoundsInLocal()).getMinY()
                    - list.localToScene(list.getBoundsInLocal()).getMinY();
            double fraction = top / list.getHeight();
            return fraction > 0.15 && fraction < 0.55;
        }, "the revealed row to sit about a third down the viewport");
    }

    private static VirtualFlow<?> flow(ListView<Object> list) {
        return (VirtualFlow<?>) list.lookup(".virtual-flow");
    }

    private static int firstVisibleIndex(ListView<Object> list) {
        IndexedCell<?> cell = flow(list).getFirstVisibleCell();
        return cell == null ? -1 : cell.getIndex();
    }

    private static int lastVisibleIndex(ListView<Object> list) {
        IndexedCell<?> cell = flow(list).getLastVisibleCell();
        return cell == null ? -1 : cell.getIndex();
    }

    /// The realized cell showing a task id, or null when the row is off-screen
    /// (a `VirtualFlow` renders no cell for it).
    private static ListCell<?> cellForId(ListView<Object> list, String id) {
        return FX_ROBOT.selectNodes(ListCell.class).from(list)
                .filter(cell -> cell.getItem() instanceof TaskEntry.Loaded loaded
                        && loaded.id().equals(id))
                .findFirst().orElse(null);
    }

    private static MouseEvent mousePress() {
        return new MouseEvent(MouseEvent.MOUSE_PRESSED, 5, 5, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }

    private static String taskFile(String title) {
        return "---\ntitle: " + title + "\nstatus: active\n---\nnotes\n";
    }

    private static ListView<Object> awaitListView() {
        return UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(v -> (ListView<Object>) v).findFirst(),
                "the task ListView");
    }

    private static int indexOfId(ListView<Object> list, String id) {
        for (int i = 0; i < list.getItems().size(); i++) {
            if (list.getItems().get(i) instanceof TaskEntry.Loaded loaded && loaded.id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static String selectedId(ListView<Object> list) {
        return list.getSelectionModel().getSelectedItem() instanceof TaskEntry.Loaded loaded
                ? loaded.id() : "";
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
