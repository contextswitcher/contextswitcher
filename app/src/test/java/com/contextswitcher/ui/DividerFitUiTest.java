package com.contextswitcher.ui;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

import com.contextswitcher.Main;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.callOnFXThread;
import static org.assertj.core.api.Assertions.assertThat;

/// UI automation of `dsn~divider-fit-content~1`: a category name too long for
/// the default task list width is cut, and a double-click on the divider right
/// of the task list widens the pane until it is not.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~divider-fit-content~1]
@Tag("ui")
@TestFxApplication(DividerFitUiTest.TestApp.class)
class DividerFitUiTest {

    private static final String LONG_NAME = "a-category-name-far-too-long-for-the-default-task-list-width";

    /// [Main] on a fresh config dir holding one category with a long name.
    public static class TestApp extends Main {

        @Override
        public void start(Stage stage) throws Exception {
            Path configDir = Files.createTempDirectory("cs-uitest");
            Path tasksDir = configDir.resolve("tasks");
            Path category = tasksDir.resolve(LONG_NAME);
            Files.createDirectories(category);
            Files.writeString(tasksDir.resolve(".gitkeep"), "");
            Files.writeString(category.resolve("CONTEXTSWITCHER.md"), """
                    ---
                    repo: https://github.com/example/repo
                    ---
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
    void doubleClickOnTheDividerUncutsTheCategoryName() {
        await(() -> callOnFXThread(() -> missing() > 0 ? Optional.of(true) : Optional.<Boolean>empty()),
                "the long category name cut at the default width");

        Node divider = callOnFXThread(() -> {
            Bounds name = nameLabel().orElseThrow().localToScene(nameLabel().orElseThrow().getBoundsInLocal());
            // The first divider right of the name is the one beside the task list.
            return FX_ROBOT.selectNodes(Node.class, ".split-pane-divider").fromAll()
                    .filter(node -> node.localToScene(node.getBoundsInLocal()).getMinX() > name.getMinX())
                    .min(Comparator.comparingDouble(node -> node.localToScene(node.getBoundsInLocal()).getMinX()))
                    .orElseThrow();
        });
        FX_ROBOT.mouse().moveTo(divider).click().click();

        await(() -> callOnFXThread(() -> missing() <= 0 ? Optional.of(true) : Optional.<Boolean>empty()),
                "the category name shown in full after the double-click");
        assertThat(callOnFXThread(DividerFitUiTest::missing)).isLessThanOrEqualTo(0.0);
    }

    /// Pixels the category name label lacks for its full text.
    private static double missing() {
        return nameLabel().map(label -> label.prefWidth(-1) - label.getWidth()).orElse(Double.NaN);
    }

    private static Optional<Label> nameLabel() {
        return FX_ROBOT.selectNodes(Label.class).fromAll()
                .filter(label -> "group-name-label".equals(label.getId()) && LONG_NAME.equals(label.getText())
                        && DividerFit.isShown(label) && label.getWidth() > 0)
                .findFirst();
    }

    private static <T> T await(java.util.function.Supplier<Optional<T>> query, String what) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<T> hit = query.get();
            if (hit.isPresent()) {
                return hit.get();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Never saw " + what);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
