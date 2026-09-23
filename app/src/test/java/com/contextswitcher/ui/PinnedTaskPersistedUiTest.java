package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;

import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// The row menu's `Pinned` toggle is written to the task file, so the pin
/// survives a restart and reaches the other machines through the synchronised
/// tasks directory (`dsn~pinned-tasks~2`): pinning adds `pinned: true` to the
/// frontmatter and unpinning takes the key out again, the rest of the file
/// untouched.
// [utest->dsn~pinned-tasks~2]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class PinnedTaskPersistedUiTest {

    @Test
    void thePinToggleIsWrittenToTheTaskFile() throws Exception {
        Path file = UiTestSupport.tasksDir.resolve("pin-me.md");
        String original = "---\ntitle: pin me\nstatus: active\n---\nnotes\n";
        Files.writeString(file, original);

        firePinnedItem();
        awaitPresent(() -> read(file).contains("pinned: true") ? Optional.of(true) : Optional.empty(),
                "`pinned: true` in the task file");

        firePinnedItem();
        awaitPresent(() -> read(file).equals(original) ? Optional.of(true) : Optional.empty(),
                "the task file back to its unpinned original");
    }

    /// Fires the "Pinned" item of the task row's context menu — the menu is
    /// rebuilt with the cell on every rebuild, so it is looked up freshly for
    /// each toggle (a stale item would carry the stale pin state).
    private static void firePinnedItem() {
        MenuItem pinned = awaitPresent(PinnedTaskPersistedUiTest::pinnedItem, "the row's Pinned item");
        Platform.runLater(() -> pinned.getOnAction().handle(new ActionEvent()));
    }

    private static Optional<MenuItem> pinnedItem() {
        return FX_ROBOT.selectNodes(ListCell.class).fromAll()
                .filter(cell -> cell.getItem() instanceof TaskEntry.Loaded loaded
                        && loaded.id().equals("pin-me"))
                .filter(cell -> cell.getContextMenu() != null)
                .flatMap(cell -> cell.getContextMenu().getItems().stream())
                .filter(item -> "Pinned".equals(item.getText()))
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
