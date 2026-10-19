package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.contextswitcher.config.EnvironmentProfile;

/// Puts the machine-level keys of an [EnvironmentProfile] into effect —
/// projection mode (`displays`), primary-display `resolution`, and taskbar
/// auto-hide — through one `powershell` script with inline C# P/Invoke, the
/// same shape as the other Windows integrations.
///
/// Every step reads the current state first and changes only what differs,
/// so a switch between two tasks of one category costs no display flicker:
/// - `displays`: `QueryDisplayConfig(QDC_DATABASE_CURRENT)` yields the active
///   topology, `SetDisplayConfig(SDC_APPLY | SDC_TOPOLOGY_*)` sets it — the
///   documented call behind Win+P (`DisplaySwitch.exe`, whose switches moved
///   between Windows 11 builds). It runs first and the script then waits for
///   the mode set to settle, since duplicating picks a common resolution of
///   its own that the `resolution` step is to override.
/// - `resolution`: `EnumDisplaySettings(ENUM_CURRENT_SETTINGS)` against
///   `ChangeDisplaySettings(…, CDS_UPDATEREGISTRY)` on the primary display —
///   persisted, so a projector hot-plug (which re-applies the stored mode)
///   does not undo it; the `default` profile is what puts it back.
/// - `taskbar`: `SHAppBarMessage(ABM_GETSTATE/ABM_SETSTATE)` on
///   `Shell_TrayWnd` — the auto-hide checkbox of the taskbar settings, which
///   ExplorerPatcher's Windows 10 taskbar honours as well.
///
/// The script prints the changes as one line; exit 3 means a requested mode
/// the displays do not offer (no projector plugged in for `clone`) — the
/// other keys are still applied, and the line names the failure first. Windows-only by nature: elsewhere the runner skips
/// `powershell` and the result says so.
// [impl->dsn~environment-profile-windows~1]
public class WindowsEnvironment {

    /// Outcome of an apply; `detail` lists what changed (or why not).
    public record ApplyResult(boolean ok, String detail) {
    }

    private static final String TYPES = """
            $ErrorActionPreference = 'Stop'
            Add-Type -TypeDefinition @"
            using System;
            using System.Runtime.InteropServices;
            public class CsEnv {
              [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
              public struct DEVMODE {
                [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string dmDeviceName;
                public short dmSpecVersion, dmDriverVersion, dmSize, dmDriverExtra;
                public int dmFields, dmPositionX, dmPositionY, dmDisplayOrientation, dmDisplayFixedOutput;
                public short dmColor, dmDuplex, dmYResolution, dmTTOption, dmCollate;
                [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string dmFormName;
                public short dmLogPixels;
                public int dmBitsPerPel, dmPelsWidth, dmPelsHeight, dmDisplayFlags, dmDisplayFrequency;
                public int dmICMMethod, dmICMIntent, dmMediaType, dmDitherType, dmReserved1, dmReserved2, dmPanningWidth, dmPanningHeight;
              }
              [StructLayout(LayoutKind.Sequential)]
              public struct APPBARDATA {
                public int cbSize; public IntPtr hWnd; public uint uCallbackMessage; public uint uEdge;
                public int left, top, right, bottom; public IntPtr lParam;
              }
              [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern bool EnumDisplaySettings(string dev, int mode, ref DEVMODE dm);
              [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int ChangeDisplaySettings(ref DEVMODE dm, int flags);
              [DllImport("user32.dll")] static extern int GetDisplayConfigBufferSizes(uint flags, out uint paths, out uint modes);
              [DllImport("user32.dll")] static extern int QueryDisplayConfig(uint flags, ref uint paths, IntPtr pathArray, ref uint modes, IntPtr modeArray, out uint topology);
              [DllImport("user32.dll")] static extern int SetDisplayConfig(uint paths, IntPtr pathArray, uint modes, IntPtr modeArray, uint flags);
              [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern IntPtr FindWindow(string cls, string title);
              [DllImport("shell32.dll")] static extern IntPtr SHAppBarMessage(uint msg, ref APPBARDATA data);

              // QDC_DATABASE_CURRENT; a path is 72 bytes, a mode 64.
              public static uint Topology() {
                uint paths, modes, topology;
                if (GetDisplayConfigBufferSizes(4, out paths, out modes) != 0) { return 0; }
                IntPtr p = Marshal.AllocHGlobal((int) Math.Max(1, paths) * 72);
                IntPtr m = Marshal.AllocHGlobal((int) Math.Max(1, modes) * 64);
                try {
                  return QueryDisplayConfig(4, ref paths, p, ref modes, m, out topology) == 0 ? topology : 0;
                } finally { Marshal.FreeHGlobal(p); Marshal.FreeHGlobal(m); }
              }
              // SDC_APPLY | SDC_TOPOLOGY_{INTERNAL=1, CLONE=2, EXTEND=4, EXTERNAL=8}
              public static int SetTopology(uint topology) {
                return SetDisplayConfig(0, IntPtr.Zero, 0, IntPtr.Zero, 0x80 | topology);
              }
              public static string Resolution() {
                DEVMODE dm = new DEVMODE(); dm.dmSize = (short) Marshal.SizeOf(typeof(DEVMODE));
                EnumDisplaySettings(null, -1, ref dm);
                return dm.dmPelsWidth + "x" + dm.dmPelsHeight;
              }
              // DM_PELSWIDTH | DM_PELSHEIGHT, CDS_UPDATEREGISTRY
              public static int SetResolution(int width, int height) {
                DEVMODE dm = new DEVMODE(); dm.dmSize = (short) Marshal.SizeOf(typeof(DEVMODE));
                EnumDisplaySettings(null, -1, ref dm);
                dm.dmPelsWidth = width; dm.dmPelsHeight = height; dm.dmFields = 0x80000 | 0x100000;
                return ChangeDisplaySettings(ref dm, 1);
              }
              // ABM_GETSTATE = 4, ABM_SETSTATE = 10; ABS_AUTOHIDE = 1, ABS_ALWAYSONTOP = 2
              public static bool AutoHide() {
                APPBARDATA d = new APPBARDATA(); d.cbSize = Marshal.SizeOf(typeof(APPBARDATA));
                return ((long) SHAppBarMessage(4, ref d) & 1) != 0;
              }
              public static void SetAutoHide(bool on) {
                APPBARDATA d = new APPBARDATA(); d.cbSize = Marshal.SizeOf(typeof(APPBARDATA));
                d.hWnd = FindWindow("Shell_TrayWnd", null);
                d.lParam = (IntPtr) (on ? 1 : 2);
                SHAppBarMessage(10, ref d);
              }
            }
            "@
            $changes = @()
            $failures = @()
            """;

