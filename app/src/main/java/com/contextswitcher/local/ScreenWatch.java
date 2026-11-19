package com.contextswitcher.local;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.contextswitcher.config.ScreenProfiles.ConnectedScreen;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Reports the set of connected screens whenever it changes — what picks the
/// screen-chosen environment profile (`screenProfiles:` in `settings.yaml`)
/// on docking, undocking and plugging in a projector, without a task switch.
///
/// A long-running `powershell` watcher (inline C#, [WindowsEnvironment]'s
/// `CsScreens` compiled alongside) polls the active screens every 500 ms and
/// prints the set only once it has stayed the same for [#STABLE_MS]: waking
/// up docked, Windows reconfigures the displays a dozen times or more within
/// seconds, and applying a profile in between would chase a moving target.
/// An empty set (all screens off, as during standby) is never reported.
/// The watcher exits on its own once the app's process is gone.
///
/// Windows-only: elsewhere [#start] does nothing.
// [impl->dsn~screen-chosen-profile~1]
public class ScreenWatch implements AutoCloseable {

    /// How long the screen set must stay unchanged before it is reported.
    static final int STABLE_MS = 3000;

    private static final String SCRIPT = """
            $ErrorActionPreference = 'Stop'
            Add-Type -TypeDefinition @"
            using System;
            using System.Collections.Generic;
            using System.Diagnostics;
            using System.Runtime.InteropServices;
            using System.Text;
            using System.Threading;
            public static class CsScreenWatch {
              public static void Run(int parentPid, int stableMs) {
                Process parent = null;
                try { parent = Process.GetProcessById(parentPid); } catch { return; }
                string reported = null, last = null;
                long since = 0;
                var clock = Stopwatch.StartNew();
                while (!parent.HasExited) {
                  var sb = new StringBuilder();
                  foreach (var s in CsScreens.Active()) {
                    sb.Append("screen\\t").Append((s.Name ?? "").Replace('\\t', ' ')).Append('\\t').Append(s.Code ?? "")
                      .Append('\\t').Append(s.Internal ? 1 : 0).Append('\\t').Append(s.Primary ? 1 : 0).Append('\\n');
                  }
                  string now = sb.ToString();
                  long tick = clock.ElapsedMilliseconds;
                  if (now != last) { last = now; since = tick; }
                  else if (now != reported && now.Length > 0 && tick - since >= stableMs) {
                    reported = now;
                    Console.Write(now);
                    Console.WriteLine("end");
                    Console.Out.Flush();
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

    public ScreenWatch() {
        this(command -> new ProcessBuilder(command).redirectErrorStream(true).start(),
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"));
    }

    ScreenWatch(Launcher launcher, boolean windows) {
        this.launcher = launcher;
        this.windows = windows;
    }

    static String script(long parentPid) {
        return SCRIPT.replace("__SCREENS__", WindowsEnvironment.screenTypes())
                + "[CsScreenWatch]::Run(%d, %d)\n".formatted(parentPid, STABLE_MS);
    }

    static List<String> command(long parentPid) {
        return PowerShellCommand.of(script(parentPid));
    }

    /// Starts watching; `onChange` gets each stable screen set, the first
    /// one shortly after the start, on the watcher's reader thread.
    public synchronized void start(Consumer<List<ConnectedScreen>> onChange) {
        if (!windows || process != null) {
            return;
        }
        try {
            Process started = launcher.start(command(ProcessHandle.current().pid()));
            process = started;
            Thread reader = new Thread(() -> read(started, onChange), "screen-watch");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException e) {
            Logger.warn("Screen watcher did not start: {}", e.getMessage());
        }
    }

    private static void read(Process watcher, Consumer<List<ConnectedScreen>> onChange) {
        Parser parser = new Parser();
        try (BufferedReader out = new BufferedReader(
                new InputStreamReader(watcher.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = out.readLine()) != null) {
                List<ConnectedScreen> screens = parser.accept(line);
                if (screens != null) {
                    Logger.info("Screens: {}", screens);
                    try {
                        onChange.accept(screens);
                    } catch (RuntimeException e) {
                        Logger.warn(e, "Screen change handling failed");
                    }
                }
            }
        } catch (IOException e) {
            // the process was stopped
        }
    }

    /// Turns the watcher's output into screen sets: `screen<TAB>name<TAB>code<TAB>internal<TAB>primary`
    /// lines, closed by `end`. Anything else (a compiler warning) is logged
    /// and skipped.
    static final class Parser {
        private final List<ConnectedScreen> pending = new ArrayList<>();

        /// The completed set when `line` closes one; null otherwise.
        @Nullable List<ConnectedScreen> accept(String line) {
            if (line.equals("end")) {
                List<ConnectedScreen> done = List.copyOf(pending);
                pending.clear();
                return done;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length == 5 && parts[0].equals("screen")) {
                pending.add(new ConnectedScreen(parts[1].strip(), parts[2].strip(),
                        parts[3].strip().equals("1"), parts[4].strip().equals("1")));
            } else if (!line.isBlank()) {
                Logger.info("Screen watch: {}", line.strip());
            }
            return null;
        }
    }

    @Override
    public synchronized void close() {
        Process running = process;
        if (running != null) {
            running.destroy();
        }
        process = null;
    }
}
