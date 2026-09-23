package com.contextswitcher.terminal;

import com.techsenger.jeditermfx.core.RequestOrigin;
import com.techsenger.jeditermfx.core.TerminalDisplay;
import com.techsenger.jeditermfx.core.model.JediTerminal;
import com.techsenger.jeditermfx.core.model.StyleState;
import com.techsenger.jeditermfx.core.model.TerminalTextBuffer;
import com.techsenger.jeditermfx.core.util.TermSize;

/// A `JediTerminal` whose resize survives a narrower **and** much shorter
/// grid at once.
///
/// JediTermFX 1.1.0 (and JetBrains' jediterm it ports) re-wraps the buffer
/// for a width change in `ChangeWidthOperation`, which keeps the cursor on
/// screen by starting the new screen at `cursorY - newHeight + 1`. The cursor
/// is tracked below the buffer's last line when the lines under it are blank
/// — Claude Code's alternate-buffer screen — so a big enough height cut puts
/// that start index past the end of the line list, and `subList` throws
/// `IndexOutOfBoundsException`. The resize runs as a task on JediTermFX's
/// executor, which swallows the exception: the emulator and the pty keep the
/// old size while the panel already paints the new one — zooming in cut off
/// the right and bottom edges, and every later shrink failed the same way.
///
/// Splitting such a resize avoids the case: first the width at the **old**
/// height, where the start index cannot pass the cursor's own line, then the
/// height alone, which never re-wraps. The pty still receives one resize, the
/// final size (`TerminalStarter` schedules it from its own argument).
///
/// After any resize the cursor is also clamped onto the screen. A shorter
/// alternate buffer keeps the cursor's old row (`TerminalTextBuffer.resize`
/// only moves it outside the alternate buffer), so it could sit below the last
/// row; the next write that wrapped then read the line under it and JediTermFX
/// logged "Attempt to get line out of bounds: 32 >= 32". xterm keeps the
/// cursor on screen across a resize, and so does this.
// [impl->dsn~terminal-zoom~4]
public class StepwiseResizeTerminal extends JediTerminal {

    private final TerminalTextBuffer buffer;

    public StepwiseResizeTerminal(TerminalDisplay display, TerminalTextBuffer buffer, StyleState style) {
        super(display, buffer, style);
        this.buffer = buffer;
    }

    @Override
    public void resize(TermSize newTermSize, RequestOrigin origin) {
        if (newTermSize.getColumns() != getTerminalWidth() && newTermSize.getRows() < getTerminalHeight()) {
            super.resize(new TermSize(newTermSize.getColumns(), getTerminalHeight()), origin);
        }
        super.resize(newTermSize, origin);
        buffer.lock();
        try {
            if (getCursorY() > getTerminalHeight()) {
                // The raw row, not cursorPosition: that one counts from the
                // scrolling region's top in origin mode.
                setY(getTerminalHeight());
            }
        } finally {
            buffer.unlock();
        }
    }
}
