package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javafx.scene.control.ListView;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// A category whose `CONTEXTSWITCHER.md` says `pinned: true` is listed above
/// the unpinned ones, whatever its name sorts like (`dsn~pinned-categories~1`).
// [utest->dsn~pinned-categories~1]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class PinnedCategoryOrderUiTest {

    @Test
    void pinnedCategoryIsListedFirst() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        category(dir, "aaa-plain", false);
        category(dir, "zzz-pinned", true);

        ListView<Object> list = awaitListView();
        awaitPresent(() -> headers(list).equals(List.of("zzz-pinned", "aaa-plain"))
                ? Optional.of(true) : Optional.empty(),
                "the pinned category above the unpinned one");
    }

    private static void category(Path dir, String name, boolean pinned) throws Exception {
        Path folder = Files.createDirectories(dir.resolve(name));
        Files.writeString(folder.resolve("CONTEXTSWITCHER.md"),
                pinned ? "---\npinned: true\n---\n" : "---\nremote: host\n---\n");
        Files.writeString(folder.resolve("task.md"),
                "---\ntitle: %s task\nstatus: active\n---\nnotes\n".formatted(name));
    }

    private static List<String> headers(ListView<Object> list) {
        return list.getItems().stream()
                .filter(TaskListCell.GroupHeader.class::isInstance)
                .map(row -> ((TaskListCell.GroupHeader) row).name())
                .toList();
    }

    private static ListView<Object> awaitListView() {
        return awaitPresent(() -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(),
                "the task ListView");
    }
}
