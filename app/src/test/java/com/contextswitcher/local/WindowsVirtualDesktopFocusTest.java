package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~category-desktop-focus~4]
class WindowsVirtualDesktopFocusTest {

    @Test
    void scriptEmbedsTheNameAsADoubledSingleQuoteLiteral() {
        String script = WindowsVirtualDesktopFocus.script("Oliver's desk");
        assertThat(script).contains("$target = 'Oliver''s desk'");
    }

    @Test
    void scriptResolvesByRegistryNameAndWalksWithCtrlWinArrows() {
        String script = WindowsVirtualDesktopFocus.script("vs.code");
        assertThat(script)
                .contains("VirtualDesktopIDs")
                .contains("CurrentVirtualDesktop")
                .contains("\\Desktops\\{")
                .contains("keybd_event")
                // 0x27 = right arrow, 0x25 = left arrow; the walk direction is
                // chosen by the signed delta and stepped |delta| times.
                .contains("$vk = if ($delta -ge 0) { 0x27 } else { 0x25 }")
                .contains("Send-Switch $vk");
    }

    @Test
    void scriptTriesTheDirectComSwitchBeforeWalking() {
        String script = WindowsVirtualDesktopFocus.script("vs.code");
        assertThat(script)
                // IVirtualDesktopManagerInternal as of Windows 11 24H2, reached
                // through the immersive shell's IServiceProvider.
                .contains("Guid(\"53F5CA0B-158F-4124-900C-057158060B27\")")
                .contains("C2F03A33-21F5-47FA-B4BB-156362A2F239")
                .contains("manager.SwitchDesktop(desktop)");
        assertThat(script.indexOf("Invoke-DirectSwitch $targetGuid"))
                .isLessThan(script.indexOf("Send-Switch $vk"));
    }

    @Test
    void switchDesktopSitsAtVtableSlotSixAndFindDesktopAtEleven() {
        // A COM method is bound by its position, so a stray edit to the
        // placeholder slots would silently call the wrong shell function.
        String script = WindowsVirtualDesktopFocus.script("vs.code");
        String body = script.substring(
                script.indexOf("public interface ICsVdmInternal"),
                script.indexOf("public class CsVdCom"));
        var slots = body.lines()
                .map(String::strip)
                .filter(line -> line.endsWith(");"))
                .toList();
        assertThat(slots.get(6)).isEqualTo("void SwitchDesktop(IntPtr desktop);");
        assertThat(slots.get(11)).isEqualTo("IntPtr FindDesktop(ref Guid id);");
    }

    @Test
    void aWalkedSwitchSaysSoSoAMovedComInterfaceIsVisible() {
        assertThat(focusReturning("focused-walk: vs.code\n").focus("vs.code").detail())
                .isEqualTo("focused desktop \"vs.code\" (hotkey walk)");
        assertThat(focusReturning("focused-direct: vs.code\n").focus("vs.code").detail())
                .isEqualTo("focused desktop \"vs.code\"");
    }

