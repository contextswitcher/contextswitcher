package com.contextswitcher.local;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Keeps windows off the projector while an environment profile with
/// `gatherWindows: <screen>` is in effect: a long-running `powershell`
/// watcher (inline C#, like [WindowsEnvironment]) that pulls every window
/// that shows up on another screen onto the named one.
///
/// "Shows up" is the point — the watcher does not pin windows: a window is
/// pulled over only within [#GRACE_MS] of the watcher first seeing it (the
/// start of the watcher, a new window, one restored from minimized or
/// arriving from another virtual desktop), and again whenever the set of
/// screens changes (a projector plugged in moves windows around). A window
/// the lecturer drags onto the projector afterwards stays there.
///
/// Left alone: full-screen windows (covering their whole screen — the
/// PowerPoint slide show, a PDF in presentation mode), minimized and
/// cloaked ones (other virtual desktops), tool windows, owned windows
/// (dialogs follow their owner), untitled ones, and the shell's own
/// (taskbars, desktop, Start). A maximized window is maximized again on the
/// target screen. The target is picked with [WindowsEnvironment]'s screen
/// selectors (EDID name or product code, `primary`, `internal`,
/// `external`); with a single screen, or no screen matching, nothing moves.
///
/// One watcher at a time: [#gather] with the running selector is a no-op,
/// another selector restarts it, null stops it. The watcher also exits when
/// the app's process is gone, so a crash leaves no orphan behind.
// [impl->dsn~environment-profile-gather-windows~1]
public class WindowGather implements AutoCloseable {

    /// How long after first sight a window on the wrong screen is pulled over.
    static final int GRACE_MS = 4000;

