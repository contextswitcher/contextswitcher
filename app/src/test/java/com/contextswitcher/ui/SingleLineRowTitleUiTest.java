package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.control.Label;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// A task whose `title:` is still the typed multi-line description shows it on
/// one row line, so the row stays as tall as its neighbours
/// (`dsn~task-row-single-line-title~1`).
// [utest->dsn~task-row-single-line-title~1]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class SingleLineRowTitleUiTest {

    @Test
    void aMultiLineTitleRendersOnOneLine() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.writeString(dir.resolve("multiline.md"),
                "---\ntitle: \"no multiline tasks\\n\\nsingle line is enough\"\nstatus: active\n"
                        + "---\nnotes\n");

        String title = awaitPresent(() -> FX_ROBOT.selectNodes(Label.class).fromAll()
                .map(Label::getText)
                .filter(text -> text.contains("single line is enough"))
                .findFirst(),
                "the task's title label");
        assertEquals("no multiline tasks single line is enough", title);
    }
}
