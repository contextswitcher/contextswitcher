package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Brings an application's window to the **virtual desktop in view** instead
/// of letting Windows jump to the desktop the window happens to sit on.
///
/// Single-window apps (OneNote) and window-reusing ones (File Explorer, VS
/// Code) activate an existing window when asked to open something; when that
/// window lives on another desktop, Windows switches there — the user lands on
/// the wrong project's desktop. The fix is to move the window first:
/// `IVirtualDesktopManagerInternal::MoveViewToDesktop(view, GetCurrentDesktop())`.
/// The *documented* `IVirtualDesktopManager::MoveWindowToDesktop` only accepts
/// windows of the calling process, so the undocumented interface the shell
/// itself uses is the only route for another app's window.
///
/// [#TYPES] is the shared C#: `CsMv.Here(hwnd)` moves one window here (no-op
/// for a window already here or pinned to all desktops), `CsMv.BringAppHere`
/// picks an app's top-level window by process and class — preferring one
/// already on this desktop, else the topmost elsewhere — moves it here and
/// brings it to the front. Other scripts ([LocalFolderFocus]) embed [#TYPES]
/// and call `Here` themselves.
///
/// The internal interface is pinned to the Windows 11 24H2 layout (the IID
/// [WindowsVirtualDesktopFocus] declares too); another build fails
/// `QueryService` with `E_NOINTERFACE`, the move reports `failed:` and the
/// caller carries on as before — the window is merely not moved.
// [impl->dsn~window-move-here~1]
public class WindowsWindowMove {

    /// Outcome of [#bringHere]: `outcome` is `here`, `moved`, `none` (no such
    /// window — the app is not running) or `failed`; `unavailable` when the
    /// mechanism itself is missing (no `powershell`, script error).
    public record MoveResult(String outcome, String detail) {

        /// A short phrase for a chip detail when the window was actually
        /// moved, null otherwise — the common case needs no mention.
        public @Nullable String note() {
            return "moved".equals(outcome) ? detail : null;
        }
    }

    /// The C# types, declared once for every script that moves windows.
    /// Slot positions matter (a COM method binds by position): `GetViewForHwnd`
    /// is slot 3 of `IApplicationViewCollection`, `MoveViewToDesktop` slot 1
    /// and `GetCurrentDesktop` slot 3 of the internal manager.
    static final String TYPES = """
            using System;
            using System.Text;
            using System.Runtime.InteropServices;
            [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("6D5140C1-7436-11CE-8034-00AA006009FA")]
            public interface ICsMvShell {
              [return: MarshalAs(UnmanagedType.IUnknown)] object QueryService(ref Guid service, ref Guid riid);
            }
            [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("372E1D3B-38D3-42E4-A15B-8AB2B178F513")]
            public interface ICsMvView {
            }
            [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("1841C6D7-4F9D-42C0-AF41-8747538F10E5")]
            public interface ICsMvViews {
              void GetViews();
              void GetViewsByZOrder();
              void GetViewsByAppUserModelId();
              ICsMvView GetViewForHwnd(IntPtr hwnd);
            }
            [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("53F5CA0B-158F-4124-900C-057158060B27")]
            public interface ICsMvInternal {
              void GetCount();
              void MoveViewToDesktop(ICsMvView view, IntPtr desktop);
              void CanViewMoveDesktops();
              IntPtr GetCurrentDesktop();
            }
            [ComImport, Guid("aa509086-5ca9-4c25-8f95-589d3c07b48a")]
            public class CsMvVdmClass { }
            [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("a5cd92ff-29be-454c-8d04-d82879fb3f1b")]
            public interface ICsMvVdm {
              [PreserveSig] int IsWindowOnCurrentVirtualDesktop(IntPtr window, [MarshalAs(UnmanagedType.Bool)] out bool onCurrent);
            }
            public static class CsMv {
              public delegate bool EnumProc(IntPtr h, IntPtr l);
              [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc cb, IntPtr l);
              [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
              [DllImport("user32.dll")] static extern int GetWindowTextLength(IntPtr h);
              [DllImport("user32.dll")] static extern int GetClassName(IntPtr h, StringBuilder s, int n);
              [DllImport("user32.dll")] static extern IntPtr GetWindow(IntPtr h, uint cmd);
              [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
              [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr h);
              [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr h, int c);
              [DllImport("user32.dll")] static extern bool IsIconic(IntPtr h);
              [DllImport("user32.dll")] static extern void keybd_event(byte k, byte s, uint f, IntPtr e);
              static readonly Guid ImmersiveShell = new Guid("C2F03A33-21F5-47FA-B4BB-156362A2F239");
              static readonly Guid VdmInternal = new Guid("C5E0CDCA-7B6E-41B2-9FC4-D93975CC467B");

              public static bool IsHere(IntPtr hwnd) {
                bool on;
                var vdm = (ICsMvVdm)new CsMvVdmClass();
                return vdm.IsWindowOnCurrentVirtualDesktop(hwnd, out on) == 0 && on;
              }

              // "here" (already on this desktop, or pinned to all), "moved",
              // or "failed: <reason>".
              public static string Here(IntPtr hwnd) {
                try {
                  if (IsHere(hwnd)) { return "here"; }
                  var shell = (ICsMvShell)Activator.CreateInstance(Type.GetTypeFromCLSID(ImmersiveShell));
                  Guid service = typeof(ICsMvViews).GUID;
                  Guid iid = typeof(ICsMvViews).GUID;
                  var views = (ICsMvViews)shell.QueryService(ref service, ref iid);
                  ICsMvView view = views.GetViewForHwnd(hwnd);
                  service = VdmInternal;
                  iid = typeof(ICsMvInternal).GUID;
                  var manager = (ICsMvInternal)shell.QueryService(ref service, ref iid);
                  IntPtr current = manager.GetCurrentDesktop();
                  try { manager.MoveViewToDesktop(view, current); }
                  finally { Marshal.Release(current); }
                  return "moved";
                } catch (Exception e) {
                  return "failed: " + e.Message;
                }
              }

              public static void Front(IntPtr hwnd) {
                if (IsIconic(hwnd)) { ShowWindow(hwnd, 9); }
                keybd_event(0x12, 0, 0, IntPtr.Zero);
                keybd_event(0x12, 0, 2, IntPtr.Zero);
                SetForegroundWindow(hwnd);
              }

              static string ProcessName(IntPtr h) {
                uint pid;
                GetWindowThreadProcessId(h, out pid);
                try { return System.Diagnostics.Process.GetProcessById((int)pid).ProcessName; }
                catch { return ""; }
              }

              // The app's visible, titled, unowned top-level windows of the
              // given class (empty: any class), in z-order (topmost first).
              public static System.Collections.Generic.List<IntPtr> Windows(string process, string cls) {
                var found = new System.Collections.Generic.List<IntPtr>();
                EnumWindows((h, l) => {
                  if (!IsWindowVisible(h) || GetWindowTextLength(h) == 0) { return true; }
                  if (GetWindow(h, 4) != IntPtr.Zero) { return true; }
                  if (cls.Length > 0) {
                    var sb = new StringBuilder(256);
                    GetClassName(h, sb, 256);
                    if (sb.ToString() != cls) { return true; }
                  }
                  if (!string.Equals(ProcessName(h), process, StringComparison.OrdinalIgnoreCase)) { return true; }
                  found.Add(h);
                  return true;
                }, IntPtr.Zero);
                return found;
              }

              // "none", "here", "moved" or "failed: <reason>"; the chosen window
              // ends up in front unless the move failed.
              public static string BringAppHere(string process, string cls) {
                var windows = Windows(process, cls);
                if (windows.Count == 0) { return "none"; }
                foreach (IntPtr h in windows) {
                  if (IsHere(h)) { Front(h); return "here"; }
                }
                IntPtr top = windows[0];
                string outcome = Here(top);
                if (!outcome.StartsWith("failed")) { Front(top); }
                return outcome;
              }
            }
            """;

