package com.contextswitcher.ui;

import java.util.Locale;

import com.contextswitcher.tasks.TaskTags;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import org.jspecify.annotations.Nullable;

/// Renders a tag as a small rounded colored chip — shared by the task rows,
/// the group headers, and the toolbar tag-filter menu, so a tag looks the
/// same everywhere it appears.
// [impl->dsn~task-tag-filter~2]
final class TagChips {

    private TagChips() {
    }

    /// A chip for `name`: `colorHex` (CSS hex) as background; a null or
    /// unparseable color falls back to the tag's stable name-derived color
    /// ([TaskTags#autoColor]). The text color is chosen (dark or white) for
    /// contrast against the chip color.
    // [impl->dsn~tag-auto-color~2]
    static Label chip(String name, @Nullable String colorHex) {
        Label chip = new Label(name);
        Color color;
        try {
            color = Color.web(colorHex == null ? TaskTags.autoColor(name) : colorHex);
        } catch (IllegalArgumentException e) {
            color = Color.web(TaskTags.autoColor(name));
        }
        double luminance = 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
        chip.getStyleClass().addAll("tag-chip", luminance > 0.6 ? "tag-chip-on-light" : "tag-chip-on-dark");
        String bg = String.format(Locale.ROOT, "#%02x%02x%02x",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
        // The one inline colour in the UI: the tag's palette entry is data the
        // user chose, not a theme colour a style class could name.
        chip.setStyle("-fx-background-color: " + bg + ";");
        chip.setMinWidth(Label.USE_PREF_SIZE);
        return chip;
    }
}
