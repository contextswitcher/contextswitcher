package com.contextswitcher.local;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.jspecify.annotations.Nullable;

/// Switches Windows to a **named virtual desktop**, driven through `powershell`.
/// Windows exposes no *supported* API to activate a desktop by name or index, so
/// the target is always resolved from the registry and then reached in one of
/// two ways — a direct COM jump when the running build's interface is the one we
/// know, the built-in hotkey walk otherwise.
///
/// The desktop order lives in `HKCU:\…\Explorer\VirtualDesktops\VirtualDesktopIDs`
/// (a packed array of 16-byte GUIDs); each desktop the user has renamed carries
/// its label under `…\Desktops\{GUID}\Name`, and `CurrentVirtualDesktop` holds
/// the active desktop's GUID. The script finds the target's index **and GUID** by
/// name (case-insensitive) and the current index.
///
/// It then tries `IVirtualDesktopManagerInternal::SwitchDesktop` — the interface
/// the shell itself uses, reached through the immersive shell's `IServiceProvider`
/// — which jumps straight to the desktop with no walk through the ones between.
/// That interface is undocumented and Microsoft rebuilds its vtable (and IID)
/// across Windows builds, so only the current layout is declared. A build with a
/// different IID makes `QueryService` fail with `E_NOINTERFACE` — a clean miss,
/// never a wrong vtable slot — and the script falls back to injecting
/// `Ctrl+Win+Left/Right` the signed index difference of times via `keybd_event`,
/// a global hotkey the shell honors regardless of which window is in front, so no
/// foreground-stealing dance is needed (unlike [LocalFolderFocus]).
///
/// The script is passed as a base64 `-EncodedCommand`, so there is no shell
/// quoting to get wrong; the only escaping is doubling `'` for the name's
/// single-quoted literal.
///
/// Windows-only and undocumented-registry-backed by nature; a Linux/GNOME
/// analog (`wmctrl -s` / `gdbus`) is future work. Best-effort: no `powershell`,
/// or a Windows too old to name desktops (pre-1809), fails the call with a
/// logged detail rather than throwing.
// ponytail: only the current (Windows 11 24H2) vtable layout is declared, not a
//   per-build table like MScholtes/VirtualDesktop ships; every other build lands
//   on the hotkey walk, which is exactly the old behaviour. Add a second
//   interface declaration only if a build people actually run walks. See MADR 0019 (superseding MADR 0013).
// [impl->dsn~category-desktop-focus~4]
public class WindowsVirtualDesktopFocus {

    /// Outcome of a switch attempt; `detail` is the story for the status bar.
    public record FocusResult(boolean ok, String detail) {
    }

    private static final String SCRIPT_TEMPLATE = """
            $ErrorActionPreference = 'Stop'
            Add-Type @"
            using System;
            using System.Runtime.InteropServices;
            public class CsVd {
              [DllImport("user32.dll")] public static extern void keybd_event(byte k, byte s, uint f, IntPtr e);
            }
            "@
            $target = '__NAME__'
            $base = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\VirtualDesktops'
            $ids = (Get-ItemProperty -Path $base -Name VirtualDesktopIDs).VirtualDesktopIDs
            $curBytes = Get-CurrentBytes $base
            $current = if ($curBytes) { [Guid]::new([byte[]]$curBytes) } else { $null }
            $count = [int]($ids.Length / 16)
            $targetIndex = -1
            $targetGuid = $null
            $currentIndex = -1
            for ($i = 0; $i -lt $count; $i++) {
              $guid = [Guid]::new([byte[]]($ids[($i * 16)..($i * 16 + 15)]))
              if ($current -ne $null -and $guid -eq $current) { $currentIndex = $i }
              $key = $base + '\\Desktops\\{' + $guid.ToString() + '}'
              $name = (Get-ItemProperty -Path $key -Name Name -ErrorAction SilentlyContinue).Name
              if ($name -and ($name -ieq $target)) { $targetIndex = $i; $targetGuid = $guid }
            }
            if ($targetIndex -lt 0) { Write-Output ("not found: " + $target); exit 2 }
            # The direct jump, when this build's interface is the one we declare.
            if (Invoke-DirectSwitch $targetGuid) { Write-Output ("focused-direct: " + $target); exit 0 }
            # Unknown current desktop: step fully left first (a no-op at desktop 0),
            # so the following rightward steps land deterministically on the target.
            if ($currentIndex -lt 0) {
              for ($i = 0; $i -lt $count; $i++) { Send-Switch 0x25 }
              $currentIndex = 0
            }
            $delta = $targetIndex - $currentIndex
            $vk = if ($delta -ge 0) { 0x27 } else { 0x25 }
            for ($i = 0; $i -lt [Math]::Abs($delta); $i++) { Send-Switch $vk }
            Write-Output ("focused-walk: " + $target)
            exit 0
            """;

