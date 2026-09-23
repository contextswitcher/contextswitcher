package com.contextswitcher.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.ListView;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The theme check tells a themed start from an unthemed one, and repairs the
/// latter (`dsn~theme-select~8`). Field report 2026-09-16: "theme not loaded",
/// then "second run worked" — both starts logged the same stylesheets and the
/// user-agent URL "as requested", so the URL cannot be what is checked; the
/// theme's colour variables are.
///
/// JavaFX's own Modena stands in for the unthemed window: it defines none of
/// AtlantaFX's `-color-*` variables, which is what the screenshot showed —
/// dark text on white, glyphs without a fill.
// [utest->dsn~theme-select~8]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class ThemeVariablesProbeUiTest {

    @Test
    void theProbeTellsAnUnthemedWindowAndVerifyRepairsIt() {
        // The app is up once its task list is: before that no theme is applied.
        UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll().findFirst(), "the task ListView");
        assertThat(callFx(Themes::variablesResolve))
                .as("the app's theme defines the variables").isTrue();

        // Unthemed: JavaFX's default stylesheet in force, the app's theme still requested.
        String theme = Themes.stylesheet();
        assertThat(theme).isNotNull();
        assertThat(callFx(() -> {
            Application.setUserAgentStylesheet(null);
            return Themes.variablesResolve();
        })).as("Modena defines none of the theme variables").isFalse();

        assertThat(callFx(() -> Themes.verify("test")))
                .as("verify refreshes the theme and reports the repair").isTrue();
        assertThat(callFx(Themes::variablesResolve)).isTrue();
        assertThat(callFx(() -> theme.equals(Application.getUserAgentStylesheet()))).isTrue();
    }

    private static boolean callFx(BooleanSupplier body) {
        if (Platform.isFxApplicationThread()) {
            return body.getAsBoolean();
        }
        boolean[] result = {false};
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result[0] = body.getAsBoolean();
            } finally {
                done.countDown();
            }
        });
        try {
            assertThat(done.await(10, TimeUnit.SECONDS)).as("the FX thread answered").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }
}
