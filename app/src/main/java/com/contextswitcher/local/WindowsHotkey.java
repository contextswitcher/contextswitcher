package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.contextswitcher.config.WindowHotkey;

/// The global *switch by window* hotkey on Windows
/// (`dsn~switch-by-window-hotkey~1`): a **persistent** `powershell` process
/// whose inline C# registers the hotkey with `RegisterHotKey` (thread-bound,
/// no window, `MOD_NOREPEAT` so holding the keys fires once) and then runs a
/// `GetMessage` loop, printing one line per press that describes the window
/// in front — the same pattern as the active-desktop watcher
/// ([WindowsVirtualDesktopFocus#watch]), so there is no native library in the
/// JVM (MADR 0039).
///
/// Protocol on stdout (UTF-8, auto-flushed):
/// - `registered` once the hotkey is held,
/// - `failed:<win32 error>` when `RegisterHotKey` refuses (1409: taken), after
///   which the process exits,
/// - `hotkey:<pid>\t<process name>\t<title>` per press.
///
/// The window described is the foreground one — unless it belongs to
/// ContextSwitcher itself (its pid is passed in): then the next window down
/// the z-order that a user would call a window (visible, not cloaked on
/// another desktop, titled, unowned, no tool window) — i.e. the one that was
/// in front before ContextSwitcher was.
///
/// The process ends when its stdin reaches end-of-file: a thread blocks on
/// `Console.In` and posts `WM_QUIT` to the loop, which unregisters the
/// hotkey. Java keeps the pipe open for the process's whole life, so a JVM
/// that dies without [Listener#close] still frees the hotkey rather than
/// leaving an orphan holding it — the next start would otherwise fail with
/// 1409 against its own predecessor.
// [impl->dsn~switch-by-window-hotkey~1]
public final class WindowsHotkey {

    /// One press: the window in front (or, when that was ContextSwitcher, the
    /// one before it).
    public record Press(long pid, String process, String title) {
    }

    static final String REGISTERED = "registered";
    static final String FAILED_PREFIX = "failed:";
    static final String PRESS_PREFIX = "hotkey:";

