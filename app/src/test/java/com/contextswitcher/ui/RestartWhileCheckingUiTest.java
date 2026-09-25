package com.contextswitcher.ui;

import java.lang.reflect.Field;
import java.util.List;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.contextswitcher.Main;
import com.contextswitcher.local.AppUpdate;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static org.assertj.core.api.Assertions.assertThat;

/// A fetch that never answers leaves "Checking remote …" spinning, yet
/// *Restart to update* is pressable meanwhile — a hanging remote must not
/// hold the restart hostage.
// [utest->dsn~restart-to-update~11]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class RestartWhileCheckingUiTest {

    @Test
    void restartIsLiveWhileRemoteCheckRuns() {
        Platform.runLater(() -> {
            MainWindow window = mainWindow();
            window.setOnCheckRemote(() -> { });
            window.showWhatsNew("What's new (checking)", List.of(), true);
        });
        Stage dialog = awaitPresent(() -> Window.getWindows().stream()
                .filter(w -> w instanceof Stage s && "What's new (checking)".equals(s.getTitle()) && s.getHeight() > 0)
                .map(Stage.class::cast).findFirst(), "the What's new window");
        String label = AppUpdate.actionLabel(AppUpdate.startedByLoop());
        Button restart = (Button) dialog.getScene().getRoot().lookupAll(".button").stream()
                .filter(node -> node instanceof Button b && label.equals(b.getText()))
                .findFirst().orElseThrow();
        assertThat(dialog.getScene().getRoot().lookupAll(".progress-bar")).anyMatch(node -> node.isVisible());
        assertThat(restart.isDisabled()).isFalse();
        Platform.runLater(dialog::close);
    }

    private static MainWindow mainWindow() {
        try {
            Field field = Main.class.getDeclaredField("window");
            field.setAccessible(true);
            return (MainWindow) field.get(UiTestSupport.app);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
