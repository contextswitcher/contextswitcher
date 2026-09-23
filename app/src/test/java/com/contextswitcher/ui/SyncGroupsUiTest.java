package com.contextswitcher.ui;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.contextswitcher.Main;
import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.tasks.TaskEntry;
import com.contextswitcher.tasks.TaskSync;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// A shared task another member pushed shows up as a badge on the sync-groups
/// button; the window's "Move to category" sorts it in as a local task that is
/// a member of the group, which the task's own "Sync groups" submenu then shows.
// [utest->dsn~task-sync-groups-ui~1]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class SyncGroupsUiTest {

    @Test
    void sharedTaskIsBadgedAndSortedInFromTheWindow() throws Exception {
        Path temp = Files.createTempDirectory("cs-sync-uitest");
        Path bare = temp.resolve("team.git");
        Path seed = temp.resolve("seed");
        git("init", "--bare", bare.toString());
        git("init", seed.toString());
        Files.writeString(seed.resolve("fix.md"), "---\ntitle: Fix the shared bug\n---\n# Notes\n");
        git("-C", seed.toString(), "add", "-A");
        git("-C", seed.toString(), "commit", "-m", "seed");
        git("-C", seed.toString(), "push", bare.toString(), "HEAD:main");
        Files.createDirectories(UiTestSupport.tasksDir.resolve(".sync"));
        Files.writeString(UiTestSupport.tasksDir.resolve(".sync/groups.yaml"),
                "- name: team\n  url: " + bare + "\n");

        MainWindow window = UiTestSupport.awaitPresent(
                () -> Optional.ofNullable((MainWindow) field(UiTestSupport.app, Main.class, "window")),
                "the main window");
        UiTestSupport.awaitPresent(() -> Optional.ofNullable(field(UiTestSupport.app, Main.class, "taskSync")),
                "the sync groups to be installed");
        @SuppressWarnings("unchecked")
        Supplier<CompletableFuture<Void>> round =
                (Supplier<CompletableFuture<Void>>) field(UiTestSupport.app, Main.class, "syncRound");
        round.get();
        Label badge = callFx(() -> (Label) ((Button) field(window, MainWindow.class, "syncButton")).getGraphic().lookup(".update-badge"));
        UiTestSupport.awaitPresent(() -> Optional.ofNullable(
                callQuietly(() -> badge.isVisible() && "1".equals(badge.getText()) ? badge : null)),
                "the badge counting one shared task");

        callFx(() -> {
            ((Button) field(window, MainWindow.class, "syncButton")).fire();
            return null;
        });
        ListCell<?> cell = UiTestSupport.awaitPresent(() -> Optional.ofNullable(callQuietly(() ->
                FX_ROBOT.selectNodes(ListCell.class).fromAll()
                        .filter(c -> c.getItem() instanceof TaskSync.Incoming)
                        .findFirst().orElse(null))), "the shared task in the sync-groups window");
        callFx(() -> {
            item(cell.getContextMenu(), "Move to category", "(no category)").fire();
            return null;
        });

        Path local = UiTestSupport.tasksDir.resolve("fix.md");
        UiTestSupport.awaitPresent(() -> Optional.ofNullable(callQuietly(() ->
                Files.exists(local) ? local : null)), "the sorted-in task file");
        assertThat(Files.readString(local)).contains("title: Fix the shared bug", "sync:", "syncId: fix",
                "status: suspended");
        assertThat(callFx(badge::isVisible)).isFalse();

        // The task's own row menu shows it as a member of the group.
        @SuppressWarnings("unchecked")
        ListView<Object> list = UiTestSupport.awaitPresent(() -> Optional.ofNullable(callQuietly(() ->
                (ListView<Object>) FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .filter(view -> view.getItems().stream().anyMatch(item -> item instanceof TaskEntry))
                        .findFirst().orElse(null))), "the task list");
        ListCell<?> row = UiTestSupport.awaitPresent(() -> Optional.ofNullable(callQuietly(() -> {
            int index = -1;
            for (int i = 0; i < list.getItems().size(); i++) {
                if (list.getItems().get(i) instanceof TaskEntry.Loaded loaded && loaded.id().equals("fix")) {
                    index = i;
                }
            }
            if (index < 0) {
                return null;
            }
            list.scrollTo(index);
            return FX_ROBOT.selectNodes(ListCell.class).from(list)
                    .filter(c -> c.getItem() instanceof TaskEntry.Loaded loaded && loaded.id().equals("fix")
                            && c.getContextMenu() != null)
                    .findFirst().orElse(null);
        })), "the sorted-in task row");
        assertThat(callFx(() -> ((CheckMenuItem) item(row.getContextMenu(), "Sync groups", "team")).isSelected()))
                .isTrue();
    }

    private static MenuItem item(ContextMenu menu, String submenu, String text) {
        for (MenuItem item : menu.getItems()) {
            if (item instanceof Menu sub && submenu.equals(sub.getText())) {
                for (MenuItem inner : sub.getItems()) {
                    if (text.equals(inner.getText())) {
                        return inner;
                    }
                }
            }
        }
        throw new AssertionError("No " + submenu + " › " + text);
    }

    private static void git(String... args) {
        List<String> argv = new ArrayList<>(List.of("git", "-c", "user.name=Test",
                "-c", "user.email=test@example.org", "-c", "init.defaultBranch=main"));
        argv.addAll(List.of(args));
        LocalCommandRunner.LocalResult result = new LocalCommandRunner(Duration.ofSeconds(30)).run(argv);
        assertThat(result.ok()).as("git %s: %s", String.join(" ", args), result.stderr()).isTrue();
    }

    private static Object field(Object owner, Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T callQuietly(Supplier<T> body) {
        try {
            return callFx(body);
        } catch (Exception e) {
            return null;
        }
    }

    private static <T> T callFx(Supplier<T> body) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(body.get());
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }
}
