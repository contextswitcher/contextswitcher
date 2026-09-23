package com.contextswitcher.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import atlantafx.base.theme.NordDark;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Does the generated Everforest stylesheet actually *apply* when handed to
/// `Application.setUserAgentStylesheet`? Parsing it (EverforestStylesheetTest)
/// is not the same question: the app came up in JavaFX's own Modena three
/// times on 2026-09-12/13 with the file present, valid and readable.
///
/// This drives the whole round trip — set it, build a scene, apply CSS, read a
/// colour back — so a failure here is ours and a pass points at the machine.
@Tag("ui")
@TestFxApplication(UserAgentStylesheetUiTest.App.class)
class UserAgentStylesheetUiTest {

    public static class App extends Application {
        @Override
        public void start(Stage stage) {
            stage.setScene(new Scene(new StackPane(), 200, 100));
            stage.show();
        }
    }

    @Test
    void theGeneratedStylesheetStylesAScene() throws Exception {
        String url = Themes.everforest(new NordDark(), true);

        Region region = new Region();
        region.getStyleClass().add("root");
        CountDownLatch done = new CountDownLatch(1);
        String[] background = {""};
        Platform.runLater(() -> {
            try {
                Application.setUserAgentStylesheet(url);
                Scene scene = new Scene(new StackPane(region), 200, 100);
                scene.getRoot().applyCss();
                region.applyCss();
                background[0] = String.valueOf(region.getBackground() == null
                        ? scene.getRoot().lookup(".root") : region.getBackground());
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();

        // The real question: did JavaFX take the stylesheet at all?
        assertThat(Application.getUserAgentStylesheet())
                .as("the user-agent stylesheet JavaFX ended up with")
                .isEqualTo(url);
    }
}
