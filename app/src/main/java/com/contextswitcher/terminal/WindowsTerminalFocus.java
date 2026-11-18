package com.contextswitcher.terminal;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.local.PowerShellCommand;
import com.contextswitcher.local.WindowsWindowMove;

/// Focuses a Windows Terminal tab by its (fixed) title using UI Automation,
/// driven through `powershell`. The script finds the `CASCADIA_HOSTING_WINDOW_CLASS`
/// window whose `TabItem` name equals the title, selects that tab
/// (`SelectionItemPattern.Select`) and brings the window to the foreground
/// (restoring it if minimised; an Alt tap works around the foreground lock).
/// A window on another virtual desktop is moved here first and left alone
/// when that fails (`CsMv.Focus`, [WindowsWindowMove]) — never a desktop jump.
///
/// The whole script is passed as a base64 `-EncodedCommand`, so there is no
/// shell quoting to get wrong, no temp file, and no execution-policy prompt;
/// the only escaping is doubling `'` for the single-quoted title literal.
// [impl->dsn~local-terminal-focus~7]
public class WindowsTerminalFocus {

    /// Outcome of a focus attempt.
    public record FocusResult(boolean ok, String detail) {
    }

    private static final String SCRIPT_TEMPLATE = """
            $ErrorActionPreference = 'Stop'
            Add-Type -AssemblyName UIAutomationClient
            Add-Type -AssemblyName UIAutomationTypes
            Add-Type @"
            using System;
            using System.Runtime.InteropServices;
            using System.Text;
            public class CsFg {
              public delegate bool CB(IntPtr h, IntPtr l);
              [DllImport("user32.dll")] public static extern bool EnumWindows(CB cb, IntPtr l);
              [DllImport("user32.dll")] public static extern int GetClassName(IntPtr h, StringBuilder s, int n);
              [DllImport("user32.dll")] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
            }
            "@
            Add-Type -TypeDefinition @"
            __MOVE_TYPES__
            "@
            $title = '__TITLE__'
            $au = [System.Windows.Automation.AutomationElement]
            $typeCond = New-Object System.Windows.Automation.PropertyCondition($au::ControlTypeProperty, [System.Windows.Automation.ControlType]::TabItem)
            $nameCond = New-Object System.Windows.Automation.PropertyCondition($au::NameProperty, $title)
            $tabCond = New-Object System.Windows.Automation.AndCondition($typeCond, $nameCond)
            function Select-Tab($w, $hwnd) {
              $tab = $w.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $tabCond)
              if ($tab -eq $null) { return $false }
              $tab.GetCurrentPattern([System.Windows.Automation.SelectionItemPattern]::Pattern).Select()
              # UIA lists only windows of this desktop, but the raise goes
              # through the same guard as the fallback below.
              [CsMv]::Focus($hwnd) | Out-Null
              return $true
            }
            $winCond = New-Object System.Windows.Automation.PropertyCondition($au::ClassNameProperty, 'CASCADIA_HOSTING_WINDOW_CLASS')
            $wins = $au::RootElement.FindAll([System.Windows.Automation.TreeScope]::Children, $winCond)
            foreach ($w in $wins) {
              if (Select-Tab $w ([IntPtr]$w.Current.NativeWindowHandle)) {
                Write-Output ("focused: " + $title)
                exit 0
              }
            }
            # Cross-desktop fallback: windows parked on another virtual
            # desktop are cloaked - absent from the UIA root, and UIA cannot
            # descend into them either. EnumWindows does list them, and a
            # window whose TITLE equals the tab title has that tab active,
            # so no tab selection is needed - only bringing it here. A window
            # that cannot be moved here is left alone: raising it would switch
            # the user to its desktop.
            $hwnds = New-Object System.Collections.ArrayList
            [CsFg]::EnumWindows({ param($h, $l)
              $cls = New-Object System.Text.StringBuilder 256
              [CsFg]::GetClassName($h, $cls, 256) | Out-Null
              if ($cls.ToString() -eq 'CASCADIA_HOSTING_WINDOW_CLASS') {
                $txt = New-Object System.Text.StringBuilder 512
                [CsFg]::GetWindowText($h, $txt, 512) | Out-Null
                if ($txt.ToString() -eq $title) { $hwnds.Add($h) | Out-Null }
              }
              $true }, [IntPtr]::Zero) | Out-Null
            foreach ($h in $hwnds) {
              $outcome = [CsMv]::Focus($h)
              Write-Output ($outcome + ": " + $title)
              if ($outcome -like 'elsewhere*') { exit 3 }
              exit 0
            }
            Write-Output ("not found: " + $title)
            exit 2
            """;

    private final LocalCommandRunner runner;

    public WindowsTerminalFocus(LocalCommandRunner runner) {
        this.runner = runner;
    }

    /// Doubles `'` so the title is a safe PowerShell single-quoted literal.
    static String escape(String title) {
        return title.replace("'", "''");
    }

    /// The PowerShell script that focuses the tab with the given title.
    static String script(String tabTitle) {
        return SCRIPT_TEMPLATE.replace("__MOVE_TYPES__", WindowsWindowMove.TYPES.strip())
                .replace("__TITLE__", escape(tabTitle));
    }

    /// The local argv: the script as a base64 `-EncodedCommand` (UTF-16LE).
    static List<String> command(String tabTitle) {
        return PowerShellCommand.of(script(tabTitle));
    }

    public FocusResult focus(String tabTitle) {
        return focus(tabTitle, null);
    }

    /// `wt` argv opening a tab titled `tabTitle` in `folder` in the most recent
    /// Windows Terminal window (a new window when none is open). The title is
    /// pinned (`--suppressApplicationTitle`), so the next switch finds the tab
    /// again; `;` is wt's command separator and is escaped.
    // [impl->dsn~local-terminal-focus~7]
    static List<String> openCommand(String tabTitle, String folder) {
        return List.of("wt", "-w", "0", "new-tab", "--title", tabTitle.replace(";", "\\;"),
                "--suppressApplicationTitle", "-d", folder.replace(";", "\\;"));
    }

    /// As [#focus(String)], but a tab that does not exist — after a reboot —
    /// is opened in `folder` when one is given.
    // [impl->dsn~local-terminal-focus~7]
    public FocusResult focus(String tabTitle, @Nullable String folder) {
        LocalCommandRunner.LocalResult result = runner.run(command(tabTitle));
        if (result.exitCode() == 0) {
            return new FocusResult(true, "focused tab \"%s\"".formatted(tabTitle));
        }
        if (result.exitCode() == 2 && folder != null) {
            LocalCommandRunner.LocalResult opened = runner.run(openCommand(tabTitle, folder));
            return opened.ok()
                    ? new FocusResult(true, "opened tab \"%s\" in %s".formatted(tabTitle, folder))
                    : new FocusResult(false, "cannot open tab \"%s\": %s".formatted(tabTitle,
                            opened.stderr().isBlank() ? "wt exit " + opened.exitCode() : opened.stderr().strip()));
        }
        if (result.exitCode() == 2) {
            return new FocusResult(false, "no Windows Terminal tab titled \"%s\"".formatted(tabTitle));
        }
        // The tab's window sits on another desktop and could not be moved
        // here; it was left alone rather than switching desktops.
        if (result.exitCode() == 3) {
            org.tinylog.Logger.info("Windows Terminal focus: {}", result.stdout().strip());
            return new FocusResult(false, "tab \"%s\" is on another desktop".formatted(tabTitle));
        }
        String detail = result.stderr().isBlank()
                ? "powershell exit " + result.exitCode()
                : result.stderr().strip();
        return new FocusResult(false, detail);
    }
}
