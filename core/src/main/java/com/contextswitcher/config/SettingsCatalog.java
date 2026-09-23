package com.contextswitcher.config;

import java.util.List;

/// The catalog of every user-facing `settings.yaml` option — one [Option] per
/// key [AppSettings] reads. It is the single source the in-app settings editor
/// generates its form from (a control per option, grouped by [Option#group]),
/// so adding a setting is a matter of adding a getter/writer in [AppSettings]
/// and one entry here rather than hand-wiring another control.
// [impl->dsn~settings-editor~4]
public final class SettingsCatalog {

    /// How an option's value is rendered as a form control and read back.
    public enum Type {
        /// A single-line text field (paths, hosts, the WebSocket token).
        STRING,
        /// A whole-number field (validated by the field — bad text keeps the
        /// last valid value on persist).
        INTEGER,
        /// A checkbox.
        BOOLEAN,
        /// A multi-line field, one list entry per line (the ssh remotes).
        STRING_LIST,
        /// A multi-line field, one `name  #rrggbb` tag per line (color optional).
        TAG_LIST,
        /// A fixed set of values, rendered as a drop-down (`Option#choices`).
        ENUM
    }

    /// One catalog entry: the YAML `key` (matching an [AppSettings] getter), the
    /// form `label` and its `group` section, the value `type`, one line of `help`
    /// shown under the field, and — for an [Type#ENUM] — the allowed `choices`.
    public record Option(String key, String label, String group, Type type, String help,
            List<String> choices) {

        /// A non-enum option (no fixed choice set).
        public Option(String key, String label, String group, Type type, String help) {
            this(key, label, group, type, help, List.of());
        }
    }

    /// Every option, in display order; `group` collects them into form sections.
    public static final List<Option> OPTIONS = List.of(
            new Option("tasksDir", "Tasks directory", "General", Type.STRING,
                    "Folder holding the task .md files (watched)."),
            new Option("hints", "Onboarding hints in generated task files", "General", Type.BOOLEAN,
                    "Off writes compact task files carrying only the real keys."),
            new Option("claudeAuto", "Start Claude with --dangerously-skip-permissions",
                    "General", Type.BOOLEAN,
                    "Skips permission prompts for launched Claude sessions — trust your remotes."),
            new Option("theme", "Theme", "General", Type.ENUM,
                    "UI color theme. everforest (the default) and its light/dark variants "
                            + "recolour the Nord palette of system/light/dark "
                            + "(the terminal follows either way).",
                    AppSettings.THEMES),
            new Option("showOnAllDesktops", "Show window on all desktops", "General", Type.BOOLEAN,
                    "Pins the window to every Windows virtual desktop at startup; "
                            + "each desktop then remembers its own window position."),
            // [impl->dsn~fallback-desktop~1]
            new Option("fallbackDesktop", "Fallback desktop", "General", Type.STRING,
                    "Virtual desktop used for a category that names no desktop, or one that "
                            + "does not exist; empty turns the fallback off."),
            // [impl->dsn~readline-keys~1]
            new Option("readlineKeys", "Bash cursor keys in text fields", "General", Type.BOOLEAN,
                    "Ctrl+A/E line start/end, Ctrl+B/F and Alt+B/F move, Ctrl+K/U/W and Alt+D "
                            + "delete — Ctrl+A is then no longer select-all."),
            // [impl->dsn~auto-suspend-idle~2]
            new Option("autoSuspendMinutes", "Auto-suspend idle tasks after (minutes)", "General",
                    Type.INTEGER,
                    "An active Claude task whose chat has been idle this long is suspended on its "
                            + "own — the window is ended, its last screen kept; sending a message "
                            + "resumes it. Default 2880 (48 h); 0 turns this off. Applies on save: "
                            + "one due task per status poll, counted down in the status bar."),
            // [impl->dsn~merged-task-cleanup~2]
            new Option("mergedCleanupDays", "Remove merged tasks after (days)", "General",
                    Type.INTEGER,
                    "A task whose pull requests are all merged is removed on its own — window "
                            + "ended, transcript and worktree deleted, task file committed with "
                            + "its last screen and then deleted. A last screen that still wants a "
                            + "human waits until the task has been paused this long. 0 turns this "
                            + "off."),
            // [impl->dsn~terminal-markdown-copy~1]
            new Option("autoCopyReplies", "Copy each finished Claude reply as Markdown", "General",
                    Type.BOOLEAN,
                    "When the mirrored chat finishes a reply, its Markdown replaces the "
                            + "clipboard."),
            // [impl->dsn~browser-choice~2]
            new Option("browser", "Browser", "Browser bridge", Type.ENUM,
                    "The browser whose windows are raised, launched, and — on a complete-control "
                            + "desktop — captured. The extension itself connects on its own, so "
                            + "either one's may be installed.",
                    Browser.KEYS),
            new Option("wsPort", "WebSocket port", "Browser bridge", Type.INTEGER,
                    "Loopback port the browser extension connects to."),
            new Option("wsToken", "WebSocket token", "Browser bridge", Type.STRING,
                    "Shared secret with the extension — generated once; change only to re-pair."),
            new Option("remotes", "Remotes", "Remotes", Type.STRING_LIST,
                    "ssh destinations the tmux sync scans; one per line."),
            // [impl->dsn~refactoring-web-view~1]
            new Option("refactoringMinerHome", "RefactoringMiner directory", "Refactoring insight",
                    Type.STRING,
                    "Unzipped RefactoringMiner release on the remotes; empty turns the "
                            + "refactoring view and badge off."),
            new Option("refactoringMinerPort", "Refactoring view port", "Refactoring insight",
                    Type.INTEGER,
                    "Loopback port the AST-diff web view is served and tunnelled on."),
            new Option("tags", "Tag palette", "Tags", Type.TAG_LIST,
                    "Filter/chip palette; one per line: name, then an optional #rrggbb color."));

    private SettingsCatalog() {
    }
}
