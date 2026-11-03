package com.contextswitcher.ui;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javafx.application.Platform;
import javafx.scene.control.ListView;

import com.contextswitcher.Main;
import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;
import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// A task-file rename must not let the editor lane write the file it was
/// showing back under the **old** name.
///
/// The title adoption (`dsn~claude-title-sync~3`) saves the new title, moves
/// the file and calls `taskRenamed`. That used to select the renamed task
/// first, whose `openInEditor` committed the config forms and saved the editor
/// — still bound to the old file name and holding the pre-adoption buffer —
/// before the editor was retargeted. The old file reappeared as a second row
/// driving the same tmux window and Claude session. Field report 2026-11-03:
/// "the two plantuml tasks show the same claude remote".
// [utest->dsn~task-move-dnd~6]
// [utest->dsn~richtext-markdown-editor~9]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class RenameEditorResurrectUiTest {

    private static final String FROM = "resurrect-placeholder";
    private static final String TO = "resurrect-adopted";

    /// The incident's file: the description-derived placeholder title, folded
    /// over several lines, as the live-create flow writes it.
    private static final String CONTENT = """
            ---
            title: Indeppentend of this at repo https://github.com/koppor/plantuml1. update github
              actions version to SHA (dependabot compatible)2. check why not building any more
              see - https://github.com/koppor/plantuml/actions/runs/33364129867/job/99401179915?pr=84
            status: active
            remote: koppor@swkgpu1
            claude:
              workspace: /data/kopp/latextemplates
              sessionId: af6e82d5-b5f7-4626-b429-83986ebc71eb
              cwd: /data/kopp/latextemplates
            browser:
              urls:
                - https://github.com/koppor/plantuml
            tmux:
              session: "old-group"
              window: "@100"   # created 2026-11-02
            ---

            # Notes

            Indeppentend of this at repo https://github.com/koppor/plantuml
            """;

    /// The rename lands while the editor holds an unsaved change of the file
    /// being renamed — the selection `taskRenamed` makes must not flush it to
    /// the old name.
    @Test
    void aRenameDoesNotWriteTheOldFileBack() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.writeString(dir.resolve(FROM + ".md"), CONTENT);
        openInEditor(FROM);
        dirtyNotesEditor();

        // As adoptPublishedTitles does it: new title on disk, then the move.
        Files.writeString(dir.resolve(FROM + ".md"), CONTENT.replaceFirst(
                "(?s)title: .*?\nstatus:", "title: plantuml actions SHA + build fix\nstatus:"));
        Files.move(dir.resolve(FROM + ".md"), dir.resolve(TO + ".md"));
        runFx(() -> mainWindow().taskRenamed(FROM, TO));
        awaitSelected(TO);
        UiTestSupport.sleep();

        assertThat(Files.exists(dir.resolve(FROM + ".md")))
                .as("the file the task left was not written back")
                .isFalse();
        assertThat(Files.readString(dir.resolve(TO + ".md")))
                .as("the renamed file keeps the adopted title")
                .contains("title: plantuml actions SHA + build fix");
    }

    /// Whatever path saves a stale editor, it must not create the file it
    /// was showing anew once that file is gone (moved or deleted under it).
    @Test
    void theEditorDoesNotRecreateAVanishedFile() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        String gone = "vanishing";
        Files.writeString(dir.resolve(gone + ".md"), CONTENT);
        Files.writeString(dir.resolve("bystander.md"),
                "---\ntitle: bystander\nstatus: active\n---\nnotes\n");
        awaitRow("bystander");
        openInEditor(gone);
        dirtyNotesEditor();

        // Moved away behind the editor's back, no taskRenamed.
        Files.move(dir.resolve(gone + ".md"), dir.resolve("moved-away.md"));
        // Leaving the file saves a dirty editor (openInEditor's flush).
        runFx(() -> mainWindow().selectTask("bystander"));
        awaitSelected("bystander");
        UiTestSupport.sleep();

        assertThat(Files.exists(dir.resolve(gone + ".md")))
                .as("the stale buffer was not saved as a new file")
                .isFalse();
    }

    private static void openInEditor(String id) {
        awaitRow(id);
        runFx(() -> mainWindow().selectTask(id));
        awaitSelected(id);
        awaitPresent(() -> callFx(() -> plainText(notesEditor()).contains("Indeppentend"))
                ? Optional.of(true) : Optional.empty(), "the " + id + " notes in the editor");
    }

    /// An unsaved edit in the notes editor, as typing leaves it.
    private static void dirtyNotesEditor() {
        runFx(() -> {
            RichTextArea notes = notesEditor();
            notes.getModel().replace(null, notes.getModel().getDocumentEnd(),
                    notes.getModel().getDocumentEnd(), "\nunsaved line");
        });
        UiTestSupport.sleep();
    }

    private static RichTextArea notesEditor() {
        return FX_ROBOT.selectNodes(RichTextArea.class).fromAll()
                .filter(area -> "notes-editor".equals(area.getId())).findFirst().orElseThrow();
    }

    private static String plainText(RichTextArea area) {
        CodeTextModel model = (CodeTextModel) area.getModel();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < model.size(); i++) {
            text.append(model.getPlainText(i)).append('\n');
        }
        return text.toString();
    }

    private static void awaitRow(String id) {
        awaitPresent(() -> callFx(() -> awaitListView().getItems().stream().anyMatch(row ->
                row instanceof TaskEntry.Loaded loaded && loaded.id().equals(id)))
                        ? Optional.of(true) : Optional.empty(),
                "the " + id + " row");
    }

    private static void awaitSelected(String id) {
        awaitPresent(() -> callFx(() -> selectedId(awaitListView()).equals(id))
                ? Optional.of(true) : Optional.empty(), "selected task " + id);
    }

    private static String selectedId(ListView<Object> list) {
        return list.getSelectionModel().getSelectedItem() instanceof TaskEntry.Loaded loaded
                ? loaded.id() : "";
    }

    private static ListView<Object> awaitListView() {
        return UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(),
                "the task ListView");
    }

    private static MainWindow mainWindow() {
        try {
            Field field = Main.class.getDeclaredField("window");
            field.setAccessible(true);
            return (MainWindow) field.get(UiTestSupport.app);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void runFx(Runnable action) {
        callFx(() -> {
            action.run();
            return false;
        });
    }

    private static boolean callFx(BooleanSupplier body) {
        if (Platform.isFxApplicationThread()) {
            return body.getAsBoolean();
        }
        boolean[] result = {false};
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result[0] = body.getAsBoolean();
            } finally {
                done.countDown();
            }
        });
        try {
            done.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }
}
