package com.contextswitcher.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javax.imageio.ImageIO;

import com.contextswitcher.Main;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.image.ImageView;
import javafx.stage.PopupWindow;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;
import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static com.contextswitcher.ui.UiTestSupport.sleep;
import static io.gitlab.fxlabs.testfx.util.FxPredicates.hasText;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// UI automation of the editor lane's attachment preview
/// (`dsn~attachment-image-hover~2`): a task whose body is nothing but
/// `[image: …]` markers is opened in the editor, the robot walks the pointer
/// across the marker text, and a popup carrying the picture must appear — then
/// vanish once the pointer leaves the editor.
///
/// The pointer is walked over a grid rather than aimed at one computed
/// coordinate: the exact screen position of a marker depends on the lane
/// width, font and wrapping, none of which the test should pin down.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `xvfb-run -a` headless.
// [utest->dsn~attachment-image-hover~2]
// [utest->dsn~attachment-copy-path~1]
@Tag("ui")
@TestFxApplication(EditorImagePreviewUiTest.TestApp.class)
class EditorImagePreviewUiTest {

    private static final String TITLE = "Screenshot task";

    /// The launched app's tasks directory — read by the failure diagnostic, so
    /// a report says whether the task file was still on disk.
    private static volatile @Nullable Path launchedTasksDir;

    /// [Main] on a fresh config dir holding one task whose body is 40 lines of
    /// image markers pointing at a real PNG next to it.
    public static class TestApp extends Main {

        @Override
        public void start(Stage stage) throws Exception {
            Path configDir = Files.createTempDirectory("cs-uitest");
            Path tasksDir = configDir.resolve("tasks");
            launchedTasksDir = tasksDir;
            Files.createDirectories(tasksDir);
            Path png = tasksDir.resolve("shot.png");
            ImageIO.write(new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB),
                    "png", png.toFile());
            Files.writeString(tasksDir.resolve("shot.md"), """
                    ---
                    title: %s
                    status: active
                    ---

                    """.formatted(TITLE) + ("[image: " + png + "]\n").repeat(40));
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
    void hoveringAnImageMarkerPopsThePictureUpAndLeavingHidesItAgain() {
        awaitPresent(EditorImagePreviewUiTest::taskRow, "task row");

        // The markers live in the notes tab (the body), which is also the
        // one shown selected by default.
        RichTextArea editor = awaitPresent(
                () -> FX_ROBOT.selectNodes(RichTextArea.class).fromAll()
                        .filter(area -> "notes-editor".equals(area.getId())).findFirst(),
                "notes editor");
        openTaskInEditor(editor);
        onFx(() -> {
            Node node = editor;
            while (!(node instanceof TabPane tabs)) {
                node = node.getParent();
            }
            tabs.getSelectionModel().select(0);
            return null;
        });

        assertThat(walkOverMarkers(editor)).as("preview popup while hovering a marker").isTrue();

        // Out of the editor, into the lane left of it — by coordinate, not by
        // node: the clicked row's label may have been recycled by then.
        Bounds bounds = onFx(() -> editor.localToScreen(editor.getBoundsInLocal()));
        FX_ROBOT.mouse().moveTo(bounds.getMinX() - 40, bounds.getMinY() + 40);
        assertThat(awaitPreview(false)).as("preview popup gone after leaving the editor").isFalse();

        // Every line is a marker, so the first point that hits text will do.
        @Nullable String copied = null;
        for (double y = 12; copied == null && y < bounds.getHeight() - 12; y += 14) {
            copied = UiTestSupport.copyPathAt(editor, 40, y);
        }
        assertThat(copied).as("right-click on a marker copies its path")
                .isEqualTo(Objects.requireNonNull(launchedTasksDir).resolve("shot.png").toString());
    }

    /// The task row's title label, but only while it is really on screen.
    ///
    /// The scene check is the point: a row's labels are rebuilt whenever the
    /// list rebuilds, and the discarded ones stay reachable through the node
    /// lookup while no longer belonging to any scene. Aiming the robot at such
    /// a label is a click into nothing — it has no screen position left.
    private static Optional<Label> taskRow() {
        return FX_ROBOT.selectNodes(Label.class).fromAll()
                .filter(hasText(TITLE))
                .filter(label -> onFx(() -> label.getScene() != null
                        && label.localToScreen(label.getBoundsInLocal()) != null))
                .findFirst();
    }

