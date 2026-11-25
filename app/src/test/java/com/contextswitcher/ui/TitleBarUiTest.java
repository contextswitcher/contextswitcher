package com.contextswitcher.ui;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.application.Platform;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The main window's title bar carries the window title, the app-wide controls
/// and a close button that asks before it closes (`dsn~shell-layout~3`), and
/// the app icon the OS frame drew before the stage became extended.
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class TitleBarUiTest {

    @Test
    void titleBarHoldsTitleAndAppControlsAndItsCloseButtonRequestsTheClose() {
        Node energySaver = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ToggleButton.class).fromAll()
                        .filter(button -> button.getStyleClass().contains("energy-saver"))
                        .<Node>map(button -> button)
                        .findFirst(),
                "the energy saver toggle");
        Node titleBar = UiTestSupport.awaitPresent(
                () -> Optional.ofNullable(energySaver.getScene().getRoot().lookup(".title-bar")),
                "the title bar");
        assertTrue(isInside(energySaver, titleBar), "the app-wide controls sit in the title bar");

        Stage stage = (Stage) titleBar.getScene().getWindow();
        UiTestSupport.awaitPresent(
                () -> titleBar.lookupAll(".label").stream()
                        .filter(label -> label instanceof Label text && !stage.getTitle().isEmpty()
                                && stage.getTitle().equals(text.getText()))
                        .findFirst(),
                "the window title in the title bar");
        // [utest->dsn~app-icon~6]
        assertTrue(titleBar.lookupAll("*").stream().anyMatch(node -> node instanceof ImageView image
                        && image.getImage() != null),
                "the app icon in the title bar");

        // What CloseConfirmation relies on: the close button sends a close
        // request, which a filter may consume to keep the window open.
        var requested = new AtomicBoolean();
        Platform.runLater(() -> {
            stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
                requested.set(true);
                event.consume();
            });
            ((Button) titleBar.lookup(".close-button")).fire();
        });
        UiTestSupport.awaitPresent(() -> Optional.of(requested).filter(AtomicBoolean::get),
                "the close request of the title bar's close button");
        UiTestSupport.sleep();
        assertTrue(stage.isShowing(), "a consumed close request keeps the window open");
    }

    private static boolean isInside(Node node, Node ancestor) {
        for (Node parent = node; parent != null; parent = parent.getParent()) {
            if (parent == ancestor) {
                return true;
            }
        }
        return false;
    }
}
