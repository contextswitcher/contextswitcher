package com.contextswitcher.ui;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

import com.contextswitcher.Main;

import java.util.concurrent.FutureTask;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ContextMenuEvent;
import javafx.stage.Stage;
import javafx.stage.Window;

import io.gitlab.fxlabs.testfx.api.NoNodeFoundException;

import org.jspecify.annotations.Nullable;

import static io.gitlab.fxlabs.testfx.util.FxPredicates.hasText;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// Shared plumbing for the TestFX UI tests (MADR 0014): the app-under-test
/// bootstrap and the polling helpers — robot tests are timing-sensitive, so
/// every lookup that can race the FX thread polls instead of asserting once.
final class UiTestSupport {

    /// The tasks dir of the most recently launched [TestApp].
    static Path tasksDir;

    /// The most recently launched app instance, for tests that need to drive
    /// state the robot cannot reach (e.g. pushing an active-desktop name).
    static Main app;

    private UiTestSupport() {
    }

    /// The app under test: [Main] booted on a fresh config dir. Fresh per
    /// launch — the TestFX extension relaunches the app for every test method,
    /// and a shared WebSocket port would collide with the previous instance's
    /// socket still going down. The tasks dir must be non-empty, or startup
    /// blocks in the modal "clone your task repository?" dialog.
    public static class TestApp extends Main {

        @Override
        public void start(Stage stage) throws Exception {
            Path configDir = Files.createTempDirectory("cs-uitest");
            tasksDir = configDir.resolve("tasks");
            Files.createDirectories(tasksDir);
            Files.writeString(tasksDir.resolve(".gitkeep"), "");
            Files.writeString(configDir.resolve("settings.yaml"), """
                    tasksDir: %s
                    wsPort: %d
                    wsToken: uitest
                    remotes: []
                    """.formatted(tasksDir, freePort()));
            System.setProperty("contextswitcher.configDir", configDir.toString());
            app = this;
            seedTasks(tasksDir);
            super.start(stage);
        }

        /// Writes task files the startup scan picks up — no file watcher
        /// needed. No-op by default.
        protected void seedTasks(Path tasksDir) throws Exception {
        }
    }

    static Optional<Button> findButton(String text) {
        return FX_ROBOT.selectNodes(Button.class).fromAll().filter(hasText(text)).findFirst();
    }

    /// A button by its text, waiting for it to appear — a dialog opens through
    /// a nested event loop, so its nodes are not there the instant the
    /// opening click returns.
    static Button awaitButton(String text) {
        return awaitPresent(() -> findButton(text), "button \"" + text + "\"");
    }

    static void clickButton(String text) {
        FX_ROBOT.mouse().moveTo(awaitButton(text)).click();
    }

    /// Opens the "Add task…" dialog from the toolbar's icon "add" menu button
    /// (id `add-menu`, `dsn~task-list-toolbar~3`). "Add task…" is a `MenuItem`
    /// there, not a `Button`, so its action is fired directly (the item text
    /// alone cannot be reached with a plain button lookup); the fire is
    /// fire-and-forget because the handler calls the dialog's blocking
    /// `showAndWait`. The dialog's description field (id `add-task-description`)
    /// is then focused explicitly — a fired action opens the dialog a beat
    /// before the field grabs focus, so without this the first keystrokes leak
    /// to the default button.
    static void openAddTask() {
        MenuButton add = awaitPresent(
                () -> FX_ROBOT.selectNodes(MenuButton.class).fromAll()
                        .filter(button -> "add-menu".equals(button.getId())).findFirst(),
                "add menu button");
        Platform.runLater(() -> add.getItems().stream()
                .filter(item -> "Add task…".equals(item.getText()))
                .findFirst().orElseThrow()
                .getOnAction().handle(new ActionEvent()));
        TextArea field = awaitPresent(
                () -> FX_ROBOT.selectNodes(TextArea.class).fromAll()
                        .filter(area -> "add-task-description".equals(area.getId())).findFirst(),
                "add-task description field");
        awaitFocused(field, "add-task description field");
    }