    /// The direct switch: `IVirtualDesktopManagerInternal::SwitchDesktop`, the
    /// call Task View itself makes, obtained by asking the immersive shell's
    /// `IServiceProvider` for the internal virtual-desktop service.
    ///
    /// The interface is undocumented: its IID and vtable layout are rebuilt
    /// across Windows builds, so the declaration below pins the **Windows 11
    /// 24H2** layout (IID `53F5CA0B-…`, as reverse-engineered by
    /// `MScholtes/VirtualDesktop`). Any other build answers `QueryService` with
    /// `E_NOINTERFACE`, because the IID moves whenever the vtable does — so a
    /// mismatched build fails the `QueryService` call instead of dispatching to
    /// the wrong slot, and `$false` sends the caller to the hotkey walk.
    ///
    /// Only the slots up to `FindDesktop` are declared, and only the two we call
    /// carry real signatures — for a COM vtable a method's *position* is what
    /// binds it, so the rest are position-holders.
    private static final String DIRECT_FUNCTION = """
            function Invoke-DirectSwitch([Guid]$id) {
              try {
                Add-Type -TypeDefinition @"
            using System;
            using System.Runtime.InteropServices;
            [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("6D5140C1-7436-11CE-8034-00AA006009FA")]
            public interface ICsShell {
              [return: MarshalAs(UnmanagedType.IUnknown)] object QueryService(ref Guid service, ref Guid riid);
            }
            [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("53F5CA0B-158F-4124-900C-057158060B27")]
            public interface ICsVdmInternal {
              void GetCount();
              void MoveViewToDesktop();
              void CanViewMoveDesktops();
              void GetCurrentDesktop();
              void GetDesktops();
              void GetAdjacentDesktop();
              void SwitchDesktop(IntPtr desktop);
              void SwitchDesktopAndMoveForegroundView();
              void CreateDesktop();
              void MoveDesktop();
              void RemoveDesktop();
              IntPtr FindDesktop(ref Guid id);
            }
            public class CsVdCom {
              static readonly Guid ImmersiveShell = new Guid("C2F03A33-21F5-47FA-B4BB-156362A2F239");
              static readonly Guid VdmInternal = new Guid("C5E0CDCA-7B6E-41B2-9FC4-D93975CC467B");
              public static void Switch(Guid id) {
                var shell = (ICsShell)Activator.CreateInstance(Type.GetTypeFromCLSID(ImmersiveShell));
                Guid service = VdmInternal;
                Guid iid = typeof(ICsVdmInternal).GUID;
                var manager = (ICsVdmInternal)shell.QueryService(ref service, ref iid);
                IntPtr desktop = manager.FindDesktop(ref id);
                if (desktop == IntPtr.Zero) { throw new Exception("desktop not found"); }
                manager.SwitchDesktop(desktop);
              }
            }
            "@
                [CsVdCom]::Switch($id)
                return $true
              } catch {
                return $false
              }
            }
            """;

