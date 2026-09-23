package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.contextswitcher.config.Browser;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~browser-desktop-windows~2]
class BrowserDesktopWindowsTest {

    private static final BrowserDesktopWindows FIREFOX =
            new BrowserDesktopWindows(new LocalCommandRunner(), Browser.FIREFOX);

    @Test
    void scriptResolvesTheDesktopFromTheRegistryAndFiltersMozillaWindows() {
        String script = FIREFOX.script("JabRef");
        assertThat(script)
                .contains("$target = 'JabRef'")
                .contains("VirtualDesktopIDs")
                .contains("MozillaWindowClass")
                // The *documented* IVirtualDesktopManager (CLSID + IID) —
                // GetWindowDesktopId is what scopes the capture to the desktop.
                .contains("Guid(\"aa509086-5ca9-4c25-8f95-589d3c07b48a\")")
                .contains("Guid(\"a5cd92ff-29be-454c-8d04-d82879fb3f1b\")")
                .contains("GetWindowDesktopId")
                // A pinned window is shown on all desktops and must survive.
                .contains("IsPinned");
    }

    @Test
    void getWindowDesktopIdSitsAtSlotTwoOfTheDocumentedInterface() {
        // COM binds by position: IsWindowOnCurrentVirtualDesktop is slot 1,
        // GetWindowDesktopId slot 2 — an edit reordering them would silently
        // ask the wrong question.
        String script = FIREFOX.script("x");
        String vdm = script.substring(
                script.indexOf("public interface ICsVdm"),
                script.indexOf("public interface ICsShell"));
        List<String> slots = vdm.lines().map(String::strip)
                .filter(line -> line.endsWith(");")).toList();
        assertThat(slots.get(0)).contains("IsWindowOnCurrentVirtualDesktop");
        assertThat(slots.get(1)).isEqualTo("int GetWindowDesktopId(IntPtr window, out Guid desktop);");
    }

    @Test
    void pinCheckUsesTheStablePinnedAppsInterfaces() {
        assertThat(FIREFOX.script("x"))
                .contains("Guid(\"1841C6D7-4F9D-42C0-AF41-8747538F10E5\")")
                .contains("Guid(\"4CE81583-1E4C-4632-A621-07A53543148F\")")
                .contains("B5A399E7-1C87-46B8-88E9-FC5747B171BD")
                .contains("IsViewPinned(view)");
    }

    /// The capture closes what it lists, so a Chrome capture must not sweep up
    /// every other Chromium window on the desktop — the process name is what
    /// separates Chrome from VS Code, Slack, and the rest.
    // [utest->dsn~browser-choice~2]
    @Test
    void chromeCaptureIsScopedToChromesOwnProcess() {
        String script = new BrowserDesktopWindows(new LocalCommandRunner(), Browser.CHROME)
                .script("JabRef");
        assertThat(script)
                .contains("$cls.ToString() -ne 'Chrome_WidgetWin_1'")
                .contains("[CsFdw]::ProcessName($h) -ine 'chrome'")
                .doesNotContain("MozillaWindowClass");
    }

    @Test
    void singleQuotesInTheDesktopNameAreEscaped() {
        assertThat(FIREFOX.script("it's"))
                .contains("$target = 'it''s'");
    }

    @Test
    void commandIsAnEncodedPowershellInvocation() {
        var command = FIREFOX.command("JabRef");
        assertThat(command).startsWith("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand");
        String decoded = new String(Base64.getDecoder().decode(command.get(4)), StandardCharsets.UTF_16LE);
        assertThat(decoded).isEqualTo(FIREFOX.script("JabRef"));
    }

    @Test
    void captionsAreTheStdoutLinesAndFailureIsNull() {
        assertThat(listing(0, "My PR — Mozilla Firefox\nDocs — Mozilla Firefox\n")
                .captionsOn("JabRef"))
                .containsExactly("My PR — Mozilla Firefox", "Docs — Mozilla Firefox");
        assertThat(listing(0, "").captionsOn("JabRef")).isEmpty();
        assertThat(listing(1, "").captionsOn("JabRef")).isNull();
    }

    /// A lister whose script "ran" with the given exit code and stdout.
    private static BrowserDesktopWindows listing(int exitCode, String stdout) {
        return new BrowserDesktopWindows(new LocalCommandRunner() {
            @Override
            public LocalResult run(List<String> command) {
                return new LocalResult(exitCode, stdout, "");
            }
        }, Browser.FIREFOX);
    }
}
