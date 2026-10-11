package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoubleSupplier;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.jspecify.annotations.Nullable;

/// Double-clicking a split divider between docked panes widens the pane next to
/// it just enough for its content to fit — for the task list, until no
/// category name ends in "…". A pane opts in with [#widthDeficit]: how many
/// pixels it is short of. The dock host's splits are plain JavaFX
/// [SplitPane]s (ShellFX's `DockSplitPane`), so one filter on the dock host
/// covers every divider, including the splits ShellFX creates on a drop.
// [impl->dsn~divider-fit-content~1]
final class DividerFit {

    private static final String DEFICIT_KEY = DividerFit.class.getName() + ".widthDeficit";
    private static final String DIVIDER_STYLE = "split-pane-divider";

    private DividerFit() {
    }

    /// Lets `pane` be widened by a double-click on a divider beside it, by the
    /// pixels `deficit` reports missing (0 or less: it fits already).
    static void widthDeficit(Node pane, DoubleSupplier deficit) {
        pane.getProperties().put(DEFICIT_KEY, deficit);
    }

    /// The pixels the shown `labels` lack for their full text — the largest
    /// shortfall of any of them, so the widest name decides.
    static double labelDeficit(Iterable<? extends Node> labels) {
        double deficit = 0;
        for (Node node : labels) {
            if (node instanceof Label label && isShown(label)) {
                deficit = Math.max(deficit, label.prefWidth(-1) - label.getWidth());
            }
        }
        return Math.ceil(deficit);
    }

    static void install(Node root) {
        root.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2) {
                return;
            }
            Node divider = divider(event.getPickResult().getIntersectedNode());
            if (divider != null && divider.getParent() instanceof SplitPane split
                    && split.getOrientation() == Orientation.HORIZONTAL && fit(split, divider)) {
                event.consume();
            }
        });
    }

    /// The new position of a divider at `position` (a fraction of `length`)
    /// that gives the pane `deficit` pixels more: to the right when the pane is
    /// left of the divider, to the left otherwise. Kept within the split.
    static double widened(double position, double length, double deficit, boolean paneBefore) {
        double shift = deficit / length;
        return Math.clamp(paneBefore ? position + shift : position - shift, 0.0, 1.0);
    }

    private static boolean fit(SplitPane split, Node divider) {
        // The skin's dividers sit among its children; sorting by x gives their
        // index, which is the index of the item left of them.
        List<Node> dividers = new ArrayList<>();
        for (Node child : split.getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains(DIVIDER_STYLE)) {
                dividers.add(child);
            }
        }
        dividers.sort(Comparator.comparingDouble(Node::getLayoutX));
        int index = dividers.indexOf(divider);
        if (index < 0 || index + 1 >= split.getItems().size() || split.getWidth() <= 0) {
            return false;
        }
        double before = deficit(split.getItems().get(index));
        double after = before > 0 ? 0 : deficit(split.getItems().get(index + 1));
        if (before <= 0 && after <= 0) {
            return false;
        }
        double position = split.getDividerPositions()[index];
        split.setDividerPosition(index, before > 0
                ? widened(position, split.getWidth(), before, true)
                : widened(position, split.getWidth(), after, false));
        return true;
    }

    /// The largest deficit any shown pane under `node` reports — the pane may
    /// sit in a nested split or a dock's tab.
    private static double deficit(Node node) {
        double deficit = 0;
        if (node.getProperties().get(DEFICIT_KEY) instanceof DoubleSupplier supplier && isShown(node)) {
            deficit = supplier.getAsDouble();
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                deficit = Math.max(deficit, deficit(child));
            }
        }
        return deficit;
    }

    private static @Nullable Node divider(@Nullable Node picked) {
        for (Node node = picked; node != null; node = node.getParent()) {
            if (node.getStyleClass().contains(DIVIDER_STYLE)) {
                return node;
            }
        }
        return null;
    }

    /// Visible all the way up: a dock's unselected tab and a list's spare cells
    /// are hidden by an ancestor, and their widths say nothing about the pane.
    static boolean isShown(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }
}
