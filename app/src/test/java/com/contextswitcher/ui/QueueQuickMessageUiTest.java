package com.contextswitcher.ui;

import java.nio.file.Files;
import java.util.Optional;

import com.contextswitcher.tasks.TaskEntry;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The quick message buttons below the last-sent box
/// (`dsn~quick-message-buttons~7`): a fresh config offers `Go`, and the button
/// is dead without a selection and, once a task is shown, appends its text to
/// the add box instead of sending it.
///
/// The `…` editor adds and removes messages: its list on the left, the
/// selected message's text on the right.
///
/// A row too long for the pane pages with `‹` and `›`.
///
/// The task's remote is bogus, so nothing is ever actually delivered.
// [utest->dsn~quick-message-buttons~7]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class QueueQuickMessageUiTest {

    private static final String TASK = "quick";

    @Test
    void goButtonFollowsTheShownTask() throws Exception {
        Button go = UiTestSupport.awaitButton("Go");
        // No task selected yet: there is nothing to send to.
        assertThat(go.isDisabled()).isTrue();
        // The `…` that opens the editor sits right behind it.
        assertThat(UiTestSupport.findButton("…")).isPresent();
        // All messages fit, so there is nothing to page through.
        assertThat(UiTestSupport.findButton("‹").map(Button::isDisabled)).contains(true);
        assertThat(UiTestSupport.findButton("›").map(Button::isDisabled)).contains(true);

        selectTaskWithTmux();
        Button enabled = UiTestSupport.awaitPresent(
                () -> UiTestSupport.findButton("Go").filter(button -> !button.isDisabled()),
                "the enabled Go button");

        // A click only fills the add box — appended, nothing sent.
        TextArea addBox = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(TextArea.class).fromAll()
                        .filter(area -> area.getPromptText() != null
                                && area.getPromptText().startsWith("Queue a message"))
                        .findFirst(),
                "the add box");
        Platform.runLater(() -> addBox.setText("see url"));
        Platform.runLater(enabled::fire);
        UiTestSupport.awaitPresent(
                () -> Optional.of(addBox.getText()).filter("see url\n\nGo"::equals),
                "Go appended to the add box");
        // Ready to type on: focused, caret behind the appended text.
        UiTestSupport.awaitPresent(
                () -> Optional.of(addBox).filter(TextArea::isFocused)
                        .filter(area -> area.getCaretPosition() == area.getLength()),
                "the focused add box with the caret at its end");
        Platform.runLater(addBox::clear);
    }

    @Test
    void theEditorAddsAndRemovesMessages() {
        openEditor();
        ListView<String> list = quickList();
        assertThat(list.getItems()).containsExactly("Go");
        UiTestSupport.clickButton("Add");
        type("Ship it");
        UiTestSupport.clickButton("Save");
        UiTestSupport.awaitButton("Ship it");

        // And back out again, so the row is as the other test expects it.
        openEditor();
        ListView<String> reopened = quickList();
        assertThat(reopened.getItems()).containsExactly("Go", "Ship it");
        select(reopened, "Ship it");
        UiTestSupport.clickButton("Remove");
        UiTestSupport.clickButton("Save");
        UiTestSupport.awaitPresent(
                () -> UiTestSupport.findButton("Ship it").isPresent()
                        ? Optional.empty() : Optional.of(true),
                "the removed Ship it button to be gone");
        assertThat(UiTestSupport.findButton("Go")).isPresent();
    }

    @Test
    void anOverflowingRowPages() {
        openEditor();
        ListView<String> list = quickList();
        Platform.runLater(() -> {
            for (int i = 0; i < 40; i++) {
                list.getItems().add("Overflowing message " + i);
            }
        });
        UiTestSupport.awaitPresent(
                () -> Optional.of(list.getItems().size()).filter(size -> size == 41), "the added messages");
        UiTestSupport.clickButton("Save");
        Button next = UiTestSupport.awaitPresent(
                () -> UiTestSupport.findButton("›").filter(button -> !button.isDisabled()),
                "the enabled › button");
        assertThat(UiTestSupport.findButton("‹").map(Button::isDisabled)).contains(true);
        Platform.runLater(next::fire);
        UiTestSupport.awaitPresent(
                () -> UiTestSupport.findButton("‹").filter(button -> !button.isDisabled()),
                "the enabled ‹ button after paging forward");

        // Back to the row the other tests expect.
        openEditor();
        ListView<String> reopened = quickList();
        Platform.runLater(() -> reopened.getItems().setAll("Go"));
        UiTestSupport.awaitPresent(
                () -> Optional.of(reopened.getItems().size()).filter(size -> size == 1), "the reset list");
        UiTestSupport.clickButton("Save");
        UiTestSupport.awaitPresent(
                () -> UiTestSupport.findButton("›").filter(Button::isDisabled),
                "the disabled › button once everything fits");
    }

    /// Fires `…` fire-and-forget — its handler blocks in the dialog's
    /// `showAndWait` — and waits for the editor to be up.
    private static void openEditor() {
        Button more = UiTestSupport.awaitButton("…");
        Platform.runLater(more::fire);
        UiTestSupport.awaitButton("Save");
    }

    /// The editor's own list — the app's other `ListView` holds task entries,
    /// this one plain message strings.
    @SuppressWarnings("unchecked")
    private static ListView<String> quickList() {
        return (ListView<String>) UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .filter(view -> !view.getItems().isEmpty())
                        .filter(view -> view.getItems().stream()
                                .allMatch(item -> item instanceof String))
                        .findFirst(),
                "the quick message ListView");
    }

    private static void select(ListView<String> list, String item) {
        Platform.runLater(() -> list.getSelectionModel().select(item));
        UiTestSupport.awaitPresent(
                () -> Optional.ofNullable(list.getSelectionModel().getSelectedItem())
                        .filter(item::equals),
                "the selected " + item + " row");
    }

    /// Types into the editor's `TextArea` — the only one in the dialog's own
    /// scene — once it truly holds focus (a queued `requestFocus` is not
    /// enough; the keystrokes would leak to the default button).
    private static void type(String text) {
        TextArea editor = (TextArea) UiTestSupport.awaitButton("Save").getScene()
                .getRoot().lookup(".text-area");
        UiTestSupport.awaitPresent(() -> {
            Platform.runLater(editor::requestFocus);
            return editor.isFocused() ? Optional.of(editor) : Optional.empty();
        }, "the focused quick message editor");
        FX_ROBOT.keyboard().print(text);
    }

    private static void selectTaskWithTmux() throws Exception {
        Files.writeString(UiTestSupport.tasksDir.resolve(TASK + ".md"), """
                ---
                title: quick
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
    }

    private static int indexOf(ListView<Object> list) {
        for (int i = 0; i < list.getItems().size(); i++) {
            if (list.getItems().get(i) instanceof TaskEntry.Loaded loaded
                    && loaded.id().equals(TASK)) {
                return i;
            }
        }
        return -1;
    }
}
