package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javafx.application.Platform;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// While a filter narrows the list, the selection of a task whose file is
/// rewritten under it must survive the rebuild (`dsn~task-folder-grouping-ui~8`)
/// — the running task is exactly the one whose file keeps changing (session id,
/// status, commit), and it used to lose the highlight because the rebuilt row
/// is a different `TaskEntry.Loaded` record for the same task.
// [utest->dsn~task-folder-grouping-ui~8]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class SelectionSurvivesFileRewriteUiTest {

    @Test
    void selectedTaskKeepsTheHighlightWhenItsFileChangesWhileFiltered() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Path file = dir.resolve("alpha.md");
        Files.writeString(file, "---\ntitle: alpha\nstatus: active\n---\nnotes\n");

        ListView<Object> list = awaitListView();
        awaitPresent(() -> indexOf(list, "alpha") >= 0 ? Optional.of(true) : Optional.empty(),
                "the task to load");

        // Narrow the list — only then is the selection preservation gated on
        // the rebuilt rows still holding the task.
        TextField search = searchField();
        FX_ROBOT.mouse().moveTo(search).click();
        FX_ROBOT.keyboard().print("alpha");
        awaitPresent(() -> list.getItems().size() == 1 ? Optional.of(true) : Optional.empty(),
                "the filtered list");

        int row = indexOf(list, "alpha");
        Platform.runLater(() -> list.getSelectionModel().select(row));
        awaitPresent(() -> list.getSelectionModel().getSelectedIndex() == row
                ? Optional.of(true) : Optional.empty(), "the row to be selected");

        // What a running task does to its own file, over and over.
        Files.writeString(file, "---\ntitle: alpha\nstatus: active\n---\nnotes rewritten\n");

        awaitPresent(() -> list.getItems().stream().anyMatch(
                        item -> item instanceof TaskEntry.Loaded loaded
                                && loaded.id().equals("alpha")
                                && loaded.task().notes().contains("rewritten"))
                ? Optional.of(true) : Optional.empty(),
                "the rewritten task to reload");

        assertThat(list.getSelectionModel().getSelectedItem())
                .isInstanceOfSatisfying(TaskEntry.Loaded.class,
                        loaded -> assertThat(loaded.id()).isEqualTo("alpha"));
    }

    private static int indexOf(ListView<Object> list, String id) {
        for (int i = 0; i < list.getItems().size(); i++) {
            if (list.getItems().get(i) instanceof TaskEntry.Loaded loaded && loaded.id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static TextField searchField() {
        return awaitPresent(
                () -> FX_ROBOT.selectNodes(TextField.class).fromAll()
                        .filter(field -> field.getStyleClass().contains("search-inner"))
                        .findFirst(),
                "the find bar's search field");
    }

    @SuppressWarnings("unchecked")
    private static ListView<Object> awaitListView() {
        return awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(v -> (ListView<Object>) v).findFirst(),
                "the task ListView");
    }
}
