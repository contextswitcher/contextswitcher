package com.contextswitcher.ui;

import java.util.List;
import java.util.Optional;

import javafx.application.Platform;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.stage.Window;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// The filter menu's items must keep their labels in one column whatever is
/// ticked. Field report 2026-07-30: after checking and unchecking "Awaits
/// input", its label sat indented against its neighbours — AtlantaFX sizes the
/// check mark only under `:checked`, so ticking an item widened just that row's
/// left column while JavaFX kept the label offset it had computed when the popup
/// was laid out. `main.css` reserves the column unconditionally.
// [utest->dsn~task-list-toolbar~3]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class FilterMenuAlignmentUiTest {

    @Test
    void filterItemLabelsStayAlignedAcrossToggling() throws Exception {
        MenuButton filter = awaitPresent(() -> FX_ROBOT.selectNodes(MenuButton.class).fromAll()
                .filter(button -> button.getItems().stream()
                        .anyMatch(item -> item.getText() != null && item.getText().startsWith("Awaits input only")))
                .findFirst(), "the toolbar filter menu button");

        Platform.runLater(filter::show);
        assertAligned("nothing ticked");

        Platform.runLater(() -> awaitsInput(filter).setSelected(true));
        assertAligned("\"Awaits input only\" ticked");

        Platform.runLater(() -> awaitsInput(filter).setSelected(false));
        assertAligned("\"Awaits input only\" unticked again");
    }

    private static CheckMenuItem awaitsInput(MenuButton filter) {
        return filter.getItems().stream()
                .filter(CheckMenuItem.class::isInstance).map(CheckMenuItem.class::cast)
                .filter(item -> item.getText() != null && item.getText().startsWith("Awaits input only"))
                .findFirst().orElseThrow();
    }

    /// Every label of the open menu popup must start at the same x. Polls: the
    /// popup lays out on the FX thread, after the call that changed it returned.
    private static void assertAligned(String state) throws InterruptedException {
        // The tick that misaligned the labels is applied in a later layout
        // pulse than the call that set it, so read after the popup has settled.
        Thread.sleep(500);
        List<Double> offsets = awaitPresent(() -> {
            List<Double> found = Window.getWindows().stream()
                    .filter(window -> window.getScene() != null)
                    .flatMap(window -> window.getScene().getRoot()
                            .lookupAll(".menu-item").stream())
                    .map(row -> row.lookup(".label"))
                    .filter(label -> label != null)
                    .map(label -> label.localToScene(label.getLayoutBounds()).getMinX())
                    .toList();
            return found.size() < 3 ? Optional.empty() : Optional.of(found);
        }, "the open filter menu's item labels");
        assertEquals(1, offsets.stream().distinct().count(),
                "filter menu labels misaligned with " + state + ": " + offsets);
    }
}
