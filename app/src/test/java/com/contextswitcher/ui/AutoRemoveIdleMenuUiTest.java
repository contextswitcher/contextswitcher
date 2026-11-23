package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;

import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// The row menu's *Remove after 24 hours of inactivity* writes and removes
/// the Add-task checkbox's `autoRemoveIdle: true` (`dsn~idle-task-removal~3`).
// [utest->dsn~idle-task-removal~3]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class AutoRemoveIdleMenuUiTest {

    private static final String LABEL = "Remove after 24 hours of inactivity";

    @Test
    void theMenuItemTogglesTheFlagInTheTaskFile() throws Exception {
        Path file = UiTestSupport.tasksDir.resolve("auto-remove-me.md");
        String original = "---\ntitle: auto remove me\nstatus: active\nclaude:\n  cwd: /w\n---\nnotes\n";
        Files.writeString(file, original);

        fireItem();
        awaitPresent(() -> read(file).contains("autoRemoveIdle: true") ? Optional.of(true) : Optional.empty(),
                "`autoRemoveIdle: true` in the task file");

        fireItem();
        awaitPresent(() -> read(file).equals(original) ? Optional.of(true) : Optional.empty(),
                "the task file back to its original");
    }

    private static void fireItem() {
        MenuItem item = awaitPresent(AutoRemoveIdleMenuUiTest::item, "the row's " + LABEL + " item");
        Platform.runLater(() -> item.getOnAction().handle(new ActionEvent()));
    }

    private static Optional<MenuItem> item() {
        return FX_ROBOT.selectNodes(ListCell.class).fromAll()
                .filter(cell -> cell.getItem() instanceof TaskEntry.Loaded loaded
                        && loaded.id().equals("auto-remove-me"))
                .filter(cell -> cell.getContextMenu() != null)
                .flatMap(cell -> cell.getContextMenu().getItems().stream())
                .filter(item -> item instanceof CheckMenuItem && LABEL.equals(item.getText()))
                .findFirst();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file).replace("\r\n", "\n");
        } catch (java.io.IOException e) {
            return "";
        }
    }
}
