package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javafx.application.Platform;
import javafx.scene.control.ListView;
import javafx.scene.layout.Background;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The category header stays pinned to the top of the task list while its own
/// row is scrolled off (`dsn~sticky-group-header~1`). Only a running,
/// overflowing list can show this — the pinned cell exists solely because the
/// `VirtualFlow` scrolled its real row out of the viewport — so it is a TestFX
/// test rather than a checklist item.
// [utest->dsn~sticky-group-header~1]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class StickyHeaderUiTest {

    @Test
    void theCategoryHeaderStaysPinnedWhileItsOwnRowIsScrolledOff() throws Exception {
        // One category holding more tasks than fit, so scrolling into it pushes
        // its own header row out of the viewport.
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("Alpha"));
        for (int i = 0; i < 60; i++) {
            Files.writeString(dir.resolve("Alpha/task-%02d.md".formatted(i)),
                    "---\ntitle: task-" + i + "\nstatus: active\n---\nnotes\n");
        }

        ListView<Object> list = awaitListView();
        awaitFx(() -> list.getItems().size() > 50, "the category's tasks to load");

        // At the top the header row itself is on screen, so nothing is pinned.
        runFx(() -> list.scrollTo(0));
        awaitFx(() -> !pinnedHeaderVisible(), "no pinned header while the list is at the top");

        runFx(() -> list.scrollTo(list.getItems().size() - 1));
        awaitFx(StickyHeaderUiTest::pinnedHeaderVisible, "the pinned category header");
        assertThat(callFx(() -> pinnedHeader().getItem()
                instanceof TaskListCell.GroupHeader header && "Alpha".equals(header.name())))
                .as("the pinned cell renders the Alpha category header")
                .isTrue();
        // It floats over moving rows, so a see-through background is a bug, not
        // a cosmetic detail: main.css scopes the section background to
        // `.task-list .list-cell`, which the pinned cell only matches because
        // the overlay StackPane carries the class as well.
        assertThat(callFx(StickyHeaderUiTest::pinnedHeaderIsOpaque))
                .as("the pinned header paints an opaque section background")
                .isTrue();

        // The selected task's category is highlighted with a colour AtlantaFX
        // only defines inside `.list-view`; the pinned header must get it too,
        // and in the same shape as the header row it stands in for.
        // [utest->dsn~current-category-highlight~1]
        // The last row is the category's "Add task…" row, which is no task.
        runFx(() -> list.getSelectionModel().select(list.getItems().size() - 2));
        awaitFx(() -> pinnedHeader().getStyleClass().contains("group-header-current"),
                "the pinned header to show the current category");
        assertThat(callFx(StickyHeaderUiTest::pinnedHeaderIsOpaque))
                .as("the pinned current-category header paints an opaque background")
                .isTrue();
        double[] heights = {0, 0};
        runFx(() -> heights[0] = pinnedHeader().getHeight());
        runFx(() -> list.scrollTo(0));
        awaitFx(() -> !pinnedHeaderVisible(), "the header row itself back on screen");
        runFx(() -> heights[1] = list.lookupAll(".group-header-cell").stream()
                .filter(node -> node != pinnedHeader())
                .mapToDouble(node -> node.getBoundsInLocal().getHeight()).findFirst().orElse(-1));
        assertThat(heights[0]).as("the pinned header is as tall as the header row")
                .isEqualTo(heights[1]);
    }

    private static boolean pinnedHeaderIsOpaque() {
        Background background = pinnedHeader().getBackground();
        return background != null && background.getFills().stream()
                .anyMatch(fill -> fill.getFill() instanceof Color color && color.getOpacity() == 1);
    }

    /// The pinned cell is the one `TaskListCell` sitting directly in the
    /// overlay `StackPane` instead of inside the list's `VirtualFlow`.
    private static TaskListCell pinnedHeader() {
        return FX_ROBOT.selectNodes(TaskListCell.class).fromAll()
                .filter(cell -> cell.getParent() instanceof StackPane)
                .findFirst().orElse(null);
    }

    private static boolean pinnedHeaderVisible() {
        TaskListCell cell = pinnedHeader();
        return cell != null && cell.isVisible();
    }

    private static ListView<Object> awaitListView() {
        return UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(),
                "the task ListView");
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
