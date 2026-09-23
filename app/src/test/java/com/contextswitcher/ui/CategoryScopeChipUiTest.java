package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import com.contextswitcher.queue.QueueFile;
import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// Pressing a category header's name drops the category into the find field
/// as a chip: the list narrows to that category, typing searches only there —
/// finding a task by a queued chat message too, and counting its matches —
/// and the chip's × brings every category back.
// [utest->dsn~category-search~3]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class CategoryScopeChipUiTest {

    @Test
    void theChipScopesTheFindToItsCategory() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("alpha"));
        Files.createDirectories(dir.resolve("beta"));
        write(dir.resolve("alpha").resolve("parser.md"), "parser");
        write(dir.resolve("alpha").resolve("renamer.md"), "renamer");
        write(dir.resolve("beta").resolve("parser-too.md"), "parser-too");
        write(dir.resolve("alpha").resolve("mailer.md"), "mailer");
        // Only the queue says "renamer": the scoped search must read it.
        QueueFile.save(QueueFile.file(dir.resolve(".queues"), "alpha/mailer"),
                java.util.List.of("please also fix the renamer"));

        ListView<Object> list = awaitListView();
        awaitPresent(() -> hasTask(list, "alpha/renamer") && hasTask(list, "beta/parser-too")
                ? Optional.of(true) : Optional.empty(), "the category tasks to load");

        // Inside the list itself: the pinned sticky header is a second, often
        // hidden, rendering of the same header outside it.
        Label name = awaitPresent(() -> FX_ROBOT.selectNodes(Label.class).fromAll()
                .filter(node -> "group-name-label".equals(node.getId()) && "alpha".equals(node.getText())
                        && isInside(node, list))
                .findFirst(), "the alpha header's name");
        FX_ROBOT.mouse().moveTo(name).click();

        Node chip = awaitPresent(() -> FX_ROBOT.selectNodes(Node.class).fromAll()
                .filter(node -> "find-scope-chip".equals(node.getId()) && node.isVisible())
                .findFirst(), "the scope chip in the find field");
        awaitPresent(() -> hasTask(list, "beta/parser-too") ? Optional.empty() : Optional.of(true),
                "the other category to leave the list");
        assertThat(hasTask(list, "alpha/parser")).isTrue();

        // The press put the keyboard in the find field.
        FX_ROBOT.keyboard().print("renamer");
        awaitPresent(() -> hasTask(list, "alpha/parser") ? Optional.empty() : Optional.of(true),
                "the non-matching task of the scoped category to go");
        assertThat(hasTask(list, "alpha/renamer")).isTrue();
        assertThat(hasTask(list, "alpha/mailer")).as("found by its queued message").isTrue();
        Label count = FX_ROBOT.selectNodes(Label.class).fromAll()
                .filter(node -> node.getStyleClass().contains("find-match-count")).findFirst().orElseThrow();
        awaitPresent(() -> Optional.of(count.getText()).filter("2 matches"::equals),
                "the scoped match count");

        Button remove = FX_ROBOT.selectNodes(Button.class).fromAll()
                .filter(node -> "find-scope-remove".equals(node.getId())).findFirst().orElseThrow();
        FX_ROBOT.mouse().moveTo(remove).click();
        awaitPresent(() -> chip.isVisible() ? Optional.empty() : Optional.of(true), "the chip to go");
        // The query stays and now searches every category again.
        awaitPresent(() -> hasTask(list, "alpha/renamer") ? Optional.of(true) : Optional.empty(),
                "the query's hit to stay");
    }

    private static void write(Path file, String title) throws Exception {
        Files.writeString(file, "---\ntitle: %s\nstatus: active\n---\nnotes\n".formatted(title));
    }

    private static boolean isInside(Node node, Node ancestor) {
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static ListView<Object> awaitListView() {
        return awaitPresent(() -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(),
                "the task ListView");
    }

    private static boolean hasTask(ListView<Object> list, String id) {
        return list.getItems().stream()
                .anyMatch(row -> row instanceof TaskEntry.Loaded loaded && loaded.id().equals(id));
    }
}
