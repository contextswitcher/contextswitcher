package com.contextswitcher.ui;

import java.util.concurrent.FutureTask;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// Ctrl+V into the queue's add box (`dsn~message-queue-ui~28`): a clipboard
/// with only an image pastes an `[image: …]` marker, one with text beside the
/// image pastes the text — OneNote copies a link as both, and the link was
/// arriving as a picture of itself (field report 2026-11-24).
// [utest->dsn~message-queue-ui~28]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class QueuePasteUiTest {

    private static final String LINK = "https://github.com/example/repo";

    @Test
    void textBesideAnImagePastesTheText() throws Exception {
        TextArea addBox = QueueComposeKeysUiTest.selectAlphaAndFocusAddBox();
        setClipboard(LINK);
        FX_ROBOT.keyboard().pressAndThenRelease(KeyCode.CONTROL, KeyCode.V);
        QueueComposeKeysUiTest.fxSync();
        assertThat(addBox.getText()).isEqualTo(LINK);
    }

    @Test
    void anImageAlonePastesItsMarker() throws Exception {
        TextArea addBox = QueueComposeKeysUiTest.selectAlphaAndFocusAddBox();
        setClipboard(null);
        FX_ROBOT.keyboard().pressAndThenRelease(KeyCode.CONTROL, KeyCode.V);
        QueueComposeKeysUiTest.fxSync();
        assertThat(addBox.getText()).startsWith("[image: ").endsWith(".png]");
    }

    /// Puts a small opaque image on the system clipboard, with `text`
    /// beside it unless null.
    private static void setClipboard(@org.jspecify.annotations.Nullable String text) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            WritableImage image = new WritableImage(4, 4);
            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    image.getPixelWriter().setColor(x, y, Color.CORNFLOWERBLUE);
                }
            }
            ClipboardContent content = new ClipboardContent();
            content.putImage(image);
            if (text != null) {
                content.putString(text);
            }
            Clipboard.getSystemClipboard().setContent(content);
            return null;
        });
        Platform.runLater(task);
        task.get();
    }
}
