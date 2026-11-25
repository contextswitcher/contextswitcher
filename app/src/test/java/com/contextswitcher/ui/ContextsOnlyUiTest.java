package com.contextswitcher.ui;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import javafx.scene.control.Labeled;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// `contextsOnly: true` opens the window without the Terminal and Queue
/// tabs, with the notes shown, and without the tmux actions in the ⋮ menu.
// [utest->dsn~contexts-only~1]
@Tag("ui")
@TestFxApplication(ContextsOnlyUiTest.App.class)
class ContextsOnlyUiTest {

    public static class App extends UiTestSupport.TestApp {
        @Override
        protected void seedTasks(Path dir) throws Exception {
            Files.writeString(dir.resolveSibling("settings.yaml"), "contextsOnly: true\n",
                    StandardOpenOption.APPEND);
        }
    }

    @Test
    void terminalQueueAndTmuxActionsAreHidden() {
        MenuButton more = awaitPresent(() -> FX_ROBOT.selectNodes(MenuButton.class).fromAll()
                .filter(button -> button.getTooltip() != null
                        && "More list actions".equals(button.getTooltip().getText()))
                .findFirst(), "the ⋮ menu button");
        awaitPresent(() -> tabTitles().stream().filter("Notes"::equals).findFirst(), "the Notes tab");
        assertThat(tabTitles()).doesNotContain("Terminal", "Queue");
        assertThat(more.getItems().stream().map(MenuItem::getText).toList())
                .doesNotContain("Sync tmux windows…", "Restart running tasks…");
    }

    private static List<String> tabTitles() {
        return FX_ROBOT.selectNodes(Labeled.class).fromAll()
                .filter(label -> label.getStyleClass().contains("tab-label"))
                .map(Labeled::getText).toList();
    }
}