    private static final String TYPES = """
            using System;
            using System.Text;
            using System.Threading;
            using System.Runtime.InteropServices;
            public static class CsHotkey {
              [StructLayout(LayoutKind.Sequential)]
              struct MSG { public IntPtr hwnd; public uint message; public IntPtr wParam; public IntPtr lParam; public uint time; public int x; public int y; }
              [DllImport("user32.dll", SetLastError = true)] static extern bool RegisterHotKey(IntPtr h, int id, uint mods, uint vk);
              [DllImport("user32.dll")] static extern bool UnregisterHotKey(IntPtr h, int id);
              [DllImport("user32.dll")] static extern int GetMessage(out MSG m, IntPtr h, uint min, uint max);
              [DllImport("user32.dll")] static extern bool PostThreadMessage(uint tid, uint msg, IntPtr w, IntPtr l);
              [DllImport("kernel32.dll")] static extern uint GetCurrentThreadId();
              [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
              [DllImport("user32.dll")] static extern IntPtr GetWindow(IntPtr h, uint cmd);
              [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
              [DllImport("user32.dll")] static extern int GetWindowLong(IntPtr h, int index);
              [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
              [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
              [DllImport("user32.dll")] static extern bool AllowSetForegroundWindow(uint pid);
              [DllImport("dwmapi.dll")] static extern int DwmGetWindowAttribute(IntPtr h, int attr, out int value, int size);
              const uint MOD_NOREPEAT = 0x4000, WM_HOTKEY = 0x0312, WM_QUIT = 0x0012, GW_HWNDNEXT = 2, GW_OWNER = 4;
              const int GWL_EXSTYLE = -20, WS_EX_TOOLWINDOW = 0x80, DWMWA_CLOAKED = 14;
              static uint Pid(IntPtr h) { uint pid; GetWindowThreadProcessId(h, out pid); return pid; }
              static string Title(IntPtr h) { var s = new StringBuilder(1024); GetWindowText(h, s, 1024); return s.ToString(); }
              static bool Eligible(IntPtr h, uint own) {
                if (!IsWindowVisible(h) || GetWindow(h, GW_OWNER) != IntPtr.Zero || Pid(h) == own) { return false; }
                if ((GetWindowLong(h, GWL_EXSTYLE) & WS_EX_TOOLWINDOW) != 0) { return false; }
                int cloaked = 0;
                if (DwmGetWindowAttribute(h, DWMWA_CLOAKED, out cloaked, 4) == 0 && cloaked != 0) { return false; }
                return Title(h).Length > 0;
              }
              static IntPtr Target(uint own) {
                IntPtr fg = GetForegroundWindow();
                if (fg == IntPtr.Zero || Pid(fg) != own) { return fg; }
                for (IntPtr h = GetWindow(fg, GW_HWNDNEXT); h != IntPtr.Zero; h = GetWindow(h, GW_HWNDNEXT)) {
                  if (Eligible(h, own)) { return h; }
                }
                return IntPtr.Zero;
              }
              static string Describe(IntPtr h) {
                if (h == IntPtr.Zero) { return "0\\t\\t"; }
                uint pid = Pid(h);
                string process = "";
                try { process = System.Diagnostics.Process.GetProcessById((int)pid).ProcessName; } catch { }
                return pid + "\\t" + process + "\\t" + Title(h).Replace("\\t", " ").Replace("\\r", " ").Replace("\\n", " ");
              }
              public static int Run(uint mods, uint vk, uint own) {
                if (!RegisterHotKey(IntPtr.Zero, 1, mods | MOD_NOREPEAT, vk)) {
                  Console.Out.WriteLine("failed:" + Marshal.GetLastWin32Error());
                  return 3;
                }
                Console.Out.WriteLine("registered");
                uint tid = GetCurrentThreadId();
                var stdin = new Thread(() => {
                  try { while (Console.In.Read() >= 0) { } } catch { }
                  PostThreadMessage(tid, WM_QUIT, IntPtr.Zero, IntPtr.Zero);
                });
                stdin.IsBackground = true;
                stdin.Start();
                MSG m;
                while (GetMessage(out m, IntPtr.Zero, 0, 0) > 0) {
                  if (m.message == WM_HOTKEY) {
                    // The press is this process's input, so it may hand the
                    // foreground right on: ContextSwitcher can then raise itself.
                    AllowSetForegroundWindow(own);
                    try { Console.Out.WriteLine("hotkey:" + Describe(Target(own))); } catch { }
                  }
                }
                UnregisterHotKey(IntPtr.Zero, 1);
                return 0;
              }
            }
            """;

    private static final String SCRIPT = """
            $ErrorActionPreference = 'Stop'
            $ProgressPreference = 'SilentlyContinue'
            [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
            Add-Type -TypeDefinition @"
            __TYPES__
            "@
            exit [CsHotkey]::Run(__MODS__, __VK__, __PID__)
            """;

    private WindowsHotkey() {
    }

    /// The listener script for `hotkey`, treating windows of `ownPid` as
    /// ContextSwitcher's own.
    static String script(WindowHotkey.Hotkey hotkey, long ownPid) {
        return SCRIPT.replace("__TYPES__", TYPES.strip())
                .replace("__MODS__", Integer.toString(hotkey.modifiers()))
                .replace("__VK__", Integer.toString(hotkey.virtualKey()))
                .replace("__PID__", Long.toString(ownPid));
    }

    static List<String> command(WindowHotkey.Hotkey hotkey, long ownPid) {
        return PowerShellCommand.of(script(hotkey, ownPid));
    }

    /// A merged-stream line with any PowerShell CLIXML (a progress record
    /// on stderr, which ends without a newline) cut off its front, so the
    /// protocol line glued behind it still parses.
    static String unglued(String line) {
        int end = line.lastIndexOf("</Objs>");
        return end < 0 ? line : line.substring(end + "</Objs>".length());
    }

