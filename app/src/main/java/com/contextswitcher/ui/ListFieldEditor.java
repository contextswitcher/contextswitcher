package com.contextswitcher.ui;

import java.util.List;

import atlantafx.base.theme.Styles;

import com.contextswitcher.tasks.Task;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.jspecify.annotations.Nullable;

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
///
/// With [BrowserUrlActions] the entries are URLs (`dsn~browser-url-links~1`): a
/// row reads as a link that shows the page in the browser and drags to another
/// place, its text field only appears for adding an entry or through the row's
/// edit button.
// [impl->dsn~task-field-form~5]
final class ListFieldEditor extends VBox {

    /// A row showing the page of the browser's tab in view (`main.css`).
    private static final PseudoClass BROWSER_TAB = PseudoClass.getPseudoClass("browser-tab");

    /// A row a dragged link is over (`main.css`).
    private static final PseudoClass DROP_TARGET = PseudoClass.getPseudoClass("drop-target");

    private final @Nullable BrowserUrlActions actions;
    /// The browser's tab in view, re-matched when an entry is edited.
    private @Nullable String browserTab;
    /// The row being dragged, so a drag from anywhere else is not accepted.
    private @Nullable HBox dragged;
    private Runnable onReordered = () -> { };

    ListFieldEditor(List<String> entries) {
        this(entries, null);
    }

