package com.contextswitcher.terminal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-process-close~1]
class PtyTtyConnectorCloseTest {

    /// JediTermFX closes its connector twice per widget close, from two
    /// threads; the process must be destroyed once, or the second
    /// `TerminateProcess` hits a process already exiting.
    @Test
    void concurrentClosesDestroyTheProcessOnce() throws Exception {
        CountingPty pty = new CountingPty();
        PtyTtyConnector connector = new PtyTtyConnector(pty, List.of("test"));
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> closers = List.of(
                Thread.ofPlatform().start(() -> closeAfter(start, connector)),
                Thread.ofPlatform().start(() -> closeAfter(start, connector)),
                Thread.ofPlatform().start(() -> closeAfter(start, connector)));
        start.countDown();
        for (Thread closer : closers) {
            closer.join();
        }
        assertThat(pty.destroyed.get()).isEqualTo(1);
    }

    private static void closeAfter(CountDownLatch start, PtyTtyConnector connector) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        connector.close();
    }

    private static final class CountingPty extends PtyProcess {

        final AtomicInteger destroyed = new AtomicInteger();

        @Override
        public void setWinSize(WinSize winSize) {
        }

        @Override
        public WinSize getWinSize() {
            return new WinSize(80, 24);
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            destroyed.incrementAndGet();
        }
    }
}
