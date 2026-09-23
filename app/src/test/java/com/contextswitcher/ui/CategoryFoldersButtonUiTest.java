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
import static org.assertj.core.api.Assertions.assertThat;

/// UI automation of the category header's folder icon
/// (`dsn~category-folders-button~1`): it shows only for a category that names
/// `folders:`, and clicking it runs the open attempt to a reported end instead
/// of leaving the button grayed out.
/// What the attempt does differs per platform (Explorer via PowerShell on
/// Windows, `Desktop.open` elsewhere), so what is asserted here is the part
/// that does not: the button's presence rule, that the click fires at all, and
/// that a settled round-trip re-enables it and lands a `Folders: …` /
/// `Cannot open folders: …` line in the status bar — under Xvfb, with no file
/// manager registered, that is the failure wording.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~category-folders-button~1]
@Tag("ui")
@TestFxApplication(CategoryFoldersButtonUiTest.TestApp.class)
class CategoryFoldersButtonUiTest {

    /// [Main] on a fresh config dir holding two categories — one naming
    /// `folders:`, one not — so the header renders the button exactly once.
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
    void onlyTheCategoryWithFoldersCarriesTheButtonAndItsClickSettles() {
        Button openFolders = await(() -> foldersButtons().findFirst(), "the category's folder icon");
        // Two categories, one `folders:` — so exactly one button, not one per header.
        assertThat(foldersButtons().count()).isEqualTo(1);

        FX_ROBOT.mouse().moveTo(openFolders).click();

        // The open runs off the FX thread, so poll for the outcome line.
        String message = await(() -> FX_ROBOT.selectNodes(Labeled.class).fromAll()
                .map(Labeled::getText)
                .filter(text -> text != null
                        && (text.startsWith("Folders: ") || text.startsWith("Cannot open folders: ")))
                .findFirst(), "the status-bar outcome");
        assertThat(message).isNotBlank();
        await(() -> openFolders.isDisabled() ? Optional.empty() : Optional.of(true),
                "the button to re-enable once the attempt settled");
    }

    private static java.util.stream.Stream<Button> foldersButtons() {
        return FX_ROBOT.selectNodes(Button.class).fromAll()
                .filter(button -> "group-folders-button".equals(button.getId()));
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
