package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// *Browser URLs* as links (`dsn~browser-url-links~1`): a click opens the page,
/// the pencil turns a row into its field, and the tab in view is highlighted.
// [utest->dsn~browser-url-links~1]
@Tag("ui")
@TestFxApplication(BrowserUrlLinksUiTest.TestApp.class)
class BrowserUrlLinksUiTest {

    private static final String REPO = "https://example.org/repo";
    private static final String PR = REPO + "/pull/3";
    private static final PseudoClass BROWSER_TAB = PseudoClass.getPseudoClass("browser-tab");

    private static final List<String> OPENED = new ArrayList<>();
    private static ListFieldEditor editor;

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            editor = new ListFieldEditor(List.of(REPO, PR + " — The PR"), OPENED::add);
            editor.setId("urls");
            stage.setScene(new Scene(new VBox(editor), 600, 300));
            stage.show();
        }
    }

    @Test
    void aRowIsALinkThatEditsThroughItsPencilAndTheBestMatchIsHighlighted() {
        Hyperlink link = awaitPresent(() -> FX_ROBOT.selectNodes(Hyperlink.class).fromAll()
                .filter(node -> node.getText().contains("The PR")).findFirst(), "the PR's link");
        FX_ROBOT.mouse().moveTo(link).click();
        waitForFx();
        assertThat(OPENED).containsExactly(PR + " — The PR");

        // Only the add row shows a field: entries read as links.
        assertThat(rows().stream().map(row -> field(row).isVisible()).toList())
                .containsExactly(false, false, true);

        Platform.runLater(() -> editor.highlight(PR + "/files"));
        waitForFx();
        assertThat(rows().stream().map(row -> row.getPseudoClassStates().contains(BROWSER_TAB)).toList())
                .containsExactly(false, true, false);
        Platform.runLater(() -> editor.highlight("https://elsewhere.example.org"));
        waitForFx();
        assertThat(rows().stream().noneMatch(row -> row.getPseudoClassStates().contains(BROWSER_TAB))).isTrue();

        HBox row = rows().get(1);
        Platform.runLater(() -> ((Button) row.getChildren().get(2)).fire());
        waitForFx();
        assertThat(field(row).isVisible()).isTrue();
        assertThat(link.isVisible()).isFalse();

        // Enter ends the edit: the add row takes the focus, the row is a link again.
        FX_ROBOT.keyboard().press(javafx.scene.input.KeyCode.ENTER).release(javafx.scene.input.KeyCode.ENTER);
        waitForFx();
        assertThat(field(rows().get(2)).isFocused()).isTrue();
        assertThat(link.isVisible()).isTrue();
    }

    private static List<HBox> rows() {
        return editor.getChildren().stream().map(HBox.class::cast).toList();
    }

    private static Node field(HBox row) {
        return (TextField) row.getChildren().getFirst();
    }

    private static void waitForFx() {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Platform.runLater(done::countDown);
        try {
            done.await();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        UiTestSupport.sleep();
    }
}
