package com.contextswitcher.terminal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import com.techsenger.jeditermfx.core.ProcessTtyConnector;
import com.techsenger.jeditermfx.core.util.TermSize;
import org.jspecify.annotations.Nullable;

/// JediTermFX connector over a pty4j pty: the widget's resize reaches the
/// pty (and through ssh the remote tmux) as a window-size change. Promoted
/// from the https://github.com/contextswitcher/contextswitcher-private/issues/34 spike.
// [impl->dsn~terminal-pane~14]
public class PtyTtyConnector extends ProcessTtyConnector {

    private final PtyProcess pty;

    private final AtomicBoolean closed = new AtomicBoolean();

    private final AtomicReference<@Nullable Runnable> onOutput = new AtomicReference<>();

    private final LeakingCsiFilter leaks = new LeakingCsiFilter();

    public PtyTtyConnector(PtyProcess pty, List<String> command) {
        super(pty, StandardCharsets.UTF_8, command);
        this.pty = pty;
    }

    /// Runs `action` once, on the reader thread, as soon as the pty delivers
    /// its next batch of output — the pane's signal that the mirror has
    /// actually repainted, which no ssh side-channel reply can tell it.
    /// A second call replaces a still-pending action.
    public void onNextOutput(Runnable action) {
        onOutput.set(action);
    }

    /// Reads past the sequences [LeakingCsiFilter] drops. A chunk that filters
    /// down to nothing reads again, because JediTermFX takes a read of 0 as
    /// the end of the stream.
    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        while (true) {
            int held = leaks.held();
            int read = super.read(buf, offset + held, length - held);
            if (read <= 0) {
                return read;
            }
            Runnable action = onOutput.getAndSet(null);
            if (action != null) {
                action.run();
            }
            int filtered = leaks.filter(buf, offset, read);
            if (filtered > 0) {
                return filtered;
            }
        }
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

    /// Ends the pty's process and closes the streams — once.
    ///
    /// JediTermFX closes its connector twice per widget close, on two threads:
    /// the cancelled emulator task closes it in its `finally`, and
    /// `TerminalStarter.close` queues another close. `ProcessTtyConnector.close`
    /// destroys unconditionally, so the second `TerminateProcess` hit a process
    /// the first had already set exiting — Windows refuses that, and pty4j
    /// logged "Failed to terminate process … Zugriff verweigert" on every close
    /// although the whole process tree was gone.
    // [impl->dsn~terminal-process-close~1]
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        pty.destroy();
        try {
            myOutputStream.close();
        } catch (IOException ignored) {
            // Closing anyway; the pty's own cleanup closes it too.
        }
        try {
            myInputStream.close();
        } catch (IOException ignored) {
            // As above.
        }
    }

    @Override
    public boolean isConnected() {
        return pty.isAlive();
    }
}