    /// One stdout line → a press, or null for any other line.
    static @Nullable Press parsePress(String line) {
        if (!line.startsWith(PRESS_PREFIX)) {
            return null;
        }
        String[] parts = line.substring(PRESS_PREFIX.length()).split("\t", 3);
        long pid;
        try {
            pid = Long.parseLong(parts[0].strip());
        } catch (NumberFormatException e) {
            pid = 0;
        }
        return new Press(pid, parts.length > 1 ? parts[1] : "", parts.length > 2 ? parts[2].strip() : "");
    }

    /// One stdout line → the Win32 error of a refused registration, or null.
    static @Nullable Integer parseFailure(String line) {
        if (!line.startsWith(FAILED_PREFIX)) {
            return null;
        }
        try {
            return Integer.parseInt(line.substring(FAILED_PREFIX.length()).strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /// A running listener; closing ends its stdin (the script's quit signal)
    /// and then destroys the process for good measure.
    public record Listener(Process process) implements AutoCloseable {
        @Override
        public void close() {
            try {
                process.getOutputStream().close();
            } catch (java.io.IOException e) {
                // destroyed below anyway
            }
            process.destroy();
        }
    }

    /// Callbacks of [#start], all called from the reader thread.
    public interface Events {
        /// The hotkey is registered and live.
        void registered();

        /// `RegisterHotKey` refused with `errorCode` — or -1: the process
        /// ended before saying either (it did not start: `Add-Type` failed,
        /// `powershell` died), its output logged as a warning.
        void failed(int errorCode);

        /// The hotkey was pressed.
        void pressed(Press press);

        /// The process ended — after [#failed], on [Listener#close], or
        /// unexpectedly; `registered` says whether it ever held the key.
        void ended(boolean registered);
    }

    /// Starts the listener for `hotkey`, or returns null where it cannot run:
    /// off Windows, under the UI tests (a global hotkey there would be the
    /// developer's), or when `powershell` does not start.
    public static @Nullable Listener start(WindowHotkey.Hotkey hotkey, Events events) {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                || Boolean.getBoolean(LocalCommandRunner.NO_DESKTOP_SIDE_EFFECTS)) {
            return null;
        }
        Process process;
        try {
            process = new ProcessBuilder(command(hotkey, ProcessHandle.current().pid()))
                    .redirectErrorStream(true).start();
        } catch (java.io.IOException e) {
            org.tinylog.Logger.warn("No {} hotkey listener: {}", hotkey.label(), e.getMessage());
            return null;
        }
        Thread reader = new Thread(() -> {
            boolean registered = false;
            boolean failed = false;
            StringBuilder noise = new StringBuilder();
            try (var lines = process.inputReader(StandardCharsets.UTF_8)) {
                String raw;
                while ((raw = lines.readLine()) != null) {
                    String line = unglued(raw);
                    if (line.equals(REGISTERED)) {
                        registered = true;
                        events.registered();
                        continue;
                    }
                    Integer failure = parseFailure(line);
                    if (failure != null) {
                        failed = true;
                        events.failed(failure);
                        continue;
                    }
                    Press press = parsePress(line);
                    if (press != null) {
                        events.pressed(press);
                    } else if (!line.isBlank() && !line.startsWith("#< CLIXML") && !line.startsWith("<Objs")) {
                        org.tinylog.Logger.debug("Hotkey listener: {}", line);
                        if (noise.length() < 4000) {
                            noise.append(line).append('\n');
                        }
                    }
                }
            } catch (java.io.IOException e) {
                org.tinylog.Logger.debug("Hotkey listener stream ended: {}", e.getMessage());
            }
            if (!registered && !failed) {
                org.tinylog.Logger.warn("{} hotkey listener ended before registering: {}", hotkey.label(),
                        noise.isEmpty() ? "(no output)" : noise.toString().strip());
                events.failed(-1);
            }
            events.ended(registered);
        }, "window-hotkey");
        reader.setDaemon(true);
        reader.start();
        return new Listener(process);
    }
}