    ListFieldEditor(List<String> entries, @Nullable BrowserUrlActions actions) {
        super(2);
        this.actions = actions;
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

    /// Told when the entries changed their order, which leaves no focus to
    /// lose: the form cannot wait for that to save.
    void setOnReordered(Runnable onReordered) {
        this.onReordered = onReordered;
    }

    /// Replaces the entries.
    // [impl->dsn~browser-url-order~1]
    void setValues(List<String> entries) {
        getChildren().clear();
        entries.forEach(this::addRow);
        addRow("");
        highlight(browserTab);
    }

    /// Highlights the entry the browser's tab shows: an exact hit, else the
    /// longest URL it is under, as a repository and its pull request are
    /// routinely listed together.
    // [impl->dsn~browser-url-links~1]
    void highlight(@Nullable String tabUrl) {
        browserTab = tabUrl;
        List<Integer> scores = getChildren().stream()
                .map(row -> tabUrl == null || field(row).getText().isBlank()
                        ? 0
                        : matchScore(tabUrl, ConfigForm.urlEntry(field(row).getText()).url()))
                .toList();
        int best = scores.stream().max(Integer::compare).orElse(0);
        for (int i = 0; i < scores.size(); i++) {
            getChildren().get(i).pseudoClassStateChanged(BROWSER_TAB, best > 0 && scores.get(i) == best);
        }
    }

    private static int matchScore(String tabUrl, String url) {
        int match = Task.urlMatch(tabUrl, url);
        return match == 2 ? Integer.MAX_VALUE : match * url.length();
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
        row.getStyleClass().add("list-field-row");
        row.setAlignment(Pos.CENTER_LEFT);
        // The trailing empty row has nothing to delete; it keeps the slot so
        // every field ends at the same x.
        delete.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> row.isHover() && !(isLast(row) && field.getText().isEmpty()),
                row.hoverProperty(), field.textProperty(), getChildren()));
        delete.setOnAction(event -> remove(row));
        if (actions != null) {
            addLink(row, field, actions);
        }
        field.textProperty().addListener((obs, old, now) -> {
            if (isLast(row) && !now.isEmpty()) {
                addRow("");
            }
        });
        field.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                // A row reading as a link has no field to move to.
                getChildren().stream().skip(getChildren().indexOf(row) + 1L)
                        .map(ListFieldEditor::field).filter(Node::isVisible).findFirst()
                        .ifPresent(Node::requestFocus);
                event.consume();
            }
        });
        getChildren().add(row);
        renumber();
    }

    /// Turns the row into a link with an edit button next to the delete one,
    /// both on hover like the web's link lists. The field takes the link's
    /// place while it has the focus, and while it has nothing to link to.
    // [impl->dsn~browser-url-links~1]
    private void addLink(HBox row, TextField field, BrowserUrlActions urls) {
        BooleanProperty editing = new SimpleBooleanProperty();
        BooleanBinding showField = editing.or(Bindings.createBooleanBinding(
                () -> field.getText().isBlank(), field.textProperty()));
        field.visibleProperty().bind(showField);
        field.managedProperty().bind(showField);
        field.focusedProperty().addListener((obs, was, focused) -> editing.set(focused));
        field.textProperty().addListener((obs, old, now) -> highlight(browserTab));
        Runnable edit = () -> {
            editing.set(true);
            field.requestFocus();
        };

        Hyperlink link = new Hyperlink();
        link.getStyleClass().add("monospace");
        link.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(link, Priority.ALWAYS);
        link.textProperty().bind(field.textProperty().map(line -> ConfigForm.urlEntry(line).display()));
        link.visibleProperty().bind(showField.not());
        link.managedProperty().bind(showField.not());
        link.setTooltip(pageHint(field, urls));
        installDrag(row, link, field);
        link.setOnAction(event -> {
            urls.open(field.getText());
            // Keeps the link's colour: it is a jump to a tab, not a visited page.
            link.setVisited(false);
        });
        link.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.F2) {
                edit.run();
                event.consume();
            }
        });
        row.getChildren().add(1, link);

        Button editButton = new Button(null, new SvgNode(MDIInterface.PENCIL_OUTLINE.path(), 14));
        editButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        editButton.setFocusTraversable(false);
        editButton.setTooltip(new Tooltip("Edit this entry"));
        editButton.visibleProperty().bind(row.hoverProperty().and(showField.not()));
        editButton.setOnAction(event -> edit.run());
        row.getChildren().add(2, editButton);
    }

    /// The link's tooltip: the entry's own description, else the page's title
    /// once that is known, else the address.
    private static Tooltip pageHint(TextField field, BrowserUrlActions urls) {
        Tooltip hint = new Tooltip();
        hint.setOnShowing(event -> {
            Task.UrlEntry entry = ConfigForm.urlEntry(field.getText());
            String title = entry.title();
            if (title != null && !title.isBlank()) {
                hint.setText(title);
            } else {
                hint.setText(entry.url());
                urls.pageTitle(entry.url(), hint::setText);
            }
        });
        return hint;
    }

    /// Drags the row by its link, onto the place of the row it is dropped on;
    /// the add row stands for the end of the list.
    // [impl->dsn~browser-url-order~1]
    private void installDrag(HBox row, Hyperlink link, TextField field) {
        link.setOnDragDetected(event -> {
            Dragboard dragboard = link.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(field.getText());
            dragboard.setContent(content);
            dragboard.setDragView(link.snapshot(null, null));
            dragged = row;
            event.consume();
        });
        link.setOnDragDone(event -> dragged = null);
        row.setOnDragOver(event -> {
            if (dragged != null && dragged != row) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        row.setOnDragEntered(event -> row.pseudoClassStateChanged(DROP_TARGET, dragged != null && dragged != row));
        row.setOnDragExited(event -> row.pseudoClassStateChanged(DROP_TARGET, false));
        row.setOnDragDropped(event -> {
            HBox source = dragged;
            if (source != null) {
                move(source, row);
                event.setDropCompleted(true);
                event.consume();
            }
        });
    }

    /// Puts `source` in the place of `target`; package-private for the test,
    /// as the headless platform runs no drag gesture.
    void move(HBox source, HBox target) {
        int place = Math.min(getChildren().indexOf(target), getChildren().size() - 2);
        getChildren().remove(source);
        getChildren().add(place, source);
        renumber();
        onReordered.run();
    }

    private void remove(HBox row) {
        if (isLast(row)) {
            field(row).clear();
            return;
        }
        getChildren().remove(row);
        renumber();
        // The form saves when the focus leaves it, and a hover button takes no
        // focus: without it inside, a removal would never be written.
        field(getChildren().getLast()).requestFocus();
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
