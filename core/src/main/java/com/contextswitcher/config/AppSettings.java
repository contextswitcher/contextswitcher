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
/// randomly generated WebSocket token — on first run. `remotes` is the ssh
/// destinations the tmux sync scans (empty by default; the sync then falls
/// back to the distinct remotes of existing tasks). `tags` is the tag palette
/// (name + display color) the task-tag filter offers and colors rows by.
/// `hints` (default true) picks the intro flavor of generated task files —
/// commented example sections and a provenance note under `# Notes` — for
/// onboarding; false generates compact files carrying only the real keys.
/// `claudeAuto` (default false) starts every Claude session ContextSwitcher
/// launches with `--dangerously-skip-permissions`, so a fresh live task
/// bootstraps without permission prompts (`dsn~claude-auto-permissions~1`).
/// `theme` (default `everforest`) picks the AtlantaFX color theme: `light`,
/// `dark`, or `system` — follow the OS's light/dark preference — each also in an
/// `everforest` recolouring (`dsn~theme-select~8`).
/// `showOnAllDesktops` (default false) pins the main window to every Windows
/// virtual desktop at startup — the Task View pin is per window handle and
/// lost on exit, so it is re-applied here (`dsn~window-desktop-pin~2`).
/// `refactoringMinerHome` is the directory of the unzipped RefactoringMiner
/// release on the remotes (null/blank = the refactoring view and badge are
/// off, https://github.com/contextswitcher/contextswitcher-private/issues/50) and `refactoringMinerPort` the loopback port its web view is
/// served and tunnelled on (`dsn~refactoring-web-view~1`).
/// `readlineKeys` (default false) turns the bash/emacs cursor chords — Ctrl+A,
/// Ctrl+E, Alt+B, … — on in every text input (`dsn~readline-keys~1`).
/// `fallbackDesktop` (default `misc`) is the virtual desktop a category falls
/// back to: one that names no `desktop:`, or names one that does not exist on
/// this machine. Empty turns the fallback off (`dsn~fallback-desktop~1`).
/// `browser` (default `firefox`) is the browser the Windows helpers raise,
/// launch, and capture windows of; the extension itself needs no such setting,
/// since it dials in (`dsn~browser-choice~2`).
/// `mergedCleanupDays` (default 7) is the grace period of the merged-task
/// janitor; 0 turns it off (`dsn~merged-task-cleanup~2`).
/// `autoCopyReplies` (default false) puts each finished Claude reply of the
/// mirrored chat on the clipboard as Markdown (`dsn~terminal-markdown-copy~1`).
// [impl->dsn~app-settings~5]
// [impl->dsn~theme-select~8]
// [impl->dsn~window-desktop-pin~2]
// [impl->dsn~refactoring-web-view~1]
// [impl->dsn~readline-keys~1]
// [impl->dsn~fallback-desktop~1]
// [impl->dsn~auto-suspend-idle~2]
// [impl->dsn~browser-choice~2]
// [impl->dsn~merged-task-cleanup~2]
// [impl->dsn~terminal-markdown-copy~1]
public record AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes,
        List<TagDef> tags, boolean hints, boolean claudeAuto, String theme,
        boolean showOnAllDesktops, @org.jspecify.annotations.Nullable String refactoringMinerHome,
        int refactoringMinerPort, boolean readlineKeys, String fallbackDesktop,
        int autoSuspendMinutes, Browser browser, int mergedCleanupDays, boolean autoCopyReplies) {

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
    // [impl->dsn~theme-select~8]
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
    /// categories are focused on. [impl->dsn~fallback-desktop~1]
    public static final String DEFAULT_FALLBACK_DESKTOP = "misc";
    private static final String FILE_NAME = "settings.yaml";

    /// Back-compat constructor without a tag palette (defaults to empty).
    public AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes) {
        this(tasksDir, wsPort, wsToken, remotes, List.of(), true, false);
    }

    /// Back-compat constructor without the hints flag (defaults to true).
    public AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes,
            List<TagDef> tags) {
        this(tasksDir, wsPort, wsToken, remotes, tags, true, false);
    }

    /// Back-compat constructor without the claudeAuto flag (defaults to false).
    public AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes,
            List<TagDef> tags, boolean hints) {
        this(tasksDir, wsPort, wsToken, remotes, tags, hints, false);
    }

    /// Back-compat constructor without the theme (defaults to `system`).
    public AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes,
            List<TagDef> tags, boolean hints, boolean claudeAuto) {
        this(tasksDir, wsPort, wsToken, remotes, tags, hints, claudeAuto, DEFAULT_THEME);
    }

    /// Back-compat constructor without the all-desktops pin (defaults to false).
    public AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes,
            List<TagDef> tags, boolean hints, boolean claudeAuto, String theme) {
        this(tasksDir, wsPort, wsToken, remotes, tags, hints, claudeAuto, theme, false);
    }

    /// Back-compat constructor without the RefactoringMiner keys (feature off,
    /// default port).
    public AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes,
            List<TagDef> tags, boolean hints, boolean claudeAuto, String theme,
            boolean showOnAllDesktops) {
        this(tasksDir, wsPort, wsToken, remotes, tags, hints, claudeAuto, theme,
                showOnAllDesktops, null, DEFAULT_REFACTORING_MINER_PORT);
    }

    /// Back-compat constructor without the readline chords (off).
    public AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes,
            List<TagDef> tags, boolean hints, boolean claudeAuto, String theme,
            boolean showOnAllDesktops, @org.jspecify.annotations.Nullable String refactoringMinerHome,
            int refactoringMinerPort) {
        this(tasksDir, wsPort, wsToken, remotes, tags, hints, claudeAuto, theme,
                showOnAllDesktops, refactoringMinerHome, refactoringMinerPort, false);
    }

    /// Back-compat constructor without the fallback desktop (defaults to `misc`).
    public AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes,
            List<TagDef> tags, boolean hints, boolean claudeAuto, String theme,
            boolean showOnAllDesktops, @org.jspecify.annotations.Nullable String refactoringMinerHome,
            int refactoringMinerPort, boolean readlineKeys) {
        this(tasksDir, wsPort, wsToken, remotes, tags, hints, claudeAuto, theme,
                showOnAllDesktops, refactoringMinerHome, refactoringMinerPort, readlineKeys,
                DEFAULT_FALLBACK_DESKTOP);
    }

    /// Back-compat constructor without the auto-suspend (default 60 min).
    public AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes,
            List<TagDef> tags, boolean hints, boolean claudeAuto, String theme,
            boolean showOnAllDesktops, @org.jspecify.annotations.Nullable String refactoringMinerHome,
            int refactoringMinerPort, boolean readlineKeys, String fallbackDesktop) {
        this(tasksDir, wsPort, wsToken, remotes, tags, hints, claudeAuto, theme,
                showOnAllDesktops, refactoringMinerHome, refactoringMinerPort, readlineKeys,
                fallbackDesktop, DEFAULT_AUTO_SUSPEND_MINUTES);
    }

    /// Back-compat constructor without the browser choice (defaults to Firefox).
    public AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes,
            List<TagDef> tags, boolean hints, boolean claudeAuto, String theme,
            boolean showOnAllDesktops, @org.jspecify.annotations.Nullable String refactoringMinerHome,
            int refactoringMinerPort, boolean readlineKeys, String fallbackDesktop,
            int autoSuspendMinutes) {
        this(tasksDir, wsPort, wsToken, remotes, tags, hints, claudeAuto, theme,
                showOnAllDesktops, refactoringMinerHome, refactoringMinerPort, readlineKeys,
                fallbackDesktop, autoSuspendMinutes, Browser.DEFAULT);
    }

    /// Back-compat constructor without the merged-task janitor (default 7 days).
    public AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes,
            List<TagDef> tags, boolean hints, boolean claudeAuto, String theme,
            boolean showOnAllDesktops, @org.jspecify.annotations.Nullable String refactoringMinerHome,
            int refactoringMinerPort, boolean readlineKeys, String fallbackDesktop,
            int autoSuspendMinutes, Browser browser) {
        this(tasksDir, wsPort, wsToken, remotes, tags, hints, claudeAuto, theme,
                showOnAllDesktops, refactoringMinerHome, refactoringMinerPort, readlineKeys,
                fallbackDesktop, autoSuspendMinutes, browser, DEFAULT_MERGED_CLEANUP_DAYS);
    }

    /// Back-compat constructor without the reply auto-copy (off).
    public AppSettings(Path tasksDir, int wsPort, String wsToken, List<String> remotes,
            List<TagDef> tags, boolean hints, boolean claudeAuto, String theme,
            boolean showOnAllDesktops, @org.jspecify.annotations.Nullable String refactoringMinerHome,
            int refactoringMinerPort, boolean readlineKeys, String fallbackDesktop,
            int autoSuspendMinutes, Browser browser, int mergedCleanupDays) {
        this(tasksDir, wsPort, wsToken, remotes, tags, hints, claudeAuto, theme,
                showOnAllDesktops, refactoringMinerHome, refactoringMinerPort, readlineKeys,
                fallbackDesktop, autoSuspendMinutes, browser, mergedCleanupDays, false);
    }

    public AppSettings {
        remotes = List.copyOf(remotes);
        tags = List.copyOf(tags);
        theme = normalizeTheme(theme);
        // Blank means unset — the settings form's empty text field and an
        // absent key are the same "feature off".
        if (refactoringMinerHome != null && refactoringMinerHome.isBlank()) {
            refactoringMinerHome = null;
        }
        fallbackDesktop = fallbackDesktop.strip();
    }

    /// Normalizes a theme value to one of [#THEMES], falling back to the default
    /// for anything unknown (a hand-edited file, an older/newer value).
    // [impl->dsn~theme-select~8]
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
            return parse(TextFiles.read(file));
        } catch (IOException e) {
            // Prefix the source so a hand-broken file names itself in the error.
            throw new IOException("%s: %s".formatted(file, e.getMessage()), e);
        }
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
        List<String> remotes = List.of();
        if (map.get("remotes") instanceof List<?> list) {
            remotes = list.stream().map(Object::toString).toList();
        }
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
        // Absent/blank home = the RefactoringMiner integration is off; the
        // constructor normalizes blank to null. [impl->dsn~refactoring-web-view~1]
        Object rmHome = map.get("refactoringMinerHome");
        int rmPort = map.get("refactoringMinerPort") instanceof Integer p
                ? p : DEFAULT_REFACTORING_MINER_PORT;
        // The readline chords take Ctrl+A away from select-all, so they are
        // opt-in: only an explicit `true` counts. [impl->dsn~readline-keys~1]
        boolean readlineKeys = map.get("readlineKeys") instanceof Boolean chords && chords;
        // An absent key keeps the default fallback; an explicit empty string
        // turns the fallback off. [impl->dsn~fallback-desktop~1]
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
        return new AppSettings(Path.of(tasksDir.toString()), port, wsToken.toString(), remotes,
                parseTags(map.get("tags")), hints, claudeAuto,
                theme == null ? DEFAULT_THEME : theme.toString(), showOnAllDesktops,
                rmHome == null ? null : rmHome.toString(), rmPort, readlineKeys,
                fallbackDesktop == null ? DEFAULT_FALLBACK_DESKTOP : fallbackDesktop.toString(),
                autoSuspend, browser, mergedCleanup, autoCopyReplies);
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
        data.put("refactoringMinerHome", refactoringMinerHome == null ? "" : refactoringMinerHome);
        data.put("refactoringMinerPort", refactoringMinerPort);
        data.put("remotes", remotes);
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
            case "refactoringMinerHome" -> refactoringMinerHome == null ? "" : refactoringMinerHome;
            case "refactoringMinerPort" -> refactoringMinerPort;
            case "remotes" -> remotes;
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
                refactoringMinerHome: ''     # unzipped RefactoringMiner directory on the remotes; empty = refactoring view/badge off
                refactoringMinerPort: 6789   # loopback port the refactoring web view is served and tunnelled on
                remotes:                     # ssh destinations the tmux sync scans
                  - devbox                   # empty list: sync falls back to remotes of existing tasks
                tags:                        # tag palette for the task-tag filter and row chips
                  - name: jabref             # tag name (spaces allowed), referenced from a task's `tags:` line
                    color: "#2da44e"         # optional CSS hex; omitted = muted gray chip
                  - name: phone
                    color: "#bf3989"
                """;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
