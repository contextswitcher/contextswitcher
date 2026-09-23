package com.contextswitcher.terminal;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-pane~14]
// `sh -c` is the shortest way to get a pty that writes twice; Windows gets
// the same code exercised by the app itself.
@DisabledOnOs(OS.WINDOWS)
class PtyTtyConnectorTest {

    /// The pane lifts its "Switching…" cover on this callback, so it must fire
    /// on the output that follows the request — and only once, or a later
    /// batch would uncover a cover a newer selection just put up.
    @Test
    void nextOutputFiresOnceOnTheFollowingOutput() throws Exception {
        List<String> command = List.of("sh", "-c", "printf hello; sleep 0.2; printf again");
        PtyProcess process = new PtyProcessBuilder()
                .setCommand(command.toArray(String[]::new))
                .setConsole(false)
                .start();
        try {
            PtyTtyConnector connector = new PtyTtyConnector(process, command);
            AtomicInteger fired = new AtomicInteger();
            CountDownLatch first = new CountDownLatch(1);
            connector.onNextOutput(() -> {
                fired.incrementAndGet();
                first.countDown();
            });
            char[] buffer = new char[64];
            while (connector.read(buffer, 0, buffer.length) > 0) {
                // drain everything the process writes
            }
            assertThat(first.await(5, TimeUnit.SECONDS)).as("the callback fired").isTrue();
            assertThat(fired).hasValue(1);
        } finally {
            process.destroy();
        }
    }
}
