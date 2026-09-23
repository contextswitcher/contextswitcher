package com.contextswitcher.ui;

import com.contextswitcher.terminal.StringTtyConnector;
import com.contextswitcher.terminal.TerminalSettings;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Duration;

/// A read-only terminal window showing fixed ANSI text — "Show diff" for an
/// app-owned session (`dsn~terminal-owned-session~3`). The mirror's own
/// emulator fed by a [StringTtyConnector], so git's colors render as in a
/// terminal; the widget's scrollback holds everything beyond one screen.
// [impl->dsn~terminal-owned-session~3]
public final class AnsiTextWindow {

    private static final int COLUMNS = 140;
    private static final int ROWS = 45;

    /// How long before the view is moved to its first line. A timer, not a
    /// signal: the emulator reads the connector on its own thread, and the
    /// panel applies the scroll range (value included) from its repaint
    /// timeline, so even "all text read" would still race the next repaint.
    private static final Duration SETTLE = Duration.millis(300);

    private AnsiTextWindow() {
    }

    public static void show(String title, String text) {
        JediTermFxWidget view = new JediTermFxWidget(COLUMNS, ROWS,
                new TerminalSettings(Themes.isDark(), Themes.isEverforest()));
        view.setTtyConnector(new StringTtyConnector(text));
        view.start();
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setScene(new Scene(new BorderPane(view.getPane()), 1100, 750));
        stage.setOnHidden(event -> view.close());
        stage.show();
        // A diff is read from the top; the emulator leaves the view on the last
        // screen, and the scrollbar's value is what positions it.
        PauseTransition settle = new PauseTransition(SETTLE);
        settle.setOnFinished(event -> {
            ScrollBar bar = view.getTerminalPanel().getScrollBar();
            bar.setValue(bar.getMin());
        });
        settle.play();
    }
}
