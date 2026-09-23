package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

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
/// hit), so `Add category…` clears the query: the new header shows, and the
/// search field is empty afterwards.
// [utest->dsn~task-folder-grouping-ui~8]
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

        MenuButton add = awaitPresent(
                () -> FX_ROBOT.selectNodes(MenuButton.class).fromAll()
                        .filter(button -> "add-menu".equals(button.getId())).findFirst(),
                "add menu button");
        Platform.runLater(() -> add.getItems().stream()
                .filter(item -> "Add category…".equals(item.getText()))
                .findFirst().orElseThrow()
                .getOnAction().handle(new ActionEvent()));

        TextField nameField = awaitPresent(
                () -> FX_ROBOT.selectNodes(TextField.class)
                        .fetchOptionalFrom(dialogPane()),
                "add-category name field");
        FX_ROBOT.mouse().moveTo(nameField).click();
        FX_ROBOT.keyboard().print("fresh-category").type(KeyCode.ENTER);

        awaitPresent(() -> hasGroupHeader(list, "fresh-category") ? java.util.Optional.of(true)
                : java.util.Optional.empty(), "the new category to show up");
        assertThat(search.getText()).isEmpty();
    }

    private static DialogPane dialogPane() {
        return awaitPresent(
                () -> FX_ROBOT.selectNodes(DialogPane.class, ".dialog-pane").fetchOptional(),
                "dialog pane");
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
