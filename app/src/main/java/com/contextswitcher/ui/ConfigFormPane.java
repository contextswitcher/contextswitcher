package com.contextswitcher.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import atlantafx.base.theme.Styles;

import com.contextswitcher.tasks.Frontmatter;
import com.contextswitcher.tasks.FrontmatterCatalog;
import com.contextswitcher.tasks.FrontmatterCatalog.Field;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import org.jspecify.annotations.Nullable;

import tools.maran.svg.materialdesign.MDIInterface;
import tools.maran.svg.materialdesign.MDITechnology;
import tools.maran.svgnode.SvgNode;

/// The main window's **Configuration** pane (`dsn~shell-layout~2`): the open task
/// or category file's frontmatter as the catalog form ([ConfigForm]), with the
/// raw YAML editor one toggle away. It works on the editor buffer: **Apply**
/// patches only the changed keys in and saves.
// [impl->dsn~task-field-form~4]
final class ConfigFormPane {

    /// Held, not extended: only the header and the form or raw view go in it,
    /// and callers get a [Node] rather than every `BorderPane` setter.
    private final BorderPane root = new BorderPane();
    private final Node rawEditor;
    private final Supplier<String> text;
    private final Consumer<String> apply;
    private final BooleanSupplier category;
    private final ToggleButton rawToggle =
            new ToggleButton(null, new SvgNode(MDITechnology.CODE_BRACES.path(), 16));
    private final Button applyButton = new Button(null, new SvgNode(MDIInterface.CHECK.path(), 16));
    private final Button reloadButton = new Button(null, new SvgNode(MDIInterface.RELOAD.path(), 16));
    private final Button detachButton = new Button(null, new SvgNode(MDIInterface.OPEN_IN_NEW.path(), 16));
    /// The form over the current buffer; null while the raw view shows or the
    /// file has no frontmatter to configure.
    private @Nullable ConfigForm form;
    private Runnable onDetachToggle = () -> { };
    /// Set while an invalid form un-toggles the raw view, so that switch back
    /// does not rebuild the form and throw away what the user typed.
    private boolean revertingToggle;

    /// `rawEditor` is the editor lane's frontmatter editor (it auto-saves on
    /// focus loss); `text` and `apply` read and replace-and-save the whole buffer.
    ConfigFormPane(Node rawEditor, Supplier<String> text, Consumer<String> apply, BooleanSupplier category) {
        this.rawEditor = rawEditor;
        this.text = text;
        this.apply = apply;
        this.category = category;
        // Icon-only like every pane toolbar's controls; the tooltip names each.
        // [impl->dsn~task-field-form~4]
        rawToggle.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        rawToggle.setId("config-raw-toggle");
        applyButton.setId("config-apply-button");
        reloadButton.setId("config-reload-button");
        detachButton.setId("config-pop-out-button");
        for (Button button : List.of(applyButton, reloadButton, detachButton)) {
            button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            // Not focus-traversable, as the editor lane's own buttons: a click
            // must not move focus out of the raw editor before it saved.
            button.setFocusTraversable(false);
        }
        rawToggle.setTooltip(new Tooltip("Raw YAML — edit the frontmatter as YAML, the fallback for anything the form does not cover"));
        applyButton.setTooltip(new Tooltip("Apply — write the changed fields into the file and save"));
        reloadButton.setTooltip(new Tooltip("Reload — rebuild the form from the file, dropping unapplied changes"));
        detachButton.setTooltip(new Tooltip("Pop out — move the configuration into a window of its own"));
        applyButton.setOnAction(event -> applyForm());
        reloadButton.setOnAction(event -> reload());
        detachButton.setOnAction(event -> onDetachToggle.run());
        rawToggle.selectedProperty().addListener((obs, was, raw) -> switchView(raw));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(6, rawToggle, applyButton, reloadButton, spacer, detachButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 6, 4, 6));
        header.getStyleClass().add("panel-header");
        root.setTop(header);
        reload();
    }

    /// The pane's node — for its dock tab, or the window it pops out into.
    Node getRoot() {
        return root;
    }

    void setOnDetachToggle(Runnable toggle) {
        this.onDetachToggle = toggle;
    }

    /// A popped-out window has Dock back in its own title bar, so the pane's
    /// Pop out button is hidden while detached.
    void setDetached(boolean detached) {
        detachButton.setVisible(!detached);
        detachButton.setManaged(!detached);
    }

    /// Rebuilds the form from the current buffer — after a file switch, a
    /// revert, an apply. The raw view needs nothing: it is the buffer.
    void reload() {
        if (rawToggle.isSelected()) {
            return;
        }
        String content = text.get();
        if (Frontmatter.block(content) == null) {
            form = null;
            Label none = new Label("This file has no '---' frontmatter to configure.");
            none.getStyleClass().add(Styles.TEXT_MUTED);
            root.setCenter(new StackPane(none));
            applyButton.setDisable(true);
            return;
        }
        List<Field> fields = category.getAsBoolean() ? FrontmatterCatalog.CATEGORY : FrontmatterCatalog.TASK;
        ConfigForm built = new ConfigForm(fields, content);
        form = built;
        ScrollPane scroll = new ScrollPane(built.render());
        scroll.setFitToWidth(true);
        root.setCenter(scroll);
        applyButton.setDisable(false);
    }

    /// False when the form is invalid, after an alert saying why.
    private boolean applyForm() {
        ConfigForm current = form;
        if (current == null) {
            return true;
        }
        String content = text.get();
        return switch (current.mergeInto(content)) {
            case ConfigForm.MergeResult.Invalid(String reason) -> {
                new Alert(Alert.AlertType.ERROR, reason).show();
                yield false;
            }
            case ConfigForm.MergeResult.Merged(String merged) -> {
                if (!merged.equals(content)) {
                    apply.accept(merged);
                }
                yield true;
            }
        };
    }

    private void switchView(boolean raw) {
        if (revertingToggle) {
            return;
        }
        if (raw) {
            // Form edits are carried into the raw view before it shows.
            if (!applyForm()) {
                revertingToggle = true;
                rawToggle.setSelected(false);
                revertingToggle = false;
                return;
            }
            form = null;
            applyButton.setDisable(true);
            reloadButton.setDisable(true);
            root.setCenter(rawEditor);
        } else {
            reloadButton.setDisable(false);
            reload();
        }
    }
}
