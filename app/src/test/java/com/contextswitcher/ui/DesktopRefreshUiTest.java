package com.contextswitcher.ui;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import com.contextswitcher.tasks.FrontmatterCatalog;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static com.contextswitcher.ui.UiTestSupport.sleep;
import static io.gitlab.fxlabs.testfx.util.FxPredicates.hasText;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The category form's *Virtual desktop* refresh button (`dsn~task-field-form~4`):
/// a desktop created after the form was built is not suggested until the
/// button reads the desktops again, which then opens the suggestions with it.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~task-field-form~4]
@Tag("ui")
@TestFxApplication(DesktopRefreshUiTest.TestApp.class)
class DesktopRefreshUiTest {

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            ConfigForm form = new ConfigForm(FrontmatterCatalog.CATEGORY, """
                    ---
                    pinned: false
                    ---
                    """, List.of(), List.of("misc"),
                    () -> CompletableFuture.completedFuture(List.of("misc", "Fresh desk")));
            stage.setScene(new Scene(new ScrollPane(form.render()), 600, 700));
            stage.show();
        }
    }

    @Test
    void refreshReadsTheDesktopsAgainAndSuggestsThem() {
        TextField field = awaitPresent(() -> FX_ROBOT.selectNodes(TextField.class).fromAll()
                .filter(node -> "field-desktop".equals(node.getId())).findFirst(), "the desktop field");
        Button refresh = awaitPresent(() -> FX_ROBOT.selectNodes(Button.class).fromAll()
                .filter(node -> "field-desktop-refresh".equals(node.getId())).findFirst(), "the refresh button");
        FX_ROBOT.mouse().moveTo(refresh).click();
        // The suggestions open on the empty field with the desktop read just now.
        FX_ROBOT.mouse().moveTo(awaitPresent(() -> FX_ROBOT.selectNodes(Label.class).fromAll()
                .filter(hasText("Fresh desk")).findFirst(), "the new desktop's suggestion")).click();

        long deadline = System.currentTimeMillis() + 5_000;
        while (!"Fresh desk".equals(textOf(field)) && System.currentTimeMillis() < deadline) {
            sleep();
        }
        assertThat(textOf(field)).isEqualTo("Fresh desk");
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
