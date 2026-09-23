package com.contextswitcher.tasks;

import java.util.List;

import org.jspecify.annotations.Nullable;

/// The catalog of frontmatter keys the configuration form offers — one [Field]
/// per key a user hand-edits in a task file or a category's
/// `CONTEXTSWITCHER.md`. It is the single source the form is generated from (a
/// control per field, grouped by [Field#group]), the way
/// [com.contextswitcher.config.SettingsCatalog] drives the settings dialog, so
/// a new key is a parser change plus one entry here rather than another
/// hand-wired control.
///
/// Not every key is listed: the ones the app writes for itself
/// (`claude.workspace`, `claude.commit`, `suspended`, `autoPrClosed`,
/// `storedTabs`) and the
/// machine-suffixed variants (`folders-windows`) stay out of the form and are
/// edited — untouched by it — in the raw YAML.
// [impl->dsn~task-field-form~4]
public final class FrontmatterCatalog {

    /// How a field's value is rendered as a form control and read back.
    public enum Type {
        /// A single-line text field; blank means the key is not set.
        STRING,
        /// A checkbox. Unticked removes the key rather than writing `false`.
        BOOLEAN,
        /// A fixed set of values, rendered as a drop-down ([Field#choices]).
        ENUM,
        /// A multi-line field, one list entry per line; empty removes the key.
        LIST
    }

    /// One catalog entry: the frontmatter `key` — top-level, or one level of
    /// nesting as `parent.child` — the form `label` and its `group` section,
    /// the value `type`, one line of `help` shown as the row's tooltip, and,
    /// for a [Type#ENUM], the allowed `choices`.
    public record Field(String key, String label, String group, Type type, String help,
            List<String> choices) {

        /// A non-enum field (no fixed choice set).
        public Field(String key, String label, String group, Type type, String help) {
            this(key, label, group, type, help, List.of());
        }

        /// The `parent` of a nested `parent.child` key, or null for a
        /// top-level one.
        public @Nullable String parent() {
            int dot = key.indexOf('.');
            return dot < 0 ? null : key.substring(0, dot);
        }

        /// The `child` of a nested key, or the whole key when it is top-level.
        public String child() {
            return key.substring(key.indexOf('.') + 1);
        }

        /// The control's node id. Dots become dashes so the id stays usable as
        /// a CSS selector (`#field-intellij-projectPath`).
        public String controlId() {
            return "field-" + key.replace('.', '-');
        }
    }

    /// The `browser.urls` line format: the address, optionally followed by
    /// this separator and a short human title (`Task.UrlEntry`).
    public static final String URL_TITLE_SEPARATOR = " — ";

    /// The keys of a task file, in form order.
    public static final List<Field> TASK = List.of(
            new Field("title", "Title", "Task", Type.STRING,
                    "The name shown in the task list."),
            new Field("status", "Status", "Task", Type.ENUM,
                    "active runs, suspended keeps the last screen, done archives the task.",
                    List.of("active", "suspended", "done")),
            // [impl->dsn~pinned-tasks~2]
            new Field("pinned", "Pinned", "Task", Type.BOOLEAN,
                    "Lifts the task above the unpinned ones of its status block."),
            // [impl->dsn~task-tag-model~2]
            new Field("tags", "Tags", "Task", Type.LIST,
                    "One tag per line; the palette and its colors are configured in settings."),
            new Field("remote", "Remote", "Session", Type.STRING,
                    "ssh alias or user@host — used by every remote action of the task."),
            new Field("tmux.session", "tmux session", "Session", Type.STRING,
                    "Session holding the task's window, e.g. 0."),
            new Field("tmux.window", "tmux window", "Session", Type.STRING,
                    "Window id (@17 — survives renumbering), or a window name/index."),
            // [impl->dsn~local-terminal-focus~5]
            new Field("terminal.tabTitle", "Windows Terminal tab", "Session", Type.STRING,
                    "Local terminal tab to focus instead of a remote tmux window "
                            + "(the title pinned via \"Rename Tab\")."),
            // [impl->dsn~claude-session-capture~3]
            new Field("claude.cwd", "Claude directory", "Session", Type.STRING,
                    "Where the Claude session was started — enough to resume it after a reboot."),
            new Field("claude.sessionId", "Claude session id", "Session", Type.STRING,
                    "Enables claude --resume; normally captured by the app itself."),
            // [impl->dsn~open-in-intellij~3]
            new Field("intellij", "Open in IntelliJ", "Switch actions", Type.BOOLEAN,
                    "Opens the project on every switch to this task."),
            new Field("intellij.projectPath", "IntelliJ project path", "Switch actions",
                    Type.STRING,
                    "Project directory; empty falls back to the Claude session's workspace."),
            // [impl->dsn~browser-url-title~1]
            new Field("browser.urls", "Browser URLs", "Switch actions", Type.LIST,
                    "One URL per line, optionally followed by \"" + URL_TITLE_SEPARATOR
                            + "\" and a short title."),
            // [impl->dsn~explorer-folder-focus~3]
            new Field("folders", "Local folders", "Switch actions", Type.LIST,
                    "Folders opened in the file manager on switch; one per line."),
            new Field("chat", "Chat room", "Switch actions", Type.STRING,
                    "Matrix room opened in the Element app — a matrix.to permalink."),
            new Field("note", "Note link", "Switch actions", Type.STRING,
                    "Opened by \"Open note\"; for OneNote use the onenote:… link form."));

