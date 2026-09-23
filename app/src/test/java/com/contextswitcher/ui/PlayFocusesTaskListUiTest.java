package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import com.contextswitcher.tasks.TaskEntry;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;

import jfx.incubator.scene.control.richtext.RichTextArea;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// Pressing a row's play toggle puts the keyboard on the task list
/// (`dsn~task-row-hover-actions~7`), so the arrow keys move on from the row
/// just switched to. The button is **fired**, not robot-clicked: a mouse press
/// on the cell focuses the ListView by itself (JavaFX cell behavior), so a
/// clicking test would pass without the focus request that is the point here.
// [utest->dsn~task-row-hover-actions~7]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class PlayFocusesTaskListUiTest {

    @Test
    void pressingPlayMovesTheKeyboardToTheTaskList() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        // No tmux/remote: the switch reports "nothing configured" and reaches
        // no ssh — where the keyboard ends up is all this test is after.
        // Suspended, so the toggle is play (on an active task it pauses).
        Files.writeString(dir.resolve("alpha.md"),
                "---\ntitle: alpha\nstatus: suspended\n---\nnotes\n");

        ListView<Object> list = awaitPresent(() -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(),
                "the task ListView");
        awaitPresent(() -> list.getItems().stream()
                .anyMatch(row -> row instanceof TaskEntry.Loaded loaded
                        && loaded.id().equals("alpha"))
                ? Optional.of(true) : Optional.empty(), "the alpha row");

        // Park the keyboard away from the list — in the editor lane, where it
        // sits after typing a task's notes.
        Node editor = awaitPresent(() -> FX_ROBOT.selectNodes(RichTextArea.class).fromAll()
                .<Node>map(area -> area).findFirst(), "the editor");
        FX_ROBOT.mouse().moveTo(editor).click();
        // `Node.isFocused` stays false while no window manager focuses the
        // stage (the headless run), so the scene's focus owner is what counts.
        // By type, not by identity: the click re-selects the previewed task
        // (`dsn~pane-click-selects-task~1`), which can rebuild the editor lane.
        awaitPresent(() -> focusOwner(list) instanceof RichTextArea
                ? Optional.of(true) : Optional.empty(), "the editor to hold the keyboard focus");

        Button play = awaitPresent(() -> FX_ROBOT.selectNodes(Node.class).fromAll()
                .filter(node -> "task-toggle-button".equals(node.getId()))
                .map(Button.class::cast).findFirst(), "the row's play button");
        runFx(play::fire);

        awaitPresent(() -> focusOwner(list) == list ? Optional.of(true) : Optional.empty(),
                "the task list to hold the keyboard focus");
        assertThat(focusOwner(list)).isSameAs(list);
    }

    private static @Nullable Node focusOwner(Node node) {
        return node.getScene() == null ? null : node.getScene().getFocusOwner();
    }

    private static void runFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                done.countDown();
            }
        });
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
