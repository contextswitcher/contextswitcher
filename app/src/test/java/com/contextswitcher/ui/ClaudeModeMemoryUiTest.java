package com.contextswitcher.ui;


import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ListCell;
import javafx.scene.input.KeyCode;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static com.contextswitcher.ui.UiTestSupport.awaitButton;
import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static com.contextswitcher.ui.UiTestSupport.awaitTaskFileContaining;
import static com.contextswitcher.ui.UiTestSupport.openAddTask;
import static io.gitlab.fxlabs.testfx.util.FxPredicates.hasText;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The "Add task…" dialog remembers the last model/effort actually sent and
/// pre-selects it on the next open — surviving an app restart, because the
/// memory is Java `Preferences` (`dsn~claude-mode-select~3`; manual-test item
/// "both pickers come up pre-selected with that last pick").
///
/// The restart is real: the TestFX extension relaunches the app for every
/// test method, so [#restartPrefillsThePickersWithTheLastPick] runs against a
/// fresh app instance in the same JVM. The two methods are ordered halves of
/// one scenario — running only the second alone fails by design.
/// The `uiTest` task keeps Java Preferences in memory
/// ([InMemoryPreferencesFactory]), so the pick never touches the developer's
/// real preferences.
@Tag("ui")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestFxApplication(UiTestSupport.TestApp.class)
class ClaudeModeMemoryUiTest {

    @Test
    @Order(1)
    void pickingModelAndEffortAndSubmittingRemembersThePick() throws Exception {
        openAddTask();
        awaitButton("Add plain task");
        pickInCombo(modeCombo("model: as is"), "opus");
        pickInCombo(modeCombo("effort: as is"), "high");
        var field = awaitPresent(
                () -> FX_ROBOT.selectNodes(javafx.scene.control.TextArea.class)
                        .fetchOptionalFrom(dialogPane()),
                "dialog text area");
        FX_ROBOT.mouse().moveTo(field).click();
        FX_ROBOT.keyboard()
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.A)
                .type(KeyCode.DELETE)
                .print("Mode memory task")
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER)
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER)
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);
        awaitTaskFileContaining("Mode memory task");
    }

    @Test
    @Order(2)
    void restartPrefillsThePickersWithTheLastPick() {
        openAddTask();
        awaitButton("Add plain task");
        assertThat(modeCombo("model: as is").getValue()).isEqualTo("opus");
        assertThat(modeCombo("effort: as is").getValue()).isEqualTo("high");
        FX_ROBOT.keyboard().type(KeyCode.ESCAPE);
    }

    private static DialogPane dialogPane() {
        return awaitPresent(
                () -> FX_ROBOT.selectNodes(DialogPane.class, ".dialog-pane").fetchOptional(),
                "dialog pane");
    }

    /// The dialog's model or effort picker, identified by its "as is" first
    /// entry — the pickers' current value varies with the remembered pick,
    /// hence items + dialog scope.
    @SuppressWarnings("unchecked")
    private static ComboBox<String> modeCombo(String asIsEntry) {
        DialogPane dialog = dialogPane();
        return awaitPresent(
                () -> FX_ROBOT.selectNodes(ComboBox.class).from(dialog)
                        .filter(combo -> combo.getItems().contains(asIsEntry))
                        .map(combo -> (ComboBox<String>) combo)
                        .findFirst(),
                "combo with entry \"" + asIsEntry + "\"");
    }

    /// Opens the combo's popup with a click and clicks the entry — driving
    /// the real selection UI, not `setValue`.
    private static void pickInCombo(ComboBox<String> combo, String entry) {
        FX_ROBOT.mouse().moveTo(combo).click();
        ListCell<?> cell = awaitPresent(
                () -> FX_ROBOT.selectNodes(ListCell.class).fromAll()
                        .filter(hasText(entry))
                        .filter(ListCell::isVisible)
                        .<ListCell<?>>map(c -> c)
                        .findFirst(),
                "combo entry \"" + entry + "\"");
        FX_ROBOT.mouse().moveTo(cell).click();
    }
}
