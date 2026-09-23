package com.contextswitcher.terminal;

import java.io.IOException;

import com.techsenger.jeditermfx.core.ArrayTerminalDataStream;
import com.techsenger.jeditermfx.core.DataStreamIteratingEmulator;
import com.techsenger.jeditermfx.core.RequestOrigin;
import com.techsenger.jeditermfx.core.emulator.JediEmulator;
import com.techsenger.jeditermfx.core.model.JediTerminal;
import com.techsenger.jeditermfx.core.model.StyleState;
import com.techsenger.jeditermfx.core.model.TerminalTextBuffer;
import com.techsenger.jeditermfx.core.util.TermSize;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-optional-sequences~1]
class OptionalSequencesEmulatorTest {

    private static final String ESC = String.valueOf((char) 27);

    private static final String CR = String.valueOf((char) 13);

    @Test
    void recognizesTheOptionalSequencesClaudeCodeSends() {
        for (String body : new String[] {"?2026h", "?2026l", "?2031h", "?2031l",
                ">5u", ">u", "<u", "<1u", "?u", ">4;2m", ">4m", "1t"}) {
            assertThat(OptionalSequencesEmulator.isIgnored(body)).as(body).isTrue();
        }
    }

    @Test
    void leavesEverythingElseToJediTermFx() {
        // Alternate buffer, cursor visibility, cursor position, secondary DA,
        // SGR, and the plain CSI u that restores the cursor.
        for (String body : new String[] {"?1049h", "?25l", "2;3H", ">c", "1;31m", "u", "s", "2t", "?2004h"}) {
            assertThat(OptionalSequencesEmulator.isIgnored(body)).as(body).isFalse();
        }
    }

    @Test
    void anIgnoredSequenceLeavesNoTraceAndTheNextOneStillWorks() throws IOException {
        TerminalTextBuffer buffer = run("a" + ESC + "[?2026h" + "b" + ESC + "[?2026l"
                + ESC + "[>5u" + ESC + "[?u" + ESC + "[>4;2m"
                + ESC + "[2;1H" + "c" + ESC + "[1;31m" + "d");
        assertThat(buffer.getLine(0).getText().strip()).isEqualTo("ab");
        assertThat(buffer.getLine(1).getText().strip()).isEqualTo("cd");
    }

    /// Whatever is not ignored must come out exactly as JediTermFX alone
    /// renders it — the CSI is read ahead and pushed back, and that must be
    /// invisible: a control char inside a CSI (executed, the CSI continues),
    /// a CSI too long to be one of ours, a plain `CSI u`, a lone `ESC`.
    @Test
    void everythingNotIgnoredRendersExactlyAsJediTermFxAlone() throws IOException {
        for (String output : new String[] {
                "x" + ESC + "[?20" + CR + "y" + "z",
                "a" + ESC + "[1;2;3;4;5;6;7;8;9;10;31m" + "b",
                "a" + ESC + "7" + "bc" + ESC + "[u" + "d",
                "a" + ESC + "(0" + "q" + ESC + "(B" + "q",
                "a" + ESC + "[2J" + ESC + "[3;4H" + "b" + ESC + "[K"}) {
            assertThat(run(output, true).getScreenLines())
                    .as(output.replace(ESC, "ESC").replace(CR, "CR"))
                    .isEqualTo(run(output, false).getScreenLines());
        }
    }

    private static TerminalTextBuffer run(String output) throws IOException {
        return run(output, true);
    }

    private static TerminalTextBuffer run(String output, boolean optionalSequences) throws IOException {
        TerminalTextBuffer buffer = new TerminalTextBuffer(40, 5, new StyleState());
        JediTerminal terminal = new JediTerminal(new NoOpDisplay(), buffer, new StyleState());
        terminal.resize(new TermSize(40, 5), RequestOrigin.User);
        ArrayTerminalDataStream stream = new ArrayTerminalDataStream(output.toCharArray());
        DataStreamIteratingEmulator emulator = optionalSequences
                ? new OptionalSequencesEmulator(stream, terminal)
                : new JediEmulator(stream, terminal);
        while (emulator.hasNext()) {
            emulator.next();
        }
        return buffer;
    }
}
