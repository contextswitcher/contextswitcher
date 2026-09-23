package com.contextswitcher.ui;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import javafx.application.Platform;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.contextswitcher.Main;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static org.assertj.core.api.Assertions.assertThat;

/// A long changelog opens the "What's new" window tall enough to show it —
/// past the old fixed 650 pixels — yet no taller than the screen.
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class WhatsNewSizeUiTest {

    @Test
    void longChangelogGrowsWindowUpToScreen() {
        List<WhatsNew.Item> items = IntStream.range(0, 20)
                .mapToObj(n -> new WhatsNew.Item("someone", "2026-09-13", "Changed",
                        List.of("**Bullet " + n + ".**", "A continuation line explaining bullet " + n + ".")))
                .toList();
        Platform.runLater(() -> mainWindow().showWhatsNew("What's new", items, false));
        Stage dialog = awaitPresent(() -> Window.getWindows().stream()
                .filter(w -> w instanceof Stage s && "What's new".equals(s.getTitle()) && s.getHeight() > 0)
                .map(Stage.class::cast).findFirst(), "the What's new window");
        assertThat(dialog.getScene().getHeight())
                .isGreaterThan(650)
                .isLessThanOrEqualTo(Screen.getPrimary().getVisualBounds().getHeight());
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
