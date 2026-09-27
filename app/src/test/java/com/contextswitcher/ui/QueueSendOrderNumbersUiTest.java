package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.contextswitcher.tasks.TaskEntry;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The send-order numbers on the queue cards' ≡ handles
/// (`dsn~message-queue-ui~26`): only a card armed for the delayed send
/// (`dsn~message-queue-delayed-send~4`) carries one, and it counts within the
/// armed cards, not within the whole stack — an unarmed card is never sent on
/// its own, so a number on it would promise a turn it never gets. Arming a
/// card moves it below the last armed one, so it gets the next number.
///
/// The task needs a remote for the clock toggle to be enabled; it is bogus, so
/// the status poll never reports the window idle and nothing is actually
/// delivered while the test looks at the handles.
// [utest->dsn~message-queue-ui~26]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class QueueSendOrderNumbersUiTest {

    private static final String TASK = "numbered";

    @Test
    void onlyArmedCardsAreNumbered() throws Exception {
        queue("first msg");
        queue("second msg");
        // Nothing armed yet: both handles are bare.
        assertThat(handleOf("first msg")).isEqualTo("≡");
        assertThat(handleOf("second msg")).isEqualTo("≡");

        // Arming the *second* card makes it ➊ — it is the first to go out,
        // even though the unarmed card above it comes first in the stack.
        arm("second msg");
        assertThat(handleOf("first msg")).isEqualTo("≡");
        assertThat(handleOf("second msg")).isEqualTo("≡ ➊");

        // Arming the card above an armed one moves it directly below that
        // card, so it takes the next number instead of ➊.
        arm("first msg");
        assertThat(handleOf("second msg")).isEqualTo("≡ ➊");
        assertThat(handleOf("first msg")).isEqualTo("≡ ➋");
        String saved = Files.readString(
                UiTestSupport.tasksDir.resolve(".queues").resolve(TASK + ".yaml"));
        assertThat(saved.indexOf("second msg")).isLessThan(saved.indexOf("first msg"));

        // Disarming the moved card renumbers the rest right away.
        arm("first msg");
        assertThat(handleOf("first msg")).isEqualTo("≡");
        assertThat(handleOf("second msg")).isEqualTo("≡ ➊");
    }

    /// Ticks (or unticks) the clock toggle of the card holding `text`.
    private static void arm(String text) throws Exception {
        ToggleButton clock = (ToggleButton) leftColumn(text).getChildren().get(2);
        assertThat(clock.isDisabled()).isFalse();
        Platform.runLater(clock::fire);
        fxSync();
    }

    /// The handle label of the card holding `text`. Re-looked-up every time:
    /// arming rebuilds the cards, so a kept reference is a stale node.
    private static String handleOf(String text) throws Exception {
        return ((Label) leftColumn(text).getChildren().get(0)).getText();
    }

    /// The card row's left column (handle, send, clock, qodo link).
    private static VBox leftColumn(String text) throws Exception {
        TextArea area = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(TextArea.class).fromAll()
                        .filter(box -> box.isEditable() && text.equals(box.getText()))
                        .findFirst(),
                "the card for \"" + text + "\"");
        // area -> areaBox (VBox with the resize grip) -> row (HBox)
        Node row = area.getParent().getParent();
        return (VBox) ((HBox) row).getChildren().get(0);
    }

    /// Queues one message through the add box's triple-Enter commit.
    private static void queue(String text) throws Exception {
        selectAndFocusAddBox();
        FX_ROBOT.keyboard().print(text).type(KeyCode.ENTER, KeyCode.ENTER, KeyCode.ENTER);
        awaitQueued(text);
    }

    private static TextArea selectAndFocusAddBox() throws Exception {
        Files.writeString(UiTestSupport.tasksDir.resolve(TASK + ".md"), """
                ---
                title: numbered
                status: active
                remote: nosuchhost.invalid
                tmux:
                  session: "0"
                  window: "@1"
                ---
                notes
                """);
        ListView<Object> list = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(),
                "the task ListView");
        UiTestSupport.awaitPresent(() -> {
            int index = indexOf(list);
            if (index < 0) {
                return Optional.empty();
            }
            Platform.runLater(() -> list.getSelectionModel().select(index));
            return Optional.of(index);
        }, "the " + TASK + " row");
        TextArea addBox = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(TextArea.class).fromAll()
                        .filter(area -> area.getPromptText() != null
                                && area.getPromptText().startsWith("Queue a message"))
                        .findFirst(),
                "the queue add box");
        UiTestSupport.awaitFocused(addBox, "add box");
        return addBox;
    }

    /// Waits until the queue file holds `marker` — the commit runs through the
    /// FX thread, so poll rather than read once.
    private static void awaitQueued(String marker) throws Exception {
        Path file = UiTestSupport.tasksDir.resolve(".queues").resolve(TASK + ".yaml");
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(file) && Files.readString(file).contains(marker)) {
                return;
            }
            UiTestSupport.sleep();
        }
        throw new AssertionError("\"" + marker + "\" never reached the queue file");
    }

    private static void fxSync() {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(done::countDown);
        try {
            if (!done.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("The FX thread did not drain");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int indexOf(ListView<Object> list) {
        for (int i = 0; i < list.getItems().size(); i++) {
            if (list.getItems().get(i) instanceof TaskEntry.Loaded loaded && loaded.id().equals(TASK)) {
                return i;
            }
        }
        return -1;
    }
}
