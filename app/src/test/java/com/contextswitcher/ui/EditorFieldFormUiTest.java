package com.contextswitcher.ui;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.function.Predicate;

import com.contextswitcher.Main;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
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
import static org.assertj.core.api.Assertions.assertThat;

/// UI automation of the Configuration pane (`dsn~task-field-form~4`): the notes
/// header's configure button brings the pane forward, its form is generated from
/// the field catalog and pre-filled from the open file, **Raw YAML** shows the
/// same frontmatter as YAML, and **Apply** writes back only what changed —
/// comments included. The rebuilt form shows what was written, and clearing a
/// field removes its key.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~task-field-form~4]
@Tag("ui")
@TestFxApplication(EditorFieldFormUiTest.TestApp.class)
class EditorFieldFormUiTest {

    private static final String TITLE = "Field form task";
    private static volatile Path taskFile;

    /// [Main] on a fresh config dir holding one task that already names a
    /// remote (the form must show it) and carries a comment (which must
    /// survive the write).
    public static class TestApp extends Main {

        @Override
        public void start(Stage stage) throws Exception {
            Path configDir = Files.createTempDirectory("cs-uitest");
            Path tasksDir = configDir.resolve("tasks");
            Files.createDirectories(tasksDir);
            taskFile = tasksDir.resolve("form.md");
            Files.writeString(taskFile, """
                    ---
                    title: %s
                    status: active
                    remote: devbox
                    # keep me
                    ---

                    # Notes

                    """.formatted(TITLE));
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
    void theFormFillsClearsAndShowsTheFrontmatterAsFields() throws Exception {
        openTaskInEditor();
        openForm();

        TextField remote = awaitControl(TextField.class, "field-remote");
        // Pre-filled with what the file configures; untouched fields stay empty.
        assertThat(text(remote)).isEqualTo("devbox");
        assertThat(text(awaitControl(TextField.class, "field-chat"))).isEmpty();

        // The raw view is the fallback: the same frontmatter as YAML, comment
        // and all. Switching back rebuilds the form from it.
        clickById("config-raw-toggle");
        assertThat(richText(awaitControl(RichTextArea.class, "config-editor"))).contains("# keep me");
        clickById("config-raw-toggle");

        set(awaitControl(TextField.class, "field-chat"), "https://matrix.to/#/!room:matrix.org");
        set(awaitControl(TextArea.class, "field-browser-urls"),
                "https://example.org/pr/1 — The PR");
        set(awaitControl(TextArea.class, "field-folders"), "/data/koppor/p");
        check(awaitControl(CheckBox.class, "field-intellij"));
        clickById("config-apply-button");

        String written = awaitContent("matrix.to");
        assertThat(written)
                .contains("https://example.org/pr/1")
                .contains("The PR")
                .contains("intellij:")
                .contains("/data/koppor/p")
                .contains("remote: devbox")
                .contains("# keep me")
                // Nothing was typed here, so no key appeared for it.
                .doesNotContain("note:");

        // The form is rebuilt from what was written, and an emptied field
        // removes its key — the form edits the file, it does not only add to it.
        assertThat(text(awaitControl(TextArea.class, "field-browser-urls")))
                .isEqualTo("https://example.org/pr/1 — The PR");
        set(awaitControl(TextField.class, "field-remote"), "");
        clickById("config-apply-button");

        String cleared = awaitAbsent("remote:");
        assertThat(cleared).contains("chat: ").contains("# keep me").contains("title: " + TITLE);
    }

    private static void openForm() {
        FX_ROBOT.mouse().moveTo(awaitPresent(
                () -> FX_ROBOT.selectNodes(Button.class).fromAll()
                        .filter(button -> "add-field-button".equals(button.getId())).findFirst(),
                "the configure button")).click();
    }

    /// The Configuration pane's buttons are icon-only, so they are found by id.
    private static void clickById(String id) {
        FX_ROBOT.mouse().moveTo(awaitControl(ButtonBase.class, id)).click();
    }

    private static String text(TextInputControl field) {
        return onFx(field::getText);
    }

    private static String richText(RichTextArea area) {
        return onFx(() -> {
            CodeTextModel model = (CodeTextModel) area.getModel();
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < model.size(); i++) {
                text.append(model.getPlainText(i)).append('\n');
            }
            return text.toString();
        });
    }

    private static void set(TextInputControl field, String value) {
        onFx(() -> {
            field.setText(value);
            return null;
        });
    }

    private static void check(CheckBox box) {
        onFx(() -> {
            box.setSelected(true);
            return null;
        });
    }

    private static <T extends Node> T awaitControl(Class<T> type, String id) {
        return awaitPresent(() -> FX_ROBOT.selectNodes(type).fromAll()
                .filter(node -> id.equals(node.getId())).findFirst(), "control " + id);
    }

    /// Clicks the task row until the notes editor holds the body — the same
    /// race the other editor tests fight: the list rebuilds during startup and
    /// detaches the label the click was aimed at.
    private static void openTaskInEditor() {
        RichTextArea editor = awaitPresent(() -> FX_ROBOT.selectNodes(RichTextArea.class).fromAll()
                .filter(area -> "notes-editor".equals(area.getId())).findFirst(), "notes editor");
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            FX_ROBOT.selectNodes(Label.class).fromAll().filter(hasText(TITLE))
                    .filter(label -> onFx(() -> label.getScene() != null
                            && label.localToScreen(label.getBoundsInLocal()) != null))
                    .findFirst()
                    .ifPresent(row -> FX_ROBOT.mouse().moveTo(row).click());
            for (int attempt = 0; attempt < 10; attempt++) {
                if (richText(editor).contains("# Notes")) {
                    return;
                }
                sleep();
            }
        }
        throw new AssertionError("The task file never appeared in the editor");
    }

    private static String awaitContent(String marker) throws IOException {
        return await(content -> content.contains(marker), "got \"" + marker + "\"");
    }

    private static String awaitAbsent(String marker) throws IOException {
        return await(content -> !content.contains(marker), "lost \"" + marker + "\"");
    }

    private static String await(Predicate<String> done, String what)
            throws IOException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            String content = Files.readString(taskFile);
            if (done.test(content)) {
                return content;
            }
            sleep();
        }
        throw new AssertionError("The task file never " + what + ": "
                + Files.readString(taskFile));
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
