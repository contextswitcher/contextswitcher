package com.contextswitcher.ui;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.contextswitcher.Main;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The badge on the update button (`dsn~restart-to-update~11`) counts the
/// pending changes the click shows, not the commits behind; with nothing
/// pending there is no badge while the tooltip still names the commits, and
/// appearing does not widen the button.
// [utest->dsn~restart-to-update~11]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class UpdateBadgeUiTest {

    private static final WhatsNew.Item ITEM = new WhatsNew.Item("me", "2026-09-13", "Added", List.of("**x**"));

    @Test
    void badgeCountsThePendingChangesWithoutWideningTheButton() throws Exception {
        MainWindow window = UiTestSupport.awaitPresent(
                () -> java.util.Optional.ofNullable((MainWindow) field(UiTestSupport.app, Main.class, "window")),
                "the main window");
        UpdateNews news = callFx(() -> (UpdateNews) field(window, MainWindow.class, "updateNews"));
        Button update = callFx(news::button);
        Label badge = callFx(() -> (Label) update.getGraphic().lookup(".update-badge"));
        double width = callFx(update::getWidth);

        callFx(() -> {
            window.showUpdateAvailable(7);
            window.showPendingNews(List.of(ITEM), null, () -> { });
            return null;
        });
        UiTestSupport.sleep();
        assertThat(callFx(badge::getText)).isEqualTo("1");
        assertThat(callFx(() -> badge.isVisible() && badge.getScene() != null)).isTrue();
        assertThat(callFx(update::getWidth)).isEqualTo(width);

        // Merge-only update, or the news already read: no badge, still an update.
        callFx(() -> {
            window.showPendingNews(List.of(), null, () -> { });
            return null;
        });
        assertThat(callFx(badge::isVisible)).isFalse();
        assertThat(callFx(() -> update.getTooltip().getText())).contains("(7 commits)");
    }

    private static Object field(Object owner, Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T callFx(Supplier<T> body) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(body.get());
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }
}
