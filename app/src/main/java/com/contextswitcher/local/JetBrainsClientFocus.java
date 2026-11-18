package com.contextswitcher.local;

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
/// process route sees them regardless. Such a window is moved to the desktop
/// in view before it is raised (`CsMv.Focus`, [WindowsWindowMove]); when that
/// fails it is left alone — raising it would switch desktops — and still
/// counts as found, so no second client is launched.
///
/// The script is passed as a base64 `-EncodedCommand`; the only escaping is
/// doubling `'` for the single-quoted project literal. On systems without
/// `powershell` the runner fails and the caller falls back to the URL.
// [impl->dsn~gateway-url-action~12]
public class JetBrainsClientFocus {

    private static final String SCRIPT_TEMPLATE = """
            $ErrorActionPreference = 'Stop'
            Add-Type -TypeDefinition @"
            __MOVE_TYPES__
            "@
            $project = '__PROJECT__'
            $procs = Get-Process jetbrains_client64 -ErrorAction SilentlyContinue
            foreach ($p in $procs) {
              if ($p.MainWindowHandle -ne 0 -and $p.MainWindowTitle -like ('*' + $project + '*')) {
                $outcome = [CsMv]::Focus($p.MainWindowHandle)
                Write-Output ($outcome + ": " + $p.MainWindowTitle)
                if ($outcome -like 'elsewhere*') { exit 3 }
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
        return SCRIPT_TEMPLATE.replace("__MOVE_TYPES__", WindowsWindowMove.TYPES.strip())
                .replace("__PROJECT__", escape(projectName));
    }

    /// The local argv: the script as a base64 `-EncodedCommand` (UTF-16LE).
    static List<String> command(String projectName) {
        return PowerShellCommand.of(script(projectName));
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
        // Open on another desktop and not movable here: left alone, but it
        // exists — relaunching the URL would only prompt or open a second one.
        if (result.exitCode() == 3) {
            Logger.info("JetBrains Client focus: {}", result.stdout().strip());
            return true;
        }
        if (result.exitCode() != 2) {
            Logger.debug("JetBrains Client focus unavailable ({}): {}",
                    result.exitCode(), result.stderr().strip());
        }
        return false;
    }
}
