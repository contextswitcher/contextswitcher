package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import atlantafx.base.theme.Styles;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.jspecify.annotations.Nullable;

/// Asks before the app is left while it still hosts Claude sessions
/// (`dsn~owned-session-close-confirm~1`): on Windows the app owns a local
/// session's process (`dsn~terminal-owned-session~4`), so leaving ends it —
/// a Claude in the middle of a turn is cut off. The dialog lists the tasks
/// whose session is running, grouped by category; clicking one cancels the
/// leaving and brings that task (and its terminal) into view.
// [impl->dsn~owned-session-close-confirm~1]
public final class CloseConfirmation {

    /// One task whose app-owned session is running.
    public record Row(String taskId, String title) {
    }

    /// The button that leaves anyway.
    static final ButtonType END = new ButtonType("Close and end them", ButtonBar.ButtonData.OK_DONE);

    private CloseConfirmation() {
    }

    /// `rows` by category — the task id's folder part, "" for the root —
    /// categories alphabetically with the root last, tasks by title.
    static Map<String, List<Row>> byCategory(List<Row> rows) {
        Map<String, List<Row>> grouped = new TreeMap<>(Comparator
                .comparing((String category) -> category.isEmpty())
                .thenComparing(String.CASE_INSENSITIVE_ORDER));
        for (Row row : rows) {
            int slash = row.taskId().lastIndexOf('/');
            grouped.computeIfAbsent(slash < 0 ? "" : row.taskId().substring(0, slash),
                    key -> new ArrayList<>()).add(row);
        }
        Map<String, List<Row>> sorted = new LinkedHashMap<>();
        grouped.forEach((category, tasks) -> sorted.put(category, tasks.stream()
                .sorted(Comparator.comparing(Row::title, String.CASE_INSENSITIVE_ORDER)).toList()));
        return sorted;
    }

    /// Shows the dialog over `owner` and answers whether to leave: true only
    /// for "Close and end them". A click on a listed task closes the dialog,
    /// answers false and hands the task id to `onPick`. `action` names what
    /// leaving is ("Closing ContextSwitcher", "Restarting").
    public static boolean confirm(@Nullable Window owner, String action, List<Row> rows,
            Consumer<String> onPick) {
        if (rows.isEmpty()) {
            return true;
        }
        Alert alert = Alerts.withContent(owner, Alert.AlertType.CONFIRMATION, new VBox(),
                END, ButtonType.CANCEL);
        alert.setTitle("Claude sessions are running");
        alert.setHeaderText(null);
        String[] picked = {null};
        VBox content = new VBox(4);
        Label intro = new Label(("%s ends the Claude session%s it runs for %s. Each can be resumed "
                + "later, but a turn in progress is cut off. Click a task to look at it first.")
                .formatted(action, rows.size() == 1 ? "" : "s",
                        rows.size() == 1 ? "this task" : "these " + rows.size() + " tasks"));
        intro.setWrapText(true);
        intro.setMinHeight(Region.USE_PREF_SIZE);
        content.getChildren().add(intro);
        byCategory(rows).forEach((category, tasks) -> {
            Label heading = new Label(category.isEmpty() ? "(no category)" : category);
            heading.getStyleClass().add(Styles.TEXT_BOLD);
            heading.setId("close-confirm-category");
            content.getChildren().add(heading);
            for (Row task : tasks) {
                Hyperlink link = new Hyperlink(task.title());
                link.setId("close-confirm-task");
                link.setUserData(task.taskId());
                link.setOnAction(event -> {
                    picked[0] = task.taskId();
                    alert.setResult(ButtonType.CANCEL);
                    alert.close();
                });
                content.getChildren().add(link);
            }
        });
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefWidth(520);
        boolean leave = alert.showAndWait().filter(END::equals).isPresent();
        if (picked[0] != null) {
            onPick.accept(picked[0]);
        }
        return leave;
    }
}
