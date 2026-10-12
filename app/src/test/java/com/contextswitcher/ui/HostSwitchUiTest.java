package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;

import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// Unticking a host in the filter menu's Hosts submenu hides its work — the
/// tasks whose `remote:` it is, and the categories whose `CONTEXTSWITCHER.md`
/// names it, empty ones included — and saves it as `active: false` in `hosts`;
/// ticking it again brings everything back (`dsn~host-on-off~2`).
// [utest->dsn~host-on-off~2]
@Tag("ui")
@TestFxApplication(HostSwitchUiTest.App.class)
class HostSwitchUiTest {

    public static class App extends UiTestSupport.TestApp {
        @Override
        protected void seedTasks(Path dir) throws Exception {
            Files.writeString(dir.resolve("here.md"),
                    "---\ntitle: here\nstatus: active\nremote: nixos\n---\nnotes\n");
            Files.writeString(dir.resolve("away.md"),
                    "---\ntitle: away\nstatus: active\nremote: offbox\n---\nnotes\n");
            Files.createDirectories(dir.resolve("gpu"));
            Files.writeString(dir.resolve("gpu/CONTEXTSWITCHER.md"), "---\nremote: offbox\n---\n");
        }
    }

    @Test
    void untickingAHostHidesItsTasksAndCategories() throws Exception {
        ListView<Object> list = awaitListView();
        awaitRows(list, List.of("away", "here"), true);

        Platform.runLater(() -> click(hostItem("offbox")));
        awaitRows(list, List.of("here"), false);
        assertThat(Files.readString(UiTestSupport.tasksDir.resolveSibling("settings.yaml")))
                .containsPattern("(?m)^- destination: offbox\\s*\\n\\s*active: false");

        Platform.runLater(() -> click(hostItem("offbox")));
        awaitRows(list, List.of("away", "here"), true);
    }

    /// Waits until exactly `ids` are the visible task rows and the empty
    /// `gpu` category's header is shown (`gpu`) or not.
    private static void awaitRows(ListView<Object> list, List<String> ids, boolean gpu) {
        awaitPresent(() -> {
            List<String> visible = list.getItems().stream()
                    .filter(TaskEntry.Loaded.class::isInstance)
                    .map(row -> ((TaskEntry.Loaded) row).id())
                    .sorted().toList();
            boolean gpuShown = list.getItems().stream().anyMatch(row ->
                    row instanceof TaskListCell.GroupHeader header && header.name().equals("gpu"));
            return visible.equals(ids) && gpuShown == gpu ? Optional.of(true) : Optional.empty();
        }, "visible tasks " + ids + (gpu ? " with" : " without") + " the gpu category");
    }

    /// What a click on a check item does: the menu skin flips the tick, then
    /// the action fires (`fire()` alone leaves the tick as it was).
    private static void click(CheckMenuItem item) {
        item.setSelected(!item.isSelected());
        item.fire();
    }

    /// The Hosts submenu's item for `host`, filled the way opening it does.
    private static CheckMenuItem hostItem(String host) {
        Menu hosts = FX_ROBOT.selectNodes(MenuButton.class).fromAll()
                .flatMap(button -> button.getItems().stream())
                .filter(item -> "hosts-menu".equals(item.getId()))
                .map(Menu.class::cast)
                .findFirst().orElseThrow();
        hosts.getOnShowing().handle(new Event(Menu.ON_SHOWING));
        return hosts.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .filter(item -> item.getText().equals(host))
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
