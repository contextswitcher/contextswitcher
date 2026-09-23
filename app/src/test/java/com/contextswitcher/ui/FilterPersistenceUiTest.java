package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javafx.application.Platform;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;

import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// A toolbar filter the user turned on is still on after a restart
/// (`dsn~filter-persistence~1`): the check item comes up ticked and the list
/// comes up narrowed, without the user re-ticking it.
///
/// The restart is real: the TestFX extension relaunches the app for every test
/// method, so [#restartComesUpFilteredToRunningTasks] runs against a fresh app
/// instance in the same JVM (as in `ClaudeModeMemoryUiTest`). The two methods
/// are ordered halves of one scenario — running only the second alone fails by
/// design. The `uiTest` task keeps Java Preferences in memory
/// ([InMemoryPreferencesFactory]), so the tick never touches the developer's
/// real preferences.
// [utest->dsn~filter-persistence~1]
@Tag("ui")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestFxApplication(UiTestSupport.TestApp.class)
class FilterPersistenceUiTest {

    @AfterAll
    static void forgetTheFilter() {
        UiTestSupport.clearStoredFilters();
    }

    @Test
    @Order(1)
    void tickingTheRunningFilterStoresIt() throws Exception {
        writeTasks();
        ListView<Object> list = awaitListView();
        awaitTasks(list, List.of("paused", "running"));

        Platform.runLater(() -> filterItem("Show running tasks only").setSelected(true));
        awaitTasks(list, List.of("running"));
    }

    @Test
    @Order(2)
    void restartComesUpFilteredToRunningTasks() throws Exception {
        writeTasks();
        ListView<Object> list = awaitListView();

        // Ticked without anyone ticking it — and actually narrowing, not just
        // showing a checkmark: the flag must reach `rebuildRows`, not only the
        // menu item.
        assertThat(filterItem("Show running tasks only").isSelected()).isTrue();
        awaitTasks(list, List.of("running"));
    }

    /// One running and one paused task in a fresh app's tasks dir — enough for
    /// the running filter to have something to hide. (Paused, not done: a done
    /// task lives in the collapsed Done section and is no visible row either
    /// way.)
    private static void writeTasks() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.writeString(dir.resolve("running.md"),
                "---\ntitle: running\nstatus: active\n---\nnotes\n");
        Files.writeString(dir.resolve("paused.md"),
                "---\ntitle: paused\nstatus: suspended\n---\nnotes\n");
    }

    /// Waits until exactly the given task ids are the visible Loaded rows.
    private static void awaitTasks(ListView<Object> list, List<String> ids) {
        awaitPresent(() -> {
            List<String> visible = list.getItems().stream()
                    .filter(TaskEntry.Loaded.class::isInstance)
                    .map(row -> ((TaskEntry.Loaded) row).id())
                    .sorted().toList();
            return visible.equals(ids) ? Optional.of(true) : Optional.empty();
        }, "visible tasks " + ids);
    }

    /// A check item of the toolbar filter menu, by its text prefix.
    private static CheckMenuItem filterItem(String textPrefix) {
        return FX_ROBOT.selectNodes(MenuButton.class).fromAll()
                .flatMap(button -> button.getItems().stream())
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .filter(item -> item.getText().startsWith(textPrefix))
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static ListView<Object> awaitListView() {
        return awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(v -> (ListView<Object>) v).findFirst(),
                "the task ListView");
    }
}
