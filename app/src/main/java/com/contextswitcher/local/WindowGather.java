package com.contextswitcher.local;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
/// Windows on other virtual desktops (cloaked by the shell) are gathered
/// too, while unseen: docking moves every desktop's windows at once.
///
/// Left alone: full-screen windows (covering their whole screen without
/// being maximized with a title bar — the PowerPoint slide show, a PDF in
/// presentation mode, a browser in F11), minimized ones, windows cloaked
/// for other reasons, tool windows, owned windows (dialogs follow their
/// owner), untitled ones, and the shell's own (taskbars, desktop, Start). A
/// maximized window is maximized again on the target screen; it covers its
/// screen too where no taskbar takes room — the laptop's maximized Firefox
/// was taken for a slide show and never moved (field report 2026-11-22) —
/// which is why "maximized" is told apart from "full screen". A window that
/// is back on its old screen after [#MAX_TRIES] moves (an elevated Task
/// Manager refuses them) is reported once and left alone. The target is picked with [WindowsEnvironment]'s screen
/// selectors (EDID name or product code, `primary`, `internal`,
/// `external`); with a single screen, or no screen matching, nothing moves.
///
/// With `gatherWhen: docking` the watcher does not look at new windows at
/// all: it sweeps every window over for [#DOCK_SWEEP_MS] after the screens
/// changed — reported by [#screensChanged], or seen by the running watcher —
/// and leaves everything alone otherwise. At the desk a window opened on the
/// laptop or the second monitor stays there (field report 2026-11-22: a new
/// SumatraPDF on the 27" was pulled onto the 32" and back three times). A
/// watcher started without a recent screen change (the app starting while
/// docked, a task switch) does not sweep.
///
/// One watcher at a time: [#gather] with the running selector is a no-op,
/// another selector restarts it, null stops it. The watcher also exits when
/// the app's process is gone, so a crash leaves no orphan behind.
// [impl->dsn~environment-profile-gather-windows~3]
public class WindowGather implements AutoCloseable {

    /// How long after first sight a window on the wrong screen is pulled over.
    static final int GRACE_MS = 4000;

    /// Moves of one window, per grace, before it is reported and left alone.
    static final int MAX_TRIES = 3;

    /// With `gatherWhen: docking`: how long after a screen change windows
    /// are swept over — long enough for Windows' own restoring of window
    /// positions on redocking to be undone.
    static final int DOCK_SWEEP_MS = 10_000;

