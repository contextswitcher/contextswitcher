package com.contextswitcher.ui;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javax.imageio.ImageIO;

import com.contextswitcher.queue.Attachments;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.TextArea;
import javafx.scene.control.skin.TextAreaSkin;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.jspecify.annotations.Nullable;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The queue boxes' side of `dsn~attachment-image-hover~2`: the pointer picks
/// the marker it rests on, so a text carrying two attachments previews the
/// hovered one — not both.
///
/// Driven through the Add-task dialog's description field because the lookup
/// needs a laid-out text area: the skin is what maps a point to a text index.
// [utest->dsn~attachment-image-hover~2]
// [utest->dsn~attachment-copy-path~1]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class AttachmentPreviewHoverUiTest {

    @Test
    void theHoveredMarkerDecidesWhichPictureIsPreviewed() throws Exception {
        Path first = png("first.png");
        Path second = png("second.png");
        UiTestSupport.openAddTask();
        TextArea field = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(TextArea.class).fromAll()
                        .filter(area -> "add-task-description".equals(area.getId())).findFirst(),
                "add-task description field");

        String text = "one " + Attachments.marker(first)
                + "\ntwo " + Attachments.marker(second)
                + "\nsent `/export/home/u/.contextswitcher/attachments/third.png`"
                + "\nplain";
        onFx(() -> field.setText(text));

        // The skin lays the new text out on the next pulse, so poll rather
        // than read once.
        assertThat(UiTestSupport.awaitPresent(
                () -> Optional.ofNullable(pathUnder(field, text.indexOf(first.toString()) + 2)),
                "preview of the first marker"))
                .isEqualTo(first);
        assertThat(pathUnder(field, text.indexOf(second.toString()) + 2))
                .as("the second marker previews its own picture")
                .isEqualTo(second);
        assertThat(pathUnder(field, text.indexOf("third.png") + 2))
                .as("an uploaded attachment's remote path previews the local copy")
                .isEqualTo(UiTestSupport.tasksDir.resolve("third.png"));
        assertThat(pathUnder(field, text.indexOf("plain") + 2))
                .as("plain text previews nothing")
                .isNull();

        Rectangle2D box = onFx(() -> ((TextAreaSkin) field.getSkin())
                .getCharacterBounds(text.indexOf(second.toString()) + 2));
        assertThat(UiTestSupport.copyPathAt(field, box.getMinX() + box.getWidth() / 2, box.getMinY() + box.getHeight() / 2))
                .as("right-click on the second marker copies its path")
                .isEqualTo(second.toString());
    }

    /// The marker path the hover lookup finds at the character `index`, using
    /// that character's own screen box as the pointer position.
    private static @Nullable Path pathUnder(TextArea field, int index) {
        return onFx(() -> {
            if (!(field.getSkin() instanceof TextAreaSkin skin)) {
                return null;
            }
            Rectangle2D box = skin.getCharacterBounds(index);
            return AttachmentPreview.pathAt(
                    field, box.getMinX() + box.getWidth() / 2, box.getMinY() + box.getHeight() / 2,
                    UiTestSupport.tasksDir);
        });
    }

    private static Path png(String name) throws Exception {
        Path file = UiTestSupport.tasksDir.resolve(name);
        ImageIO.write(new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
        return file;
    }

    /// Runs the work on the FX thread — the skin may not be touched from the
    /// test thread.
    private static <T> T onFx(Callable<T> work) {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        try {
            return task.get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void onFx(Runnable work) {
        onFx(() -> {
            work.run();
            return null;
        });
    }
}
