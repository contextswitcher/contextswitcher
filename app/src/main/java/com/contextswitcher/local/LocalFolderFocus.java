package com.contextswitcher.local;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import org.tinylog.Logger;



/// Opens a folder in the platform's file manager, and on Windows focuses an
/// already-open window showing it rather than opening a second one.
///
/// Off Windows there is no `powershell` and no window enumeration: the folder
/// is handed to `java.awt.Desktop.open`, which is the file manager the desktop
/// itself would use (`xdg-open` under GNOME/KDE) — no focus semantics, the
/// file manager decides whether to reuse a window it already has for the
/// folder.
///
/// The Windows path focuses an already-open File Explorer window showing the
/// folder, or opens a new one, driven through `powershell`. Open windows are enumerated
/// via the `Shell.Application` COM object (`.Windows()`); a window whose
/// `Document.Folder.Self.Path` matches the target (case-insensitive, trailing
/// separators ignored) is brought to the foreground by its `HWND` (restoring it
/// if minimised; an Alt tap works around the foreground lock, `SwitchToThisWindow`
/// jumps across virtual desktops). With none open, a fresh Explorer window is
/// launched at the folder.
///
/// The script is passed as a base64 `-EncodedCommand`, so there is no shell
/// quoting to get wrong; the only escaping is doubling `'` for the folder
/// single-quoted literal.
// [impl->dsn~explorer-folder-focus~3]
public class LocalFolderFocus {

    /// Outcome of a focus/open attempt.
    public record FocusResult(boolean ok, String detail) {
    }

    private static final String SCRIPT_TEMPLATE = """
            $ErrorActionPreference = 'Stop'
            Add-Type @"
            using System;
            using System.Runtime.InteropServices;
            public class CsFg {
              [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
              [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
              [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
              [DllImport("user32.dll")] public static extern void keybd_event(byte k, byte s, uint f, IntPtr e);
              [DllImport("user32.dll")] public static extern void SwitchToThisWindow(IntPtr h, bool alt);
            }
            "@
            $target = '__PATH__'
            $norm = $target.TrimEnd('\\', '/')
            $shell = New-Object -ComObject Shell.Application
            foreach ($w in $shell.Windows()) {
              $path = $null
              try { $path = $w.Document.Folder.Self.Path } catch { continue }
              if ($path -and ($path.TrimEnd('\\', '/') -ieq $norm)) {
                $h = [IntPtr]$w.HWND
                if ([CsFg]::IsIconic($h)) { [CsFg]::ShowWindow($h, 9) | Out-Null }
                [CsFg]::keybd_event(0x12, 0, 0, [IntPtr]::Zero)
                [CsFg]::keybd_event(0x12, 0, 2, [IntPtr]::Zero)
                [CsFg]::SetForegroundWindow($h) | Out-Null
                [CsFg]::SwitchToThisWindow($h, $true)
                Write-Output ("focused: " + $target)
                exit 0
              }
            }
            if (Test-Path -LiteralPath $target) {
              Start-Process explorer.exe -ArgumentList $target
              Write-Output ("opened: " + $target)
              exit 0
            }
            Write-Output ("missing: " + $target)
            exit 2
            """;

    private final LocalCommandRunner runner;
    private final boolean windows;

    public LocalFolderFocus(LocalCommandRunner runner) {
        this(runner, LocalCommandRunner.onWindows());
    }

    private LocalFolderFocus(LocalCommandRunner runner, boolean windows) {
        this.runner = runner;
        this.windows = windows;
    }

    /// Pinned to the PowerShell path whatever the OS — for tests, which
    /// drive it through a stubbed runner on any machine.
    public static LocalFolderFocus forWindows(LocalCommandRunner runner) {
        return new LocalFolderFocus(runner, true);
    }

    /// Doubles `'` so the folder is a safe PowerShell single-quoted literal.
    static String escape(String folder) {
        return folder.replace("'", "''");
    }

    /// The PowerShell script that focuses (or opens) the given folder.
    static String script(String folder) {
        return SCRIPT_TEMPLATE.replace("__PATH__", escape(folder));
    }

    /// The local argv: the script as a base64 `-EncodedCommand` (UTF-16LE).
    static List<String> command(String folder) {
        String encoded = Base64.getEncoder().encodeToString(
                script(folder).getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    /// Focuses the folder's Explorer window, opening one when none is showing
    /// it; off Windows, opens it in the desktop's file manager.
    public FocusResult focus(String folder) {
        if (!windows) {
            return open(folder);
        }
        LocalCommandRunner.LocalResult result = runner.run(command(folder));
        if (result.exitCode() == 0) {
            String out = result.stdout().strip();
            return new FocusResult(true, out.isBlank() ? "opened \"%s\"".formatted(folder) : out);
        }
        if (result.exitCode() == 2) {
            return new FocusResult(false, "folder not found: \"%s\"".formatted(folder));
        }
        String detail = result.stderr().isBlank()
                ? "powershell exit " + result.exitCode()
                : result.stderr().strip();
        return new FocusResult(false, detail);
    }

    /// The non-Windows path: `Desktop.open` hands the folder to the desktop's
    /// own file manager. The existence check is our own, so a typo in a
    /// `folders:` entry reads the same as on Windows instead of an
    /// implementation-specific `IOException`.
    private FocusResult open(String folder) {
        Path path;
        try {
            path = Path.of(folder);
        } catch (InvalidPathException e) {
            return new FocusResult(false, "not a path: \"%s\"".formatted(folder));
        }
        if (!Files.isDirectory(path)) {
            return new FocusResult(false, "folder not found: \"%s\"".formatted(folder));
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            return new FocusResult(false, "no file manager on this desktop");
        }
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            Logger.warn("Cannot open {}: {}", folder, e.getMessage());
            return new FocusResult(false, "cannot open \"%s\": %s".formatted(folder, e.getMessage()));
        }
        return new FocusResult(true, "opened \"%s\"".formatted(folder));
    }
}
