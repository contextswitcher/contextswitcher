package com.contextswitcher.terminal;

import com.techsenger.jeditermfx.core.TerminalColor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-theme~2]
class TerminalSettingsTest {

    // The deprecated getDefaultStyle() seeds JediTermFX 1.1.0's StyleState,
    // which colors the block cursor — if it drifts from the color overrides
    // (or the seemingly redundant override is deleted), the cursor renders
    // black-on-dark again, invisible.
    @Test
    @SuppressWarnings("deprecation")
    void defaultStyleMatchesThemeColors() {
        for (TerminalSettings settings : all()) {
            assertThat(settings.getDefaultStyle().getForeground())
                    .isEqualTo(settings.getDefaultForeground());
            assertThat(settings.getDefaultStyle().getBackground())
                    .isEqualTo(settings.getDefaultBackground());
        }
    }

    // The ground the JavaFX regions around the canvas are painted with has to
    // be the very color the canvas itself uses, or the pane shows a seam.
    @Test
    void backgroundColorMatchesTheTerminalBackground() {
        for (boolean dark : new boolean[] {true, false}) {
            for (boolean everforest : new boolean[] {true, false}) {
                TerminalColor background = new TerminalSettings(dark, everforest).getDefaultBackground();
                assertThat(TerminalSettings.backgroundColor(dark, everforest))
                        .isEqualTo("#%06x".formatted(background.toColor().getRGB() & 0xffffff));
            }
        }
    }

    // A light theme whose ANSI colors stayed dark-theme values would be the
    // worst of both: the two palettes must be genuinely different, and each
    // must keep its own default foreground legible against its own ground.
    @Test
    void lightAndDarkPalettesDiffer() {
        TerminalSettings light = new TerminalSettings(false, true);
        TerminalSettings dark = new TerminalSettings(true, true);
        assertThat(light.getDefaultBackground()).isNotEqualTo(dark.getDefaultBackground());
        assertThat(light.getTerminalColorPalette()
                .getForeground(TerminalColor.index(1)))
                .isNotEqualTo(dark.getTerminalColorPalette()
                        .getForeground(TerminalColor.index(1)));
        for (boolean everforest : new boolean[] {true, false}) {
            TerminalSettings lightGround = new TerminalSettings(false, everforest);
            TerminalSettings darkGround = new TerminalSettings(true, everforest);
            assertThat(luminance(lightGround.getDefaultBackground()))
                    .isGreaterThan(luminance(lightGround.getDefaultForeground()));
            assertThat(luminance(darkGround.getDefaultBackground()))
                    .isLessThan(luminance(darkGround.getDefaultForeground()));
        }
    }

    // Everforest is for the Everforest app themes only: under the plain Nord
    // themes the terminal keeps JediTermFX's stock palette, not the yellowish
    // Everforest ground.
    @Test
    void plainThemesDoNotUseEverforest() {
        for (boolean dark : new boolean[] {true, false}) {
            assertThat(TerminalSettings.backgroundColor(dark, false))
                    .isNotEqualTo(TerminalSettings.backgroundColor(dark, true));
            assertThat(new TerminalSettings(dark, false).getTerminalColorPalette())
                    .isNotSameAs(new TerminalSettings(dark, true).getTerminalColorPalette());
        }
    }

    private static TerminalSettings[] all() {
        return new TerminalSettings[] {
            new TerminalSettings(true, true), new TerminalSettings(false, true),
            new TerminalSettings(true, false), new TerminalSettings(false, false),
        };
    }

    private static int luminance(TerminalColor color) {
        com.techsenger.jeditermfx.core.Color rgb = color.toColor();
        return rgb.getRed() + rgb.getGreen() + rgb.getBlue();
    }
}