    /// Reads the active desktop's GUID **from the registry** — Explorer's mirror
    /// of what the shell knows, and the only route left when the COM interface
    /// this build declares does not match the running Windows.
    ///
    /// Where that mirror lives moved between Windows versions: Windows 11 keeps
    /// it as `CurrentVirtualDesktop` next to `VirtualDesktopIDs`, Windows 10
    /// keeps it per logon session under
    /// `…\Explorer\SessionInfo\{session}\VirtualDesktops`. Windows 10 has *no*
    /// value in the Windows 11 place, so reading only there made every read on
    /// Windows 10 fall through to "no switch happened yet" and report desktop 0
    /// — the app permanently believed the user was on the first desktop, no
    /// matter which one was in view. Both places are read, the Windows 11 one
    /// first; an empty result means neither exists (no switch since login).
    ///
    /// The session number is the PowerShell process's own (`$PID`), which is the
    /// user's interactive session — the same one Explorer writes under.
    /// The result is returned with a leading `,` so PowerShell hands back the
    /// byte array itself rather than unrolling it onto the pipeline.
    private static final String REGISTRY_CURRENT_FUNCTION = """
            function Get-CurrentBytes([string]$base) {
              $bytes = (Get-ItemProperty -Path $base -Name CurrentVirtualDesktop -ErrorAction SilentlyContinue).CurrentVirtualDesktop
              if ($bytes) { return ,$bytes }
              $session = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\SessionInfo\\' `
                + (Get-Process -Id $PID).SessionId + '\\VirtualDesktops'
              $bytes = (Get-ItemProperty -Path $session -Name CurrentVirtualDesktop -ErrorAction SilentlyContinue).CurrentVirtualDesktop
              if ($bytes) { return ,$bytes }
              return $null
            }
            """;

    /// The `Send-Switch` helper is prepended so the template above can call it;
    /// PowerShell needs the function defined before use in a plain script body.
    private static final String SWITCH_FUNCTION = """
            function Send-Switch([byte]$vk) {
              [CsVd]::keybd_event(0x11, 0, 0, [IntPtr]::Zero)  # Ctrl down
              [CsVd]::keybd_event(0x5B, 0, 0, [IntPtr]::Zero)  # Win down
              [CsVd]::keybd_event($vk, 0, 0, [IntPtr]::Zero)   # arrow down
              [CsVd]::keybd_event($vk, 0, 2, [IntPtr]::Zero)   # arrow up
              [CsVd]::keybd_event(0x5B, 0, 2, [IntPtr]::Zero)  # Win up
              [CsVd]::keybd_event(0x11, 0, 2, [IntPtr]::Zero)  # Ctrl up
              Start-Sleep -Milliseconds 80
            }
            """;

    private final LocalCommandRunner runner;
    /// The desktop a name that matches nothing falls back to (`fallbackDesktop`
    /// in `settings.yaml`); empty = no fallback, a miss just fails.
    // [impl->dsn~fallback-desktop~1]
    private final String fallback;

    public WindowsVirtualDesktopFocus(LocalCommandRunner runner) {
        this(runner, "");
    }

    public WindowsVirtualDesktopFocus(LocalCommandRunner runner, String fallback) {
        this.runner = runner;
        this.fallback = fallback.strip();
    }

    /// Doubles `'` so the desktop name is a safe PowerShell single-quoted literal.
    static String escape(String name) {
        return name.replace("'", "''");
    }

    /// The PowerShell script that switches to the named virtual desktop. Both
    /// helper functions are defined first, then the resolve-then-switch body
    /// (which the Add-Type inside it sets up before the first `Send-Switch` call).
    static String script(String name) {
        return SWITCH_FUNCTION + "\n" + DIRECT_FUNCTION + "\n" + REGISTRY_CURRENT_FUNCTION + "\n"
                + SCRIPT_TEMPLATE.replace("__NAME__", escape(name));
    }

