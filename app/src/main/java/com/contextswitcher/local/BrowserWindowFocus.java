package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

import com.contextswitcher.config.Browser;

/// Brings a specific browser **window** to the foreground, via `powershell`,
/// after the extension has focused the right tab: the browser's own
/// `windows.update({focused:true})` cannot raise the window while
/// ContextSwitcher is the foreground app (Windows' foreground-stealing lock),
/// so the tab is activated but the window stays behind the app.
///
/// The right window is found by **title**, not by process: all of a browser's
/// windows share one process (so `Process.MainWindowHandle` only ever exposes
/// one arbitrary window — useless when many are open), so `EnumWindows` walks
/// the top-level windows and matches the window of the configured browser
/// (`Browser#windowClass` *and* `Browser#processName` — `Chrome_WidgetWin_1`
/// alone is every Electron app on the machine) whose caption contains the
/// focused tab's title (which the extension reports; a window's caption is its
/// active tab's title). The `keybd_event(VK_MENU …)` synthetic ALT tap clears
/// the foreground lock so `SetForegroundWindow` then succeeds — the same
/// mechanism [JetBrainsClientFocus] relies on.
///
/// Best-effort: no `powershell` (non-Windows, or stripped down) makes the
/// runner fail and the call a silent no-op; a title matching two windows
/// raises the first found.
// [impl->dsn~pr-state-indicator~3]
// [impl->dsn~browser-choice~2]
public class BrowserWindowFocus {

    /// The p/invoke surface both scripts share: window enumeration, the
    /// foreground dance, and the one *documented* virtual-desktop API
    /// (`IVirtualDesktopManager::IsWindowOnCurrentVirtualDesktop`) — unlike the
    /// desktop *switching* of [WindowsVirtualDesktopFocus], asking which desktop
    /// a window is on needs no undocumented COM. `CsVdmHelper` does the
    /// `(ICsVdm)new CsVdmClass()` cast in compiled C#, not PowerShell script
    /// text: on Windows PowerShell 5.1, casting a `[ComImport]` class instance
    /// to its interface via `[ICsVdm](New-Object CsVdmClass)` throws
    /// `InvalidCastException` even though the identical cast succeeds in
    /// compiled code — a PowerShell type-conversion quirk, not a COM one.
    private static final String PREAMBLE = """
            $ErrorActionPreference = 'Stop'
            Add-Type @"
            using System;
            using System.Text;
            using System.Runtime.InteropServices;
            public class CsFf {
              public delegate bool EnumProc(IntPtr h, IntPtr l);
              [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
              [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
              [DllImport("user32.dll")] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
              [DllImport("user32.dll")] public static extern int GetClassName(IntPtr h, StringBuilder s, int n);
              [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
              [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
              [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
              [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
              [DllImport("user32.dll")] public static extern void keybd_event(byte k, byte s, uint f, IntPtr e);
              [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
              public static string ProcessName(IntPtr h) {
                uint pid;
                GetWindowThreadProcessId(h, out pid);
                try { return System.Diagnostics.Process.GetProcessById((int)pid).ProcessName; }
                catch { return ""; }
              }
            }
            [ComImport, Guid("aa509086-5ca9-4c25-8f95-589d3c07b48a")]
            public class CsVdmClass { }
            [ComImport, Guid("a5cd92ff-29be-454c-8d04-d82879fb3f1b"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            public interface ICsVdm {
              int IsWindowOnCurrentVirtualDesktop(IntPtr window, [MarshalAs(UnmanagedType.Bool)] out bool onCurrent);
            }
            public class CsVdmHelper {
              public static bool IsOnCurrentDesktop(IntPtr h) {
                var vdm = (ICsVdm)new CsVdmClass();
                bool onCurrent;
                vdm.IsWindowOnCurrentVirtualDesktop(h, out onCurrent);
                return onCurrent;
              }
            }
            "@
            function Test-CsBrowserWindow($h) {
              if (-not [CsFf]::IsWindowVisible($h)) { return $false }
              $cls = New-Object System.Text.StringBuilder 256
              [CsFf]::GetClassName($h, $cls, 256) | Out-Null
              if ($cls.ToString() -ne '__CLASS__') { return $false }
              return ([CsFf]::ProcessName($h) -ieq '__PROCESS__')
            }
            """;

    /// Raises `$found` (restoring it when minimized) past the foreground lock
    /// and prints its caption, so the caller can name the window elsewhere.
    private static final String RAISE = """
            if ($found -ne [IntPtr]::Zero) {
              if ([CsFf]::IsIconic($found)) { [CsFf]::ShowWindow($found, 9) | Out-Null }
              [CsFf]::keybd_event(0x12, 0, 0, [IntPtr]::Zero)
              [CsFf]::keybd_event(0x12, 0, 2, [IntPtr]::Zero)
              [CsFf]::SetForegroundWindow($found) | Out-Null
              [CsFf]::BringWindowToTop($found) | Out-Null
              $caption = New-Object System.Text.StringBuilder 1024
              [CsFf]::GetWindowText($found, $caption, 1024) | Out-Null
              Write-Output $caption.ToString()
              exit 0
            }
            Write-Output 'not found'
            exit 2
            """;