    private static final String APP_SCRIPT = """
            $ErrorActionPreference = 'Stop'
            Add-Type -TypeDefinition @"
            __TYPES__
            "@
            Write-Output ([CsMv]::BringAppHere('__PROCESS__', '__CLASS__'))
            exit 0
            """;

    private final LocalCommandRunner runner;

    public WindowsWindowMove(LocalCommandRunner runner) {
        this.runner = runner;
    }

    /// Doubles `'` so a value is a safe PowerShell single-quoted literal.
    private static String escape(String value) {
        return value.replace("'", "''");
    }

    /// The script bringing `process`'s window of class `windowClass` (blank:
    /// any class) to the current desktop.
    static String appScript(String process, String windowClass) {
        return APP_SCRIPT.replace("__TYPES__", TYPES.strip())
                .replace("__PROCESS__", escape(process))
                .replace("__CLASS__", escape(windowClass));
    }

    /// The local argv: the script as a base64 `-EncodedCommand` (UTF-16LE).
    static List<String> appCommand(String process, String windowClass) {
        String encoded = Base64.getEncoder().encodeToString(
                appScript(process, windowClass).getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    /// Moves the app's window to the desktop in view (when it sits on
    /// another one) and brings it to the front, so whatever the app opens
    /// next appears here. `label` names the app in the detail.
    public MoveResult bringHere(String label, String process, String windowClass) {
        LocalCommandRunner.LocalResult result = runner.run(appCommand(process, windowClass));
        if (result.exitCode() != 0) {
            Logger.debug("Window move for {} unavailable ({}): {}", label, result.exitCode(),
                    result.stderr().strip());
            return new MoveResult("unavailable", "cannot move " + label + " here");
        }
        return parse(label, result.stdout());
    }

    /// Reads the script's one-line outcome.
    static MoveResult parse(String label, String stdout) {
        String line = stdout.strip();
        return switch (line) {
            case "none" -> new MoveResult("none", label + " not running");
            case "here" -> new MoveResult("here", label + " already on this desktop");
            case "moved" -> new MoveResult("moved", "moved " + label + " here");
            default -> {
                Logger.debug("Window move for {}: {}", label, line);
                yield new MoveResult("failed", "cannot move " + label + " here");
            }
        };
    }
}
