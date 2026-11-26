package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~explorer-folder-focus~5]
class LocalFolderFocusTest {

    @Test
    void scriptEmbedsTheFolderAsADoubledSingleQuoteLiteral() {
        String script = LocalFolderFocus.script("C:\\it's\\a folder", 0);
        assertThat(script).contains("$target = 'C:\\it''s\\a folder'");
    }

    @Test
    void scriptFocusesViaShellApplicationAndOpensAsFallback() {
        String script = LocalFolderFocus.script("C:\\p", 0);
        assertThat(script)
                .contains("New-Object -ComObject Shell.Application")
                .contains("$w.Document.Folder.Self.Path")
                .contains("SetForegroundWindow")
                .contains("Start-Process explorer.exe");
    }

    // [utest->dsn~window-move-here~2]
    @Test
    void scriptMovesAWindowFromAnotherDesktopHereBeforeFocusingIt() {
        String script = LocalFolderFocus.script("C:\\p", 0);
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
        String script = LocalFolderFocus.script("C:\\p", 0);
        String body = script.substring(script.indexOf("$target = "));
        String elsewhere = body.substring(body.indexOf("if ($outcome -like 'elsewhere*')"));
        assertThat(elsewhere.substring(0, elsewhere.indexOf("Restore-Back $before")))
                .contains("if (Open-AsTab)")
                .contains("Start-Process explorer.exe -ArgumentList $target");
        assertThat(body).doesNotContain("SetForegroundWindow").doesNotContain("SwitchToThisWindow");
        assertThat(script).doesNotContain("SwitchToThisWindow");
    }

    /// A folder no window shows opens as a new tab of an Explorer window on
    /// this desktop (Ctrl+T, then `Navigate2`); a new window only without one.
    // [utest->dsn~explorer-folder-focus~5]
    @Test
    void aFolderNotShownOpensAsATabOfAnExplorerWindowHere() {
        String script = LocalFolderFocus.script("C:\\p", 0);
        assertThat(script)
                .contains("if ([CsMv]::IsHere($h)) { $hwnd = $h; break }")
                .contains("if (-not [CsMv]::NewTab($hwnd)) { return $false }")
                .contains("$now[$now.Count - 1].Navigate2($target)")
                .contains("if ($o.FullName -notlike '*\\explorer.exe') { continue }");
        String open = script.substring(script.indexOf("if (Test-Path -LiteralPath $target)"));
        assertThat(open.indexOf("if (Open-AsTab)")).isLessThan(open.indexOf("Start-Process explorer.exe"));
        assertThat(WindowsWindowMove.TYPES)
                .contains("if (GetForegroundWindow() != hwnd) { return false; }");
    }

    @Test
    void commandIsAnEncodedPowershellInvocation() {
        var command = LocalFolderFocus.command("C:\\p", 4711);
        assertThat(command).startsWith("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand");
        String decoded = new String(Base64.getDecoder().decode(command.get(4)), StandardCharsets.UTF_16LE);
        assertThat(decoded).isEqualTo(LocalFolderFocus.script("C:\\p", 4711));
    }

    /// A switch keeps ContextSwitcher in front: the script gets the app's pid
    /// and raises its window again after every path that focuses or opens
    /// Explorer — for a new window only once it has shown up, since Explorer
    /// activates it late (field report 2026-11-17). Pid 0 leaves Explorer in
    /// front, and the app's window is raised only when it is on this desktop.
    @Test
    void aSwitchRaisesTheAppAgainAfterExplorer() {
        String script = LocalFolderFocus.script("C:\\p", 4711);
        assertThat(script).contains("$back = 4711")
                .contains("[CsMv]::FrontProcess([uint32]$back)")
                .contains("if ($back -le 0) { return }");
        String body = script.substring(script.indexOf("$shell = New-Object"));
        // focus, tab elsewhere, window elsewhere, tab, new window
        assertThat(body.split("Restore-Back", -1)).hasSize(6);
        assertThat(body.indexOf("Restore-Back 0"))
                .isGreaterThan(body.lastIndexOf("Start-Process explorer.exe"));
        assertThat(WindowsWindowMove.TYPES).contains("owner != pid || !IsHere(h)");
        assertThat(LocalFolderFocus.script("C:\\p", 0)).contains("$back = 0");
        assertThat(LocalFolderFocus.script("C:\\p", -3)).contains("$back = 0");
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
