package com.contextswitcher.ui;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.contextswitcher.switching.ActionStatus;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.jspecify.annotations.Nullable;

/// One chip per action of the current switch, color-coded by status;
/// the failure detail is shown in the tooltip.
// [impl->dsn~main-window~2]
public class SwitchStatusBar extends HBox {

    /// A chip's style class; `CHIP-<status>` picks its colour (`main.css`).
    private static final String CHIP = "switch-chip";

    private final Map<String, Label> chips = new LinkedHashMap<>();
    private final Label taskLabel = new Label();
    /// The label text to put back when the pointer leaves whatever is showing
    /// a transient hover text; null when nothing is being hovered.
    private @Nullable String beforeHover;

    public SwitchStatusBar() {
        super(8);
        setPadding(new Insets(6));
        setAlignment(Pos.CENTER_LEFT);
    }

    /// Resets the bar for a new switch.
    public void beginSwitch(String taskTitle) {
        chips.clear();
        beforeHover = null;
        setText(taskTitle + ":");
        getChildren().setAll(taskLabel);
    }

    /// Replaces the title line only — the chips of the running switch stay.
    // [impl->dsn~auto-suspend-idle~2]
    public void retitle(String text) {
        setText(text);
    }

    /// Shows a one-off status message with no action chips — for ad-hoc
    /// actions like opening a PR link.
    public void message(String text) {
        chips.clear();
        beforeHover = null;
        setText(text);
        getChildren().setAll(taskLabel);
    }

    /// Shows `text` while the pointer rests on something (a row's PR icon —
    /// its URL, which the row itself has no room for), and puts the previous
    /// text back on null. Only the label is swapped, never the chips: a switch
    /// running while the user hovers keeps reporting its actions.
    /// A [#message] or [#beginSwitch] arriving mid-hover wins — it drops the
    /// remembered text, so the leave does not resurrect a stale line.
    public void hover(@Nullable String text) {
        if (text != null) {
            if (beforeHover == null) {
                beforeHover = taskLabel.getText();
            }
            setText(text);
            // Until the first switch or message the bar has no children at all,
            // and setting the text of a label outside the scene shows nothing.
            if (!getChildren().contains(taskLabel)) {
                getChildren().addFirst(taskLabel);
            }
        } else if (beforeHover != null) {
            setText(beforeHover);
            beforeHover = null;
        }
    }

    /// The bar is one line high: a text with line breaks in it — a command's
    /// stderr in a failure message, a queued multi-line prompt — would grow the
    /// whole bottom bar over half the window, so every text put on the label is
    /// folded onto one line first.
    static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    /// Sets the one-line form on the label and keeps the whole text in the
    /// tooltip: the bar is too narrow for a long message, and the `Label`
    /// ellipsizes it (its own default overrun) rather than showing the end.
    // [impl->dsn~status-bar-one-line~2]
    private void setText(String text) {
        String line = oneLine(text);
        taskLabel.setText(line);
        taskLabel.setTooltip(line.isBlank() ? null : new Tooltip(line));
    }

    public void update(String action, ActionStatus status, String detail) {
        Label chip = chips.computeIfAbsent(action, name -> {
            Label label = new Label();
            label.getStyleClass().add(CHIP);
            getChildren().add(label);
            return label;
        });
        chip.setText("%s %s".formatted(action, symbol(status)));
        chip.getStyleClass().removeIf(styleClass -> styleClass.startsWith(CHIP + "-"));
        chip.getStyleClass().add(CHIP + "-" + status.name().toLowerCase(Locale.ROOT));
        chip.setTooltip(detail.isBlank() ? null : new Tooltip(detail));
    }

    private static String symbol(ActionStatus status) {
        return switch (status) {
            case PENDING -> "…";
            case RUNNING -> "⏳";
            case OK -> "✓";
            case FAILED -> "✗";
        };
    }
}
