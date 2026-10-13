package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

/// Type-ahead for a free-text field: while the user types, a popup under the
/// field lists the known values that match; picking one fills the field. Any
/// other value can still be typed — the list only suggests. The configuration
/// form's *Virtual desktop* field uses it (`dsn~task-field-form~4`).
// [impl->dsn~task-field-form~4]
final class Suggestions {

    /// More than this many entries stops being a suggestion and becomes a list
    /// to read; typing one more letter narrows it instead.
    static final int MAX_SHOWN = 10;

    private Suggestions() {
    }

    /// The `candidates` that match `typed`, ignoring case: those starting with
    /// it first, then those containing it, each in the given order. A value
    /// equal to what is typed is left out — there is nothing left to complete —
    /// and blank `typed` matches everything.
    static List<String> matching(List<String> candidates, String typed) {
        String needle = typed.strip().toLowerCase(Locale.ROOT);
        List<String> prefix = new ArrayList<>();
        List<String> infix = new ArrayList<>();
        for (String candidate : candidates) {
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.equals(needle)) {
                continue;
            }
            if (lower.startsWith(needle)) {
                prefix.add(candidate);
            } else if (lower.contains(needle)) {
                infix.add(candidate);
            }
        }
        prefix.addAll(infix);
        return prefix.size() > MAX_SHOWN ? prefix.subList(0, MAX_SHOWN) : prefix;
    }

    /// Attaches the popup to `field`. It opens on typing and on Down (which
    /// also lists everything for an empty field), and closes when the field
    /// loses focus.
    static void install(TextField field, List<String> candidates) {
        ContextMenu popup = new ContextMenu();
        popup.getStyleClass().add("suggestions");
        Runnable refresh = () -> {
            List<String> matches = matching(candidates, field.getText());
            if (matches.isEmpty()) {
                popup.hide();
                return;
            }
            List<MenuItem> items = new ArrayList<>();
            for (String match : matches) {
                MenuItem item = new MenuItem(match);
                // Desktop names may contain `_`, which must not turn into a mnemonic.
                item.setMnemonicParsing(false);
                item.setOnAction(event -> {
                    field.setText(match);
                    field.positionCaret(match.length());
                });
                items.add(item);
            }
            popup.getItems().setAll(items);
            if (!popup.isShowing()) {
                popup.show(field, Side.BOTTOM, 0, 0);
            }
        };
        field.textProperty().addListener((obs, was, now) -> {
            if (field.isFocused()) {
                refresh.run();
            }
        });
        field.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN && !popup.isShowing()) {
                refresh.run();
                event.consume();
            }
        });
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                popup.hide();
            }
        });
    }
}
