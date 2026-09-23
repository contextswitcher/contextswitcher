package com.contextswitcher.terminal;

import com.techsenger.jeditermfx.core.CursorShape;
import com.techsenger.jeditermfx.core.TerminalDisplay;
import com.techsenger.jeditermfx.core.emulator.mouse.MouseFormat;
import com.techsenger.jeditermfx.core.emulator.mouse.MouseMode;
import com.techsenger.jeditermfx.core.model.TerminalSelection;
import org.jspecify.annotations.Nullable;

/// A `TerminalDisplay` that draws nothing, for driving JediTermFX's emulator
/// and buffer without a UI.
final class NoOpDisplay implements TerminalDisplay {

    @Override
    public void setCursor(int x, int y) {
    }

    @Override
    public void setCursorShape(@Nullable CursorShape cursorShape) {
    }

    @Override
    public void beep() {
    }

    @Override
    public void scrollArea(int scrollRegionTop, int scrollRegionSize, int dy) {
    }

    @Override
    public void setCursorVisible(boolean isCursorVisible) {
    }

    @Override
    public void useAlternateScreenBuffer(boolean useAlternateScreenBuffer) {
    }

    @Override
    public String getWindowTitle() {
        return "";
    }

    @Override
    public void setWindowTitle(String windowTitle) {
    }

    @Override
    public @Nullable TerminalSelection getSelection() {
        return null;
    }

    @Override
    public void terminalMouseModeSet(MouseMode mouseMode) {
    }

    @Override
    public void setMouseFormat(MouseFormat mouseFormat) {
    }

    @Override
    public boolean ambiguousCharsAreDoubleWidth() {
        return false;
    }
}