    private static final String SCRIPT = """
            Add-Type -TypeDefinition @"
            using System;
            using System.Collections.Generic;
            using System.Diagnostics;
            using System.Runtime.InteropServices;
            using System.Text;
            using System.Threading;
            public static class CsGather {
              [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
              [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
              public struct MONITORINFOEX {
                public int cbSize; public RECT rcMonitor, rcWork; public uint dwFlags;
                [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string szDevice;
              }
              delegate bool EnumProc(IntPtr h, IntPtr l);
              delegate bool MonProc(IntPtr m, IntPtr dc, ref RECT r, IntPtr l);
              [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc cb, IntPtr l);
              [DllImport("user32.dll")] static extern bool EnumDisplayMonitors(IntPtr dc, IntPtr clip, MonProc cb, IntPtr l);
              [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern bool GetMonitorInfo(IntPtr m, ref MONITORINFOEX mi);
              [DllImport("user32.dll")] static extern IntPtr MonitorFromWindow(IntPtr h, uint flags);
              [DllImport("user32.dll")] static extern bool IsWindow(IntPtr h);
              [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
              [DllImport("user32.dll")] static extern bool IsIconic(IntPtr h);
              [DllImport("user32.dll")] static extern bool IsZoomed(IntPtr h);
              [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr h, out RECT r);
              [DllImport("user32.dll")] static extern int GetWindowLong(IntPtr h, int index);
              [DllImport("user32.dll")] static extern IntPtr GetWindow(IntPtr h, uint cmd);
              [DllImport("user32.dll")] static extern int GetWindowTextLength(IntPtr h);
              [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetClassName(IntPtr h, StringBuilder s, int n);
              [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
              [DllImport("user32.dll")] static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int w, int ht, uint flags);
              [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr h, int cmd);
              [DllImport("user32.dll")] static extern IntPtr SetThreadDpiAwarenessContext(IntPtr ctx);
              [DllImport("dwmapi.dll")] static extern int DwmGetWindowAttribute(IntPtr h, int attr, out int value, int size);
              static readonly HashSet<string> Shell = new HashSet<string> {
                "Shell_TrayWnd", "Shell_SecondaryTrayWnd", "Progman", "WorkerW",
                "Windows.UI.Core.CoreWindow", "XamlExplorerHostIslandWindow", "TopLevelWindowForOverflowXamlIsland" };

              class Mon { public IntPtr H; public RECT Full, Work; public string Device; }

              static List<Mon> Monitors() {
                var result = new List<Mon>();
                EnumDisplayMonitors(IntPtr.Zero, IntPtr.Zero, (IntPtr m, IntPtr dc, ref RECT r, IntPtr l) => {
                  var mi = new MONITORINFOEX(); mi.cbSize = Marshal.SizeOf(typeof(MONITORINFOEX));
                  if (GetMonitorInfo(m, ref mi)) { result.Add(new Mon { H = m, Full = mi.rcMonitor, Work = mi.rcWork, Device = mi.szDevice }); }
                  return true;
                }, IntPtr.Zero);
                return result;
              }

              static string Layout(List<Mon> mons) {
                var parts = new List<string>();
                foreach (var m in mons) { parts.Add(string.Format("{0}@{1},{2},{3},{4}", m.Device, m.Full.L, m.Full.T, m.Full.R, m.Full.B)); }
                parts.Sort();
                return string.Join(";", parts);
              }

              static string Label(IntPtr h) {
                uint pid; GetWindowThreadProcessId(h, out pid);
                try { return Process.GetProcessById((int) pid).ProcessName; } catch { return "pid " + pid; }
              }

              static bool Candidate(IntPtr h) {
                if (!IsWindowVisible(h) || IsIconic(h) || GetWindowTextLength(h) == 0) { return false; }
                int ex = GetWindowLong(h, -20);
                bool app = (ex & 0x40000) != 0;            // WS_EX_APPWINDOW
                if ((ex & 0x80) != 0 && !app) { return false; }  // WS_EX_TOOLWINDOW
                if (GetWindow(h, 4) != IntPtr.Zero && !app) { return false; }  // GW_OWNER
                int cloaked;
                if (DwmGetWindowAttribute(h, 14, out cloaked, 4) == 0 && cloaked != 0) { return false; }
                var cls = new StringBuilder(256); GetClassName(h, cls, cls.Capacity);
                return !Shell.Contains(cls.ToString());
              }

              static void Move(IntPtr h, Mon from, Mon to) {
                bool max = IsZoomed(h);
                if (max) { ShowWindow(h, 4); }  // SW_SHOWNOACTIVATE restores without raising
                RECT r; GetWindowRect(h, out r);
                int w = Math.Min(r.R - r.L, to.Work.R - to.Work.L);
                int ht = Math.Min(r.B - r.T, to.Work.B - to.Work.T);
                int x = to.Work.L + Math.Max(0, Math.Min(r.L - from.Work.L, (to.Work.R - to.Work.L) - w));
                int y = to.Work.T + Math.Max(0, Math.Min(r.T - from.Work.T, (to.Work.B - to.Work.T) - ht));
                SetWindowPos(h, IntPtr.Zero, x, y, w, ht, 0x4 | 0x10);  // SWP_NOZORDER | SWP_NOACTIVATE
                if (max) { ShowWindow(h, 3); }
                Console.WriteLine("moved " + Label(h) + " from " + from.Device + " to " + to.Device);
              }

              public static void Run(string selector, int parentPid, int graceMs) {
                SetThreadDpiAwarenessContext((IntPtr) (-4));  // per-monitor v2: physical pixels everywhere
                Process parent = null;
                try { parent = Process.GetProcessById(parentPid); } catch { return; }
                var firstSeen = new Dictionary<IntPtr, long>();
                string layout = null;
                string target = null;
                var clock = Stopwatch.StartNew();
                Console.WriteLine("watching for " + selector);
                while (!parent.HasExited) {
                  var mons = Monitors();
                  string now = Layout(mons);
                  if (now != layout) {
                    layout = now;
                    firstSeen.Clear();  // a changed screen set: every window gets its grace again
                    target = null;
                    foreach (var s in CsScreens.Active()) {
                      if (CsScreens.Pick(s, new[] { selector }) == 0) { target = s.Gdi; break; }
                    }
                    Console.WriteLine("screens " + now + "; target " + (target ?? "none"));
                  }
                  Mon to = mons.Find(m => string.Equals(m.Device, target, StringComparison.OrdinalIgnoreCase));
                  long tick = clock.ElapsedMilliseconds;
                  if (to != null && mons.Count > 1) {
                    var seen = new HashSet<IntPtr>();
                    EnumWindows((h, l) => {
                      if (!Candidate(h)) { return true; }
                      seen.Add(h);
                      long since;
                      if (!firstSeen.TryGetValue(h, out since)) { since = tick; firstSeen[h] = tick; }
                      if (tick - since > graceMs) { return true; }
                      IntPtr hm = MonitorFromWindow(h, 2);  // MONITOR_DEFAULTTONEAREST
                      if (hm == to.H) { return true; }
                      Mon from = mons.Find(m => m.H == hm);
                      if (from == null) { return true; }
                      RECT r; GetWindowRect(h, out r);
                      // full screen: the slide show stays on the projector
                      if (r.L <= from.Full.L && r.T <= from.Full.T && r.R >= from.Full.R && r.B >= from.Full.B) { return true; }
                      try { Move(h, from, to); } catch (Exception e) { Console.WriteLine("move failed: " + e.Message); }
                      return true;
                    }, IntPtr.Zero);
                    // minimized, hidden or closed windows get a fresh grace when they show again
                    var gone = new List<IntPtr>();
                    foreach (var h in firstSeen.Keys) { if (!seen.Contains(h)) { gone.Add(h); } }
                    foreach (var h in gone) { firstSeen.Remove(h); }
                  }
                  Thread.Sleep(500);
                }
              }
            }
            __SCREENS__
            "@
            """;

