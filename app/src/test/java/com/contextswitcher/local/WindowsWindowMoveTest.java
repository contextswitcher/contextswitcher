package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~window-move-here~1]
class WindowsWindowMoveTest {

    @Test
    void scriptEmbedsProcessAndClassAsSingleQuoteLiterals() {
        String script = WindowsWindowMove.appScript("ONENOTE", "Framework::CFrame");
        assertThat(script)
                .contains("[CsMv]::BringAppHere('ONENOTE', 'Framework::CFrame')")
                .contains("MoveViewToDesktop(view, current)");
        assertThat(WindowsWindowMove.appScript("it's", ""))
                .contains("BringAppHere('it''s', '')");
    }

    @Test
    void commandIsAnEncodedPowershellInvocation() {
        var command = WindowsWindowMove.appCommand("ONENOTE", "Framework::CFrame");
        assertThat(command).startsWith("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand");
        String decoded = new String(Base64.getDecoder().decode(command.get(4)), StandardCharsets.UTF_16LE);
        assertThat(decoded).isEqualTo(WindowsWindowMove.appScript("ONENOTE", "Framework::CFrame"));
    }

    @Test
    void comSlotsMatchTheWindows11Layout() {
        // A COM method binds by position: a stray edit to a placeholder slot
        // would silently call the wrong shell function.
        String types = WindowsWindowMove.TYPES;
        String views = types.substring(types.indexOf("public interface ICsMvViews"),
                types.indexOf("public interface ICsMvInternal"));
        assertThat(comSlots(views).get(3)).isEqualTo("ICsMvView GetViewForHwnd(IntPtr hwnd);");
        String internal = types.substring(types.indexOf("public interface ICsMvInternal"),
                types.indexOf("public class CsMvVdmClass"));
        assertThat(comSlots(internal).get(1)).isEqualTo("void MoveViewToDesktop(ICsMvView view, IntPtr desktop);");
        assertThat(comSlots(internal).get(3)).isEqualTo("IntPtr GetCurrentDesktop();");
        assertThat(types).contains("Guid(\"53F5CA0B-158F-4124-900C-057158060B27\")");
    }

    private static List<String> comSlots(String interfaceBody) {
        return interfaceBody.lines().map(String::strip).filter(line -> line.endsWith(");")).toList();
    }

    @Test
    void onlyAnActualMoveIsWorthAChipNote() {
        assertThat(WindowsWindowMove.parse("OneNote", "moved\r\n").note()).isEqualTo("moved OneNote here");
        assertThat(WindowsWindowMove.parse("OneNote", "here\n").note()).isNull();
        assertThat(WindowsWindowMove.parse("OneNote", "none\n").note()).isNull();
        assertThat(WindowsWindowMove.parse("OneNote", "failed: E_NOINTERFACE\n").outcome()).isEqualTo("failed");
    }
}