    /// The keys of a category's `CONTEXTSWITCHER.md`, in form order.
    public static final List<Field> CATEGORY = List.of(
            // [impl->dsn~pinned-categories~1]
            new Field("pinned", "Pinned", "Category", Type.BOOLEAN,
                    "Lifts the category above the unpinned ones in the task list."),
            // [impl->dsn~auto-delete-opt-in~1]
            new Field("autoDelete", "Delete tasks automatically", "Category", Type.BOOLEAN,
                    "Merged, expired auto-PR and closed plain-shell tasks are deleted "
                            + "instead of kept; pinned tasks never are."),
            new Field("tags", "Tags", "Category", Type.LIST,
                    "Inherited by every task in the category; one per line."),
            // [impl->dsn~category-desktop-focus~4]
            new Field("desktop", "Virtual desktop", "Category", Type.STRING,
                    "Name of the Windows virtual desktop this category lives on — "
                            + "the header's ▶ button focuses it."),
            // [impl->dsn~complete-control-desktop~1]
            new Field("completeControl", "Clear that desktop on suspend", "Category",
                    Type.BOOLEAN,
                    "Suspending a task stores and closes every Firefox window on the desktop; "
                            + "resuming reopens the stored tabs."),
            new Field("note", "Note link", "Category", Type.STRING,
                    "Opened by the header's \"Open note\"; for OneNote use the onenote:… form."),
            new Field("chat", "Chat room", "Category", Type.STRING,
                    "Matrix room opened on every switch into the category "
                            + "(a task's own chat wins)."),
            new Field("repo", "GitHub repository", "Repository", Type.STRING,
                    "The category's GitHub repository, opened by the header's repository icon "
                            + "and named in a live task's bootstrap prompt."),
            new Field("workspacesRoot", "Workspaces root", "Repository", Type.STRING,
                    "Where a task's Claude session starts; Claude creates each task's own "
                            + "working directory under it. Normally the only one of the two."),
            new Field("workdir", "Fixed working directory", "Repository", Type.STRING,
                    "An existing checkout every task reuses as it is, instead of a "
                            + "per-task worktree under the workspaces root."),
            // [impl->dsn~diff-after-worktree-removal~1]
            new Field("mainCheckout", "Main checkout", "Repository", Type.STRING,
                    "A permanent checkout of the repository — \"Show diff\" falls back to it "
                            + "once a task's own worktree is gone."),
            // [impl->dsn~refactoring-miner-commands~1]
            new Field("baseBranch", "Base branch", "Repository", Type.STRING,
                    "What the task worktrees branch from, and what the refactoring view diffs "
                            + "against; empty means " + GroupConfig.DEFAULT_BASE_BRANCH + "."),
            // [impl->dsn~auto-pr-category~3]
            new Field("auto.query", "PR search", "Automatic PR tasks", Type.STRING,
                    "GitHub search whose open PRs become tasks here, e.g. "
                            + "repo:JabRef/jabref review-requested:@me; empty = off."),
            new Field("auto.maxSloc", "Size limit (non-test lines)", "Automatic PR tasks",
                    Type.STRING,
                    "Skip a PR changing more lines than this outside its tests; "
                            + "empty or 0 = no size limit."),
            new Field("auto.deleteHours", "Delete after (hours)", "Automatic PR tasks",
                    Type.STRING,
                    "How long a task is kept after its PR left the search, if the category "
                            + "deletes automatically; 0 = kept. "
                            + "Empty means " + GroupConfig.AutoPr.DEFAULT_DELETE_HOURS + "."),
            new Field("remote", "Remote", "Defaults for its tasks", Type.STRING,
                    "ssh alias or user@host a new task in the category inherits."),
            // [impl->dsn~category-action-defaults~1]
            new Field("intellij", "Open in IntelliJ", "Defaults for its tasks", Type.BOOLEAN,
                    "Used by every task of the category that configures no intellij section."),
            new Field("intellij.projectPath", "IntelliJ project path", "Defaults for its tasks",
                    Type.STRING,
                    "Project directory; empty falls back to the task's Claude workspace."),
            new Field("browser.urls", "Browser URLs", "Defaults for its tasks", Type.LIST,
                    "One URL per line, optionally followed by \"" + URL_TITLE_SEPARATOR
                            + "\" and a short title."),
            new Field("folders", "Local folders", "Defaults for its tasks", Type.LIST,
                    "Folders opened in the file manager on switch; one per line."));

    private FrontmatterCatalog() {
    }
}
