package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;

import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// A category's "Find task…" and "Add task…" rows show right under its header
/// only while the header is selected, and "Find task…" scopes the find field.
// [utest->dsn~category-action-rows~1]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class CategoryActionRowsUiTest {

    @Test
    void rowsFollowTheSelectedHeader() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("gamma"));
        Files.writeString(dir.resolve("gamma").resolve("one.md"), "---\ntitle: one\nstatus: active\n---\n");
        ListView<Object> list = awaitPresent(() -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(), "the task ListView");
        int header = awaitPresent(() -> Optional.of(headerIndex(list)).filter(i -> i >= 0)
                .filter(i -> hasTask(list, "gamma/one")), "the gamma category");
        assertThat(actionRows(list)).as("no action rows while nothing selects gamma").isEmpty();

        runFx(() -> list.getSelectionModel().select(header));
        awaitPresent(() -> Optional.of(headerIndex(list)).filter(i -> i >= 0
                && list.getItems().get(i + 1) instanceof TaskListCell.FindTaskRow
                && list.getItems().get(i + 2) instanceof TaskListCell.AddTaskRow), "the rows under the header");

        runFx(() -> list.getSelectionModel().select(headerIndex(list) + 3));
        awaitPresent(() -> actionRows(list).isEmpty() ? Optional.of(true) : Optional.empty(),
                "the rows to go with the selection");
        assertThat(list.getSelectionModel().getSelectedItem())
                .as("the task stays selected").isInstanceOf(TaskEntry.Loaded.class);

        runFx(() -> list.getSelectionModel().select(headerIndex(list)));
        Button find = awaitPresent(() -> FX_ROBOT.selectNodes(Button.class).fromAll()
                .filter(node -> "group-find-task-button".equals(node.getId())).findFirst(), "Find task…");
        runFx(find::fire);
        awaitPresent(() -> FX_ROBOT.selectNodes(Node.class).fromAll()
                .filter(node -> "find-scope-chip".equals(node.getId()) && node.isVisible())
                .findFirst(), "the scope chip");
    }

    private static int headerIndex(ListView<Object> list) {
        List<Object> items = list.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof TaskListCell.GroupHeader h && "gamma".equals(h.name())) {
                return i;
            }
        }
        return -1;
    }

    private static List<Object> actionRows(ListView<Object> list) {
        return list.getItems().stream().filter(row -> row instanceof TaskListCell.FindTaskRow
                || row instanceof TaskListCell.AddTaskRow add && "gamma".equals(add.group())).toList();
    }

    private static boolean hasTask(ListView<Object> list, String id) {
        return list.getItems().stream()
                .anyMatch(row -> row instanceof TaskEntry.Loaded loaded && loaded.id().equals(id));
    }

    private static void runFx(Runnable action) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    }
}
