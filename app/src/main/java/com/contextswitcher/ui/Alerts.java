package com.contextswitcher.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/// Builders for the app's modal alerts.
///
/// They work around a JavaFX `DialogPane` sizing bug: the pane's built-in
/// content `Label` fixes its own width/height before the text is measured and
/// has text-ellipsis on, so a long or multi-line message collapses to a single
/// `…`-clipped line instead of wrapping. Supplying our own wrap-enabled `Label`
/// as the content and letting the pane grow to its preferred height (rather
/// than the mis-computed one) restores full, multi-line messages.
public final class Alerts {

    private Alerts() {
    }

    /// An alert whose `message` is shown in full — long lines wrap and embedded
    /// newlines break as written, instead of the built-in single-line clip.
    public static Alert wrapping(Alert.AlertType type, String message) {
        Label content = new Label(message);
        content.setWrapText(true);
        // A little breathing room below the header separator.
        content.setPadding(new Insets(4, 0, 0, 0));
        return withContent(type, content);
    }

    /// [#wrapping] with explicit buttons replacing the type's defaults.
    public static Alert wrapping(Alert.AlertType type, String message, ButtonType... buttons) {
        Alert alert = wrapping(type, message);
        alert.getButtonTypes().setAll(buttons);
        return alert;
    }

    /// An alert showing an arbitrary content node in full: the pane grows to
    /// the node's preferred height instead of clipping it — a multi-line
    /// message plus the delete dialog's checkboxes below it would otherwise be
    /// cut off at the height JavaFX computed before the text was measured.
    public static Alert withContent(Alert.AlertType type, Node content, ButtonType... buttons) {
        Alert alert = new Alert(type);
        alert.getDialogPane().setContent(content);
        // Size the pane to the (wrapped) content instead of clipping it.
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        // Belt and braces for the same bug on Windows, where the stage is sized
        // before the labels wrap: a resizable dialog re-lays out to fit, and
        // leaves the user a drag handle if it still comes up short. No
        // ScrollPane — the message and its checkboxes are meant to be read whole.
        alert.setResizable(true);
        if (buttons.length > 0) {
            alert.getButtonTypes().setAll(buttons);
        }
        return alert;
    }
}
