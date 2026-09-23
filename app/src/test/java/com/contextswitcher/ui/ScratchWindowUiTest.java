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

/// UI automation of the category header's terminal icon
/// (`dsn~category-scratch-window~2`): clicking it must reach the remote and
/// re-enable itself once a failed attempt settles (never stuck grayed out).
/// The category points at a host that cannot resolve, so the observable
/// outcome is the "Cannot open a window on …" alert — which a click that never
/// fires (the button was a dead glyph once) would never produce.
/// Against this fast-failing host the whole round-trip (ssh + the button's
/// gray-out and re-enable) settles inside TestFX's own event-pump wait, so the
/// transient grayed-out state itself is not independently observable here —
/// only that the button ends up re-enabled, not stuck.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `xvfb-run -a` headless.
// [utest->dsn~category-scratch-window~2]
@Tag("ui")
@TestFxApplication(ScratchWindowUiTest.TestApp.class)
class ScratchWindowUiTest {

    private static final String HOST = "cs-uitest-no-such-host.invalid";

    /// [Main] on a fresh config dir holding one remote category, so the list
    /// renders a group header with the terminal icon (see [AddTaskDialogUiTest]
    /// for why the config dir and port are per-launch).
    public static class TestApp extends Main {

        @Override
        public void start(Stage stage) throws Exception {
            Path configDir = Files.createTempDirectory("cs-uitest");
            Path tasksDir = configDir.resolve("tasks");
            Path category = tasksDir.resolve("remotecat");
            Files.createDirectories(category);
            Files.writeString(tasksDir.resolve(".gitkeep"), "");
            Files.writeString(category.resolve("CONTEXTSWITCHER.md"), """
                    ---
                    remote: %s
                    ---
                    """.formatted(HOST));
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
    void clickingTheCategoryTerminalIconOpensARemoteWindow() {
        Button scratch = await(() -> FX_ROBOT.selectNodes(Button.class).fromAll()
                .filter(button -> "scratch-window-button".equals(button.getId()))
                .findFirst(), "the category's terminal icon");
        FX_ROBOT.mouse().moveTo(scratch).click();
        // ssh runs off the FX thread against an unresolvable host; the alert
        // follows on it, so poll rather than race (ssh's own timeout is 10s).
        String alert = await(() -> FX_ROBOT.selectNodes(Labeled.class).fromAll()
                .map(Labeled::getText)
                .filter(text -> text != null && text.startsWith("Cannot open a window on"))
                .findFirst(), "the failure alert");
        assertThat(alert).contains(HOST);
        // The failure still settles the attempt — the button must not stay
        // grayed out forever.
        await(() -> scratch.isDisabled() ? Optional.empty() : Optional.of(true),
                "the button to re-enable after the failure");
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
