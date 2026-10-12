package com.contextswitcher.ui;

import javafx.collections.ListChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.DialogPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

/// Opens every ownerless dialog over the main window instead of on the
/// primary screen.
///
/// A JavaFX `Dialog`/`Alert` without `initOwner` centers itself on the
/// primary screen, so with the app on a second monitor "Add task" and every
/// error alert popped up on the other one (field report 2026-10-12). Most of
/// the app's dialogs set no owner, and a new one easily forgets it, so this
/// works like [ReadlineKeys#installEverywhere]: a listener on
/// [Window#getWindows()] repositions each dialog window as it is shown —
/// centered horizontally over the main window, in its upper third the way
/// JavaFX places an owned dialog — and clamps it to that window's screen.
/// Dialogs with an owner are left to JavaFX, and so are windows that are no
/// dialog (detached panes, the sync-groups window), which keep their own
/// geometry.
// [impl->dsn~dialog-placement~1]
public final class DialogPlacement {

    private DialogPlacement() {
    }

    /// Repositions every ownerless dialog shown from now on over `main`.
    public static void installEverywhere(Stage main) {
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window window : change.getAddedSubList()) {
                    if (window != main && isOwnerlessDialog(window) && main.isShowing()) {
                        place(window, main);
                    }
                }
            }
        });
    }

    static boolean isOwnerlessDialog(Window window) {
        return window instanceof Stage stage && stage.getOwner() == null
                && stage.getScene() != null && stage.getScene().getRoot() instanceof DialogPane;
    }

    private static void place(Window dialog, Window main) {
        Rectangle2D screen = Screen.getScreensForRectangle(main.getX(), main.getY(),
                        Math.max(1, main.getWidth()), Math.max(1, main.getHeight())).stream()
                .findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        // The window enters the list before JavaFX sized it; the pane's
        // preferred size stands in until then (decorations aside).
        javafx.scene.Parent root = dialog.getScene().getRoot();
        double width = Double.isNaN(dialog.getWidth()) ? root.prefWidth(-1) : dialog.getWidth();
        double height = Double.isNaN(dialog.getHeight()) ? root.prefHeight(width) : dialog.getHeight();
        Rectangle2D at = position(main.getX(), main.getY(), main.getWidth(), main.getHeight(),
                width, height, screen);
        dialog.setX(at.getMinX());
        dialog.setY(at.getMinY());
    }

    /// Where a `width`×`height` dialog goes over the owner rectangle: centered
    /// horizontally, its top a third of the remaining height down, then kept
    /// inside `screen` (a dialog larger than the screen keeps its top-left
    /// corner on it).
    static Rectangle2D position(double ownerX, double ownerY, double ownerWidth, double ownerHeight,
            double width, double height, Rectangle2D screen) {
        double x = ownerX + (ownerWidth - width) / 2;
        double y = ownerY + Math.max(0, ownerHeight - height) / 3;
        x = Math.max(screen.getMinX(), Math.min(x, screen.getMaxX() - width));
        y = Math.max(screen.getMinY(), Math.min(y, screen.getMaxY() - height));
        return new Rectangle2D(x, y, width, height);
    }
}
