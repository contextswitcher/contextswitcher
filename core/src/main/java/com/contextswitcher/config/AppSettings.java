package com.contextswitcher.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.contextswitcher.tasks.TextFiles;

/// Application settings in `<configDir>/settings.yaml` (default config dir:
/// `%USERPROFILE%\.contextswitcher`). Created with defaults — including a
/// randomly generated WebSocket token — on first run. `hosts` is the machines
/// sessions run on ([HostSettings]: the ssh destination, whether it is switched
/// on, its workspaces and RefactoringMiner directories); their destinations are
/// what the tmux sync scans (none by default; the sync then falls back to the
/// distinct remotes of existing tasks). `tags` is the tag palette
/// (name + display color) the task-tag filter offers and colors rows by.
/// `hints` (default true) picks the intro flavor of generated task files —
/// commented example sections and a provenance note under `# Notes` — for
/// onboarding; false generates compact files carrying only the real keys.
/// `claudeAuto` (default false) starts every Claude session ContextSwitcher
/// launches with `--dangerously-skip-permissions`, so a fresh live task
/// bootstraps without permission prompts (`dsn~claude-auto-permissions~1`).
/// `theme` (default `everforest`) picks the AtlantaFX color theme: `light`,
/// `dark`, or `system` — follow the OS's light/dark preference — each also in an
/// `everforest` recolouring (`dsn~theme-select~9`).
/// `showOnAllDesktops` (default false) pins the main window to every Windows
/// virtual desktop at startup — the Task View pin is per window handle and
/// lost on exit, so it is re-applied here (`dsn~window-desktop-pin~2`).
/// `refactoringMinerPort` is the loopback port the RefactoringMiner web view is
/// served and tunnelled on (`dsn~refactoring-web-view~1`); the directory of the
/// release is per host (`HostSettings.refactoringMiner`).
/// `readlineKeys` (default false) turns the bash/emacs cursor chords — Ctrl+A,
/// Ctrl+E, Alt+B, … — on in every text input (`dsn~readline-keys~1`).
/// `fallbackDesktop` (default `misc`) is the virtual desktop a category falls
/// back to: one that names no `desktop:`, or names one that does not exist on
/// this machine. Empty turns the fallback off (`dsn~fallback-desktop~2`).
/// `browser` (default `firefox`) is the browser the Windows helpers raise,
/// launch, and capture windows of; the extension itself needs no such setting,
/// since it dials in (`dsn~browser-choice~2`).
/// `mergedCleanupDays` (default 7) is the grace period of the merged-task
/// janitor; 0 turns it off (`dsn~merged-task-cleanup~2`).
/// `autoCopyReplies` (default false) puts each finished Claude reply of the
/// mirrored chat on the clipboard as Markdown (`dsn~terminal-markdown-copy~1`).
/// Files written before `hosts` existed carry `remotes`, `inactiveRemotes` and
/// one global `refactoringMinerHome` instead; [#parse(String)] reads them into
/// hosts (the global directory for every one of them), and [#loadOrCreate(Path)]
/// rewrites the file with `hosts` in their place (`dsn~host-table~1`).
// [impl->dsn~app-settings~5]
// [impl->dsn~theme-select~9]
// [impl->dsn~window-desktop-pin~2]
// [impl->dsn~refactoring-web-view~1]
// [impl->dsn~readline-keys~1]
// [impl->dsn~fallback-desktop~2]
// [impl->dsn~auto-suspend-idle~2]
// [impl->dsn~browser-choice~2]
// [impl->dsn~merged-task-cleanup~2]
// [impl->dsn~terminal-markdown-copy~1]
// [impl->dsn~host-on-off~2]
// [impl->dsn~host-table~1]
public record AppSettings(Path tasksDir, int wsPort, String wsToken, List<HostSettings> hosts,
        List<TagDef> tags, boolean hints, boolean claudeAuto, String theme,
        boolean showOnAllDesktops, int refactoringMinerPort, boolean readlineKeys,
        String fallbackDesktop, int autoSuspendMinutes, Browser browser, int mergedCleanupDays,
        boolean autoCopyReplies) {

    /// The settings keys `hosts` replaced — read from an older file, removed
    /// from it when the settings dialog writes `hosts`.
    public static final List<String> LEGACY_HOST_KEYS =
            List.of("remotes", "inactiveRemotes", "refactoringMinerHome");

    /// Default idle time after which an active Claude task is suspended on
    /// its own — two days, so a task left over the weekend is still live on
    /// Monday and resumes instantly; 0 turns it off.
    // [impl->dsn~auto-suspend-idle~2]
    public static final int DEFAULT_AUTO_SUSPEND_MINUTES = 2880;

    /// Default grace period of the merged-task janitor: a task whose pull
    /// requests are all merged and whose last screen still wants a human is
    /// cleaned up once it has been paused this long; 0 turns the janitor off.
    // [impl->dsn~merged-task-cleanup~2]
    public static final int DEFAULT_MERGED_CLEANUP_DAYS = 7;

    /// The valid `theme` values: the Nord palette or its Everforest
    /// recolouring (`dsn~everforest-theme~2`), each forced light or dark or —
    /// as the bare name — following the operating system's setting.
    // [impl->dsn~theme-select~9]
    public static final List<String> THEMES =
            List.of("system", "light", "dark", "everforest", "everforest-light", "everforest-dark");
    /// The default theme — Everforest, following the OS's light/dark setting.
    public static final String DEFAULT_THEME = "everforest";

    /// One entry of the tag palette: a tag `name` (spaces allowed) and an optional
    /// `color` (a CSS hex like `#2da44e`) its chips render in — a null color
    /// falls back to a muted gray chip.
    // [impl->dsn~task-tag-model~2]
    public record TagDef(String name, @org.jspecify.annotations.Nullable String color) {
    }

    public static final int DEFAULT_WS_PORT = 17872;
    /// The default `refactoringMinerPort` — RM's own web-view default (https://github.com/contextswitcher/contextswitcher-private/issues/50).
    public static final int DEFAULT_REFACTORING_MINER_PORT = 6789;
    /// The default `fallbackDesktop` — the desktop unconfigured (or misnamed)
    /// categories are focused on. [impl->dsn~fallback-desktop~2]
    public static final String DEFAULT_FALLBACK_DESKTOP = "misc";
    private static final String FILE_NAME = "settings.yaml";

    /// Settings with `remotes` as active hosts and defaults for the rest —
    /// the first-run file and tests.
    public AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes) {
        this(tasksDir, wsPort, wsToken, remotes, List.of());
    }

    /// [#AppSettings(Path, int, String, List)] with a tag palette.
    public AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes,
            List<TagDef> tags) {
        this(tasksDir, wsPort, wsToken, remotes.stream().map(HostSettings::of).toList(), tags,
                true, false, DEFAULT_THEME, false, DEFAULT_REFACTORING_MINER_PORT, false,
                DEFAULT_FALLBACK_DESKTOP, DEFAULT_AUTO_SUSPEND_MINUTES, Browser.DEFAULT,
                DEFAULT_MERGED_CLEANUP_DAYS, false);
    }

    public AppSettings {
        // One entry per destination — the first wins, as when reading the
        // file top to bottom.
        List<HostSettings> unique = new java.util.ArrayList<>();
        for (HostSettings host : hosts) {
            if (!host.destination().isEmpty()
                    && unique.stream().noneMatch(known -> known.is(host.destination()))) {
                unique.add(host);
            }
        }
        hosts = List.copyOf(unique);
        tags = List.copyOf(tags);
        theme = normalizeTheme(theme);
        fallbackDesktop = fallbackDesktop.strip();
    }

    /// Every host's ssh destination, in the file's order — what the tmux sync
    /// scans and the task dialogs offer, switched-off ones included.
    public List<String> remotes() {
        return hosts.stream().map(HostSettings::destination).toList();
    }

    /// The destinations of the switched-off hosts. [impl->dsn~host-on-off~2]
    public List<String> inactiveRemotes() {
        return hosts.stream().filter(host -> !host.active()).map(HostSettings::destination).toList();
    }

    /// The entry for `destination`, or null when no host has it.
    public @org.jspecify.annotations.Nullable HostSettings host(
            @org.jspecify.annotations.Nullable String destination) {
        return hosts.stream().filter(host -> host.is(destination)).findFirst().orElse(null);
    }

    /// The RefactoringMiner directory on `destination`, or null when that host
    /// has none (the refactoring view and badge are off there).
    // [impl->dsn~refactoring-web-view~1]
    public @org.jspecify.annotations.Nullable String refactoringMinerHome(
            @org.jspecify.annotations.Nullable String destination) {
        HostSettings host = host(destination);
        return host == null ? null : host.refactoringMiner();
    }

    /// Whether any host has a RefactoringMiner directory — the refactoring
    /// badge's poller runs only then.
    public boolean anyRefactoringMiner() {
        return hosts.stream().anyMatch(host -> host.refactoringMiner() != null);
    }

    /// A copy whose hosts are `replaced`.
    public AppSettings withHosts(List<HostSettings> replaced) {
        return new AppSettings(tasksDir, wsPort, wsToken, replaced, tags, hints, claudeAuto, theme,
                showOnAllDesktops, refactoringMinerPort, readlineKeys, fallbackDesktop,
                autoSuspendMinutes, browser, mergedCleanupDays, autoCopyReplies);
    }

    /// `hosts` with `destination`'s entry passed through `change` — or, when
    /// there is none yet, a new active entry for it passed through `change`
    /// and appended. The building block of every one-host edit (the filter
    /// menu's switch, the wizard, the asked workspaces directory).
    public List<HostSettings> hostsWith(String destination,
            java.util.function.UnaryOperator<HostSettings> change) {
        List<HostSettings> out = new java.util.ArrayList<>(hosts);
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).is(destination)) {
                out.set(i, change.apply(out.get(i)));
                return out;
            }
        }
        out.add(change.apply(HostSettings.of(destination)));
        return out;
    }

    /// `raw` settings text with `hosts` written in and the legacy keys it
    /// replaces taken out — everything else, comments included, untouched.
    // [impl->dsn~host-table~1]
    public static String patchHosts(String raw, List<HostSettings> hosts) {
        String out = raw;
        for (String key : LEGACY_HOST_KEYS) {
            out = YamlPatch.remove(out, key);
        }
        return YamlPatch.set(out, "hosts", hosts.stream().map(HostSettings::toYaml).toList());
    }

    /// `hosts` as the list-of-maps shape the YAML carries.
    public List<Map<String, Object>> hostMaps() {
        return hosts.stream().map(HostSettings::toYaml).toList();
    }

    /// Normalizes a theme value to one of [#THEMES], falling back to the default
    /// for anything unknown (a hand-edited file, an older/newer value).
    // [impl->dsn~theme-select~9]
    private static String normalizeTheme(@org.jspecify.annotations.Nullable String theme) {
        if (theme == null) {
            return DEFAULT_THEME;
        }
        String normalized = theme.trim().toLowerCase(java.util.Locale.ROOT);
        return THEMES.contains(normalized) ? normalized : DEFAULT_THEME;
    }

    public static Path defaultConfigDir() {
        return Path.of(System.getProperty("user.home"), ".contextswitcher");
    }

    /// Loads settings from `configDir`, creating the file with defaults on first run.
    public static AppSettings loadOrCreate(Path configDir) throws IOException {
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            AppSettings defaults = new AppSettings(
                    configDir.resolve("tasks"), DEFAULT_WS_PORT, generateToken(), List.of());
            defaults.store(configDir);
            return defaults;
        }
        try {
            String raw = TextFiles.read(file);
            AppSettings settings = parse(raw);
            // An older file is rewritten in the `hosts` shape on load, so
            // the file says what the app works with. [impl->dsn~host-table~1]
            String migrated = migratedHosts(raw);
            if (migrated != null) {
                TextFiles.write(file, migrated);
                org.tinylog.Logger.info("Moved remotes/inactiveRemotes/refactoringMinerHome of {} "
                        + "into hosts", file);
            }
            return settings;
        } catch (IOException e) {
            // Prefix the source so a hand-broken file names itself in the error.
            throw new IOException("%s: %s".formatted(file, e.getMessage()), e);
        }
    }

    /// `raw` with the keys `hosts` replaced moved into it — the hosts
    /// [#parse(String)] reads, the legacy keys removed, everything else
    /// untouched — or null when `raw` has none of them (nothing to do).
    // [impl->dsn~host-table~1]
    static @org.jspecify.annotations.Nullable String migratedHosts(String raw) throws IOException {
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(raw);
        if (!(loaded instanceof Map<?, ?> map) || LEGACY_HOST_KEYS.stream().noneMatch(map::containsKey)) {
            return null;
        }
        return patchHosts(raw, parse(raw).hosts());
    }

    /// Parses settings from a raw YAML string — the same field reading
    /// [#loadOrCreate(Path)] does, factored out so the in-app settings editor
    /// can round-trip its form to and from the file text without touching disk.
    // [impl->dsn~settings-editor~4]
    public static AppSettings parse(String yaml) throws IOException {
        Yaml parser = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object loaded = parser.load(yaml);
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IOException("expected a YAML mapping");
        }
        Object tasksDir = map.get("tasksDir");
        Object wsPort = map.get("wsPort");
        Object wsToken = map.get("wsToken");
        if (tasksDir == null || wsPort == null || wsToken == null) {
            throw new IOException("tasksDir, wsPort, and wsToken are required");
        }
        if (!(wsPort instanceof Integer port)) {
            throw new IOException("wsPort must be an integer");
        }
        List<HostSettings> hosts = parseHosts(map);
        // An absent or non-boolean `hints` means intro mode — the safe
        // default for a hand-edited file.
        boolean hints = !(map.get("hints") instanceof Boolean flag) || flag;
        // Skipping permission prompts is opt-in: only an explicit `true` counts.
        // [impl->dsn~claude-auto-permissions~1]
        boolean claudeAuto = map.get("claudeAuto") instanceof Boolean auto && auto;
        // An absent/unknown theme normalizes to the default in the constructor.
        Object theme = map.get("theme");
        // The all-desktops pin is opt-in: only an explicit `true` counts.
        // [impl->dsn~window-desktop-pin~2]
        boolean showOnAllDesktops = map.get("showOnAllDesktops") instanceof Boolean pin && pin;
        // [impl->dsn~refactoring-web-view~1]
        int rmPort = map.get("refactoringMinerPort") instanceof Integer p
                ? p : DEFAULT_REFACTORING_MINER_PORT;
        // The readline chords take Ctrl+A away from select-all, so they are
        // opt-in: only an explicit `true` counts. [impl->dsn~readline-keys~1]
        boolean readlineKeys = map.get("readlineKeys") instanceof Boolean chords && chords;
        // An absent key keeps the default fallback; an explicit empty string
        // turns the fallback off. [impl->dsn~fallback-desktop~2]
        Object fallbackDesktop = map.get("fallbackDesktop");
        // Absent = the default hour; a non-integer reads as off, like 0.
        // [impl->dsn~auto-suspend-idle~2]
        int autoSuspend = map.get("autoSuspendMinutes") instanceof Integer m
                ? m : map.get("autoSuspendMinutes") == null ? DEFAULT_AUTO_SUSPEND_MINUTES : 0;
        // Absent/unknown = Firefox, the browser the extension shipped for first.
        // [impl->dsn~browser-choice~2]
        Browser browser = Browser.of(map.get("browser"));
        // Absent = the default week; a non-integer reads as off, like 0.
        // [impl->dsn~merged-task-cleanup~2]
        int mergedCleanup = map.get("mergedCleanupDays") instanceof Integer d
                ? d : map.get("mergedCleanupDays") == null ? DEFAULT_MERGED_CLEANUP_DAYS : 0;
        // Overwriting the clipboard unasked is opt-in: only an explicit `true`
        // counts. [impl->dsn~terminal-markdown-copy~1]
        boolean autoCopyReplies = map.get("autoCopyReplies") instanceof Boolean copy && copy;
        return new AppSettings(Path.of(tasksDir.toString()), port, wsToken.toString(), hosts,
                parseTags(map.get("tags")), hints, claudeAuto,
                theme == null ? DEFAULT_THEME : theme.toString(), showOnAllDesktops,
                rmPort, readlineKeys,
                fallbackDesktop == null ? DEFAULT_FALLBACK_DESKTOP : fallbackDesktop.toString(),
                autoSuspend, browser, mergedCleanup, autoCopyReplies);
    }

    /// The `hosts` list — or, in a file from before it, the hosts the legacy
    /// keys describe: every `remotes` entry, switched off when
    /// `inactiveRemotes` names it, then the switched-off hosts `remotes` did
    /// not list (a task's remote can be switched off without being
    /// configured), each with the one global `refactoringMinerHome`, which
    /// used to name the directory on all of them.
    // [impl->dsn~host-table~1]
    // [impl->dsn~host-on-off~2]
    private static List<HostSettings> parseHosts(Map<?, ?> map) {
        if (map.get("hosts") instanceof List<?> list) {
            return list.stream().map(HostSettings::fromYaml)
                    .filter(java.util.Objects::nonNull).toList();
        }
        List<String> remotes = strings(map.get("remotes"));
        List<String> inactive = strings(map.get("inactiveRemotes"));
        Object rmHome = map.get("refactoringMinerHome");
        String home = rmHome == null ? null : rmHome.toString();
        List<HostSettings> hosts = new java.util.ArrayList<>();
        for (String remote : remotes) {
            boolean off = inactive.stream().anyMatch(remote::equalsIgnoreCase);
            hosts.add(new HostSettings(remote, !off, null, home));
        }
        for (String remote : inactive) {
            if (remotes.stream().noneMatch(remote::equalsIgnoreCase)) {
                hosts.add(new HostSettings(remote, false, null, home));
            }
        }
        return hosts;
    }

    private static List<String> strings(Object value) {
        return value instanceof List<?> list ? list.stream().map(Object::toString).toList() : List.of();
    }

    /// Reads the `tags` palette: a list of `{name, color}` maps. The `color`
    /// is optional (a color-less tag renders as a muted gray chip). Tolerant —
    /// entries missing a name are skipped rather than failing the whole load
    /// (the file is hand-edited configuration).
    // [impl->dsn~task-tag-model~2]
    private static List<TagDef> parseTags(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<TagDef> tags = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> entry && entry.get("name") != null) {
                Object color = entry.get("color");
                tags.add(new TagDef(entry.get("name").toString(),
                        color == null ? null : color.toString()));
            }
        }
        return List.copyOf(tags);
    }

    public void store(Path configDir) throws IOException {
        Files.createDirectories(configDir);
        TextFiles.write(configDir.resolve(FILE_NAME), dump());
    }

    /// Renders these settings as the `settings.yaml` text — the exact bytes
    /// [#store(Path)] writes. Used by the in-app editor to turn its form back
    /// into file text (raw view and Save).
    // [impl->dsn~settings-editor~4]
    public String dump() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tasksDir", tasksDir.toString());
        data.put("wsPort", wsPort);
        data.put("wsToken", wsToken);
        data.put("hints", hints);
        data.put("claudeAuto", claudeAuto);
        data.put("theme", theme);
        data.put("showOnAllDesktops", showOnAllDesktops);
        data.put("fallbackDesktop", fallbackDesktop);
        data.put("readlineKeys", readlineKeys);
        data.put("autoSuspendMinutes", autoSuspendMinutes);
        data.put("mergedCleanupDays", mergedCleanupDays);
        data.put("autoCopyReplies", autoCopyReplies);
        data.put("browser", browser.key());
        data.put("refactoringMinerPort", refactoringMinerPort);
        data.put("hosts", hostMaps());
        data.put("tags", tagMaps());
        return new Yaml().dumpAsMap(data);
    }

    /// The tag palette as the list-of-maps shape (`name`, optional `color`) the
    /// YAML carries — shared by [#dump()] and [#yamlValue(String)].
    private List<Map<String, Object>> tagMaps() {
        List<Map<String, Object>> tagList = new java.util.ArrayList<>();
        for (TagDef tag : tags) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", tag.name());
            if (tag.color() != null) {
                entry.put("color", tag.color());
            }
            tagList.add(entry);
        }
        return tagList;
    }

    /// The dump-ready value of a single settings `key` (as [#dump()] would emit
    /// it) — the in-app editor patches one key at a time into the raw YAML text,
    /// so it renders just that key's value rather than the whole file.
    // [impl->dsn~settings-editor~4]
    public Object yamlValue(String key) {
        return switch (key) {
            case "tasksDir" -> tasksDir.toString();
            case "wsPort" -> wsPort;
            case "wsToken" -> wsToken;
            case "hints" -> hints;
            case "claudeAuto" -> claudeAuto;
            case "theme" -> theme;
            case "showOnAllDesktops" -> showOnAllDesktops;
            case "fallbackDesktop" -> fallbackDesktop;
            case "readlineKeys" -> readlineKeys;
            case "autoSuspendMinutes" -> autoSuspendMinutes;
            case "mergedCleanupDays" -> mergedCleanupDays;
            case "autoCopyReplies" -> autoCopyReplies;
            case "browser" -> browser.key();
            case "refactoringMinerPort" -> refactoringMinerPort;
            case "hosts" -> hostMaps();
            case "tags" -> tagMaps();
            default -> throw new IllegalArgumentException("unknown settings key: " + key);
        };
    }

    /// The commented settings.yaml field reference for the settings editor's
    /// F1 help — every key [#loadOrCreate(Path)] reads, with placeholders.
    /// Lives next to the parser so the help cannot drift far from what is
    /// actually read (guarded by a keys-match unit test).
    // [impl->dsn~settings-editor~4]
    public static String settingsReference() {
        return """
                tasksDir: C:\\Users\\you\\.contextswitcher\\tasks   # where the task .md files live
                browser: firefox             # browser the Windows helpers raise/launch/capture windows of: firefox or chrome
                wsPort: 17872                # loopback WebSocket port the browser extension connects to
                wsToken: <generated>         # shared secret; keep the generated value
                hints: true                  # onboarding hints in generated task files; false = compact (real keys only)
                claudeAuto: false            # true = start/resume Claude with --dangerously-skip-permissions (trust your remotes)
                theme: everforest            # UI color theme: system/light/dark (Nord) or everforest/everforest-light/everforest-dark; the bare names follow the OS
                showOnAllDesktops: false     # true = pin the window to all Windows virtual desktops at startup (each desktop then remembers its own window position)
                fallbackDesktop: misc        # virtual desktop for categories with no `desktop:` (or one that does not exist); empty = no fallback
                readlineKeys: false          # true = bash cursor chords in text inputs (Ctrl+A/E/B/F/D/H/K/U/W, Alt+B/F/D); Ctrl+A is then line start, not select all
                autoSuspendMinutes: 2880     # suspend an active Claude task whose chat has been idle this long (48 h) (its window is ended, resume recreates it); 0 = never
                mergedCleanupDays: 7         # a task whose PRs are all merged is cleaned up (window, transcript, worktree, file) — right away when its last screen needs no human, else once paused this long; 0 = never
                autoCopyReplies: false       # true = each finished reply of the mirrored Claude chat is put on the clipboard as Markdown
                refactoringMinerPort: 6789   # loopback port the refactoring web view is served and tunnelled on
                hosts:                       # machines sessions run on; their destinations are what the tmux sync scans (none: it falls back to remotes of existing tasks)
                  - destination: koppor@devbox             # ssh destination, user part included (or an alias, or wsl:<distro>)
                    active: true                           # false = switched off: never polled, its tasks and categories hidden (also the filter menu's Hosts submenu)
                    workspaces: /home/koppor/workspaces    # where new workspaces roots go on that host; asked when the host is added and on its first category
                    refactoringMiner: /home/koppor/.contextswitcher/RefactoringMiner-3.1.4   # unzipped RefactoringMiner there; omitted = refactoring view/badge off for that host
                tags:                        # tag palette for the task-tag filter and row chips
                  - name: jabref             # tag name (spaces allowed), referenced from a task's `tags:` line
                    color: "#2da44e"         # optional CSS hex; omitted = muted gray chip
                  - name: phone
                    color: "#bf3989"
                profiles:                    # how the machine looks per situation; a category picks one with `profile:`, the status bar can override it
                  default:                   # the system's normal look: every category without `profile:`, and the fallback for keys a profile leaves out
                    resolution:              # per screen, <width>x<height>[@<scale>%] (Windows' "Scale", 100% when left out); a bare `resolution: 2560x1440` means the primary screen
                      internal: 2880x1800@125%   # the laptop panel
                      DELL U4025QW: 5120x2160   # a monitor by EDID name or product code (DEL4308); name/code beats primary beats internal/external
                      external: 2560x1440    # any other external screen; screens no rule matches are left alone
                    displays: extend         # Win+P mode: internal (PC screen only), clone (duplicate), extend, external (second screen only)
                    taskbar: visible         # visible or autohide
                    bookmarksBar: visible    # Firefox bookmarks toolbar: visible or hidden (needs the userChrome.css rule, see README)
                  lecture:
                    resolution: {external: 1920x1080}   # the projector; merged per screen over default's
                    displays: clone
                    taskbar: autohide
                    bookmarksBar: hidden
                """;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
