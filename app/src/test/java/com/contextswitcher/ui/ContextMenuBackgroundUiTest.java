package com.contextswitcher.ui;

import javafx.application.Platform;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.stage.Window;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// A menu popup in the shell paints a background. Field report 2026-09-14: the
/// add menu showed its items over the window behind, and on Windows clicks fell
/// through it — ShellFX's `.window-box .context-menu` fill needs a colour only
/// its per-theme sheet defines, which it did not add for the app's theme.
// [utest->dsn~theme-select~8]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class ContextMenuBackgroundUiTest {

    @Test
    void addMenuPopupHasBackground() {
        MenuButton add = awaitPresent(() -> FX_ROBOT.selectNodes(MenuButton.class).fromAll()
                .filter(button -> "add-menu".equals(button.getId()))
                .findFirst(), "the add menu button");
        Platform.runLater(add::show);

        awaitPresent(() -> Window.getWindows().stream()
                .filter(window -> window.getScene() != null)
                .flatMap(window -> window.getScene().getRoot().lookupAll(".context-menu").stream())
                .map(Region.class::cast)
                .filter(menu -> {
                    Background background = menu.getBackground();
                    return background != null && !background.getFills().isEmpty();
                })
                .findFirst(), "a context menu popup with a background fill");
        Platform.runLater(add::hide);
    }
}
