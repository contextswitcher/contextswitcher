package com.contextswitcher.ui;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitFocused;
import static com.contextswitcher.ui.UiTestSupport.sleep;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The configuration form's *Virtual desktop* type-ahead (`dsn~task-field-form~4`):
/// typing part of a known name opens the suggestions, and Down + Enter picks
/// one into the field.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~task-field-form~4]
@Tag("ui")
@TestFxApplication(SuggestionsUiTest.TestApp.class)
class SuggestionsUiTest {

    private static final AtomicReference<TextField> FIELD = new AtomicReference<>();

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            TextField field = new TextField();
            Suggestions.install(field, List.of("misc", "JabRef", "contextswitcher"));
            FIELD.set(field);
            stage.setScene(new Scene(new VBox(field), 400, 300));
            stage.show();
        }
    }

    @Test
    void typingSuggestsAndEnterPicks() {
        TextField field = FIELD.get();
        FX_ROBOT.mouse().moveTo(field).click();
        awaitFocused(field, "the desktop field");
        FX_ROBOT.keyboard().type(KeyCode.J, KeyCode.A, KeyCode.B);
        FX_ROBOT.keyboard().type(KeyCode.DOWN, KeyCode.ENTER);

        long deadline = System.currentTimeMillis() + 5_000;
        while (!"JabRef".equals(textOf(field)) && System.currentTimeMillis() < deadline) {
            sleep();
        }
        assertThat(textOf(field)).isEqualTo("JabRef");
    }

    private static String textOf(TextField field) {
        AtomicReference<String> text = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            text.set(field.getText());
            done.countDown();
        });
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return text.get();
    }
}