    private static final String SCRIPT_TEMPLATE = PREAMBLE + """
            $target = '__TITLE__'
            $found = [IntPtr]::Zero
            $cb = {
              param($h, $l)
              if (-not (Test-CsBrowserWindow $h)) { return $true }
              $sb = New-Object System.Text.StringBuilder 1024
              [CsFf]::GetWindowText($h, $sb, 1024) | Out-Null
              if ($sb.ToString().Contains($target)) {
                $script:found = $h
                return $false
              }
              return $true
            }
            [CsFf]::EnumWindows($cb, [IntPtr]::Zero) | Out-Null
            """ + RAISE;

    /// Raises *any* window of the browser sitting on the virtual desktop currently in
    /// view — the caller has just switched to the category's desktop, so the
    /// tab it opens next lands in a window the user can see. `EnumWindows`
    /// walks in Z-order, so the first hit is the topmost one there — the
    /// window the user focused last on that desktop.
    private static final String CURRENT_DESKTOP_SCRIPT = PREAMBLE + """
            $found = [IntPtr]::Zero
            $cb = {
              param($h, $l)
              if (-not (Test-CsBrowserWindow $h)) { return $true }
              $sb = New-Object System.Text.StringBuilder 1024
              [CsFf]::GetWindowText($h, $sb, 1024) | Out-Null
              if ($sb.Length -eq 0) { return $true }
              $onCurrent = $false
              try { $onCurrent = [CsVdmHelper]::IsOnCurrentDesktop($h) } catch { return $true }
              if (-not $onCurrent) { return $true }
              $script:found = $h
              return $false
            }
            [CsFf]::EnumWindows($cb, [IntPtr]::Zero) | Out-Null
            """ + RAISE;

    /// Opens a **new** browser window on the desktop in view. The new-window
    /// flag (`Browser#newWindowFlag`) is what makes it a window of its own: a
    /// plain `firefox <url>` is remoted into the running instance and would
    /// open the tab in whatever window the browser last used — on the desktop
    /// we just left. `Start-Process` (ShellExecute, so the browser need not be
    /// on `PATH`) returns at once instead of blocking the runner for the
    /// browser's lifetime.
    private static final String LAUNCH_TEMPLATE =
            "Start-Process '__EXE__' -ArgumentList '__NEWWINDOW__', '__URL__'";

    private final LocalCommandRunner runner;
    private final Browser browser;

    public BrowserWindowFocus(LocalCommandRunner runner, Browser browser) {
        this.runner = runner;
        this.browser = browser;
    }

    /// The class/process pair of the configured browser patched into a script
    /// template — what turns the shared PowerShell into *this* browser's.
    private String forBrowser(String template) {
        return template.replace("__CLASS__", browser.windowClass())
                .replace("__PROCESS__", browser.processName());
    }

    /// Doubles `'` so the title is a safe PowerShell single-quoted literal.
    static String escape(String value) {
        return value.replace("'", "''");
    }

    /// The PowerShell script raising the browser window whose caption contains
    /// `windowTitle`.
    String script(String windowTitle) {
        return forBrowser(SCRIPT_TEMPLATE).replace("__TITLE__", escape(windowTitle));
    }

    /// The local argv: the script as a base64 `-EncodedCommand` (UTF-16LE).
    List<String> command(String windowTitle) {
        return encoded(script(windowTitle));
    }

    private static List<String> encoded(String script) {
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    /// The argv raising a browser window on the virtual desktop in view.
    // [impl->dsn~pr-open-on-category-desktop~3]
    List<String> currentDesktopCommand() {
        return encoded(forBrowser(CURRENT_DESKTOP_SCRIPT));
    }

    /// The argv opening `url` in a new browser window on the desktop in view.
    // [impl->dsn~pr-open-on-category-desktop~3]
    List<String> launchCommand(String url) {
        return encoded(LAUNCH_TEMPLATE.replace("__EXE__", browser.processName())
                .replace("__NEWWINDOW__", browser.newWindowFlag())
                .replace("__URL__", escape(url)));
    }

    /// The caption of the browser window on the virtual desktop currently in
    /// view that was raised — the extension opens the tab into that window by
    /// it; null when that desktop has none (so the caller opens one) or the
    /// mechanism is unavailable.
    // [impl->dsn~pr-open-on-category-desktop~3]
    public @Nullable String focusOnCurrentDesktop() {
        LocalCommandRunner.LocalResult result = runner.run(currentDesktopCommand());
        if (!report(result)) {
            return null;
        }
        String caption = result.stdout().strip();
        return caption.isEmpty() ? null : caption;
    }

    /// Opens `url` in a new browser window, which Windows places on the desktop
    /// in view. True when the browser was started.
    // [impl->dsn~pr-open-on-category-desktop~3]
    public boolean launch(String url) {
        return report(runner.run(launchCommand(url)));
    }

    /// True when a browser window whose caption contains `windowTitle` was
    /// raised; false when none matched or the mechanism is unavailable (logged)
    /// — the tab focus already happened, so this is only the courtesy raise.
    public boolean focus(String windowTitle) {
        return report(runner.run(command(windowTitle)));
    }

    /// True on exit 0; exit 2 is the expected "no such window" and stays quiet,
    /// anything else means the mechanism is unavailable and is logged.
    private static boolean report(LocalCommandRunner.LocalResult result) {
        if (result.exitCode() == 0) {
            return true;
        }
        if (result.exitCode() != 2) {
            Logger.debug("Browser window focus unavailable ({}): {}",
                    result.exitCode(), result.stderr().strip());
        }
        return false;
    }
}
