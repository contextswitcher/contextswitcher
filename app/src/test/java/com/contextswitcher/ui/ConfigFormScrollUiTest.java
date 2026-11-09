package com.contextswitcher.ui;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/// A form rebuilt under the user (`dsn~task-field-form~6`): the same file stays
/// where it was scrolled to, even with a row more, another file starts on top.
// [utest->dsn~task-field-form~6]
@Tag("ui")
@TestFxApplication(ConfigFormScrollUiTest.TestApp.class)
class ConfigFormScrollUiTest {

    private static ConfigFormPane pane;
    private static String buffer = file(40);
    private static String fileName = "a.md";

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            pane = new ConfigFormPane("test", field -> field.key().equals("browser.urls"), new BorderPane(),
                    () -> buffer, updated -> buffer = updated, () -> false, () -> fileName,
                    List::of, List::of, () -> CompletableFuture.completedFuture(List.of()),
                    new BrowserUrlActions() {
                        @Override
                        public void open(String line) {
                        }

                        @Override
                        public void pageTitle(String url, Consumer<String> answer) {
                        }

                        @Override
                        public boolean hasTabGroup() {
                            return false;
                        }

                        @Override
                        public void openWindow(List<String> lines, Runnable done) {
                            done.run();
                        }

                        @Override
                        public void inTabOrder(List<String> lines, Consumer<List<String>> answer) {
                            answer.accept(lines);
                        }
                    });
            stage.setScene(new Scene((BorderPane) pane.getRoot(), 600, 300));
            stage.show();
        }
    }

    @Test
    void aRebuildKeepsThePositionOfTheSameFileOnly() throws Exception {
        onFx(() -> scroll().setVvalue(0.5));
        double before = onFxValue(ConfigFormScrollUiTest::scrolledPixels);
        assertThat(before).isGreaterThan(100);

        onFx(() -> {
            buffer = file(41);
            pane.reload(true);
        });
        assertThat(onFxValue(ConfigFormScrollUiTest::scrolledPixels)).isCloseTo(before, within(1.0));

        onFx(() -> {
            fileName = "b.md";
            pane.reload(true);
        });
        assertThat(onFxValue(() -> scroll().getVvalue())).isZero();
    }

    private static ScrollPane scroll() {
        return (ScrollPane) ((BorderPane) pane.getRoot()).getCenter();
    }

    private static double scrolledPixels() {
        ScrollPane scroll = scroll();
        Node content = scroll.getContent();
        return scroll.getVvalue() * (content.getLayoutBounds().getHeight() - scroll.getViewportBounds().getHeight());
    }

    private static String file(int urls) {
        return "---\nbrowser:\n  urls:\n"
                + IntStream.range(0, urls).mapToObj(i -> "    - https://example.org/" + i + "\n")
                        .collect(Collectors.joining())
                + "---\n";
    }

    private static void onFx(Runnable action) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            action.run();
            done.countDown();
        });
        done.await();
        // A pulse, so the layout has settled before the next read.
        Thread.sleep(200);
    }

    private static double onFxValue(java.util.function.DoubleSupplier value) throws InterruptedException {
        double[] read = new double[1];
        onFx(() -> read[0] = value.getAsDouble());
        return read[0];
    }
}
