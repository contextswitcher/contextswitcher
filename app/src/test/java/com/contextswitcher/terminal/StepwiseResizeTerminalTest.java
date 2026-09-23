package com.contextswitcher.terminal;

import com.techsenger.jeditermfx.core.RequestOrigin;
import com.techsenger.jeditermfx.core.TerminalDisplay;
import com.techsenger.jeditermfx.core.model.JediTerminal;
import com.techsenger.jeditermfx.core.model.StyleState;
import com.techsenger.jeditermfx.core.model.TerminalTextBuffer;
import com.techsenger.jeditermfx.core.util.TermSize;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// [utest->dsn~terminal-zoom~4]
class StepwiseResizeTerminalTest {

    /// The zoom-in that failed live: Claude Code's alternate-buffer screen,
    /// a few lines of content, the cursor far below them, and the grid cut to
    /// a third in both directions at once.
    @Test
    void jediTermFxAloneThrowsOnTheZoomIn() {
        JediTerminal terminal = claudeScreen(new JediTerminal(DISPLAY, buffer(), new StyleState()));
        assertThatThrownBy(() -> terminal.resize(new TermSize(42, 28), RequestOrigin.User))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void theStepwiseResizeReachesTheNewSize() {
        TerminalTextBuffer buffer = buffer();
        JediTerminal terminal = claudeScreen(new StepwiseResizeTerminal(DISPLAY, buffer, new StyleState()));
        terminal.resize(new TermSize(42, 28), RequestOrigin.User);
        assertThat(terminal.getTerminalWidth()).isEqualTo(42);
        assertThat(terminal.getTerminalHeight()).isEqualTo(28);
        assertThat(buffer.getWidth()).isEqualTo(42);
        assertThat(buffer.getHeight()).isEqualTo(28);
    }

    @Test
    void aResizeThatDoesNotShrinkBothWaysIsLeftAsItIs() {
        TerminalTextBuffer buffer = buffer();
        JediTerminal terminal = claudeScreen(new StepwiseResizeTerminal(DISPLAY, buffer, new StyleState()));
        terminal.resize(new TermSize(200, 90), RequestOrigin.User);
        assertThat(buffer.getWidth()).isEqualTo(200);
        assertThat(buffer.getHeight()).isEqualTo(90);
    }

    /// A shorter alternate buffer left the cursor below the last row, and the
    /// next wrapping write read a line past the screen.
    @Test
    void aShorterScreenKeepsTheCursorOnIt() {
        TerminalTextBuffer buffer = buffer();
        JediTerminal terminal = claudeScreen(new StepwiseResizeTerminal(DISPLAY, buffer, new StyleState()));
        terminal.resize(new TermSize(126, 28), RequestOrigin.User);
        assertThat(terminal.getCursorY()).isEqualTo(28);
    }

    @Test
    void jediTermFxAloneLeavesTheCursorBelowAShorterScreen() {
        JediTerminal terminal = claudeScreen(new JediTerminal(DISPLAY, buffer(), new StyleState()));
        terminal.resize(new TermSize(126, 28), RequestOrigin.User);
        assertThat(terminal.getCursorY()).isGreaterThan(28);
    }

    private static TerminalTextBuffer buffer() {
        return new TerminalTextBuffer(126, 76, new StyleState());
    }

    private static JediTerminal claudeScreen(JediTerminal terminal) {
        terminal.resize(new TermSize(126, 76), RequestOrigin.User);
        terminal.useAlternateBuffer(true);
        for (int line = 1; line <= 5; line++) {
            terminal.cursorPosition(1, line);
            terminal.writeString("line " + line);
        }
        terminal.cursorPosition(1, 70);
        return terminal;
    }

    private static final TerminalDisplay DISPLAY = new NoOpDisplay();
}
