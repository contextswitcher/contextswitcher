package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.IntStream;

import com.contextswitcher.tasks.TaskEntry;

import javafx.application.Platform;

import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// Clicking the Queue tab's header puts the caret in the "Queue a message" add
/// box (`dsn~shell-layout~2`).
@Tag("ui")
@TestFxApplication(QueueTabFocusUiTest.ShellFxApp.class)
class QueueTabFocusUiTest {

    public static class ShellFxApp extends UiTestSupport.TestApp {

        @Override
        protected void seedTasks(Path tasksDir) throws Exception {
            Files.writeString(tasksDir.resolve("tabfocus.md"), """
                    ---
                    title: tabfocus
                    status: active
                    ---
                    notes
                    """);
        }
    }

    @Test
    void clickingTheQueueTabFocusesTheAddBox() {
        ListView<?> list = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .filter(view -> view.getItems().stream().anyMatch(item ->
                                item instanceof TaskEntry.Loaded loaded && loaded.id().equals("tabfocus")))
                        .<ListView<?>>map(view -> view)
                        .findFirst(),
                "the task list with the seeded task");
        int index = IntStream.range(0, list.getItems().size())
                .filter(i -> list.getItems().get(i) instanceof TaskEntry.Loaded loaded
                        && loaded.id().equals("tabfocus"))
                .findFirst().orElseThrow();
        Platform.runLater(() -> list.getSelectionModel().select(index));
        Node header = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(Labeled.class).fromAll()
                        .filter(label -> label.getStyleClass().contains("tab-label")
                                && "Queue".equals(label.getText()))
                        .<Node>map(label -> label)
                        .findFirst(),
                "the Queue tab header");
        TextArea addBox = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(TextArea.class).fromAll()
                        .filter(area -> area.getPromptText() != null
                                && area.getPromptText().startsWith("Queue a message"))
                        .findFirst(),
                "the add box");
        FX_ROBOT.mouse().moveTo(header).click();
        UiTestSupport.awaitPresent(() -> Optional.of(addBox).filter(TextArea::isFocused),
                "the focused add box");
    }
}
