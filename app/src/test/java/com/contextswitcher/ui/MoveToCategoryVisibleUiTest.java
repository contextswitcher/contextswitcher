package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import com.contextswitcher.tasks.TaskEntry;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// After moving a task into a category — the drag'n'drop or the row menu's
/// "Move to category" — the moved task must end up visible and selected
/// (`dsn~task-move-dnd~6`; supersedes the manual-test item "the moved row is
/// selected and scrolled into view"). The category renders below every root
/// task, so a move from the top drops the task off the bottom of the viewport:
/// the behavior is only real if the list scrolls to it, which only a running
/// list can show — hence a TestFX test rather than a checklist item.
// [utest->dsn~task-move-dnd~6]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class MoveToCategoryVisibleUiTest {

    @Test
    void movingATaskIntoAnOffscreenCategorySelectsAndScrollsToIt() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        // A category (a subfolder with a task) far below a screenful of root
        // tasks, and the task to move sitting at the top ("aaa" sorts first).
        Files.createDirectories(dir.resolve("Alpha"));
        Files.writeString(dir.resolve("Alpha/keep.md"), taskFile("keep"));
        for (int i = 0; i < 50; i++) {
            Files.writeString(dir.resolve("task-%02d.md".formatted(i)), taskFile("task-" + i));
        }
        Files.writeString(dir.resolve("aaa-mover.md"), taskFile("aaa-mover"));

        ListView<Object> list = awaitListView();
        awaitFx(() -> indexOfId(list, "aaa-mover") >= 0, "the mover row to load");
        awaitFx(() -> indexOfId(list, "Alpha/keep") >= 0, "the Alpha category to load");

        // Realize the mover's cell so its context menu exists, and grab the
        // "Move to category > Alpha" item — the exact action a user clicks.
        runFx(() -> list.scrollTo(indexOfId(list, "aaa-mover")));
        awaitFx(() -> cellForId(list, "aaa-mover") != null, "the mover cell to realize");
        AtomicReference<MenuItem> alpha = new AtomicReference<>();
        runFx(() -> {
            ContextMenu menu = cellForId(list, "aaa-mover").getContextMenu();
            alpha.set(moveToCategoryItem(menu, "Alpha"));
        });
        assertThat(alpha.get()).as("\"Move to category > Alpha\" menu item").isNotNull();

        // Scroll the mover far off-screen (top of the list; Alpha is below all
        // 50 root tasks), then fire the move. Without the scroll-into-view the
        // moved row stays off the bottom of the viewport.
        runFx(() -> list.scrollTo(0));
        // Precondition guard: the list must actually overflow, or "in viewport"
        // below would be trivially true and the test would not exercise the
        // scroll at all. With the viewport at the top, Alpha (past 51 root
        // rows) must have no realized cell.
        awaitFx(() -> cellForId(list, "Alpha/keep") == null,
                "the Alpha category to be off-screen (list must overflow)");
        runFx(() -> alpha.get().fire());

        awaitFx(() -> selectedId(list).equals("Alpha/aaa-mover"), "the moved task to be selected");
        awaitFx(() -> {
            ListCell<?> cell = cellForId(list, "Alpha/aaa-mover");
            return cell != null && isInViewport(list, cell);
        }, "the moved task to be scrolled into view");
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

    /// Index of a task row by id in the (FX-thread) list contents, or -1.
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

    /// The realized cell currently showing the task id, or null when no cell
    /// is rendered for it (off-screen rows have no cell in a VirtualFlow).
    private static ListCell<?> cellForId(ListView<Object> list, String id) {
        return FX_ROBOT.selectNodes(ListCell.class).from(list)
                .filter(cell -> cell.getItem() instanceof TaskEntry.Loaded loaded
                        && loaded.id().equals(id))
                .findFirst().orElse(null);
    }

    private static MenuItem moveToCategoryItem(ContextMenu menu, String category) {
        for (MenuItem item : menu.getItems()) {
            if (item instanceof Menu submenu && "Move to category".equals(submenu.getText())) {
                for (MenuItem inner : submenu.getItems()) {
                    if (category.equals(inner.getText())) {
                        return inner;
                    }
                }
            }
        }
        return null;
    }

    /// Whether the cell sits within the ListView's viewport (fully, allowing a
    /// 1px rounding slack) — i.e. it was scrolled into view, not merely
    /// realized just off the edge.
    private static boolean isInViewport(ListView<Object> list, ListCell<?> cell) {
        Bounds listBounds = list.localToScene(list.getBoundsInLocal());
        Bounds cellBounds = cell.localToScene(cell.getBoundsInLocal());
        return cellBounds.getMinY() >= listBounds.getMinY() - 1
                && cellBounds.getMaxY() <= listBounds.getMaxY() + 1;
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
