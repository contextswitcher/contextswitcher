package com.contextswitcher.ui;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.contextswitcher.Main;
import com.contextswitcher.tasks.TaskEntry;

import javafx.application.Platform;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.input.KeyCode;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// Back/Forward along the visited tasks (`dsn~task-history-navigation~2`).
/// The trail is fed by the list's selection listener and the chords are a
/// capture-phase scene filter, so only a running window can show either.
// [utest->dsn~task-history-navigation~2]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class TaskHistoryNavigationUiTest {

    @Test
    void altArrowsWalkBackAndForwardThroughTheVisitedTasks() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("hist"));
        for (String name : new String[] {"one", "two", "three"}) {
            Files.writeString(dir.resolve("hist/" + name + ".md"), taskFile(name));
        }

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitFx(() -> indexOfId(list, "hist/three") >= 0, "all three rows to load");

        select(list, "hist/one");
        select(list, "hist/two");
        select(list, "hist/three");

        press(KeyCode.LEFT);
        awaitFx(() -> selectedId(list).equals("hist/two"), "Back to land on the previous task");
        press(KeyCode.LEFT);
        awaitFx(() -> selectedId(list).equals("hist/one"), "Back again to land on the first task");

        // The oldest visit: Back has nowhere left to go and the arrow says so.
        press(KeyCode.LEFT);
        UiTestSupport.sleep();
        assertThat(callFx(() -> selectedId(list).equals("hist/one")))
                .as("Back at the oldest visit stays put")
                .isTrue();
        assertThat(callFx(() -> historyButton("backButton").isDisable()))
                .as("the Back arrow is greyed out at the oldest visit")
                .isTrue();

        press(KeyCode.RIGHT);
        awaitFx(() -> selectedId(list).equals("hist/two"), "Forward to retrace the trail");

        // Selecting anything else truncates the forward branch.
        select(list, "hist/three");
        assertThat(callFx(() -> historyButton("forwardButton").isDisable()))
                .as("a fresh selection drops the forward branch")
                .isTrue();
    }

    /// A desktop switch selects the desktop's last task and the activated
    /// browser window selects its tab's task right after; Back must skip the
    /// task that only flashed (field report 2026-09-13).
    @Test
    void backSkipsATaskReplacedAtOnce() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("flash"));
        for (String name : new String[] {"before", "desktop", "browser"}) {
            Files.writeString(dir.resolve("flash/" + name + ".md"), taskFile(name));
        }

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitFx(() -> indexOfId(list, "flash/browser") >= 0, "all rows to load");

        select(list, "flash/before");
        flash(list, "flash/desktop");
        select(list, "flash/browser");

        press(KeyCode.LEFT);
        awaitFx(() -> selectedId(list).equals("flash/before"),
                "Back to skip the flashed task and land on the one worked on before");
    }

    /// A task visited and then deleted leaves a dead entry on the trail; Back
    /// has to walk past it instead of selecting nothing.
    @Test
    void backSkipsATaskDeletedSinceTheVisit() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("gone"));
        Files.writeString(dir.resolve("gone/keep.md"), taskFile("keep"));
        Path doomed = dir.resolve("gone/doomed.md");
        Files.writeString(doomed, taskFile("doomed"));
        Files.writeString(dir.resolve("gone/last.md"), taskFile("last"));

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitFx(() -> indexOfId(list, "gone/last") >= 0, "all rows to load");

        select(list, "gone/keep");
        select(list, "gone/doomed");
        select(list, "gone/last");

        Files.delete(doomed);
        awaitFx(() -> indexOfId(list, "gone/doomed") < 0, "the deleted row to disappear");

        press(KeyCode.LEFT);
        awaitFx(() -> selectedId(list).equals("gone/keep"),
                "Back to skip the deleted task and land on the one before it");
    }

    /// A task selected and then looked at: stays on the trail.
    private static void select(ListView<Object> list, String id) {
        flash(list, id);
        try {
            Thread.sleep(1_200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /// A task selected and replaced at once, as a desktop switch does before
    /// the browser reports its tab.
    private static void flash(ListView<Object> list, String id) {
        runFx(() -> list.getSelectionModel().select(indexOfId(list, id)));
        awaitFx(() -> selectedId(list).equals(id), "the row " + id + " to be selected");
    }

    private static void press(KeyCode arrow) {
        FX_ROBOT.keyboard().pressAndThenRelease(KeyCode.ALT, arrow);
    }

    private static javafx.scene.control.Button historyButton(String name) {
        return (javafx.scene.control.Button) field(mainWindow(), name);
    }

    private static void resetFilters() {
        runFx(() -> {
            filterItem("Show running tasks only").setSelected(false);
            filterItem("Also show categories without a desktop").setSelected(false);
            filterItem("Show active desktop only").setSelected(false);
            filterItem("Awaits input only").setSelected(false);
        });
    }

    @AfterAll
    static void forgetFilters() {
        UiTestSupport.clearStoredFilters();
    }

    private static CheckMenuItem filterItem(String textPrefix) {
        return FX_ROBOT.selectNodes(MenuButton.class).fromAll()
                .flatMap(button -> button.getItems().stream())
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .filter(item -> item.getText().startsWith(textPrefix))
                .findFirst().orElseThrow();
    }

    private static MainWindow mainWindow() {
        return (MainWindow) field(UiTestSupport.app, "window");
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = (owner instanceof Main ? Main.class : MainWindow.class)
                    .getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String taskFile(String title) {
        return "---\ntitle: " + title + "\nstatus: active\n---\nnotes\n";
    }

    private static ListView<Object> awaitListView() {
        return UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(v -> (ListView<Object>) v).findFirst(),
                "the task ListView");
    }

    private static int indexOfId(ListView<Object> list, String id) {
        for (int i = 0; i < list.getItems().size(); i++) {
            if (list.getItems().get(i) instanceof TaskEntry.Loaded loaded && loaded.id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static String selectedId(ListView<Object> list) {
        return list.getSelectionModel().getSelectedItem() instanceof TaskEntry.Loaded loaded
                ? loaded.id() : "";
    }

    private static void awaitFx(BooleanSupplier condition, String what) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (callFx(condition)) {
                return;
            }
            UiTestSupport.sleep();
        }
        throw new AssertionError("Timed out waiting for " + what);
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
            if (!done.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("FX action did not complete");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        return result[0];
    }
}
