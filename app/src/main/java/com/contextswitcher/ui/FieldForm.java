package com.contextswitcher.ui;

import atlantafx.base.theme.Styles;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;

/// The shared look of the app's generated option forms — the settings dialog
/// (`dsn~settings-editor~4`) and the task/category configuration form
/// (`dsn~task-field-form~4`), which are the same form over two different
/// catalogs: a bold section header, then one row per option with its label on
/// the line above a full-width control, highlighting on hover and carrying the
/// option's help as its tooltip.
// [impl->dsn~settings-editor~4]
// [impl->dsn~task-field-form~4]
final class FieldForm {

    private FieldForm() {
    }

    /// The share of the screen height a form dialog may take, window and all.
    static final double MAX_SCREEN_SHARE = 0.75;

    /// What a form dialog needs besides the form: header, button bar, window chrome.
    static final double DIALOG_CHROME = 260;

    /// The least viewport a form gets, so a tiny screen still shows some of it.
    static final double MIN_VIEWPORT = 120;

    /// The viewport height for a scrolling form on the primary screen — see
    /// [#viewportHeightOn(double)].
    static double viewportHeight() {
        return viewportHeightOn(Screen.getPrimary().getVisualBounds().getHeight());
    }

    /// The viewport height for a scrolling form on a screen `screenHeight`
    /// tall: the dialog, chrome included, stays within [#MAX_SCREEN_SHARE] of
    /// it. Taking the whole height let a long form open taller than the
    /// screen (field report 2026-09-13), and a per-dialog minimum would break
    /// the cap again on a small screen — only [#MIN_VIEWPORT] is kept.
    static double viewportHeightOn(double screenHeight) {
        return Math.max(MIN_VIEWPORT, screenHeight * MAX_SCREEN_SHARE - DIALOG_CHROME);
    }

    /// A form section header. `first` (the top of the form) drops the leading
    /// margin that separates it from the section above.
    static Label section(String title, boolean first) {
        Label header = new Label(title);
        header.getStyleClass().add(Styles.TITLE_4);
        VBox.setMargin(header, new Insets(first ? 0 : 10, 0, 2, 0));
        return header;
    }

    /// One form row: the label on the line above, the control below stretched
    /// to the row's full width, `help` as the row tooltip, highlighting on
    /// hover. Anything in `beside` is placed right of the control, for the rare
    /// row with an action of its own.
    ///
    /// A checkbox is the exception: it takes the label as its own text on a
    /// single line, since a lone box opposite a label on the line above reads
    /// as an unlabelled control with a caption floating over it.
    static Node row(String label, String help, Region control, Node... beside) {
        boolean flag = control instanceof CheckBox;
        Label caption = new Label(label);
        caption.getStyleClass().add(Styles.TEXT_BOLD);
        if (flag) {
            CheckBox box = (CheckBox) control;
            box.setText(label);
            box.getStyleClass().add(Styles.TEXT_BOLD);
        }

        HBox controlLine = new HBox(6, control);
        controlLine.getChildren().addAll(beside);
        controlLine.setAlignment(Pos.CENTER_LEFT);
        if (!flag) {
            controlLine.setAlignment(Pos.CENTER_RIGHT);
            control.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(control, Priority.ALWAYS);
        }

        VBox row = flag ? new VBox(3, controlLine) : new VBox(3, caption, controlLine);
        row.setPadding(new Insets(4, 6, 4, 6));
        Tooltip.install(row, new Tooltip(help));
        // Hover highlight — a subtle inset (`.field-row:hover`, main.css).
        row.getStyleClass().add("field-row");
        return row;
    }

    /// A multi-line list control: one entry per line, monospace so paths and
    /// URLs line up.
    static TextArea listArea(String text, int rows) {
        TextArea area = new TextArea(text);
        area.setPrefRowCount(rows);
        area.getStyleClass().add("monospace");
        return area;
    }
}
