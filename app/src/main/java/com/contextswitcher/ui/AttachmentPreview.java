package com.contextswitcher.ui;

import java.nio.file.Path;
import java.util.function.Function;

import com.contextswitcher.queue.Attachments;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.TextAreaSkin;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.HitInfo;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Thumbnails for the attachment markers (`[image: …]`, `[file: …]`) a text
/// carries: hovering a marker pops up the picture that marker points at, in
/// the editor lane and in the queue pane's text boxes alike.
// [impl->dsn~attachment-image-hover~2]
final class AttachmentPreview {

    /// Thumbnail edge: big enough to recognise a screenshot, small enough to
    /// stay a popup rather than a second window.
    private static final int SIZE = 480;

    private AttachmentPreview() {
    }

    /// The marker's file scaled into a thumbnail, or null when it is missing
    /// or not an image (a `[file: …]` marker for a zip, a hand-written path).
    // ponytail: decoded per hover-enter / text change, no cache — a screenshot
    // PNG is a few ms; add a Path->Image map if a large picture ever stutters.
    static @Nullable Image thumbnail(Path path) {
        try {
            Image image = new Image(path.toUri().toString(), SIZE, SIZE, true, true);
            return image.isError() ? null : image;
        } catch (RuntimeException e) {
            Logger.debug("Cannot preview {}: {}", path, e.getMessage());
            return null;
        }
    }

    /// Makes `area` show the picture of the marker under the pointer, the way
    /// the editor lane does for its own markers — a text with several
    /// attachments shows the hovered one, not all of them.
    // [impl->dsn~attachment-image-hover~2]
    static void install(TextArea area, Path attachmentsDir) {
        Hover hover = new Hover();
        area.setOnMouseMoved(event -> hover.show(area, event, pathAt(area, event.getX(), event.getY(), attachmentsDir)));
        area.setOnMouseExited(event -> hover.hide());
        area.setOnScroll(event -> hover.hide());
        area.textProperty().subscribe(text -> hover.hide());
        installCopyPath(area, event -> pathAt(area, event.getX(), event.getY(), attachmentsDir), hover::hide);
    }

    /// Right-clicking a marker offers "Copy path" for the file it points at,
    /// instead of the control's own menu; anywhere else that menu shows as
    /// before. A filter, so the control's menu never sees a handled click.
    // [impl->dsn~attachment-copy-path~1]
    static void installCopyPath(Node owner, Function<ContextMenuEvent, @Nullable Path> lookup,
            Runnable beforeShow) {
        owner.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            Path path = lookup.apply(event);
            if (path == null) {
                return;
            }
            event.consume();
            beforeShow.run();
            MenuItem copy = new MenuItem("Copy path");
            copy.setOnAction(action -> {
                ClipboardContent content = new ClipboardContent();
                content.putString(path.toString());
                Clipboard.getSystemClipboard().setContent(content);
            });
            new ContextMenu(copy).show(owner, event.getScreenX(), event.getScreenY());
        });
    }

    /// The attachment path at control-local coordinates — a marker's, or an
    /// uploaded attachment's local copy — or null when the pointer rests on
    /// plain text (or the area has no skin yet, before it is first laid out).
    static @Nullable Path pathAt(TextArea area, double x, double y, Path attachmentsDir) {
        if (!(area.getSkin() instanceof TextAreaSkin skin)) {
            return null;
        }
        HitInfo hit = skin.getIndex(x, y);
        String text = area.getText();
        int index = hit.getCharIndex();
        if (index < 0 || index >= text.length()) {
            return null;
        }
        return Attachments.pathAt(text, index, attachmentsDir);
    }

    /// The pop-up itself: the hovered marker's thumbnail next to the pointer.
    /// Re-entering the same marker leaves it where it is (no flicker while the
    /// pointer travels along the marker text); a marker whose file does not
    /// load as an image shows nothing rather than an error.
    // [impl->dsn~attachment-image-hover~2]
    static final class Hover {

        private final Tooltip popup = new Tooltip();
        private @Nullable Path shown;

        /// Shows `path`'s thumbnail beside the pointer, hiding whatever was up
        /// before; a null path just hides.
        void show(Node owner, MouseEvent event, @Nullable Path path) {
            show(owner, event.getScreenX(), event.getScreenY(), path);
        }

        /// Same, beside a pointer at screen coordinates.
        void show(Node owner, double screenX, double screenY, @Nullable Path path) {
            if (path != null && path.equals(shown)) {
                return;
            }
            hide();
            if (path == null) {
                return;
            }
            Image image = thumbnail(path);
            if (image == null) {
                return;
            }
            shown = path;
            popup.setGraphic(new ImageView(image));
            popup.show(owner, screenX + 16, screenY + 16);
        }

        void hide() {
            if (shown != null) {
                popup.hide();
                popup.setGraphic(null);
                shown = null;
            }
        }
    }
}