    private final LocalCommandRunner runner;

    public WindowsEnvironment(LocalCommandRunner runner) {
        this.runner = runner;
    }

    /// Whether `profile` has anything for this class to do — the
    /// `bookmarksBar` key is the browser extension's.
    public static boolean handles(EnvironmentProfile profile) {
        return profile.displays() != null || profile.resolution() != null || profile.taskbar() != null;
    }

    /// The PowerShell script putting the machine-level keys of `profile` into
    /// effect. Only enum constants and integers reach the script text.
    static String script(EnvironmentProfile profile) {
        StringBuilder script = new StringBuilder(TYPES);
        if (profile.displays() != null) {
            int topology = switch (profile.displays()) {
                case INTERNAL -> 1;
                case CLONE -> 2;
                case EXTEND -> 4;
                case EXTERNAL -> 8;
            };
            String key = profile.displays().key();
            script.append("""
                    if ([CsEnv]::Topology() -ne %1$d) {
                      $rc = [CsEnv]::SetTopology(%1$d)
                      if ($rc -ne 0) { $failures += "displays %2$s unavailable ($rc)" }
                      else { $changes += 'displays %2$s'; Start-Sleep -Milliseconds 2500 }
                    }
                    """.formatted(topology, key));
        }
        if (profile.resolution() != null) {
            EnvironmentProfile.Resolution r = profile.resolution();
            script.append("""
                    if ([CsEnv]::Resolution() -ne '%1$dx%2$d') {
                      $rc = [CsEnv]::SetResolution(%1$d, %2$d)
                      if ($rc -lt 0) { $failures += "resolution %1$dx%2$d not supported ($rc)" }
                      else { $changes += 'resolution %1$dx%2$d' }
                    }
                    """.formatted(r.width(), r.height()));
        }
        if (profile.taskbar() != null) {
            boolean autoHide = profile.taskbar() == EnvironmentProfile.Taskbar.AUTOHIDE;
            script.append("""
                    if ([CsEnv]::AutoHide() -ne $%1$s) {
                      [CsEnv]::SetAutoHide($%1$s)
                      $changes += 'taskbar %2$s'
                    }
                    """.formatted(autoHide, profile.taskbar().key()));
        }
        script.append("""
                if ($failures.Count -gt 0) { Write-Output (($failures + $changes) -join ', '); exit 3 }
                Write-Output ($changes -join ', ')
                exit 0
                """);
        return script.toString();
    }

    static List<String> command(EnvironmentProfile profile) {
        String encoded = Base64.getEncoder().encodeToString(
                script(profile).getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    /// Applies `profile`'s machine-level keys; a profile without any is a
    /// no-op success without spawning `powershell`.
    public ApplyResult apply(EnvironmentProfile profile) {
        if (!handles(profile)) {
            return new ApplyResult(true, "");
        }
        LocalCommandRunner.LocalResult result = runner.run(command(profile));
        String out = result.stdout().strip();
        if (result.exitCode() == 0) {
            return new ApplyResult(true, out);
        }
        List<String> parts = new ArrayList<>();
        if (!out.isEmpty()) {
            parts.add(out);
        }
        if (!result.stderr().isBlank()) {
            parts.add(result.stderr().strip());
        }
        return new ApplyResult(false, parts.isEmpty()
                ? "powershell exit " + result.exitCode() : String.join(": ", parts));
    }
}
