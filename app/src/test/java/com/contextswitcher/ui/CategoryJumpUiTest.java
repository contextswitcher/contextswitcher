package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// Ctrl+J, a few letters of a category's name and Enter select that
/// category's header in the task list.
// [utest->dsn~category-jump~1]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class CategoryJumpUiTest {

    @Test
    void ctrlJTypingAndEnterSelectTheCategory() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("gamma"));
        Files.createDirectories(dir.resolve("delta"));
        Files.writeString(dir.resolve("gamma").resolve("one.md"), "---\ntitle: one\nstatus: active\n---\n");
        Files.writeString(dir.resolve("delta").resolve("two.md"), "---\ntitle: two\nstatus: active\n---\n");

        ListView<Object> list = awaitPresent(() -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(), "the task ListView");
        awaitPresent(() -> list.getItems().stream()
                .anyMatch(row -> row instanceof TaskEntry.Loaded loaded && loaded.id().equals("delta/two"))
                ? Optional.of(true) : Optional.empty(), "the tasks to load");

        FX_ROBOT.keyboard().press(KeyCode.CONTROL, KeyCode.J).release(KeyCode.J, KeyCode.CONTROL);
        TextField field = awaitPresent(() -> FX_ROBOT.selectNodes(TextField.class).fromAll()
                .filter(node -> "category-jump-field".equals(node.getId()) && node.isFocused())
                .findFirst(), "the jump field to take the keyboard");
        // The robot's keyboard refuses to type with two windows in focus (the
        // main stage and the popup), so type and press Enter on the field itself.
        Platform.runLater(() -> {
            field.setText("elt");
            field.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
        });

        awaitPresent(() -> list.getSelectionModel().getSelectedItem() instanceof TaskListCell.GroupHeader header
                && header.name().equals("delta") ? Optional.of(true) : Optional.empty(),
                "the delta header to be selected");
    }
}
