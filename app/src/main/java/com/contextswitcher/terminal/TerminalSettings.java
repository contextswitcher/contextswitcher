package com.contextswitcher.terminal;

import com.techsenger.jeditermfx.core.Color;
import com.techsenger.jeditermfx.core.TerminalColor;
import com.techsenger.jeditermfx.core.TextStyle;
import com.techsenger.jeditermfx.core.emulator.ColorPalette;
import com.techsenger.jeditermfx.ui.settings.DefaultSettingsProvider;

/// Colors and behaviour of the terminal pane's JediTermFX widget. Under an
/// Everforest app theme: the Everforest palette (MADR 0024) in its medium dark
/// or medium light variant. Under the plain Nord themes: JediTermFX's stock
/// ANSI palette on black or white. Either way the lightness follows the app
/// theme, so a light UI does not frame a dark terminal.
///
/// Only the 16 ANSI indices are ours — `ColorPalette` asserts index < 16, and
/// the 256-color cube above them is fixed by xterm — so a remote program that
/// emits 256-index or 24-bit color (a Claude Code theme, a hex-styled tmux
/// status line) keeps its own colors, dark ones included. That is the known
/// ceiling of a light terminal here: it is fixed on the remote, by giving the
/// program a light theme of its own.
// [impl->dsn~terminal-theme~2]
public class TerminalSettings extends DefaultSettingsProvider {

    /// Everforest Dark Medium, ANSI 0–15: `bg3` as black (`bg0` would be
    /// invisible on itself), `fg` as white, `grey1` as bright black, and the
    /// brights otherwise equal to the normals — the palette has one shade per
    /// hue, and inventing a second would break its low-contrast intent.
    private static final int[] DARK_ANSI = {
            0x475258, 0xe67e80, 0xa7c080, 0xdbbc7f, 0x7fbbb3, 0xd699b6, 0x83c092, 0xd3c6aa,
            0x859289, 0xe67e80, 0xa7c080, 0xdbbc7f, 0x7fbbb3, 0xd699b6, 0x83c092, 0xd3c6aa,
    };

    /// Everforest Light Medium, same mapping mirrored: `fg` as black, `bg3` as
    /// white, `bg0` as bright white. White-on-white therefore vanishes here, as
    /// in every light terminal theme — a program wanting readable text on the
    /// default ground must use the default foreground, not bright white.
    private static final int[] LIGHT_ANSI = {
            0x5c6a72, 0xf85552, 0x8da101, 0xdfa000, 0x3a94c5, 0xdf69ba, 0x35a77c, 0xe6e2cc,
            0x939f91, 0xf85552, 0x8da101, 0xdfa000, 0x3a94c5, 0xdf69ba, 0x35a77c, 0xfdf6e3,
    };

    private static final int DARK_BG = 0x2d353b;
    private static final int DARK_FG = 0xd3c6aa;
    private static final int LIGHT_BG = 0xfdf6e3;
    private static final int LIGHT_FG = 0x5c6a72;
    private static final int PLAIN_DARK_BG = 0x000000;
    private static final int PLAIN_LIGHT_BG = 0xffffff;

    private static final ColorPalette DARK_PALETTE = palette(DARK_ANSI);
    private static final ColorPalette LIGHT_PALETTE = palette(LIGHT_ANSI);

    private final boolean dark;
    private final boolean everforest;

    public TerminalSettings(boolean dark, boolean everforest) {
        this.dark = dark;
        this.everforest = everforest;
    }

    /// The terminal ground as a CSS color, for the JavaFX regions framing the
    /// widget (placeholder area, switching overlay, snapshot scroller) — they
    /// must not show a seam against the canvas.
    public static String backgroundColor(boolean dark, boolean everforest) {
        return "#%06x".formatted(background(dark, everforest));
    }

    private static int background(boolean dark, boolean everforest) {
        if (everforest) {
            return dark ? DARK_BG : LIGHT_BG;
        }
        return dark ? PLAIN_DARK_BG : PLAIN_LIGHT_BG;
    }

    @Override
    public TerminalColor getDefaultBackground() {
        return terminalColor(background(dark, everforest));
    }

