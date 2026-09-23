package com.contextswitcher.ui;

import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The app mark comes from the extension's PNG through `processResources`
/// (`dsn~app-icon~4`), so this check stands between a missed copy step and a
/// window that silently falls back to the toolkit's default icon. It also
/// pins the pixel format: JavaFX drops a stage icon that is not byte-based,
/// which is how the drawn version failed. Image loading needs the toolkit,
/// hence a (windowless) TestFX application rather than a plain unit test.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~app-icon~4]
@Tag("ui")
@TestFxApplication(AppIconUiTest.TestApp.class)
class AppIconUiTest {

    private static Image icon;

    /// Loads the icon on the FX thread; no window is needed for that.
    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            icon = AppIcon.image(64);
        }
    }

    @Test
    void showsTheExtensionsTargetRings() {
        Color blue = Color.web("#0077BB");
        // The centre dot, the white ring, the blue ring and the transparent
        // corner — sampled along the horizontal centre line at the SVG's radii
        // (64 px = 4 px per SVG unit).
        assertThat(icon.getPixelReader().getColor(32, 32)).isEqualTo(blue);
        assertThat(icon.getPixelReader().getColor(32 + 4 * 3, 32)).isEqualTo(Color.WHITE);
        assertThat(icon.getPixelReader().getColor(32 + 4 * 6, 32)).isEqualTo(blue);
        assertThat(icon.getPixelReader().getColor(0, 0).getOpacity()).isZero();
        // A stage icon JavaFX will actually use: `WindowStage.findBestImage`
        // skips anything but byte-based pixels, so an int-based image (what a
        // canvas snapshot gives) leaves the window on the toolkit default.
        assertThat(icon.getPixelReader().getPixelFormat().getType())
                .isEqualTo(PixelFormat.Type.BYTE_BGRA_PRE);
    }
}
