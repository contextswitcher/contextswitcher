package com.contextswitcher.ui;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.contextswitcher.Main;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// UI automation of the category header's repository icon
/// (`dsn~group-repo-open~2`): the icon shows for the category whose
/// `CONTEXTSWITCHER.md` carries a `repo:` URL and for no other — two categories
/// render, only one has the key, so exactly one icon may exist — and hovering
/// it names the URL in the status bar and turns the cursor into a hand.
/// The click itself is not automated — it hands the URL to the OS, which would
/// open a real browser on the build machine.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `xvfb-run -a` headless.
// [utest->dsn~group-repo-open~2]
@Tag("ui")
@TestFxApplication(GroupRepoIconUiTest.TestApp.class)
class GroupRepoIconUiTest {

    private static final String REPO = "https://github.com/JabRef/html-to-node";

    /// [Main] on a fresh config dir holding two categories — one with a `repo:`,
    /// one without — so the icon's presence is attributable to the key.
    public static class TestApp extends Main {

        @Override
        public void start(Stage stage) throws Exception {
            Path configDir = Files.createTempDirectory("cs-uitest");
            Path tasksDir = configDir.resolve("tasks");
            Path category = tasksDir.resolve("repocat");
            Path plain = tasksDir.resolve("plaincat");
            Files.createDirectories(category);
            Files.createDirectories(plain);
            Files.writeString(tasksDir.resolve(".gitkeep"), "");
            Files.writeString(plain.resolve("CONTEXTSWITCHER.md"), """
                    ---
                    desktop: plaincat
                    ---
                    """);
            Files.writeString(category.resolve("CONTEXTSWITCHER.md"), """
                    ---
                    repo: %s
                    ---
                    """.formatted(REPO));
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
    void onlyTheCategoryWithARepoUrlShowsTheRepositoryIcon() {
        await(() -> repoButtons().findFirst(), "the category's repository icon");
        assertThat(repoButtons().count()).isEqualTo(1);
    }

    @Test
    void hoveringTheIconNamesTheUrlInTheStatusBarAndShowsAHandCursor() {
        Button repo = await(() -> repoButtons().findFirst(), "the category's repository icon");
        assertThat(repo.getCursor()).isEqualTo(javafx.scene.Cursor.HAND);
        // Before the hover nothing shows the bare URL (the tooltip reads
        // "Open <url>"), so the assertion below cannot pass by accident.
        assertThat(labelsReading(REPO)).isEmpty();
        FX_ROBOT.mouse().moveTo(repo);
        String hovered = await(() -> labelsReading(REPO).stream().findFirst(),
                "the URL in the status bar");
        assertThat(hovered).isEqualTo(REPO);
    }

    private static java.util.List<String> labelsReading(String text) {
        return FX_ROBOT.selectNodes(Label.class).fromAll()
                .map(Label::getText)
                .filter(text::equals)
                .toList();
    }

    private static java.util.stream.Stream<Button> repoButtons() {
        return FX_ROBOT.selectNodes(Button.class).fromAll()
                .filter(button -> "group-repo-button".equals(button.getId()));
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
