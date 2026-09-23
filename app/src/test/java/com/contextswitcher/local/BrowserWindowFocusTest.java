package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.contextswitcher.config.Browser;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~pr-state-indicator~3]
class BrowserWindowFocusTest {

    private static final BrowserWindowFocus FIREFOX =
            new BrowserWindowFocus(new LocalCommandRunner(), Browser.FIREFOX);
    private static final BrowserWindowFocus CHROME =
            new BrowserWindowFocus(new LocalCommandRunner(), Browser.CHROME);

    @Test
    void commandIsEncodedPowershellWithoutRawScript() {
        assertThat(FIREFOX.command("Some PR — Mozilla Firefox"))
                .startsWith("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand")
                .hasSize(5);
    }

    @Test
    void encodedScriptMatchesTheTitledFirefoxWindow() {
        String encoded = FIREFOX.command("My PR title").get(4);
        String script = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_16LE);
        assertThat(script)
                .contains("EnumWindows")
                .contains("MozillaWindowClass")
                .contains("$target = 'My PR title'")
                .contains("SetForegroundWindow");
    }

    // [utest->dsn~pr-open-on-category-desktop~3]
    @Test
    void currentDesktopScriptFiltersByTheDocumentedVirtualDesktopApi() {
        String script = decoded(FIREFOX.currentDesktopCommand());
        assertThat(script)
                .contains("MozillaWindowClass")
                .contains("IsWindowOnCurrentVirtualDesktop")
                .contains("SetForegroundWindow")
                // No title to match: any Firefox window on this desktop will do.
                .doesNotContain("__TITLE__")
                // The raised window's caption is the answer, so the extension
                // can open the tab into exactly that window.
                .contains("GetWindowText($found");
    }

    // [utest->dsn~pr-open-on-category-desktop~3]
    @Test
    void focusOnCurrentDesktopAnswersWithTheRaisedCaption() {
        LocalCommandRunner raised = new LocalCommandRunner() {
            @Override
            public LocalResult run(java.util.List<String> command) {
                return new LocalResult(0, "Pull Request #1 — Mozilla Firefox\r\n", "");
            }
        };
        assertThat(new BrowserWindowFocus(raised, Browser.FIREFOX).focusOnCurrentDesktop())
                .isEqualTo("Pull Request #1 — Mozilla Firefox");
        LocalCommandRunner none = new LocalCommandRunner() {
            @Override
            public LocalResult run(java.util.List<String> command) {
                return new LocalResult(2, "not found\n", "");
            }
        };
        assertThat(new BrowserWindowFocus(none, Browser.FIREFOX).focusOnCurrentDesktop()).isNull();
    }

    // [utest->dsn~pr-open-on-category-desktop~3]
    @Test
    void launchOpensASeparateWindowSoItLandsOnTheDesktopInView() {
        assertThat(decoded(FIREFOX.launchCommand("https://example.org/pull/1?x=it's")))
                .isEqualTo("Start-Process 'firefox' -ArgumentList '-new-window', "
                        + "'https://example.org/pull/1?x=it''s'");
    }

    /// The browser choice reaches every script: the class *and* the process
    /// name, because `Chrome_WidgetWin_1` alone is every Electron app on the
    /// machine and this same filter decides what a complete-control suspend
    /// closes.
    // [utest->dsn~browser-choice~2]
    @Test
    void chromeScriptsFilterByChromesClassAndProcess() {
        for (String script : List.of(decoded(CHROME.command("My PR title")),
                decoded(CHROME.currentDesktopCommand()))) {
            assertThat(script)
                    .contains("$cls.ToString() -ne 'Chrome_WidgetWin_1'")
                    .contains("[CsFf]::ProcessName($h) -ieq 'chrome'")
                    .doesNotContain("MozillaWindowClass");
        }
    }

    // [utest->dsn~browser-choice~2]
    @Test
    void firefoxScriptsCheckTheProcessTooSoThunderbirdIsNotAFirefoxWindow() {
        assertThat(decoded(FIREFOX.currentDesktopCommand()))
                .contains("$cls.ToString() -ne 'MozillaWindowClass'")
                .contains("[CsFf]::ProcessName($h) -ieq 'firefox'");
    }

    /// Chrome's new-window flag is `--new-window`, not Firefox's `-new-window`.
    // [utest->dsn~browser-choice~2]
    @Test
    void chromeLaunchesWithItsOwnExecutableAndFlag() {
        assertThat(decoded(CHROME.launchCommand("https://example.org/pull/1")))
                .isEqualTo("Start-Process 'chrome' -ArgumentList '--new-window', "
                        + "'https://example.org/pull/1'");
    }

    private static String decoded(List<String> command) {
        return new String(Base64.getDecoder().decode(command.get(4)), StandardCharsets.UTF_16LE);
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', textBlock = """
            "plain title",           "plain title"
            "it's a title",          "it''s a title"
            """)
    void escapeDoublesSingleQuotes(String value, String expected) {
        assertThat(BrowserWindowFocus.escape(value)).isEqualTo(expected);
    }
}
