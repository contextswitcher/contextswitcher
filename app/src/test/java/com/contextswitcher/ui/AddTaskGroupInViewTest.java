package com.contextswitcher.ui;

import java.util.List;

import com.contextswitcher.ui.TaskListCell.AddTaskRow;
import com.contextswitcher.ui.TaskListCell.GroupHeader;
import com.contextswitcher.ui.TaskListCell.LabelHeader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-create-ui~15]
class AddTaskGroupInViewTest {

    private static final List<Object> ROWS = List.of(
            new GroupHeader("alpha", true, List.of(), null),
            new AddTaskRow("alpha"),
            new GroupHeader("beta", true, List.of(), null),
            new AddTaskRow("beta"),
            new LabelHeader("label", "key", true, 0, 0),
            new GroupHeader("gamma", true, List.of(), null));

    @Test
    void selectionOnScreenWins() {
        assertThat(MainWindow.groupInView(ROWS, 0, 0, 5)).isEqualTo("alpha");
    }

    @Test
    void withoutSelectionTheTopmostVisibleGroupCounts() {
        assertThat(MainWindow.groupInView(ROWS, -1, 2, 5)).isEqualTo("beta");
        assertThat(MainWindow.groupInView(ROWS, -1, 3, 5)).isEqualTo("beta");
    }

    @Test
    void selectionScrolledAwayYieldsToTheTopmostVisibleGroup() {
        assertThat(MainWindow.groupInView(ROWS, 0, 2, 5)).isEqualTo("beta");
    }

    @Test
    void groupLessTopRowLooksFurtherDown() {
        assertThat(MainWindow.groupInView(ROWS, -1, 4, 5)).isEqualTo("gamma");
    }

    @Test
    void nothingKnownIsTheRoot() {
        assertThat(MainWindow.groupInView(ROWS, -1, -1, -1)).isEmpty();
        assertThat(MainWindow.groupInView(ROWS, 4, 0, 5)).isEmpty();
    }
}
