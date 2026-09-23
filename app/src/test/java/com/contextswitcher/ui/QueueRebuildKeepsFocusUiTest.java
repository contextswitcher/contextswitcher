package com.contextswitcher.ui;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import com.contextswitcher.Main;
import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// A queue rebuild while typing in a **card** must keep the keyboard in that
/// card (`dsn~message-queue-ui~26`): rebuilds used to create the cards' text
/// areas anew, so the one that had the focus left the scene for good and the
/// next pulse cleared the focus — the next Tab then started from the toolbar.
/// Field report 2026-09-16: "I was just in the queued message, then the
/// cursor got dragged away … I pressed Tab and then that no-browser thing got
/// focussed". The area is reused now, like the add box always was — one node
/// put back into the scene within the same pulse keeps focus and caret, and
/// a half-typed edit with them.
///
/// The rebuild here is another card's clock toggle, fired programmatically
/// (it is not focus-traversable, so nothing but the redraw touches the
/// focus) — the same redraw a review-comment sync or a sent message does.
// [utest->dsn~message-queue-ui~26]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class QueueRebuildKeepsFocusUiTest {

    private static final String TASK = "refocus";
    private static final String TYPED = "half a sentence";
    private static final String OTHER = "another message";

    @Test
    void aRebuildKeepsFocusAndCaretInTheCard() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve(".queues"));
        Files.writeString(dir.resolve(".queues").resolve(TASK + ".yaml"),
                "- " + TYPED + "\n- " + OTHER + "\n");
        // A remote and a tmux window, so the clock toggle is enabled.
        Files.writeString(dir.resolve(TASK + ".md"), """
                ---
                title: refocus
                status: active
                remote: nosuchhost.invalid
                tmux:
                  session: "0"
                  window: "@1"
                ---
                notes
                """);
        awaitRow(TASK);
        runFx(() -> mainWindow().selectTask(TASK));
        awaitSelected(TASK);

        TextArea box = card(TYPED);
        runFx(() -> {
            box.requestFocus();
            box.positionCaret(4);
            box.insertText(4, "!");   // an uncommitted edit
        });
        UiTestSupport.sleep();
        assertThat(callFx(box::isFocused)).isTrue();

        // Arming the other card redraws every card: its handle gets a number.
        ToggleButton clock = (ToggleButton) leftColumn(OTHER).getChildren().get(2);
        assertThat(clock.isDisabled()).isFalse();
        Platform.runLater(clock::fire);
        awaitPresent(() -> callFx(() -> !handleOf(OTHER).equals("≡"))
                ? Optional.of(true) : Optional.empty(), "the cards rebuilt");
        UiTestSupport.sleep();

        // The same area is back in the stack, holding the keyboard, caret and edit.
        assertThat(callFx(() -> card(EDITED) == box)).as("the card area is reused").isTrue();
        assertThat(callFx(() -> box.getScene().getFocusOwner() == box))
                .as("the card kept the focus").isTrue();
        assertThat(callFx(() -> box.getCaretPosition() == 5))
                .as("the caret survived the rebuild").isTrue();
    }

    private static final String EDITED = "half! a sentence";

    private static String handleOf(String text) {
        return ((Label) leftColumn(text).getChildren().get(0)).getText();
    }

    /// The card holding `text` — re-looked-up: a rebuild replaces it.
    private static TextArea card(String text) {
        return FX_ROBOT.selectNodes(TextArea.class).fromAll()
                .filter(area -> area.isEditable() && text.equals(area.getText()))
                .findFirst().orElseThrow();
    }

    /// The card row's left column (handle, send, clock, qodo link).
    private static VBox leftColumn(String text) {
        // area -> areaBox (VBox with the resize grip) -> row (HBox)
        Node row = card(text).getParent().getParent();
        return (VBox) ((HBox) row).getChildren().get(0);
    }

    private static void awaitRow(String id) {
        awaitPresent(() -> callFx(() -> awaitListView().getItems().stream().anyMatch(row ->
                row instanceof TaskEntry.Loaded loaded && loaded.id().equals(id)))
                        ? Optional.of(true) : Optional.empty(),
                "the " + id + " row");
    }

    private static void awaitSelected(String id) {
        awaitPresent(() -> callFx(() -> awaitListView().getSelectionModel().getSelectedItem()
                instanceof TaskEntry.Loaded loaded && loaded.id().equals(id))
                ? Optional.of(true) : Optional.empty(), "selected task " + id);
    }

    private static ListView<Object> awaitListView() {
        return UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(),
                "the task ListView");
    }

    private static MainWindow mainWindow() {
        try {
            Field field = Main.class.getDeclaredField("window");
            field.setAccessible(true);
            return (MainWindow) field.get(UiTestSupport.app);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void runFx(Runnable action) {
        callFx(() -> {
            action.run();
            return false;
        });
    }

    private static boolean callFx(BooleanSupplier body) {
        if (Platform.isFxApplicationThread()) {
            return body.getAsBoolean();
        }
        boolean[] result = {false};
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result[0] = body.getAsBoolean();
            } finally {
                done.countDown();
            }
        });
        try {
            done.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }
}
