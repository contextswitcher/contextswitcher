package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javafx.application.Platform;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// Selecting a task marks its category header with the selection colours, so
/// the highlighted row names the context it sits in; selecting a task of
/// another category moves the mark along.
// [utest->dsn~current-category-highlight~1]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class CurrentCategoryHighlightUiTest {

    private static final String CURRENT = "group-header-current";

    @Test
    void theSelectedTasksCategoryIsMarked() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("alpha"));
        Files.createDirectories(dir.resolve("beta"));
        write(dir.resolve("alpha").resolve("parser.md"), "parser");
        write(dir.resolve("beta").resolve("renamer.md"), "renamer");

        ListView<Object> list = awaitListView();
        awaitPresent(() -> taskRow(list, "beta/renamer"), "the category tasks to load");

        select(list, awaitPresent(() -> taskRow(list, "alpha/parser"), "the alpha task row"));
        awaitPresent(() -> markedHeader().filter("alpha"::equals), "alpha to be marked");

        select(list, awaitPresent(() -> taskRow(list, "beta/renamer"), "the beta task row"));
        awaitPresent(() -> markedHeader().filter("beta"::equals), "the mark to move to beta");
    }

    private static void write(Path file, String title) throws Exception {
        Files.writeString(file, "---\ntitle: %s\nstatus: active\n---\nnotes\n".formatted(title));
    }

    private static void select(ListView<Object> list, int row) {
        Platform.runLater(() -> list.getSelectionModel().select(row));
    }

    /// The name of the category header cell currently carrying the mark, if any.
    private static Optional<String> markedHeader() {
        return FX_ROBOT.selectNodes(ListCell.class).fromAll()
                .filter(cell -> cell.getStyleClass().contains(CURRENT))
                .filter(cell -> cell.getItem() instanceof TaskListCell.GroupHeader)
                .map(cell -> ((TaskListCell.GroupHeader) cell.getItem()).name())
                .findFirst();
    }

    private static Optional<Integer> taskRow(ListView<Object> list, String id) {
        for (int i = 0; i < list.getItems().size(); i++) {
            if (list.getItems().get(i) instanceof TaskEntry.Loaded loaded
                    && loaded.id().equals(id)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private static ListView<Object> awaitListView() {
        return awaitPresent(() -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(),
                "the task ListView");
    }
}
