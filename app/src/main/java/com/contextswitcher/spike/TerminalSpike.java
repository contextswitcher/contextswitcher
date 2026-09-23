package com.contextswitcher.spike;

import com.contextswitcher.ssh.ProcessSshRunner;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import com.techsenger.jeditermfx.core.ProcessTtyConnector;
import com.techsenger.jeditermfx.core.TerminalColor;
import com.techsenger.jeditermfx.core.util.TermSize;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import com.techsenger.jeditermfx.ui.settings.DefaultSettingsProvider;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.tinylog.Logger;

/// Spike https://github.com/contextswitcher/contextswitcher-private/issues/34, decides MADR 0007: a live terminal inside the JavaFX app,
/// mirroring the remote tmux/Claude session.
///
/// Stack under test: **JediTermFX** (Techsenger's native JavaFX port of
/// JetBrains' JediTerm; dual-licensed LGPLv3/Apache-2.0, we elect
/// Apache-2.0 — and no Swing/`SwingNode` bridge needed), fed by a
/// **pty4j** (EPL-1.0) pty running `ssh -t <remote> tmux attach` —
/// ConPTY on Windows, so ssh gets a real tty.
///
/// Run: `gradlew :app:spikeTerminal -Dspike.remote=koppor@devbox
/// [-Dspike.session=0]`.
/// Spike questions: does it render/scroll/color the Claude TUI correctly,
/// does typing reach Claude, does resize propagate to the remote tmux,
/// does it hold up under the classpath-loaded JavaFX 26 + Java 25?
public class TerminalSpike extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        String remote = System.getProperty("spike.remote", "koppor@devbox");
        String session = System.getProperty("spike.session", "");
        String ssh = com.contextswitcher.ssh.ProcessSshRunner.sshExecutable();
        List<String> command = session.isBlank()
                ? List.of(ssh, "-t", remote, "tmux", "attach")
                : List.of(ssh, "-t", remote, "tmux", "attach", "-t", session);
        Logger.info("Spike terminal: {}", command);

        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("TERM", "xterm-256color");
        PtyProcess process = new PtyProcessBuilder()
                .setCommand(command.toArray(String[]::new))
                .setEnvironment(environment)
                .setInitialColumns(120)
                .setInitialRows(32)
                .setConsole(false)
                .setUseWinConPty(true)
                .start();

        JediTermFxWidget widget = new JediTermFxWidget(120, 32, new DarkSettings());
        widget.setTtyConnector(new PtyTtyConnector(process, command));
        widget.start();

        VBox.setVgrow(widget.getPane(), Priority.ALWAYS);
        stage.setScene(new Scene(new VBox(widget.getPane()), 1000, 640));
        stage.setTitle("JediTermFX spike — " + String.join(" ", command));
        stage.setOnCloseRequest(event -> {
            widget.close();
            process.destroy();
            Platform.exit();
        });
        stage.show();
    }

    /// Dark theme matching the snapshot pane — the provider default is
    /// black-on-white.
    private static final class DarkSettings extends DefaultSettingsProvider {

        @Override
        public TerminalColor getDefaultBackground() {
            return new TerminalColor(0x1e, 0x1e, 0x1e);
        }

        @Override
        public TerminalColor getDefaultForeground() {
            return new TerminalColor(0xe6, 0xe6, 0xe6);
        }
    }

    /// pty-backed connector: JediTermFX's resize reaches the pty (and thus
    /// the remote tmux) as a window-size change.
    private static final class PtyTtyConnector extends ProcessTtyConnector {

        private final PtyProcess pty;

        PtyTtyConnector(PtyProcess pty, List<String> command) {
            super(pty, StandardCharsets.UTF_8, command);
            this.pty = pty;
        }

        @Override
        public String getName() {
            return "ssh";
        }

        @Override
        public void resize(TermSize termSize) {
            if (isConnected()) {
                pty.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
            }
        }

        @Override
        public boolean isConnected() {
            return pty.isAlive();
        }
    }
}
