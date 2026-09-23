package com.contextswitcher.terminal;

import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.core.util.TermSize;

/// A read-only `TtyConnector` that replays a fixed string into a JediTermFX
/// widget and then ends the stream — used to render a stored pane snapshot
/// (`dsn~terminal-suspend-snapshot~3`) with the same emulator, and so the
/// same color fidelity, as the live mirror.
///
/// It carries the `capture-pane -e` output (SGR color escapes and all): the
/// widget's emulator parses them exactly as it does the live pty, so no ANSI
/// parser of our own is needed. Input is ignored — the snapshot is a picture
/// of a window that no longer exists.
// [impl->dsn~terminal-suspend-snapshot~3]
public final class StringTtyConnector implements TtyConnector {

    private final char[] content;
    private int position;

    /// Normalises the line ends to CRLF. `capture-pane` separates the screen's
    /// lines with a bare LF, and an emulator reads that as "one row down, same
    /// column" — the LNM mode that would add the carriage return is off, as on
    /// a real terminal — so the stored screen came out as a staircase: every
    /// line started where the previous one ended and wrapped around, and an
    /// indented screen turned into overlapping nonsense.
    public StringTtyConnector(String content) {
        this.content = content.replaceAll("\r?\n", "\r\n").toCharArray();
    }

    @Override
    public int read(char[] buffer, int offset, int length) {
        if (position >= content.length) {
            return -1; // EOF — the reader loop stops, nothing more to show.
        }
        int count = Math.min(length, content.length - position);
        System.arraycopy(content, position, buffer, offset, count);
        position += count;
        return count;
    }

    @Override
    public boolean ready() {
        return position < content.length;
    }

    @Override
    public void write(byte[] bytes) {
        // Read-only: the mirrored window is gone, keystrokes have nowhere to go.
    }

    @Override
    public void write(String string) {
    }

    @Override
    public boolean isConnected() {
        return position < content.length;
    }

    @Override
    public int waitFor() {
        return 0;
    }

    @Override
    public String getName() {
        return "snapshot";
    }

    @Override
    public void resize(TermSize termSize) {
    }

    @Override
    public void close() {
        position = content.length;
    }
}
