package com.contextswitcher.ui;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.application.Platform;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import com.contextswitcher.Main;
import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// A category created while the find bar narrows the list must show up in the
/// left task list right away — not only once the search is cleared or the app
/// restarted (`dsn~task-folder-grouping-ui~8`). A freshly created folder has no
/// task yet, and a typed query hides task-less categories (they can never be a
/// hit), so creating a category (`Add category…`, the setup wizard's last
/// page) clears the query: the new header shows, and the search field is
/// empty afterwards. The config names the desktop the wizard asked for.
// [utest->dsn~task-folder-grouping-ui~8]
// [utest->dsn~setup-wizard~13]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class NewCategoryVisibleWhileFilteredUiTest {

    @Test
    void categoryCreatedWhileSearchingAppearsImmediately() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.writeString(dir.resolve("keep.md"),
                "---\ntitle: keep\nstatus: active\n---\nnotes\n");

        ListView<Object> list = awaitListView();
        awaitPresent(() -> hasTask(list, "keep") ? java.util.Optional.of(true)
                : java.util.Optional.empty(), "the root task to load");

        // Narrow the view: a query matching nothing hides "keep" but must not
        // hide a category that gets created while the query is still active.
        TextField search = searchField();
        FX_ROBOT.mouse().moveTo(search).click();
        FX_ROBOT.keyboard().print("no-such-task-xyz");

        Path folder = Files.createTempDirectory("cs-fresh");
        Platform.runLater(() -> mainWindow().createCategoryFromFolder("fresh-category",
                folder.toString(), null, MainWindow.lastMode(), null, false, "Work"));

        awaitPresent(() -> hasGroupHeader(list, "fresh-category") ? java.util.Optional.of(true)
                : java.util.Optional.empty(), "the new category to show up");
        assertThat(search.getText()).isEmpty();
        assertThat(Files.readString(dir.resolve("fresh-category").resolve("CONTEXTSWITCHER.md")))
                .contains("desktop: Work");
    }

    private static MainWindow mainWindow() {
        try {
            Field field = Main.class.getDeclaredField("window");
            field.setAccessible(true);
            return (MainWindow) field.get(UiTestSupport.app);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static TextField searchField() {
        return awaitPresent(
                () -> FX_ROBOT.selectNodes(TextField.class).fromAll()
                        .filter(field -> field.getStyleClass().contains("search-inner"))
                        .findFirst(),
                "the find bar's search field");
    }

    private static ListView<Object> awaitListView() {
        return UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(v -> (ListView<Object>) v).findFirst(),
                "the task ListView");
    }

    private static boolean hasGroupHeader(ListView<Object> list, String name) {
        return list.getItems().stream()
                .anyMatch(row -> row instanceof TaskListCell.GroupHeader header
                        && header.name().equals(name));
    }

    private static boolean hasTask(ListView<Object> list, String id) {
        return list.getItems().stream()
                .anyMatch(row -> row instanceof TaskEntry.Loaded loaded && loaded.id().equals(id));
    }
}
