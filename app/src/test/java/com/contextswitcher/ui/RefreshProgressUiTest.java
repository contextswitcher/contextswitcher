package com.contextswitcher.ui;

import java.util.Optional;

import javafx.application.Platform;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.stage.Window;

import com.dlsc.gemsfx.infocenter.InfoCenterPane;
import com.dlsc.gemsfx.infocenter.Notification;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// "Refresh now" reports its polls in the info center and ends with an outcome
/// notification, re-enabling the menu item.
// [utest->dsn~refresh-progress~2]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class RefreshProgressUiTest {

    @Test
    void refreshNowEndsInOutcomeNotification() {
        MenuButton more = awaitPresent(() -> FX_ROBOT.selectNodes(MenuButton.class).fromAll()
                .filter(button -> button.getTooltip() != null
                        && "More list actions".equals(button.getTooltip().getText()))
                .findFirst(), "the ⋮ menu button");
        MenuItem refresh = more.getItems().getLast();
        Platform.runLater(refresh::fire);

        awaitPresent(() -> FX_ROBOT.selectNodes(InfoCenterPane.class).fromAll()
                .flatMap(pane -> pane.getInfoCenterView()
                        .getUnmodifiableNotifications().stream())
                .map(Notification::getTitle)
                .filter("Refreshed"::equals)
                .findFirst(), "the \"Refreshed\" notification");
        assertFalse(refresh.isDisable(), "refresh item still disabled after the refresh finished");

        // The pane follows the window: wrapped around the scene root it stayed
        // at the start size, and a maximized window showed the shell in its
        // top-left corner (field report 2026-09-14).
        InfoCenterPane pane = FX_ROBOT.selectNodes(InfoCenterPane.class).fromAll().findFirst().orElseThrow();
        Window window = pane.getScene().getWindow();
        double wider = window.getWidth() + 200;
        double taller = window.getHeight() + 200;
        Platform.runLater(() -> {
            window.setWidth(wider);
            window.setHeight(taller);
        });
        awaitPresent(() -> Optional.of(pane.getWidth()).filter(width -> width >= wider - 50),
                "the info center pane grown with the window's width");
        awaitPresent(() -> Optional.of(pane.getHeight()).filter(height -> height >= taller - 250),
                "the info center pane grown with the window's height");
    }
}
