package com.contextswitcher.ui;

import java.util.List;

import atlantafx.base.theme.Styles;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import tools.maran.svg.materialdesign.MDIInterface;
import tools.maran.svgnode.SvgNode;

/// A list field of the configuration forms (`dsn~task-field-form~5`): one
/// single-line text field per entry, plus one empty field at the end to add
/// the next entry. Typing into that last field adds a fresh empty one below
/// it; a row's trash can, shown while the row is hovered, removes the entry —
/// the queue's delete-on-hover ([QueuePane]). Enter moves to the next field.
///
/// The fields carry the editor's id with the row index appended
/// (`field-folders-0`, …), so a UI test can address a single entry.
// [impl->dsn~task-field-form~5]
final class ListFieldEditor extends VBox {

    ListFieldEditor(List<String> entries) {
        super(2);
        for (String entry : entries) {
            addRow(entry);
        }
        addRow("");
        idProperty().addListener((obs, old, id) -> renumber());
    }

    /// The entries in order, blank ones included — the form drops those.
    List<String> values() {
        return getChildren().stream().map(row -> field(row).getText()).toList();
    }

    private void addRow(String text) {
        TextField field = new TextField(text);
        field.getStyleClass().add("monospace");
        field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);
        Button delete = new Button(null, new SvgNode(MDIInterface.TRASH_CAN_OUTLINE.path(), 14));
        delete.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        // A click must not pull the focus out of the form: leaving it applies.
        delete.setFocusTraversable(false);
        delete.setTooltip(new Tooltip("Remove this entry"));
        HBox row = new HBox(4, field, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        // The trailing empty row has nothing to delete; it keeps the slot so
        // every field ends at the same x.
        delete.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> row.isHover() && !(isLast(row) && field.getText().isEmpty()),
                row.hoverProperty(), field.textProperty(), getChildren()));
        delete.setOnAction(event -> remove(row));
        field.textProperty().addListener((obs, old, now) -> {
            if (isLast(row) && !now.isEmpty()) {
                addRow("");
            }
        });
        field.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                int index = getChildren().indexOf(row);
                if (index + 1 < getChildren().size()) {
                    field(getChildren().get(index + 1)).requestFocus();
                }
                event.consume();
            }
        });
        getChildren().add(row);
        renumber();
    }

    private void remove(HBox row) {
        if (isLast(row)) {
            field(row).clear();
            return;
        }
        getChildren().remove(row);
        renumber();
    }

    private boolean isLast(HBox row) {
        return !getChildren().isEmpty() && getChildren().getLast() == row;
    }

    private void renumber() {
        String id = getId();
        for (int i = 0; i < getChildren().size(); i++) {
            field(getChildren().get(i)).setId(id == null ? null : id + "-" + i);
        }
    }

    private static TextField field(Node row) {
        return (TextField) ((HBox) row).getChildren().getFirst();
    }
}
