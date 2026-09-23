package com.contextswitcher.ui;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javafx.application.Platform;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import com.contextswitcher.Main;
import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// A virtual-desktop switch must not move the selection while the keyboard is
/// in the queue pane's message box (`dsn~desktop-last-task-selection~4`).
///
/// The switch is the one *ambient* source of a selection change — every other
/// one is a click or a keypress — and a selection change swaps the queue pane
/// to the new task, which commits the half-typed message as the **previous**
/// task's draft before clearing the box. Field report 2026-09-12: "I typed
/// something in the queue field, focus was stolen to the task list - and my
/// typed letters lost" (they were not lost; they were filed under the task the
/// user had just left).
///
/// Its own class, not a case in `FilterAndCombineUiTest`: that class shares one
/// tasks directory between its methods and several of its assertions are exact
/// row sets, so two extra categories here turned one pre-existing failure into
/// five.
// [utest->dsn~desktop-last-task-selection~4]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class DesktopSwitchWhileTypingUiTest {

    @Test
    void aDesktopSwitchLeavesTheSelectionAloneWhileTheQueueBoxIsFocused() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("pi"));
        Files.createDirectories(dir.resolve("rho"));
        Files.writeString(dir.resolve("pi/CONTEXTSWITCHER.md"), "---\ndesktop: Pi\n---\n");
        Files.writeString(dir.resolve("rho/CONTEXTSWITCHER.md"), "---\ndesktop: Rho\n---\n");
        Files.writeString(dir.resolve("pi/typing.md"),
                "---\ntitle: typing\nstatus: active\n---\nnotes\n");
        Files.writeString(dir.resolve("rho/other.md"),
                "---\ntitle: other\nstatus: active\n---\nnotes\n");

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitVisible("pi/typing", true);

        runFx(() -> mainWindow().updateActiveDesktop("Pi"));
        runFx(() -> filterItem("Show active desktop only").setSelected(true));
        awaitVisible("rho/other", false);
        runFx(() -> mainWindow().selectTask("pi/typing"));
        awaitSelected("pi/typing");

        // The keyboard goes into the message box, as if mid-word.
        runFx(() -> queueBox().requestFocus());
        UiTestSupport.sleep();

        runFx(() -> mainWindow().updateActiveDesktop("Rho"));
        awaitVisible("rho/other", true);
        UiTestSupport.sleep();

        // The list followed the desktop; the selection did not move to its
        // task, so the queue pane was never swapped and the draft never
        // committed. (`pi/typing`'s row is gone with the filter, so the
        // selection is simply empty — what matters is where it did *not* go.)
        assertThat(callFx(() -> queueBox().isFocused()))
                .as("the queue box kept the keyboard")
                .isTrue();
        assertThat(callFx(() -> selectedId(list).equals("rho/other")))
                .as("the desktop's task was not selected out from under the typing")
                .isFalse();
        // The title still follows the desktop, to the category it would select.
        // [utest->dsn~window-title-category~3]
        assertThat(callFx(() -> ((Stage) list.getScene().getWindow()).getTitle().startsWith("rho | ")))
                .as("the title names the new desktop's category")
                .isTrue();

        // A desktop with no task to select names itself in the title.
        runFx(() -> mainWindow().updateActiveDesktop("Sigma"));
        awaitVisible("rho/other", false);
        assertThat(callFx(() -> ((Stage) list.getScene().getWindow()).getTitle().startsWith("Sigma | ")))
                .as("the title falls back to the desktop name")
                .isTrue();

        // Focus out of the box, and the next switch applies normally.
        runFx(list::requestFocus);
        runFx(() -> mainWindow().updateActiveDesktop("Pi"));
        runFx(() -> mainWindow().updateActiveDesktop("Rho"));
        awaitSelected("rho/other");
    }

    /// The queue pane's message box — the `TextArea` its prompt names.
    private static TextArea queueBox() {
        return FX_ROBOT.selectNodes(TextArea.class).fromAll()
                .filter(area -> area.getPromptText() != null
                        && area.getPromptText().startsWith("Queue a message"))
                .findFirst().orElseThrow();
    }

    private static void awaitVisible(String id, boolean present) {
        awaitPresent(() -> callFx(() -> awaitListView().getItems().stream().anyMatch(row ->
                row instanceof TaskEntry.Loaded loaded && loaded.id().equals(id))) == present
                        ? Optional.of(true) : Optional.empty(),
                (present ? "visible " : "hidden ") + id);
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

    private static CheckMenuItem filterItem(String textPrefix) {
        return FX_ROBOT.selectNodes(MenuButton.class).fromAll()
                .flatMap(button -> button.getItems().stream())
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .filter(item -> item.getText().startsWith(textPrefix))
                .findFirst().orElseThrow();
    }

    /// The filters survive between test applications, so start from a known
    /// state like the other filter tests do.
    private static void resetFilters() {
        runFx(() -> {
            filterItem("Show running tasks only").setSelected(false);
            filterItem("Also show categories without a desktop").setSelected(false);
            filterItem("Show active desktop only").setSelected(false);
            filterItem("Awaits input only").setSelected(false);
        });
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
