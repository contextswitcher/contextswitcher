package com.contextswitcher.ui;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import com.contextswitcher.Main;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;
import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static com.contextswitcher.ui.UiTestSupport.sleep;
import static io.gitlab.fxlabs.testfx.util.FxPredicates.hasText;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// Each task keeps the panes it had in front (`dsn~richtext-markdown-editor~10`):
/// Configuration put in front for task A gives way to the start layout's Notes
/// for task B, and comes back with A.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~richtext-markdown-editor~10]
@Tag("ui")
@TestFxApplication(PanesPerTaskUiTest.TestApp.class)
class PanesPerTaskUiTest {

    public static class TestApp extends Main {

        @Override
        public void start(Stage stage) throws Exception {
            Path configDir = Files.createTempDirectory("cs-uitest");
            Path tasksDir = configDir.resolve("tasks");
            Path category = tasksDir.resolve("cat");
            Files.createDirectories(category);
            for (String name : new String[] {"A", "B"}) {
                Files.writeString(category.resolve(name.toLowerCase() + ".md"), """
                        ---
                        title: Panes task %s
                        status: active
                        ---

                        # Notes %s
                        """.formatted(name, name));
            }
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
    void configurationStaysWithItsTask() {
        open("A");
        FX_ROBOT.mouse().moveTo(awaitPresent(() -> FX_ROBOT.selectNodes(Labeled.class).fromAll()
                .filter(hasText("Configuration")).findFirst(), "the Configuration tab")).click();
        awaitShown(true);

        open("B");
        awaitShown(false);

        open("A");
        awaitShown(true);
    }

    /// Waits until the Configuration pane's host field is (or is not) in front.
    private static void awaitShown(boolean shown) {
        awaitPresent(() -> FX_ROBOT.selectNodes(ComboBox.class).fromAll()
                .filter(node -> "field-remote".equals(node.getId()))
                .filter(node -> onFx(() -> treeVisible(node)) == shown)
                .findFirst(), "Configuration " + (shown ? "in front" : "behind"));
    }

    private static boolean treeVisible(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }

    /// Clicks the task row until the notes editor holds its body — the list
    /// rebuilds during startup and may detach the label aimed at.
    private static void open(String name) {
        RichTextArea editor = awaitPresent(() -> FX_ROBOT.selectNodes(RichTextArea.class).fromAll()
                .filter(area -> "notes-editor".equals(area.getId())).findFirst(), "notes editor");
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            FX_ROBOT.selectNodes(Label.class).fromAll().filter(hasText("Panes task " + name))
                    .filter(label -> onFx(() -> label.getScene() != null
                            && label.localToScreen(label.getBoundsInLocal()) != null))
                    .findFirst()
                    .ifPresent(row -> FX_ROBOT.mouse().moveTo(row).click());
            for (int attempt = 0; attempt < 10; attempt++) {
                if (onFx(() -> ((CodeTextModel) editor.getModel()).getPlainText(0)).contains("Notes " + name)) {
                    return;
                }
                sleep();
            }
        }
        throw new AssertionError("Task " + name + " never appeared in the editor");
    }

    private static <T> T onFx(Callable<T> action) {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        try {
            return task.get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
