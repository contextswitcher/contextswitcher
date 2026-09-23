package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.tinylog.Logger;

/// Focuses an already-open local **JetBrains Client** window for a project,
/// via `powershell`, so the intellij switch action focuses an
/// already-connected project instead of relaunching the Gateway URL (which
/// triggers Gateway's "Requested Project Is Already Running" version prompt
/// and can open a second window). The window is found through its
/// **process**: `jetbrains_client64` whose `MainWindowTitle` carries the
/// project folder name — a client titles itself
/// `<IDE project name> – <file path>`, and the path carries the folder
/// name. Window enumeration (UI Automation, even `EnumWindows`) proved
/// unreliable for client windows parked on another virtual desktop; the
/// process route sees them regardless, and `SetForegroundWindow` then also
/// switches to that desktop.
///
/// The script is passed as a base64 `-EncodedCommand`; the only escaping is
/// doubling `'` for the single-quoted project literal. On systems without
/// `powershell` the runner fails and the caller falls back to the URL.
// [impl->dsn~gateway-url-action~11]
public class JetBrainsClientFocus {

    private static final String SCRIPT_TEMPLATE = """
            $ErrorActionPreference = 'Stop'
            Add-Type @"
            using System;
            using System.Runtime.InteropServices;
            public class CsFg {
              [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
              [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
              [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
              [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
              [DllImport("user32.dll")] public static extern void keybd_event(byte k, byte s, uint f, IntPtr e);
            }
            "@
            $project = '__PROJECT__'
            $procs = Get-Process jetbrains_client64 -ErrorAction SilentlyContinue
            foreach ($p in $procs) {
              if ($p.MainWindowHandle -ne 0 -and $p.MainWindowTitle -like ('*' + $project + '*')) {
                $hwnd = $p.MainWindowHandle
                if ([CsFg]::IsIconic($hwnd)) { [CsFg]::ShowWindow($hwnd, 9) | Out-Null }
                [CsFg]::keybd_event(0x12, 0, 0, [IntPtr]::Zero)
                [CsFg]::keybd_event(0x12, 0, 2, [IntPtr]::Zero)
                [CsFg]::SetForegroundWindow($hwnd) | Out-Null
                [CsFg]::BringWindowToTop($hwnd) | Out-Null
                Write-Output ("focused: " + $p.MainWindowTitle)
                exit 0
              }
            }
            Write-Output ("not found: " + $project)
            exit 2
            """;

    private final LocalCommandRunner runner;

    public JetBrainsClientFocus(LocalCommandRunner runner) {
        this.runner = runner;
    }

    /// The project folder name a client window's title carries: the last
    /// segment of the (forward-slash) project path.
    static String projectName(String projectPath) {
        String trimmed = projectPath.endsWith("/")
                ? projectPath.substring(0, projectPath.length() - 1)
                : projectPath;
        int slash = trimmed.lastIndexOf('/');
        return slash < 0 ? trimmed : trimmed.substring(slash + 1);
    }

    /// Doubles `'` so the name is a safe PowerShell single-quoted literal.
    static String escape(String value) {
        return value.replace("'", "''");
    }

    /// The PowerShell script focusing the client window of the project.
    static String script(String projectName) {
        return SCRIPT_TEMPLATE.replace("__PROJECT__", escape(projectName));
    }

    /// The local argv: the script as a base64 `-EncodedCommand` (UTF-16LE).
    static List<String> command(String projectName) {
        String encoded = Base64.getEncoder().encodeToString(
                script(projectName).getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    /// True when an open client window for the project was focused; false
    /// when none exists or the mechanism is unavailable (logged) — the
    /// caller then launches the Gateway URL instead.
    public boolean focus(String projectPath) {
        String name = projectName(projectPath);
        LocalCommandRunner.LocalResult result = runner.run(command(name));
        if (result.exitCode() == 0) {
            return true;
        }
        if (result.exitCode() != 2) {
            Logger.debug("JetBrains Client focus unavailable ({}): {}",
                    result.exitCode(), result.stderr().strip());
        }
        return false;
    }
}