    /// Starts a watcher process from its argv; swapped out in tests.
    @FunctionalInterface
    interface Launcher {
        Process start(List<String> command) throws IOException;
    }

    private final Launcher launcher;
    private final boolean windows;
    private @Nullable Process process;
    private @Nullable String selector;

    public WindowGather() {
        this(command -> new ProcessBuilder(command).redirectErrorStream(true).start(),
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"));
    }

    WindowGather(Launcher launcher, boolean windows) {
        this.launcher = launcher;
        this.windows = windows;
    }

    /// The watcher script for `selector`, stopping when process `parentPid`
    /// exits. The selector reaches the script as a single-quoted literal
    /// with `'` doubled.
    static String script(String selector, long parentPid) {
        return "$ErrorActionPreference = 'Stop'\n"
                + SCRIPT.replace("__SCREENS__", WindowsEnvironment.screenTypes())
                + "[CsGather]::Run('%s', %d, %d)\n".formatted(selector.replace("'", "''"), parentPid, GRACE_MS);
    }

    static List<String> command(String selector, long parentPid) {
        String encoded = Base64.getEncoder().encodeToString(
                script(selector, parentPid).getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    /// Gathers windows onto `target` from now on (a screen selector), or
    /// stops gathering (null). Returns the change for the status chip —
    /// empty when nothing changed.
    public synchronized WindowsEnvironment.ApplyResult gather(@Nullable String target) {
        Process running = process;
        boolean alive = running != null && running.isAlive();
        if (alive && Objects.equals(target, selector)) {
            return new WindowsEnvironment.ApplyResult(true, "");
        }
        stopWatcher();
        if (target == null) {
            return new WindowsEnvironment.ApplyResult(true, alive ? "windows no longer gathered" : "");
        }
        if (!windows) {
            return new WindowsEnvironment.ApplyResult(true, "gatherWindows: Windows only");
        }
        try {
            Process started = launcher.start(command(target, ProcessHandle.current().pid()));
            process = started;
            selector = target;
            Thread reader = new Thread(() -> log(started), "window-gather");
            reader.setDaemon(true);
            reader.start();
            return new WindowsEnvironment.ApplyResult(true, "windows gathered onto " + target);
        } catch (IOException e) {
            return new WindowsEnvironment.ApplyResult(false, "gatherWindows: " + e.getMessage());
        }
    }

    private void log(Process watcher) {
        try (BufferedReader out = new BufferedReader(
                new InputStreamReader(watcher.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = out.readLine()) != null) {
                if (!line.isBlank()) {
                    Logger.info("Window gather: {}", line.strip());
                }
            }
        } catch (IOException e) {
            // the process was stopped
        }
        boolean stopped;
        synchronized (this) {
            stopped = process != watcher;
        }
        if (!stopped && !watcher.isAlive() && watcher.exitValue() != 0) {
            Logger.warn("Window gather watcher exited with {}", watcher.exitValue());
        }
    }

    private void stopWatcher() {
        Process running = process;
        if (running != null) {
            running.destroy();
        }
        process = null;
        selector = null;
    }

    @Override
    public synchronized void close() {
        stopWatcher();
    }
}
