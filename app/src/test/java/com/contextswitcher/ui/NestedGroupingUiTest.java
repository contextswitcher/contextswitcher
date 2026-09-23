package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;

import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// Ticking "Folder" and "Labels" together nests the label headers inside the
/// category; unticking "Folder" again is the plain label view
/// (`dsn~nested-grouping~1`).
// [utest->dsn~nested-grouping~1]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class NestedGroupingUiTest {

    @Test
    void labelsNestInsideCategoriesAndASingleTickIsTheOldView() throws Exception {
        Path work = Files.createDirectories(UiTestSupport.tasksDir.resolve("nest-work"));
        Files.writeString(work.resolve("nest-prio.md"),
                "---\ntitle: nest-prio\nstatus: active\ntags: [prio1]\n---\nnotes\n");
        Files.writeString(work.resolve("nest-plain.md"),
                "---\ntitle: nest-plain\nstatus: active\n---\nnotes\n");
        ListView<Object> list = awaitListView();
        try {
            await(list, () -> rowNames(list).contains("nest-work/nest-plain"), "the tasks to load");

            click("Labels");
            await(list, () -> {
                List<String> names = rowNames(list);
                int header = names.indexOf("[nest-work]");
                return header >= 0 && names.subList(header, header + 4).equals(List.of(
                        "[nest-work]", "nest-work/nest-plain", "{prio1@1}", "nest-work/nest-prio"));
            }, "prio1 nested in nest-work, the untagged task first");

            click("Folder");
            await(list, () -> rowNames(list).contains("{prio1@0}")
                    && !rowNames(list).contains("[nest-work]"), "the plain label view");
        } finally {
            click("Folder");
            click("Labels");
            UiTestSupport.clearStoredFilters();
        }
    }

    private static void click(String label) {
        MenuButton button = awaitPresent(() -> FX_ROBOT.selectNodes(MenuButton.class).fromAll()
                .filter(menu -> menu.getTooltip() != null
                        && "Group the task list by".equals(menu.getTooltip().getText()))
                .findFirst(), "group menu button");
        Platform.runLater(() -> {
            CheckMenuItem item = (CheckMenuItem) button.getItems().stream()
                    .filter(candidate -> label.equals(candidate.getText())).findFirst().orElseThrow();
            item.setSelected(!item.isSelected());
            item.getOnAction().handle(new ActionEvent());
        });
    }

    private static List<String> rowNames(ListView<Object> list) {
        return list.getItems().stream().map(row -> switch (row) {
            case TaskListCell.GroupHeader header -> "[" + header.name() + "]";
            case TaskListCell.LabelHeader header -> "{" + header.name() + "@" + header.depth() + "}";
            case TaskEntry.Loaded loaded -> loaded.id();
            default -> "?";
        }).toList();
    }

    private static void await(ListView<Object> list, java.util.function.BooleanSupplier condition, String what) {
        awaitPresent(() -> condition.getAsBoolean() ? Optional.of(true) : Optional.empty(), what);
    }

    @SuppressWarnings("unchecked")
    private static ListView<Object> awaitListView() {
        return awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(v -> (ListView<Object>) v).findFirst(),
                "the task ListView");
    }
}
