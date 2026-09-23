package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/// Pins the app's own main window to **all virtual desktops** — the same state
/// Task View's "Show this window on all desktops" toggles — driven through
/// `powershell`. Windows keeps that pin per window handle and drops it when the
/// process exits, so an app that should stay pinned has to re-pin itself at
/// every start; there is no supported API for it.
///
/// The script resolves the window from the JVM's PID (`Get-Process`'s
/// `MainWindowHandle`, retried briefly in case the stage is still appearing),
/// then walks the immersive shell's `IServiceProvider` to
/// `IApplicationViewCollection::GetViewForHwnd` and
/// `IVirtualDesktopPinnedApps::PinView` — the call Task View itself makes.
/// Unlike the desktop-*switch* interface ([WindowsVirtualDesktopFocus]), whose
/// IID moves between Windows builds, these three IIDs have been stable since
/// Windows 10 1607, so no per-build fallback is needed. An already-pinned view
/// (`IsViewPinned`) is left alone rather than re-pinned.
///
/// The script is passed as a base64 `-EncodedCommand`; the only interpolation
/// is the numeric PID. Windows-only and undocumented-COM-backed by nature;
/// best-effort — no `powershell` or a moved interface fails the call with a
/// logged detail rather than throwing.
// [impl->dsn~window-desktop-pin~2]
public class WindowsDesktopPin {

    /// Outcome of a pin attempt; `detail` is the story for the log.
    public record PinResult(boolean ok, String detail) {
    }

    /// `$pid` is PowerShell's own automatic variable (the powershell process),
    /// so the JVM's PID lives in `$targetPid`. Only the slots up to the ones we
    /// call carry real signatures — a COM vtable binds methods by *position*,
    /// so the rest are position-holders.
    private static final String SCRIPT_TEMPLATE = """
            $ErrorActionPreference = 'Stop'
            Add-Type -TypeDefinition @"
            using System;
            using System.Runtime.InteropServices;
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
            public class CsPin {
              static readonly Guid ImmersiveShell = new Guid("C2F03A33-21F5-47FA-B4BB-156362A2F239");
              static readonly Guid PinnedApps = new Guid("B5A399E7-1C87-46B8-88E9-FC5747B171BD");
              public static string Pin(IntPtr hwnd) {
                var shell = (ICsShell)Activator.CreateInstance(Type.GetTypeFromCLSID(ImmersiveShell));
                Guid service = typeof(ICsAppViewCollection).GUID;
                Guid iid = typeof(ICsAppViewCollection).GUID;
                var views = (ICsAppViewCollection)shell.QueryService(ref service, ref iid);
                ICsAppView view = views.GetViewForHwnd(hwnd);
                service = PinnedApps;
                iid = typeof(ICsPinnedApps).GUID;
                var pinned = (ICsPinnedApps)shell.QueryService(ref service, ref iid);
                if (pinned.IsViewPinned(view)) { return "already-pinned"; }
                pinned.PinView(view);
                return "pinned";
              }
            }
            "@
            $targetPid = __PID__
            $hwnd = [IntPtr]::Zero
            for ($i = 0; $i -lt 25; $i++) {
              $hwnd = (Get-Process -Id $targetPid).MainWindowHandle
              if ($hwnd -ne [IntPtr]::Zero) { break }
              Start-Sleep -Milliseconds 200
            }
            if ($hwnd -eq [IntPtr]::Zero) { Write-Output "no main window"; exit 2 }
            Write-Output ([CsPin]::Pin($hwnd))
            exit 0
            """;

    private final LocalCommandRunner runner;

    public WindowsDesktopPin(LocalCommandRunner runner) {
        this.runner = runner;
    }

    /// The PowerShell script pinning the main window of process `pid`.
    static String script(long pid) {
        return SCRIPT_TEMPLATE.replace("__PID__", Long.toString(pid));
    }

    /// The local argv: the script as a base64 `-EncodedCommand` (UTF-16LE).
    static List<String> command(long pid) {
        String encoded = Base64.getEncoder().encodeToString(
                script(pid).getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    /// Pins the main window of process `pid` (normally the JVM's own,
    /// `ProcessHandle.current().pid()`) to all virtual desktops. `ok` is false
    /// when the process has no main window yet (exit 2) or the mechanism is
    /// unavailable (no `powershell`, a Windows build that moved the interfaces).
    public PinResult pin(long pid) {
        LocalCommandRunner.LocalResult result = runner.run(command(pid));
        if (result.exitCode() == 0) {
            String state = result.stdout().strip().equals("already-pinned")
                    ? "already pinned" : "pinned";
            return new PinResult(true, state + " on all desktops");
        }
        if (result.exitCode() == 2) {
            return new PinResult(false, "no main window found for pid " + pid);
        }
        String detail = result.stderr().isBlank()
                ? "powershell exit " + result.exitCode()
                : result.stderr().strip();
        return new PinResult(false, detail);
    }
}
