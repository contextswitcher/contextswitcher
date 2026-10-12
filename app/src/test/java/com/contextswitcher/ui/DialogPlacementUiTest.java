package com.contextswitcher.ui;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// An ownerless dialog opens over the main window (`dsn~dialog-placement~1`).
/// One screen is enough to tell: the main window sits at the screen's left
/// edge, where JavaFX's own placement — the screen's center — lies outside it.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~dialog-placement~1]
@Tag("ui")
@TestFxApplication(DialogPlacementUiTest.TestApp.class)
class DialogPlacementUiTest {

    private static final AtomicReference<Stage> MAIN = new AtomicReference<>();

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            Rectangle2D screen = Screen.getPrimary().getVisualBounds();
            stage.setScene(new Scene(new StackPane(), 360, 300));
            stage.setX(screen.getMinX());
            stage.setY(screen.getMinY());
            DialogPlacement.installEverywhere(stage);
            stage.show();
            MAIN.set(stage);
        }
    }

    @Test
    void anOwnerlessAlertOpensOverTheMainWindow() throws Exception {
        CompletableFuture<Rectangle2D> shown = new CompletableFuture<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Cannot create the file");
            alert.setOnShown(event -> {
                Stage window = (Stage) alert.getDialogPane().getScene().getWindow();
                shown.complete(new Rectangle2D(window.getX(), window.getY(),
                        window.getWidth(), window.getHeight()));
                Platform.runLater(alert::close);
            });
            alert.show();
        });
        Rectangle2D dialog = shown.get(10, TimeUnit.SECONDS);
        Stage main = MAIN.get();
        double centerX = dialog.getMinX() + dialog.getWidth() / 2;
        // Centered over the main window, give or take the decorations the
        // pre-show size estimate leaves out.
        assertThat(centerX).isBetween(main.getX(), main.getX() + main.getWidth());
        assertThat(dialog.getMinY()).isBetween(main.getY(), main.getY() + main.getHeight());
    }

    @Test
    void positionIsCenteredAndKeptOnTheScreen() {
        Rectangle2D screen = new Rectangle2D(1920, 0, 1920, 1080);
        assertThat(DialogPlacement.position(2000, 100, 800, 600, 400, 300, screen))
                .isEqualTo(new Rectangle2D(2200, 200, 400, 300));
        // Owner hanging over the screen's left edge: the dialog stays on it.
        assertThat(DialogPlacement.position(1800, 100, 200, 600, 400, 300, screen).getMinX())
                .isEqualTo(1920);
    }
}
