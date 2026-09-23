package com.contextswitcher.terminal;

import java.util.prefs.Preferences;

/// The terminal pane's font size, as `Ctrl`+wheel leaves it.
///
/// One size for every terminal in the app, not one per task: the size is how
/// the user reads the pane, the same reasoning the sort order is persisted by
/// (`dsn~task-sort-modes~2`), so it is kept in `Preferences` and survives a
/// restart.
///
/// The value is read by [TerminalSettings#getTerminalFontSize], which
/// JediTermFX asks whenever it builds its font — so a changed size reaches a
/// widget on its next font reinit, which `TerminalPane.zoom` triggers.
// [impl->dsn~terminal-zoom~4]
public final class TerminalZoom {

    /// JediTermFX's own default (`DefaultSettingsProvider.getTerminalFontSize`).
    public static final float DEFAULT_SIZE = 14;

    /// Below this a line is unreadable, above it a notch or two fills the pane
    /// with a handful of columns — and tmux is told the new size, so a mirror
    /// would reflow the remote window for nothing.
    private static final float MIN_SIZE = 6;
    private static final float MAX_SIZE = 40;

    private static final Preferences PREFERENCES =
            Preferences.userNodeForPackage(TerminalZoom.class);
    private static final String KEY = "terminalFontSize";

    private static volatile float size = clamp(PREFERENCES.getFloat(KEY, DEFAULT_SIZE));

    private TerminalZoom() {
    }

    /// The current size, in points.
    public static float fontSize() {
        return size;
    }

    /// Grows (`steps > 0`) or shrinks the font by one point per step and
    /// persists it; `false` when the size did not change, i.e. at a limit —
    /// the caller then has no reason to rebuild any font.
    public static boolean zoom(int steps) {
        return resize(stepped(size, steps));
    }

    /// Back to [#DEFAULT_SIZE]; `false` when already there.
    public static boolean reset() {
        return resize(DEFAULT_SIZE);
    }

    /// `size` grown by `steps` points, clamped — the pure half of [#zoom].
    static float stepped(float size, int steps) {
        return clamp(size + steps);
    }

    private static boolean resize(float wanted) {
        if (wanted == size) {
            return false;
        }
        size = wanted;
        PREFERENCES.putFloat(KEY, wanted);
        return true;
    }

    private static float clamp(float wanted) {
        return Math.min(MAX_SIZE, Math.max(MIN_SIZE, wanted));
    }
}
