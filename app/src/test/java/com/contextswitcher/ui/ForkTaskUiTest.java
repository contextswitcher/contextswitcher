package com.contextswitcher.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.IntStream;

import com.contextswitcher.tasks.TaskEntry;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxPredicates.hasText;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The terminal bar's "Fork…" button and its dialog (`dsn~task-fork~1`): the
/// button's enabled-ness follows the previewed task (a remote, a tmux window
/// and a recorded Claude session id — `plain` has none, `forkable` has all
/// three), the dialog's Fork button is disabled while its field is blank,
/// Back creates nothing, and typing an instruction, picking a model, then
/// either clicking Fork or firing its compose chord starts a fork — reusing
/// `dsn~compose-key-conventions`, which needs a test at each call site
/// (`AddTaskDialogUiTest`, `QueueComposeKeysUiTest` are the other two).
///
/// The seeded remote ("devbox") is not a real host — the assertions only
/// need the synchronous half of `Main.forkTask` (the task file write, which
/// runs on the FX thread before the ssh round-trip), never the tmux/Claude
/// launch itself.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless
/// (MADR 0014).
// [utest->dsn~task-fork~1]
@Tag("ui")
@TestFxApplication(ForkTaskUiTest.TestApp.class)
class ForkTaskUiTest {

    public static class TestApp extends UiTestSupport.TestApp {

        @Override
        protected void seedTasks(Path tasksDir) throws Exception {
            Files.writeString(tasksDir.resolve("plain.md"), """
                    ---
                    title: plain
                    status: active
                    ---
                    notes
                    """);
            Files.writeString(tasksDir.resolve("forkable.md"), """
                    ---
                    title: Forkable source
                    status: active
                    remote: devbox
                    tmux:
                      session: '0'
                      window: '@5'
                    claude:
                      cwd: /home/carl/proj
                      sessionId: source-session-id
                    ---
                    notes
                    """);
        }
    }

    @Test
    void forkIsDisabledWithoutARemoteTmuxWindowAndSessionId() {
        selectTask("plain");
        Button fork = forkButton();
        assertThat(fork.isDisabled()).isTrue();
        assertThat(fork.getTooltip()).isNotNull();
        assertThat(fork.getTooltip().getText()).contains("needs");
    }

    @Test
    void forkIsEnabledForATaskWithASessionIdAndBackCreatesNothing() throws IOException {
        selectTask("forkable");
        Button fork = forkButton();
        assertThat(fork.isDisabled()).isFalse();
        long before = countTaskFiles();
        FX_ROBOT.mouse().moveTo(fork).click();

        DialogPane dialog = awaitPresent(
                () -> FX_ROBOT.selectNodes(DialogPane.class, ".dialog-pane").fetchOptional(),
                "the fork dialog");
        assertThat(dialog.getScene().getWindow()).isNotNull();
        Button forkSubmit = awaitPresent(
                () -> FX_ROBOT.selectNodes(Button.class).from(dialog)
                        .filter(hasText("Fork")).findFirst(),
                "the dialog's Fork button");
        // Blank field: Fork stays disabled.
        assertThat(forkSubmit.isDisabled()).isTrue();

        Button back = awaitPresent(
                () -> FX_ROBOT.selectNodes(Button.class).from(dialog)
                        .filter(hasText("Back")).findFirst(),
                "the dialog's Back button");
        FX_ROBOT.mouse().moveTo(back).click();
        assertThat(countTaskFiles()).isEqualTo(before);
    }

    @Test
    void typingAndPickingAModelThenTheComposeChordForksTheTask() throws Exception {
        selectTask("forkable");
        FX_ROBOT.mouse().moveTo(forkButton()).click();

        DialogPane dialog = awaitPresent(
                () -> FX_ROBOT.selectNodes(DialogPane.class, ".dialog-pane").fetchOptional(),
                "the fork dialog");
        TextArea field = awaitPresent(
                () -> FX_ROBOT.selectNodes(TextArea.class).from(dialog)
                        .filter(area -> "fork-instruction".equals(area.getId())).findFirst(),
                "the fork instruction field");
        FX_ROBOT.mouse().moveTo(field).click();
        pickInCombo(modeCombo(dialog, "model: as is"), "opus");

        FX_ROBOT.keyboard().print("Add a regression test")
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER)
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER)
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);

        Path task = awaitForkedTaskFile("Add a regression test");
        String content = Files.readString(task);
        assertThat(content).contains("remote: devbox");
        assertThat(content).contains("Forked from Forkable source");
        assertThat(content).contains("(`forkable`)");
        // The pick is remembered the same way the Add-task dialog's is.
        assertThat(MainWindow.lastMode().model()).isEqualTo("opus");
    }

    private static Button forkButton() {
        return awaitPresent(
                () -> FX_ROBOT.selectNodes(Button.class).fromAll()
                        .filter(button -> "fork-task-button".equals(button.getId())).findFirst(),
                "the Fork… button");
    }

    private static void selectTask(String taskId) {
        ListView<Object> list = awaitPresent(() -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                .filter(view -> view.getItems().stream().anyMatch(item ->
                        item instanceof TaskEntry.Loaded loaded && loaded.id().equals(taskId)))
                .<ListView<Object>>map(view -> (ListView<Object>) view)
                .findFirst(),
                "the task list with " + taskId);
        int index = IntStream.range(0, list.getItems().size())
                .filter(i -> list.getItems().get(i) instanceof TaskEntry.Loaded loaded
                        && loaded.id().equals(taskId))
                .findFirst().orElseThrow();
        Platform.runLater(() -> list.getSelectionModel().select(index));
        awaitPresent(() -> Optional.of(list).filter(v ->
                v.getSelectionModel().getSelectedIndex() == index), "the selection to settle");
    }

    private static long countTaskFiles() throws IOException {
        try (var files = Files.walk(UiTestSupport.tasksDir)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".md")).count();
        }
    }

    private static Path awaitForkedTaskFile(String marker) throws IOException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            try (var files = Files.walk(UiTestSupport.tasksDir)) {
                var hits = files
                        .filter(file -> file.getFileName().toString().endsWith(".md"))
                        .filter(file -> !file.getFileName().toString().equals("forkable.md"))
                        .filter(file -> !file.getFileName().toString().equals("plain.md"))
                        .filter(file -> readQuietly(file).contains(marker))
                        .toList();
                if (!hits.isEmpty()) {
                    return hits.getFirst();
                }
            }
            UiTestSupport.sleep();
        }
        throw new AssertionError("No forked task file containing \"" + marker + "\"");
    }

    private static String readQuietly(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> modeCombo(DialogPane dialog, String asIsEntry) {
        return awaitPresent(
                () -> FX_ROBOT.selectNodes(ComboBox.class).from(dialog)
                        .filter(combo -> combo.getItems().contains(asIsEntry))
                        .map(combo -> (ComboBox<String>) combo)
                        .findFirst(),
                "combo with entry \"" + asIsEntry + "\"");
    }

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