    /// Waits until `node` truly holds the keyboard focus, pulling its window
    /// to the front on every attempt. Two reasons to wait rather than to
    /// request focus once: a keystroke typed before focus settles leaks to
    /// whatever had it before (the Add-task dialog's default button, say), and
    /// a node's `isFocused` stays false while its window is unfocused — on a
    /// desktop in use, the robot would otherwise type into whatever the user
    /// left in front instead of into ours.
    static void awaitFocused(Node node, String what) {
        awaitPresent(() -> {
            Platform.runLater(() -> {
                if (node.getScene() != null && node.getScene().getWindow() instanceof Stage stage) {
                    stage.toFront();
                    stage.requestFocus();
                }
                node.requestFocus();
            });
            return node.isFocused() ? Optional.of(node) : Optional.empty();
        }, "the focused " + what);
    }

    /// Polls `query` until it yields a value. A query that finds no node of
    /// its type at all throws [NoNodeFoundException] — during a poll that just
    /// means "not yet", so it counts as empty. So does a
    /// [java.util.ConcurrentModificationException]: queries read the list's
    /// items from the test thread, and a poll can catch a rebuild mid-change.
    static <T> T awaitPresent(Supplier<Optional<T>> query, String what) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<T> result;
            try {
                result = query.get();
            } catch (NoNodeFoundException | java.util.ConcurrentModificationException e) {
                result = Optional.empty();
            }
            if (result.isPresent()) {
                return result.get();
            }
            sleep();
        }
        throw new AssertionError("No " + what + " appeared");
    }

    /// Task creation runs through the FX thread and the file system watcher;
    /// poll for the resulting `.md` file instead of racing it.
    static Path awaitTaskFileContaining(String marker) throws IOException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            try (Stream<Path> files = Files.walk(tasksDir)) {
                List<Path> hits = files
                        .filter(file -> file.getFileName().toString().endsWith(".md"))
                        .filter(file -> !file.getFileName().toString().equals("TEMPLATE.md"))
                        .filter(file -> readQuietly(file).contains(marker))
                        .toList();
                if (!hits.isEmpty()) {
                    return hits.getFirst();
                }
            }
            sleep();
        }
        throw new AssertionError("No task file containing \"" + marker + "\" in " + tasksDir);
    }

    /// Forgets the stored toolbar filters (`dsn~filter-persistence~1`). The
    /// `uiTest` prefs store outlives the app restart between test methods, so
    /// a filter one test class leaves on would narrow the next class's list;
    /// every test touching a filter clears them when it is done.
    static void clearStoredFilters() {
        Preferences prefs = Preferences.userNodeForPackage(MainWindow.class);
        for (String key : List.of(MainWindow.FILTER_AWAITS_INPUT, MainWindow.FILTER_RUNNING_TASKS,
                MainWindow.FILTER_ACTIVE_DESKTOP, MainWindow.FILTER_NO_DESKTOP_CATEGORIES,
                MainWindow.FILTER_TAGS, MainWindow.FILTER_RECENT_TAGS, MainWindow.GROUPING)) {
            prefs.remove(key);
        }
        try {
            MainWindow.LAST_TASK_BY_DESKTOP.clear();
        } catch (java.util.prefs.BackingStoreException e) {
            throw new IllegalStateException(e);
        }
    }

    /// Right-clicks `node` at the local point, picks "Copy path" from the menu
    /// that opens and returns what landed on the clipboard — null when no such
    /// menu opened (`dsn~attachment-copy-path~1`).
    static @Nullable String copyPathAt(Node node, double x, double y) {
        FutureTask<@Nullable String> task = new FutureTask<>(() -> {
            Clipboard.getSystemClipboard().clear();
            // The event's x/y are scene coordinates until dispatch maps them
            // into the target's local space.
            Point2D scene = node.localToScene(x, y);
            Point2D screen = node.localToScreen(x, y);
            Event.fireEvent(node, new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                    scene.getX(), scene.getY(), screen.getX(), screen.getY(), false, null));
            List<MenuItem> items = Window.getWindows().stream()
                    .filter(window -> window instanceof ContextMenu && window.isShowing())
                    .flatMap(window -> ((ContextMenu) window).getItems().stream())
                    .filter(item -> "Copy path".equals(item.getText()))
                    .toList();
            if (items.isEmpty()) {
                return null;
            }
            items.getFirst().fire();
            Window.getWindows().stream().filter(ContextMenu.class::isInstance).toList()
                    .forEach(Window::hide);
            return Clipboard.getSystemClipboard().getString();
        });
        Platform.runLater(task);
        try {
            return task.get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String readQuietly(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            return "";
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
