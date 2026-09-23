package com.contextswitcher.ui;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.contextswitcher.queue.ClaudeMode;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxPredicates.hasText;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The window choice play and the terminal placeholder share asks for the
/// model and effort of the Claude session it starts, remembers the pick, and
/// starts nothing when cancelled (`dsn~remote-window-choice~6`,
/// `dsn~claude-mode-select~3`).
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~remote-window-choice~6]
// [utest->dsn~claude-mode-select~3]
@Tag("ui")
@TestFxApplication(RemoteWindowModeUiTest.TestApp.class)
class RemoteWindowModeUiTest {

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            stage.setScene(new Scene(new Pane(), 800, 600));
            stage.show();
        }
    }

    @Test
    void claudeChoiceCarriesThePickedModeAndRemembersIt() {
        AtomicReference<Optional<MainWindow.RemoteWindowChoice>> result = ask();
        pickInCombo(modeCombo("model: as is"), "sonnet");
        pickInCombo(modeCombo("effort: as is"), "low");
        clickDialogButton("claude");

        MainWindow.RemoteWindowChoice choice = settled(result)
                .orElseThrow(() -> new AssertionError("the dialog was cancelled"));
        assertThat(choice.withClaude()).as("the claude choice").isTrue();
        assertThat(choice.mode()).isEqualTo(new ClaudeMode("sonnet", "low"));
        assertThat(MainWindow.lastMode()).as("remembered for the next start")
                .isEqualTo(new ClaudeMode("sonnet", "low"));
    }

    @Test
    void cancellingStartsNothing() {
        AtomicReference<Optional<MainWindow.RemoteWindowChoice>> result = ask();
        clickDialogButton("Cancel");

        assertThat(settled(result)).isEmpty();
    }

    /// Opened from the terminal placeholder's `claude` button the choice is
    /// already made, so the dialog is the pickers alone: no **tmux** button to
    /// undo it with.
    @Test
    void theClaudeButtonAlonePickerDialogHasNoTmuxButton() {
        ask(null, false);

        assertThat(FX_ROBOT.selectNodes(Button.class).fromAll().filter(hasText("tmux")).findFirst())
                .as("the tmux button").isEmpty();
        assertThat(FX_ROBOT.selectNodes(Button.class).fromAll()
                .filter(hasText("claude")).findFirst()).as("the claude button").isPresent();
        clickDialogButton("Cancel");
    }

    /// The host in the text is the one passed in — the task's own `remote:` is
    /// null whenever the host comes from the category's `CONTEXTSWITCHER.md`,
    /// and the dialog used to print that null.
    @Test
    void theResolvedHostIsNamedEvenWhenTheTaskCarriesNone() {
        ask("koppor@devbox", false);

        assertThat(FX_ROBOT.selectNodes(Label.class).from(findDialog().orElseThrow())
                .filter(label -> label.getText() != null
                        && label.getText().contains("koppor@devbox")).findFirst())
                .as("the resolved host in the dialog text").isPresent();
        clickDialogButton("Cancel");
    }

    /// Shows the dialog (blocking on the FX thread, hence `runLater`) and hands
    /// back the cell its outcome lands in — empty `Optional` for a cancel, and
    /// still null while the dialog is up.
    private static AtomicReference<Optional<MainWindow.RemoteWindowChoice>> ask() {
        return ask("devbox", true);
    }

    /// As [#ask], with the host the dialog names and whether the **tmux**
    /// button is offered. The task itself carries no `remote:` here: the
    /// resolved host reaches the dialog as the parameter, not through the task.
    private static AtomicReference<Optional<MainWindow.RemoteWindowChoice>> ask(
            String remote, boolean offerTmux) {
        Task task = new Task("t", "T", TaskStatus.ACTIVE, null,
                null, null, null, null, null, null, "");
        AtomicReference<Optional<MainWindow.RemoteWindowChoice>> result = new AtomicReference<>();
        Platform.runLater(() -> result.set(
                Optional.ofNullable(MainWindow.askRemoteWindow(task, remote, offerTmux))));
        awaitPresent(RemoteWindowModeUiTest::findDialog, "the remote-window dialog");
        return result;
    }

    /// The dialog's outcome once it has closed — the cell holds null while it
    /// is still up, and an empty `Optional` for a cancel.
    private static Optional<MainWindow.RemoteWindowChoice> settled(
            AtomicReference<Optional<MainWindow.RemoteWindowChoice>> result) {
        return awaitPresent(() -> Optional.ofNullable(result.get()), "dialog outcome");
    }

    private static Optional<DialogPane> findDialog() {
        return FX_ROBOT.selectNodes(DialogPane.class, ".dialog-pane").fetchOptional();
    }

    private static void clickDialogButton(String text) {
        Button button = awaitPresent(
                () -> FX_ROBOT.selectNodes(Button.class).fromAll().filter(hasText(text)).findFirst(),
                "dialog button \"" + text + "\"");
        FX_ROBOT.mouse().moveTo(button).click();
    }

    /// The dialog's model or effort picker, identified by its "as is" first
    /// entry — the value varies with the remembered pick.
    @SuppressWarnings("unchecked")
    private static ComboBox<String> modeCombo(String asIsEntry) {
        return awaitPresent(
                () -> FX_ROBOT.selectNodes(ComboBox.class).from(findDialog().orElseThrow())
                        .filter(combo -> combo.getItems().contains(asIsEntry))
                        .map(combo -> (ComboBox<String>) combo)
                        .findFirst(),
                "combo with entry \"" + asIsEntry + "\"");
    }

    /// Opens the combo's popup with a click and clicks the entry — the real
    /// selection UI, not `setValue`.
    private static void pickInCombo(ComboBox<String> combo, String entry) {
        FX_ROBOT.mouse().moveTo(combo).click();
        ListCell<?> cell = awaitPresent(
                () -> FX_ROBOT.selectNodes(ListCell.class).fromAll()
                        .filter(hasText(entry))
                        .filter(ListCell::isVisible)
                        .<ListCell<?>>map(c -> c)
                        .findFirst(),
                "combo entry \"" + entry + "\"");
        FX_ROBOT.mouse().moveTo(cell).click();
    }
}
