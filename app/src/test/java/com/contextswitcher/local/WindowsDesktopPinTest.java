package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~window-desktop-pin~2]
class WindowsDesktopPinTest {

    @Test
    void scriptResolvesTheWindowFromThePidAndPinsViaThePinnedAppsService() {
        String script = WindowsDesktopPin.script(4242L);
        assertThat(script)
                .contains("$targetPid = 4242")
                .contains("MainWindowHandle")
                // IApplicationViewCollection and IVirtualDesktopPinnedApps —
                // IIDs stable since Windows 10 1607.
                .contains("Guid(\"1841C6D7-4F9D-42C0-AF41-8747538F10E5\")")
                .contains("Guid(\"4CE81583-1E4C-4632-A621-07A53543148F\")")
                // The pinned-apps *service* GUID — a wrong value here fails
                // QueryService and the whole pin (the 2026-07-25 bug).
                .contains("B5A399E7-1C87-46B8-88E9-FC5747B171BD")
                .contains("pinned.PinView(view)");
    }

    @Test
    void getViewForHwndSitsAtSlotThreeAndPinViewAtSlotFour() {
        // A COM method is bound by its position, so a stray edit to the
        // placeholder slots would silently call the wrong shell function.
        String script = WindowsDesktopPin.script(1L);
        String views = script.substring(
                script.indexOf("public interface ICsAppViewCollection"),
                script.indexOf("public interface ICsPinnedApps"));
        assertThat(comSlots(views).get(3)).isEqualTo("ICsAppView GetViewForHwnd(IntPtr hwnd);");
        String pins = script.substring(
                script.indexOf("public interface ICsPinnedApps"),
                script.indexOf("public class CsPin"));
        assertThat(comSlots(pins).get(3)).endsWith("bool IsViewPinned(ICsAppView view);");
        assertThat(comSlots(pins).get(4)).isEqualTo("void PinView(ICsAppView view);");
    }

    private static List<String> comSlots(String interfaceBody) {
        return interfaceBody.lines()
                .map(String::strip)
                .filter(line -> line.endsWith(");"))
                .toList();
    }

    @Test
    void anAlreadyPinnedViewIsLeftAloneAndSaysSo() {
        assertThat(pinReturning("already-pinned\n").pin(1L).detail())
                .isEqualTo("already pinned on all desktops");
        assertThat(pinReturning("pinned\n").pin(1L).detail())
                .isEqualTo("pinned on all desktops");
    }

    /// A pin whose script "ran" with the given stdout, so no PowerShell starts.
    private static WindowsDesktopPin pinReturning(String stdout) {
        return new WindowsDesktopPin(new LocalCommandRunner() {
            @Override
            public LocalResult run(List<String> command) {
                return new LocalResult(0, stdout, "");
            }
        });
    }

    @Test
    void commandIsAnEncodedPowershellInvocation() {
        var command = WindowsDesktopPin.command(7L);
        assertThat(command).startsWith("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand");
        String decoded = new String(Base64.getDecoder().decode(command.get(4)), StandardCharsets.UTF_16LE);
        assertThat(decoded).isEqualTo(WindowsDesktopPin.script(7L));
    }
}