    @Override
    public TerminalColor getDefaultForeground() {
        if (everforest) {
            return terminalColor(dark ? DARK_FG : LIGHT_FG);
        }
        return terminalColor(dark ? PLAIN_LIGHT_BG : PLAIN_DARK_BG);
    }

    @Override
    public ColorPalette getTerminalColorPalette() {
        if (everforest) {
            return dark ? DARK_PALETTE : LIGHT_PALETTE;
        }
        return super.getTerminalColorPalette();
    }

    /// Must stay aligned with the two overrides above: JediTermFX 1.1.0's
    /// `JediTermFxWidget` seeds the terminal's `StyleState` default from this
    /// **deprecated** method (not from `getDefaultForeground`/`getDefaultBackground`),
    /// and the block cursor is filled with that StyleState foreground — without
    /// this override the inherited black-on-white default painted the cursor
    /// **black** on the dark background, i.e. invisible in Claude's input box
    /// (techsenger/jeditermfx#26). Text was unaffected (it resolves defaults
    /// via the window colors), which is why only the cursor was wrong.
    /// Upstream master fixed the seeding and removed this method (`32b3066`),
    /// so the next jeditermfx upgrade breaks this `@Override` — delete it then.
    @Override
    @SuppressWarnings("deprecation")
    public TextStyle getDefaultStyle() {
        return new TextStyle(getDefaultForeground(), getDefaultBackground());
    }

    /// Steady cursor (no blink): the port's blinking cursor renders nothing
    /// on the off phase, which reads as "no cursor" while focused.
    @Override
    public int caretBlinkingMs() {
        return 0;
    }

    /// Copy a finished local selection to the system clipboard on mouse
    /// release (PuTTY-style). The mirror's tmux mouse mode would hand a plain
    /// drag to tmux copy-mode, whose copy stays in tmux's remote buffer and
    /// never reaches the clipboard, so `TerminalPane.installLocalSelection`
    /// re-fires plain button events as `Shift`ed ones — every selection the
    /// user makes with the mouse is local, and lands here.
    // [impl->dsn~terminal-mouse-scroll~9]
    @Override
    public boolean copyOnSelect() {
        return true;
    }

    /// The font size `Ctrl`+wheel left behind ([TerminalZoom]) — asked for
    /// every time JediTermFX builds its font, so a zoom reaches a widget on
    /// its next font reinit.
    // [impl->dsn~terminal-zoom~4]
    @Override
    public float getTerminalFontSize() {
        return TerminalZoom.fontSize();
    }

    /// No alternate-scroll emulation: the pane decides what the wheel does.
    ///
    /// JediTermFX sends `Up`/`Down` keys for a wheel in the alternate buffer
    /// from a canvas handler with no `Shift` guard and no `else` against the
    /// mouse-reporting branch next to it, so a program that asked for the
    /// wheel would get the report **and** the keys. The keys read the raw sign
    /// while the report is corrected (`TerminalPane.installWheelDirectionFix`),
    /// which fits what was seen in an owned session: a wheel down scrolled
    /// Claude Code down and moved its prompt cursor up. A mirror is a tmux
    /// client, always the alternate buffer, so it would double there too.
    // [impl->dsn~terminal-mouse-scroll~9]
    @Override
    public boolean sendArrowKeysInAlternativeMode() {
        return false;
    }

    /// A palette serving `ansi` for indices 0–15 as both foreground and
    /// background; the `Color`s are built once, not per painted glyph.
    private static ColorPalette palette(int[] ansi) {
        Color[] colors = new Color[ansi.length];
        for (int i = 0; i < ansi.length; i++) {
            colors[i] = new Color(red(ansi[i]), green(ansi[i]), blue(ansi[i]));
        }
        return new ColorPalette() {

            @Override
            protected Color getForegroundByColorIndex(int index) {
                return colors[index];
            }

            @Override
            protected Color getBackgroundByColorIndex(int index) {
                return colors[index];
            }
        };
    }

    private static TerminalColor terminalColor(int rgb) {
        return new TerminalColor(red(rgb), green(rgb), blue(rgb));
    }

    private static int red(int rgb) {
        return (rgb >> 16) & 0xff;
    }

    private static int green(int rgb) {
        return (rgb >> 8) & 0xff;
    }

    private static int blue(int rgb) {
        return rgb & 0xff;
    }
}