    /// Clicks the task row until its file shows up in the editor lane.
    ///
    /// Selecting the task loads the file synchronously on the FX thread, so a
    /// click that landed is visible almost at once — but the app is still
    /// starting up, and each rebuild of the list detaches the label the click
    /// was aimed at. Re-resolving the row per attempt is what makes this
    /// independent of whether the machine finished starting the app before or
    /// after the test first looked (it reliably lost that race on CI and
    /// reliably won it on a developer box).
    private static void openTaskInEditor(RichTextArea editor) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            taskRow().ifPresent(row -> FX_ROBOT.mouse().moveTo(row).click());
            for (int attempt = 0; attempt < 10; attempt++) {
                if (onFx(() -> ((CodeTextModel) editor.getModel()).size()) > 5) {
                    return;
                }
                sleep();
            }
        }
        throw new AssertionError("The task file never appeared in the editor. "
                + describeScene(taskRow().orElse(null)));
    }

    /// What the app actually showed when an expectation timed out.
    private static String describeScene(@Nullable Label title) {
        return onFx(() -> {
            StringBuilder out = new StringBuilder();
            out.append("clicked row: ");
            if (title == null) {
                out.append("<no on-screen row carrying the title>");
            } else {
                out.append("text=").append(title.getText())
                        .append(" visible=").append(title.isVisible())
                        .append(" inScene=").append(title.getScene() != null)
                        .append(" atScreen=").append(title.localToScreen(title.getBoundsInLocal()));
            }
            out.append("\n  screen=").append(Screen.getPrimary().getVisualBounds());
            for (Window window : Window.getWindows()) {
                out.append("\n  window ").append(window.getClass().getSimpleName())
                        .append(" showing=").append(window.isShowing())
                        .append(" focused=").append(window.isFocused())
                        .append(" at ").append(window.getX()).append(',').append(window.getY())
                        .append(' ').append(window.getWidth()).append('x').append(window.getHeight());
            }
            out.append("\n  labels=").append(FX_ROBOT.selectNodes(Label.class).fromAll()
                    .map(Label::getText).toList());
            // The rendered rows themselves: an empty list tells a repository
            // that loaded nothing apart from a filter that hid everything.
            FX_ROBOT.selectNodes(ListView.class).fromAll().forEach(list -> {
                List<?> items = list.getItems();
                out.append("\n  listView id=").append(list.getId())
                        .append(" items=").append(items.size())
                        .append(' ').append(items.stream()
                                .map(item -> item == null ? "null" : item.getClass().getSimpleName())
                                .toList());
            });
            out.append("\n  tasksDir=").append(describeTasksDir());
            out.append("\n  appLog=").append(tailOfAppLog());
            return out.toString();
        });
    }

    /// The tasks directory as it stands at failure time: an empty task list can
    /// mean the file went away, or that it stopped parsing.
    private static String describeTasksDir() {
        Path dir = launchedTasksDir;
        if (dir == null) {
            return "<app never started>";
        }
        try (var entries = Files.list(dir)) {
            return dir + " -> " + entries
                    .map(path -> path.getFileName() + "(" + sizeOf(path) + ")")
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return dir + " -> unreadable: " + e;
        }
    }

    private static String sizeOf(Path path) {
        try {
            return Files.size(path) + "B";
        } catch (IOException e) {
            return "?";
        }
    }

    /// Tail of the app's own log — a task that stopped parsing says so there
    /// (`Cannot parse task file …`) and nowhere else.
    private static String tailOfAppLog() {
        Path log = Path.of(System.getProperty("user.home"), ".contextswitcher", "logs",
                "contextswitcher_0.log");
        try {
            List<String> lines = Files.readAllLines(log);
            return String.join("\n    ", lines.subList(Math.max(0, lines.size() - 25), lines.size()));
        } catch (IOException e) {
            return "unreadable (" + log + "): " + e;
        }
    }

    /// Walks the pointer across the editor's text area until the preview shows.
    private static boolean walkOverMarkers(RichTextArea editor) {
        Bounds bounds = onFx(() -> editor.localToScreen(editor.getBoundsInLocal()));
        for (double y = bounds.getMinY() + 12; y < bounds.getMaxY() - 12; y += 14) {
            for (double x = bounds.getMinX() + 12; x < bounds.getMaxX() - 12; x += 30) {
                FX_ROBOT.mouse().moveTo(x, y);
                if (awaitPreview(true)) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Polls the popup state briefly — the mouse event is delivered on the FX
    /// thread, so the popup is not up the instant `moveTo` returns.
    private static boolean awaitPreview(boolean expected) {
        for (int attempt = 0; attempt < 5; attempt++) {
            if (previewShowing() == expected) {
                return expected;
            }
            sleep();
        }
        return !expected;
    }

    private static boolean previewShowing() {
        return onFx(() -> Window.getWindows().stream()
                .filter(window -> window instanceof PopupWindow && window.isShowing())
                .map(Window::getScene)
                .filter(Objects::nonNull)
                .map(Scene::getRoot)
                .anyMatch(EditorImagePreviewUiTest::hasImage));
    }

    private static boolean hasImage(Node node) {
        if (node instanceof ImageView view) {
            return view.getImage() != null;
        }
        return node instanceof Parent parent
                && parent.getChildrenUnmodifiable().stream()
                        .anyMatch(EditorImagePreviewUiTest::hasImage);
    }

    /// Reads scene-graph state on the FX thread — [Window#getWindows] and the
    /// bounds queries are not safe from the test thread.
    private static <T> T onFx(Callable<T> query) {
        FutureTask<T> task = new FutureTask<>(query);
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
