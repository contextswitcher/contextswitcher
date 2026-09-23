package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

import com.contextswitcher.config.Browser;

/// Lists the captions of the configured browser's windows sitting on a
/// **named** virtual desktop, for the complete-control suspend: the app closes
/// exactly those windows (matched back to extension window ids by title), so
/// tabs on every other desktop survive.
///
/// The script resolves the desktop's GUID from the registry (`…\Explorer\
/// VirtualDesktops`, the same lookup [WindowsVirtualDesktopFocus] uses), then
/// walks the top-level windows of the browser's class **and** process name
/// ([Browser#processName] — `Chrome_WidgetWin_1` alone is every Electron app on
/// the machine, and a complete-control suspend closes what it captures) and
/// keeps those whose
/// `IVirtualDesktopManager::GetWindowDesktopId` — a *documented* API — returns
/// that GUID. Windows pinned to **all** desktops (`IVirtualDesktopPinnedApps::
/// IsViewPinned`, the [WindowsDesktopPin] interfaces) are excluded: "shown on
/// all desktops" means the user wants them everywhere, so a suspend must not
/// close them; if that undocumented check fails on some build, the window
/// counts as unpinned rather than dropping the whole capture.
///
/// Best-effort by nature: no `powershell` (non-Windows) or a failing script
/// returns null and the caller skips the capture; an unknown desktop name or
/// simply no browser window there is a clean empty list.
// [impl->dsn~browser-desktop-windows~2]
// [impl->dsn~browser-choice~2]
public class BrowserDesktopWindows {

    private static final String SCRIPT_TEMPLATE = """
            $ErrorActionPreference = 'Stop'
            Add-Type @"
            using System;
            using System.Text;
            using System.Runtime.InteropServices;
            public class CsFdw {
              public delegate bool EnumProc(IntPtr h, IntPtr l);
              [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
              [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
              [DllImport("user32.dll")] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
              [DllImport("user32.dll")] public static extern int GetClassName(IntPtr h, StringBuilder s, int n);
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
              int GetWindowDesktopId(IntPtr window, out Guid desktop);
            }
            [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("6D5140C1-7436-11CE-8034-00AA006009FA")]
            public interface ICsShell {
              [return: MarshalAs(UnmanagedType.IUnknown)] object QueryService(ref Guid service, ref Guid riid);
            }
            [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("372E1D3B-38D3-42E4-A15B-8AB2B178F513")]
            public interface ICsAppView {
            }
            [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("1841C6D7-4F9D-42C0-AF41-8747538F10E5")]
            public interface ICsAppViewCollection {
              void GetViews();
              void GetViewsByZOrder();
              void GetViewsByAppUserModelId();
              ICsAppView GetViewForHwnd(IntPtr hwnd);
            }
            [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("4CE81583-1E4C-4632-A621-07A53543148F")]
            public interface ICsPinnedApps {
              void IsAppIdPinned();
              void PinAppID();
              void UnpinAppID();
              [return: MarshalAs(UnmanagedType.Bool)] bool IsViewPinned(ICsAppView view);
              void PinView(ICsAppView view);
              void UnpinView(ICsAppView view);
            }
            public class CsPinCheck {
              static readonly Guid ImmersiveShell = new Guid("C2F03A33-21F5-47FA-B4BB-156362A2F239");
              static readonly Guid PinnedApps = new Guid("B5A399E7-1C87-46B8-88E9-FC5747B171BD");
              public static bool IsPinned(IntPtr hwnd) {
                try {
                  var shell = (ICsShell)Activator.CreateInstance(Type.GetTypeFromCLSID(ImmersiveShell));
                  Guid service = typeof(ICsAppViewCollection).GUID;
                  Guid iid = typeof(ICsAppViewCollection).GUID;
                  var views = (ICsAppViewCollection)shell.QueryService(ref service, ref iid);
                  ICsAppView view = views.GetViewForHwnd(hwnd);
                  service = PinnedApps;
                  iid = typeof(ICsPinnedApps).GUID;
                  var pinned = (ICsPinnedApps)shell.QueryService(ref service, ref iid);
                  return pinned.IsViewPinned(view);
                } catch { return false; }
              }
            }
            "@
            $target = '__NAME__'
            $base = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\VirtualDesktops'
            $ids = (Get-ItemProperty -Path $base -Name VirtualDesktopIDs).VirtualDesktopIDs
            $count = [int]($ids.Length / 16)
            $targetGuid = $null
            for ($i = 0; $i -lt $count; $i++) {
              $guid = [Guid]::new([byte[]]($ids[($i * 16)..($i * 16 + 15)]))
              $key = $base + '\\Desktops\\{' + $guid.ToString() + '}'
              $name = (Get-ItemProperty -Path $key -Name Name -ErrorAction SilentlyContinue).Name
              if ($name -and ($name -ieq $target)) { $targetGuid = $guid }
            }
            if ($targetGuid -eq $null) { exit 0 }
            $vdm = [ICsVdm](New-Object CsVdmClass)
            $cb = {
              param($h, $l)
              if (-not [CsFdw]::IsWindowVisible($h)) { return $true }
              $cls = New-Object System.Text.StringBuilder 256
              [CsFdw]::GetClassName($h, $cls, 256) | Out-Null
              if ($cls.ToString() -ne '__CLASS__') { return $true }
              if ([CsFdw]::ProcessName($h) -ine '__PROCESS__') { return $true }
              $sb = New-Object System.Text.StringBuilder 1024
              [CsFdw]::GetWindowText($h, $sb, 1024) | Out-Null
              if ($sb.Length -eq 0) { return $true }
              $onDesktop = [Guid]::Empty
              try { $vdm.GetWindowDesktopId($h, [ref]$onDesktop) | Out-Null } catch { return $true }
              if ($onDesktop -ne $targetGuid) { return $true }
              if ([CsPinCheck]::IsPinned($h)) { return $true }
              Write-Output $sb.ToString()
              return $true
            }
            [CsFdw]::EnumWindows($cb, [IntPtr]::Zero) | Out-Null
            exit 0
            """;

    private final LocalCommandRunner runner;
    private final Browser browser;

    public BrowserDesktopWindows(LocalCommandRunner runner, Browser browser) {
        this.runner = runner;
        this.browser = browser;
    }

    /// The PowerShell script listing the browser's window captions on the
    /// desktop named `desktopName` (one caption per line).
    String script(String desktopName) {
        return SCRIPT_TEMPLATE.replace("__NAME__", desktopName.replace("'", "''"))
                .replace("__CLASS__", browser.windowClass())
                .replace("__PROCESS__", browser.processName());
    }

    /// The local argv: the script as a base64 `-EncodedCommand` (UTF-16LE).
    List<String> command(String desktopName) {
        String encoded = Base64.getEncoder().encodeToString(
                script(desktopName).getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    /// The captions of the browser's unpinned windows on the desktop named
    /// `desktopName`: empty when that desktop has none (or no desktop of that
    /// name exists), null when the mechanism is unavailable (no `powershell` —
    /// non-Windows — or the script failed; logged) so the caller can skip the
    /// capture instead of storing a wrong empty state.
    public @Nullable List<String> captionsOn(String desktopName) {
        LocalCommandRunner.LocalResult result = runner.run(command(desktopName));
        if (result.exitCode() != 0) {
            Logger.debug("Browser desktop-window listing unavailable ({}): {}",
                    result.exitCode(), result.stderr().strip());
            return null;
        }
        return result.stdout().lines().map(String::strip)
                .filter(line -> !line.isEmpty()).toList();
    }
}
