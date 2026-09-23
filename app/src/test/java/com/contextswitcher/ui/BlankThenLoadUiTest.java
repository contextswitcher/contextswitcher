package com.contextswitcher.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.contextswitcher.ssh.ProcessSshRunner;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// A task click blanks the terminal pane in the same event and loads the real
/// terminal only afterwards (`dsn~terminal-pane~14`), so the previous task's
/// screen never lingers while the selection does its work.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~terminal-pane~14]
@Tag("ui")
@TestFxApplication(BlankThenLoadUiTest.TestApp.class)
class BlankThenLoadUiTest {

    private static final AtomicReference<Stage> STAGE = new AtomicReference<>();

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            stage.setScene(new Scene(new Pane(), 800, 600));
            stage.show();
            STAGE.set(stage);
        }
    }

    @Test
    void blanksAtOnceAndLoadsLater() throws InterruptedException {
        AtomicBoolean loadedInClick = new AtomicBoolean(true);
        AtomicBoolean coveredInClick = new AtomicBoolean();
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch clicked = new CountDownLatch(1);
        Platform.runLater(() -> {
            TerminalPane pane = new TerminalPane(
                    Executors.newSingleThreadExecutor(), new ProcessSshRunner());
            ((Pane) STAGE.get().getScene().getRoot()).getChildren().setAll(pane.getRoot());
            pane.blankThen(() -> {
                pane.showMessage("real terminal");
                loaded.countDown();
            });
            StackPane center = (StackPane) ((BorderPane) pane.getRoot()).getCenter();
            coveredInClick.set(center.getChildren().size() == 2);
            loadedInClick.set(loaded.getCount() == 0);
            clicked.countDown();
        });

        assertThat(clicked.await(20, TimeUnit.SECONDS)).isTrue();
        assertThat(coveredInClick).as("blank cover up in the click itself").isTrue();
        assertThat(loadedInClick).as("load deferred past the click").isFalse();
        assertThat(loaded.await(20, TimeUnit.SECONDS)).as("load runs a frame later").isTrue();
    }
}
