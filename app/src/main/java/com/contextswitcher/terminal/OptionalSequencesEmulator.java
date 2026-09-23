package com.contextswitcher.terminal;

import java.io.IOException;
import java.util.regex.Pattern;

import com.techsenger.jeditermfx.core.Terminal;
import com.techsenger.jeditermfx.core.TerminalDataStream;
import com.techsenger.jeditermfx.core.emulator.JediEmulator;

/// A `JediEmulator` that knows the optional control sequences Claude Code
/// sends and handles them the way a terminal without those features must:
/// by doing nothing.
///
/// JediTermFX 1.1.0 has no case for them and logs every occurrence as an
/// "Unhandled Control Sequence" warning — synchronized output alone twice per
/// frame, about 186,000 lines in one day's log. They are not faults: each spec
/// makes the feature optional and tells a terminal that lacks it to ignore it.
///
/// - `CSI ? 2026 h` / `l` — synchronized output: the program brackets a frame
///   so a supporting terminal can paint it at once; without it the frame
///   simply paints as it arrives.
/// - `CSI > flags u`, `CSI < u`, `CSI ? u` — kitty keyboard protocol push, pop
///   and query: a terminal without the protocol must not answer the query,
///   which is how the program learns to keep the classic key encoding (the
///   one this pane sends).
/// - `CSI > 4 ; n m`, `CSI > 4 m` — xterm modifyOtherKeys: optional, same
///   reasoning.
/// - `CSI ? 2031 h` / `l` — color-scheme change notifications: optional, and
///   the pane sends none.
/// - `CSI 1 t` — de-iconify the window: the pane is never iconified.
///
/// Everything else goes to JediTermFX untouched, so a sequence nobody
/// recognizes still warns. `JediEmulator`'s dispatch is private, so the CSI is
/// read here and, when it is not one of the above, pushed back for the parent
/// to parse as if it had never been looked at.
// [impl->dsn~terminal-optional-sequences~1]
public class OptionalSequencesEmulator extends JediEmulator {

    private static final char ESC = 27;

    /// A CSI that is still unterminated after this many chars is not one of
    /// ours; it goes back to JediTermFX, whose parser has its own rules.
    private static final int MAX_BODY = 16;

    private static final Pattern IGNORED = Pattern.compile(
            "[?](2026|2031)[hl]"          // synchronized output, color-scheme notifications
            + "|>[0-9]*u|<[0-9]*u|[?]u"   // kitty keyboard push, pop, query
            + "|>4(;[0-9]+)?m"            // modifyOtherKeys
            + "|1t");                     // de-iconify

    private final TerminalDataStream stream;

    public OptionalSequencesEmulator(TerminalDataStream stream, Terminal terminal) {
        super(stream, terminal);
        this.stream = stream;
    }

    @Override
    public void processChar(char ch, Terminal terminal) throws IOException {
        if (ch != ESC) {
            super.processChar(ch, terminal);
            return;
        }
        char next = stream.getChar();
        if (next != '[') {
            stream.pushChar(next);
            super.processChar(ch, terminal);
            return;
        }
        StringBuilder body = new StringBuilder(MAX_BODY);
        while (true) {
            char c = stream.getChar();
            body.append(c);
            if (c >= 0x40 && c <= 0x7E) {
                break;
            }
            // A control char inside a CSI, or a body too long to be ours:
            // JediTermFX decides what it means.
            if (c < 0x20 || body.length() >= MAX_BODY) {
                pushBack(body);
                super.processChar(ch, terminal);
                return;
            }
        }
        if (isIgnored(body)) {
            return;
        }
        pushBack(body);
        super.processChar(ch, terminal);
    }

    /// Whether `body` — a CSI after `ESC [`, final byte included — is one of the
    /// optional sequences this terminal deliberately ignores.
    static boolean isIgnored(CharSequence body) {
        return IGNORED.matcher(body).matches();
    }

    private void pushBack(StringBuilder body) throws IOException {
        body.insert(0, '[');
        char[] chars = body.toString().toCharArray();
        stream.pushBackBuffer(chars, chars.length);
    }
}