    /// The local argv: the script as a base64 `-EncodedCommand` (UTF-16LE).
    static List<String> command(String name) {
        String encoded = Base64.getEncoder().encodeToString(
                script(name).getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    /// Switches to the virtual desktop named `name`. `ok` is false when the name
    /// matches no desktop (exit 2) or the mechanism is unavailable (no
    /// `powershell`, registry keys missing). A successful switch says which route
    /// it took, so a build that has moved the COM interface on is visible in the
    /// status bar rather than only as a slower animation.
    public FocusResult focus(String name) {
        LocalCommandRunner.LocalResult result = runner.run(command(name));
        if (result.exitCode() == 0) {
            return new FocusResult(true, focused(name, result));
        }
        if (result.exitCode() == 2) {
            // A configured desktop that does not exist on this machine lands on
            // the fallback rather than nowhere. [impl->dsn~fallback-desktop~1]
            if (!fallback.isBlank() && !fallback.equalsIgnoreCase(name)) {
                LocalCommandRunner.LocalResult retry = runner.run(command(fallback));
                if (retry.exitCode() == 0) {
                    return new FocusResult(true, "%s (no desktop \"%s\")"
                            .formatted(focused(fallback, retry), name));
                }
                return new FocusResult(false,
                        "desktop not found: \"%s\", nor the fallback \"%s\"".formatted(name, fallback));
            }
            return new FocusResult(false, "desktop not found: \"%s\"".formatted(name));
        }
        String detail = result.stderr().isBlank()
                ? "powershell exit " + result.exitCode()
                : result.stderr().strip();
        return new FocusResult(false, detail);
    }

    /// The status-bar line of a successful switch, saying which route ran.
    private static String focused(String name, LocalCommandRunner.LocalResult result) {
        String route = result.stdout().strip().startsWith("focused-walk") ? " (hotkey walk)" : "";
        return "focused desktop \"%s\"%s".formatted(name, route);
    }

    /// Reads the **active** virtual desktop's name. The GUID comes from the
    /// shell itself first — `IVirtualDesktopManagerInternal::GetCurrentDesktop`
    /// through the same pinned 24H2 vtable the direct switch uses (the
    /// placeholder slot 3 there, carrying its real signature here), the
    /// desktop's identity via `IVirtualDesktop::GetID`. The registry copy
    /// (`CurrentVirtualDesktop`) is only Explorer's lazily written mirror: it
    /// goes stale after programmatic switches (including our own direct COM
    /// switch), so trusting it first narrowed the list to the *wrong* desktop.
    /// It remains the fallback for builds whose interface moved
    /// (`QueryService` fails on a mismatched IID, never dispatches wrongly —
    /// which is every Windows 10, whose shell interface predates the pinned
    /// 24H2 one), read from both places it can live via
    /// [#REGISTRY_CURRENT_FUNCTION]; a missing value in *either* means no
    /// switch happened since login, so the first GUID in `VirtualDesktopIDs`
    /// is the desktop in view (the same assumption the focus script's walk
    /// makes). The GUID's label is read
    /// from `…\Desktops\{GUID}\Name`: printed with exit 0, or exit 2 when the
    /// desktop has no name (unrenamed).
    private static final String CURRENT_SCRIPT = """
            $ErrorActionPreference = 'Stop'
            $base = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\VirtualDesktops'
            function Get-CurrentGuid {
              try {
                Add-Type -TypeDefinition @"
            __TYPES__
            "@
                return [CsVdCur]::Current()
              } catch {
                return $null
              }
            }
            $cur = Get-CurrentGuid
            if (-not $cur) {
              $curBytes = Get-CurrentBytes $base
              if (-not $curBytes) {
                $ids = (Get-ItemProperty -Path $base -Name VirtualDesktopIDs).VirtualDesktopIDs
                $curBytes = [byte[]]($ids[0..15])
              }
              $cur = [Guid]::new([byte[]]$curBytes)
            }
            $key = $base + '\\Desktops\\{' + $cur.ToString() + '}'
            $name = (Get-ItemProperty -Path $key -Name Name -ErrorAction SilentlyContinue).Name
            if ($name) { Write-Output $name; exit 0 }
            exit 2
            """;

    /// The C# behind both current-desktop reads — declared once so the pinned
    /// vtable cannot drift between the one-shot script and the watcher.
    private static final String CURRENT_COM_TYPES = """
            using System;
            using System.Runtime.InteropServices;
            [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("6D5140C1-7436-11CE-8034-00AA006009FA")]
            public interface ICsShell {
              [return: MarshalAs(UnmanagedType.IUnknown)] object QueryService(ref Guid service, ref Guid riid);
            }
            [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("53F5CA0B-158F-4124-900C-057158060B27")]
            public interface ICsVdmInternal {
              void GetCount();
              void MoveViewToDesktop();
              void CanViewMoveDesktops();
              IntPtr GetCurrentDesktop();
              void GetDesktops();
              void GetAdjacentDesktop();
              void SwitchDesktop(IntPtr desktop);
              void SwitchDesktopAndMoveForegroundView();
              void CreateDesktop();
              void MoveDesktop();
              void RemoveDesktop();
              IntPtr FindDesktop(ref Guid id);
            }
            [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("3F07F4BE-B107-441A-AF0F-39D82529072C")]
            public interface ICsVdesk {
              void IsViewVisible();
              Guid GetID();
            }
            public class CsVdCur {
              static readonly Guid ImmersiveShell = new Guid("C2F03A33-21F5-47FA-B4BB-156362A2F239");
              static readonly Guid VdmInternal = new Guid("C5E0CDCA-7B6E-41B2-9FC4-D93975CC467B");
              public static Guid Current() {
                var shell = (ICsShell)Activator.CreateInstance(Type.GetTypeFromCLSID(ImmersiveShell));
                Guid service = VdmInternal;
                Guid iid = typeof(ICsVdmInternal).GUID;
                var manager = (ICsVdmInternal)shell.QueryService(ref service, ref iid);
                var desktop = (ICsVdesk)Marshal.GetObjectForIUnknown(manager.GetCurrentDesktop());
                return desktop.GetID();
              }
            }
            """;

    /// The PowerShell script that prints the active virtual desktop's name —
    /// the registry helper first, since a plain script body needs a function
    /// defined before it is called.
    static String currentScript() {
        return REGISTRY_CURRENT_FUNCTION + "\n"
                + CURRENT_SCRIPT.replace("__TYPES__", CURRENT_COM_TYPES.strip());
    }

    /// The local argv reading the active desktop name (base64 `-EncodedCommand`).
    static List<String> currentCommand() {
        String encoded = Base64.getEncoder().encodeToString(
                currentScript().getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    /// One read of the active virtual desktop for the "show active desktop only"
    /// filter. `read` says whether the registry was actually consulted — false
    /// on a mechanism failure (no `powershell`, non-Windows, timeout), where the
    /// caller must keep its last known desktop instead of silently un-narrowing
    /// the list. A successful read's `name` is null on an unrenamed desktop.
    public record ActiveDesktop(boolean read, @Nullable String name) {
    }

    /// Exit code of [#CURRENT_SCRIPT] when the active desktop has no name.
    private static final int EXIT_UNNAMED = 2;

    /// Reads the active virtual desktop's name — from the shell itself where
    /// the pinned COM interface matches, else from the registry.
    // [impl->dsn~active-desktop-filter~6]
    public ActiveDesktop current() {
        LocalCommandRunner.LocalResult result = runner.run(currentCommand());
        if (result.exitCode() == EXIT_UNNAMED) {
            return new ActiveDesktop(true, null);
        }
        if (result.exitCode() != 0) {
            return new ActiveDesktop(false, null);
        }
        String name = result.stdout().strip();
        return new ActiveDesktop(true, name.isEmpty() ? null : name);
    }

    /// The **persistent** watcher: the same read as [#CURRENT_SCRIPT], but in a
    /// long-running process that compiles the COM shim **once** (`Add-Type` at
    /// the top — a second `Add-Type` of the same types throws, which inside the
    /// loop would silently demote every later tick to the registry) and then
    /// checks the desktop in-process every [#WATCH_INTERVAL_MILLIS], printing
    /// a [#WATCH_PREFIX] line whenever the name changes. That turns
    /// switch-detection latency from poll interval + `powershell` start +
    /// Roslyn compile (seconds) into at most the sleep interval.
    ///
    /// stdout is forced to UTF-8 so a non-ASCII desktop name survives the
    /// pipe to the UTF-8 reader on the Java side; `[Console]::Out` auto-flushes,
    /// so a change line is visible immediately. A tick that cannot read at all
    /// prints nothing — the keep-last-known contract of [#current].
    // [impl->dsn~active-desktop-filter~6]
    private static final String WATCH_SCRIPT = """
            $ErrorActionPreference = 'Stop'
            [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
            $base = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\VirtualDesktops'
            $comOk = $true
            try {
              Add-Type -TypeDefinition @"
            __TYPES__
            "@
            } catch { $comOk = $false }
            function Get-CurrentName {
              $cur = $null
              if ($comOk) { try { $cur = [CsVdCur]::Current() } catch { $cur = $null } }
              if (-not $cur) {
                $curBytes = Get-CurrentBytes $base
                if (-not $curBytes) {
                  $ids = (Get-ItemProperty -Path $base -Name VirtualDesktopIDs).VirtualDesktopIDs
                  $curBytes = [byte[]]($ids[0..15])
                }
                $cur = [Guid]::new([byte[]]$curBytes)
              }
              $key = $base + '\\Desktops\\{' + $cur.ToString() + '}'
              return (Get-ItemProperty -Path $key -Name Name -ErrorAction SilentlyContinue).Name
            }
            $last = $null
            while ($true) {
              try {
                $line = 'desktop:' + (Get-CurrentName)
                if ($line -ne $last) { [Console]::Out.WriteLine($line); $last = $line }
              } catch { }
              Start-Sleep -Milliseconds 250
            }
            """;

    /// Marks a watcher stdout line carrying the active desktop's name; anything
    /// else on the (merged) stream is PowerShell noise and is ignored.
    static final String WATCH_PREFIX = "desktop:";

    /// The watcher's in-process check interval — the new worst-case latency for
    /// noticing an externally triggered desktop switch.
    static final int WATCH_INTERVAL_MILLIS = 250;

    /// The watcher script: registry helper first (functions before use), then
    /// the compile-once + check-loop body.
    static String watchScript() {
        return REGISTRY_CURRENT_FUNCTION + "\n"
                + WATCH_SCRIPT.replace("__TYPES__", CURRENT_COM_TYPES.strip());
    }

    /// The local argv for the watcher (base64 `-EncodedCommand`).
    static List<String> watchCommand() {
        String encoded = Base64.getEncoder().encodeToString(
                watchScript().getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    /// One watcher stdout line → a successful read, or null for non-protocol
    /// noise. An empty name after the prefix is the unnamed-desktop case.
    static @Nullable ActiveDesktop parseWatchLine(String line) {
        if (!line.startsWith(WATCH_PREFIX)) {
            return null;
        }
        String name = line.substring(WATCH_PREFIX.length()).strip();
        return new ActiveDesktop(true, name.isEmpty() ? null : name);
    }

    /// A running watcher; closing destroys the `powershell` process (which ends
    /// the reader thread through end-of-stream).
    public record Watch(Process process) implements AutoCloseable {
        @Override
        public void close() {
            process.destroy();
        }
    }

    /// Starts the persistent watcher, feeding every successful read to `onRead`
    /// (from a daemon reader thread — the callback marshals to the UI thread
    /// itself) and calling `onExit` once when the process ends for any reason,
    /// so the caller can fall back to one-shot polling. Returns null where the
    /// process cannot start at all (no `powershell`, non-Windows).
    // [impl->dsn~active-desktop-filter~6]
    public static @Nullable Watch watch(java.util.function.Consumer<ActiveDesktop> onRead,
            Runnable onExit) {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return null;
        }
        Process process;
        try {
            process = new ProcessBuilder(watchCommand()).redirectErrorStream(true).start();
        } catch (java.io.IOException e) {
            org.tinylog.Logger.debug("No active-desktop watcher: {}", e.getMessage());
            return null;
        }
        Thread reader = new Thread(() -> {
            try (var lines = process.inputReader(StandardCharsets.UTF_8)) {
                process.getOutputStream().close();
                String line;
                while ((line = lines.readLine()) != null) {
                    ActiveDesktop read = parseWatchLine(line);
                    if (read != null) {
                        onRead.accept(read);
                    }
                }
            } catch (java.io.IOException e) {
                org.tinylog.Logger.debug("Active-desktop watcher stream ended: {}", e.getMessage());
            }
            onExit.run();
        }, "active-desktop-watch");
        reader.setDaemon(true);
        reader.start();
        return new Watch(process);
    }
}
