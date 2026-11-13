package com.contextswitcher.ui;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.contextswitcher.Main;

import javafx.scene.control.Button;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// UI automation of the local category header's sync button
/// (`dsn~local-category-session-import~1`): only the local category carries
/// it, and a click turns the Claude session recorded for the category's
/// folder — a folder with spaces in its path, as OneDrive's have — into a
/// suspended task holding that session — switching "Show running tasks only"
/// off, which would hide it, and selecting it; a second click adds nothing
/// more.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~local-category-session-import~1]
@Tag("ui")
@TestFxApplication(LocalCategorySessionSyncUiTest.TestApp.class)
class LocalCategorySessionSyncUiTest {

    private static final String SESSION = "d60a0a07-ed98-463d-93a1-f79ab3608f78";

    static Path home;
    static Path folder;
    static Path category;

    /// [Main] on a fresh config dir holding a local category (its
    /// `workspacesRoot` a folder named with spaces) and a remote one.
    public static class TestApp extends Main {

        @Override
        public void start(Stage stage) throws Exception {
            Path configDir = Files.createTempDirectory("cs-uitest");
            home = configDir.resolve("home");
            folder = configDir.resolve("OneDrive - Uni").resolve("TH Ulm");
            Files.createDirectories(folder);
            Path tasksDir = configDir.resolve("tasks");
            category = tasksDir.resolve("localcat");
            Path remoteCategory = tasksDir.resolve("remotecat");
            Files.createDirectories(category);
            Files.createDirectories(remoteCategory);
            Files.writeString(tasksDir.resolve(".gitkeep"), "");
            Files.writeString(category.resolve("CONTEXTSWITCHER.md"), """
                    ---
                    workspacesRoot: '%s'
                    ---
                    """.formatted(folder));
            Files.writeString(remoteCategory.resolve("CONTEXTSWITCHER.md"), """
                    ---
                    remote: devbox
                    workspacesRoot: /home/me/ws
                    ---
                    """);
            Files.writeString(configDir.resolve("settings.yaml"), """
                    tasksDir: %s
                    wsPort: %d
                    wsToken: uitest
                    remotes: []
                    """.formatted(tasksDir, freePort()));
            System.setProperty("contextswitcher.configDir", configDir.toString());
            // The running filter on, as in the field report: the import must
            // switch it off, or its suspended tasks stay out of sight.
            java.util.prefs.Preferences.userNodeForPackage(MainWindow.class)
                    .putBoolean(MainWindow.FILTER_RUNNING_TASKS, true);
            super.start(stage);
        }
    }

    @Test
    void theLocalCategorysSyncAddsItsRecordedSessionOnce() throws IOException {
        Path projects = home.resolve(".claude").resolve("projects")
                .resolve(folder.toString().replaceAll("[^A-Za-z0-9]", "-"));
        Files.createDirectories(projects);
        Files.writeString(projects.resolve(SESSION + ".jsonl"), """
                {"type":"user","message":{"role":"user","content":"What's next?"}}
                {"type":"ai-title","aiTitle":"Plan the next lecture"}
                """);
        // Two days old: the task must carry this, not the import's time.
        java.time.LocalDateTime sessionTime = java.time.LocalDateTime.now().minusDays(2).withSecond(0).withNano(0);
        java.time.Instant sessionInstant = sessionTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
        Files.setLastModifiedTime(projects.resolve(SESSION + ".jsonl"),
                java.nio.file.attribute.FileTime.from(sessionInstant));

        Button sync = await(() -> syncButtons().filter(LocalCategorySessionSyncUiTest::shown).findFirst(),
                "the local category's sync icon");
        assertThat(syncButtons().filter(LocalCategorySessionSyncUiTest::shown).count()).isEqualTo(1);

        String realHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            FX_ROBOT.mouse().moveTo(sync).click();
            Path task = category.resolve("plan-the-next-lecture.md");
            await(() -> Files.exists(task) ? Optional.of(true) : Optional.empty(), "the session's task file");
            assertThat(Files.readString(task))
                    .contains("title: Plan the next lecture")
                    .contains("status: suspended")
                    .contains("sessionId: " + SESSION)
                    .contains(sessionTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            assertThat(Files.getLastModifiedTime(task).toInstant()).isEqualTo(sessionInstant);
            await(() -> selectedTaskId().filter("localcat/plan-the-next-lecture"::equals),
                    "the imported task selected");
            assertThat(java.util.prefs.Preferences.userNodeForPackage(MainWindow.class)
                    .getBoolean(MainWindow.FILTER_RUNNING_TASKS, true)).isFalse();
            // The new row rebuilds the header, so the button is looked up
            // afresh — attached and enabled once the import settled.
            Button again = await(() -> syncButtons()
                    .filter(button -> shown(button) && !button.isDisabled()).findFirst(),
                    "the sync icon, enabled again");

            // Fired, not clicked: the first click's tooltip may sit over it.
            javafx.application.Platform.runLater(again::fire);
            await(() -> FX_ROBOT.selectNodes(javafx.scene.control.Labeled.class).fromAll()
                    .map(javafx.scene.control.Labeled::getText)
                    .filter(text -> text != null && text.startsWith("No new Claude sessions in "))
                    .findFirst(), "the nothing-new status line");
            try (Stream<Path> files = Files.list(category)) {
                assertThat(files.filter(file -> file.getFileName().toString().startsWith("plan-the-next-lecture")))
                        .hasSize(1);
            }
        } finally {
            System.setProperty("user.home", realHome);
            UiTestSupport.clearStoredFilters();
        }
    }

    /// Attached and visible all the way up — a `ListView` keeps rebuilt
    /// cells around invisible, buttons and all.
    private static boolean shown(javafx.scene.Node node) {
        if (node.getScene() == null) {
            return false;
        }
        for (javafx.scene.Node at = node; at != null; at = at.getParent()) {
            if (!at.isVisible()) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> selectedTaskId() {
        return FX_ROBOT.selectNodes(javafx.scene.control.ListView.class).fromAll()
                .map(view -> ((javafx.scene.control.ListView<Object>) view).getSelectionModel().getSelectedItem())
                .filter(com.contextswitcher.tasks.TaskEntry.Loaded.class::isInstance)
                .map(item -> ((com.contextswitcher.tasks.TaskEntry.Loaded) item).id())
                .findFirst();
    }

    private static Stream<Button> syncButtons() {
        return FX_ROBOT.selectNodes(Button.class).fromAll()
                .filter(button -> "group-sync-sessions-button".equals(button.getId()));
    }

    private static <T> T await(Supplier<Optional<T>> query, String what) {
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
