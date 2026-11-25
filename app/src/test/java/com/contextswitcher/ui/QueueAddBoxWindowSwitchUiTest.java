package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import com.contextswitcher.tasks.TaskEntry;

import com.sun.javafx.stage.WindowHelper;

import javafx.application.Platform;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The queue's add box grows with its text, and leaving the window while
/// typing in it stores the message but keeps the caret in that message's new
/// card (`dsn~message-queue-ui~28`).
// [utest->dsn~message-queue-ui~28]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class QueueAddBoxWindowSwitchUiTest {

    @Test
    void theAddBoxGrowsWithItsText() throws Exception {
        TextArea addBox = addBoxOf("growbox");
        double before = call(addBox::getHeight);
        Platform.runLater(() -> addBox.setText("line\n".repeat(12)));
        UiTestSupport.awaitPresent(() -> Optional.of(addBox).filter(box -> box.getHeight() > before * 2),
                "the add box to grow with its text");
        Platform.runLater(addBox::clear);
        UiTestSupport.awaitPresent(() -> Optional.of(addBox).filter(box -> box.getHeight() <= before + 0.5),
                "the emptied add box back at its default height");
    }

    @Test
    void leavingTheWindowKeepsTheCaretInTheStoredMessage() throws Exception {
        TextArea addBox = addBoxOf("winswitch");
        Platform.runLater(() -> {
            addBox.requestFocus();
            addBox.setText("half a thought");
            addBox.positionCaret(4);
        });
        UiTestSupport.awaitFocused(addBox, "add box");
        Window window = call(() -> addBox.getScene().getWindow());

        Platform.runLater(() -> WindowHelper.setFocused(window, false));
        TextArea card = UiTestSupport.awaitPresent(
                () -> Optional.ofNullable(window.getScene().getFocusOwner())
                        .filter(owner -> owner instanceof TextArea && owner != addBox)
                        .map(TextArea.class::cast),
                "the caret to move into the stored message's card");
        assertThat((String) call(() -> card.getText())).isEqualTo("half a thought");
        assertThat(call(card::getCaretPosition)).isEqualTo(4);
        assertThat((String) call(() -> addBox.getText())).isEmpty();

        Platform.runLater(() -> WindowHelper.setFocused(window, true));
        UiTestSupport.awaitFocused(card, "card");
    }

    private static TextArea addBoxOf(String id) throws Exception {
        Files.writeString(UiTestSupport.tasksDir.resolve(id + ".md"),
                "---\ntitle: " + id + "\nstatus: active\n---\nnotes\n");
        ListView<?> list = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .filter(view -> indexOf(view, id) >= 0)
                        .<ListView<?>>map(view -> view)
                        .findFirst(),
                "the task list with " + id);
        Platform.runLater(() -> list.getSelectionModel().select(indexOf(list, id)));
        TextArea addBox = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(TextArea.class).fromAll()
                        .filter(area -> area.getPromptText() != null
                                && area.getPromptText().startsWith("Queue a message"))
                        .findFirst(),
                "the add box");
        // Selected task shown: the add box only stores messages for a task.
        UiTestSupport.awaitPresent(() -> Optional.of(list)
                        .filter(view -> view.getSelectionModel().getSelectedIndex() == indexOf(view, id)),
                id + " selected");
        return addBox;
    }

    private static int indexOf(ListView<?> list, String id) {
        return IntStream.range(0, list.getItems().size())
                .filter(i -> list.getItems().get(i) instanceof TaskEntry.Loaded loaded && loaded.id().equals(id))
                .findFirst().orElse(-1);
    }

    private static <T> T call(java.util.concurrent.Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        return result.get();
    }
}
