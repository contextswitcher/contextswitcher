package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.contextswitcher.tasks.TaskFileParser;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitButton;
import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static com.contextswitcher.ui.UiTestSupport.awaitTaskFileContaining;
import static com.contextswitcher.ui.UiTestSupport.openAddTask;
import static com.contextswitcher.ui.UiTestSupport.findButton;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// UI automation of the "Add task…" dialog compose chords — the manual-test
/// items that need no remote host: plain Enter stays in the dialog as a
/// newline, three Ctrl+Enters, three plain Enters, and Shift+Enter behave
/// like in the queue boxes (`dsn~task-create-ui~15`,
/// `dsn~message-queue-ui~26`), plus where the keyboard lands afterwards.
///
/// Boots the real `Main` (via [UiTestSupport.TestApp]) against a throwaway
/// config dir (no remotes, free WebSocket port). Needs a display: run via
/// `gradlew :app:uiTest` on a desktop, or `xvfb-run -a ./gradlew :app:uiTest`
/// headless (MADR 0014).
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class AddTaskDialogUiTest {

    @Test
    void enterInsertsNewlineAndThreeCtrlEntersCreateTheTask() throws Exception {
        openAddTask();
        awaitButton("Add plain task");
        clearPrefill();
        FX_ROBOT.keyboard().print("Alpha title").type(KeyCode.ENTER);
        // Mid-description Enter must not fire the default button: the dialog
        // (recognizable by its buttons) is still open, no task file yet.
        assertThat(findButton("Add plain task")).isPresent();
        FX_ROBOT.keyboard().print("second line")
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);
        // One Ctrl+Enter used to fire the default button right away — it takes
        // all three presses now, so a half-typed description cannot go out.
        assertThat(findButton("Add plain task")).isPresent();
        FX_ROBOT.keyboard()
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER)
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);
        Path task = awaitTaskFileContaining("second line");
        assertThat(Files.readString(task)).contains("Alpha title");
    }

    /// Cursor Up parks the caret right behind a line's newline; two Enters
    /// there used to look like a triple-Enter run and submitted mid-edit.
    /// Only Enters at the end of the field may commit.
    @Test
    void cursorUpThenTwoEntersDoesNotCreateTheTask() {
        openAddTask();
        awaitButton("Add plain task");
        clearPrefill();
        // A blank line, then Up: the caret lands on the empty line, right
        // behind a newline — two Enters there completed the old
        // two-newlines-before-caret pattern and submitted.
        FX_ROBOT.keyboard().print("Beta title").type(KeyCode.ENTER, KeyCode.ENTER)
                .print("tail").type(KeyCode.UP, KeyCode.ENTER, KeyCode.ENTER);
        assertThat(findButton("Add plain task")).isPresent();
        FX_ROBOT.keyboard().type(KeyCode.ESCAPE);
    }

    @Test
    void threePlainEntersCreateTheTaskWithoutTrailingBlanks() throws Exception {
        openAddTask();
        awaitButton("Add plain task");
        clearPrefill();
        // Into the empty field, three Enters must not commit anything.
        FX_ROBOT.keyboard().type(KeyCode.ENTER, KeyCode.ENTER, KeyCode.ENTER);
        assertThat(findButton("Add plain task")).isPresent();
        clearPrefill();
        // Shift+Enter is a newline like plain Enter, then triple-Enter commits.
        FX_ROBOT.keyboard()
                .print("Gamma delta")
                .pressAndThenRelease(KeyCode.SHIFT, KeyCode.ENTER)
                .print("epsilon")
                .type(KeyCode.ENTER, KeyCode.ENTER, KeyCode.ENTER);
        Path task = awaitTaskFileContaining("Gamma delta");
        // The exact title scalar: Shift+Enter's newline survives, the two
        // commit Enters' blank lines do not.
        assertThat(Files.readString(task))
                .contains("title: " + TaskFileParser.yamlScalar("Gamma delta\nepsilon"));
        assertThat(findButton("Add plain task")).isEmpty();
    }

    /// Outside a local category (here: the root group of a fresh config) the
    /// local button is offered right of the remote one but disabled, and the
    /// default is `Add plain task` — the test config has no remotes either
    /// (`dsn~task-create-local~4`).
    @Test
    void localClaudeSitsRightOfRemoteAndIsDisabledOutsideALocalCategory() {
        openAddTask();
        Button local = awaitButton("Add local Claude");
        Button remote = awaitButton("Add remote Claude");
        assertThat(local.isDisabled()).isTrue();
        assertThat(awaitButton("Add plain task").isDefaultButton()).isTrue();
        var buttons = local.getParent().getChildrenUnmodifiable();
        assertThat(buttons.indexOf(local)).isGreaterThan(buttons.indexOf(remote));
        FX_ROBOT.keyboard().type(KeyCode.ESCAPE);
    }

    /// Submitting leaves the keyboard on the left task list, not on the
    /// toolbar add menu the dialog was opened from, so the arrow keys and the
    /// switch chord act on the new task.
    @Test
    void creatingATaskMovesTheKeyboardToTheTaskList() throws Exception {
        openAddTask();
        awaitButton("Add plain task");
        clearPrefill();
        FX_ROBOT.keyboard().print("Zeta focus")
                .type(KeyCode.ENTER, KeyCode.ENTER, KeyCode.ENTER);
        awaitTaskFileContaining("Zeta focus");
        ListView<Object> list = awaitPresent(() -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(),
                "the task ListView");
        // `Node.isFocused` stays false while no window manager focuses the
        // stage (the headless run), so the scene's focus owner is what counts.
        awaitPresent(() -> list.getScene().getFocusOwner() == list
                ? java.util.Optional.of(true) : java.util.Optional.empty(),
                "the task list to hold the keyboard focus");
        assertThat((Node) list.getScene().getFocusOwner()).isSameAs(list);
    }

    /// The queue pane's quick messages sit above the description field too
    /// and append into it, not into the queue's add box.
    // [utest->dsn~quick-message-buttons~7]
    @Test
    void quickMessageButtonFillsTheDescription() {
        openAddTask();
        Button plain = awaitButton("Add plain task");
        clearPrefill();
        FX_ROBOT.keyboard().print("Eta");
        TextArea field = (TextArea) plain.getScene().lookup("#add-task-description");
        Button go = (Button) plain.getScene().getRoot().lookupAll(".button").stream()
                .filter(node -> node instanceof Button button && "Go".equals(button.getText()))
                .findFirst().orElseThrow();
        Platform.runLater(go::fire);
        awaitPresent(() -> Optional.of(field.getText()).filter("Eta\n\nGo"::equals),
                "Go appended to the description");
        FX_ROBOT.keyboard().type(KeyCode.ESCAPE);
    }

    /// Ctrl+T opens the dialog from the main window, its category combo
    /// pre-selected with the selection's group (the root in a fresh config).
    @Test
    void ctrlTOpensTheDialogWithTheCurrentCategory() {
        ListView<Object> list = awaitPresent(() -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(),
                "the task ListView");
        Platform.runLater(list::requestFocus);
        UiTestSupport.awaitFocused(list, "the task list");
        FX_ROBOT.keyboard().pressAndThenRelease(KeyCode.CONTROL, KeyCode.T);
        ComboBox<?> category = awaitPresent(() -> FX_ROBOT.selectNodes(ComboBox.class).fromAll()
                .filter(box -> "add-task-category".equals(box.getId()))
                .<ComboBox<?>>map(box -> (ComboBox<?>) box).findFirst(),
                "the add-task category combo");
        assertThat(category.getValue()).isEqualTo("");
        FX_ROBOT.keyboard().type(KeyCode.ESCAPE);
    }

    /// The dialog pre-fills from the system clipboard — whatever the display
    /// happens to hold. Select-all + delete makes each test start blank.
    private static void clearPrefill() {
        FX_ROBOT.keyboard().pressAndThenRelease(KeyCode.CONTROL, KeyCode.A).type(KeyCode.DELETE);
    }
}
