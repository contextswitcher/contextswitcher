package com.contextswitcher.ui;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The leave confirmation (`dsn~owned-session-close-confirm~1`): it lists the
/// running tasks under their categories, a click on a task stays (answers
/// false) and hands that task over to be shown, and only "Close and end them"
/// answers true.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~owned-session-close-confirm~1]
@Tag("ui")
@TestFxApplication(CloseConfirmationUiTest.TestApp.class)
class CloseConfirmationUiTest {

    private static final AtomicReference<Stage> STAGE = new AtomicReference<>();

    private static final List<CloseConfirmation.Row> ROWS = List.of(
            new CloseConfirmation.Row("2026-sofen/plate", "Tomorrow's plate, session 08"),
            new CloseConfirmation.Row("2026-prog1/karel", "WIF Karel solutions"));

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            stage.setScene(new Scene(new StackPane(), 900, 600));
            stage.show();
            STAGE.set(stage);
        }
    }

    @Test
    void clickingATaskStaysAndPicksIt() throws Exception {
        AtomicReference<String> picked = new AtomicReference<>();
        CompletableFuture<Boolean> answer = open(picked);

        assertThat(texts(Label.class, "close-confirm-category")).containsExactly("2026-prog1", "2026-sofen");
        Hyperlink plate = await(() -> FX_ROBOT.selectNodes(Hyperlink.class).fromAll()
                .filter(link -> "2026-sofen/plate".equals(link.getUserData())).findFirst(), "the task link");
        assertThat(UiTestSupport.truncatedTexts(plate)).isEmpty();
        Platform.runLater(plate::fire);

        assertThat(answer.get(10, TimeUnit.SECONDS)).isFalse();
        assertThat(picked.get()).isEqualTo("2026-sofen/plate");
    }

    @Test
    void closeAndEndThemLeaves() throws Exception {
        AtomicReference<String> picked = new AtomicReference<>();
        CompletableFuture<Boolean> answer = open(picked);

        Button end = UiTestSupport.awaitButton("Close and end them");
        Platform.runLater(end::fire);

        assertThat(answer.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(picked.get()).isNull();
    }

    @Test
    void cancelStays() throws Exception {
        CompletableFuture<Boolean> answer = open(new AtomicReference<>());

        Button cancel = UiTestSupport.awaitButton("Cancel");
        Platform.runLater(cancel::fire);

        assertThat(answer.get(10, TimeUnit.SECONDS)).isFalse();
    }

    @Test
    void nothingRunningLeavesWithoutAsking() {
        assertThat(CloseConfirmation.confirm(null, "Closing", List.of(), id -> { })).isTrue();
    }

    private static CompletableFuture<Boolean> open(AtomicReference<String> picked) {
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        Platform.runLater(() -> answer.complete(
                CloseConfirmation.confirm(STAGE.get(), "Closing ContextSwitcher", ROWS, picked::set)));
        await(() -> FX_ROBOT.selectNodes(Hyperlink.class).fromAll().findFirst(), "the dialog");
        return answer;
    }

    private static List<String> texts(Class<? extends Label> type, String id) {
        return FX_ROBOT.selectNodes(type).fromAll().filter(node -> id.equals(node.getId()))
                .map(Label::getText).toList();
    }

    private static <T> T await(java.util.function.Supplier<Optional<T>> query, String what) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<T> hit = query.get();
            if (hit.isPresent()) {
                return hit.get();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Never saw " + what);
    }
}
