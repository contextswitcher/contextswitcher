package com.contextswitcher.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/// A notification count over a toolbar icon button's glyph — the update
/// button's pending news, the sync button's waiting tasks — like a phone's
/// badge, in the glyph's lower right corner (`.update-badge` in main.css).
final class Badge {

    private final Label label = new Label();

    /// Puts the badge over `button`'s glyph, hidden until [#show] gets a count.
    /// The pane keeps the glyph's size, so the badge overflows it and no
    /// toolbar icon moves when it appears.
    Badge(Button button) {
        StackPane.setAlignment(label, Pos.BOTTOM_RIGHT);
        label.setTranslateX(6);
        label.setTranslateY(4);
        label.getStyleClass().add("update-badge");
        label.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        label.setVisible(false);
        StackPane graphic = new StackPane(button.getGraphic(), label);
        graphic.setMinSize(16, 16);
        graphic.setPrefSize(16, 16);
        graphic.setMaxSize(16, 16);
        button.setGraphic(graphic);
    }

    /// Shows `count`; zero hides the badge — never a 0.
    void show(int count) {
        label.setText(text(count));
        label.setVisible(count > 0);
    }

    /// The badge text for `count`: `99+` past 99, so the badge keeps its size.
    // [impl->dsn~restart-to-update~11]
    static String text(int count) {
        return count > 99 ? "99+" : String.valueOf(count);
    }
}
