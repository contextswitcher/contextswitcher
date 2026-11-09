package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.contextswitcher.tasks.FrontmatterCatalog;

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
/// the pencil turns a row into its field, the tab in view is highlighted, and
/// the caption's buttons reach the browser (`dsn~browser-task-window~1`).
// [utest->dsn~browser-url-links~1]
// [utest->dsn~browser-url-order~1]
@Tag("ui")
@TestFxApplication(BrowserUrlLinksUiTest.TestApp.class)
class BrowserUrlLinksUiTest {

    private static final String REPO = "https://example.org/repo";
    private static final String PR = REPO + "/pull/3";
    private static final PseudoClass BROWSER_TAB = PseudoClass.getPseudoClass("browser-tab");

    private static final List<String> OPENED = new ArrayList<>();
    private static ListFieldEditor editor;
    private static int reorders;
    private static final String FILE = "---\nbrowser:\n  urls:\n    - https://a.example\n    - https://b.example\n---\n";
    private static final List<List<String>> WINDOWS = new ArrayList<>();
    private static ConfigForm form;
    private static int saves;

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            editor = new ListFieldEditor(List.of(REPO, PR + " — The PR"), new BrowserUrlActions() {
                @Override
                public void open(String line) {
                    OPENED.add(line);
                }

                @Override
                public void pageTitle(String url, Consumer<String> answer) {
                    answer.accept("Title of " + url);
                }

                @Override
                public boolean hasTabGroup() {
                    return true;
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
            editor.setId("urls");
            editor.setOnReordered(() -> reorders++);
            form = new ConfigForm(FrontmatterCatalog.TASK.stream()
                    .filter(field -> field.key().equals("browser.urls")).toList(), FILE, List.of(), List.of(),
                    () -> CompletableFuture.completedFuture(List.of()), new BrowserUrlActions() {
                        @Override
                        public void open(String line) {
                        }

                        @Override
                        public void pageTitle(String url, Consumer<String> answer) {
                        }

                        @Override
                        public boolean hasTabGroup() {
                            return true;
                        }

                        @Override
                        public void openWindow(List<String> lines, Runnable done) {
                            WINDOWS.add(lines);
                            done.run();
                        }

                        @Override
                        public void inTabOrder(List<String> lines, Consumer<List<String>> answer) {
                            answer.accept(lines.reversed());
                        }
                    }, () -> saves++);
            stage.setScene(new Scene(new VBox(editor, form.render()), 600, 500));
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

        // Hovering: the entry's description, else the page's title.
        Platform.runLater(() -> {
            link.getTooltip().getOnShowing().handle(null);
            assertThat(link.getTooltip().getText()).isEqualTo("The PR");
        });
        waitForFx();
        Hyperlink repo = (Hyperlink) rows().getFirst().getChildren().get(1);
        Platform.runLater(() -> repo.getTooltip().getOnShowing().handle(null));
        waitForFx();
        assertThat(repo.getTooltip().getText()).isEqualTo("Title of " + REPO);

        // A drop takes the place of its target; the add row means the end.
        Platform.runLater(() -> editor.move(rows().getFirst(), rows().get(1)));
        waitForFx();
        assertThat(editor.values()).containsExactly(PR + " — The PR", REPO, "");
        Platform.runLater(() -> editor.move(rows().getFirst(), rows().get(2)));
        waitForFx();
        assertThat(editor.values()).containsExactly(REPO, PR + " — The PR", "");
        assertThat(reorders).isEqualTo(2);
    }

    // [utest->dsn~browser-task-window~1]
    // [utest->dsn~browser-url-order~1]
    @Test
    void theCaptionButtonsOpenTheWindowAndTakeOverTheTabOrder() {
        Button window = awaitPresent(() -> FX_ROBOT.selectNodes(Button.class).fromAll()
                .filter(node -> "field-browser-urls-window".equals(node.getId())).findFirst(), "the window button");
        Button sync = awaitPresent(() -> FX_ROBOT.selectNodes(Button.class).fromAll()
                .filter(node -> "field-browser-urls-sync".equals(node.getId())).findFirst(), "the sync button");
        Platform.runLater(window::fire);
        waitForFx();
        assertThat(WINDOWS).containsExactly(List.of("https://a.example", "https://b.example"));
        assertThat(window.isDisable()).isFalse();

        Platform.runLater(sync::fire);
        waitForFx();
        assertThat(saves).isEqualTo(1);
        assertThat(sync.isDisable()).isFalse();
        ConfigForm.MergeResult merged = form.mergeInto(FILE);
        assertThat(((ConfigForm.MergeResult.Merged) merged).content().indexOf("b.example"))
                .isLessThan(((ConfigForm.MergeResult.Merged) merged).content().indexOf("a.example"));
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
