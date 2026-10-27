package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.contextswitcher.config.EnvironmentProfile;

/// Puts the machine-level keys of an [EnvironmentProfile] into effect —
/// projection mode (`displays`), per-screen resolution and scale (`screens`), and taskbar
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
///   its own that the `screens` step is to override.
/// - `screens`: `QueryDisplayConfig(QDC_ONLY_ACTIVE_PATHS)` lists the
///   active screens; `DisplayConfigGetDeviceInfo` names each (EDID friendly
///   name, the product code from its device path, the output technology that
///   tells the laptop panel from an external screen, its GDI source). Each
///   screen takes the rule for its name or code, else `primary` (the screen
///   at 0,0), else `internal`/`external`; a screen no rule matches is left
///   alone, and so is a rule for a screen that is not connected. Changed
///   modes (of rules with a `resolution`) are staged with `ChangeDisplaySettingsEx(CDS_UPDATEREGISTRY |
///   CDS_NORESET)` and applied together — persisted, so a projector hot-plug
///   (which re-applies the stored mode) does not undo them; the `default`
///   profile is what puts them back. Each matched screen's scale (the
///   Settings app's "Scale", 100% unless the rule says otherwise) follows
///   once the modes are in place, through the undocumented
///   `DisplayConfigGet/SetDeviceInfo` DPI-scale requests the Settings app
///   uses: they count steps of the Windows scale list from the screen's
///   recommended scale, which depends on the resolution just set.
/// - `taskbar`: `SHAppBarMessage(ABM_GETSTATE/ABM_SETSTATE)` on
///   `Shell_TrayWnd` — the auto-hide checkbox of the taskbar settings, which
///   ExplorerPatcher's Windows 10 taskbar honours as well.
///
/// The script prints the changes as one line; exit 3 means a requested mode
/// the displays do not offer (no projector plugged in for `clone`, a mode a
/// screen lacks) — the other keys are still applied, and the line names the
/// failures first. Windows-only by nature: elsewhere the runner skips
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
            using System.Collections.Generic;
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
            public class CsScreens {
              [StructLayout(LayoutKind.Sequential)]
              public struct LUID { public uint Low; public int High; }
              [StructLayout(LayoutKind.Sequential)]
              public struct PATH {
                public LUID sourceAdapter; public uint sourceId, sourceModeIdx, sourceStatus;
                public LUID targetAdapter; public uint targetId, targetModeIdx, outputTechnology, rotation, scaling, refreshNum, refreshDen, scanLine;
                public int targetAvailable; public uint targetStatus; public uint flags;
              }
              [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
              public struct TARGET_NAME {
                public uint type, size; public LUID adapter; public uint id;
                public uint flags, outputTechnology; public ushort edidManufacture, edidProduct; public uint connector;
                [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 64)] public string friendlyName;
                [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string devicePath;
              }
              [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
              public struct SOURCE_NAME {
                public uint type, size; public LUID adapter; public uint id;
                [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string gdiName;
              }
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
              [DllImport("user32.dll")] static extern int GetDisplayConfigBufferSizes(uint flags, out uint paths, out uint modes);
              [DllImport("user32.dll")] static extern int QueryDisplayConfig(uint flags, ref uint paths, [Out] PATH[] pathArray, ref uint modes, IntPtr modeArray, IntPtr topology);
              [DllImport("user32.dll")] static extern int DisplayConfigGetDeviceInfo(ref TARGET_NAME info);
              [DllImport("user32.dll")] static extern int DisplayConfigGetDeviceInfo(ref SOURCE_NAME info);
              // Undocumented DISPLAYCONFIG_DEVICE_INFO_GET/SET_DPI_SCALE (-3/-4), what the Settings
              // app's "Scale" uses: steps relative to the screen's recommended scale.
              [StructLayout(LayoutKind.Sequential)]
              public struct DPI_GET { public int type; public uint size; public LUID adapter; public uint id; public int minRel, curRel, maxRel; }
              [StructLayout(LayoutKind.Sequential)]
              public struct DPI_SET { public int type; public uint size; public LUID adapter; public uint id; public int rel; }
              [DllImport("user32.dll")] static extern int DisplayConfigGetDeviceInfo(ref DPI_GET info);
              [DllImport("user32.dll")] static extern int DisplayConfigSetDeviceInfo(ref DPI_SET info);
              static readonly int[] Scales = { 100, 125, 150, 175, 200, 225, 250, 300, 350, 400, 450, 500 };
              [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern bool EnumDisplaySettings(string dev, int mode, ref DEVMODE dm);
              [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int ChangeDisplaySettingsEx(string dev, ref DEVMODE dm, IntPtr hwnd, uint flags, IntPtr param);
              [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int ChangeDisplaySettingsEx(string dev, IntPtr dm, IntPtr hwnd, uint flags, IntPtr param);

              public class Screen { public string Gdi, Name, Code; public bool Internal, Primary; public int Width, Height; public LUID Adapter; public uint Source; }

              // One entry per active target (a cloned source appears once per screen).
              public static List<Screen> Active() {
                var result = new List<Screen>();
                uint paths, modes;
                if (GetDisplayConfigBufferSizes(2, out paths, out modes) != 0) { return result; }
                var pathArray = new PATH[paths];
                IntPtr modeArray = Marshal.AllocHGlobal((int) Math.Max(1, modes) * 64);
                try {
                  if (QueryDisplayConfig(2, ref paths, pathArray, ref modes, modeArray, IntPtr.Zero) != 0) { return result; }
                } finally { Marshal.FreeHGlobal(modeArray); }
                for (int i = 0; i < paths; i++) {
                  var t = new TARGET_NAME(); t.type = 2; t.size = (uint) Marshal.SizeOf(typeof(TARGET_NAME));
                  t.adapter = pathArray[i].targetAdapter; t.id = pathArray[i].targetId;
                  var s = new SOURCE_NAME(); s.type = 1; s.size = (uint) Marshal.SizeOf(typeof(SOURCE_NAME));
                  s.adapter = pathArray[i].sourceAdapter; s.id = pathArray[i].sourceId;
                  if (DisplayConfigGetDeviceInfo(ref t) != 0 || DisplayConfigGetDeviceInfo(ref s) != 0) { continue; }
                  var dm = new DEVMODE(); dm.dmSize = (short) Marshal.SizeOf(typeof(DEVMODE));
                  EnumDisplaySettings(s.gdiName, -1, ref dm);
                  string[] parts = (t.devicePath ?? "").Split('#');
                  uint tech = t.outputTechnology;
                  result.Add(new Screen {
                    Gdi = s.gdiName, Name = t.friendlyName ?? "", Code = parts.Length > 1 ? parts[1] : "",
                    // LVDS, embedded DisplayPort, embedded UDI, or the generic internal flag
                    Internal = tech == 6 || tech == 11 || tech == 13 || tech == 0x80000000,
                    Primary = dm.dmPositionX == 0 && dm.dmPositionY == 0,
                    Width = dm.dmPelsWidth, Height = dm.dmPelsHeight,
                    Adapter = pathArray[i].sourceAdapter, Source = pathArray[i].sourceId });
                }
                return result;
              }

              public static string Describe() {
                var lines = new List<string>();
                foreach (var s in Active()) {
                  lines.Add(string.Format("{0} {1} [{2}] {3}x{4}{5}{6}", s.Gdi, s.Name == "" ? "(no name)" : s.Name, s.Code,
                      s.Width, s.Height, s.Internal ? " internal" : " external", s.Primary ? " primary" : ""));
                }
                return string.Join("; ", lines);
              }

              // The rule for a screen: its EDID name or product code, then primary, then internal/external.
              static int Pick(Screen s, string[] keys) {
                foreach (string want in new[] { s.Name, s.Code }) {
                  if (want == "") { continue; }
                  for (int i = 0; i < keys.Length; i++) { if (string.Equals(keys[i], want, StringComparison.OrdinalIgnoreCase)) { return i; } }
                }
                foreach (string want in new[] { s.Primary ? "primary" : null, s.Internal ? "internal" : "external" }) {
                  if (want == null) { continue; }
                  for (int i = 0; i < keys.Length; i++) { if (string.Equals(keys[i], want, StringComparison.OrdinalIgnoreCase)) { return i; } }
                }
                return -1;
              }

              // Sets each matched screen's mode (staged with CDS_UPDATEREGISTRY | CDS_NORESET,
              // then applied at once), then its scale; returns the changes, failures prefixed with "!".
              public static List<string> Apply(string[] keys, int[] widths, int[] heights, int[] scales) {
                var changes = Modes(keys, widths, heights);
                changes.AddRange(Scale(keys, scales));
                return changes;
              }

              static List<string> Modes(string[] keys, int[] widths, int[] heights) {
                var changes = new List<string>();
                var done = new HashSet<string>();
                bool staged = false;
                foreach (var s in Active()) {
                  int rule = Pick(s, keys);
                  string label = s.Name != "" ? s.Name : (s.Code != "" ? s.Code : s.Gdi);
                  if (rule < 0 || done.Contains(s.Gdi)) { continue; }
                  done.Add(s.Gdi);
                  // 0x0: the rule only sets the scale
                  if (widths[rule] == 0 || (s.Width == widths[rule] && s.Height == heights[rule])) { continue; }
                  var dm = new DEVMODE(); dm.dmSize = (short) Marshal.SizeOf(typeof(DEVMODE));
                  EnumDisplaySettings(s.Gdi, -1, ref dm);
                  dm.dmPelsWidth = widths[rule]; dm.dmPelsHeight = heights[rule]; dm.dmFields = 0x80000 | 0x100000;
                  int rc = ChangeDisplaySettingsEx(s.Gdi, ref dm, IntPtr.Zero, 0x1 | 0x10000000, IntPtr.Zero);
                  if (rc < 0) { changes.Add(string.Format("!{0} {1}x{2} not supported ({3})", label, widths[rule], heights[rule], rc)); continue; }
                  staged = true;
                  changes.Add(string.Format("{0} {1}x{2}", label, widths[rule], heights[rule]));
                }
                if (staged) { ChangeDisplaySettingsEx(null, IntPtr.Zero, IntPtr.Zero, 0, IntPtr.Zero); }
                return changes;
              }

              // Runs after the modes are in place: the recommended scale the steps count
              // from depends on the resolution.
              static List<string> Scale(string[] keys, int[] scales) {
                var changes = new List<string>();
                var done = new HashSet<string>();
                foreach (var s in Active()) {
                  int rule = Pick(s, keys);
                  string label = s.Name != "" ? s.Name : (s.Code != "" ? s.Code : s.Gdi);
                  if (rule < 0 || done.Contains(s.Gdi)) { continue; }
                  done.Add(s.Gdi);
                  var get = new DPI_GET(); get.type = -3; get.size = (uint) Marshal.SizeOf(typeof(DPI_GET));
                  get.adapter = s.Adapter; get.id = s.Source;
                  int rc = DisplayConfigGetDeviceInfo(ref get);
                  if (rc != 0) { changes.Add(string.Format("!{0} scale unreadable ({1})", label, rc)); continue; }
                  int recommended = -get.minRel;
                  int target = Array.IndexOf(Scales, scales[rule]) - recommended;
                  if (target == get.curRel) { continue; }
                  if (target < get.minRel || target > get.maxRel) {
                    int max = Scales[Math.Min(Scales.Length - 1, recommended + get.maxRel)];
                    changes.Add(string.Format("!{0} scale {1}% not offered (100-{2}%)", label, scales[rule], max));
                    continue;
                  }
                  var set = new DPI_SET(); set.type = -4; set.size = (uint) Marshal.SizeOf(typeof(DPI_SET));
                  set.adapter = s.Adapter; set.id = s.Source; set.rel = target;
                  rc = DisplayConfigSetDeviceInfo(ref set);
                  if (rc != 0) { changes.Add(string.Format("!{0} scale {1}% failed ({2})", label, scales[rule], rc)); continue; }
                  changes.Add(string.Format("{0} {1}%", label, scales[rule]));
                }
                return changes;
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
        return profile.displays() != null || !profile.screens().isEmpty() || profile.taskbar() != null;
    }

    /// The PowerShell script putting the machine-level keys of `profile` into
    /// effect. Besides enum constants and integers only the screen selectors
    /// reach the script text, as single-quoted literals with `'` doubled.
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
        if (!profile.screens().isEmpty()) {
            List<String> selectors = new ArrayList<>();
            List<String> widths = new ArrayList<>();
            List<String> heights = new ArrayList<>();
            List<String> scales = new ArrayList<>();
            profile.screens().forEach((selector, screen) -> {
                selectors.add("'" + selector.replace("'", "''") + "'");
                widths.add(screen.resolution() == null ? "0" : Integer.toString(screen.resolution().width()));
                heights.add(screen.resolution() == null ? "0" : Integer.toString(screen.resolution().height()));
                scales.add(Integer.toString(screen.scale()));
            });
            script.append("""
                    foreach ($c in [CsScreens]::Apply(@(%s), @(%s), @(%s), @(%s))) {
                      if ($c.StartsWith('!')) { $failures += $c.Substring(1) } else { $changes += $c }
                    }
                    """.formatted(String.join(", ", selectors), String.join(", ", widths),
                    String.join(", ", heights), String.join(", ", scales)));
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
