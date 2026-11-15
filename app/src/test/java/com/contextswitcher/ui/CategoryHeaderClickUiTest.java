package com.contextswitcher.ui;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.contextswitcher.Main;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// A real mouse click on one category header after another opens each
/// category's config in the editor lane (`dsn~group-config-create~9`).
///
/// Field report 2026-11-15: pressing another category's header selects it,
/// the selection marks it the current category, and that `ListView.refresh()`
/// recreated the cells between press and release — the click went to the
/// list, not to a cell, and the Configuration / Switch actions panes kept the
/// previous file.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~group-config-create~9]
@Tag("ui")
@TestFxApplication(CategoryHeaderClickUiTest.TestApp.class)
class CategoryHeaderClickUiTest {

    /// [Main] on a fresh config dir holding two configured categories, a task each.
    public static class TestApp extends Main {

        @Override
        public void start(Stage stage) throws Exception {
            Path configDir = Files.createTempDirectory("cs-uitest");
            Path tasksDir = configDir.resolve("tasks");
            for (String category : new String[] {"acat", "bcat"}) {
                Files.createDirectories(tasksDir.resolve(category));
                Files.writeString(tasksDir.resolve(category).resolve("CONTEXTSWITCHER.md"), """
                        ---
                        repo: https://github.com/example/%s
                        ---
                        """.formatted(category));
                // A task per category: an empty list shows the setup hint
                // over the rows instead.
                Files.writeString(tasksDir.resolve(category).resolve("work.md"),
                        "---\ntitle: work in %s\nstatus: active\n---\nnotes\n".formatted(category));
            }
            Files.writeString(tasksDir.resolve(".gitkeep"), "");
            Files.writeString(configDir.resolve("settings.yaml"), """
                    tasksDir: %s
                    wsPort: %d
                    wsToken: uitest
                    remotes: []
                    """.formatted(tasksDir, freePort()));
            System.setProperty("contextswitcher.configDir", configDir.toString());
            super.start(stage);
        }
    }

    @Test
    void clickingOneHeaderAfterAnotherOpensEachCategorysConfig() {
        clickHeader("acat");
        await(() -> labelStartingWith("acat/CONTEXTSWITCHER.md"), "acat's config in the editor");

        clickHeader("bcat");
        await(() -> labelStartingWith("bcat/CONTEXTSWITCHER.md"), "bcat's config in the editor");

        clickHeader("acat");
        await(() -> labelStartingWith("acat/CONTEXTSWITCHER.md"), "acat's config in the editor again");
    }

    private static void clickHeader(String name) {
        ListCell<?> cell = await(() -> FX_ROBOT.selectNodes(ListCell.class).fromAll()
                .filter(c -> c.getItem() instanceof TaskListCell.GroupHeader header
                        && header.name().equals(name))
                .<ListCell<?>>map(c -> c)
                .findFirst(), "the header of " + name);
        // Just right of the name, at the lower edge of the header: clear of
        // the arrow and the name (left), which scope the search, and of the
        // icon buttons, which handle their own presses.
        Node label = cell.lookup("#group-name-label");
        Bounds nameBounds = label.localToScreen(label.getBoundsInLocal());
        Bounds cellBounds = cell.localToScreen(cell.getBoundsInLocal());
        FX_ROBOT.mouse().moveTo(new Point2D(nameBounds.getMaxX() + 2, cellBounds.getMaxY() - 3));
        // A human's press selects the row (the headless platform marks every
        // press a popup trigger, which ListView does not select on — so the
        // test selects it), and press and release lie ~100 ms apart, so the
        // layout pulse that recreates the refreshed cells falls in between.
        // The robot's plain click() does neither and passed with the bug.
        FX_ROBOT.mouse().press(MouseButton.PRIMARY);
        javafx.application.Platform.runLater(() -> cell.getListView().getSelectionModel().select(cell.getIndex()));
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        FX_ROBOT.mouse().release(MouseButton.PRIMARY);
    }

    private static Optional<String> labelStartingWith(String prefix) {
        return FX_ROBOT.selectNodes(Labeled.class).fromAll()
                .map(Labeled::getText)
                .filter(text -> text != null && text.startsWith(prefix))
                .findFirst();
    }

    private static <T> T await(java.util.function.Supplier<Optional<T>> query, String what) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<T> hit = query.get();
            if (hit.isPresent()) {
                return hit.get();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Never saw " + what);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
