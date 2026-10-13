package com.contextswitcher.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;

/// Builders for the app's modal alerts.
///
/// They work around a JavaFX `DialogPane` sizing bug: the pane's built-in
/// content `Label` fixes its own width/height before the text is measured and
/// has text-ellipsis on, so a long or multi-line message collapses to a single
/// `…`-clipped line instead of wrapping. Supplying our own wrap-enabled `Label`
/// as the content and letting the pane grow to its preferred height (rather
/// than the mis-computed one) restores full, multi-line messages.
///
/// Every builder takes the dialog's **owner** first, standard JavaFX
/// practice (`req~owned-dialogs~1`): an owned dialog opens over its window —
/// on that window's monitor, where an ownerless one is centered on the
/// primary screen — stays above it and blocks only it. Null is for the rare
/// dialog with no window to belong to yet (the first start).
// [impl->dsn~owned-dialogs~1]
public final class Alerts {

    private Alerts() {
    }

    /// A plain `new Alert(type, message, buttons)` owned by `owner`.
    public static Alert plain(@Nullable Window owner, Alert.AlertType type, String message,
            ButtonType... buttons) {
        return owned(owner, new Alert(type, message, buttons));
    }

    /// Sets `dialog`'s owner and returns it — for the dialogs built by hand.
    public static <D extends Dialog<?>> D owned(@Nullable Window owner, D dialog) {
        dialog.initOwner(owner);
        return dialog;
    }

    /// The window `node` is shown in, null while it is in none.
    public static @Nullable Window windowOf(@Nullable Node node) {
        return node == null || node.getScene() == null ? null : node.getScene().getWindow();
    }

    /// An alert whose `message` is shown in full — long lines wrap and embedded
    /// newlines break as written, instead of the built-in single-line clip.
    public static Alert wrapping(@Nullable Window owner, Alert.AlertType type, String message) {
        Label content = new Label(message);
        content.setWrapText(true);
        // A little breathing room below the header separator.
        content.setPadding(new Insets(4, 0, 0, 0));
        return withContent(owner, type, content);
    }

    /// [#wrapping] with explicit buttons replacing the type's defaults.
    public static Alert wrapping(@Nullable Window owner, Alert.AlertType type, String message,
            ButtonType... buttons) {
        Alert alert = wrapping(owner, type, message);
        alert.getButtonTypes().setAll(buttons);
        return alert;
    }

    /// An alert showing an arbitrary content node in full: the pane grows to
    /// the node's preferred height instead of clipping it — a multi-line
    /// message plus the delete dialog's checkboxes below it would otherwise be
    /// cut off at the height JavaFX computed before the text was measured.
    public static Alert withContent(@Nullable Window owner, Alert.AlertType type, Node content,
            ButtonType... buttons) {
        Alert alert = owned(owner, new Alert(type));
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
