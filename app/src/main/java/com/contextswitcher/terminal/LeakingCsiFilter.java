package com.contextswitcher.terminal;

/// Drops the control sequences JediTermFX would print half of: a CSI whose
/// first byte is `<` or `=`. Its parser only knows `!`, `?` and `>` in that
/// spot, files any other one as "unhandled" and pushes it back in front of
/// the sequence — so it lands on screen as text. Claude Code's kitty keyboard
/// pop `ESC[<u` thereby left one `<` at the cursor, its input line, each time
/// it sent it, until the next redraw of that line. JediTermFX implements no
/// sequence with these prefixes, so nothing it could act on is dropped.
///
/// Stateful because a pty read can end anywhere inside a sequence: a trailing
/// `ESC` or `ESC[` is held back until the next read decides it.
// [impl->dsn~terminal-pane~14]
final class LeakingCsiFilter {

    private static final char ESC = 27;

    private enum State { TEXT, ESCAPE, CSI_START, DROPPING }

    private State state = State.TEXT;

    /// How many already-read chars are held back — the caller reads the next
    /// chunk this far behind its offset, so [#filter] can emit them in place.
    int held() {
        return switch (state) {
            case ESCAPE -> 1;
            case CSI_START -> 2;
            case TEXT, DROPPING -> 0;
        };
    }

    /// Filters `count` fresh chars at `buf[offset + held()]` in place and
    /// returns how many chars now start at `buf[offset]`. The output never
    /// overtakes the input: every char written was read at or after that slot.
    int filter(char[] buf, int offset, int count) {
        int out = offset;
        int in = offset + held();
        int end = in + count;
        while (in < end) {
            char c = buf[in];
            switch (state) {
                case TEXT -> {
                    if (c == ESC) {
                        state = State.ESCAPE;
                    } else {
                        buf[out++] = c;
                    }
                    in++;
                }
                case ESCAPE -> {
                    if (c == '[') {
                        state = State.CSI_START;
                        in++;
                    } else {
                        buf[out++] = ESC;
                        state = State.TEXT;
                    }
                }
                case CSI_START -> {
                    if (c == '<' || c == '=') {
                        state = State.DROPPING;
                        in++;
                    } else {
                        buf[out++] = ESC;
                        buf[out++] = '[';
                        state = State.TEXT;
                    }
                }
                case DROPPING -> {
                    if (c >= 0x40 && c <= 0x7E) {
                        state = State.TEXT;
                        in++;
                    } else if (c < 0x20) {
                        // A control char aborts a CSI in every terminal; let
                        // it through rather than swallow what follows.
                        state = State.TEXT;
                    } else {
                        in++;
                    }
                }
            }
        }
        return out - offset;
    }
}