    // [utest->dsn~fallback-desktop~1]
    @Test
    void aMissingDesktopIsRetriedOnTheFallback() {
        var runner = new RecordingRunner(name -> name.contains("misc") ? 0 : 2);
        var result = new WindowsVirtualDesktopFocus(runner, "misc").focus("jabref");
        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).isEqualTo("focused desktop \"misc\" (no desktop \"jabref\")");
        assertThat(runner.names).containsExactly("jabref", "misc");
    }

    // [utest->dsn~fallback-desktop~1]
    @Test
    void aMissingFallbackFailsNamingBothAndIsNotRetriedForItself() {
        var runner = new RecordingRunner(name -> 2);
        assertThat(new WindowsVirtualDesktopFocus(runner, "misc").focus("jabref").detail())
                .isEqualTo("desktop not found: \"jabref\", nor the fallback \"misc\"");
        assertThat(new WindowsVirtualDesktopFocus(runner, "misc").focus("MISC").detail())
                .isEqualTo("desktop not found: \"MISC\"");
        // Without a fallback configured a miss stays a plain miss.
        assertThat(new WindowsVirtualDesktopFocus(runner).focus("jabref").detail())
                .isEqualTo("desktop not found: \"jabref\"");
        assertThat(runner.names).containsExactly("jabref", "misc", "MISC", "jabref");
    }

    /// A runner that never starts PowerShell: it decodes the desktop name out of
    /// the encoded script, records it, and answers with `exitCode`'s verdict.
    private static final class RecordingRunner extends LocalCommandRunner {
        private final List<String> names = new java.util.ArrayList<>();
        private final java.util.function.ToIntFunction<String> exitCode;

        private RecordingRunner(java.util.function.ToIntFunction<String> exitCode) {
            this.exitCode = exitCode;
        }

        @Override
        public LocalResult run(List<String> command) {
            String script = new String(Base64.getDecoder().decode(command.get(4)),
                    StandardCharsets.UTF_16LE);
            String name = script.split("\\$target = '", 2)[1].split("'\n", 2)[0];
            names.add(name);
            int exit = exitCode.applyAsInt(name);
            return new LocalResult(exit, exit == 0 ? "focused-direct: " + name : "not found", "");
        }
    }

    /// A focus whose script "ran" with the given stdout, so no PowerShell starts.
    private static WindowsVirtualDesktopFocus focusReturning(String stdout) {
        return new WindowsVirtualDesktopFocus(new LocalCommandRunner() {
            @Override
            public LocalResult run(List<String> command) {
                return new LocalResult(0, stdout, "");
            }
        });
    }

    @Test
    void commandIsAnEncodedPowershellInvocation() {
        var command = WindowsVirtualDesktopFocus.command("vs.code");
        assertThat(command).startsWith("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand");
        String decoded = new String(Base64.getDecoder().decode(command.get(4)), StandardCharsets.UTF_16LE);
        assertThat(decoded).isEqualTo(WindowsVirtualDesktopFocus.script("vs.code"));
    }

    // [utest->dsn~active-desktop-filter~6]
    @Test
    void currentScriptReadsTheActiveDesktopNameFromTheRegistry() {
        String script = WindowsVirtualDesktopFocus.currentScript();
        assertThat(script)
                .contains("CurrentVirtualDesktop")
                .contains("\\Desktops\\{")
                .contains("Write-Output $name");
    }

    // [utest->dsn~active-desktop-filter~6]
    @Test
    void currentScriptAsksTheShellBeforeTheRegistry() {
        // The registry's CurrentVirtualDesktop is Explorer's lazily written
        // mirror and goes stale after programmatic switches — the shell's own
        // GetCurrentDesktop is the authority; the registry is the fallback for
        // builds whose interface moved (QueryService then fails cleanly).
        String script = WindowsVirtualDesktopFocus.currentScript();
        assertThat(script)
                .contains("IntPtr GetCurrentDesktop();")
                .contains("C2F03A33-21F5-47FA-B4BB-156362A2F239")
                // IVirtualDesktop as of Windows 11 22H2+, its GetID at slot 1.
                .contains("Guid(\"3F07F4BE-B107-441A-AF0F-39D82529072C\")")
                .contains("Marshal.GetObjectForIUnknown(manager.GetCurrentDesktop())");
        // The registry helper is *defined* first (PowerShell needs that), but
        // it is only reached after the shell answered nothing.
        assertThat(script.indexOf("[CsVdCur]::Current()"))
                .isLessThan(script.indexOf("$curBytes = Get-CurrentBytes $base"));
    }

    // [utest->dsn~active-desktop-filter~6]
    @Test
    void theRegistryFallbackAlsoReadsTheWindows10SessionInfoLocation() {
        // Windows 10 keeps the active desktop's GUID under
        // SessionInfo\{session}\VirtualDesktops, not next to VirtualDesktopIDs.
        // Reading only the Windows 11 location made every Windows 10 read miss
        // and report desktop 0, so the app was stuck on the first desktop's
        // name however the user switched.
        for (String script : List.of(
                WindowsVirtualDesktopFocus.currentScript(),
                WindowsVirtualDesktopFocus.script("vs.code"))) {
            assertThat(script)
                    .contains("function Get-CurrentBytes")
                    .contains("\\Explorer\\SessionInfo\\")
                    .contains("(Get-Process -Id $PID).SessionId")
                    .contains("Get-CurrentBytes $base");
        }
    }

    // [utest->dsn~active-desktop-filter~6]
    @Test
    void getCurrentDesktopSitsAtVtableSlotThree() {
        // Same guard as for the switch slots: a COM method is bound by its
        // position, so a stray edit would silently call the wrong function.
        String script = WindowsVirtualDesktopFocus.currentScript();
        String body = script.substring(
                script.indexOf("public interface ICsVdmInternal"),
                script.indexOf("public interface ICsVdesk"));
        var slots = body.lines()
                .map(String::strip)
                .filter(line -> line.endsWith(");"))
                .toList();
        assertThat(slots.get(3)).isEqualTo("IntPtr GetCurrentDesktop();");
        assertThat(slots.get(6)).isEqualTo("void SwitchDesktop(IntPtr desktop);");
    }

    // [utest->dsn~active-desktop-filter~6]
    @Test
    void currentScriptFallsBackToTheFirstDesktopWhenNoSwitchHappenedYet() {
        // Explorer writes CurrentVirtualDesktop only on the first switch of a
        // session; without the VirtualDesktopIDs[0] fallback the filter
        // silently narrowed nothing until the user switched desktops once.
        String script = WindowsVirtualDesktopFocus.currentScript();
        assertThat(script)
                .contains("-Name CurrentVirtualDesktop -ErrorAction SilentlyContinue")
                .contains("VirtualDesktopIDs")
                .contains("$ids[0..15]");
    }

    // [utest->dsn~active-desktop-filter~6]
    @Test
    void watchScriptCompilesTheComShimOnceBeforeTheLoop() {
        // A second Add-Type of the same types throws, so an Add-Type inside
        // the loop would silently demote every tick after the first to the
        // registry read — the stale mirror the watcher exists to avoid.
        String script = WindowsVirtualDesktopFocus.watchScript();
        assertThat(script).containsOnlyOnce("Add-Type");
        assertThat(script.indexOf("Add-Type"))
                .isLessThan(script.indexOf("while ($true)"));
        // The same pinned vtable as the one-shot read, and the registry (with
        // its desktop-0 assumption) still as the in-loop fallback.
        assertThat(script)
                .contains("IntPtr GetCurrentDesktop();")
                .contains("Get-CurrentBytes $base")
                .contains("$ids[0..15]")
                // Non-ASCII desktop names must survive the pipe to the UTF-8
                // reader on the Java side.
                .contains("[Console]::OutputEncoding");
    }

    // [utest->dsn~active-desktop-filter~6]
    @Test
    void watchCommandIsAnEncodedPowershellInvocation() {
        var command = WindowsVirtualDesktopFocus.watchCommand();
        assertThat(command).startsWith("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand");
        String decoded = new String(Base64.getDecoder().decode(command.get(4)), StandardCharsets.UTF_16LE);
        assertThat(decoded).isEqualTo(WindowsVirtualDesktopFocus.watchScript());
    }

    // [utest->dsn~active-desktop-filter~6]
    @Test
    void watchLinesCarryNameUnnamedOrNoise() {
        assertThat(WindowsVirtualDesktopFocus.parseWatchLine("desktop:JabRef"))
                .isEqualTo(new WindowsVirtualDesktopFocus.ActiveDesktop(true, "JabRef"));
        // The active desktop was read but has no name (unrenamed).
        assertThat(WindowsVirtualDesktopFocus.parseWatchLine("desktop:"))
                .isEqualTo(new WindowsVirtualDesktopFocus.ActiveDesktop(true, null));
        // Merged-in stderr chatter is not a reading.
        assertThat(WindowsVirtualDesktopFocus.parseWatchLine("Add-Type : whatever"))
                .isNull();
    }

    // [utest->dsn~active-desktop-filter~6]
    @Test
    void currentDistinguishesNameUnnamedAndFailedRead() {
        assertThat(currentReturning(0, "vs.code\n").current())
                .isEqualTo(new WindowsVirtualDesktopFocus.ActiveDesktop(true, "vs.code"));
        // Exit 2 = the script ran and the desktop has no name: a real "unknown".
        assertThat(currentReturning(2, "").current())
                .isEqualTo(new WindowsVirtualDesktopFocus.ActiveDesktop(true, null));
        // Any other failure never reached the registry: the caller must keep
        // its last known desktop, not un-narrow the list.
        assertThat(currentReturning(-1, "").current())
                .isEqualTo(new WindowsVirtualDesktopFocus.ActiveDesktop(false, null));
    }

    /// A focus whose current-desktop read "ran" with the given outcome.
    private static WindowsVirtualDesktopFocus currentReturning(int exitCode, String stdout) {
        return new WindowsVirtualDesktopFocus(new LocalCommandRunner() {
            @Override
            public LocalResult run(List<String> command) {
                return new LocalResult(exitCode, stdout, "");
            }
        });
    }

    @Test
    void currentCommandIsAnEncodedPowershellInvocation() {
        var command = WindowsVirtualDesktopFocus.currentCommand();
        assertThat(command).startsWith("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand");
        String decoded = new String(Base64.getDecoder().decode(command.get(4)), StandardCharsets.UTF_16LE);
        assertThat(decoded).isEqualTo(WindowsVirtualDesktopFocus.currentScript());
    }
}
