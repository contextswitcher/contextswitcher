package com.contextswitcher.ui;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javafx.application.Platform;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;

import com.contextswitcher.Main;
import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// A task-file rename must not read as a task switch in the queue pane
/// (`dsn~message-queue-ui~26`).
///
/// A live task adopts the short title its Claude session publishes seconds
/// after it was created (`dsn~claude-title-sync~3`), which renames the file and
/// so changes the task id — typically while the user is still typing the first
/// message for it. `showTask` used to see the new id, commit the half-typed add
/// box under the **old** one and load the new id's empty queue: the box went
/// empty, no card appeared (the old id has no row), and the message stayed in a
/// queue file nothing points at. Field report 2026-09-13: "i just typed and
/// then the box was empty, but no task switched".
// [utest->dsn~message-queue-ui~26]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class RenameWhileTypingUiTest {

    private static final String FROM = "renaming";
    private static final String TO = "renamed";
    private static final String TYPED = "half a sentence about";

    @Test
    void aRenameKeepsTheHalfTypedMessageInTheBox() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.writeString(dir.resolve(FROM + ".md"),
                "---\ntitle: renaming\nstatus: active\n---\nnotes\n");
        awaitRow(FROM);
        runFx(() -> mainWindow().selectTask(FROM));
        awaitSelected(FROM);

        // Mid-word in the queue box, as the title adoption lands.
        TextArea box = queueBox();
        runFx(() -> {
            box.requestFocus();
            box.setText(TYPED);
            box.end();
        });
        UiTestSupport.sleep();

        Files.move(dir.resolve(FROM + ".md"), dir.resolve(TO + ".md"));
        runFx(() -> mainWindow().taskRenamed(FROM, TO));
        awaitSelected(TO);
        UiTestSupport.sleep();

        assertThat(callFx(() -> TYPED.equals(queueBox().getText())))
                .as("the half-typed message survived the rename")
                .isTrue();
        assertThat(Files.exists(queueFile(FROM)))
                .as("nothing was filed under the id the task left")
                .isFalse();
        assertThat(Files.exists(queueFile(TO)))
                .as("and nothing was queued under the new id either — it is still being typed")
                .isFalse();

        // The box is a normal add box again: a real switch still commits it.
        Files.writeString(dir.resolve("other.md"),
                "---\ntitle: other\nstatus: active\n---\nnotes\n");
        awaitRow("other");
        runFx(() -> mainWindow().selectTask("other"));
        awaitSelected("other");
        awaitPresent(() -> Files.exists(queueFile(TO)) ? Optional.of(true) : Optional.empty(),
                "the message committed to the renamed task by the real switch");
        assertThat(Files.readString(queueFile(TO))).contains(TYPED);
    }

    /// The per-task queue file, in the tasks dir's `.queues/` (`Main` keys it
    /// by task id with `/` flattened to `__`).
    private static Path queueFile(String taskId) {
        return UiTestSupport.tasksDir.resolve(".queues")
                .resolve(taskId.replace("/", "__") + ".yaml");
    }

    /// The queue pane's message box — the `TextArea` its prompt names.
    private static TextArea queueBox() {
        return FX_ROBOT.selectNodes(TextArea.class).fromAll()
                .filter(area -> area.getPromptText() != null
                        && area.getPromptText().startsWith("Queue a message"))
                .findFirst().orElseThrow();
    }

    private static void awaitRow(String id) {
        awaitPresent(() -> callFx(() -> awaitListView().getItems().stream().anyMatch(row ->
                row instanceof TaskEntry.Loaded loaded && loaded.id().equals(id)))
                        ? Optional.of(true) : Optional.empty(),
                "the " + id + " row");
    }

    private static void awaitSelected(String id) {
        awaitPresent(() -> callFx(() -> selectedId(awaitListView()).equals(id))
                ? Optional.of(true) : Optional.empty(), "selected task " + id);
    }

    private static String selectedId(ListView<Object> list) {
        return list.getSelectionModel().getSelectedItem() instanceof TaskEntry.Loaded loaded
                ? loaded.id() : "";
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
