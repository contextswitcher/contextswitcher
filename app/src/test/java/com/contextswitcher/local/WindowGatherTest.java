package com.contextswitcher.local;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~environment-profile-gather-windows~4]
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
    private final List<List<String>> commands = new ArrayList<>();
    private final WindowGather gather = new WindowGather(command -> {
        FakeProcess process = new FakeProcess();
        started.add(process);
        commands.add(command);
        return process;
    }, true);

    /// The script a launched command runs, inline or through the loader.
    private static String scriptOf(List<String> command) throws java.io.IOException {
        String last = command.getLast();
        java.util.regex.Matcher file = java.util.regex.Pattern.compile("ReadAllText\\('([^']+)'").matcher(last);
        if (command.contains("-EncodedCommand")) {
            String decoded = new String(java.util.Base64.getDecoder().decode(last),
                    java.nio.charset.StandardCharsets.UTF_16LE);
            file = java.util.regex.Pattern.compile("ReadAllText\\('([^']+)'").matcher(decoded);
            if (!file.find()) {
                return decoded;
            }
        } else if (!file.find()) {
            return last;
        }
        return java.nio.file.Files.readString(java.nio.file.Path.of(file.group(1).replace("''", "'")));
    }

    /// Field report 2026-11-22: at the desk a new window on the 27" was
    /// pulled onto the 32". `gatherWhen: docking` sweeps only after a screen
    /// change; a watcher started without one (app start, task switch) leaves
    /// the windows where they are.
    @Test
    void dockingOnlySweepsOnlyRightAfterAScreenChange() throws Exception {
        assertThat(gather.gather("DELL U3223QE", true).detail())
                .isEqualTo("windows gathered onto DELL U3223QE on docking");
        assertThat(scriptOf(commands.getLast())).contains(", $true, $false, " + WindowGather.DOCK_SWEEP_MS + ", " + WindowGather.SETTLE_MS + ")");

        gather.gather(null);
        gather.screensChanged();
        gather.gather("DELL U3223QE", true);
        assertThat(scriptOf(commands.getLast())).contains(", $true, $true, " + WindowGather.DOCK_SWEEP_MS + ", " + WindowGather.SETTLE_MS + ")");

        // Switching the mode restarts the watcher; `always` always gathers.
        gather.gather("DELL U3223QE", false);
        assertThat(started).hasSize(3);
        assertThat(scriptOf(commands.getLast())).contains(", $false, $true, " + WindowGather.DOCK_SWEEP_MS + ", " + WindowGather.SETTLE_MS + ")");
    }

    @Test
    void scriptCompilesTheScreenSelectorAlongsideAndEscapesTheSelector() {
        String script = WindowGather.script("O'Brien Beamer", 4711);

        assertThat(script)
                .contains("public static class CsGather")
                .contains("public class CsScreens {")
                .contains("public static int Pick(Screen s, string[] keys)")
                .endsWith("[CsGather]::Run('O''Brien Beamer', 4711, " + WindowGather.GRACE_MS + ", "
                        + WindowGather.MAX_TRIES + ", $false, $true, " + WindowGather.DOCK_SWEEP_MS + ", " + WindowGather.SETTLE_MS + ")\n");
        // One Add-Type: CsGather refers to CsScreens, which a second
        // in-memory assembly would not see.
        assertThat(script.split("Add-Type", -1)).hasSize(2);
        assertThat(script.indexOf("public class CsScreens {")).isLessThan(script.indexOf("\"@"));
    }

    /// Field report 2026-11-22: on a screen without a taskbar a maximized
    /// window covers the whole screen and was taken for a slide show.
    @Test
    void maximizedIsToldApartFromFullScreenAndOtherDesktopsAreGathered() {
        String script = WindowGather.script("DELL U3223QE", 4711);

        assertThat(script)
                .contains("bool maximized = IsZoomed(h) && (GetWindowLong(h, -16) & 0x00C00000) == 0x00C00000;")
                .contains("return covers && !maximized;")
                .contains("cloaked != 0 && cloaked != 2")
                .contains("did not stay: ")
                .contains("if (!seen.Contains(h) && !IsWindow(h))")
                .contains("(!dockingOnly || sweeping)");
    }

    /// Compiles the watcher's C# for real: with a parent pid that does not
    /// exist, `Run` returns at once, so nothing on the desktop moves.
    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void watcherCompilesAndExitsWithoutItsParent() {
        LocalCommandRunner.LocalResult result = new LocalCommandRunner(java.time.Duration.ofSeconds(60))
                .run(PowerShellCommand.of(WindowGather.script("DELL U3223QE", Integer.MAX_VALUE)));

        assertThat(result.stderr()).doesNotContain("error CS");
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).doesNotContain("moved");
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