    /// A screen change this recent makes a newly started docking watcher
    /// sweep: the screen watch reports a set once it has been stable for a
    /// few seconds, and the profile is applied after that.
    static final long RECENT_CHANGE_MS = 20_000;

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
                // 2 = DWM_CLOAKED_SHELL: on another virtual desktop, gathered too
                if (DwmGetWindowAttribute(h, 14, out cloaked, 4) == 0 && cloaked != 0 && cloaked != 2) { return false; }
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
                Console.WriteLine("moved " + Name(h) + " from " + from.Device + " to " + to.Device);
              }

              static string Name(IntPtr h) {
                return Label(h) + " 0x" + h.ToInt64().ToString("X");
              }

              // Covers its whole screen without being an ordinary maximized
              // window (WS_MAXIMIZE with WS_CAPTION): the slide show, F11.
              static bool FullScreen(IntPtr h, RECT r, Mon m) {
                bool covers = r.L <= m.Full.L && r.T <= m.Full.T && r.R >= m.Full.R && r.B >= m.Full.B;
                bool maximized = IsZoomed(h) && (GetWindowLong(h, -16) & 0x00C00000) == 0x00C00000;
                return covers && !maximized;
              }

              public static void Run(string selector, int parentPid, int graceMs, int maxTries,
                  bool dockingOnly, bool sweepNow, int sweepMs) {
                SetThreadDpiAwarenessContext((IntPtr) (-4));  // per-monitor v2: physical pixels everywhere
                Process parent = null;
                try { parent = Process.GetProcessById(parentPid); } catch { return; }
                var firstSeen = new Dictionary<IntPtr, long>();
                var tries = new Dictionary<IntPtr, int>();
                string layout = null;
                string target = null;
                var clock = Stopwatch.StartNew();
                long sweepFrom = long.MinValue / 2;
                Console.WriteLine("watching for " + selector + (dockingOnly ? " (on docking only)" : ""));
                while (!parent.HasExited) {
                  long tick = clock.ElapsedMilliseconds;
                  var mons = Monitors();
                  string now = Layout(mons);
                  if (now != layout) {
                    bool first = layout == null;
                    layout = now;
                    if (dockingOnly) {
                      if (!first || sweepNow) { sweepFrom = tick; Console.WriteLine("sweeping windows over for " + sweepMs + " ms"); }
                      else { Console.WriteLine("no recent docking: windows stay where they are"); }
                    }
                    firstSeen.Clear();  // a changed screen set: every window gets its grace again
                    tries.Clear();
                    target = null;
                    foreach (var s in CsScreens.Active()) {
                      if (CsScreens.Pick(s, new[] { selector }) == 0) { target = s.Gdi; break; }
                    }
                    Console.WriteLine("screens " + now + "; target " + (target ?? "none"));
                  }
                  Mon to = mons.Find(m => string.Equals(m.Device, target, StringComparison.OrdinalIgnoreCase));
                  bool sweeping = tick - sweepFrom <= sweepMs;
                  if (to != null && mons.Count > 1 && (!dockingOnly || sweeping)) {
                    var seen = new HashSet<IntPtr>();
                    EnumWindows((h, l) => {
                      if (!Candidate(h)) { return true; }
                      seen.Add(h);
                      if (!dockingOnly) {
                        long since;
                        if (!firstSeen.TryGetValue(h, out since)) { since = tick; firstSeen[h] = tick; }
                        if (tick - since > graceMs) { return true; }
                      }
                      IntPtr hm = MonitorFromWindow(h, 2);  // MONITOR_DEFAULTTONEAREST
                      if (hm == to.H) { return true; }
                      Mon from = mons.Find(m => m.H == hm);
                      if (from == null) { return true; }
                      RECT r; GetWindowRect(h, out r);
                      // full screen: the slide show stays on the projector
                      if (FullScreen(h, r, from)) { return true; }
                      int n; tries.TryGetValue(h, out n);
                      if (n >= maxTries) {
                        if (n == maxTries) { Console.WriteLine("did not stay: " + Name(h) + " is back on " + from.Device + " after " + n + " moves; left alone"); }
                        tries[h] = maxTries + 1;
                        return true;
                      }
                      tries[h] = n + 1;
                      try { Move(h, from, to); } catch (Exception e) { Console.WriteLine("move failed: " + e.Message); }
                      return true;
                    }, IntPtr.Zero);
                    // minimized, hidden or closed windows get a fresh grace when they show again;
                    // a window that would not stay is forgotten only once it is closed
                    var gone = new List<IntPtr>();
                    foreach (var h in firstSeen.Keys) { if (!seen.Contains(h)) { gone.Add(h); } }
                    foreach (var h in gone) { firstSeen.Remove(h); }
                    var closed = new List<IntPtr>();
                    foreach (var h in tries.Keys) { if (!seen.Contains(h) && !IsWindow(h)) { closed.Add(h); } }
                    foreach (var h in closed) { tries.Remove(h); }
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
    private boolean dockingOnly;
    private volatile long screensChangedAt;
    private volatile boolean screensChangedSeen;

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
        return script(selector, parentPid, false, true);
    }

    /// The watcher script; with `dockingOnly` it sweeps only after a screen
    /// change, and at its start only when `sweepNow`.
    static String script(String selector, long parentPid, boolean dockingOnly, boolean sweepNow) {
        return "$ErrorActionPreference = 'Stop'\n"
                + SCRIPT.replace("__SCREENS__", WindowsEnvironment.screenTypes())
                + "[CsGather]::Run('%s', %d, %d, %d, $%b, $%b, %d)\n".formatted(selector.replace("'", "''"),
                        parentPid, GRACE_MS, MAX_TRIES, dockingOnly, sweepNow, DOCK_SWEEP_MS);
    }

    static List<String> command(String selector, long parentPid) {
        return PowerShellCommand.of(script(selector, parentPid));
    }

    /// The set of connected screens changed (docking, undocking): a
    /// `gatherWhen: docking` watcher started soon after sweeps.
    public void screensChanged() {
        screensChangedAt = System.nanoTime();
        screensChangedSeen = true;
    }

    private boolean recentScreenChange() {
        return screensChangedSeen && System.nanoTime() - screensChangedAt < RECENT_CHANGE_MS * 1_000_000;
    }

    /// Gathers every window that shows up elsewhere onto `target` from now
    /// on (`gatherWhen: always`); see [#gather(String, boolean)].
    public WindowsEnvironment.ApplyResult gather(@Nullable String target) {
        return gather(target, false);
    }

    /// Gathers windows onto `target` from now on (a screen selector) — with
    /// `dockingOnly` just right after screen changes — or stops gathering
    /// (null). Returns the change for the status chip — empty when nothing
    /// changed.
    public synchronized WindowsEnvironment.ApplyResult gather(@Nullable String target, boolean dockingOnly) {
        Process running = process;
        boolean alive = running != null && running.isAlive();
        if (alive && Objects.equals(target, selector) && dockingOnly == this.dockingOnly) {
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
            Process started = launcher.start(PowerShellCommand.of(
                    script(target, ProcessHandle.current().pid(), dockingOnly, !dockingOnly || recentScreenChange())));
            process = started;
            selector = target;
            this.dockingOnly = dockingOnly;
            Thread reader = new Thread(() -> log(started), "window-gather");
            reader.setDaemon(true);
            reader.start();
            return new WindowsEnvironment.ApplyResult(true,
                    "windows gathered onto " + target + (dockingOnly ? " on docking" : ""));
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
