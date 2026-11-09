package com.contextswitcher.ui;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import atlantafx.base.theme.Styles;

import com.contextswitcher.tasks.Frontmatter;
import com.contextswitcher.tasks.FrontmatterCatalog;
import com.contextswitcher.tasks.FrontmatterCatalog.Field;

import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
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
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;

import tools.maran.svg.materialdesign.MDIInterface;
import tools.maran.svg.materialdesign.MDITechnology;
import tools.maran.svgnode.SvgNode;

/// The main window's **Configuration** and **Switch actions** panes
/// (`dsn~shell-layout~2`): the open task or category file's frontmatter as the
/// catalog form ([ConfigForm]) — each pane the part of the catalog it `shows` —
/// with the raw YAML editor one toggle away. It works on the editor buffer:
/// when the focus leaves the pane, the changed keys are patched in and saved;
/// **Reload** drops what was not saved yet.
// [impl->dsn~task-field-form~5]
final class ConfigFormPane {

    /// Held, not extended: only the header and the form or raw view go in it,
    /// and callers get a [Node] rather than every `BorderPane` setter.
    private final BorderPane root = new BorderPane();
    private final Node rawEditor;
    private final Supplier<String> text;
    private final Consumer<String> apply;
    private final BooleanSupplier category;
    /// Which catalog fields this pane's form holds.
    private final Predicate<Field> shows;
    private final ToggleButton rawToggle =
            new ToggleButton(null, new SvgNode(MDITechnology.CODE_BRACES.path(), 16));
    private final Button reloadButton = new Button(null, new SvgNode(MDIInterface.RELOAD.path(), 16));
    private final Button detachButton = new Button(null, new SvgNode(MDIInterface.OPEN_IN_NEW.path(), 16));
    /// The form over the current buffer; null while the raw view shows or the
    /// file has no frontmatter to configure.
    private @Nullable ConfigForm form;
    private Runnable onDetachToggle = () -> { };
    /// Told before this pane takes the raw editor, so a sibling pane showing
    /// it gives it up first — the editor is one node.
    private Runnable onRawShown = () -> { };
    /// Set while an invalid form un-toggles the raw view, so that switch back
    /// does not rebuild the form and throw away what the user typed.
    private boolean revertingToggle;
    /// Whether the focus is in this pane, in a focused window — when it goes,
    /// the form is saved.
    private boolean focusInside;
    /// The window of the pane's scene, whose focus is watched.
    private @Nullable Window window;
    private final InvalidationListener focusMoved = obs -> updateFocusInside();
    private final ChangeListener<@Nullable Window> windowChanged = (obs, old, now) -> watchWindow(now);
    /// The settings' host destinations a host field offers, read per build.
    private final Supplier<List<String>> hosts;
    /// The desktop names a desktop field suggests, read per build.
    private final Supplier<List<String>> desktops;
    /// Re-reads the desktop names for a desktop field's refresh button.
    private final Supplier<CompletableFuture<List<String>>> refreshDesktops;
    /// What the browser URLs do.
    private final BrowserUrlActions urlActions;
    /// The browser's tab in view, for the forms built later on.
    private @Nullable String browserTab;

