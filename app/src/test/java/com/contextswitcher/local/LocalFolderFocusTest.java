package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~explorer-folder-focus~4]
class LocalFolderFocusTest {

    @Test
    void scriptEmbedsTheFolderAsADoubledSingleQuoteLiteral() {
        String script = LocalFolderFocus.script("C:\\it's\\a folder");
        assertThat(script).contains("$target = 'C:\\it''s\\a folder'");
    }

    @Test
    void scriptFocusesViaShellApplicationAndOpensAsFallback() {
        String script = LocalFolderFocus.script("C:\\p");
        assertThat(script)
                .contains("New-Object -ComObject Shell.Application")
                .contains("$w.Document.Folder.Self.Path")
                .contains("SetForegroundWindow")
                .contains("Start-Process explorer.exe");
    }

    // [utest->dsn~window-move-here~2]
    @Test
    void scriptMovesAWindowFromAnotherDesktopHereBeforeFocusingIt() {
        String script = LocalFolderFocus.script("C:\\p");
        assertThat(script).contains(WindowsWindowMove.TYPES.strip())
                .contains("$outcome = [CsMv]::Focus([IntPtr]$w.HWND)")
                .contains("moved here: ");
    }

    /// A window that stays on another desktop is not raised: a new Explorer
    /// window opens here instead, and nothing switches desktops (field report
    /// 2026-11-15, Insider build 26300). The raise itself lives in
    /// `CsMv.Focus` only — the script body calls no foreground API.
    // [utest->dsn~window-move-here~2]
    @Test
    void aWindowLeftOnAnotherDesktopGetsANewWindowHereInsteadOfAJump() {
        String script = LocalFolderFocus.script("C:\\p");
        String body = script.substring(script.indexOf("$target = "));
        String elsewhere = body.substring(body.indexOf("if ($outcome -like 'elsewhere*')"));
        assertThat(elsewhere.substring(0, elsewhere.indexOf("exit 0")))
                .contains("Start-Process explorer.exe -ArgumentList $target");
        assertThat(body).doesNotContain("SetForegroundWindow").doesNotContain("SwitchToThisWindow");
        assertThat(script).doesNotContain("SwitchToThisWindow");
    }

    @Test
    void commandIsAnEncodedPowershellInvocation() {
        var command = LocalFolderFocus.command("C:\\p");
        assertThat(command).startsWith("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand");
        String decoded = new String(Base64.getDecoder().decode(command.get(4)), StandardCharsets.UTF_16LE);
        assertThat(decoded).isEqualTo(LocalFolderFocus.script("C:\\p"));
    }

    /// Off Windows the folder goes to the desktop's own file manager, so a
    /// path that is no folder must be rejected before anything is launched —
    /// and with the same wording the PowerShell path uses.
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void reportsAMissingFolderWithoutTouchingTheDesktopElsewhere() {
        LocalFolderFocus.FocusResult result =
                new LocalFolderFocus(new LocalCommandRunner()).focus("/no/such/folder");
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).isEqualTo("folder not found: \"/no/such/folder\"");
    }

    /// On Windows nothing changed: the attempt still goes through PowerShell,
    /// and a missing folder is the script's exit 2.
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void stillReportsAMissingFolderThroughPowershellOnWindows() {
        LocalFolderFocus.FocusResult result =
                new LocalFolderFocus(new LocalCommandRunner()).focus("C:\\no\\such\\folder");
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).isEqualTo("folder not found: \"C:\\no\\such\\folder\"");
    }
}
