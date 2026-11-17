package com.contextswitcher.local;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~environment-profile-gather-windows~1]
class WindowGatherTest {

    /// A process that runs until destroyed and prints nothing.
    private static final class FakeProcess extends Process {
        boolean alive = true;

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
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
        public boolean isAlive() {
            return alive;
        }

        @Override
        public void destroy() {
            alive = false;
        }
    }

    private final List<FakeProcess> started = new ArrayList<>();
    private final WindowGather gather = new WindowGather(command -> {
        FakeProcess process = new FakeProcess();
        started.add(process);
        return process;
    }, true);

    @Test
    void scriptCompilesTheScreenSelectorAlongsideAndEscapesTheSelector() {
        String script = WindowGather.script("O'Brien Beamer", 4711);

        assertThat(script)
                .contains("public static class CsGather")
                .contains("public class CsScreens {")
                .contains("public static int Pick(Screen s, string[] keys)")
                .endsWith("[CsGather]::Run('O''Brien Beamer', 4711, " + WindowGather.GRACE_MS + ")\n");
        // One Add-Type: CsGather refers to CsScreens, which a second
        // in-memory assembly would not see.
        assertThat(script.split("Add-Type", -1)).hasSize(2);
        assertThat(script.indexOf("public class CsScreens {")).isLessThan(script.indexOf("\"@"));
    }

    @Test
    void sameTargetKeepsTheWatcherAnotherRestartsItNullStopsIt() {
        assertThat(gather.gather("internal").detail()).isEqualTo("windows gathered onto internal");
        assertThat(gather.gather("internal").detail()).isEmpty();
        assertThat(started).hasSize(1);

        gather.gather("primary");
        assertThat(started).hasSize(2);
        assertThat(started.getFirst().alive).isFalse();

        assertThat(gather.gather(null).detail()).isEqualTo("windows no longer gathered");
        assertThat(started.getLast().alive).isFalse();
        assertThat(gather.gather(null).detail()).isEmpty();
    }

    @Test
    void aDeadWatcherIsRestarted() {
        gather.gather("internal");
        started.getFirst().alive = false;

        gather.gather("internal");

        assertThat(started).hasSize(2);
    }

    @Test
    void closeStopsTheWatcher() {
        gather.gather("internal");
        gather.close();

        assertThat(started.getFirst().alive).isFalse();
    }

    @Test
    void elsewhereNothingStarts() {
        WindowGather linux = new WindowGather(command -> {
            throw new AssertionError("no watcher off Windows");
        }, false);

        assertThat(linux.gather("internal").ok()).isTrue();
        assertThat(linux.gather("internal").detail()).isEqualTo("gatherWindows: Windows only");
    }
}