    /// `id` prefixes the toolbar controls' ids (`config-raw-toggle`); `shows`
    /// picks the catalog fields of this pane. `rawEditor` is the editor lane's
    /// frontmatter editor (it auto-saves on focus loss); `text` and `apply`
    /// read and replace-and-save the whole buffer.
    ConfigFormPane(String id, Predicate<Field> shows, Node rawEditor, Supplier<String> text,
            Consumer<String> apply, BooleanSupplier category,
            Supplier<List<String>> hosts, Supplier<List<String>> desktops,
            Supplier<CompletableFuture<List<String>>> refreshDesktops, BrowserUrlActions urlActions) {
        this.shows = shows;
        this.rawEditor = rawEditor;
        this.text = text;
        this.apply = apply;
        this.category = category;
        this.hosts = hosts;
        this.desktops = desktops;
        this.refreshDesktops = refreshDesktops;
        this.urlActions = urlActions;
        // Icon-only like every pane toolbar's controls; the tooltip names each.
        // [impl->dsn~task-field-form~5]
        rawToggle.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        rawToggle.setId(id + "-raw-toggle");
        reloadButton.setId(id + "-reload-button");
        detachButton.setId(id + "-pop-out-button");
        for (Button button : List.of(reloadButton, detachButton)) {
            button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            // Not focus-traversable, as the editor lane's own buttons: a click
            // must not move focus out of the raw editor before it saved.
            button.setFocusTraversable(false);
        }
        rawToggle.setTooltip(new Tooltip("Raw YAML — edit the frontmatter as YAML, the fallback for anything the form does not cover"));
        reloadButton.setTooltip(new Tooltip("Reload — rebuild the form from the file, dropping changes "
                + "not saved yet (they are saved when the focus leaves the pane)"));
        detachButton.setTooltip(new Tooltip("Pop out — move the configuration into a window of its own"));
        reloadButton.setOnAction(event -> reload(true));
        detachButton.setOnAction(event -> onDetachToggle.run());
        rawToggle.selectedProperty().addListener((obs, was, raw) -> switchView(raw));
        // Auto-save: leaving the pane — another pane, another tab, another
        // window — writes the changed fields. The buttons are not focus
        // traversable, so Reload is clicked without leaving.
        //
        // Tracked from the scene's focus owner and the window's focus rather
        // than Node.focusWithin: that flag stayed true with the task list
        // owning the focus once the form had been rebuilt under it.
        root.sceneProperty().addListener((obs, old, scene) -> watchScene(old, scene));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(6, rawToggle, reloadButton, spacer, detachButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 6, 4, 6));
        header.getStyleClass().add("panel-header");
        root.setTop(header);
        reload(true);
    }

    /// The pane's node — for its dock tab, or the window it pops out into.
    Node getRoot() {
        return root;
    }

    void setOnDetachToggle(Runnable toggle) {
        this.onDetachToggle = toggle;
    }

    void setOnRawShown(Runnable onRawShown) {
        this.onRawShown = onRawShown;
    }

    /// Back to the form view, if the raw editor shows — a sibling pane is
    /// about to take it.
    void leaveRaw() {
        rawToggle.setSelected(false);
    }

    /// Highlights the browser URL of the tab in view (null: none known).
    // [impl->dsn~browser-url-links~1]
    void showBrowserTab(@Nullable String tabUrl) {
        browserTab = tabUrl;
        ConfigForm current = form;
        if (current != null) {
            current.showBrowserTab(tabUrl);
        }
    }

    /// Writes what the form holds now, without waiting for the focus to leave
    /// — before the editor switches to another file.
    void commit() {
        applyForm();
    }

    /// A popped-out window has Dock back in its own title bar, so the pane's
    /// Pop out button is hidden while detached.
    void setDetached(boolean detached) {
        detachButton.setVisible(!detached);
        detachButton.setManaged(!detached);
    }

    private void watchScene(@Nullable Scene old, @Nullable Scene scene) {
        if (old != null) {
            old.focusOwnerProperty().removeListener(focusMoved);
            old.windowProperty().removeListener(windowChanged);
        }
        if (scene != null) {
            scene.focusOwnerProperty().addListener(focusMoved);
            scene.windowProperty().addListener(windowChanged);
        }
        watchWindow(scene == null ? null : scene.getWindow());
    }

    private void watchWindow(@Nullable Window now) {
        Window previous = window;
        if (previous != null) {
            previous.focusedProperty().removeListener(focusMoved);
        }
        window = now;
        if (now != null) {
            now.focusedProperty().addListener(focusMoved);
        }
        updateFocusInside();
    }

    /// Saves the form when the focus has just left the pane.
    private void updateFocusInside() {
        Scene scene = root.getScene();
        Window current = window;
        Node owner = scene == null ? null : scene.getFocusOwner();
        boolean inside = current != null && current.isFocused() && owner != null && contains(owner);
        if (inside == focusInside) {
            return;
        }
        focusInside = inside;
        if (!inside) {
            applyForm();
        }
    }

    private boolean contains(Node node) {
        for (Node at = node; at != null; at = at.getParent()) {
            if (at == root) {
                return true;
            }
        }
        return false;
    }

    /// Rebuilds the form from the current buffer — after a file switch, a
    /// revert, a save. The raw view needs nothing: it is the buffer.
    ///
    /// Without `force` a form the user is in stays as it is: a sibling pane
    /// saving as the focus moves here must not swap the field just clicked
    /// for a new one. The fields of the two panes are disjoint, and saving
    /// patches only changed keys, so the form loses nothing by waiting for
    /// its own save.
    void reload(boolean force) {
        if (rawToggle.isSelected() || (!force && form != null && focusInside)) {
            return;
        }
        String content = text.get();
        if (Frontmatter.block(content) == null) {
            form = null;
            Label none = new Label("This file has no '---' frontmatter to configure.");
            none.getStyleClass().add(Styles.TEXT_MUTED);
            root.setCenter(new StackPane(none));
            return;
        }
        List<Field> fields = (category.getAsBoolean() ? FrontmatterCatalog.CATEGORY : FrontmatterCatalog.TASK)
                .stream().filter(shows).toList();
        ConfigForm built = new ConfigForm(fields, content, hosts.get(), desktops.get(), refreshDesktops, urlActions,
                this::applyForm);
        built.showBrowserTab(browserTab);
        form = built;
        ScrollPane scroll = new ScrollPane(built.render());
        scroll.setFitToWidth(true);
        root.setCenter(scroll);
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
                Alerts.plain(Alerts.windowOf(root), Alert.AlertType.ERROR, reason).show();
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
            reloadButton.setDisable(true);
            onRawShown.run();
            root.setCenter(rawEditor);
        } else {
            reloadButton.setDisable(false);
            reload(true);
        }
    }
}
