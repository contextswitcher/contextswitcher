package com.contextswitcher.ui;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.contextswitcher.Main;

import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// UI automation of the category header's play button
/// (`dsn~category-switch~1`): every named category carries it, and a click runs
/// a regular switch titled with the category's name whose chips are the
/// category's own actions — here its `folders:`, the `folder` chip.
/// Under Xvfb the desktop focus fails (no PowerShell); the switch still runs
/// after it, which is what keeps a failed desktop from swallowing the switch.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~category-switch~1]
@Tag("ui")
@TestFxApplication(CategorySwitchButtonUiTest.TestApp.class)
class CategorySwitchButtonUiTest {

    /// [Main] on a fresh config dir holding two categories, one naming `folders:`.
    public static class TestApp extends Main {

        @Override
        public void start(Stage stage) throws Exception {
            Path configDir = Files.createTempDirectory("cs-uitest");
            Path tasksDir = configDir.resolve("tasks");
            Path withFolders = tasksDir.resolve("acat");
            Path withoutFolders = tasksDir.resolve("bcat");
            Files.createDirectories(withFolders);
            Files.createDirectories(withoutFolders);
            Files.writeString(tasksDir.resolve(".gitkeep"), "");
            Files.writeString(withFolders.resolve("CONTEXTSWITCHER.md"), """
                    ---
                    folders:
                      - %s
                    ---
                    """.formatted(configDir));
            Files.writeString(withoutFolders.resolve("CONTEXTSWITCHER.md"), """
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
    void everyCategoryCarriesTheButtonAndItsClickRunsTheCategorysActions() {
        await(() -> switchButtons().count() == 2 ? Optional.of(true) : Optional.empty(),
                "a switch button on each of the two category headers");
        // Categories sort by name, so acat's header comes first.
        Button switchAcat = switchButtons().findFirst().orElseThrow();

        FX_ROBOT.mouse().moveTo(switchAcat).click();

        await(() -> labelStartingWith("acat:"), "the switch titled with the category");
        await(() -> labelStartingWith("folder "), "the folder chip of the category's folders");
    }

    private static Optional<String> labelStartingWith(String prefix) {
        return FX_ROBOT.selectNodes(Labeled.class).fromAll()
                .map(Labeled::getText)
                .filter(text -> text != null && text.startsWith(prefix))
                .findFirst();
    }

    private static java.util.stream.Stream<Button> switchButtons() {
        return FX_ROBOT.selectNodes(Button.class).fromAll()
                .filter(button -> "group-switch-button".equals(button.getId()));
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
