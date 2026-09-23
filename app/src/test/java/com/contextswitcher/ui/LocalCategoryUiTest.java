package com.contextswitcher.ui;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.contextswitcher.Main;
import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.tasks.TaskEntry;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// UI automation of the **local category** half of the Add-task dialog
/// (`dsn~task-create-local~4`): with a task of a local category selected — a
/// CS config with a `workspacesRoot` and no `remote` — `Add local Claude` is
/// enabled, is the dialog's default button, and the hint names what the click
/// will actually do on **this** platform: an app-owned session on Windows, a
/// window in the local tmux server (with its attach command) everywhere else.
///
/// The click itself is deliberately not fired: it would start a real Claude.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~task-create-local~4]
@Tag("ui")
@TestFxApplication(LocalCategoryUiTest.TestApp.class)
class LocalCategoryUiTest {

    static Path workspaces;

    /// [Main] on a fresh config dir holding one **local** category (a
    /// `workspacesRoot`, no `remote`) with one task in it.
    public static class TestApp extends Main {

        @Override
        public void start(Stage stage) throws Exception {
            Path configDir = Files.createTempDirectory("cs-uitest");
            Path tasksDir = configDir.resolve("tasks");
            Path category = tasksDir.resolve("localcat");
            workspaces = configDir.resolve("workspaces");
            Files.createDirectories(category);
            Files.writeString(tasksDir.resolve(".gitkeep"), "");
            Files.writeString(category.resolve("CONTEXTSWITCHER.md"), """
                    ---
                    workspacesRoot: %s
                    ---
                    """.formatted(workspaces));
            Files.writeString(category.resolve("demo.md"), """
                    ---
                    title: demo task
                    status: active
                    ---

                    # Notes
                    """);
            Files.writeString(configDir.resolve("settings.yaml"), """
                    tasksDir: %s
                    wsPort: %d
                    wsToken: uitest
                    remotes: []
                    """.formatted(tasksDir, freePort()));
            System.setProperty("contextswitcher.configDir", configDir.toString());
            super.start(stage);
        }
    }

    @Test
    void localClaudeIsTheDefaultAndTheHintNamesThisPlatformsTerminal() {
        selectTheCategorysTask();
        UiTestSupport.openAddTask();

        Button local = UiTestSupport.awaitButton("Add local Claude");
        assertThat(local.isDisabled()).isFalse();
        assertThat(local.isDefaultButton()).isTrue();
        assertThat(UiTestSupport.awaitButton("Add remote Claude").isDisabled()).isTrue();

        String hint = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(Label.class).fromAll()
                        .map(Label::getText)
                        .filter(text -> text != null && text.startsWith("Local Claude opens"))
                        .findFirst(),
                "the local-category hint");
        assertThat(hint).contains(workspaces.toString());
        if (LocalCommandRunner.onWindows()) {
            assertThat(hint).contains("this app's terminal pane");
        } else {
            assertThat(hint).contains("a tmux window").contains("tmux attach -t 0");
        }
    }

    /// Selects the category's task, so the dialog opens for that group — the
    /// toolbar entry adds to the selected group.
    @SuppressWarnings("unchecked")
    private static void selectTheCategorysTask() {
        ListView<Object> list = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(),
                "the task ListView");
        UiTestSupport.awaitPresent(() -> {
            int index = indexOf(list, "localcat/demo");
            if (index < 0) {
                return Optional.empty();
            }
            Platform.runLater(() -> list.getSelectionModel().select(index));
            return Optional.of(index);
        }, "the category's task row");
    }

    private static int indexOf(ListView<Object> list, String id) {
        for (int i = 0; i < list.getItems().size(); i++) {
            if (list.getItems().get(i) instanceof TaskEntry.Loaded loaded && loaded.id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
