package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// Field report 2026-11-12: an active task whose creation failed before its
/// window came up, in a category that carries the `remote:`, showed pause on
/// its row although the mirror offered `Start Claude`. The category's remote
/// counts, so the row offers play (`dsn~task-row-hover-actions~7`).
// [utest->dsn~task-row-hover-actions~7]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class CategoryRemoteRowPlayUiTest {

    @Test
    void failedCreationInRemoteCategoryOffersPlay() throws Exception {
        Path category = Files.createDirectories(UiTestSupport.tasksDir.resolve("remotecat"));
        Files.writeString(category.resolve("CONTEXTSWITCHER.md"), "---\nremote: me@devbox\n---\n");
        Files.writeString(category.resolve("failed.md"), "---\ntitle: failed\nstatus: active\n---\n");

        awaitPresent(() -> FX_ROBOT.selectNodes(Node.class).fromAll()
                .filter(node -> "task-toggle-button".equals(node.getId()))
                .map(Button.class::cast)
                // `Tooltip.install` keeps it in the properties, not `getTooltip()`.
                .filter(button -> button.getProperties().get("javafx.scene.control.Tooltip")
                        instanceof Tooltip tip && tip.getText().startsWith("No session running"))
                .findFirst(), "the failed task's play button");
    }
}
