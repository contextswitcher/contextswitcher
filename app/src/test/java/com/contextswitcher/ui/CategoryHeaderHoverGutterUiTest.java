package com.contextswitcher.ui;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.contextswitcher.Main;

import javafx.scene.control.Button;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// UI automation of the category header's hover gutter
/// (`dsn~category-header-hover-actions~1`): away from the header only play
/// shows; on the header the repository icon appears, just left of play.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~category-header-hover-actions~1]
@Tag("ui")
@TestFxApplication(CategoryHeaderHoverGutterUiTest.TestApp.class)
class CategoryHeaderHoverGutterUiTest {

    /// [Main] on a fresh config dir holding one category with a `repo:`.
    public static class TestApp extends Main {

        @Override
        public void start(Stage stage) throws Exception {
            Path configDir = Files.createTempDirectory("cs-uitest");
            Path tasksDir = configDir.resolve("tasks");
            Path category = tasksDir.resolve("acat");
            Files.createDirectories(category);
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
    void onlyPlayShowsUntilTheHeaderIsHovered() {
        Button repo = await(() -> buttonWithId("group-repo-button"), "the category's repository icon");
        Button play = await(() -> buttonWithId("group-switch-button"), "the category's play button");
        // Off the header: the find field, which every window has.
        FX_ROBOT.mouse().moveTo(FX_ROBOT.selectNodes(TextField.class).fromAll().findFirst().orElseThrow());
        await(() -> shown(repo) ? Optional.empty() : Optional.of(true), "the repository icon hidden");
        assertThat(shown(play)).isTrue();

        FX_ROBOT.mouse().moveTo(play);

        await(() -> shown(repo) ? Optional.of(true) : Optional.empty(), "the repository icon on hover");
        assertThat(repo.localToScene(repo.getBoundsInLocal()).getMaxX())
                .isLessThanOrEqualTo(play.localToScene(play.getBoundsInLocal()).getMinX());
    }

    /// Visible along the whole parent chain — the gutter hides, not the button.
    private static boolean shown(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }

    private static Optional<Button> buttonWithId(String id) {
        return FX_ROBOT.selectNodes(Button.class).fromAll()
                .filter(button -> id.equals(button.getId()))
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
