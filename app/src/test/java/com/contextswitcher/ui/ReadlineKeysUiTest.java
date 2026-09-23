package com.contextswitcher.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.RichTextModel;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The bash cursor chords in a text input (`dsn~readline-keys~1`), pressed for
/// real: the scene filter has to beat the control's own bindings, which only a
/// live scene and a real key press can show.
///
/// Drives a bare [TextArea] in its own scene rather than booting
/// [com.contextswitcher.Main]: the filter is installed per scene and knows
/// nothing about the app around it, and the setting is read through
/// [ReadlineKeys#setEnabled] anyway.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~readline-keys~1]
@Tag("ui")
@TestFxApplication(ReadlineKeysUiTest.TestApp.class)
class ReadlineKeysUiTest {

    private static final AtomicReference<TextArea> FIELD = new AtomicReference<>();
    private static final AtomicReference<RichTextArea> EDITOR = new AtomicReference<>();

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            TextArea field = new TextArea();
            RichTextArea editor = new RichTextArea(new RichTextModel());
            Scene scene = new Scene(new VBox(field, editor), 400, 300);
            ReadlineKeys.install(scene);
            stage.setScene(scene);
            stage.show();
            field.requestFocus();
            FIELD.set(field);
            EDITOR.set(editor);
        }
    }

    /// The flag is application-wide and outlives a test method — the last test
    /// leaves it off, so every test states the mode it needs.
    @BeforeEach
    void chordsOn() {
        ReadlineKeys.setEnabled(true);
    }

    @Test
    void chordsMoveTheCaretWithinItsOwnLine() {
        seed("one two\nthree four", 12);

        press(KeyCode.CONTROL, KeyCode.A);
        assertThat(caret()).as("Ctrl+A: start of the caret's line").isEqualTo(8);
        press(KeyCode.CONTROL, KeyCode.E);
        assertThat(caret()).as("Ctrl+E: end of that line, not of the text").isEqualTo(18);
        press(KeyCode.CONTROL, KeyCode.B);
        assertThat(caret()).as("Ctrl+B: one character back").isEqualTo(17);
        press(KeyCode.CONTROL, KeyCode.F);
        assertThat(caret()).as("Ctrl+F: one character on — the find bar is not opened")
                .isEqualTo(18);
        press(KeyCode.ALT, KeyCode.B);
        assertThat(caret()).as("Alt+B: back to the start of \"four\"").isEqualTo(14);
        press(KeyCode.ALT, KeyCode.F);
        assertThat(caret()).as("Alt+F: on past the end of \"four\"").isEqualTo(18);
    }

    @Test
    void chordsDeleteAroundTheCaret() {
        seed("cd /data/koppor.dev now", 21);
        press(KeyCode.CONTROL, KeyCode.W);
        assertThat(text()).as("Ctrl+W: the whitespace word before the caret")
                .isEqualTo("cd /data/koppor.dev ");

        press(KeyCode.CONTROL, KeyCode.U);
        assertThat(text()).as("Ctrl+U: everything before the caret").isEmpty();

        seed("one two\nthree", 3);
        press(KeyCode.CONTROL, KeyCode.K);
        assertThat(text()).as("Ctrl+K: to the end of the line, break kept")
                .isEqualTo("one\nthree");
        press(KeyCode.CONTROL, KeyCode.D);
        assertThat(text()).as("Ctrl+D: the character after the caret — here the break")
                .isEqualTo("onethree");
        press(KeyCode.CONTROL, KeyCode.H);
        assertThat(text()).as("Ctrl+H: the character before the caret").isEqualTo("onthree");

        seed("keep this", 4);
        press(KeyCode.ALT, KeyCode.D);
        assertThat(text()).as("Alt+D: to the end of the next word").isEqualTo("keep");
    }

    /// The rich editor takes the same chords through its own function tags —
    /// the second branch of the filter, and the focus that sits on an inner
    /// node rather than on the control itself.
    @Test
    void chordsReachTheRichTextEditorToo() {
        seedEditor("one two\nthree four", 4);

        press(KeyCode.CONTROL, KeyCode.A);
        assertThat(editorCaret().offset()).as("Ctrl+A: start of the line").isZero();
        press(KeyCode.CONTROL, KeyCode.E);
        assertThat(editorCaret().offset()).as("Ctrl+E: end of the line").isEqualTo(10);
        assertThat(editorCaret().index()).as("still on the second paragraph").isEqualTo(1);

        press(KeyCode.CONTROL, KeyCode.W);
        assertThat(editorLine(1)).as("Ctrl+W: the word before the caret").isEqualTo("three ");
    }

    @Test
    void ctrlAStaysSelectAllWhileTheSettingIsOff() {
        seed("one two\nthree four", 12);
        ReadlineKeys.setEnabled(false);

        press(KeyCode.CONTROL, KeyCode.A);

        assertThat(onFx(() -> FIELD.get().getSelectedText()))
                .as("the JavaFX default the switch protects")
                .isEqualTo("one two\nthree four");
    }

    /// Puts `content` into the field with the caret at `caret` and the focus
    /// where the robot's keys land.
    private static void seed(String content, int caret) {
        onFx(() -> {
            TextArea field = FIELD.get();
            field.setText(content);
            field.positionCaret(caret);
            field.requestFocus();
            return null;
        });
    }

    /// Puts `content` into the rich editor with the caret in its second
    /// paragraph at `offset`, and the focus there.
    private static void seedEditor(String content, int offset) {
        onFx(() -> {
            RichTextArea editor = EDITOR.get();
            editor.getModel().replace(null, TextPos.ZERO, editor.getModel().getDocumentEnd(),
                    content);
            editor.select(TextPos.ofLeading(1, offset));
            editor.requestFocus();
            return null;
        });
    }

    private static TextPos editorCaret() {
        return onFx(() -> EDITOR.get().getCaretPosition());
    }

    private static String editorLine(int index) {
        return onFx(() -> EDITOR.get().getModel().getPlainText(index));
    }

    private static void press(KeyCode modifier, KeyCode key) {
        FX_ROBOT.keyboard().pressAndThenRelease(modifier, key);
    }

    private static int caret() {
        return onFx(() -> FIELD.get().getCaretPosition());
    }

    private static String text() {
        return onFx(() -> FIELD.get().getText());
    }

    /// Reads/writes the control on the FX thread and waits — which doubles as
    /// the barrier that lets the robot's key press finish first.
    private static <T> T onFx(Supplier<T> work) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(work.get());
            } catch (Throwable e) {
                failure.set(e);
            } finally {
                done.countDown();
            }
        });
        try {
            assertThat(done.await(20, TimeUnit.SECONDS)).as("FX thread ran the work").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        if (failure.get() != null) {
            throw new AssertionError("The FX work failed", failure.get());
        }
        return result.get();
    }
}
