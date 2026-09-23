package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javafx.scene.Node;
import javafx.scene.control.ListView;

import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The pin glyph shows on the pinned task's row and on no other
/// (`dsn~pinned-tasks~2`): two tasks render, only one carries `pinned: true`,
/// so exactly one pin may exist.
// [utest->dsn~pinned-tasks~2]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class PinnedTaskIconUiTest {

    @Test
    void onlyThePinnedTaskRowShowsThePin() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.writeString(dir.resolve("plain.md"),
                "---\ntitle: plain\nstatus: active\n---\nnotes\n");
        Files.writeString(dir.resolve("kept.md"),
                "---\ntitle: kept\nstatus: active\npinned: true\n---\nnotes\n");

        // Both rows must be on screen before counting — a pin count of one
        // while only the pinned task has loaded would pass for the wrong reason.
        ListView<Object> list = awaitPresent(() -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(),
                "the task ListView");
        awaitPresent(() -> loadedIds(list).containsAll(java.util.List.of("plain", "kept"))
                ? Optional.of(true) : Optional.empty(), "both task rows");
        awaitPresent(() -> pins() == 1 ? Optional.of(true) : Optional.empty(),
                "exactly one pin glyph in the task list");
        assertThat(pins()).isEqualTo(1);
    }

    private static java.util.List<String> loadedIds(ListView<Object> list) {
        return list.getItems().stream()
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(row -> ((TaskEntry.Loaded) row).id())
                .toList();
    }

    private static long pins() {
        return FX_ROBOT.selectNodes(Node.class).fromAll()
                .filter(node -> "task-pin-icon".equals(node.getId()))
                .count();
    }
}
