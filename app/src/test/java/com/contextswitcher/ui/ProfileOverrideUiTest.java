package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// The status bar's environment-profile override is shown once the app is up
/// — `Main` wires it before the window builds its bottom bar, which once hid
/// it again — and offers the profiles `settings.yaml` defines at the moment
/// the menu opens.
// [utest->dsn~environment-profile-override~1]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class ProfileOverrideUiTest {

    @Test
    void overrideButtonIsShownAndListsTheProfiles() throws Exception {
        MenuButton button = awaitPresent(() -> FX_ROBOT.selectNodes(MenuButton.class).fromAll()
                .filter(node -> "profile-override".equals(node.getId()) && node.isVisible())
                .findFirst(),
                "the profile override button to be visible");

        Path settings = UiTestSupport.tasksDir.getParent().resolve("settings.yaml");
        Files.writeString(settings, """
                profiles:
                  default: {bookmarksBar: visible}
                  screenshare: {bookmarksBar: hidden}
                """, StandardOpenOption.APPEND);

        javafx.application.Platform.runLater(button::show);
        awaitPresent(() -> button.getItems().stream().map(MenuItem::getText)
                .anyMatch("screenshare"::equals) ? Optional.of(true) : Optional.empty(),
                "the screenshare profile in the override menu");
        FX_ROBOT.keyboard().type(KeyCode.ESCAPE);
    }
}
