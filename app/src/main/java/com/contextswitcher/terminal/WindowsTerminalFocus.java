package com.contextswitcher.terminal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import com.contextswitcher.local.LocalCommandRunner;

/// Focuses a Windows Terminal tab by its (fixed) title using UI Automation,
/// driven through `powershell`. The script finds the `CASCADIA_HOSTING_WINDOW_CLASS`
/// window whose `TabItem` name equals the title, selects that tab
/// (`SelectionItemPattern.Select`) and brings the window to the foreground
/// (restoring it if minimised; an Alt tap works around the foreground lock).
///
/// The whole script is passed as a base64 `-EncodedCommand`, so there is no
/// shell quoting to get wrong, no temp file, and no execution-policy prompt;
/// the only escaping is doubling `'` for the single-quoted title literal.
// [impl->dsn~local-terminal-focus~5]
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
              [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
              [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
              [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
              [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
              [DllImport("user32.dll")] public static extern void keybd_event(byte k, byte s, uint f, IntPtr e);
              [DllImport("user32.dll")] public static extern void SwitchToThisWindow(IntPtr h, bool alt);
            }
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
              if ([CsFg]::IsIconic($hwnd)) { [CsFg]::ShowWindow($hwnd, 9) | Out-Null }
              [CsFg]::keybd_event(0x12, 0, 0, [IntPtr]::Zero)
              [CsFg]::keybd_event(0x12, 0, 2, [IntPtr]::Zero)
              [CsFg]::SetForegroundWindow($hwnd) | Out-Null
              [CsFg]::BringWindowToTop($hwnd) | Out-Null
              [CsFg]::SwitchToThisWindow($hwnd, $true)
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
            # so no tab selection is needed - just the jump
            # (SwitchToThisWindow switches to the window's desktop).
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
              if ([CsFg]::IsIconic($h)) { [CsFg]::ShowWindow($h, 9) | Out-Null }
              [CsFg]::keybd_event(0x12, 0, 0, [IntPtr]::Zero)
              [CsFg]::keybd_event(0x12, 0, 2, [IntPtr]::Zero)
              [CsFg]::SetForegroundWindow($h) | Out-Null
              [CsFg]::SwitchToThisWindow($h, $true)
              Write-Output ("focused: " + $title)
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
        return SCRIPT_TEMPLATE.replace("__TITLE__", escape(tabTitle));
    }

    /// The local argv: the script as a base64 `-EncodedCommand` (UTF-16LE).
    static List<String> command(String tabTitle) {
        String encoded = Base64.getEncoder().encodeToString(
                script(tabTitle).getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    public FocusResult focus(String tabTitle) {
        LocalCommandRunner.LocalResult result = runner.run(command(tabTitle));
        if (result.exitCode() == 0) {
            return new FocusResult(true, "focused tab \"%s\"".formatted(tabTitle));
        }
        if (result.exitCode() == 2) {
            return new FocusResult(false, "no Windows Terminal tab titled \"%s\"".formatted(tabTitle));
        }
        String detail = result.stderr().isBlank()
                ? "powershell exit " + result.exitCode()
                : result.stderr().strip();
        return new FocusResult(false, detail);
    }
}
