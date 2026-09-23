package com.contextswitcher.ui;

import java.util.concurrent.CompletableFuture;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import atlantafx.base.theme.Styles;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import com.dlsc.gemsfx.infocenter.Notification;
import com.dlsc.gemsfx.infocenter.NotificationGroup;
import com.contextswitcher.analysis.RefactoringSummary;
import com.contextswitcher.config.AppSettings;
import com.contextswitcher.config.Browser;
import com.contextswitcher.config.EnergySaver;
import com.contextswitcher.discovery.ClaudeSessionCleanup;
import com.contextswitcher.local.AppUpdate;
import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.local.WindowsVirtualDesktopFocus;
import com.contextswitcher.queue.Attachments;
import com.contextswitcher.queue.ClaudeMode;
import com.contextswitcher.queue.QueueFile;
import com.contextswitcher.tasks.AutoPrReconcile;
import com.contextswitcher.tasks.Frontmatter;
import com.contextswitcher.tasks.GroupConfig;
import com.contextswitcher.tasks.MergedCleanup;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskEntry;
import com.contextswitcher.switching.AutoPrLookup;
import com.contextswitcher.switching.AutoPrPoller;
import com.contextswitcher.switching.GitLabMrLookup;
import com.contextswitcher.switching.PrInfo;
import com.contextswitcher.switching.PrStatusGroup;
import com.contextswitcher.switching.PrState;
import com.contextswitcher.switching.PrTitleLookup;
import com.contextswitcher.switching.TmuxWindowOwnership;
import com.contextswitcher.tasks.OneNoteLink;
import com.contextswitcher.tasks.TaskFileParser;
import com.contextswitcher.tasks.TaskFileReadWrite;
import com.contextswitcher.tasks.TaskOrder;
import com.contextswitcher.tasks.TaskParseException;
import com.contextswitcher.tasks.TaskRepository;
import com.contextswitcher.tasks.TaskSearch;
import com.contextswitcher.tasks.TaskStatus;
import com.contextswitcher.tasks.TaskSync;
import com.contextswitcher.tasks.TaskTags;
import com.contextswitcher.ui.TaskListCell.GroupHeader;
import com.contextswitcher.ui.TaskListCell.LabelHeader;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Label;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;
import tools.maran.svg.SVG;
import tools.maran.svg.materialdesign.MDIInterface;
import tools.maran.svg.materialdesign.MDITechnology;
import tools.maran.svg.materialdesign.MDIWorld;
import tools.maran.svgnode.SvgNode;

/// Main window: task list as a flat `ListView` (root-level tasks first — error
/// rows first, then by status and title — followed by one collapsible section
/// per task subfolder). A flat list, not a tree, so every row shares the same
/// left and right edge regardless of grouping.
// [impl->dsn~main-window~2]
public class MainWindow {

    /// The user-selected sort order, applied per block (ungrouped, each
    /// group, Done). Kept across restarts — unlike the narrowing filters, a
    /// sort order is how the user reads the list, and is kept like the filters.
    // [impl->dsn~task-sort-modes~2]
    private TaskOrder sortMode = storedSortMode();
    /// The ticked groupings, never empty. The first (in [Grouping] order)
    /// builds the top-level headers, each further one nests sub-headers
    /// inside them. Kept across restarts like the sort mode.
    // [impl->dsn~task-label-grouping~3]
    // [impl->dsn~pr-status-grouping~1]
    // [impl->dsn~nested-grouping~1]
    private EnumSet<Grouping> groupings = storedGroupings();

    /// Reads a task file by file name; returns the content or an error text.
    /// Unlike [#load], [#read] never returns a human-readable error string.
    public interface TaskFileAccess extends TaskFileReadWrite {
        String load(String fileName);

        /// Deletes the task file. Returns null on success, otherwise the error text.
        @Nullable String delete(String fileName);

        /// Whether a task file of that name already exists.
        // [impl->dsn~task-create-ui~15]
        boolean exists(String fileName);

        /// Moves the task file (creating the target folder if needed); the
        /// task's message-queue file follows the new id.
        /// Returns null on success, otherwise the error text.
        // [impl->dsn~task-move-dnd~6]
        @Nullable String move(String fromFileName, String toFileName);

        /// Creates a (possibly empty) project-group folder.
        /// Returns null on success, otherwise the error text.
        // [impl->dsn~category-create-ui~2]
        @Nullable String createFolder(String folder);

        /// Renames a project-group folder (the watcher re-scans the moved
        /// directory). Returns null on success, otherwise the error text.
        // [impl->dsn~group-rename~1]
        @Nullable String renameFolder(String folder, String newFolder);

        /// Deletes a project-group folder and everything inside it (its task
        /// files, `CONTEXTSWITCHER.md`, note). Returns null on success,
        /// otherwise the error text.
        // [impl->dsn~category-delete~1]
        @Nullable String deleteFolder(String folder);
    }

    private final ObservableList<TaskEntry> entries;

    /// Swaps a renamed task's entry in place, so the list keeps the row
    /// instead of losing it until the watcher's create event arrives
    /// (`TaskRepository.renamed`). No-op until [#setTaskRenamer] wires it.
    // [impl->dsn~claude-title-sync~3]
    private BiConsumer<String, String> taskRenamer = (fromId, toId) -> { };
    /// Told about the same rename, ahead of the selection that follows it, so
    /// a pane keyed by task id can re-key instead of reading the new id as a
    /// task switch — the queue pane and its half-typed add box
    /// ([QueuePane#taskRenamed]). No-op until [#setOnTaskRenamed] wires it.
    // [impl->dsn~message-queue-ui~26]
    private BiConsumer<String, String> onTaskRenamed = (fromId, toId) -> { };
    private final ObservableSet<String> folders;
    private final Consumer<Task> onSwitch;
    private final Consumer<Task> onSuspend;
    /// Re-reads the task's own tmux window and writes what it publishes about
    /// its Claude session into the task file, then runs the callback on the FX
    /// thread (`Main.refreshClaudeFromWindow`). Runs before a teardown so the
    /// decision is made on the live session, not on what the file knew when it
    /// was imported; a task with nothing to re-read calls back immediately.
    // [impl->dsn~teardown-claude-resync~1]
    private final BiConsumer<Task, Runnable> onRefreshClaude;
    /// Notified after a rename is written, so the tmux window can follow.
    private final BiConsumer<Task, String> onRenamed;
    /// Opens a URL with the OS protocol handler (`Main.openUrl`).
    private final Consumer<String> onOpenUrl;
    /// Focuses (or opens) a URL as a browser tab via the extension server —
    /// unlike `onOpenUrl`, this reuses an existing tab. Used by the PR icon.
    /// The second argument is the virtual desktop of the task's category (null
    /// when it has none): a tab that must be *opened* is opened there.
    // [impl->dsn~pr-state-indicator~3]
    // [impl->dsn~pr-open-on-category-desktop~3]
    private final BiConsumer<String, @Nullable String> onFocusUrlInBrowser;
    /// Counts how many of the given URLs have a browser tab open **right now**
    /// (`Main.countOpenTabs`), off the FX thread, and hands the number back on
    /// it — `-1` when the question cannot be asked at all (no extension
    /// connected, or the request timed out). Backs the delete dialog's
    /// "Close N browser tabs" tick.
    // [impl->dsn~claude-session-kill~6]
    private final BiConsumer<List<String>, IntConsumer> onCountOpenTabs;
    /// Switches Windows to a named virtual desktop (`Main.focusDesktop`). Backs
    /// the category header's desktop-focus button.
    // [impl->dsn~category-desktop-focus~4]
    private final Consumer<String> onFocusDesktop;
    /// Opens local folders (the category header's folder icon), off the FX
    /// thread; the `Runnable` settles the button once every folder is handled.
    // [impl->dsn~category-folders-button~1]
    private final BiConsumer<List<String>, Runnable> onOpenFolders;
    /// Turns the active-desktop watcher on/off in `Main`: while on, `Main` reads
    /// the current virtual desktop and pushes it via [#updateActiveDesktop].
    // [impl->dsn~active-desktop-filter~6]
    private final Consumer<Boolean> onActiveDesktopFilter;
    private final Path tasksDir;
    /// Where per-task message-queue files live, so the delete dialog can warn
    /// when a task still has queued messages that a delete would orphan.
    private final Path queuesDir;
    private final Runnable onOpenTasksDir;
    /// Starts a tmux sync; the passed `Runnable` is invoked on the FX thread
    /// when the sync finishes (re-enables the toolbar button).
    private final Consumer<Runnable> onSyncTmux;
    /// Recreates the tmux windows of all running tasks; the passed `Runnable`
    /// is invoked on the FX thread when it finishes (re-enables the button).
    // [impl->dsn~tmux-restart-running~1]
    private final Consumer<Runnable> onRestartTmux;
    private final Consumer<Task> onPreview;
    private final TaskFileAccess files;
    /// Re-parses a task from disk for [#freshTask].
    private final TaskFileParser parser = new TaskFileParser();

    /// The live terminal mirror (built by `Main`); [#show] adds this window's
    /// buttons and PR rows to its toolbar.
    // [impl->dsn~terminal-pane~14]
    private final TerminalPane terminal;
    /// The message-queue pane's node (built by `Main`, [QueuePane]), shown
    /// below the editor in the right lane.
    // [impl->dsn~message-queue-ui~26]
    private final Node queueNode;
    private final Runnable onPreviewCleared;
    /// Focuses the terminal mirror after a click on a task row ([TaskListCell]),
    /// so the user can type into the task's tmux window straight away.
    // [impl->dsn~terminal-pane~14]
    private final Runnable onFocusTerminal;
    /// Re-colors the terminal mirror after a live theme switch ([TerminalPane]
    /// re-attaches: a JediTermFX widget's colors are fixed at construction).
    // [impl->dsn~terminal-theme~2]
    private final Runnable onRethemeTerminal;
    /// Runs the delete dialog's ticked cleanup (window teardown, transcript,
    /// working directory) off the FX thread.
    // [impl->dsn~claude-session-kill~6]
    private final BiConsumer<Task, ClaudeSessionCleanup.Choices> onKillSession;
    /// Sends the canned "tidy up before deletion" prompt into the task's
    /// Claude window instead of deleting.
    // [impl->dsn~claude-session-kill~6]
    private final Consumer<Task> onAskClaudeCleanup;
    /// Creates a task (tmux window + Claude) for a PR URL on a remote,
    /// placed in the given group (empty = root).
    public interface PrTaskCreator {
        void create(String url, String remote, String group, ClaudeMode mode);
    }

    private final PrTaskCreator onCreateFromPr;
    /// Creates a live task — a tmux window + Claude session on `remote`
    /// (started in `workdir` when non-null) — placed in `group` (empty =
    /// root), then shows it in the terminal. https://github.com/contextswitcher/contextswitcher-private/issues/45.
    /// `appendix` is reference material the prompt ends with, after the
    /// description's fence — the workspace-root `CLAUDE.md` template of a
    /// category created from a URL. It stays out of the description because
    /// that also becomes the task's title, file name and tmux window name.
    // [impl->dsn~task-create-live~8]
    public interface LiveTaskCreator {
        void create(String title, String remote, @Nullable String workdir,
                @Nullable String repo, boolean bootstrapWorktree, String group, ClaudeMode mode,
                @Nullable String appendix);
    }

    private final LiveTaskCreator onCreateLiveTask;

    /// Forks a task's Claude conversation into a new task in the same
    /// category: a fresh tmux window on the source's remote, Claude started
    /// resumed from the source's session id and forked (`claude --resume
    /// <id> --fork-session`), given `instruction` as its context prompt
    /// (`dsn~task-fork~1`). The new task is **not** selected — it runs in the
    /// background while the user stays on the task they forked from.
    // [impl->dsn~task-fork~1]
    public interface TaskForker {
        void fork(Task source, String instruction, ClaudeMode mode);
    }

    private final TaskForker onFork;

    /// Opens a local Claude session in `workdir` — the app's own terminal pane
    /// on Windows, a local tmux window elsewhere — with the task description as its initial
    /// prompt: the `Add local Claude` button of a local category (a group
    /// whose CS config sets a `workspacesRoot`/`workdir` but no `remote`).
    /// `taskId` is the file-only task just created, whose file the tmux
    /// window id is written back into (`dsn~terminal-local-mirror~2`).
    // [impl->dsn~task-create-local~4]
    public interface LocalClaudeStarter {
        void start(String taskId, String workdir, String description, ClaudeMode mode);
    }

    private final LocalClaudeStarter onStartLocalClaude;

    /// The model and effort last actually sent — the `Add task…` dialog's
    /// pre-fill, so a new chat starts at the model the user keeps choosing
    /// without picking it again. Deliberately **not** `@cs_model`: a task being
    /// created has no session to report one.
    // [impl->dsn~claude-mode-select~3]
    public static ClaudeMode lastMode() {
        return new ClaudeMode(PREFERENCES.get(LAST_MODEL, null), PREFERENCES.get(LAST_EFFORT, null));
    }

    /// Records a sent pick. Each half only when it was actually picked: an
    /// effort-only send must not erase the remembered model.
    // [impl->dsn~claude-mode-select~3]
    static void rememberMode(ClaudeMode mode) {
        if (mode.model() != null) {
            PREFERENCES.put(LAST_MODEL, mode.model());
        }
        if (mode.effort() != null) {
            PREFERENCES.put(LAST_EFFORT, mode.effort());
        }
    }

    /// Creates and focuses a remote-only task's tmux window on play — the
    /// task has a `remote:` but no `tmux:` section yet. `withClaude` picks a
    /// Claude session over a plain shell; both start in the task's workspace
    /// on the remote and write the resulting `tmux:` (and `claude:`) section
    /// back to the file.
    // [impl->dsn~remote-window-choice~6]
    public interface RemoteWindowStarter {
        void start(Task task, boolean withClaude, ClaudeMode mode);
    }

    /// What [#askRemoteWindow] came back with: a Claude session or a plain
    /// tmux shell, and — for the Claude one — the model and effort to start it
    /// with.
    // [impl->dsn~remote-window-choice~6]
    public record RemoteWindowChoice(boolean withClaude, ClaudeMode mode) {
    }

    private final RemoteWindowStarter onStartRemoteWindow;

    /// Opens a bare tmux window on a remote, optionally in a working directory —
    /// the category header's terminal icon (`Main.startScratchWindow`). Calls
    /// `onSettled` (FX thread) once the attempt succeeds or fails.
    // [impl->dsn~category-scratch-window~2]
    public interface ScratchWindowStarter {
        void start(String remote, @Nullable String cwd, Runnable onSettled);
    }

    private final ScratchWindowStarter onNewTmuxWindow;
    /// Opens a task's RefactoringMiner web view (https://github.com/contextswitcher/contextswitcher-private/issues/50); the `Runnable` is
    /// called (FX thread) once the attempt settles, success or failure, so
    /// the clicked control can stop showing its busy state.
    // [impl->dsn~refactoring-web-view~1]
    private final BiConsumer<Task, Runnable> onOpenRefactorings;
    /// Installs the queue boxes' attachment affordances (file drop, image
    /// paste) on a text area — `QueuePane.installAttachments`, reused by the
    /// Add-task dialog's description field.
    /// Installs the RefactoringMiner release on the given remotes (the settings
    /// dialog's setup button, `Main.setupRefactoringMiner`), calling `onDone`
    /// (FX thread) with the installed directory, or null when it failed — the
    /// status bar then carries the reason.
    // [impl->dsn~refactoring-miner-setup~1]
    public interface RefactoringMinerInstaller {
        void install(List<String> remotes, Consumer<@Nullable String> onDone);
    }

    private final RefactoringMinerInstaller onSetupRefactoringMiner;

    /// The settings dialog behind the toolbar's gear.
    private final SettingsDialog settingsDialog;

    private final Consumer<TextArea> installAttachments;
    /// The queue pane's quick message row, copying into the given text area
    /// (`QueuePane.quickRow`) — shown above the Add-task dialog's field.
    // [impl->dsn~quick-message-buttons~7]
    private final Function<TextArea, Node> quickRow;
    /// Reads / writes the raw `settings.yaml` for the settings editor.
    private final Supplier<String> loadSettings;
    private final Function<String, @Nullable String> saveSettings;
    /// The remotes configured in settings.yaml (for the PR remote picker).
    private final Supplier<List<String>> configuredRemotes;
    /// Re-reads the tag palette configured in settings.yaml (name + color) —
    /// called at startup and after a settings edit, so the filter and chips
    /// reflect the file without a restart.
    // [impl->dsn~task-tag-filter~2]
    private final Supplier<List<AppSettings.TagDef>> configuredTags;
    /// The `hints` setting (intro vs compact generated files) — picks the
    /// group-config skeleton flavor, like the task-file generators in `Main`.
    // [impl->dsn~skeleton-hints~4]
    private final boolean hints;
    /// The virtual desktop a category with no `desktop:` of its own belongs to
    /// (`fallbackDesktop` in `settings.yaml`); blank = no fallback.
    // [impl->dsn~fallback-desktop~1]
    private final String fallbackDesktop;
    /// The tag palette last read from settings.yaml (cached so hot paths — chip
    /// colors, the row menus — never touch disk); refreshed on a settings edit.
    private List<AppSettings.TagDef> palette = List.of();
    /// [#selectableTagNames] memoized for one rebuild: every realized row's
    /// "Tags" submenu asks for it, and computing it scans every task's
    /// effective tags. Invalidated in [#rebuildRows], like [#groupConfigCache].
    private @Nullable List<String> selectableTagsCache;
    /// The tags currently selected in the toolbar filter (AND-combined).
    /// Restored from the stored selection by [#populateTagFilterMenu], which is
    /// where the selectable tag names become known.
    // [impl->dsn~task-tag-filter~2]
    // [impl->dsn~filter-persistence~1]
    private final Set<String> activeTags = new LinkedHashSet<>();
    /// Palette color per tag name (lowercased key), built once from settings.
    private final Map<String, String> tagColors = new HashMap<>();

    /// Group → its parsed `CONTEXTSWITCHER.md`, filled lazily by
    /// [#groupDefaults] and dropped at the start of every [#rebuildRows]. The
    /// row cells read group tags for *every* task per cell (tags submenu), so
    /// without the cache one list refresh was tasks² file reads on the FX
    /// thread — a 62 s freeze on a slow Windows drive. Every on-disk change of
    /// a config reaches `rebuildRows` via the watcher, so the cache is never
    /// older than the rows it renders.
    // [impl->dsn~group-config-cache~1]
    private final Map<String, GroupConfig> groupConfigCache = new HashMap<>();
    /// The "Tags" submenu inside the filter menu (one check item per selectable
    /// tag plus "Clear filter"); disabled when no tag exists anywhere.
    // [impl->dsn~task-tag-filter~2]
    private @Nullable Menu tagFilterMenu;
    /// The toolbar filter menu (funnel icon) holding the three narrowing
    /// filters and the tag submenu; its badge shows how many are active.
    // [impl->dsn~task-list-toolbar~3]
    private @Nullable MenuButton filterButton;
    /// When on, the list shows only active tasks whose live `@cs_status` is
    /// `waiting` or `attention` — the rows needing the user's input next. Kept
    /// across restarts like the other filters.
    // [impl->dsn~awaits-input-filter~1]
    // [impl->dsn~filter-persistence~1]
    private boolean awaitsInputOnly = PREFERENCES.getBoolean(FILTER_AWAITS_INPUT, false);
    private @Nullable CheckMenuItem awaitsInputButton;

    /// The update button with its badge, tooltip and "What's new" window;
    /// `Main` feeds it through this window's delegates.
    private final UpdateNews updateNews;

    /// Sync groups (`dsn~task-sync-groups-ui~1`): null until `Main` installs
    /// them via [#setTaskSync]; `syncRound` runs one round off the FX thread
    /// and answers through [#showSyncIncoming].
    private @Nullable TaskSync taskSync;
    private Runnable syncRound = () -> { };
    private List<TaskSync.Incoming> syncIncoming = List.of();
    /// The count of shared tasks waiting to be sorted in, over the sync glyph;
    /// null until the toolbar builds the button.
    private @Nullable Badge syncBadge;
    private @Nullable Button syncButton;
    private @Nullable SyncGroupsWindow syncWindow;


    /// The browser-extension connection indicator in the list toolbar, updated
    /// by [#showBrowserExtension].
    // [impl->dsn~extension-connection-indicator~2]
    private @Nullable Button browserStatus;
    /// When on, the list shows only categories whose `CONTEXTSWITCHER.md`
    /// `desktop:` matches [#activeDesktop] — the categories on the virtual
    /// desktop in view. Kept across restarts like the other filters.
    // [impl->dsn~active-desktop-filter~6]
    // [impl->dsn~filter-persistence~1]
    private boolean activeDesktopOnly = PREFERENCES.getBoolean(FILTER_ACTIVE_DESKTOP, false);
    private @Nullable CheckMenuItem activeDesktopButton;
    /// Widens the desktop filter above to keep the categories that name no
    /// desktop at all (and the ungrouped tasks, which have no category to name
    /// one): tooling and scratch categories that belong nowhere in particular
    /// would otherwise be reachable on no desktop. Only meaningful while
    /// [#activeDesktopOnly] is on. Kept across restarts like the filter it widens.
    // [impl->dsn~active-desktop-filter~6]
    // [impl->dsn~filter-persistence~1]
    private boolean noDesktopCategoriesToo =
            PREFERENCES.getBoolean(FILTER_NO_DESKTOP_CATEGORIES, false);
    /// The active virtual desktop's name, pushed by `Main`'s poller while the
    /// filter is on; null when unknown (non-Windows, an unnamed desktop, or not
    /// yet read) — the filter then narrows nothing (shows everything).
    // [impl->dsn~active-desktop-filter~6]
    private @Nullable String activeDesktop;

    /// Desktop name → id of the task last selected while that desktop was
    /// active, recorded by the list's selection listener and re-selected by
    /// [#updateActiveDesktop] on a desktop switch while [#activeDesktopOnly]
    /// is on — so the panes show the task belonging to the desktop in view,
    /// not the previous desktop's. Kept across restarts (a preferences child
    /// node, one entry per desktop): the memory is worthless if the first
    /// switch after every launch has nothing to restore.
    // [impl->dsn~desktop-last-task-selection~4]
    static final Preferences LAST_TASK_BY_DESKTOP =
            Preferences.userNodeForPackage(MainWindow.class).node("lastTaskByDesktop");
    /// When on, the list shows only running (active) tasks — suspended and done
    /// ones drop out. Kept across restarts like the other filters.
    // [impl->dsn~running-tasks-filter~1]
    // [impl->dsn~filter-persistence~1]
    private boolean runningTasksOnly = PREFERENCES.getBoolean(FILTER_RUNNING_TASKS, false);
    private @Nullable Task previewedTask;

    /// Runs the *browser* switch action alone for the task handed in — the
    /// toolbar globe's click; set by `Main`, a no-op until then.
    /// [impl->dsn~extension-connection-indicator~2]
    private Consumer<Task> onBrowserSwitch = task -> { };

    /// Installs the browser-only switch handler (see [#onBrowserSwitch]).
    // [impl->dsn~extension-connection-indicator~2]
    public void setOnBrowserSwitch(Consumer<Task> handler) {
        this.onBrowserSwitch = handler;
    }

    /// The globe's context menu: which browsers are connected, which one is
    /// chosen, and what choosing one does; set by `Main`.
    // [impl->dsn~prefer-chosen-browser~1]
    private Supplier<Set<Browser>> connectedBrowsers = Set::of;
    private Supplier<Browser> chosenBrowser = () -> Browser.DEFAULT;
    private Consumer<Browser> onBrowserChoice = browser -> { };

    // [impl->dsn~prefer-chosen-browser~1]
    public void setBrowserChoice(Supplier<Set<Browser>> connected, Supplier<Browser> chosen,
            Consumer<Browser> choose) {
        this.connectedBrowsers = connected;
        this.chosenBrowser = chosen;
        this.onBrowserChoice = choose;
    }

    /// Hands a task whose pull requests are all merged to the janitor; set by
    /// `Main`, a no-op until then. [impl->dsn~merged-task-cleanup~2]
    private Consumer<Task> onMergedCleanup = task -> { };

    /// Installs the merged-task janitor (see [#onMergedCleanup]).
    // [impl->dsn~merged-task-cleanup~2]
    public void setOnMergedCleanup(Consumer<Task> handler) {
        this.onMergedCleanup = handler;
    }

    /// The open file split in two: the YAML frontmatter in the configuration
    /// pane's raw editor, the Markdown body in the notes editor; [#editorText]
    /// joins them back with the `---` fences.
    // [impl->dsn~richtext-markdown-editor~9]
    private final CodeTextModel configModel = new CodeTextModel();
    private final RichTextArea configEditor = new RichTextArea(configModel);
    private final CodeTextModel notesModel = new CodeTextModel();
    private final RichTextArea notesEditor = new RichTextArea(notesModel);
    private final Label editorFileLabel = new Label("");
    /// "Task file" or, for a `CONTEXTSWITCHER.md`, "Category config file".
    // [impl->dsn~group-config-create~9]
    private final Label editorTypeLabel = new Label("Task file");
    private @Nullable String editedFileName;
    /// The editor content as loaded from / last written to disk; the editor
    /// is dirty when its text differs (drives auto-save on focus loss).
    private String editorLoadedContent = "";
    /// Popup showing the picture behind the `[image: …]` marker under the
    /// pointer, and the marker path it currently shows (null when hidden).
    // [impl->dsn~attachment-image-hover~2]
    private final AttachmentPreview.Hover imagePreview = new AttachmentPreview.Hover();

    /// Folder group names the user collapsed; kept across list rebuilds
    /// (e.g. triggered by an unrelated file-watch event) so an open group
    /// does not snap shut underneath the user.
    // [impl->dsn~task-folder-grouping-ui~8]
    private final Set<String> collapsedGroups = new HashSet<>();

    /// Whether the bottom "Done" section — every completed task, gathered out
    /// of its group — is collapsed. Finished work is de-emphasised, so the
    /// section starts collapsed and the user expands it on demand.
    // [impl->dsn~done-section~1]
    private boolean doneSectionCollapsed = true;

    /// The flattened rows the list shows: ungrouped tasks, then a
    /// [GroupHeader] plus (when expanded) its tasks per subfolder.
    private final ObservableList<Object> visibleRows = FXCollections.observableArrayList();

    /// Active find-bar query (Ctrl+F). Blank = the bar is closed or empty and
    /// the full list shows; non-blank filters the rows to matching tasks.
    // [impl->dsn~task-find~9]
    private String searchQuery = "";
    private @Nullable TextField searchField;
    /// The ids matching [#searchQuery], computed off the FX thread by
    /// [#applySearch] — matching reads the queue files, and doing that per
    /// keystroke on the FX thread stalled typing. Read by [#rebuildRows];
    /// empty while no query runs.
    private Set<String> searchHits = Set.of();
    /// True while a changed query's hits are still being computed, so the
    /// emptied list says "Searching…" instead of "No tasks match".
    private boolean searchPending;
    /// One worker so at most one query runs; bumped per query so a superseded
    /// one stops at its next entry and its result is dropped.
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "task-search");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger searchGeneration = new AtomicInteger();
    /// The task selected when a changed query emptied the rows; see
    /// [#restoreSelectionBeforeSearch].
    private @Nullable String selectionBeforeSearch;
    private final Label findMatchLabel = new Label();
    /// The find field's prompt while no category is scoped.
    private static final String FIND_PROMPT = "Find task — title, URL, PR number…";

    /// The category the find field is scoped to — shown as a chip inside the
    /// field — or null while the find covers the whole list.
    // [impl->dsn~category-search~3]
    private @Nullable String scopeCategory;
    /// The scope chip (its name label and ×), hidden while nothing is scoped.
    private final Label scopeChipLabel = new Label();
    private final HBox scopeChip = new HBox();

    /// The category of the selected row — the selected task's group, or the
    /// selected header's own name. Its header carries the selection colours
    /// too, so the highlighted row always names the category it sits in.
    // [impl->dsn~current-category-highlight~1]
    private @Nullable String currentGroup;

    /// How many entries the running query matched but the active-desktop filter
    /// hid, counted by the last [#rebuildRows]; 0 with no query or no filter.
    // [impl->dsn~search-hits-off-desktop~1]
    private int hitsOffDesktop;

    /// UI state kept across app restarts (not configuration — that lives in
    /// settings.yaml): currently only the last tmux-import destination.
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(MainWindow.class);

    /// How many task visits the Back/Forward trail keeps.
    // [impl->dsn~task-history-navigation~2]
    private static final int HISTORY_LIMIT = 50;
    // ponytail: a fixed 1 s guess at "desktop switch then browser focus";
    // raise it if a field log shows the two events further apart.
    // [impl->dsn~task-history-navigation~2]
    private static final long TRANSIENT_VISIT_NANOS = 1_000_000_000L;
    private static final String LAST_IMPORT_HOST = "lastImportHost";
    private static final String LAST_MODEL = "lastClaudeModel";
    private static final String LAST_EFFORT = "lastClaudeEffort";
    /// Delete-dialog cleanup checkboxes, pre-filled from the last run
    /// (UI state, not configuration). The workdir box is deliberately
    /// **not** persisted — `rm -rf` starts unticked every time.
    // [impl->dsn~claude-session-kill~6]
    private static final String KILL_END_WINDOW = "killEndWindow";
    private static final String KILL_CLOSE_BROWSER_TABS = "killCloseBrowserTabs";
    private static final String KILL_TRANSCRIPT = "killTranscript";
    /// Sort mode and label-grouping toggle (UI state, kept across restarts).
    // [impl->dsn~task-sort-modes~2]
    // [impl->dsn~task-label-grouping~3]
    private static final String SORT_MODE = "sortMode";
    private static final String GROUP_BY_LABELS = "groupByLabels";
    static final String GROUPING = "grouping";

    /// What the task list groups by, picked from the toolbar's group menu.
    // [impl->dsn~task-label-grouping~3]
    // [impl->dsn~pr-status-grouping~1]
    private enum Grouping {
        FOLDER("Folder"), LABELS("Labels"), PR_STATUS("PR status");

        private final String label;

        Grouping(String label) {
            this.label = label;
        }
    }
    /// The energy saver toggle (UI state, kept across restarts — a laptop that
    /// went on battery yesterday is on battery again today).
    // [impl->dsn~energy-saver~1]
    private static final String ENERGY_SAVER = "energySaver";
    /// The toolbar filters, kept across restarts like the sort mode. A view the
    /// user narrowed on purpose is how they read the list, and re-ticking it
    /// after every launch was busywork. Package-private so the UI tests can
    /// clear them — a filter one test left on must not narrow another's list.
    // [impl->dsn~filter-persistence~1]
    static final String FILTER_AWAITS_INPUT = "filterAwaitsInput";
    static final String FILTER_RUNNING_TASKS = "filterRunningTasks";
    static final String FILTER_ACTIVE_DESKTOP = "filterActiveDesktop";
    static final String FILTER_NO_DESKTOP_CATEGORIES = "filterNoDesktopCategories";
    static final String FILTER_TAGS = "filterTags";
    static final String FILTER_RECENT_TAGS = "filterRecentTags";

    /// The stored sort mode, tolerating an unknown stored name (e.g. after a
    /// downgrade) by falling back to the default.
    // [impl->dsn~task-sort-modes~2]
    /// The stored groupings, comma-separated (a single name from before
    /// nesting reads as itself); before the group menu existed it was the
    /// group-by-labels toggle, whose `true` still means [Grouping#LABELS].
    private static EnumSet<Grouping> storedGroupings() {
        String legacy = PREFERENCES.getBoolean(GROUP_BY_LABELS, false) ? Grouping.LABELS.name() : Grouping.FOLDER.name();
        try {
            EnumSet<Grouping> stored = EnumSet.noneOf(Grouping.class);
            for (String name : PREFERENCES.get(GROUPING, legacy).split(",")) {
                stored.add(Grouping.valueOf(name));
            }
            return stored;
        } catch (IllegalArgumentException e) {
            return EnumSet.of(Grouping.FOLDER);
        }
    }

    /// The grouping that builds the top-level headers.
    private Grouping grouping() {
        return groupings.iterator().next();
    }

    private static TaskOrder storedSortMode() {
        try {
            return TaskOrder.valueOf(PREFERENCES.get(SORT_MODE, TaskOrder.ALPHABETICAL.name()));
        } catch (IllegalArgumentException e) {
            return TaskOrder.ALPHABETICAL;
        }
    }

    /// Live `@cs_status` per window (`host windowId -> status`), refreshed by
    /// the status poller; drives the per-row running indicator.
    // [impl->dsn~task-running-indicator~7]
    private final Map<String, String> runningStatusByKey = new ConcurrentHashMap<>();
    /// Live `@cs_session_id` per window (`host windowId -> Claude session id`),
    /// from the same status poll. Windows that publish none are absent, so a
    /// lookup miss is "no evidence" rather than "no session".
    // [impl->dsn~tmux-window-ownership~4]
    private final Map<String, String> sessionIdByKey = new ConcurrentHashMap<>();
    /// Live PR state per PR URL, refreshed by the PR-state poller (https://github.com/contextswitcher/contextswitcher-private/issues/49).
    // [impl->dsn~pr-state-indicator~3]
    private final Map<String, PrInfo> prInfoByUrl = new ConcurrentHashMap<>();
    /// The line above the terminal naming the previewed task's PR(s): state
    /// icon, clickable title, labels — the hard facts, next to the
    /// tmux title's soft ones. Empty (collapsed) for a task without a PR.
    // [impl->dsn~pr-header-line~4]
    private final VBox prHeader = new VBox(4);
    /// The `IDEA` button at the right of the PR header line: the row menu's
    /// "Open in IntelliJ" within reach of the task you are looking at.
    // [impl->dsn~open-in-intellij~3]
    private final Button ideaButton = new Button("IDEA");
    /// The `Claude` button left of [#ideaButton], shown only while the
    /// previewed task's window is known to run something other than Claude —
    /// the repair for a start that went wrong, without a Switch; play runs
    /// the same repair as part of its switch ([#switchResolved]).
    // [impl->dsn~start-claude-button~2]
    private final Button claudeButton = new Button("Claude");
    /// Live `#{pane_current_command}` per window (`host windowId -> command`),
    /// from the status poll; a window that did not answer is absent.
    // [impl->dsn~start-claude-button~2]
    private final Map<String, String> commandByKey = new ConcurrentHashMap<>();
    /// Starts (resumes) Claude in the task's existing window; set by `Main`,
    /// a no-op until then. The `Runnable` re-enables the button.
    // [impl->dsn~start-claude-button~2]
    private BiConsumer<Task, Runnable> onStartClaude = (task, done) -> done.run();

    /// Installs the start-Claude handler (see [#onStartClaude]).
    // [impl->dsn~start-claude-button~2]
    public void setOnStartClaude(BiConsumer<Task, Runnable> handler) {
        this.onStartClaude = handler;
    }

    /// Re-runs the first-start wizard (remote, tools, first project); set by
    /// `Main`, a no-op until then. [impl->dsn~setup-wizard~8]
    private Runnable onSetupWizard = () -> { };

    /// Installs the setup-wizard handler (see [#onSetupWizard]).
    // [impl->dsn~setup-wizard~8]
    public void setOnSetupWizard(Runnable handler) {
        this.onSetupWizard = handler;
    }
    /// Refactoring count per task id, refreshed by the RefactoringMiner
    /// poller (https://github.com/contextswitcher/contextswitcher-private/issues/50); drives the per-row badge.
    // [impl->dsn~refactoring-analysis-poller~1]
    private final Map<String, RefactoringSummary> refactoringsByTask = new ConcurrentHashMap<>();
    private @Nullable ListView<Object> taskList;

    /// The selected row's category, the subject of the window title.
    // [impl->dsn~window-title-category~3]
    private String titleCategory = "";

    /// The window, kept so the title can follow the selected category.
    // [impl->dsn~window-title-category~3]
    private @Nullable Stage stage;

    /// The Configuration pane, built by [#show]: the frontmatter form with its
    /// Raw YAML view (`dsn~task-field-form~4`).
    private @Nullable ConfigFormPane configFormPane;

    /// What [#show] built in the ShellFX shell; null until then — there is no
    /// pane to bring forward before there is a shell.
    // [impl->dsn~shell-layout~2]
    private ShellFxHost.@Nullable Hosted shell;

    /// The ⋮ menu's refresh item, disabled while a refresh runs.
    private @Nullable MenuItem refreshNowItem;

    /// The info-center group the manual refresh reports into.
    // [impl->dsn~refresh-progress~2]
    private final NotificationGroup<Void, Notification<Void>> backgroundWork =
            new NotificationGroup<>("Background work");

    /// Tasks whose remote half is still being set up, by task id — written by
    /// `Main`'s creation flows, read by the row renderer.
    // [impl->dsn~task-create-progress~5]
    private final Map<String, TaskListCell.Creation> creations = new HashMap<>();

    /// True only while [#rebuildRows] hands the new rows to `visibleRows`:
    /// the selection the replacement itself emits is not a user selection and
    /// must trigger none of the selection listener's effects.
    // [impl->dsn~selection-survives-a-filter~2]
    private boolean replacingRows;

    /// Advances the creating rows' time-driven bars — through the bound
    /// properties, not a cell rebuild; runs only while a creation is in flight.
    // [impl->dsn~task-create-progress~5]
    private @Nullable Timeline creationTicker;

    /// The header row pinned over the top of the list, and the row index it
    /// currently renders (-1: nothing pinned). [#installStickyHeader]
    private @Nullable TaskListCell stickyCell;
    private int stickyIndex = -1;
    /// Task id to select after the next [#rebuildRows] (which clears the
    /// ListView selection): the neighbour of a just-deleted task, or a task
    /// just created via "Add task…".
    private @Nullable String pendingSelectionId;
    /// Whether to also scroll the pending selection into view once applied —
    /// set by [#selectTask] (an explicitly requested row, e.g. a just-moved
    /// task, may sit outside the viewport). Rebuilds that merely preserve the
    /// current selection never scroll, so background refreshes cannot yank
    /// the viewport.
    // [impl->dsn~task-move-dnd~6]
    private boolean scrollToPendingSelection;
    /// The last Add-task submission, the clipboard text at that moment, and
    /// whether that creation failed: a retry (dialog reopened, clipboard
    /// unchanged) then re-offers the failed text — with the user's edits —
    /// instead of resetting the field to the clipboard.
    // [impl->dsn~task-create-retry-prefill~1]
    private @Nullable String lastSubmittedInput;
    private @Nullable String lastSubmittedClip;
    private boolean lastSubmissionFailed;
    /// Group name whose header to select once its row appears in the next
    /// [#rebuildRows]: a just-created category whose folder the watcher has not
    /// delivered yet. Takes priority over [#pendingSelectionId] so the new
    /// category stays focused instead of the previously selected task.
    private @Nullable String pendingGroupSelection;
    /// The task ids visited, oldest first, and where in that trail the view
    /// currently stands: the browser-style Back/Forward history of the task
    /// list, so a task can be returned to without hunting for its row after a
    /// sort or a concurrent update moved it.
    // [impl->dsn~task-history-navigation~2]
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    /// When the newest trail entry was appended; set back by
    /// [#TRANSIENT_VISIT_NANOS] once that entry must no longer be replaced.
    private long lastRecordedNanos = System.nanoTime() - TRANSIENT_VISIT_NANOS;
    private @Nullable Button backButton;
    private @Nullable Button forwardButton;

    public MainWindow(ObservableList<TaskEntry> entries, ObservableSet<String> folders,
            Consumer<Task> onSwitch, Consumer<Task> onSuspend,
            BiConsumer<Task, Runnable> onRefreshClaude,
            BiConsumer<Task, String> onRenamed, Consumer<String> onOpenUrl,
            BiConsumer<String, @Nullable String> onFocusUrlInBrowser,
            BiConsumer<List<String>, IntConsumer> onCountOpenTabs, Consumer<String> onFocusDesktop,
            BiConsumer<List<String>, Runnable> onOpenFolders,
            Consumer<Boolean> onActiveDesktopFilter,
            Path tasksDir, Path queuesDir, Runnable onOpenTasksDir, Consumer<Runnable> onSyncTmux,
            Consumer<Runnable> onRestartTmux,
            Consumer<Task> onPreview, TaskFileAccess files,
            TerminalPane terminal, Node queueNode, Runnable onPreviewCleared,
            Runnable onFocusTerminal,
            Runnable onRethemeTerminal,
            BiConsumer<Task, ClaudeSessionCleanup.Choices> onKillSession,
            Consumer<Task> onAskClaudeCleanup,
            PrTaskCreator onCreateFromPr,
            LiveTaskCreator onCreateLiveTask,
            TaskForker onFork,
            LocalClaudeStarter onStartLocalClaude,
            RemoteWindowStarter onStartRemoteWindow,
            ScratchWindowStarter onNewTmuxWindow,
            BiConsumer<Task, Runnable> onOpenRefactorings,
            RefactoringMinerInstaller onSetupRefactoringMiner,
            Consumer<TextArea> installAttachments,
            Function<TextArea, Node> quickRow,
            Supplier<String> loadSettings,
            Function<String, @Nullable String> saveSettings,
            Supplier<List<String>> configuredRemotes,
            Supplier<List<AppSettings.TagDef>> configuredTags,
            boolean hints, String fallbackDesktop) {
        this.entries = entries;
        this.folders = folders;
        this.onSwitch = onSwitch;
        this.onSuspend = onSuspend;
        this.onRefreshClaude = onRefreshClaude;
        this.onRenamed = onRenamed;
        this.onOpenUrl = onOpenUrl;
        // Built here, not with the toolbar: the update check may report before
        // the window is shown, and that report must not be dropped.
        this.updateNews = new UpdateNews(iconButton(MDIInterface.UPDATE, ""), () -> stage,
                message -> statusBar.message(message), onOpenUrl);
        this.onFocusUrlInBrowser = onFocusUrlInBrowser;
        this.onCountOpenTabs = onCountOpenTabs;
        this.onFocusDesktop = onFocusDesktop;
        this.onOpenFolders = onOpenFolders;
        this.onActiveDesktopFilter = onActiveDesktopFilter;
        this.tasksDir = tasksDir;
        this.queuesDir = queuesDir;
        this.onOpenTasksDir = onOpenTasksDir;
        this.onSyncTmux = onSyncTmux;
        this.onRestartTmux = onRestartTmux;
        this.onPreview = onPreview;
        this.files = files;
        this.terminal = terminal;
        this.queueNode = queueNode;
        this.onPreviewCleared = onPreviewCleared;
        this.onFocusTerminal = onFocusTerminal;
        this.onRethemeTerminal = onRethemeTerminal;
        this.onKillSession = onKillSession;
        this.onAskClaudeCleanup = onAskClaudeCleanup;
        this.onCreateFromPr = onCreateFromPr;
        this.onCreateLiveTask = onCreateLiveTask;
        this.onFork = onFork;
        this.onStartLocalClaude = onStartLocalClaude;
        this.onStartRemoteWindow = onStartRemoteWindow;
        this.onNewTmuxWindow = onNewTmuxWindow;
        this.onOpenRefactorings = onOpenRefactorings;
        this.onSetupRefactoringMiner = onSetupRefactoringMiner;
        this.installAttachments = installAttachments;
        this.quickRow = quickRow;
        this.loadSettings = loadSettings;
        this.saveSettings = saveSettings;
        this.configuredRemotes = configuredRemotes;
        this.configuredTags = configuredTags;
        this.hints = hints;
        this.fallbackDesktop = fallbackDesktop;
        this.settingsDialog = new SettingsDialog(loadSettings, saveSettings, this::dialogOwner,
                this::showReferenceDialog, this::refreshTagPalette, onRethemeTerminal,
                onSetupRefactoringMiner);
        this.palette = configuredTags.get();
        rebuildTagColors();
    }

    private final SwitchStatusBar statusBar = new SwitchStatusBar();
    /// The commit the running app was built from, in the status bar's right
    /// corner — empty until [#showCommit] fills it (git is asked off the FX
    /// thread), and empty forever when the app runs out of a zip.
    // [impl->dsn~running-commit~3]
    private final Label commitLabel = new Label();

    /// Names the commit the running app was built from. FX thread.
    // [impl->dsn~running-commit~3]
    public void showCommit(String commit) {
        commitLabel.setText(commit);
        commitLabel.setTooltip(new Tooltip("The ContextSwitcher commit this app was built from."
                + " Double-click to copy the sha."));
    }

    /// The bare sha of the status bar's `<short sha> (<date> <time>)` line —
    /// what a bug report wants pasted, without the date around it.
    static String shaOf(String commitLine) {
        int space = commitLine.indexOf(' ');
        return space < 0 ? commitLine : commitLine.substring(0, space);
    }

    /// A newer version exists, `commits` ahead ([UpdateNews#showUpdateAvailable]).
    public void showUpdateAvailable(int commits) {
        updateNews.showUpdateAvailable(commits);
    }

    /// The pending changelog bullets ([UpdateNews#showPendingNews]). FX thread.
    public void showPendingNews(List<WhatsNew.Item> items, @Nullable String upstream, Runnable announce) {
        updateNews.showPendingNews(items, upstream, announce);
    }

    /// The fetch the What's new window waits on ([UpdateNews#setOnCheckRemote]).
    public void setOnCheckRemote(Runnable check) {
        updateNews.setOnCheckRemote(check);
    }

    /// Keeps *Restart to update* disabled while `busy` ([UpdateNews#disableRestartWhile]).
    public void disableRestartWhile(ObservableBooleanValue busy) {
        updateNews.disableRestartWhile(busy);
    }

    /// Opens the What's new window ([UpdateNews#showWhatsNew]). FX thread.
    public void showWhatsNew(String title, List<WhatsNew.Item> items, boolean check) {
        updateNews.showWhatsNew(title, items, check);
    }

    /// The fetch behind the open window answered ([UpdateNews#newsChecked]). FX thread.
    public void newsChecked(List<WhatsNew.Item> items, @Nullable String upstream, boolean behind) {
        updateNews.newsChecked(items, upstream, behind);
    }

    public SwitchStatusBar statusBar() {
        return statusBar;
    }

    public void show(Stage stage, Application application) {
        this.stage = stage;
        ListView<Object> list = new ListView<>(visibleRows);
        // Scopes the accent-edge row styling in main.css to this list — a
        // ComboBox's value is a .list-cell too, and picked up the edge.
        list.getStyleClass().add("task-list");
        this.taskList = list;
        list.setCellFactory(view -> new TaskListCell(this::switchTask, this::openInIntellij,
                this::renameTask, this::deleteTask,
                this::setStatus, this::addLinkToTask, this::openTaskNote, this::toggleGroup,
                this::toggleDoneSection,
                this::addTask,
                group -> files.exists(groupConfigFileName(group)), this::openGroupConfig,
                this::runningStatusFor, this::moveTask,
                () -> folders.stream().sorted().toList(),
                this::groupNoteUrl, this::openGroupNote,
                this::groupRepoUrl, this::openGroupRepo, this::renameGroup, this::deleteGroup,
                prInfoByUrl::get, this::focusPr, statusBar::hover,
                task -> TaskTags.visible(effectiveTags(task), activeTags),
                name -> tagColors.get(name.toLowerCase(Locale.ROOT)),
                this::selectableTagNames, this::toggleTag, this::toggleTaskPinned,
                this::groupTags, this::toggleGroupTag,
                group -> groupDefaults(group).pinned(), this::toggleGroupPinned,
                onFocusTerminal, this::queuedCountFor,
                this::deleteCorruptFile, name -> tasksDir.resolve(name).toString(),
                this::groupDesktop, this::focusDesktop, this::openGroupFolders, () -> !groupings.contains(Grouping.FOLDER),
                this::newTmuxWindow,
                this::refactoringSummaryFor, this::showRefactorings,
                task -> creations.get(task.id()),
                this::groupAutoQuery, this::openAutoSearch,
                this::toggleCategoryScope, this::groupPath, this::isCurrentGroup, this::syncGroupsMenu));
        list.setPlaceholder(emptyStatePlaceholder());
        installDragAutoScroll(list);
        // Fallback menu for the empty area below the last row (cells with
        // their own menu override it).
        // [impl->dsn~category-create-ui~2]
        MenuItem addCategory = new MenuItem("Add category…");
        addCategory.setOnAction(event -> addCategory());
        MenuItem addCategoryFromUrl = new MenuItem("Add category from URL…");
        addCategoryFromUrl.setOnAction(event -> addCategoryFromUrl());
        list.setContextMenu(new ContextMenu(addCategory, addCategoryFromUrl, setupWizardItem()));
        // Keeps title, status, and action icons legible; the dock layout
        // shrinks the other panes first once the window gets this narrow.
        list.setMinWidth(220);
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            // Replacing the rows makes JavaFX's own selection model emit a
            // selection of its own — a neighbouring row, not a user action —
            // before the rebuild restores the real one a few milliseconds
            // later. Every effect below would then run for the wrong task:
            // field report 2026-09-12, seconds after creating a task, an
            // auto-sync's file write rebuilt the rows and the transient
            // selection ran the *other* task's browser switch (status bar:
            // "Personalized whats-new startup notes: browser ✗") and pointed
            // the terminal mirror at its window, all without a `selectTask`
            // in the log. It also stamped that task as the desktop's last one
            // (`rememberLastTask`), which a later desktop switch then acted on.
            // The restore is the selection that counts; this one is noise.
            // [impl->dsn~selection-survives-a-filter~2]
            if (replacingRows) {
                Logger.debug("Ignoring the selection a row replacement emitted: {}", selected);
                return;
            }
            applySelection(selected);
        });

        rebuildRows();
        // The filter menu offers in-use tags too, so a task change may add or
        // remove menu entries (and prune stale selections) — repopulate first,
        // then rebuild with the pruned selection. [impl->dsn~tag-selection-union~2]
        entries.addListener((ListChangeListener<TaskEntry>) change -> {
            populateTagFilterMenu();
            rebuildRows();
            // A changed entry may (no longer) match: re-run the query on the
            // new list; the rebuild above showed the previous hits meanwhile.
            if (!searchQuery.strip().isEmpty()) {
                applySearch(searchQuery);
            }
        });
        folders.addListener((SetChangeListener<String>) change -> rebuildRows());

        // Left pane: the task-list icon toolbar, a find bar below it, the list.
        // [impl->dsn~task-list-toolbar~3]
        // [impl->dsn~task-find~9]
        Node findBarNode = findBar();
        StackPane listStack = installStickyHeader(list);
        ListToolbar listToolbar = toolBar();
        VBox leftPane = new VBox(listToolbar.bar(), findBarNode, listStack);
        VBox.setVgrow(listStack, Priority.ALWAYS);

        // The notes editor; the task's message queue docks below it by default.
        // [impl->dsn~message-queue-ui~26]
        Node editorNode = editorPane();

        // Clicking anywhere in a pane that belongs to the previewed task — the
        // terminal mirror, the file editor, a queued message, or any button in
        // them — re-selects that task's row. The panes can outlive the list
        // selection (a transient rebuild clears it as a no-op, "Add task…"
        // moves focus off the list), so this keeps the highlighted row matching
        // the task you are actually working in. Capture-phase filter that never
        // consumes: children (the terminal canvas, buttons) still get the click.
        // [impl->dsn~pane-click-selects-task~1]
        Consumer<Node> selectOnClick = node ->
                node.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> selectPreviewedTask());
        Node terminalLane = terminal.getRoot();
        // [impl->dsn~open-in-intellij~3]
        ideaButton.getStyleClass().addAll(Styles.FLAT, Styles.SMALL);
        ideaButton.setTooltip(new Tooltip("Open the task's project in IntelliJ"));
        ideaButton.setOnAction(event -> {
            Task task = previewedTask;
            if (task != null) {
                openInIntellij(task);
            }
        });
        // [impl->dsn~start-claude-button~2]
        claudeButton.getStyleClass().addAll(Styles.FLAT, Styles.SMALL);
        claudeButton.setTooltip(new Tooltip("Start Claude in the task's tmux window"));
        claudeButton.setOnAction(event -> {
            Task task = previewedTask;
            if (task == null) {
                return;
            }
            claudeButton.setDisable(true);
            statusBar.message("Starting Claude …");
            onStartClaude.accept(task, () -> claudeButton.setDisable(false));
        });
        // [impl->dsn~task-fork~1]
        terminal.setOnFork(() -> {
            Task task = previewedTask;
            if (task != null) {
                openForkDialog(task);
            }
        });
        // The window's buttons at the right end of the Terminal pane's toolbar
        // band, the PR rows below it.
        // [impl->dsn~shell-layout~2]
        // [impl->dsn~pr-header-line~4]
        terminal.addToolbarControls(claudeButton, ideaButton);
        terminal.setSubHeader(prHeader);
        selectOnClick.accept(terminalLane);
        selectOnClick.accept(editorNode);
        selectOnClick.accept(queueNode);

        // The settings gear lives in the main toolbar (with the other app-wide
        // actions), not down here — a lone gear in the status bar read as the
        // sync-tmux cog's twin and was easy to miss.
        // [impl->dsn~settings-editor~4]
        HBox.setHgrow(statusBar, Priority.ALWAYS);
        // The log button sits where the failures are read: a status-bar line
        // can only name the reason, the log has the context around it.
        // [impl->dsn~open-log-button~1]
        Button openLog = iconButton(MDITechnology.SCRIPT_TEXT_OUTLINE, "Open the log file");
        openLog.setOnAction(event -> openLog());
        // The commit the running app was built from, in the corner furthest
        // from the messages — a field report ("it still does X") is only
        // useful with the version it was seen on.
        // [impl->dsn~running-commit~3]
        commitLabel.getStyleClass().add(Styles.TEXT_MUTED);
        // A double-click copies the sha outright, rather than selecting it for
        // a Ctrl+C: the only thing anyone ever does with this line is paste it
        // into a bug report.
        // [impl->dsn~running-commit~3]
        commitLabel.setCursor(Cursor.HAND);
        commitLabel.setOnMouseClicked(event -> {
            if (event.getClickCount() != 2 || commitLabel.getText().isBlank()) {
                return;
            }
            String sha = shaOf(commitLabel.getText());
            ClipboardContent content = new ClipboardContent();
            content.putString(sha);
            Clipboard.getSystemClipboard().setContent(content);
            statusBar().message("Commit " + sha + " copied.");
        });
        HBox bottomBar = new HBox(statusBar, commitLabel, openLog);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(0, 6, 0, 0));
        // The panes as ShellFX dock tabs (MADR 0032). The raw frontmatter editor
        // is the Configuration pane's Raw YAML view.
        // [impl->dsn~shell-layout~2]
        ConfigFormPane pane = new ConfigFormPane(configEditor, this::editorText,
                updated -> {
                    setEditorText(updated);
                    saveEditor();
                },
                () -> {
                    String name = editedFileName;
                    return name != null && isGroupConfig(name);
                });
        configFormPane = pane;
        selectOnClick.accept(pane.getRoot());
        ShellFxHost.Hosted hosted = ShellFxHost.host(application, stage, new ShellFxHost.Panes(
                leftPane, terminalLane, editorNode, pane, queueNode, bottomBar, listToolbar.appControls()));
        shell = hosted;
        Scene scene = hosted.scene();
        showCategoryInTitle("");
        // Before the find filter below, so that with the bash chords on
        // (`readlineKeys`) Ctrl+F inside a text input moves the caret — filters
        // run in registration order, and find keeps every other focus.
        // [impl->dsn~readline-keys~1]
        ReadlineKeys.install(scene);
        // Ctrl+F focuses the find bar from anywhere in the window. A capture-phase
        // scene filter, not an accelerator: accelerators run after the focused
        // node's handlers, so the terminal pane's control-key forwarding
        // (`dsn~terminal-pane~14`) swallowed Ctrl+F as ^F before it ever fired.
        // The terminal loses forward-char (^F) — deliberate, find wins.
        // [impl->dsn~task-find~9]
        KeyCombination find = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
        // Alt+Left / Alt+Right walk the task history, the same chord every
        // browser and file manager uses. Capture-phase like Ctrl+F above, for
        // the same reason: the terminal pane would otherwise forward them as
        // an escape sequence before an accelerator ever fired.
        // [impl->dsn~task-history-navigation~2]
        KeyCombination back = new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN);
        KeyCombination forward = new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN);
        // Ctrl+T opens "Add task" for the selected category, like the add
        // menu's item. Capture-phase for the terminal's sake again (it loses
        // transpose-chars, ^T). Deferred: the dialog's nested event loop must
        // not run inside this filter.
        // [impl->dsn~task-create-ui~15]
        KeyCombination addTaskKeys = new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN);
        // Ctrl+J opens the jump-to-category popup; capture-phase like the
        // others (the terminal loses ^J, which Enter sends anyway).
        // [impl->dsn~category-jump~1]
        KeyCombination jumpKeys = new KeyCodeCombination(KeyCode.J, KeyCombination.SHORTCUT_DOWN);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (find.match(event)) {
                openFind();
                event.consume();
            } else if (back.match(event)) {
                navigateHistory(-1);
                event.consume();
            } else if (forward.match(event)) {
                navigateHistory(1);
                event.consume();
            } else if (addTaskKeys.match(event)) {
                Platform.runLater(() -> addTask(selectedGroup()));
                event.consume();
            } else if (jumpKeys.match(event)) {
                openCategoryJump();
                event.consume();
            }
        });
        // App stylesheet (bundled resource, layered over the AtlantaFX theme):
        // the off-focus selection accent (`dsn~main-window~2`), the shared
        // size + flat styling of the task-list toolbar's icon controls
        // (`dsn~task-list-toolbar~3`), and every colour the UI sets.
        // On **every** window, not only this one: a dialog builds a scene of
        // its own, and a rule here — an icon's fill above all — would miss it.
        // [impl->dsn~theme-select~8]
        URL css = MainWindow.class.getResource("main.css");
        if (css != null) {
            String sheet = css.toExternalForm();
            scene.getStylesheets().add(sheet);
            Themes.addToEveryWindow(sheet);
        }
        stage.setScene(scene);
        // The extension's target rings as the window/taskbar icon, in the sizes
        // a window manager picks from (JavaFX asks for 32 on Windows, 128 on
        // Linux and macOS); 128 is the raster's own size, so nothing is
        // upscaled. [impl->dsn~app-icon~4]
        stage.getIcons().setAll(AppIcon.image(16), AppIcon.image(32), AppIcon.image(64), AppIcon.image(128));
        stage.show();
        // The shell's scene is on screen now, and JavaFX will not restyle it
        // for a stylesheet it already holds: force the restyle a theme switch
        // in the settings dialog did (field report 2026-09-13).
        // [impl->dsn~theme-select~8]
        Themes.refresh();
        // If the user-agent stylesheet was taken at startup and lost since,
        // this is where it shows.
        // [impl->dsn~everforest-theme~2]
        Themes.checkApplied("window shown", stage.getScene());
        // What actually fails on an unthemed start is the theme's variables,
        // not the URL: check those now and again once the start-up stalls are
        // over, refreshing when they do not resolve.
        // [impl->dsn~theme-select~8]
        Themes.verify("window shown");
        PauseTransition settled = new PauseTransition(Duration.seconds(THEME_RECHECK_SECONDS));
        settled.setOnFinished(event -> Themes.verify(THEME_RECHECK_SECONDS + " s after start"));
        settled.play();
    }

    /// Seconds after the window is shown when the theme is checked again —
    /// past the FX-thread stalls of a busy start (6–13 s on 2026-09-16).
    private static final int THEME_RECHECK_SECONDS = 20;

    /// Puts the selected category in front of the app name, with the live
    /// counts of that category's tasks between them
    /// (`JabRef | 1 waiting | 2 working | ContextSwitcher`), so a window
    /// picker or taskbar entry says which category the window is on and
    /// whether anything there wants the user.
    // [impl->dsn~window-title-category~3]
    private void showCategoryInTitle(String group) {
        titleCategory = group;
        updateWindowTitle();
    }

    /// Rebuilds the window title from [#titleCategory] and the live statuses.
    /// Called whenever the selection, the task list, or the poller's statuses
    /// change.
    // [impl->dsn~window-title-category~3]
    private void updateWindowTitle() {
        if (stage == null) {
            return;
        }
        int waiting = 0;
        int working = 0;
        for (TaskEntry entry : entries) {
            if (!entry.group().equals(titleCategory)) {
                continue;
            }
            if (awaitsInput(entry)) {
                waiting++;
            } else if (entry instanceof TaskEntry.Loaded loaded
                    && "working".equals(runningStatusFor(loaded.task()))) {
                working++;
            }
        }
        stage.setTitle(windowTitle(titleCategory, waiting, working));
    }

    /// The title text: the category (empty for root-level tasks), then the
    /// non-zero counts, then the app name — each segment `|`-separated. The
    /// app name goes last because window buttons truncate the end: what
    /// distinguishes two ContextSwitcher windows must come first.
    // [impl->dsn~window-title-category~3]
    static String windowTitle(String category, int waiting, int working) {
        StringBuilder title = new StringBuilder();
        if (!category.isEmpty()) {
            title.append(category).append(" | ");
        }
        if (waiting > 0) {
            title.append(waiting).append(" waiting | ");
        }
        if (working > 0) {
            title.append(working).append(" working | ");
        }
        return title.append("ContextSwitcher").toString();
    }

    /// The find bar: a search field (a leading magnifier glyph and a trailing
    /// clear ✕ embedded in the field, like GemsFX's `SearchTextField`) that
    /// filters the task list live, followed by a match count. Always visible;
    /// Ctrl+F focuses the field and selects its text. Typing filters; Enter
    /// selects the first match; Escape clears the query and returns focus to the
    /// list. The ✕ shows only while the field has text and clears it in place.
    // [impl->dsn~task-find~9]
    private Node findBar() {
        TextField field = new TextField();
        field.setPromptText(FIND_PROMPT);
        // A borderless inner field: the surrounding .search-field box draws the
        // input frame, so the magnifier and ✕ sit inside one control.
        field.getStyleClass().addAll(Styles.SMALL, "search-inner");
        HBox.setHgrow(field, Priority.ALWAYS);
        field.textProperty().addListener((obs, old, text) -> applySearch(text));
        field.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                closeFind();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                selectFirstMatch();
                event.consume();
            } else if (event.getCode() == KeyCode.BACK_SPACE && scopeCategory != null
                    && field.getCaretPosition() == 0 && field.getSelection().getLength() == 0) {
                // Backspace at the start of the field takes the chip before it,
                // as in any chip input. [impl->dsn~category-search~3]
                scopeToCategory(null);
                event.consume();
            }
        });

        // Both glyphs are sized from the field's own font (not a fixed pixel
        // size), so they scale with it: the magnifier at 1×, the clear ✕ a touch
        // smaller so it sits unobtrusively inside the field.
        SvgNode glass = new SvgNode(MDIInterface.MAGNIFY.path());
        glass.sizeProperty().bind(Bindings.createDoubleBinding(
                () -> field.getFont().getSize(), field.fontProperty()));

        // The clear ✕: shown only while the field has text, clears it in place
        // (keeping focus in the field), unlike Escape which leaves the bar.
        SvgNode clearGlyph = new SvgNode(MDIInterface.CLOSE.path());
        clearGlyph.sizeProperty().bind(Bindings.createDoubleBinding(
                () -> field.getFont().getSize() * 0.85, field.fontProperty()));
        Button clear = new Button(null, clearGlyph);
        clear.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        clear.setTooltip(new Tooltip("Clear search"));
        clear.setOnAction(event -> {
            field.clear();
            field.requestFocus();
        });
        clear.visibleProperty().bind(field.textProperty().isEmpty().not());
        clear.managedProperty().bind(clear.visibleProperty());

        // The category scope chip, between the magnifier and the text: a press
        // on a category header's name puts it there, its × takes it away.
        // [impl->dsn~category-search~3]
        scopeChipLabel.setId("find-scope-label");
        SvgNode removeGlyph = new SvgNode(MDIInterface.CLOSE.path());
        removeGlyph.sizeProperty().bind(Bindings.createDoubleBinding(
                () -> field.getFont().getSize() * 0.7, field.fontProperty()));
        Button removeScope = new Button(null, removeGlyph);
        removeScope.setId("find-scope-remove");
        removeScope.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        removeScope.setFocusTraversable(false);
        removeScope.setTooltip(new Tooltip("Search all categories"));
        removeScope.setOnAction(event -> scopeToCategory(null));
        scopeChip.getChildren().setAll(scopeChipLabel, removeScope);
        scopeChip.setId("find-scope-chip");
        scopeChip.setAlignment(Pos.CENTER_LEFT);
        scopeChip.getStyleClass().add("find-scope-chip");
        scopeChip.setMinWidth(Region.USE_PREF_SIZE);
        scopeChip.setMaxHeight(Region.USE_PREF_SIZE);
        scopeChip.setVisible(false);
        scopeChip.managedProperty().bind(scopeChip.visibleProperty());

        HBox searchBox = new HBox(glass, scopeChip, field, clear);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("search-field");

        findMatchLabel.getStyleClass().addAll(Styles.TEXT_MUTED, "find-match-count");
        HBox bar = new HBox(6, searchBox, findMatchLabel);
        HBox.setHgrow(searchBox, Priority.ALWAYS);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 6, 4, 6));
        this.searchField = field;
        return bar;
    }

    /// Whether `group`'s header is to carry the selection colours: it holds the
    /// selected task, or is the selected row itself.
    // [impl->dsn~current-category-highlight~1]
    private boolean isCurrentGroup(String group) {
        return group.equals(currentGroup);
    }

    /// Moves the category highlight to the selected row's category (`""`: an
    /// ungrouped task or a label bucket, which has no category header). Only a
    /// change repaints — the cells are asked to re-render, not the rows
    /// rebuilt, since nothing about the list's content changed.
    // [impl->dsn~current-category-highlight~1]
    private void markCurrentGroup(String group) {
        if (group.equals(currentGroup)) {
            return;
        }
        currentGroup = group;
        ListView<Object> list = taskList;
        if (list != null) {
            list.refresh();
        }
        // The pinned header is no cell of the list, so refresh() skips it.
        stickyIndex = -1;
        updateStickyHeader();
    }

    /// Scopes the find field to `group` — its chip in the field, the list
    /// narrowed to that category — or back to the whole list (null), and puts
    /// the keyboard in the field so typing searches the category. The rebuild
    /// is deferred: this runs from a mouse press inside a cell, which the
    /// rebuild would replace under the event.
    // [impl->dsn~category-search~3]
    private void scopeToCategory(@Nullable String group) {
        TextField field = searchField;
        if (!Objects.equals(scopeCategory, group)) {
            scopeCategory = group;
            scopeChipLabel.setText(group == null ? "" : group);
            scopeChip.setVisible(group != null);
            if (field != null) {
                field.setPromptText(group == null ? FIND_PROMPT : "Find in " + group + "…");
            }
            Platform.runLater(() -> {
                rebuildRows();
                updateMatchLabel();
            });
        }
        if (field != null) {
            field.requestFocus();
        }
    }

    /// Opens the jump-to-category popup over the task list: a field that
    /// narrows the category names as the user types, the active desktop's
    /// categories grouped first. Enter or a click selects the category's header
    /// (expanding it, lifting a scope to another category); Escape closes.
    /// The active desktop is read afresh in the background — `activeDesktop`
    /// is only kept current while a desktop watch runs — and regroups the rows.
    // [impl->dsn~category-jump~1]
    private void openCategoryJump() {
        ListView<Object> list = taskList;
        if (list == null || list.getScene() == null) {
            return;
        }
        TextField field = new TextField();
        field.setId("category-jump-field");
        field.setPromptText("Jump to category…");
        ListView<CategoryJump.Row> choices = new ListView<>();
        choices.setId("category-jump-list");
        choices.setPrefHeight(320);
        choices.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(CategoryJump.@Nullable Row row, boolean empty) {
                super.updateItem(row, empty);
                boolean header = !empty && row != null && row.header();
                setText(empty || row == null ? null : row.text());
                setDisable(header);
                getStyleClass().remove(Styles.TEXT_CAPTION);
                if (header) {
                    getStyleClass().add(Styles.TEXT_CAPTION);
                }
            }
        });
        VBox box = new VBox(4, field, choices);
        box.getStyleClass().add("category-jump");
        box.setPadding(new Insets(6));
        box.setPrefWidth(Math.max(280, list.getWidth() - 16));
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(box);
        popup.getScene().getStylesheets().setAll(list.getScene().getStylesheets());

        Runnable refill = () -> {
            CategoryJump.Row picked = choices.getSelectionModel().getSelectedItem();
            List<CategoryJump.Row> rows =
                    CategoryJump.rows(folders, this::groupDesktop, activeDesktop, field.getText());
            choices.getItems().setAll(rows);
            int index = picked == null ? -1 : rows.indexOf(picked);
            selectJumpRow(choices, index < 0 ? 0 : index, 1);
        };
        field.textProperty().addListener((observable, before, after) -> refill.run());
        field.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            int at = choices.getSelectionModel().getSelectedIndex();
            switch (event.getCode()) {
                case DOWN -> selectJumpRow(choices, at + 1, 1);
                case UP -> selectJumpRow(choices, at - 1, -1);
                case ENTER -> jumpToCategory(popup, choices.getSelectionModel().getSelectedItem());
                case ESCAPE -> popup.hide();
                default -> {
                    return;
                }
            }
            event.consume();
        });
        choices.setOnMouseClicked(event -> jumpToCategory(popup, choices.getSelectionModel().getSelectedItem()));
        refill.run();

        Bounds bounds = list.localToScreen(list.getBoundsInLocal());
        popup.show(list.getScene().getWindow(), bounds.getMinX() + 8, bounds.getMinY() + 8);
        field.requestFocus();

        CompletableFuture.supplyAsync(() -> new WindowsVirtualDesktopFocus(new LocalCommandRunner()).current())
                .thenAccept(desktop -> Platform.runLater(() -> {
                    if (desktop.read()) {
                        updateActiveDesktop(desktop.name());
                        if (popup.isShowing()) {
                            refill.run();
                        }
                    }
                }));
    }

    /// Selects the first category row from `index` on in direction `step`,
    /// skipping headings; stays put when there is none.
    // [impl->dsn~category-jump~1]
    private static void selectJumpRow(ListView<CategoryJump.Row> choices, int index, int step) {
        List<CategoryJump.Row> rows = choices.getItems();
        for (int i = index; i >= 0 && i < rows.size(); i += step) {
            if (!rows.get(i).header()) {
                choices.getSelectionModel().select(i);
                choices.scrollTo(Math.max(0, i - 1));
                return;
            }
        }
    }

    /// Closes the popup and selects the picked category's header in the list.
    // [impl->dsn~category-jump~1]
    private void jumpToCategory(Popup popup, CategoryJump.@Nullable Row row) {
        if (row == null || row.header()) {
            return;
        }
        popup.hide();
        String group = row.text();
        if (scopeCategory != null && !scopeCategory.equals(group)) {
            scopeToCategory(null);
        }
        // After the scope's deferred rebuild.
        Platform.runLater(() -> {
            if (selectCategory(group)) {
                if (taskList != null) {
                    taskList.requestFocus();
                }
            } else {
                statusBar.message("Category " + group + " is hidden by a list filter.");
            }
        });
    }

    /// A press on a category header's name: scopes the find field to that
    /// category, or — when it is already the scope — back to every category.
    // [impl->dsn~category-search~3]
    private void toggleCategoryScope(String group) {
        scopeToCategory(group.equals(scopeCategory) ? null : group);
    }

    /// The directory a category's task workspaces live under — its
    /// `workspacesRoot`, else its fixed `workdir` — or null when it names neither.
    // [impl->dsn~category-header-path~1]
    private @Nullable String groupPath(String group) {
        GroupConfig config = groupDefaults(group);
        String root = config.workspacesRoot();
        return root != null && !root.isBlank() ? root
                : config.workdir() != null && !config.workdir().isBlank() ? config.workdir() : null;
    }

    /// Focuses the find field (selecting any existing text so a fresh query
    /// overwrites the last one). The bar itself is always visible.
    // [impl->dsn~task-find~9]
    private void openFind() {
        TextField field = searchField;
        if (field == null) {
            return;
        }
        field.requestFocus();
        field.selectAll();
    }

    /// Clears the query and returns focus to the list. The bar stays visible.
    // [impl->dsn~task-find~9]
    private void closeFind() {
        TextField field = searchField;
        if (field != null) {
            field.clear();
        }
        // clear() fires the listener only when the field was non-empty; set the
        // query explicitly so an already-empty field still resets cleanly.
        searchQuery = "";
        rebuildRows();
        updateMatchLabel();
        ListView<Object> list = taskList;
        if (list != null) {
            list.requestFocus();
            // The selected task was one of a handful of matches; in the restored
            // full list its row is usually far down, so scroll it back into view.
            scrollRowIntoView(list, list.getSelectionModel().getSelectedIndex());
        }
    }

    /// Applies the current find query: re-flattens the rows (filtered) and
    /// updates the match count.
    // [impl->dsn~task-find~9]
    private void applySearch(String text) {
        boolean newQuery = !text.equals(searchQuery);
        searchQuery = text;
        int generation = searchGeneration.incrementAndGet();
        if (text.strip().isEmpty()) {
            searchHits = Set.of();
            searchPending = false;
            rebuildRows();
            restoreSelectionBeforeSearch();
            updateMatchLabel();
            return;
        }
        // A changed query empties the list until its hits land: showing the
        // previous rows (the full list, on the first keystroke) meanwhile read
        // as a search that does nothing. A re-run of the same query after an
        // entry change keeps the previous hits instead, so it does not flicker.
        // Emptying the rows drops the selection, and rebuildRows keeps no
        // selection a search hides, so the task is remembered here and
        // re-highlighted once the hits show it again.
        if (newQuery) {
            ListView<Object> list = taskList;
            if (list != null && list.getSelectionModel().getSelectedItem() instanceof TaskEntry.Loaded loaded) {
                selectionBeforeSearch = loaded.id();
            }
            searchHits = Set.of();
            searchPending = true;
            rebuildRows();
            findMatchLabel.setText("Searching…");
        }
        // Match on a worker: a keystroke must never wait for the queue-file
        // reads, and a newer query cancels the one still running. The task
        // fields are in memory, so their hits show at once; the slow
        // chat-message fallback then adds its hits to them.
        List<TaskEntry> snapshot = List.copyOf(entries);
        searchExecutor.execute(() -> {
            Set<String> hits = new HashSet<>();
            List<TaskEntry> misses = new ArrayList<>();
            for (TaskEntry entry : snapshot) {
                if (TaskSearch.matches(entry, text)) {
                    hits.add(entry.id());
                } else {
                    misses.add(entry);
                }
            }
            publishSearchHits(generation, Set.copyOf(hits), misses.isEmpty());
            if (misses.isEmpty()) {
                return;
            }
            for (TaskEntry entry : misses) {
                if (searchGeneration.get() != generation) {
                    return;
                }
                if (TaskSearch.matches(entry, text, () -> messageTextFor(entry))) {
                    hits.add(entry.id());
                }
            }
            publishSearchHits(generation, Set.copyOf(hits), true);
        });
    }

    /// Shows `hits` for the query of `generation`, unless a newer query has
    /// started meanwhile; `done` ends the "Searching…" state.
    // [impl->dsn~task-find~9]
    private void publishSearchHits(int generation, Set<String> hits, boolean done) {
        Platform.runLater(() -> {
            if (searchGeneration.get() != generation) {
                return;
            }
            searchHits = hits;
            searchPending = !done;
            rebuildRows();
            restoreSelectionBeforeSearch();
            updateMatchLabel();
        });
    }

    /// Re-selects the task [#applySearch] remembered before emptying the rows,
    /// if nothing else got selected meanwhile and the rows show it again.
    // [impl->dsn~task-find~9]
    private void restoreSelectionBeforeSearch() {
        String id = selectionBeforeSearch;
        selectionBeforeSearch = null;
        ListView<Object> list = taskList;
        if (id == null || list == null || list.getSelectionModel().getSelectedItem() != null) {
            return;
        }
        int row = rowOf(id);
        if (row >= 0) {
            list.getSelectionModel().select(row);
        }
    }

    /// The count of task rows currently matching the query (blank query = no
    /// label). Group headers and add rows are not counted.
    // [impl->dsn~task-find~9]
    private void updateMatchLabel() {
        if (searchQuery.strip().isEmpty()) {
            findMatchLabel.setText("");
            return;
        }
        long matches = visibleRows.stream().filter(TaskEntry.class::isInstance).count();
        // [impl->dsn~search-hits-off-desktop~1]
        findMatchLabel.setText(matches + (matches == 1 ? " match" : " matches")
                + (hitsOffDesktop == 0 ? "" : " · %d on other desktops".formatted(hitsOffDesktop))
                + (searchPending ? " · searching messages…" : ""));
    }

    /// Selects (and scrolls to) the first matching task row — Enter in the find
    /// field — and moves focus to the list so the arrow keys and switch work.
    // [impl->dsn~task-find~9]
    private void selectFirstMatch() {
        ListView<Object> list = taskList;
        if (list == null) {
            return;
        }
        for (int i = 0; i < visibleRows.size(); i++) {
            if (visibleRows.get(i) instanceof TaskEntry.Loaded) {
                list.getSelectionModel().select(i);
                scrollRowIntoView(list, i);
                list.requestFocus();
                return;
            }
        }
    }

    /// The live `@cs_status` for a task's window, or null when unknown (no
    /// tmux window id, or the window has not published a status). Keyed the
    /// same way the poller builds its map (`host windowId`). A suspended
    /// task has none: its window was killed, so a window matching a
    /// lingering id belongs to some other context.
    // [impl->dsn~task-running-indicator~7]
    private @Nullable String runningStatusFor(Task task) {
        if (task.status() == TaskStatus.SUSPENDED
                || task.remote() == null || task.tmux() == null || task.tmux().window() == null) {
            return null;
        }
        return runningStatusByKey.get(task.remote() + " " + task.tmux().window());
    }

    /// Replaces the published-session-id map with the poller's latest snapshot.
    /// No repaint: nothing on a row shows it — it is only read when a dialog
    /// asks who owns a window. Must run on the FX thread.
    // [impl->dsn~tmux-window-ownership~4]
    public void updateSessionIds(Map<String, String> fresh) {
        sessionIdByKey.clear();
        sessionIdByKey.putAll(fresh);
    }

    /// Replaces the pane-command map with the poller's latest snapshot and
    /// re-renders the PR header, whose `Claude` button it decides. Must run on
    /// the FX thread.
    // [impl->dsn~start-claude-button~2]
    public void updateWindowCommands(Map<String, String> fresh) {
        if (commandByKey.equals(fresh)) {
            return;
        }
        commandByKey.clear();
        commandByKey.putAll(fresh);
        renderPrHeader();
    }

    /// Whether the task's own tmux window is live but running something other
    /// than Claude — a start that failed, or a session that exited. Unknown
    /// (the poll has not seen the window) counts as "has Claude": the button
    /// only appears on evidence, never on silence.
    // [impl->dsn~start-claude-button~2]
    private boolean lacksClaude(Task task) {
        Task.TmuxConfig tmux = task.tmux();
        if (task.status() == TaskStatus.SUSPENDED
                || task.remote() == null || tmux == null || tmux.window() == null) {
            return false;
        }
        String command = commandByKey.get(task.remote() + " " + tmux.window());
        return command != null && !"claude".equals(command);
    }

    /// The Claude session the task's recorded window really hosts, when the
    /// last poll saw a *different* one than the task's — else null, including
    /// for a window the poll has not seen yet (absent, so no evidence).
    /// The dialog's half of [TmuxWindowOwnership]'s check: it runs on the FX
    /// thread and must not do an ssh round-trip, so it reads the id that rides
    /// the status poll.
    // [impl->dsn~tmux-window-ownership~4]
    private @Nullable String foreignWindowOwner(Task task) {
        Task.TmuxConfig tmux = task.tmux();
        Task.ClaudeConfig claude = task.claude();
        if (tmux == null || tmux.window() == null || task.remote() == null || claude == null) {
            return null;
        }
        String published = sessionIdByKey.get(task.remote() + " " + tmux.window());
        return TmuxWindowOwnership.hijacked(claude.sessionId(), published) ? published : null;
    }

    /// The live state of a PR URL, or null when the poller has not resolved it
    /// yet.
    // [impl->dsn~pr-state-indicator~3]
    private @Nullable PrState prStateFor(String url) {
        PrInfo info = prInfoByUrl.get(url);
        return info == null ? null : info.state();
    }

    /// Rebuilds [#prHeader] for the previewed task: one row per PR URL —
    /// the row icon's glyph, the PR title as a link that focuses (or opens)
    /// the PR's tab like the icon does (right-click: open / copy link), and
    /// the PR's `status:` labels as chips ([#MAX_PR_LABELS] of them, then a
    /// `…`). Until `gh` resolved the PR the row shows the task file's own
    /// title for the URL (or the URL itself) with the muted `?` glyph.
    // [impl->dsn~pr-header-line~4]
    private void renderPrHeader() {
        prHeader.getChildren().clear();
        Task task = previewedTask;
        // [impl->dsn~open-in-intellij~3]
        ideaButton.setVisible(task != null);
        ideaButton.setManaged(task != null);
        ideaButton.setDisable(task != null && task.lacksWorktree());
        // [impl->dsn~start-claude-button~2]
        boolean needsClaude = task != null && lacksClaude(task);
        claudeButton.setVisible(needsClaude);
        claudeButton.setManaged(needsClaude);
        if (needsClaude) {
            claudeButton.setDisable(false);
        }
        // [impl->dsn~task-fork~1]
        terminal.setForkAvailability(forkAvailable(task), forkAvailabilityReason(task));
        if (task == null) {
            return;
        }
        for (Task.UrlEntry pr : task.prEntries()) {
            PrInfo info = prInfoByUrl.get(pr.url());
            String title = info != null && !info.title().isBlank() ? info.title()
                    : pr.title() != null && !pr.title().isBlank() ? pr.title() : pr.url();
            Hyperlink link = new Hyperlink(title);
            link.setTooltip(new Tooltip(pr.url()));
            link.setOnAction(event -> focusPr(task, pr));
            link.setOnMouseEntered(event -> statusBar.hover(pr.display()));
            link.setOnMouseExited(event -> statusBar.hover(null));
            link.setMinWidth(0);
            MenuItem openPr = new MenuItem("Open PR");
            openPr.setOnAction(event -> focusPr(task, pr));
            MenuItem copyLink = new MenuItem("Copy link");
            copyLink.setOnAction(event -> copyTaskUrl(pr));
            // The list grows on its own — every URL Claude mentions is filed
            // (`dsn~task-url-collect~1`) — so each line carries its own way
            // out: a trash icon that appears while the mouse is on the line,
            // like the task rows' hover actions, plus the same action in the
            // right-click menu for the keyboard/menu path. A copy icon sits
            // before it.
            // [impl->dsn~task-remove-link~2]
            MenuItem removeLink = new MenuItem("Remove link");
            removeLink.setOnAction(event -> removeTaskUrl(task, pr));
            link.setContextMenu(new ContextMenu(openPr, copyLink, removeLink));
            Button trash = iconButton(MDIInterface.TRASH_CAN_OUTLINE, "Remove this link");
            trash.setOnAction(event -> removeTaskUrl(task, pr));
            Button copy = iconButton(MDITechnology.CONTENT_COPY, "Copy link");
            copy.setOnAction(event -> copyTaskUrl(pr));
            HBox row = new HBox(6, TaskListCell.prStateIcon(prStateFor(pr.url()), pr.display()), link);
            // Stays managed, so the line does not jump on hover.
            trash.visibleProperty().bind(row.hoverProperty());
            copy.visibleProperty().bind(row.hoverProperty());
            row.setAlignment(Pos.CENTER_LEFT);
            if (info != null) {
                List<String> labels = statusLabels(List.copyOf(info.labels().keySet()));
                for (String label : labels.subList(0, Math.min(labels.size(), MAX_PR_LABELS))) {
                    Label chip = TagChips.chip(label, info.labels().get(label));
                    chip.setText(statusChipText(pr.url(), label));
                    // The header must never widen the terminal lane: a chip
                    // that insists on its preferred width pushes the queue
                    // pane off-screen (field report 2026-09-07).
                    chip.setMinWidth(0);
                    row.getChildren().add(chip);
                }
                if (labels.isEmpty() && prNumber(pr.url()) != null) {
                    // No chip to carry the number, so the line names it alone.
                    Label number = new Label(prNumber(pr.url()));
                    number.getStyleClass().add(Styles.TEXT_MUTED);
                    row.getChildren().add(number);
                }
                if (labels.size() > MAX_PR_LABELS) {
                    Label more = new Label("…");
                    more.setTooltip(new Tooltip(String.join(System.lineSeparator(),
                            labels.subList(MAX_PR_LABELS, labels.size()))));
                    row.getChildren().add(more);
                }
            }
            row.getChildren().addAll(copy, trash);
            row.setMinWidth(0);
            prHeader.getChildren().add(row);
        }
        prHeader.setPadding(prHeader.getChildren().isEmpty() ? Insets.EMPTY : new Insets(4, 6, 4, 6));
    }

    /// Whether the "Fork…" button forks `task`: it needs a remote, a tmux
    /// window and a recorded Claude session id — `--resume <id>` has nothing
    /// to resume without one (`dsn~task-fork~1`).
    // [impl->dsn~task-fork~1]
    private static boolean forkAvailable(@Nullable Task task) {
        if (task == null || task.remote() == null || task.tmux() == null) {
            return false;
        }
        Task.ClaudeConfig claude = task.claude();
        return claude != null && claude.sessionId() != null && !claude.sessionId().isBlank();
    }

    /// The "Fork…" button's tooltip: why it is disabled, or what it does.
    // [impl->dsn~task-fork~1]
    private static String forkAvailabilityReason(@Nullable Task task) {
        if (task == null) {
            return "Select a task to fork its Claude conversation.";
        }
        if (task.remote() == null || task.tmux() == null) {
            return "Fork needs a remote task with a running tmux window.";
        }
        Task.ClaudeConfig claude = task.claude();
        if (claude == null || claude.sessionId() == null || claude.sessionId().isBlank()) {
            return "Fork needs a recorded Claude session id (\"Sync tmux windows…\" picks one up).";
        }
        return "Starts a new task in the same category, its Claude session forked"
                + " from this task's conversation.";
    }

    /// The "Fork…" button's dialog: a text area for what the fork should do,
    /// the same model/effort pickers the Add-task dialog offers, **Back**
    /// (cancel) and **Fork** (default, disabled while blank). Closes right
    /// away on Fork — the creation runs in the background, and `source`
    /// stays the task shown ([TaskForker], `dsn~task-fork~1`).
    // [impl->dsn~task-fork~1]
    private void openForkDialog(Task source) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(dialogOwner());
        dialog.setTitle("Fork " + source.title());
        dialog.setResizable(true);
        dialog.setHeaderText("What should the fork do?");
        ButtonType forkType = new ButtonType("Fork", ButtonBar.ButtonData.OK_DONE);
        ButtonType backType = new ButtonType("Back", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(backType, forkType);

        TextArea field = new TextArea();
        field.setId("fork-instruction");
        field.setPromptText("What should the fork do?");
        field.setWrapText(true);
        field.setPrefRowCount(3);
        installAttachments.accept(field);

        Label keysHint = new Label("Enter starts a new line; three Enters or three"
                + " Ctrl+Enters in a row fork the task.");
        keysHint.getStyleClass().add(Styles.TEXT_MUTED);
        // [impl->dsn~claude-mode-select~3]
        ComboBox<String> modelBox = QueuePane.modeBox("model: as is", ClaudeMode.MODELS);
        ComboBox<String> effortBox = QueuePane.modeBox("effort: as is", ClaudeMode.EFFORTS);
        ClaudeMode remembered = lastMode();
        QueuePane.selectMode(modelBox, remembered.model());
        QueuePane.selectMode(effortBox, remembered.effort());
        HBox modeRow = new HBox(6, modelBox, effortBox);

        VBox content = new VBox(6, field, modeRow, keysHint);
        VBox.setVgrow(field, Priority.ALWAYS);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);

        Button forkButton = (Button) dialog.getDialogPane().lookupButton(forkType);
        forkButton.setDefaultButton(true);
        forkButton.setDisable(true);
        field.textProperty().addListener((obs, old, text) ->
                forkButton.setDisable(text == null || text.isBlank()));
        // req~compose-key-conventions~5: the field holds a message for a
        // Claude chat, so it gets the same commit chords as the queue boxes
        // and the Add-task dialog ([QueuePane#installComposeKeys]) — a
        // shared behavior needs a UI test at each call site.
        Runnable fireFork = () -> {
            if (!forkButton.isDisabled()) {
                forkButton.fire();
            }
        };
        QueuePane.installComposeKeys(field, fireFork, fireFork);
        Platform.runLater(field::requestFocus);

        ButtonType clicked = dialog.showAndWait().orElse(backType);
        if (clicked != forkType) {
            return;
        }
        String instruction = field.getText() == null ? "" : field.getText().strip();
        if (instruction.isEmpty()) {
            return;
        }
        ClaudeMode mode = new ClaudeMode(QueuePane.modeValue(modelBox), QueuePane.modeValue(effortBox));
        rememberMode(mode);
        onFork.fork(source, instruction, mode);
    }

    /// How many PR labels the header line shows before it ends in a `…`
    /// (the rest in its tooltip).
    static final int MAX_PR_LABELS = 3;

    /// The labels the PR header shows: the `status:` ones. A JabRef PR
    /// carries a dozen `component:`/`dev:` labels that say what it touches —
    /// knowable from the diff — while the status is the fact that changes
    /// under you and decides whether the PR needs you right now.
    // [impl->dsn~pr-header-line~4]
    static List<String> statusLabels(List<String> labels) {
        return labels.stream()
                .filter(label -> label.toLowerCase(Locale.ROOT).startsWith("status:"))
                .toList();
    }

    /// A status chip's text: `17148 · ready-for-review` for the label
    /// `status: ready-for-review` on PR 17148. The number tells two PRs'
    /// chips apart (they often carry the same status), and dropping the
    /// `status:` prefix pays for the width it takes.
    // [impl->dsn~pr-header-line~4]
    static String statusChipText(String prUrl, String label) {
        String status = label.substring("status:".length()).strip();
        String number = prNumber(prUrl);
        return number == null ? status : number + " · " + status;
    }

    /// The PR number of a GitHub PR URL, `!<iid>` for a GitLab MR URL
    /// (GitLab's own spelling), or null when `url` is neither.
    static @Nullable String prNumber(String url) {
        Matcher pr = PrTitleLookup.PR_URL.matcher(url);
        if (pr.matches()) {
            return pr.group(3);
        }
        Matcher mr = GitLabMrLookup.MR_URL.matcher(url);
        return mr.matches() ? "!" + mr.group(3) : null;
    }

    /// The task's refactoring summary, or null when the poller has not
    /// resolved it yet (https://github.com/contextswitcher/contextswitcher-private/issues/50).
    // [impl->dsn~refactoring-analysis-poller~1]
    private @Nullable RefactoringSummary refactoringSummaryFor(Task task) {
        return refactoringsByTask.get(task.id());
    }

    /// Opens the task's refactoring web view: flushes the editor (the group
    /// config's `baseBranch` may be an unsaved edit) and hands off to the
    /// `Main` opener, which resolves config and remote fresh.
    // [impl->dsn~refactoring-web-view~1]
    private void showRefactorings(Task task, Runnable onSettled) {
        saveEditorIfDirty();
        onOpenRefactorings.accept(task, onSettled);
    }

    /// How many messages the task has queued, read fresh from disk — the queue
    /// files are tiny and local, so a per-render read is cheaper than caching.
    /// The row re-reads only when the list refreshes (a poll tick or
    /// [#refreshTaskRows] after a queue edit).
    // [impl->dsn~message-queue-count-badge~1]
    private int queuedCountFor(Task task) {
        return QueueFile.load(QueueFile.file(queuesDir, task.id())).size();
    }

    /// The task's chat messages for the find bar: its queued messages plus
    /// every sent one, read fresh from disk like the queued-count badge. Only
    /// called for an entry the query missed on its own fields, so a search
    /// touches the queue files of the non-matching tasks only.
    // [impl->dsn~task-find~9]
    private String messageTextFor(TaskEntry entry) {
        String sent = QueueFile.loadSent(QueueFile.sentFile(queuesDir, entry.id()));
        return String.join("\n", QueueFile.load(QueueFile.file(queuesDir, entry.id())))
                + '\n' + String.join("\n",
                        QueueFile.load(QueueFile.sentHistoryFile(queuesDir, entry.id())))
                + (sent == null ? "" : "\n" + sent);
    }

    /// Re-renders the task rows so their queued-count badges pick up a queue
    /// change; called (via `Main`) whenever [QueuePane] writes a queue file.
    // [impl->dsn~message-queue-count-badge~1]
    public void refreshTaskRows() {
        if (taskList != null) {
            taskList.refresh();
        }
    }

    /// Focuses (or opens) a tab in the browser — the click handler of a PR
    /// icon and of a plain link icon alike, carrying the entry of the icon that
    /// was clicked. The status line names its title next to the URL, like the
    /// hover does; the browser gets the URL alone.
    /// The task's category desktop rides along (null when it is ungrouped or
    /// its category names no `desktop:`) so a tab that has to be opened is
    /// opened *there*; it is resolved fresh from disk like the header's
    /// desktop button does.
    // [impl->dsn~pr-state-indicator~3]
    // [impl->dsn~task-link-icons~2]
    // [impl->dsn~pr-open-on-category-desktop~3]
    private void focusPr(Task task, Task.UrlEntry pr) {
        statusBar.message("Opening " + pr.display() + " …");
        String group = groupOf(task.id());
        onFocusUrlInBrowser.accept(pr.url(), group.isEmpty() ? null : groupDesktop(group));
    }

    /// Merges the poller's latest snapshot into the PR-state map, keeping the
    /// **last known** state for a URL the poll did not resolve this tick — a
    /// transient `gh` failure (offline, rate-limited, timeout) must not flip a
    /// resolved icon back to the muted `?`. Repaints only when something
    /// actually changed (avoids the periodic flicker). Must run on the FX
    /// thread. A PR whose URL leaves every task lingers harmlessly (read only
    /// via the current `task.prUrl()`).
    // [impl->dsn~pr-state-indicator~3]
    /// The visible row list, read-only — [Main] derives which tasks are on
    /// screen (after filters, collapsed groups, and the Done section) so only
    /// their PRs are polled against the GitHub API, and reacts when rows
    /// appear.
    // [impl->dsn~pr-poll-economy~2]
    public ObservableList<Object> visibleRowsObservable() {
        return FXCollections.unmodifiableObservableList(visibleRows);
    }

    public void updatePrStates(Map<String, PrInfo> fresh) {
        boolean changed = false;
        for (Map.Entry<String, PrInfo> entry : fresh.entrySet()) {
            if (!entry.getValue().equals(prInfoByUrl.put(entry.getKey(), entry.getValue()))) {
                changed = true;
            }
        }
        if (changed && taskList != null) {
            // A new state can move a task to another bucket.
            // [impl->dsn~pr-status-grouping~1]
            if (groupings.contains(Grouping.PR_STATUS)) {
                rebuildRows();
            } else {
                taskList.refresh();
            }
            renderPrHeader();
        }
    }

    /// The distinct PR-status buckets of `task`'s resolved PRs.
    // [impl->dsn~pr-status-grouping~1]
    private List<String> prStatusBuckets(Task task, Set<String> labelRepos) {
        return task.prEntries().stream()
                .map(pr -> {
                    PrInfo info = prInfoByUrl.get(pr.url());
                    return info == null ? null : PrStatusGroup.bucket(pr.url(), info, labelRepos);
                })
                .filter(Objects::nonNull)
                .distinct().toList();
    }

    /// Merges fresh per-task refactoring summaries in — same shape as
    /// [#updatePrStates]: never removes (a failed poll keeps the last known
    /// count), repaints without rebuilding.
    // [impl->dsn~refactoring-analysis-poller~1]
    public void updateRefactorings(Map<String, RefactoringSummary> fresh) {
        boolean changed = false;
        for (Map.Entry<String, RefactoringSummary> entry : fresh.entrySet()) {
            if (!entry.getValue().equals(refactoringsByTask.put(entry.getKey(), entry.getValue()))) {
                changed = true;
            }
        }
        if (changed && taskList != null) {
            taskList.refresh();
        }
    }

    /// Re-targets the terminal mirror once a switch or suspend of `taskId`
    /// finished: resume resurrects the window (fresh id in the file),
    /// suspend ends it — the pane must follow without manual action. Uses
    /// the repository's freshest entry; only acts when that task is the one
    /// currently previewed.
    // [impl->dsn~terminal-pane~14]
    public void refreshPreview(String taskId) {
        Task previewed = previewedTask;
        if (previewed == null || !previewed.id().equals(taskId)) {
            return;
        }
        entries.stream()
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(entry -> ((TaskEntry.Loaded) entry).task())
                .filter(task -> task.id().equals(taskId))
                .findFirst()
                .ifPresent(task -> {
                    previewedTask = task;
                    onPreview.accept(task);
                    renderPrHeader();
                });
    }

    /// Replaces the running-status map with the poller's latest snapshot and
    /// repaints the rows — but only when the snapshot actually differs: the
    /// poller ticks every few seconds, and an unconditional refresh() rebuilds
    /// every visible cell, making the rows flicker. Must run on the FX thread.
    // [impl->dsn~task-running-indicator~7]
    public void updateRunningStatuses(Map<String, String> fresh) {
        if (runningStatusByKey.equals(fresh)) {
            return;
        }
        runningStatusByKey.clear();
        runningStatusByKey.putAll(fresh);
        // The title carries the same counts as the rows. [impl->dsn~window-title-category~3]
        updateWindowTitle();
        // With the "Awaits input only" filter on, a status change alters which rows
        // qualify — and under the "Action needed" sort it alters their order —
        // so the list must be rebuilt, not just repainted. Otherwise a repaint
        // suffices but the count badge still has to follow.
        // [impl->dsn~awaits-input-filter~1]
        // [impl->dsn~task-sort-modes~2]
        if (awaitsInputOnly || sortMode == TaskOrder.ACTION_NEEDED) {
            rebuildRows();
        } else {
            updateAwaitsInputButton();
            if (taskList != null) {
                taskList.refresh();
            }
        }
    }

    /// Collapses or expands a group, remembering the choice, then re-flattens.
    // [impl->dsn~task-folder-grouping-ui~8]
    private void toggleGroup(String name) {
        if (!collapsedGroups.remove(name)) {
            collapsedGroups.add(name);
        }
        Logger.debug("toggleGroup {} -> collapsed={}", name, collapsedGroups.contains(name));
        rebuildRows();
    }

    /// Collapses or expands the bottom "Done" section, then re-flattens.
    /// The keys `entry` files under for `level`: its folder, its effective
    /// tags, or its PR-status buckets; empty when it has none.
    // [impl->dsn~task-label-grouping~3]
    // [impl->dsn~pr-status-grouping~1]
    private List<String> groupKeys(TaskEntry entry, Grouping level, Set<String> labelRepos) {
        return switch (level) {
            case FOLDER -> entry.group().isEmpty() ? List.of() : List.of(entry.group());
            case LABELS -> entry instanceof TaskEntry.Loaded loaded ? effectiveTags(loaded.task()) : List.of();
            case PR_STATUS -> entry instanceof TaskEntry.Loaded loaded
                    ? prStatusBuckets(loaded.task(), labelRepos) : List.of();
        };
    }

    /// Appends a group's `tasks`, sub-grouped by `levels` (outermost first):
    /// tasks without a key at a level come first, then one indented
    /// [LabelHeader] per key, recursing into the rest of the levels. With no
    /// levels left it is the plain sorted task list. A sub-header collapses
    /// under its path (`parentKey` + name), so the same label under two
    /// categories collapses independently.
    // [impl->dsn~nested-grouping~1]
    private void addNestedRows(List<Object> rows, String parentKey, List<TaskEntry> tasks,
            List<Grouping> levels, Comparator<TaskEntry> order, Set<String> labelRepos, boolean finding) {
        if (levels.isEmpty()) {
            tasks.sort(order);
            rows.addAll(tasks);
            return;
        }
        Grouping level = levels.getFirst();
        List<TaskEntry> unkeyed = new ArrayList<>();
        Map<String, List<TaskEntry>> buckets = new TreeMap<>(level == Grouping.PR_STATUS
                ? PrStatusGroup.BY_WORKFLOW : String.CASE_INSENSITIVE_ORDER);
        for (TaskEntry task : tasks) {
            List<String> keys = groupKeys(task, level, labelRepos);
            if (keys.isEmpty()) {
                unkeyed.add(task);
            }
            for (String key : keys) {
                buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(task);
            }
        }
        unkeyed.sort(order);
        rows.addAll(unkeyed);
        int depth = groupings.size() - levels.size();
        for (Map.Entry<String, List<TaskEntry>> bucket : buckets.entrySet()) {
            String key = parentKey + NESTED_KEY_SEPARATOR + bucket.getKey();
            boolean expanded = finding || !collapsedGroups.contains(key);
            rows.add(new LabelHeader(bucket.getKey(), key, expanded, bucket.getValue().size(), depth));
            if (expanded) {
                addNestedRows(rows, key, bucket.getValue(), levels.subList(1, levels.size()),
                        order, labelRepos, finding);
            }
        }
    }

    /// Joins a sub-header's name to its parent's in its collapse key; a
    /// control character, so no folder or tag name can collide with it.
    private static final String NESTED_KEY_SEPARATOR = "\u001f";

    // [impl->dsn~done-section~1]
    private void toggleDoneSection() {
        doneSectionCollapsed = !doneSectionCollapsed;
        rebuildRows();
    }

    /// Re-flattens the entry list into the visible rows: root-level tasks
    /// first (sorted by [#ORDER]), then one [GroupHeader] per subfolder
    /// (folders sorted by name) followed — when the group is expanded — by its
    /// tasks sorted the same way.
    // [impl->dsn~task-folder-grouping-ui~8]
    // [impl->dsn~done-section~1]
    private void rebuildRows() {
        groupConfigCache.clear();   // [impl->dsn~group-config-cache~1]
        selectableTagsCache = null;
        // The find bar (Ctrl+F) and the tag filter both narrow the list; while
        // either is active, groups and the Done section are force-expanded and
        // empty groups hidden so every match is visible without extra clicks.
        // The tag filter is AND: a task must carry every selected tag; failed
        // rows are hidden while it is active (a deliberately narrowed view).
        // [impl->dsn~task-find~9]
        // [impl->dsn~task-tag-filter~2]
        boolean searching = !searchQuery.strip().isEmpty();
        // A query is an explicit "where is this task", so it suspends the
        // narrowing *content* filters while it runs: a task without the
        // selected tag or not currently running is *found* instead of being
        // swallowed by a filter the search was never told about — the find bar
        // then reports "0 matches" for a task that plainly exists. The
        // active-desktop filter below is the exception.
        // [impl->dsn~task-find~9]
        boolean filtering = !searching && !activeTags.isEmpty();
        // [impl->dsn~awaits-input-filter~1]
        boolean awaiting = !searching && awaitsInputOnly;
        // [impl->dsn~running-tasks-filter~1]
        boolean running = !searching && runningTasksOnly;
        // The active-desktop filter narrows only when we actually know the
        // active desktop (null on non-Windows / unnamed / not-yet-read, so the
        // toggle is a harmless no-op there). Captured into a non-null local so
        // the per-entry match below reads it once.
        // Unlike the content filters above it is NOT suspended by a search: the
        // desktop in view is where the user is working, and a query that
        // silently pulled in other desktops made the list jump elsewhere. The
        // hits it swallows are counted instead and reported by the find bar.
        // [impl->dsn~active-desktop-filter~6]
        @Nullable String activeDesktopFilter = activeDesktopOnly ? activeDesktop : null;
        hitsOffDesktop = 0;
        // The find field's category chip narrows the list to that category.
        // [impl->dsn~category-search~3]
        String scope = scopeCategory;
        boolean narrowed = searching || filtering || awaiting || running
                || activeDesktopFilter != null || scope != null;
        // Force-expanding every group belongs to a *find* — a query or a tag
        // selection, where the point is to see every match without extra
        // clicks. The three standing filters (awaits input, running tasks,
        // active desktop) are how the user reads their list all day, not a
        // search, and while `narrowed` drove the expansion they made
        // collapsing a category impossible: the click flipped
        // `collapsedGroups` and the rebuild ignored it. Field report
        // 2026-09-12, with `filter/Active/Desktop` on: "collapsing click
        // doesn' work".
        // [impl->dsn~collapse-survives-a-filter~1]
        boolean finding = searching || filtering;
        updateAwaitsInputButton();
        // A task added, removed or re-grouped changes the title's counts.
        // [impl->dsn~window-title-category~3]
        updateWindowTitle();
        // Group → its desktop:, memoized for this rebuild so the per-entry
        // desktop filter reads each CONTEXTSWITCHER.md at most once.
        Map<String, @Nullable String> desktopByGroup = new HashMap<>();
        // The selected sort mode's comparator; the file-time lookup is
        // memoized per rebuild so LAST_UPDATE reads each task file's
        // modification time at most once. [impl->dsn~task-sort-modes~2]
        Map<String, Long> modifiedById = new HashMap<>();
        Comparator<TaskEntry> order = sortMode.comparator(this::runningStatusFor,
                entry -> modifiedById.computeIfAbsent(entry.id(), this::lastModifiedMillis));
        Grouping grouping = grouping();
        // Further ticked groupings nest inside the top-level groups.
        // [impl->dsn~nested-grouping~1]
        List<Grouping> subLevels = List.copyOf(groupings).subList(1, groupings.size());
        List<TaskEntry> ungrouped = new ArrayList<>();
        Map<String, List<TaskEntry>> grouped = new TreeMap<>(grouping == Grouping.PR_STATUS
                ? PrStatusGroup.BY_WORKFLOW : String.CASE_INSENSITIVE_ORDER);
        // Which repos group by their `status:` labels, decided once per rebuild.
        // [impl->dsn~pr-status-grouping~1]
        Set<String> labelRepos = groupings.contains(Grouping.PR_STATUS)
                ? PrStatusGroup.labelRepos(prInfoByUrl) : Set.of();
        List<TaskEntry> done = new ArrayList<>();
        // Folders that never had a task file (freshly created groups) must stay
        // visible under a filter — they have nothing to hide, unlike a group
        // whose tasks were all filtered out.
        Set<String> groupsWithEntries = entries.stream().map(TaskEntry::group)
                .filter(group -> !group.isEmpty()).collect(Collectors.toSet());
        for (TaskEntry entry : entries) {
            if (searching && !searchHits.contains(entry.id())) {
                continue;
            }
            // [impl->dsn~category-search~3]
            if (scope != null && !entry.group().equals(scope)) {
                continue;
            }
            if (filtering && !tagFilterMatches(entry)) {
                continue;
            }
            // [impl->dsn~awaits-input-filter~1]
            if (awaiting && !awaitsInput(entry)) {
                continue;
            }
            // Show only the categories on the active virtual desktop.
            // [impl->dsn~active-desktop-filter~6]
            if (activeDesktopFilter != null
                    && !onActiveDesktop(entry.group(), activeDesktopFilter, desktopByGroup)) {
                // Counted while a query runs: the find bar says how many hits
                // sit outside the desktop in view, so "0 matches" never reads
                // as "the task does not exist".
                // [impl->dsn~search-hits-off-desktop~1]
                if (searching) {
                    hitsOffDesktop++;
                }
                continue;
            }
            // Show only running work: suspended and done tasks fall out (error
            // rows stay — a parse failure must be seen).
            // [impl->dsn~running-tasks-filter~1]
            if (running && entry instanceof TaskEntry.Loaded loaded
                    && loaded.task().status() != TaskStatus.ACTIVE) {
                continue;
            }
            // Completed tasks leave their group and gather in one bottom "Done"
            // section, so active work is not diluted by finished work. Error
            // rows always stay in place — a parse failure must be seen.
            if (entry instanceof TaskEntry.Loaded loaded
                    && loaded.task().status() == TaskStatus.DONE) {
                done.add(entry);
                continue;
            }
            // Group by labels instead of folders while the toggle is on: a
            // task files under each of its effective tags, untagged tasks and
            // error rows stay in the top ungrouped block.
            // By PR status alike: a task files under the bucket of each of its
            // PRs; one without a (resolved) PR stays ungrouped.
            // [impl->dsn~task-label-grouping~3]
            // [impl->dsn~pr-status-grouping~1]
            if (grouping != Grouping.FOLDER) {
                List<String> labels = groupKeys(entry, grouping, labelRepos);
                if (labels.isEmpty()) {
                    ungrouped.add(entry);
                } else {
                    for (String label : labels) {
                        grouped.computeIfAbsent(label, ignored -> new ArrayList<>()).add(entry);
                    }
                }
                continue;
            }
            String group = entry.group();
            List<TaskEntry> bucket = group.isEmpty()
                    ? ungrouped
                    : grouped.computeIfAbsent(group, ignored -> new ArrayList<>());
            bucket.add(entry);
        }
        // Folders without any task file are groups too — visible, with an
        // "Add…" row so the group is not a dead end. (Not in label view —
        // a label exists only through the tasks carrying it.)
        // [impl->dsn~task-create-ui~15]
        if (grouping == Grouping.FOLDER) {
            for (String folder : folders) {
                // …except under the active-desktop filter, which narrows by a
                // property of the *category* rather than of its tasks: an empty
                // folder still names a desktop, so "nothing to filter out" does
                // not apply to it and a category of another desktop must go.
                // Without this a task-less category showed on every desktop.
                // [impl->dsn~active-desktop-filter~6]
                if (activeDesktopFilter != null
                        && !onActiveDesktop(folder, activeDesktopFilter, desktopByGroup)) {
                    continue;
                }
                // …and not under a typed query either: a search looks for a
                // task, and a category without one can never be a hit, so the
                // result shows only categories that hold hits (field report
                // 2026-09-16). `Add category…` clears the query so the folder
                // it just made is not hidden by this.
                // [impl->dsn~task-folder-grouping-ui~8]
                if (searching) {
                    continue;
                }
                // [impl->dsn~category-search~3]
                if (scope != null && !folder.equals(scope)) {
                    continue;
                }
                grouped.computeIfAbsent(folder, ignored -> new ArrayList<>());
            }
        }

        List<Object> rows = new ArrayList<>();
        ungrouped.sort(order);
        rows.addAll(ungrouped);
        // Pinned categories come first, alphabetical within each block: the
        // TreeMap's order is already alphabetical and the sort is stable, so
        // only the pinned/unpinned split is applied here. Label buckets have
        // no config file, hence nothing to pin. [impl->dsn~pinned-categories~1]
        List<Map.Entry<String, List<TaskEntry>>> groups = new ArrayList<>(grouped.entrySet());
        if (grouping == Grouping.FOLDER) {
            groups.sort(Comparator.comparing(group -> !groupDefaults(group.getKey()).pinned()));
        }
        for (Map.Entry<String, List<TaskEntry>> group : groups) {
            String name = group.getKey();
            List<TaskEntry> tasks = group.getValue();
            // A search/filter hides groups with no match rather than showing an
            // empty header with an "Add task…" row — but a folder that never had a
            // task to begin with has nothing to filter out, so it stays.
            // The scoped category itself always stays: it is what the user
            // asked to see, even when nothing in it matches.
            if (narrowed && tasks.isEmpty() && groupsWithEntries.contains(name)
                    && !name.equals(scope)) {
                continue;
            }
            // A scoped category shows its tasks even when it was collapsed.
            // [impl->dsn~category-search~3]
            boolean expanded = finding || name.equals(scope) || !collapsedGroups.contains(name);
            // A label bucket renders as a plain chip header — no config file,
            // folder actions, or drop target behind it.
            // [impl->dsn~task-label-grouping~3]
            if (grouping != Grouping.FOLDER) {
                rows.add(new LabelHeader(name, expanded, tasks.size()));
                if (expanded) {
                    addNestedRows(rows, name, tasks, subLevels, order, labelRepos, finding);
                }
                continue;
            }
            GroupConfig defaults = groupDefaults(name);
            // [impl->dsn~frontmatter-duplicate-keys~1]
            // [impl->dsn~category-header-path~1]
            // [impl->dsn~running-task-accent~1]
            int runningCount = (int) tasks.stream()
                    .filter(entry -> entry instanceof TaskEntry.Loaded loaded
                            && loaded.task().status() == TaskStatus.ACTIVE)
                    .count();
            rows.add(new GroupHeader(name, expanded, defaults.tags(), defaults.remote(),
                    groupConfigWarning(name), defaults.folders(), groupPath(name), runningCount));
            if (expanded) {
                addNestedRows(rows, name, tasks, subLevels, order, labelRepos, finding);
                // Every category closes with its last row: the slim "Add task…"
                // row, or — for an auto category, filled by its search — the
                // synced-from placeholder (TaskListCell tells the two apart).
                // [impl->dsn~task-create-ui~15]
                // [impl->dsn~auto-category-placeholder-row~2]
                rows.add(new TaskListCell.AddTaskRow(name));
            }
        }
        // The completed-work section is pinned below every group, collapsed by
        // default; only rendered when something is actually done.
        if (!done.isEmpty()) {
            boolean doneExpanded = finding || !doneSectionCollapsed;
            rows.add(new TaskListCell.DoneHeader(done.size(), doneExpanded));
            if (doneExpanded) {
                done.sort(order);
                rows.addAll(done);
            }
        }
        // setAll clears the ListView selection: turn the currently selected
        // task into the pending selection first, so a background rebuild — a
        // title adoption renaming some file, a PR write-back, a collapse or
        // filter toggle — re-applies the highlight instead of silently
        // dropping it while the terminal/editor/queue keep showing the task.
        // An explicitly requested pending selection (e.g. the neighbour of a
        // just-deleted task) wins over the preserved one. If the selected
        // task itself was just renamed, its old id is not found below and
        // stays pending, which is exactly what `taskRenamed` checks to move
        // the selection to the new id.
        // A task the current **search** hides is NOT preserved: its id would
        // linger in pendingSelectionId and later override the task the user
        // actually finds, so clearing the query would jump back to the
        // pre-search task instead of keeping the found one.
        //
        // That reasoning is about a query and nothing else, but the guard used
        // to read `narrowed` — every filter. A narrowing filter that hides the
        // selected task for a moment therefore dropped the selection for good,
        // and no later rebuild brought it back once the row returned. Field
        // report 2026-09-12, on a task being created with a filter on: "the
        // highlight the task on the left while creation still does not work —
        // the terminal content is right though". Right, because a *cleared*
        // selection is deliberately a no-op for the panes (they must survive
        // the clear every rebuild does), so the terminal kept the task while
        // the list showed no highlight at all — the one state where the two
        // can disagree.
        // A filtered-out task stays pending now and takes its highlight back
        // as soon as it has a row again (it is not running *yet*, its desktop
        // is not read *yet*).
        // [impl->dsn~selection-survives-a-filter~2]
        // [impl->dsn~task-folder-grouping-ui~8]
        // [impl->dsn~task-find~9]
        ListView<Object> list = taskList;
        // The stock "No tasks yet" placeholder is wrong when tasks exist but
        // the filters hide them all: swap in a filter-aware one, which for the
        // desktop filter offers creating a category for the desktop in view.
        // [impl->dsn~desktop-filter-empty-state~2]
        // The category offer is only right when the desktop filter is the sole
        // narrowing — a search that matches nothing on a desktop *with*
        // categories must not claim the desktop has none.
        if (list != null && rows.isEmpty()) {
            boolean desktopOnly = activeDesktopFilter != null
                    && !(searching || filtering || awaiting || running || scope != null);
            list.setPlaceholder(searching && searchPending
                    ? new Label("Searching…")
                    : narrowed
                    ? filteredEmptyPlaceholder(desktopOnly ? activeDesktopFilter : null,
                            searching ? hitsOffDesktop : 0)
                    : emptyStatePlaceholder());
        }
        // The task on screen right now, kept as the fallback for the
        // selection below: an unreachable `pendingSelectionId` must not cost
        // the user the highlight they can see.
        String onScreenId = list != null
                && list.getSelectionModel().getSelectedItem()
                        instanceof TaskEntry.Loaded selectedNow
                ? selectedNow.id() : null;
        if (pendingSelectionId == null && onScreenId != null
                // By id, not by row equality: a running task's file is
                // rewritten under it (session id, status, commit), so the
                // rebuilt row is a *different* TaskEntry.Loaded for the same
                // task — comparing rows dropped the selection of exactly the
                // task being worked on whenever a filter was active.
                && (!searching || rows.stream().anyMatch(row -> row instanceof TaskEntry.Loaded loaded
                        && loaded.id().equals(onScreenId)))) {
            pendingSelectionId = onScreenId;
        }
        replacingRows = true;
        try {
            visibleRows.setAll(rows);
        } finally {
            replacingRows = false;
        }
        restoreSelection(list, onScreenId, narrowed);
        // The list and the panes must never show different tasks.
        // [impl->dsn~selection-survives-a-filter~2]
        syncPanesWithSelection(list);
    }

    /// Puts the highlight back after [#rebuildRows] replaced the rows: the
    /// pending selection if it has a row, else the task that was on screen,
    /// else the task the panes are showing.
    private void restoreSelection(@Nullable ListView<Object> list, @Nullable String onScreenId,
            boolean narrowed) {
        // A just-created category is focused once the watcher delivers its
        // folder and the header row appears — before any preserved task
        // selection, so the new category wins instead of the task that was
        // selected when `Add category…` ran.
        // [impl->dsn~category-create-ui~2]
        if (pendingGroupSelection != null && selectGroupHeaderRow(pendingGroupSelection)) {
            pendingGroupSelection = null;
            return;
        }
        // A pending selection is applied once its row exists in the rebuilt
        // list.
        // [impl->dsn~task-delete~6]
        String pending = pendingSelectionId;
        if (pending != null && list != null) {
            int row = rowOf(pending);
            if (row >= 0) {
                pendingSelectionId = null;
                list.getSelectionModel().select(row);
                // Only an explicitly requested selection scrolls — a
                // preserved one must not move the viewport on rebuilds.
                // [impl->dsn~task-move-dnd~6]
                if (scrollToPendingSelection) {
                    scrollToPendingSelection = false;
                    scrollRowIntoView(list, row);
                }
                return;
            }
        }
        // The pending row is not in the list: a rename in flight, a created
        // task the watcher has not delivered, or a task the narrowing filters
        // hide — which the browser-tab reporter deliberately leaves pending.
        // Such an id used to block the preservation above, so the *next*
        // rebuild (any task file write) left the list with nothing selected
        // at all, and only a click brought the highlight back (field report
        // 2026-09-10). The task still on screen keeps it instead; the id
        // stays pending and wins as soon as it has a row.
        // [impl->dsn~browser-tab-selects-task~5]
        if (onScreenId != null && list != null) {
            int row = rowOf(onScreenId);
            if (row >= 0) {
                list.getSelectionModel().select(row);
            }
        }
        // Last resort: the task the *panes* are showing. Both ids above can be
        // unresolvable at once — field report 2026-09-12, "task on the left not
        // focussed. IDK why is it so hard to always have a task focussed":
        // `was …-regarding-the-wizard-i-htink-for-add-category-we (pending
        // jabref/…-restore-welcome-tab-appearance, narrowed true, row -1)`.
        // The on-screen id was a task whose title had just been adopted, so its
        // file — and its row — had been renamed out from under it, while the
        // pending id was a browser-tab report for a task on another desktop
        // that the active-desktop filter will never show. Neither had a row,
        // and the list ended up with nothing selected at all.
        // `previewedTask` is the one id that survives a rename (`taskRenamed`
        // and `refreshPreview` both re-target it) and is by definition the task
        // the user is looking at, so if it has a row it gets the highlight.
        // [impl->dsn~selection-survives-a-filter~2]
        if (list != null && list.getSelectionModel().getSelectedItem() == null) {
            Task previewed = previewedTask;
            int row = previewed == null ? -1 : rowOf(previewed.id());
            if (row >= 0) {
                list.getSelectionModel().select(row);
            } else if (onScreenId != null) {
                Logger.debug("Rebuild left nothing selected; was {} (pending {}, previewed {},"
                                + " narrowed {}, row {})", onScreenId, pending,
                        previewed == null ? null : previewed.id(), narrowed, row);
            }
        }
    }

    /// The list and the panes must never show different tasks.
    ///
    /// Replacing the rows makes the selection model emit a selection of its
    /// own, which the listener deliberately ignores — but ignoring its
    /// *effects* leaves the selection itself standing. It then becomes the
    /// on-screen id the next rebuild preserves, and [#restoreSelection]
    /// "selects" a row that is already selected: a no-op, which fires no
    /// event, so nothing ever tells the panes. The list highlights one task
    /// while the terminal, the notes and the configuration pane show another,
    /// and every later rebuild reads the drifted selection back and agrees
    /// with itself. Field report 2026-09-14: "i clicked it, terminal was
    /// right, then I cleared the search, something flickered on the left - and
    /// now the terminal does not match the content".
    ///
    /// The task the panes show wins when it still has a row — it is the one
    /// the user chose, and the drift is arbitrary. When it has none, the
    /// highlight wins and the panes follow it, which beats both leaving them
    /// disagreeing and clearing a selection that may well be the right one.
    private void syncPanesWithSelection(@Nullable ListView<Object> list) {
        if (list == null
                || !(list.getSelectionModel().getSelectedItem() instanceof TaskEntry.Loaded shown)) {
            return;
        }
        Task previewed = previewedTask;
        String previewedId = previewed == null ? null : previewed.id();
        if (shown.id().equals(previewedId)) {
            return;
        }
        int row = previewedId == null ? -1 : rowOf(previewedId);
        if (row >= 0) {
            Logger.debug("The list had drifted to {}; taking the highlight back to {}",
                    shown.id(), previewedId);
            list.getSelectionModel().select(row);
            return;
        }
        Logger.debug("The panes showed {} while the list selected {}; the panes follow",
                previewedId, shown.id());
        applySelection(shown);
    }

    /// Everything a selection means for the rest of the window: the panes, the
    /// history, the desktop's last task, the window title, the editor lane.
    /// Called by the selection listener, and by [#syncPanesWithSelection] for a
    /// selection that reached the list without an event of its own.
    private void applySelection(@Nullable Object selected) {
        switch (selected) {
            case TaskEntry.Loaded loaded -> {
                // [impl->dsn~current-category-highlight~1]
                markCurrentGroup(loaded.group());
                previewedTask = loaded.task();
                // [impl->dsn~task-history-navigation~2]
                recordHistory(loaded.id());
                // No poller tick arrives while the energy saver is on, so
                // the task the user just opened asks for one itself.
                // [impl->dsn~energy-saver~1]
                if (EnergySaver.active()) {
                    EnergySaver.refreshTask();
                }
                // [impl->dsn~desktop-last-task-selection~4]
                rememberLastTask(activeDesktop, loaded.id());
                showCategoryInTitle(loaded.group());
                onPreview.accept(loaded.task());
                renderPrHeader();
                openInEditor(loaded.id() + ".md");
            }
            // A corrupt file carries no Task; drop the previewed task so a
            // click in the editor lane cannot re-select a stale one.
            case TaskEntry.Failed failed -> {
                markCurrentGroup(failed.group());
                previewedTask = null;
                showCategoryInTitle(failed.group());
                renderPrHeader();
                openInEditor(failed.fileName());
            }
            // A category header: the terminal and queue lanes must not
            // keep showing the previously selected task next to it.
            // (null stays a no-op — rebuilds clear the selection
            // transiently and the panes must survive that.)
            case GroupHeader header -> {
                // [impl->dsn~current-category-highlight~1]
                markCurrentGroup(header.name());
                previewedTask = null;
                showCategoryInTitle(header.name());
                onPreviewCleared.run();
                renderPrHeader();
            }
            // A label header has no file behind it either.
            // [impl->dsn~task-label-grouping~3]
            case LabelHeader header -> {
                markCurrentGroup("");
                previewedTask = null;
                onPreviewCleared.run();
                renderPrHeader();
            }
            case null, default -> { }
        }
    }

    /// The index of `taskId`'s row in the list as it stands, or `-1` when no
    /// row carries it (a filter hides it, or its file is not loaded yet).
    private int rowOf(String taskId) {
        for (int i = 0; i < visibleRows.size(); i++) {
            if (visibleRows.get(i) instanceof TaskEntry.Loaded loaded
                    && loaded.id().equals(taskId)) {
                return i;
            }
        }
        return -1;
    }

    /// Whether `group` passes the active-desktop filter: its `CONTEXTSWITCHER.md`
    /// `desktop:` equals the active desktop (case-insensitive), or it names no
    /// desktop at all and [#noDesktopCategoriesToo] is on. The ungrouped block
    /// (empty group name, no `CONTEXTSWITCHER.md`) counts as
    /// desktop-less too — a task in no category is on no desktop either, and
    /// would otherwise be unreachable while the filter is on.
    /// A category naming no desktop also passes while the fallback desktop is
    /// the active one — that is the desktop it is focused on
    /// (`dsn~fallback-desktop~1`) — but it stays *nameless* here, or the
    /// fallback (default `misc`) would leave "Also show categories without a
    /// desktop" with nothing to show.
    /// `desktopByGroup` memoizes the file read for one rebuild; it maps to
    /// `null` for "no desktop", hence `containsKey` rather than
    /// `computeIfAbsent` (which does not cache a null).
    // [impl->dsn~active-desktop-filter~6]
    // [impl->dsn~fallback-desktop~1]
    private boolean onActiveDesktop(String group, String activeDesktopFilter,
            Map<String, @Nullable String> desktopByGroup) {
        if (!desktopByGroup.containsKey(group)) {
            desktopByGroup.put(group, groupDefaults(group).desktop());
        }
        String desktop = desktopByGroup.get(group);
        if (desktop == null) {
            // A config that does not load has no desktop by accident; hiding
            // the category would hide the header warning saying so.
            // [impl->dsn~group-config-parse-error~1]
            return noDesktopCategoriesToo
                    || groupConfigBroken(group)
                    || (!fallbackDesktop.isBlank()
                            && activeDesktopFilter.equalsIgnoreCase(fallbackDesktop));
        }
        return activeDesktopFilter.equalsIgnoreCase(desktop);
    }

    /// A task file's last-modified time in millis, 0 when unreadable (the
    /// entry then sorts last within its status block under LAST_UPDATE).
    // [impl->dsn~task-sort-modes~2]
    private long lastModifiedMillis(String id) {
        try {
            return Files.getLastModifiedTime(tasksDir.resolve(id + ".md")).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /// Whether an entry passes the active tag filter (AND — every selected tag
    /// present, own or inherited from the group). A failed entry has no tags,
    /// so it is hidden while a tag filter is active.
    // [impl->dsn~task-tag-filter~2]
    private boolean tagFilterMatches(TaskEntry entry) {
        return entry instanceof TaskEntry.Loaded loaded
                && TaskTags.matchesAll(effectiveTags(loaded.task()), activeTags);
    }

    /// A task's effective tags: its own plus the tags its group declares in
    /// `CONTEXTSWITCHER.md` (inherited — as if each task carried them, so
    /// filtering by a group tag shows the whole group). Group tags already on
    /// the task are not duplicated.
    // [impl->dsn~task-tag-filter~2]
    private List<String> effectiveTags(Task task) {
        int slash = task.id().lastIndexOf('/');
        List<String> group = slash < 0 ? List.of() : groupTags(task.id().substring(0, slash));
        if (group.isEmpty()) {
            return task.tags();
        }
        List<String> merged = new ArrayList<>(task.tags());
        for (String tag : group) {
            if (merged.stream().noneMatch(own -> own.equalsIgnoreCase(tag))) {
                merged.add(tag);
            }
        }
        return merged;
    }

    /// The group's own tags from its `CONTEXTSWITCHER.md` (empty for the root
    /// or a group without a config).
    // [impl->dsn~task-tag-filter~2]
    private List<String> groupTags(String group) {
        return group.isEmpty() ? List.of() : groupDefaults(group).tags();
    }

    /// Renames a task by textually replacing the `title:` line of its file
    /// (comments and formatting survive); the watcher refreshes the row. The
    /// rename listener then brings the task's tmux window name along.
    // [impl->dsn~task-rename-title~2]
    private void renameTask(Task task) {
        TextInputDialog dialog = new TextInputDialog(task.title());
        dialog.setTitle("Rename task");
        dialog.setHeaderText("New title for " + task.id() + ".md");
        dialog.setContentText("Title");
        dialog.showAndWait().map(String::strip).filter(title -> !title.isEmpty()).ifPresent(title -> {
            String fileName = task.id() + ".md";
            String content = files.read(fileName);
            if (content == null) {
                new Alert(Alert.AlertType.ERROR, "Cannot rename " + fileName + ": file unreadable").show();
                return;
            }
            files.save(fileName, TaskFileParser.withTitle(content, title));
            if (fileName.equals(editedFileName)) {
                editedFileName = null;
                openInEditor(fileName);
            }
            onRenamed.accept(task, title);
        });
    }

    /// One dialog for a plain title or a GitHub PR / GitLab MR URL, with an explicit
    /// choice of what to create: `Add plain task` writes a file-only task,
    /// `Add remote Claude` spins up a tmux window + Claude session on a remote
    /// and shows it in the terminal, `Add local Claude` writes the same
    /// file-only task and starts a local Claude session in the group's
    /// directory. `Add remote Claude` is enabled whenever a remote can
    /// be resolved — the group's `CONTEXTSWITCHER.md` `remote`, else the app's
    /// first configured remote (settings `remotes`), so a folder without a CS
    /// config still offers it; `Add local Claude` whenever the group is a
    /// **local** category (a CS config with a `workspacesRoot`/`workdir` but no
    /// `remote`). The default button follows the category: local → `Add local
    /// Claude`, a group `remote:` → `Add remote Claude`, neither →
    /// `Add plain task`. A pasted PR
    /// URL always routes to the PR flow regardless of the button. The field is
    /// pre-filled from the clipboard; `group` (empty = root) places the file in
    /// that project group. https://github.com/contextswitcher/contextswitcher-private/issues/45.
    ///
    /// The field is a multi-line area: a live task's input is a free-form
    /// multi-sentence description, and with a single-line field the dialog's
    /// default button fired on every plain Enter — submitting a half-typed
    /// description mid-sentence. Enter now inserts a newline; three plain
    /// Enters in a row at the field's end, or three Ctrl+Enters, trigger the
    /// default button — the queue
    /// boxes' commit chords, one shared implementation
    /// ([QueuePane#installComposeKeys]) so they cannot drift apart. The
    /// field also takes the queue boxes' attachment affordances (file
    /// drop, image paste): the stored markers travel with the description,
    /// and the live-task prompt delivery uploads them like a queued message.
    // [impl->dsn~task-create-ui~15]
    // [impl->dsn~task-create-live~8]
    // [impl->dsn~task-create-local~4]
    private void addTask(String initialGroup) {
        // A just-edited CS config open in the editor lane may hold the remote;
        // flush it so the button reflects the current, not the on-disk, state.
        saveEditorIfDirty();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add task");
        dialog.setResizable(true);
        dialog.setHeaderText("Enter a title or description, or paste a GitHub PR or GitLab MR URL.");
        // The category the task lands in, pre-selected with the one it was
        // opened for; picking another re-evaluates hints and buttons.
        ComboBox<String> categoryBox = new ComboBox<>();
        categoryBox.setId("add-task-category");
        categoryBox.getStyleClass().add(Styles.SMALL);
        categoryBox.getItems().add("");
        categoryBox.getItems().addAll(folders.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList());
        if (!categoryBox.getItems().contains(initialGroup)) {
            categoryBox.getItems().add(initialGroup);
        }
        categoryBox.setValue(initialGroup);
        categoryBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(@Nullable String group) {
                return group == null || group.isEmpty() ? "(no category)" : group;
            }

            @Override
            public String fromString(String text) {
                return text;
            }
        });
        HBox categoryRow = new HBox(6, new Label("Category:"), categoryBox);
        categoryRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        ButtonType plainType = new ButtonType("Add plain task", ButtonBar.ButtonData.OK_DONE);
        ButtonType remoteType = new ButtonType("Add remote Claude", ButtonBar.ButtonData.OTHER);
        ButtonType localType = new ButtonType("Add local Claude", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes()
                .addAll(plainType, remoteType, localType, ButtonType.CANCEL);

        TextArea field = new TextArea();
        // Id so a TestFX test can focus this field after opening the dialog from
        // the toolbar add menu (dsn~task-list-toolbar~3).
        field.setId("add-task-description");
        field.setWrapText(true);
        field.setPrefRowCount(3);
        // The queue boxes' attachment affordances (file drop, image paste):
        // the markers travel with the description and upload on the prompt
        // send like any queued message.
        installAttachments.accept(field);
        String clip = Clipboard.getSystemClipboard().getString();
        // [impl->dsn~task-create-retry-prefill~1]
        String prefill = lastSubmissionFailed && Objects.equals(clip, lastSubmittedClip)
                ? lastSubmittedInput : clip;
        lastSubmissionFailed = false;
        if (prefill != null && !prefill.isBlank()) {
            field.setText(prefill.strip());
        }
        Label modeLabel = new Label();
        Consumer<String> updateMode = text ->
                modeLabel.setText(PrTitleLookup.isPrOrMrUrl(text.strip())
                        ? "from PR/MR URL" : "Title");
        updateMode.accept(field.getText());
        field.textProperty().addListener((obs, old, text) -> updateMode.accept(text));
        modeLabel.getStyleClass().add(Styles.TEXT_MUTED);
        Label remoteHint = new Label();
        remoteHint.getStyleClass().add(Styles.TEXT_MUTED);
        Label localHint = new Label();
        localHint.getStyleClass().add(Styles.TEXT_MUTED);
        Label keysHint = new Label("Enter starts a new line; three Enters or three"
                + " Ctrl+Enters in a row create the task. Drop files to attach them.");
        keysHint.getStyleClass().add(Styles.TEXT_MUTED);
        // Model and effort for the new session, sent as `/model`/`/effort`
        // ahead of the context prompt; ignored by `Add plain task` (no chat).
        // [impl->dsn~claude-mode-select~3]
        ComboBox<String> modelBox = QueuePane.modeBox("model: as is", ClaudeMode.MODELS);
        ComboBox<String> effortBox = QueuePane.modeBox("effort: as is", ClaudeMode.EFFORTS);
        // Pre-filled with the last pick actually sent, so the usual model is
        // one Ctrl+Enter away (`dsn~claude-mode-select~3`).
        ClaudeMode remembered = lastMode();
        QueuePane.selectMode(modelBox, remembered.model());
        QueuePane.selectMode(effortBox, remembered.effort());
        HBox modeRow = new HBox(6, modelBox, effortBox);
        // The queue pane's quick messages, above the field like above the add box.
        // [impl->dsn~quick-message-buttons~7]
        VBox content = new VBox(6, categoryRow, quickRow.apply(field), field, modeLabel, modeRow,
                keysHint, remoteHint, localHint);
        // Enlarging the dialog grows the description field, not the hints.
        VBox.setVgrow(field, Priority.ALWAYS);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);

        Button remoteButton = (Button) dialog.getDialogPane().lookupButton(remoteType);
        Button plainButton = (Button) dialog.getDialogPane().lookupButton(plainType);
        Button localButton = (Button) dialog.getDialogPane().lookupButton(localType);
        Runnable updateTarget = () -> {
            AddTarget target = addTarget(categoryBox.getValue());
            String remote = target.remote();
            boolean remoteFromGroup = target.defaults().remote() != null;
            String localWorkdir = target.localWorkdir();
            String hint;
            if (remote == null) {
                hint = "Add remote Claude needs a remote — set one in the group's CS config or settings.yaml.";
            } else if (remoteFromGroup) {
                hint = "Remote Claude runs on \"" + remote + "\" (group default).";
            } else {
                hint = "Remote Claude runs on \"" + remote
                        + "\" (configured remote; add a group CS config to override).";
            }
            remoteHint.setText(hint);
            localHint.setText(localWorkdir != null
                    ? (com.contextswitcher.terminal.LocalClaudeLauncher.onWindows()
                            ? "Local Claude opens a session in \"" + localWorkdir
                                    + "\" (group default), hosted by this app's terminal pane."
                            : "Local Claude opens a tmux window in \"" + localWorkdir
                                    + "\" (group default); attach with tmux attach -t 0.")
                    : "Add local Claude needs a local category — a group CS config with"
                            + " workspacesRoot/workdir and no remote.");
            remoteButton.setDisable(remote == null);
            localButton.setDisable(localWorkdir == null);
            // The default follows the category the task lands in: a local one
            // starts Claude locally, a group with its own `remote:` on that
            // remote, an unconfigured group creates a plain file (the fallback
            // remote from settings.yaml is offered, but not by default).
            Button defaultButton = localWorkdir != null ? localButton
                    : remoteFromGroup ? remoteButton : plainButton;
            remoteButton.setDefaultButton(defaultButton == remoteButton);
            localButton.setDefaultButton(defaultButton == localButton);
            plainButton.setDefaultButton(defaultButton == plainButton);
        };
        updateTarget.run();
        categoryBox.valueProperty().addListener((obs, old, value) -> updateTarget.run());
        Runnable fireDefault = () -> Stream.of(localButton, remoteButton, plainButton)
                .filter(Button::isDefaultButton).findFirst().ifPresent(Button::fire);
        // The area consumes plain Enter (newline), so the default button only
        // fires via an explicit commit chord — the queue boxes' conventions
        // (three plain Enters or three Ctrl+Enters in a row; Shift+Enter
        // newline), one shared implementation so dialog and queue advance in
        // sync. Both chords do the same one thing here: a dialog has nothing
        // to queue, only the task to create.
        QueuePane.installComposeKeys(field, fireDefault, fireDefault);
        Platform.runLater(field::requestFocus);

        ButtonType clicked = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (clicked == ButtonType.CANCEL) {
            return;
        }
        String input = field.getText() == null ? "" : field.getText().strip();
        if (input.isEmpty()) {
            return;
        }
        // Clipboard re-read at submit time: copying text inside the dialog
        // (e.g. a selection of the field itself) must not defeat the
        // unchanged-clipboard check on a later retry.
        // [impl->dsn~task-create-retry-prefill~1]
        lastSubmittedInput = input;
        lastSubmittedClip = Clipboard.getSystemClipboard().getString();
        ClaudeMode mode = new ClaudeMode(
                QueuePane.modeValue(modelBox), QueuePane.modeValue(effortBox));
        rememberMode(mode);
        String group = categoryBox.getValue();
        AddTarget target = addTarget(group);
        GroupConfig defaults = target.defaults();
        String remote = target.remote();
        String localWorkdir = target.localWorkdir();
        if (PrTitleLookup.isPrOrMrUrl(input)) {
            createTaskFromPrUrl(input, group, mode);
        } else if (clicked == remoteType) {
            createLiveTask(input, group, remote, defaults, mode);
        } else if (clicked == localType && localWorkdir != null) {
            // The file-only task (its `folder:` is the group's directory,
            // dsn~group-config-apply~2) plus a local Claude in that directory.
            String taskId = createTitleTask(input, group);
            if (taskId != null) {
                onStartLocalClaude.start(taskId, localWorkdir, input, mode);
            }
        } else {
            createTitleTask(input, group);
        }
        // A typed query would hide the new task (and its task-less category);
        // clear it so the row shows. Cancel above keeps the query.
        // [impl->dsn~task-folder-grouping-ui~8]
        if (!searchQuery.strip().isEmpty()) {
            closeFind();
        }
        // The keyboard lands on the left list the new row appears in, not on
        // the toolbar button (or group plus icon) the dialog was opened from,
        // so the arrow keys and the switch chord work on the new task right
        // away — the move the row's play button makes for the same reason
        // (`dsn~task-row-hover-actions~7`). Deferred: the PR flow's host
        // dialog closes after this one, and its owner window takes focus back
        // when it does.
        ListView<Object> list = taskList;
        if (list != null) {
            Platform.runLater(list::requestFocus);
        }
    }

    /// What `Add task` offers for `group`: its defaults, the remote a live
    /// task would use ([#effectiveRemote]), and — for a local category, a CS
    /// config with a directory but no remote, the lightweight no-tmux half of
    /// a local/remote group pair — the directory a local Claude starts in.
    private record AddTarget(GroupConfig defaults, @Nullable String remote,
            @Nullable String localWorkdir) {
    }

    private AddTarget addTarget(String group) {
        GroupConfig defaults = groupDefaults(group);
        return new AddTarget(defaults, effectiveRemote(defaults),
                defaults.remote() != null ? null : defaults.resolveWorkdir());
    }

    /// The remote for `Add remote Claude`: the group's `CONTEXTSWITCHER.md`
    /// `remote`, else the app's first configured remote (settings `remotes`) —
    /// the same source `openGroupConfig` pre-fills a new CS config with, so the
    /// button works before any CS config exists. Null only when no remote is
    /// configured anywhere.
    // [impl->dsn~task-create-live~8]
    private @Nullable String effectiveRemote(GroupConfig defaults) {
        if (defaults.remote() != null) {
            return defaults.remote();
        }
        List<String> remotes = configuredRemotes.get();
        return remotes.isEmpty() ? null : remotes.getFirst();
    }

    /// Spins up a live task (tmux window + Claude) for a title in `group` on
    /// `remote` (resolved by [#effectiveRemote]), using the group's workspace
    /// root (`workspacesRoot` or the shared `workdir`) when its CS config sets
    /// one. Delegated to `Main`, which does the ssh work off the FX thread and
    /// selects the row (showing it in the terminal).
    // [impl->dsn~task-create-live~8]
    private void createLiveTask(String title, String group, @Nullable String remote,
            GroupConfig defaults, ClaudeMode mode) {
        if (remote == null) {
            return;
        }
        onCreateLiveTask.create(title, remote, defaults.resolveWorkdir(),
                defaults.repo(), defaults.bootstrapWorktree(), group, mode, null);
    }

    /// Adds a browser URL to a task's `browser.urls` from a dialog
    /// (pre-filled from the clipboard when that holds a URL). Textual append
    /// via `TaskFileParser.addBrowserUrl`; the editor is flushed first so a
    /// just-edited file is not clobbered.
    /// Adds a link to a task from one dialog, auto-detecting the kind: an
    /// `onenote:` link (or a OneNote clipboard whose `onenote:` line is
    /// extracted) becomes the task's `note:` (opened via the row's note
    /// icon), an `http(s)` URL is appended to `browser.urls`. Pre-filled from
    /// the clipboard; the editor is flushed first so a just-edited file is
    /// not clobbered.
    // [impl->dsn~task-add-link~3]
    private void addLinkToTask(Task staleTask) {
        Task task = freshTask(staleTask);
        String fileName = task.id() + ".md";

        TextField linkField = new TextField();
        linkField.setPrefColumnCount(40);
        String clip = Clipboard.getSystemClipboard().getString();
        if (clip != null) {
            String note = OneNoteLink.extract(clip);
            if (note != null) {
                linkField.setText(note);
            } else if (clip.strip().matches("https?://\\S+")) {
                linkField.setText(clip.strip());
            }
        }
        TextField titleField = new TextField();
        titleField.setPromptText("optional, e.g. Probeklausur in moodle");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add link");
        dialog.setHeaderText("Adds a link to \"" + task.title() + "\":\n"
                + "an onenote: link becomes the note, an http(s) URL a browser tab.\n"
                + "The optional title labels a browser URL (ignored for notes).");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Live label showing which interpretation the current input takes, so
        // the user knows before pressing OK whether it becomes a note or a tab.
        Label modeLabel = new Label();
        modeLabel.getStyleClass().add(Styles.TEXT_MUTED);
        Consumer<String> updateMode = text -> {
            String link = text == null ? "" : text.strip();
            if (link.isEmpty()) {
                modeLabel.setText("");
            } else if (OneNoteLink.noteLink(link) != null) {
                modeLabel.setText("→ OneNote note (title ignored)");
            } else {
                modeLabel.setText("→ browser URL" + (titleField.getText().isBlank() ? "" : " (titled)"));
            }
        };
        updateMode.accept(linkField.getText());
        linkField.textProperty().addListener((obs, old, text) -> updateMode.accept(text));
        titleField.textProperty().addListener((obs, old, text) -> updateMode.accept(linkField.getText()));

        VBox content = new VBox(6,
                new Label("Link"), linkField,
                new Label("Title"), titleField,
                modeLabel);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        Platform.runLater(linkField::requestFocus);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        String link = linkField.getText() == null ? "" : linkField.getText().strip();
        if (link.isEmpty()) {
            return;
        }
        String rawTitle = titleField.getText() == null ? "" : titleField.getText().strip();
        String title = rawTitle.isEmpty() ? null : rawTitle;

        String note = OneNoteLink.noteLink(link);
        saveEditorIfDirty();
        String loaded = files.read(fileName);
        if (loaded == null) {
            new Alert(Alert.AlertType.ERROR, "Cannot add link: " + fileName + " unreadable").show();
            return;
        }
        String updated = note != null
                ? TaskFileParser.withNote(loaded, note)
                : TaskFileParser.addBrowserUrl(loaded, link, title);
        String error = files.save(fileName, updated);
        if (error != null) {
            new Alert(Alert.AlertType.ERROR, "Cannot add link: " + error).show();
            return;
        }
        if (fileName.equals(editedFileName)) {
            editedFileName = null;
            openInEditor(fileName);
        }
    }

    /// Removes one `browser.urls` entry — the trash icon (and `Remove link`
    /// menu item) on the terminal's PR header line, after a confirmation: the
    /// entry may carry a hand-written title, which is lost with it. The
    /// editor is flushed first so a just-edited file is not clobbered, and
    /// reloaded when it shows the file.
    // [impl->dsn~task-remove-link~2]
    private void removeTaskUrl(Task staleTask, Task.UrlEntry entry) {
        Alert confirm = Alerts.wrapping(Alert.AlertType.CONFIRMATION,
                "Remove " + entry.url() + " from the task?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Remove link");
        confirm.setHeaderText("Remove link");
        if (confirm.showAndWait().filter(button -> button == ButtonType.OK).isEmpty()) {
            return;
        }
        Task task = freshTask(staleTask);
        String fileName = task.id() + ".md";
        String loaded = files.read(fileName);
        if (loaded == null) {
            new Alert(Alert.AlertType.ERROR, "Cannot remove link: " + fileName + " unreadable").show();
            return;
        }
        String error = files.save(fileName, TaskFileParser.removeBrowserUrl(loaded, entry.url()));
        if (error != null) {
            new Alert(Alert.AlertType.ERROR, "Cannot remove link: " + error).show();
            return;
        }
        statusBar().message("Removed " + entry.url());
        if (task.id().equals(previewedTask == null ? null : previewedTask.id())) {
            previewedTask = freshTask(task);
            renderPrHeader();
        }
        if (fileName.equals(editedFileName)) {
            editedFileName = null;
            openInEditor(fileName);
        }
    }

    private static void copyTaskUrl(Task.UrlEntry entry) {
        ClipboardContent content = new ClipboardContent();
        content.putString(entry.url());
        Clipboard.getSystemClipboard().setContent(content);
    }

    /// Opens a task's `note:` link, resolved fresh from disk (the note icon
    /// takes no focus, so a just-edited file would otherwise be stale).
    // [impl->dsn~task-add-link~3]
    private void openTaskNote(Task staleTask) {
        saveEditorIfDirty();
        Task task = freshTask(staleTask);
        if (task.note() != null) {
            onOpenUrl.accept(task.note());
        }
    }

    /// A minimal-frontmatter title task in `group` (empty = root); opened in
    /// the editor lane, selected once the watcher delivers it. The group's
    /// `CONTEXTSWITCHER.md` defaults (remote, workspacesRoot/workdir) seed the
    /// new file's frontmatter — https://github.com/contextswitcher/contextswitcher-private/issues/45.
    /// Returns the new task's id (its file name without `.md`), or null when
    /// the file could not be written — the local flow needs it to record the
    /// tmux window it then starts (`dsn~task-create-local~4`).
    // [impl->dsn~task-create-ui~15]
    // [impl->dsn~group-config-apply~2]
    private @Nullable String createTitleTask(String title, String group) {
        String base = group.isEmpty() ? title : group + "/" + title;
        String fileName = uniqueFileName(TaskFileParser.newTaskFileName(base));
        GroupConfig defaults = groupDefaults(group);
        // Any URL inside the description also becomes a browser tab.
        // [impl->dsn~task-url-collect~1]
        String content = TaskFileParser.addUrlsFrom(
                TaskFileParser.newTaskContent(title, defaults.remote(), defaults.resolveWorkdir()),
                title);
        String error = files.save(fileName, content);
        if (error != null) {
            noteTaskCreationFailed();
            new Alert(Alert.AlertType.ERROR, "Cannot create " + fileName + ": " + error).show();
            return null;
        }
        // [impl->dsn~task-create-progress~5]
        statusBar().message("Task created.");
        String taskId = fileName.substring(0, fileName.length() - ".md".length());
        pendingSelectionId = taskId;
        openInEditor(fileName);
        return taskId;
    }

    /// Reports what a task's still-running remote creation is doing right now;
    /// the first call for a task starts its progress bar (and the repaint
    /// ticker), later ones only rename the step. FX thread.
    // [impl->dsn~task-create-progress~5]
    public void creationProgress(String taskId, String step) {
        TaskListCell.Creation running = creations.get(taskId);
        if (running != null) {
            // The row's label is bound to the step, so renaming it repaints
            // that label alone — no cell rebuild, no flicker.
            running.setStep(step);
            return;
        }
        creations.put(taskId, new TaskListCell.Creation(step, System.nanoTime()));
        if (creationTicker == null) {
            Timeline ticker = new Timeline(new KeyFrame(Duration.millis(500),
                    event -> creations.values().forEach(TaskListCell.Creation::tick)));
            ticker.setCycleCount(Animation.INDEFINITE);
            ticker.play();
            creationTicker = ticker;
        }
        // The only rebuild: the row has to grow its bar in the first place.
        refreshTaskRows();
    }

    /// Ends a task's creation progress — success or failure alike; the row
    /// falls back to its normal second line. FX thread.
    // [impl->dsn~task-create-progress~5]
    public void creationDone(String taskId) {
        if (creations.remove(taskId) == null) {
            return;
        }
        if (creations.isEmpty() && creationTicker != null) {
            creationTicker.stop();
            creationTicker = null;
        }
        refreshTaskRows();
    }

    /// Marks the last Add-task submission as failed: the next dialog, while
    /// the clipboard is unchanged since that submission, pre-fills the failed
    /// text (with the user's edits) instead of the clipboard, so a retry does
    /// not lose a hand-written description. The async creation flows (PR,
    /// live) call this from their error paths in `Main`.
    // [impl->dsn~task-create-retry-prefill~1]
    public void noteTaskCreationFailed() {
        lastSubmissionFailed = true;
    }

    /// The group's `CONTEXTSWITCHER.md` defaults, or [GroupConfig#EMPTY] for
    /// the root group or a folder without a config file.
    // [impl->dsn~group-config-apply~2]
    // [impl->dsn~group-config-cache~1]
    private GroupConfig groupDefaults(String group) {
        if (group.isEmpty()) {
            return GroupConfig.EMPTY;
        }
        return groupConfigCache.computeIfAbsent(group, name -> {
            String configFile = groupConfigFileName(name);
            return files.exists(configFile)
                    ? parser.parseGroupConfig(files.load(configFile))
                    : GroupConfig.EMPTY;
        });
    }

    /// What is wrong with the group's `CONTEXTSWITCHER.md` — why it does not
    /// load at all, else its duplicate frontmatter keys — or null when nothing
    /// is. A category is not a task and can never become an error row, so the
    /// header is the only place this can be seen from the list.
    // [impl->dsn~frontmatter-duplicate-keys~1]
    // [impl->dsn~group-config-parse-error~1]
    private @Nullable String groupConfigWarning(String group) {
        String configFile = groupConfigFileName(group);
        if (!files.exists(configFile)) {
            return null;
        }
        String content = files.load(configFile);
        String error = parser.groupConfigError(content);
        if (error != null) {
            return "%s does not load, so none of its settings apply: %s".formatted(
                    TaskRepository.GROUP_CONFIG_FILE_NAME, error);
        }
        List<String> duplicates = parser.duplicateFrontmatterKeys(content);
        if (duplicates.isEmpty()) {
            return null;
        }
        return "%s has duplicate %s %s — YAML keeps the last of each.".formatted(
                TaskRepository.GROUP_CONFIG_FILE_NAME,
                duplicates.size() == 1 ? "key" : "keys",
                String.join(", ", duplicates));
    }

    /// Whether the group's config exists but does not load. Only a cached
    /// [GroupConfig#EMPTY] can be one, so the file is read for those alone —
    /// this runs per task row on a rebuild.
    // [impl->dsn~group-config-parse-error~1]
    private boolean groupConfigBroken(String group) {
        String configFile = groupConfigFileName(group);
        return groupDefaults(group) == GroupConfig.EMPTY && files.exists(configFile)
                && parser.groupConfigError(files.load(configFile)) != null;
    }

    /// PR-URL branch of [#addTask]: an existing task carrying the URL is
    /// selected instead of duplicated; otherwise a remote is asked and the
    /// creation (tmux window, primed Claude, task file) is delegated.
    // [impl->dsn~task-from-pr~6]
    private void createTaskFromPrUrl(String rawUrl, String group, ClaudeMode mode) {
        // A pasted URL may carry a trailing slash the stored one does not.
        // [impl->dsn~browser-url-dedupe~3]
        String url = Task.normalizeUrl(rawUrl);
        var existing = entries.stream()
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(entry -> ((TaskEntry.Loaded) entry).task())
                .filter(task -> task.browser() != null && task.browser().urls().contains(url))
                .findFirst();
        if (existing.isPresent()) {
            selectTask(existing.get().id());
            return;
        }
        chooseRemote(remote -> {
            PREFERENCES.put(LAST_IMPORT_HOST, remote);
            onCreateFromPr.create(url, remote, group, mode);
        });
    }

    /// Picks the ssh host for a new PR task: the sole candidate (settings
    /// `remotes` ∪ the tasks' remotes) is used without a prompt; several
    /// offer an **editable** combo so an existing one is one click and a new
    /// one is one type; none falls back to a plain text prompt.
    // [impl->dsn~task-from-pr~6]
    private void chooseRemote(Consumer<String> onChosen) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>(configuredRemotes.get());
        entries.stream()
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(entry -> ((TaskEntry.Loaded) entry).task().remote())
                .filter(remote -> remote != null)
                .forEach(candidates::add);
        List<String> remotes = new ArrayList<>(candidates);
        if (remotes.size() == 1) {
            onChosen.accept(remotes.getFirst());
            return;
        }
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Add task from PR");
        dialog.setHeaderText("No task carries this PR yet — pick or type the ssh host for the new task.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ComboBox<String> combo = new ComboBox<>(
                FXCollections.observableArrayList(remotes));
        combo.setEditable(true);
        combo.setPromptText("alias from ~/.ssh/config or user@host");
        combo.getEditor().setText(defaultHost());
        combo.setPrefWidth(320);
        VBox content = new VBox(combo);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button ->
                button == ButtonType.OK ? combo.getEditor().getText().strip() : null);
        Platform.runLater(combo.getEditor()::requestFocus);
        dialog.showAndWait().filter(host -> !host.isEmpty()).ifPresent(onChosen);
    }

    private static String groupConfigFileName(String group) {
        return group + "/" + TaskRepository.GROUP_CONFIG_FILE_NAME;
    }

    /// Opens the group's `CONTEXTSWITCHER.md` defaults file in the editor
    /// lane, creating it with a mostly-commented skeleton first when it is
    /// missing. The `remote:` line is pre-filled (uncommented) with the first
    /// configured remote when one exists, so the common case needs no edit.
    /// The keys are not applied to tasks yet (https://github.com/contextswitcher/contextswitcher-private/issues/45) — this reserves the
    /// place where a group's host/workdir defaults live.
    // [impl->dsn~group-config-create~9]
    private void openGroupConfig(String group) {
        openGroupConfig(group, null);
    }

    /// As above; a non-null `desktop` pre-fills a freshly created config's
    /// `desktop:` line (no effect on an existing config).
    // [impl->dsn~desktop-filter-empty-state~2]
    private void openGroupConfig(String group, @Nullable String desktop) {
        if (ensureGroupConfig(group, desktop)) {
            openInEditor(groupConfigFileName(group));
            // Re-opening the already-open category config is a no-op above;
            // still put the Configuration pane in front, which is what was asked for.
            showConfigurationPane();
            // The editor now shows the category config: mirror that in the
            // left rail by selecting the header, and drop the task preview
            // either way, so the terminal and queue lanes cannot keep showing
            // an unrelated task (the header click path also clears via the
            // selection listener; this covers the creation and context-menu
            // paths). Right after `Add category…` the folder watcher has not
            // delivered the new folder yet, so the header row does not exist:
            // remember it as a pending selection and clear the old (task)
            // selection, so the coming rebuild focuses the category instead of
            // restoring the previously selected task — which would switch the
            // editor and terminal mirror back to that task.
            ListView<Object> list = taskList;
            if (list != null && !selectGroupHeaderRow(group)) {
                pendingGroupSelection = group;
                list.getSelectionModel().clearSelection();
            }
            previewedTask = null;
            onPreviewCleared.run();
            renderPrHeader();
        }
    }

    /// Selects the row of the group's header when it currently exists in the
    /// rendered list, returning whether it did. The header is absent right
    /// after `Add category…` (the folder watcher has not delivered the new
    /// folder yet); callers fall back to [#pendingGroupSelection].
    private boolean selectGroupHeaderRow(String group) {
        ListView<Object> list = taskList;
        if (list == null) {
            return false;
        }
        for (int i = 0; i < visibleRows.size(); i++) {
            if (visibleRows.get(i) instanceof GroupHeader header
                    && header.name().equals(group)) {
                list.getSelectionModel().select(i);
                scrollRowIntoView(list, i);
                return true;
            }
        }
        return false;
    }

    /// Selects (and scrolls to) the category's header row, expanding it when it
    /// was collapsed — the target of a `contextswitcher://category/<name>` deep
    /// link. Returns whether the category exists in the list.
    // [impl->dsn~category-link-copy~1]
    public boolean selectCategory(String group) {
        if (collapsedGroups.remove(group)) {
            rebuildRows();
        }
        return selectGroupHeaderRow(group);
    }

    /// Creates the group's `CONTEXTSWITCHER.md` skeleton (intro or compact per
    /// the `hints` setting, [TaskFileParser#groupConfigSkeleton]) if it does
    /// not exist yet (shared by "Add CS config…" and the group-tag toggle,
    /// which needs frontmatter to edit). Returns true when the file exists
    /// afterwards (already present or just created), false when creation
    /// failed. Does not open the editor.
    // [impl->dsn~group-config-create~9]
    // [impl->dsn~skeleton-hints~4]
    private boolean ensureGroupConfig(String group) {
        return ensureGroupConfig(group, null);
    }

    // [impl->dsn~desktop-filter-empty-state~2]
    private boolean ensureGroupConfig(String group, @Nullable String desktop) {
        String fileName = groupConfigFileName(group);
        if (files.exists(fileName)) {
            return true;
        }
        List<String> remotes = configuredRemotes.get();
        String remote = remotes.isEmpty() ? null : remotes.getFirst();
        String error = files.save(fileName,
                TaskFileParser.groupConfigSkeleton(group, remote, desktop, hints));
        if (error != null) {
            new Alert(Alert.AlertType.ERROR, "Cannot create " + fileName + ": " + error).show();
            return false;
        }
        // Nothing observable changes (the config is not a task), so repaint
        // explicitly: "Add CS config…" becomes "Edit CS config…".
        if (taskList != null) {
            taskList.refresh();
        }
        return true;
    }

    /// Toggles a tag on a project group's `CONTEXTSWITCHER.md` (creating the
    /// config first when missing), so the group — and every task in it — gains
    /// or loses the inherited tag. Written textually via `withTags` (the same
    /// comment-preserving writer as task tags), then the rows repaint.
    // [impl->dsn~task-tag-filter~2]
    private void toggleGroupTag(String group, String tag) {
        if (!ensureGroupConfig(group)) {
            return;
        }
        saveEditorIfDirty();
        String fileName = groupConfigFileName(group);
        String content = files.read(fileName);
        if (content == null) {
            new Alert(Alert.AlertType.ERROR, "Cannot tag group " + group + ": config unreadable").show();
            return;
        }
        List<String> tags = new ArrayList<>(parser.parseGroupConfig(content).tags());
        boolean removed = tags.removeIf(existing -> existing.equalsIgnoreCase(tag));
        if (!removed) {
            tags.add(tag);
        }
        String error = files.save(fileName, TaskFileParser.withTags(content, tags));
        if (error != null) {
            new Alert(Alert.AlertType.ERROR, "Cannot tag group " + group + ": " + error).show();
            return;
        }
        if (fileName.equals(editedFileName)) {
            editedFileName = null;
            openInEditor(fileName);
        }
        // Group tags are not task files, so nothing observable changes — repaint
        // so the header/child chips and any active filter update.
        rebuildRows();
        if (taskList != null) {
            taskList.refresh();
        }
    }

    /// Pins or unpins a category by writing (or removing) the `pinned:` line
    /// of its `CONTEXTSWITCHER.md`, creating the config first when missing —
    /// the same comment-preserving textual write as the group tags.
    // [impl->dsn~pinned-categories~1]
    private void toggleGroupPinned(String group) {
        if (!ensureGroupConfig(group)) {
            return;
        }
        saveEditorIfDirty();
        String fileName = groupConfigFileName(group);
        String content = files.read(fileName);
        if (content == null) {
            new Alert(Alert.AlertType.ERROR,
                    "Cannot pin category " + group + ": config unreadable").show();
            return;
        }
        boolean pinned = parser.parseGroupConfig(content).pinned();
        String error = files.save(fileName, TaskFileParser.withPinned(content, !pinned));
        if (error != null) {
            new Alert(Alert.AlertType.ERROR, "Cannot pin category " + group + ": " + error).show();
            return;
        }
        if (fileName.equals(editedFileName)) {
            editedFileName = null;
            openInEditor(fileName);
        }
        // A category config is not a task file, so nothing observable changes:
        // rebuild explicitly to re-order the sections.
        rebuildRows();
    }

    /// First free file name for a new task: `base.md`, then `base-2.md`, …
    // [impl->dsn~task-create-ui~15]
    private String uniqueFileName(String base) {
        String candidate = base + ".md";
        for (int i = 2; files.exists(candidate); i++) {
            candidate = base + "-" + i + ".md";
        }
        return candidate;
    }

    /// Sets a task's status by textually replacing the `status:` line of its
    /// file, preserving every other byte; the watcher refreshes the row. Used
    /// by both the status-label toggle (active ↔ suspended) and the right-click
    /// menu (which also reaches `done`). Suspending **and completing** both end
    /// the task's live context after an explicit confirmation (the tmux window
    /// is killed, browser tabs closed — MADR 0009), so a finished project stops
    /// consuming a remote session; resuming a suspended task runs a regular
    /// switch, whose resurrect path recreates the window and resumes Claude.
    /// A teardown first re-syncs the task against its own window
    /// ([#onRefreshClaude], `dsn~teardown-claude-resync~1`) and only then
    /// confirms, so it never warns about losing a session the task simply had
    /// not adopted yet.
    // [impl->dsn~task-status-cycle~2]
    // [impl->dsn~teardown-claude-resync~1]
    // [impl->dsn~task-suspend~6]
    // [impl->dsn~task-complete-suspend~1]
    private void setStatus(Task staleTask, TaskStatus status) {
        Task task = freshTask(staleTask);
        boolean suspending = status == TaskStatus.SUSPENDED && task.status() != TaskStatus.SUSPENDED;
        boolean completing = status == TaskStatus.DONE && task.status() != TaskStatus.DONE;
        boolean resuming = status == TaskStatus.ACTIVE && task.status() == TaskStatus.SUSPENDED;
        if (suspending || completing) {
            // Ask the window itself what it is running before judging what the
            // teardown would lose — Claude may have been started by hand in it
            // long after the task file was written. The task is re-read from
            // disk afterwards, so the confirmation sees the adopted session.
            onRefreshClaude.accept(task,
                    () -> tearDown(freshTask(task), status, completing));
            return;
        }
        writeStatus(task, status);
        if (resuming) {
            onSwitch.accept(task);
        }
    }

    /// Suspends or completes an (already re-synced) task after its
    /// confirmation: suspending and completing both end the live context, so
    /// the killed window's id must not linger. Resume (of a suspended task)
    /// resurrects: the switch passes the pre-flip status, which the tmux
    /// action reads as "recreate, do not focus the bare session".
    // [impl->dsn~task-suspend~6]
    // [impl->dsn~task-complete-suspend~1]
    private void tearDown(Task task, TaskStatus status, boolean completing) {
        if (!confirmTeardown(task, completing ? "Complete" : "Suspend")) {
            return;
        }
        rewriteFrontmatter(task, content ->
                TaskFileParser.withoutTmuxWindow(TaskFileParser.withStatus(content, status)));
        onSuspend.accept(task);
    }

    /// Resumes a suspended task from outside the list — the queue pane's
    /// send into a suspended chat. Same path as the row's "Resume".
    // [impl->dsn~message-queue-resume-send~2]
    public void resumeTask(Task task) {
        setStatus(task, TaskStatus.ACTIVE);
    }

    /// Pins or unpins a task by writing (or removing) the `pinned:` line of
    /// its file — the watcher's reload re-sorts the list, like a status flip.
    // [impl->dsn~pinned-tasks~2]
    private void toggleTaskPinned(Task staleTask) {
        Task task = freshTask(staleTask);
        rewriteFrontmatter(task, content -> TaskFileParser.withPinned(content, !task.pinned()));
    }

    /// The plain status write shared by the toggle/menu path and the
    /// switch-resumes path — no confirmation, no side effects.
    private void writeStatus(Task task, TaskStatus status) {
        rewriteFrontmatter(task, content -> TaskFileParser.withStatus(content, status));
    }

    /// Loads, transforms, and saves a task's file; an editor showing that
    /// file is reloaded so it does not overwrite the change on its next save.
    /// A failed write is reported in the status bar and logged instead of
    /// being swallowed — the change would otherwise be gone at the next
    /// restart with nothing having said so.
    // [impl->dsn~frontmatter-write-failure~1]
    private void rewriteFrontmatter(Task task, UnaryOperator<String> transform) {
        String fileName = task.id() + ".md";
        String content = files.read(fileName);
        if (content == null) {
            // File vanished (e.g. renamed by an import between read attempts).
            // Never save a transform of load()'s error placeholder back — that
            // is what corrupts a task file into unparseable error text.
            Logger.warn("Skipping frontmatter rewrite of {}: file unreadable", fileName);
            return;
        }
        String error = files.save(fileName, transform.apply(content));
        if (error != null) {
            // A failed write here is how a pin, a status flip or a tmux window
            // id silently fails to persist and is only noticed after the next
            // restart — so it is said out loud (status bar) and logged, never
            // swallowed. [impl->dsn~frontmatter-write-failure~1]
            Logger.warn("Cannot write {}: {}", fileName, error);
            statusBar().message("Cannot write " + fileName + ": " + error);
            return;
        }
        if (fileName.equals(editedFileName)) {
            editedFileName = null;
            openInEditor(fileName);
        }
        // The write re-sorts the row under the user: `status:` is the list's
        // primary sort key and `pinned:` heads its block, so suspending or
        // resuming the selected task moves its row into another block of a
        // long list. The rebuild preserves the selection by id but
        // deliberately does not scroll, so the highlight follows the row off
        // screen and the list looks like it lost the task (field report
        // 2026-09-11: resuming from a browser tab left the row nowhere in
        // view). Re-request it so the watcher's rebuild reveals it again.
        // [impl->dsn~status-flip-keeps-row-in-view~1]
        if (task.id().equals(selectedTaskId())) {
            pendingSelectionId = task.id();
            scrollToPendingSelection = true;
        }
    }

    /// Context-menu "Open in IntelliJ": ensures the task carries an `intellij:`
    /// section, then switches — the switch's IntelliJ action then opens the
    /// project just as pressing play would (`switchTask` re-reads the file, so
    /// it picks up the freshly written section). A bare section suffices only
    /// when a project path can be derived (`intellij.projectPath` or a `claude:`
    /// session's workspace/cwd); when none resolves — e.g. a PR-created task
    /// with no Claude session — the IntelliJ action would stay unconfigured, so
    /// we prompt for the remote project path and write `intellij.projectPath`.
    /// Like every switch, it highlights the row it acts on (id-based, so the
    /// watcher rebuilds its own write triggers keep it).
    // [impl->dsn~open-in-intellij~3]
    private void openInIntellij(Task staleTask) {
        Task task = freshTask(staleTask);
        String fileName = task.id() + ".md";
        // The gate is whether the IntelliJ action will have a project path, not
        // whether an `intellij:` section merely exists: a bare section with no
        // derivable path (no explicit projectPath, no claude: session) leaves
        // the action unconfigured and silently opens nothing.
        String updated;
        if (task.intellijProjectPath() == null) {
            String projectPath = promptForIntellijProjectPath(task);
            if (projectPath == null) {
                return;
            }
            String content = files.read(fileName);
            if (content == null) {
                new Alert(Alert.AlertType.ERROR, "Cannot add intellij section: " + fileName + " unreadable").show();
                return;
            }
            updated = TaskFileParser.withIntellijProjectPath(content, projectPath);
        } else if (task.intellij() == null) {
            // Path resolves (a claude: session) but no `intellij:` section yet.
            String content = files.read(fileName);
            if (content == null) {
                new Alert(Alert.AlertType.ERROR, "Cannot add intellij section: " + fileName + " unreadable").show();
                return;
            }
            updated = TaskFileParser.withIntellij(content);
        } else {
            updated = null; // already configured and resolvable — just switch.
        }
        if (updated != null) {
            String error = files.save(fileName, updated);
            if (error != null) {
                new Alert(Alert.AlertType.ERROR, "Cannot add intellij section: " + error).show();
                return;
            }
            if (fileName.equals(editedFileName)) {
                editedFileName = null;
                openInEditor(fileName);
            }
        }
        selectTask(task.id());
        // Switch to open IntelliJ, but bypass the remote-window prompt: this is
        // an explicit "open in IntelliJ", not a play, so a remote-only task must
        // not be sidetracked into the tmux/claude window dialog.
        switchResolved(freshTask(task));
    }

    /// Asks for the task's IntelliJ project path when none can be derived, so
    /// the switch's IntelliJ action has a path to open. Returns the entered
    /// path, or null when cancelled/blank (the caller then does nothing).
    // [impl->dsn~open-in-intellij~3]
    private @Nullable String promptForIntellijProjectPath(Task task) {
        String remote = task.intellijRemote();
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Open in IntelliJ");
        dialog.setHeaderText("\"" + task.title() + "\" has no project path to open"
                + " (no explicit intellij.projectPath and no claude: session).\n"
                + "Enter the project path on " + (remote == null ? "the remote" : remote) + ".");
        dialog.getEditor().setPrefColumnCount(40);
        return dialog.showAndWait().map(String::strip).filter(path -> !path.isEmpty()).orElse(null);
    }

    /// Switching a suspended task resumes it: the status flips to active
    /// before the actions run (whose resurrect path recreates the window).
    // [impl->dsn~task-suspend~6]
    private void switchTask(Task staleTask) {
        Task task = freshTask(staleTask);
        // A remote-only task has no window to focus yet: ask whether to open a
        // plain tmux shell or a Claude session on the remote, then create it.
        if (task.remote() != null && task.tmux() == null) {
            promptRemoteWindow(task);
            return;
        }
        switchResolved(task);
    }

    /// The switch core shared by play and "open in IntelliJ": resume a
    /// suspended task, then run the switch actions. No remote-window prompt —
    /// the caller has already resolved that a window exists (or does not want
    /// one created).
    private void switchResolved(Task task) {
        if (task.status() == TaskStatus.SUSPENDED) {
            writeStatus(task, TaskStatus.ACTIVE);
        }
        onSwitch.accept(task);
        // A window the poll saw at a bare shell (Claude killed or exited) gets
        // its Claude back as part of the switch — the same repair the `Claude`
        // button offers, so play is enough and nobody has to find the button.
        // [impl->dsn~start-claude-button~2]
        if (lacksClaude(task)) {
            claudeButton.setDisable(true);
            statusBar.message("Starting Claude …");
            onStartClaude.accept(task, () -> claudeButton.setDisable(false));
        }
    }

    /// Play on a task that carries a `remote:` but no `tmux:` section: there is
    /// no window to focus, so offer to create one — a plain **tmux** shell or a
    /// **claude** session — both started in the task's workspace on the remote.
    /// Once created, the file gains the `tmux:` (and, for claude, `claude:`)
    /// section, so the next play focuses it normally.
    // [impl->dsn~remote-window-choice~6]
    private void promptRemoteWindow(Task task) {
        RemoteWindowChoice choice = askRemoteWindow(task, task.remote(), true);
        if (choice != null) {
            onStartRemoteWindow.start(task, choice.withClaude(), choice.mode());
        }
    }

    /// The window choice itself, shown from both entry points that create one:
    /// play on a remote-only task and the terminal placeholder's `claude`
    /// button (`Main`'s window starter). A plain **tmux** shell or a **claude**
    /// session, the latter with the model and effort to start it with —
    /// "it differs from task to task", and a session started without asking ran
    /// whatever the remote CLI defaults to. The pickers are pre-filled from the
    /// last pick and a started Claude remembers its own
    /// (`dsn~claude-mode-select~3`); the plain shell has no model to pick, so
    /// **tmux** ignores them. Null when cancelled — the caller starts nothing.
    ///
    /// `remote` is the **resolved** host (the task's own `remote:`, else its
    /// category's) — the task's field alone read `null` in the header whenever
    /// the host came from `CONTEXTSWITCHER.md`. `offerTmux` is false when the
    /// caller already knows it wants Claude (the placeholder's `claude`
    /// button): the dialog is then only there to pick model and effort, and a
    /// **tmux** button next to it would undo the choice just made.
    // [impl->dsn~remote-window-choice~6]
    // [impl->dsn~claude-mode-select~3]
    public static @Nullable RemoteWindowChoice askRemoteWindow(
            Task task, @Nullable String remote, boolean offerTmux) {
        ButtonType tmux = new ButtonType("tmux", ButtonBar.ButtonData.OK_DONE);
        ButtonType claude = new ButtonType("claude", ButtonBar.ButtonData.OK_DONE);
        Alert alert = offerTmux
                ? new Alert(Alert.AlertType.CONFIRMATION, "", tmux, claude, ButtonType.CANCEL)
                : new Alert(Alert.AlertType.CONFIRMATION, "", claude, ButtonType.CANCEL);
        alert.setTitle("Open remote window");
        alert.setHeaderText(null);
        ComboBox<String> modelBox = QueuePane.modeBox("model: as is", ClaudeMode.MODELS);
        ComboBox<String> effortBox = QueuePane.modeBox("effort: as is", ClaudeMode.EFFORTS);
        ClaudeMode remembered = lastMode();
        QueuePane.selectMode(modelBox, remembered.model());
        QueuePane.selectMode(effortBox, remembered.effort());
        String host = remote == null ? "the remote" : remote;
        alert.getDialogPane().setContent(new VBox(8,
                new Label(offerTmux
                        ? "\"" + task.title() + "\" has a remote but no tmux window yet.\n"
                                + "Open one on " + host + "?"
                        : "Start a Claude session for \"" + task.title() + "\"\n"
                                + "on " + host + "?"),
                new HBox(6, modelBox, effortBox)));
        ButtonType picked = alert.showAndWait().orElse(ButtonType.CANCEL);
        if (picked != tmux && picked != claude) {
            return null;
        }
        ClaudeMode mode = new ClaudeMode(
                QueuePane.modeValue(modelBox), QueuePane.modeValue(effortBox));
        if (picked == claude) {
            rememberMode(mode);
        }
        return new RemoteWindowChoice(picked == claude, mode);
    }

    /// The hover action icons deliberately take no focus, so clicking one
    /// right after editing the task file means the editor has not auto-saved
    /// yet and the row still carries the pre-edit task. Flush the editor and
    /// act on what is really on disk; unparseable content falls back to the
    /// row's last good state.
    // [impl->dsn~task-row-hover-actions~7]
    private Task freshTask(Task task) {
        saveEditorIfDirty();
        try {
            return parser.parse(task.id(), files.load(task.id() + ".md"));
        } catch (TaskParseException e) {
            return task;
        }
    }

    /// Creates a new (empty) project-group folder from a dialog-entered name
    /// (slugged like task file names); the folder watcher shows it as a group
    /// with an "Add…" row. Reachable from the toolbar and the empty-area menu.
    /// With the active-desktop filter on, the new category is created for the
    /// desktop in view — the same pre-fill the filter's empty state does, so a
    /// category added while filtering does not immediately vanish from the list.
    // [impl->dsn~category-create-ui~2]
    private void addCategory() {
        addCategory(filterDesktop());
    }

    /// The desktop a category created right now belongs to: the active desktop
    /// while the desktop filter is on and it is known, else null.
    // [impl->dsn~category-create-ui~2]
    private @Nullable String filterDesktop() {
        return activeDesktopOnly ? activeDesktop : null;
    }

    /// With a `desktop`, the created config's `desktop:` line is pre-filled
    /// with it — the entry point of the desktop-filter empty state, which
    /// already knows the desktop the category is for.
    // [impl->dsn~desktop-filter-empty-state~2]
    private void addCategory(@Nullable String desktop) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add category");
        dialog.setHeaderText("Creates a project folder in the tasks directory;\n"
                + "it shows as a group and can hold tasks and a CS config."
                + (desktop == null ? ""
                        : "\nIts desktop: is pre-set to \"" + desktop + "\"."));
        dialog.setContentText("Name");
        dialog.showAndWait().map(TaskFileParser::slug).filter(name -> !name.isEmpty())
                .ifPresent(name -> {
                    String error = files.createFolder(name);
                    if (error != null) {
                        new Alert(Alert.AlertType.ERROR,
                                "Cannot create category %s: %s".formatted(name, error)).show();
                        return;
                    }
                    // A typed query hides task-less categories — this one
                    // included; clear it so the new folder is visible.
                    // [impl->dsn~task-folder-grouping-ui~8]
                    if (!searchQuery.strip().isEmpty()) {
                        closeFind();
                    }
                    // Create the CS config right away and open it, so the user
                    // lands in the defaults editor (remote, workspacesRoot).
                    openGroupConfig(name, desktop);
                });
    }

    /// Creates a category for a project repository URL and lets Claude set
    /// the project up on the remote: the folder is named after the
    /// repository, its `CONTEXTSWITCHER.md` is written with real values
    /// (remote, `workspacesRoot`, `mainCheckout`, `repo`, tag) instead of
    /// the commented skeleton, and a live task is started in the fresh
    /// workspaces root (`mkdir -p`ed by the live-task flow) whose prompt
    /// asks Claude to clone the repository and fill in the workspace-root
    /// `CLAUDE.md` template the prompt carries. The dialog takes the URL
    /// (pre-filled from the clipboard when that holds one) and the
    /// workspaces root, which follows the URL
    /// (`<parent>/<name>-workspaces`, the parent taken from a sibling
    /// category's root) until edited by hand, plus the repository type
    /// ([RepoType] — which template is filled in, and whether the clone is a
    /// fork) and an optional first message the setup prompt ends with, so
    /// the session continues into real work. Model and
    /// effort are picked as in the `Add task…` dialog. Needs a configured
    /// remote, like `Add remote Claude`.
    // [impl->dsn~category-from-url~4]
    private void addCategoryFromUrl() {
        String remote = effectiveRemote(GroupConfig.EMPTY);
        if (remote == null) {
            new Alert(Alert.AlertType.ERROR,
                    "Add category from URL needs a remote — set one in settings.yaml.").show();
            return;
        }
        String parent = workspacesParent();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add category from URL");
        dialog.setHeaderText("Creates a category named after the repository, with a CS config,\n"
                + "and starts a Claude session on " + remote + " that clones the repository\n"
                + "into the workspaces root and writes its CLAUDE.md.");
        TextField url = new TextField();
        String clip = Clipboard.getSystemClipboard().getString();
        if (clip != null && TaskFileParser.repoName(clip) != null) {
            url.setText(clip.strip());
        }
        TextField root = new TextField();
        boolean[] rootEdited = {false};
        Runnable followUrl = () -> {
            String name = TaskFileParser.repoName(url.getText());
            if (!rootEdited[0]) {
                root.setText(name == null ? "" : parent + "/" + name + "-workspaces");
            }
        };
        url.textProperty().addListener((obs, old, text) -> followUrl.run());
        root.setOnKeyTyped(event -> rootEdited[0] = true);
        followUrl.run();
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        // Labels never ellipsize: the label column keeps its preferred
        // width, the field column takes what is left and grows.
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setMinWidth(0);
        grid.getColumnConstraints().addAll(labels, fields);
        ComboBox<RepoType> repoType = new ComboBox<>();
        repoType.getItems().setAll(RepoType.values());
        repoType.setValue(RepoType.WRITE_ACCESS);
        repoType.getStyleClass().add(Styles.SMALL);
        // The setup session's model and effort, the Add-task dialog's boxes
        // pre-filled from the last pick actually sent.
        // [impl->dsn~claude-mode-select~3]
        ComboBox<String> modelBox = QueuePane.modeBox("model: as is", ClaudeMode.MODELS);
        ComboBox<String> effortBox = QueuePane.modeBox("effort: as is", ClaudeMode.EFFORTS);
        ClaudeMode remembered = lastMode();
        QueuePane.selectMode(modelBox, remembered.model());
        QueuePane.selectMode(effortBox, remembered.effort());
        TextArea firstMessage = new TextArea();
        firstMessage.setPromptText("Optional: what Claude should do once the setup is done");
        firstMessage.setPrefRowCount(3);
        firstMessage.setWrapText(true);
        grid.addRow(0, new Label("Repository URL"), url);
        grid.addRow(1, new Label("Workspaces root"), root);
        grid.addRow(2, new Label("Repository type"), repoType);
        grid.addRow(3, new Label("First message"), firstMessage);
        grid.add(new HBox(6, modelBox, effortBox), 1, 4);
        url.setPrefColumnCount(40);
        firstMessage.setPrefColumnCount(40);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> TaskFileParser.repoName(url.getText()) == null || root.getText().isBlank(),
                        url.textProperty(), root.textProperty()));
        Platform.runLater(url::requestFocus);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        ClaudeMode mode = new ClaudeMode(
                QueuePane.modeValue(modelBox), QueuePane.modeValue(effortBox));
        rememberMode(mode);
        createCategoryFromRepo(url.getText(), root.getText().strip(), remote, repoType.getValue(),
                mode, firstMessage.getText());
    }

    /// The category-from-URL flow behind [#addCategoryFromUrl]'s dialog, also
    /// the setup wizard's last page (`dsn~setup-wizard~8`): creates the folder
    /// and its config, then starts the setup session. With a `remote` that is
    /// a live task there; with none (a local category) the same description is
    /// handed to a local Claude in the workspaces root, the template appended
    /// to the prompt, since the local launcher carries no appendix of its own.
    // [impl->dsn~category-from-url~4]
    // [impl->dsn~setup-wizard~8]
    public void createCategoryFromRepo(String url, String workspacesRoot, @Nullable String remote,
            RepoType type, ClaudeMode mode, @Nullable String firstMessage) {
        String name = TaskFileParser.repoName(url);
        if (name == null || files.exists(name)) {
            new Alert(Alert.AlertType.ERROR, "Category " + name + " already exists.").show();
            return;
        }
        String error = files.createFolder(name);
        if (error == null) {
            error = files.save(groupConfigFileName(name),
                    TaskFileParser.groupConfigForRepo(name, url, remote, workspacesRoot,
                            filterDesktop()));
        }
        if (error != null) {
            new Alert(Alert.AlertType.ERROR,
                    "Cannot create category %s: %s".formatted(name, error)).show();
            return;
        }
        groupConfigCache.remove(name);
        String description = categorySetupDescription(url, name, workspacesRoot, type, firstMessage);
        if (remote != null) {
            onCreateLiveTask.create(description, remote, workspacesRoot, url.strip(), false, name,
                    mode, type.template());
            return;
        }
        String taskId = createTitleTask(description, name);
        if (taskId != null) {
            onStartLocalClaude.start(taskId, workspacesRoot,
                    description + "\n\n" + type.template(), mode);
        }
    }

    /// The directory the sibling workspaces roots live in — the parent of
    /// the first configured category's `workspacesRoot`, else `/data/koppor`
    /// (the skeleton's example) — so a new root lands next to the others.
    // [impl->dsn~category-from-url~4]
    private String workspacesParent() {
        String configured = configuredWorkspacesParent();
        return configured == null ? "/data/koppor" : configured;
    }

    /// The parent of the first configured category's `workspacesRoot`, or
    /// null when no category configures one — the setup wizard's re-run
    /// proposes a root there and otherwise under the checked home.
    // [impl->dsn~setup-wizard~8]
    public @Nullable String configuredWorkspacesParent() {
        return folders.stream().sorted()
                .map(group -> groupDefaults(group).workspacesRoot())
                .filter(root -> root != null && root.contains("/"))
                .map(root -> root.replaceAll("/+$", "").replaceAll("/[^/]*$", ""))
                .filter(parent -> !parent.isEmpty())
                .findFirst()
                .orElse(null);
    }

    /// The setup task's description for [#addCategoryFromUrl]: clone into
    /// the group-named subdirectory of the workspaces root (the
    /// `mainCheckout` the config names), then fill in the workspace-root
    /// `CLAUDE.md` template the prompt carries — the one [RepoType] the user
    /// picked ships. A `fork()` type clones a fork of the repository instead
    /// (created when there is none yet); `firstMessage` — when non-blank — is
    /// the work to do once the setup is done, so the setup session continues
    /// straight into it.
    // [impl->dsn~category-from-url~4]
    // [impl->dsn~claude-md-templates~1]
    static String categorySetupDescription(String url, String group, String workspacesRoot,
            RepoType type, @Nullable String firstMessage) {
        String clone = type.fork()
                ? "Fork the repository to your own GitHub account and clone the fork into the "
                        + "subdirectory %s of the current directory (`gh repo fork --clone` reuses "
                        + "an existing fork; keep the original repository as the `upstream` remote)"
                : "Clone the repository into the subdirectory %s of the current directory";
        String description = ("Set up the workspaces root %s for the project %s. "
                + clone
                + " (the primary clone, kept clean on its default branch; per-task git worktrees "
                + "are created next to it). Then write a CLAUDE.md into the current directory "
                + "from the template at the end of this prompt: replace every <placeholder> with "
                + "the real value, fill the build, test and pull-request sections from what the "
                + "clone's README, build files and CI workflows actually say, and delete the "
                + "sections that do not apply. Keep the rest of the template as it is.")
                .formatted(workspacesRoot, url.strip(), group);
        return firstMessage == null || firstMessage.isBlank()
                ? description
                : description + " After setting things up, please do the following: "
                        + firstMessage.strip();
    }

    /// Renames a project-group folder from a dialog (name slugged like
    /// creation). The watcher drops the old group and scans the renamed
    /// directory in place; task ids follow the new path. Collapsed state and
    /// an open editor file follow the new name; an existing target refuses.
    // [impl->dsn~group-rename~1]
    private void renameGroup(String group) {
        TextInputDialog dialog = new TextInputDialog(group);
        dialog.setTitle("Rename group");
        dialog.setHeaderText("Renames the project folder; task ids follow the new path.");
        dialog.setContentText("Name");
        dialog.showAndWait().map(TaskFileParser::slug)
                .filter(name -> !name.isEmpty() && !name.equals(group))
                .ifPresent(newName -> {
                    if (files.exists(newName)) {
                        new Alert(Alert.AlertType.ERROR,
                                "Cannot rename %s: %s already exists.".formatted(group, newName)).show();
                        return;
                    }
                    String error = files.renameFolder(group, newName);
                    if (error != null) {
                        new Alert(Alert.AlertType.ERROR,
                                "Cannot rename %s: %s".formatted(group, error)).show();
                        return;
                    }
                    if (collapsedGroups.remove(group)) {
                        collapsedGroups.add(newName);
                    }
                    String edited = editedFileName;
                    if (edited != null && edited.startsWith(group + "/")) {
                        editedFileName = null;
                        openInEditor(newName + edited.substring(group.length()));
                    }
                });
    }

    /// Deletes a project-group folder and everything in it from its header's
    /// context menu, after a confirmation naming the task count and that the
    /// action cannot be undone. Live tmux windows / Claude sessions of tasks
    /// inside are left running — only the files go. Collapsed state and an
    /// editor lane showing a file inside the folder are cleared.
    // [impl->dsn~category-delete~1]
    private void deleteGroup(String group) {
        long tasks = entries.stream().filter(entry -> entry.group().equals(group)).count();
        List<Task> running = entries.stream()
                .filter(entry -> entry.group().equals(group))
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(entry -> ((TaskEntry.Loaded) entry).task())
                .filter(this::hasLiveContext)
                .toList();
        String message = tasks == 0
                ? "Delete the empty category " + group + "?\nThis cannot be undone."
                : "Delete category " + group + " and its " + tasks
                        + (tasks == 1 ? " task" : " tasks") + "?\nThis cannot be undone."
                        + (running.isEmpty() ? "" : "\n" + running.size() + " running "
                                + (running.size() == 1 ? "task is" : "tasks are")
                                + " confirmed one by one next.");
        ButtonType delete = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        Alert confirm = Alerts.wrapping(Alert.AlertType.CONFIRMATION, message,
                delete, ButtonType.CANCEL);
        confirm.setTitle("Delete category");
        confirm.setHeaderText("Delete \"" + group + "\"");
        confirm.showAndWait().filter(button -> button == delete).ifPresent(button -> {
            // Each running task gets the full task-delete dialog (end window /
            // transcript / workdir). Cancelling — or "tidy up first" — aborts the
            // whole category delete; tasks already deleted stay deleted.
            for (Task task : running) {
                if (confirmAndDeleteTask(freshTask(task)) != DeleteChoice.DELETED) {
                    return;
                }
            }
            // Sweep the rest: non-running task files, the CS config, note, folder.
            String error = files.deleteFolder(group);
            if (error != null) {
                new Alert(Alert.AlertType.ERROR,
                        "Cannot delete %s: %s".formatted(group, error)).show();
                return;
            }
            collapsedGroups.remove(group);
            String edited = editedFileName;
            if (edited != null && edited.startsWith(group + "/")) {
                editedFileName = null;
                editorFileLabel.setText("");
                setEditorText("");
            }
        });
    }

    /// The group's `note:` URL from its `CONTEXTSWITCHER.md`, null without one.
    // [impl->dsn~group-note-open~5]
    private @Nullable String groupNoteUrl(String group) {
        String fileName = groupConfigFileName(group);
        return files.exists(fileName) ? TaskFileParser.groupNote(files.load(fileName)) : null;
    }

    /// Opens the group's note, flushing the editor first and resolving the
    /// URL fresh from disk — the note icon takes no focus, so a just-edited
    /// `CONTEXTSWITCHER.md` would otherwise yield the stale URL (the same
    /// trap [#freshTask] closes for task actions).
    // [impl->dsn~group-note-open~5]
    private void openGroupNote(String group) {
        saveEditorIfDirty();
        String note = groupNoteUrl(group);
        if (note != null) {
            onOpenUrl.accept(note);
        }
    }

    /// The group's `auto:` search query, null for a hand-filled category.
    // [impl->dsn~auto-category-placeholder-row~2]
    private @Nullable String groupAutoQuery(String group) {
        GroupConfig.AutoPr auto = groupDefaults(group).autoPr();
        return auto == null ? null : auto.query();
    }

    /// Opens the auto category's search on github.com, flushing the editor
    /// first like [#openGroupRepo].
    // [impl->dsn~auto-category-placeholder-row~2]
    private void openAutoSearch(String group) {
        saveEditorIfDirty();
        String query = groupAutoQuery(group);
        if (query != null) {
            onOpenUrl.accept(AutoPrLookup.searchUrl(query));
        }
    }

    /// The group's `repo:` URL from its `CONTEXTSWITCHER.md`, null without one.
    // [impl->dsn~group-repo-open~2]
    private @Nullable String groupRepoUrl(String group) {
        return groupDefaults(group).repo();
    }

    /// Opens the group's repository in the browser, flushing the editor and
    /// re-reading the config first — the icon takes no focus, so a just-edited
    /// `CONTEXTSWITCHER.md` would otherwise yield the stale URL (as for the
    /// note, [#openGroupNote]).
    // [impl->dsn~group-repo-open~2]
    private void openGroupRepo(String group) {
        saveEditorIfDirty();
        String repo = groupRepoUrl(group);
        if (repo != null) {
            onOpenUrl.accept(repo);
        }
    }

    /// The category's assigned virtual-desktop name from its `CONTEXTSWITCHER.md`
    /// `desktop:`, or the configured fallback desktop when it names none — so a
    /// category without a `desktop:` still has one, and the header's
    /// desktop-focus button shows for it. Null only with the fallback turned off
    /// (empty `fallbackDesktop`), which is the pre-fallback behavior.
    // [impl->dsn~category-desktop-focus~4]
    // [impl->dsn~fallback-desktop~1]
    private @Nullable String groupDesktop(String group) {
        String desktop = groupDefaults(group).desktop();
        if (desktop != null) {
            return desktop;
        }
        return fallbackDesktop.isBlank() ? null : fallbackDesktop;
    }

    /// Opens a throwaway tmux window on the category's remote (its config's
    /// `remote:`, in the group's workspace root when it sets one) — the header's
    /// terminal icon. Nothing is created locally: no task file, no selection, no
    /// mirror. A window worth keeping is adopted later via "Sync tmux windows…".
    /// The config is re-read after flushing the editor, like [#focusDesktop].
    // [impl->dsn~category-scratch-window~2]
    private void newTmuxWindow(String group, Runnable onSettled) {
        saveEditorIfDirty();
        GroupConfig defaults = groupDefaults(group);
        String remote = defaults.remote();
        if (remote != null) {
            onNewTmuxWindow.start(remote, defaults.resolveWorkdir(), onSettled);
        } else {
            onSettled.run();
        }
    }

    /// Focuses the category's virtual desktop, flushing the editor first and
    /// re-reading the config — the header button takes no focus, so a
    /// just-edited `desktop:` would otherwise switch to the stale name (the same
    /// trap [#openGroupNote] closes).
    // [impl->dsn~category-desktop-focus~4]
    private void focusDesktop(String group) {
        saveEditorIfDirty();
        String desktop = groupDesktop(group);
        if (desktop != null) {
            onFocusDesktop.accept(desktop);
        }
    }

    /// Opens the category's local `folders:` — the header's folder icon. Like
    /// [#focusDesktop] it flushes the editor and re-reads the config first, so
    /// a just-edited list is the one that opens, and it settles the button
    /// through `onSettled` whether the folders opened or not.
    // [impl->dsn~category-folders-button~1]
    private void openGroupFolders(String group, Runnable onSettled) {
        saveEditorIfDirty();
        List<String> folders = groupDefaults(group).folders();
        if (folders.isEmpty()) {
            onSettled.run();
            return;
        }
        onOpenFolders.accept(folders, onSettled);
    }

    /// Tearing down (suspend or complete) is silent in the normal case: Claude
    /// is waiting, its session identity is in the `claude:` section, resume
    /// recreates the window — nothing is lost by the kill. A dialog remains
    /// only where it protects something:
    ///  - Claude is **working** (`@cs_status`) — the kill hits it mid-task, so
    ///    an explicit "Force terminate" is required;
    ///  - the task has **no `claude:` section**, so resume cannot recreate the
    ///    window automatically;
    ///  - the `claude:` section has **no `sessionId`** yet — resume would
    ///    recreate the window but could not `claude --resume` the conversation,
    ///    so the session context would be lost (the id is published a few
    ///    seconds after the session starts and the post-create backfill records
    ///    it, `dsn~tmux-sync~7`; suspending before then is the risky case worth
    ///    a warning).
    /// `action` ("Suspend"/"Complete") titles the dialog and its confirm button
    /// ("Suspend nevertheless" / "Return").
    // [impl->dsn~task-suspend~6]
    // [impl->dsn~task-complete-suspend~1]
    private boolean confirmTeardown(Task task, String action) {
        Task.TmuxConfig tmux = task.tmux();
        String remote = task.remote();
        if (tmux == null || remote == null) {
            return true;
        }
        Task.ClaudeConfig claude = task.claude();
        boolean working = "working".equals(runningStatusFor(task));
        boolean missingSessionId = claude != null && claude.sessionId() == null;
        if (teardownIsSilent(task)) {
            return true;
        }
        String resumeNote;
        if (claude == null) {
            resumeNote = "No claude: section — resume recreates a plain window,"
                    + " but nothing running inside can be brought back.";
        } else if (missingSessionId) {
            resumeNote = "⚠ No Claude session id recorded yet — resume recreates the window but cannot"
                    + " restore the conversation (claude --resume needs the id). It is usually published a"
                    + " few seconds after the session starts and recorded automatically; suspend now and"
                    + " that context is lost.";
        } else {
            resumeNote = "On resume, the window is recreated and Claude resumed.";
        }
        ButtonType confirmButton = working
                ? new ButtonType("Force terminate")
                : new ButtonType(action + " nevertheless");
        ButtonType returnButton = new ButtonType("Return", ButtonBar.ButtonData.CANCEL_CLOSE);
        String message = working
                ? "Claude in window %s on %s is WORKING right now — terminating kills it mid-task.\n%s"
                        .formatted(tmux.target(), remote, resumeNote)
                : "End tmux window %s on %s?\n%s".formatted(tmux.target(), remote, resumeNote);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, message,
                confirmButton, returnButton);
        confirm.setTitle(action + " task");
        confirm.setHeaderText(action + " \"" + task.title() + "\"");
        return confirm.showAndWait().filter(button -> button == confirmButton).isPresent();
    }

    /// Whether ending the task's window loses nothing: Claude is not working
    /// (`@cs_status`), a `claude:` section is present, and its session id is
    /// recorded so resume can `--resume` it. The manual suspend then skips its
    /// dialog; the auto-suspend suspends only such tasks.
    // [impl->dsn~task-suspend~6]
    // [impl->dsn~auto-suspend-idle~2]
    private boolean teardownIsSilent(Task task) {
        Task.ClaudeConfig claude = task.claude();
        return !"working".equals(runningStatusFor(task))
                && claude != null && claude.sessionId() != null;
    }

    /// Suspends **one** active Claude task whose chat has published a status
    /// and been idle for at least `thresholdSeconds` — fed the status poll's
    /// `host windowId -> idle seconds` on the FX thread, right after the
    /// statuses it belongs to. Only a task the silent suspend would take
    /// without asking qualifies ([#teardownIsSilent]), and never the selected
    /// one — that is the task the user is looking at. The teardown is the
    /// manual suspend's ([#tearDown] without its dialog): status flip with
    /// timestamp, `window:` line dropped, kill + snapshot through `onSuspend`.
    /// The status bar's title counts the round down (`Auto-suspend 1/4: …`),
    /// so a lowered threshold visibly works through the pile.
    // ponytail: one task per tick — the suspend orchestrator shares one status
    // bar, so a burst would overwrite its own chips; the next tick takes the next.
    // [impl->dsn~auto-suspend-idle~2]
    public void autoSuspendIdle(Map<String, Long> idleByKey, long thresholdSeconds) {
        if (thresholdSeconds <= 0) {
            return;
        }
        ListView<Object> list = taskList;
        String selectedId = list != null
                && list.getSelectionModel().getSelectedItem() instanceof TaskEntry.Loaded loaded
                ? loaded.id() : null;
        List<Task> due = new ArrayList<>();
        for (TaskEntry entry : List.copyOf(entries)) {
            if (!(entry instanceof TaskEntry.Loaded loaded)
                    || loaded.task().status() != TaskStatus.ACTIVE
                    || loaded.id().equals(selectedId)) {
                continue;
            }
            Task task = loaded.task();
            String status = runningStatusFor(task);
            Long idle = task.remote() == null || task.tmux() == null || task.tmux().window() == null
                    ? null : idleByKey.get(task.remote() + " " + task.tmux().window());
            if (status == null || idle == null || idle < thresholdSeconds || !teardownIsSilent(task)) {
                continue;
            }
            due.add(task);
        }
        if (due.isEmpty()) {
            return;
        }
        Task task = due.getFirst();
        Logger.info("Auto-suspending {} ({} due)", task.id(), due.size());
        rewriteFrontmatter(task, content ->
                TaskFileParser.withoutTmuxWindow(TaskFileParser.withStatus(content, TaskStatus.SUSPENDED)));
        onSuspend.accept(task);
        statusBar.retitle("Auto-suspend, %d to go: %s (suspend)".formatted(due.size() - 1, task.title()));
    }

    /// Nominates **one** Claude task per PR-state round whose pull requests
    /// are all merged, so `Main` can look at its last screen and — if that
    /// screen needs nobody — end the window, remove transcript and worktree,
    /// and delete the task ([MergedCleanup] decides, `Main.cleanupMerged`
    /// acts). Called right after [#updatePrStates] with the same round.
    ///
    /// Deliberately narrow: only a task that carries a `claude:` section (a
    /// worktree session — an auto-category triage row is `AutoPrReconcile`'s
    /// business), never the selected row, never one whose window is `working`,
    /// and never one still carrying an unmerged or unknown pull request.
    /// A task marked **done** is left alone too: the user already gave that one
    /// its ending by hand, and the janitor is here for the tasks nobody ended.
    /// The candidate must have been part of *this* round (`fresh`), which is
    /// what paces the janitor: a PR already known merged is re-polled only
    /// every `PrStatePoller.MERGED_POLL_SECONDS`, so the ssh capture behind
    /// the decision happens at that rhythm rather than on every tick.
    // ponytail: one task per round, like the auto-suspend — the cleanup shares
    // the one status bar, and there is no hurry.
    // [impl->dsn~merged-task-cleanup~2]
    public void autoCleanupMerged(Map<String, PrInfo> fresh, int graceDays) {
        if (graceDays <= 0 || fresh.isEmpty()) {
            return;
        }
        ListView<Object> list = taskList;
        String selectedId = list != null
                && list.getSelectionModel().getSelectedItem() instanceof TaskEntry.Loaded loaded
                ? loaded.id() : null;
        for (TaskEntry entry : List.copyOf(entries)) {
            if (!(entry instanceof TaskEntry.Loaded loaded) || loaded.id().equals(selectedId)) {
                continue;
            }
            Task task = loaded.task();
            List<String> urls = task.prUrls();
            // A live task whose window the status poll has not reported on is
            // not a session this janitor may judge: it is either seconds old —
            // its window created between two poll rounds — or gone. Field
            // report 2026-09-12: a task created from a prompt that merely
            // *quoted* a long-merged pull request was archived and its window
            // killed three seconds after "Add task", because Claude was still
            // booting in that window and its screen therefore had nothing
            // alarming on it yet. The selected-row guard above did not catch
            // it: a row that has just appeared is selected through
            // `pendingSelectionId`, and a rebuild clears the ListView
            // selection in between.
            // A suspended task has no window left to be seen and keeps its own
            // route through the grace period.
            // [impl->dsn~auto-delete-opt-in~1]
            if (!MergedCleanup.judgeable(task, runningStatusFor(task))
                    || !groupDefaults(groupOf(task.id())).mayAutoDelete(task)
                    || urls.stream().noneMatch(fresh::containsKey)) {
                continue;
            }
            boolean allMerged = urls.stream().allMatch(url -> {
                PrInfo info = prInfoByUrl.get(url);
                return info != null && info.state() == PrState.MERGED;
            });
            if (allMerged) {
                onMergedCleanup.accept(task);
                return;
            }
        }
    }

    /// Reconciles one auto category against the poller's round
    /// (`dsn~auto-pr-category~3`), on the FX thread: a match no task carries
    /// yet becomes a task file, a task whose pull requests are all done
    /// (merged or closed — *not* merely absent from the query) is suspended
    /// and marked as suspended *for that*, one suspended longer than the
    /// category's grace period has its file deleted, and one of the
    /// reconcile's own suspends whose pull request reopened goes back to
    /// active.
    ///
    /// A suspend the **user** made carries no such mark and is therefore never
    /// undone — pausing a row is how a pull request is dismissed from an auto
    /// category, and a dismissed row is not deleted either while its pull
    /// request is open.
    ///
    /// A category task that carries a `tmux:` section is never touched — once
    /// a session runs in it, the row stopped being a triage entry and belongs
    /// to the user (`AutoPrReconcile`). The deletes are plain file deletes: no
    /// confirmation (the whole point is an unattended list) and no session
    /// kill (there is no session, by that same rule).
    // [impl->dsn~auto-pr-category~3]
    public void applyAutoPrs(AutoPrPoller.Round round) {
        List<Task> tasks = entries.stream()
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(entry -> ((TaskEntry.Loaded) entry).task())
                .toList();
        AutoPrReconcile.Plan plan = AutoPrReconcile.plan(tasks, round.category(),
                round.matches().stream().map(AutoPrLookup.Match::url).toList(),
                round.live(), round.config(), LocalDateTime.now());
        if (plan.isEmpty()) {
            return;
        }
        Map<String, String> titles = new HashMap<>();
        round.matches().forEach(match -> titles.put(match.url(), match.title()));
        int added = 0;
        for (String url : plan.add()) {
            added += createAutoPrTask(round.category(), url, titles.getOrDefault(url, url)) ? 1 : 0;
        }
        for (Task task : plan.suspend()) {
            Logger.info("Auto category {}: suspending {} — its PR is merged or closed",
                    round.category(), task.id());
            // Marked as *this* suspend's reason, so a reopened PR may undo it
            // while a suspend the user made by hand stays put.
            // [impl->dsn~auto-pr-category~3]
            rewriteFrontmatter(task, content -> Frontmatter.set(
                    TaskFileParser.withStatus(content, TaskStatus.SUSPENDED),
                    TaskFileParser.AUTO_PR_CLOSED, true));
        }
        for (Task task : plan.resume()) {
            Logger.info("Auto category {}: resuming {} — its PR is open again",
                    round.category(), task.id());
            // `withStatus` drops both the timestamp and the marker.
            // [impl->dsn~auto-pr-category~3]
            rewriteFrontmatter(task, content ->
                    TaskFileParser.withStatus(content, TaskStatus.ACTIVE));
        }
        int deleted = 0;
        // [impl->dsn~auto-delete-opt-in~1]
        for (Task task : plan.delete().stream()
                .filter(groupDefaults(round.category())::mayAutoDelete).toList()) {
            String fileName = task.id() + ".md";
            String error = files.delete(fileName);
            if (error != null) {
                Logger.warn("Cannot delete the expired auto task {}: {}", fileName, error);
                continue;
            }
            Logger.info("Auto category {}: deleted {} after its grace period",
                    round.category(), task.id());
            deleted++;
            if (fileName.equals(editedFileName)) {
                editedFileName = null;
                editorFileLabel.setText("");
                setEditorText("");
            }
        }
        statusBar().message("%s: %d added, %d suspended, %d deleted, %d resumed."
                .formatted(round.category(), added, plan.suspend().size(), deleted,
                        plan.resume().size()));
    }

    /// Writes the task file for one auto-added pull request: the PR title, the
    /// PR as the task's single browser URL, and the category's default remote
    /// so the row can be turned into a real session later. No `tmux:` section
    /// — nothing is started for it; the user switches to it when the PR is
    /// their turn. Returns whether the file was written.
    // [impl->dsn~auto-pr-category~3]
    // [impl->dsn~skeleton-hints~4]
    private boolean createAutoPrTask(String category, String url, String title) {
        String remote = groupDefaults(category).remote();
        String fileName = uniqueFileName(
                TaskFileParser.newTaskFileName(category + "/" + title));
        String content = TaskFileParser.prTaskContent(title, remote, url,
                hints ? "Added automatically from %s on %s."
                        .formatted(url, LocalDate.now()) : null);
        String error = files.save(fileName, content);
        if (error != null) {
            Logger.warn("Cannot create the auto task {}: {}", fileName, error);
            return false;
        }
        Logger.info("Auto category {}: added {} for {}", category, fileName, url);
        return true;
    }

    /// Moves a task file into `targetGroup`'s folder (empty = the root) after
    /// a drag'n'drop or a "Move to category" menu pick; the watcher updates
    /// the rows on both ends. The task id changes with the path — an editor
    /// lane showing the file follows it.
    // [impl->dsn~task-move-dnd~6]
    private void moveTask(String taskId, String targetGroup) {
        String fromFile = taskId + ".md";
        int slash = taskId.lastIndexOf('/');
        String base = slash < 0 ? taskId : taskId.substring(slash + 1);
        String toBase = targetGroup.isEmpty() ? base : targetGroup + "/" + base;
        if ((toBase + ".md").equals(fromFile)) {
            return;
        }
        // A same-named file in the target folder is never overwritten; the
        // moved task gets a -2/-3… suffixed name instead of a refusal.
        String toFile = uniqueFileName(toBase);
        String error = files.move(fromFile, toFile);
        if (error != null) {
            new Alert(Alert.AlertType.ERROR, "Cannot move %s: %s".formatted(fromFile, error)).show();
            return;
        }
        String toId = toFile.substring(0, toFile.length() - ".md".length());
        taskRenamed(taskId, toId);
        // The moved task must end up visible and selected: expand a collapsed
        // target group and select (+ scroll to) the row once the watcher
        // delivers it — also when the source row was not selected (a menu
        // move does not require selection first).
        collapsedGroups.remove(targetGroup);
        selectTask(toId);
    }

    /// Scrolls the list while a row drag hovers near its top or bottom edge,
    /// so drop targets outside the viewport become reachable — faster the
    /// closer to the edge. A Timeline does the scrolling: drag-over events
    /// stop coming while the mouse rests, but edge-hover scrolling must not.
    // [impl->dsn~task-move-dnd~6]
    /// Keeps the header of the section the list is scrolled into pinned to the
    /// list's top edge: a second `TaskListCell`, built by the same cell factory
    /// and pointed at the same row index, floats over the `ListView` in a
    /// `StackPane`. Reusing a real cell means the pinned header carries the live
    /// category's glyphs, tags, and menus for free, and a click on it collapses
    /// the group exactly like a click on the row it stands in for.
    // [impl->dsn~sticky-group-header~1]
    private StackPane installStickyHeader(ListView<Object> list) {
        TaskListCell cell = (TaskListCell) list.getCellFactory().call(list);
        cell.updateListView(list);
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMaxHeight(Region.USE_PREF_SIZE);
        cell.setVisible(false);
        this.stickyCell = cell;
        StackPane stack = new StackPane(list, cell);
        // main.css scopes the row rules to `.task-list .list-cell` — a descendant
        // selector the pinned cell escapes, since it hangs off the StackPane
        // beside the list rather than inside it. Carrying the class on the
        // StackPane too makes it an ancestor of both, so the pinned header gets
        // its (opaque!) section background and the same left-edge reserve as the
        // row it stands in for; without it the header is transparent and the
        // rows scroll through it.
        // `list-view` likewise: AtlantaFX defines the cell padding and size and
        // the cell colours (`-color-cell-bg-selected`, which the current
        // category's header uses) on `.list-view`, so without it the pinned
        // header had another shape and, as the current category, no
        // background at all. main.css drops the frame that class also brings.
        stack.getStyleClass().addAll("task-list", "list-view", "sticky-header-overlay");
        StackPane.setAlignment(cell, Pos.TOP_LEFT);
        // Only the pinned strip itself takes clicks; everywhere else the
        // overlay must fall through to the list underneath.
        stack.setPickOnBounds(false);
        // The scrollbar exists only once the skin is built. Every scroll source
        // (wheel, drag, keyboard) moves it, so one listener covers them all; a
        // resize can change the topmost row without touching it, hence the
        // second listener.
        list.skinProperty().addListener((obs, old, skin) ->
                Platform.runLater(() -> {
                    for (Node node : list.lookupAll(".scroll-bar")) {
                        if (node instanceof ScrollBar bar
                                && bar.getOrientation() == Orientation.VERTICAL) {
                            bar.valueProperty().addListener((o, a, b) -> updateStickyHeader());
                            // Stop short of the scrollbar: the overlay lies over
                            // the whole list, so a full-width pinned header would
                            // swallow clicks on the scrollbar's top strip.
                            cell.maxWidthProperty().bind(list.widthProperty().subtract(
                                    Bindings.when(bar.visibleProperty())
                                            .then(bar.widthProperty()).otherwise(0)));
                        }
                    }
                    updateStickyHeader();
                }));
        list.heightProperty().addListener((obs, old, height) -> updateStickyHeader());
        // A rebuild can leave the pinned index pointing at different content
        // (renamed group, changed tags), so re-render even when it is unchanged.
        visibleRows.addListener((ListChangeListener<Object>) change -> {
            stickyIndex = -1;
            Platform.runLater(this::updateStickyHeader);
        });
        return stack;
    }

    /// Repoints the pinned header at the nearest section header at or above the
    /// topmost fully visible row — hidden when that header *is* that row, since
    /// it is then on screen in its own right.
    private void updateStickyHeader() {
        TaskListCell cell = stickyCell;
        ListView<Object> list = taskList;
        if (cell == null || list == null) {
            return;
        }
        int header = -1;
        if (list.lookup(".virtual-flow") instanceof VirtualFlow<?> flow) {
            IndexedCell<?> first = flow.getFirstVisibleCell();
            int top = first == null ? -1 : Math.min(first.getIndex(), visibleRows.size() - 1);
            // The topmost cell is only partly on screen once it has scrolled
            // above the viewport, which is a negative layoutY inside the flow.
            boolean topFullyVisible = first != null && first.getLayoutY() >= -0.5;
            for (int i = top; i >= 0; i--) {
                if (isSectionHeader(visibleRows.get(i))) {
                    header = i == top && topFullyVisible ? -1 : i;
                    break;
                }
            }
        }
        if (header == stickyIndex) {
            return;
        }
        stickyIndex = header;
        // Unset first: a ListCell skips re-rendering when index and item are
        // unchanged, which would keep a stale current-category highlight.
        cell.updateIndex(-1);
        cell.updateIndex(header);
        cell.setVisible(header >= 0);
    }

    /// Brings a row into view — but only when it is not already on screen, and
    /// never flush against an edge.
    /// A row that is visible keeps its place (re-selecting the previewed task on
    /// a click in a pane must not yank the list around), and one that has to be
    /// scrolled to lands about a third down the viewport, clear of the pinned
    /// header and with rows above and below it to scroll into.
    // [impl->dsn~task-list-scroll-into-view~1]
    private void scrollRowIntoView(ListView<Object> list, int index) {
        if (index < 0) {
            return;
        }
        if (list.lookup(".virtual-flow") instanceof VirtualFlow<?> flow) {
            IndexedCell<?> first = flow.getFirstVisibleCell();
            IndexedCell<?> last = flow.getLastVisibleCell();
            if (first != null && last != null) {
                // Strictly between the edge cells: those two may be clipped by
                // the viewport, and the topmost one also sits under the pinned
                // section header, so neither counts as "on screen".
                if (index > first.getIndex() && index < last.getIndex()) {
                    return;
                }
                int rows = last.getIndex() - first.getIndex();
                flow.scrollToTop(Math.max(0, index - rows / 3));
                return;
            }
        }
        // Before the first layout there is no flow (or no cells) to measure.
        list.scrollTo(index);
    }

    private static boolean isSectionHeader(Object row) {
        return row instanceof GroupHeader || row instanceof LabelHeader
                || row instanceof TaskListCell.DoneHeader;
    }

    private static void installDragAutoScroll(ListView<Object> list) {
        double edge = 32;
        double maxPixelsPerTick = 12;
        double[] velocity = {0};
        Timeline scroller = new Timeline(new KeyFrame(Duration.millis(25), event -> {
            if (list.lookup(".virtual-flow") instanceof VirtualFlow<?> flow) {
                flow.scrollPixels(velocity[0]);
            }
        }));
        scroller.setCycleCount(Animation.INDEFINITE);
        // A filter, so the cells' own drag-over handlers (accepting the drop)
        // stay untouched.
        list.addEventFilter(DragEvent.DRAG_OVER, event -> {
            double y = event.getY();
            double fromBottom = list.getHeight() - y;
            if (y < edge) {
                velocity[0] = -maxPixelsPerTick * (edge - y) / edge;
                scroller.play();
            } else if (fromBottom < edge) {
                velocity[0] = maxPixelsPerTick * (edge - fromBottom) / edge;
                scroller.play();
            } else {
                scroller.stop();
            }
        });
        list.addEventFilter(DragEvent.DRAG_EXITED, event -> scroller.stop());
        list.addEventFilter(DragEvent.DRAG_DROPPED, event -> scroller.stop());
    }

    /// UI state following a task-file rename (title adoption, DnD move): a
    /// selected (or pending) row keeps its selection under the new id, an
    /// editor lane showing the file follows.
    // [impl->dsn~claude-title-sync~3]
    // [impl->dsn~task-move-dnd~6]
    /// Re-renders the task list after a group's `CONTEXTSWITCHER.md` changed
    /// on disk, so the headers pick up the fresh defaults (remote glyph, tags)
    /// without a restart.
    // [impl->dsn~task-repository-watching~6]
    /// Wires the repository's in-place rename (see [#taskRenamer]).
    public void setTaskRenamer(BiConsumer<String, String> renamer) {
        this.taskRenamer = renamer;
    }

    /// Wires the panes that follow a rename (see [#onTaskRenamed]).
    // [impl->dsn~message-queue-ui~26]
    public void setOnTaskRenamed(BiConsumer<String, String> listener) {
        this.onTaskRenamed = listener;
    }

    /// A background write to the open config — the repository's OneNote
    /// paste repair — reloads a clean editor, whose next auto-save would
    /// otherwise write the broken paste back.
    // [impl->dsn~onenote-paste-repair~1]
    public void groupConfigChanged() {
        String edited = editedFileName;
        if (edited != null && edited.endsWith("/" + TaskRepository.GROUP_CONFIG_FILE_NAME)
                && files.exists(edited) && !files.load(edited).equals(editorLoadedContent)) {
            reloadEditorIfShowing(edited);
        }
        rebuildRows();
    }

    public void taskRenamed(String fromId, String toId) {
        taskRenamer.accept(fromId, toId);
        // Before the selection below: the panes have to know the switch they
        // are about to be handed is this rename, not another task.
        // [impl->dsn~message-queue-ui~26]
        onTaskRenamed.accept(fromId, toId);
        ListView<Object> list = taskList;
        boolean selected = list != null
                && list.getSelectionModel().getSelectedItem() instanceof TaskEntry.Loaded loaded
                && loaded.id().equals(fromId);
        if (selected || fromId.equals(pendingSelectionId)) {
            selectTask(toId);
        }
        if ((fromId + ".md").equals(editedFileName)) {
            editedFileName = null;
            openInEditor(toId + ".md");
        }
    }

    /// The user's pick from the per-task delete dialog: the file was deleted,
    /// only a tidy-up prompt was sent, or the dialog was cancelled. A bulk
    /// caller (category delete) stops on anything but [#DELETED].
    private enum DeleteChoice { DELETED, TIDY_FIRST, CANCELLED }

    /// A task with a live context — a running tmux window, not suspended —
    /// whose window/session a plain file (or folder) delete would silently
    /// orphan. These are the "running" tasks the category delete asks about
    /// one by one. The window may live on a remote or in this machine's own
    /// tmux server (a local Claude session): both are ended the same way, so
    /// both are offered. [impl->dsn~terminal-local-mirror~2]
    private boolean hasLiveContext(Task task) {
        return task.status() != TaskStatus.SUSPENDED && task.tmux() != null;
    }

    /// Deletes a task's file after an explicit confirmation (the action is
    /// irreversible). Shared by the single-task delete and the per-running-task
    /// step of a category delete; the caller passes a [#freshTask] and owns any
    /// selection side effect.
    // [impl->dsn~task-delete~6]
    private void deleteTask(Task staleTask) {
        Task task = freshTask(staleTask);
        if (confirmAndDeleteTask(task) == DeleteChoice.DELETED) {
            selectNeighborOf(task.id());
        }
    }

    /// The label the tick carries while the browser is still being asked how
    /// many of the task's URLs are open.
    static final String BROWSER_TABS_CHECKING = "Close browser tabs — checking…";

    /// The "Close browser tabs" checkbox text — always naming *browser tabs*,
    /// with the number of tabs that are **actually open** once the browser has
    /// answered; the checkbox is disabled for anything but a positive number,
    /// there being nothing to close (`dsn~claude-session-kill~6`).
    /// Never "Close 1 PR": that reads as GitHub's close-the-pull-request, not
    /// as closing a tab (field report 2026-09-09).
    /// The count comes from the task's own `browser.urls` only, never a
    /// category- or desktop-wide tally, so the tick never promises closing tabs
    /// that belong to another task; `openTabs` below zero means the browser
    /// could not be asked, which the label says rather than passing off as
    /// "nothing open".
    static String browserTabsLabel(int openTabs) {
        if (openTabs < 0) {
            return "Close browser tabs — no browser extension connected";
        }
        if (openTabs == 0) {
            return "Close browser tabs — none open";
        }
        return "Close " + openTabs + (openTabs == 1 ? " browser tab" : " browser tabs");
    }

    /// Shows the delete confirmation for one (already fresh) task, with the
    /// kill checkboxes of `dsn~claude-session-kill~6`: end the tmux window,
    /// close browser tabs (independent of each other — the old combined
    /// "Terminate and delete" tick), remove the Claude transcript, and remove
    /// the per-task working directory (`claude.workspace` only —
    /// `claude.cwd` may be the group's shared workspacesRoot; always starts
    /// unticked). Window/browser/transcript ticks persist across runs (Java
    /// Preferences). While Claude is `working`, the dialog warns that ending
    /// the window kills it mid-task. "Ask Claude to tidy up first" sends the
    /// canned wrap-up prompt into the window and deletes nothing. The watcher
    /// removes the row; the editor lane is cleared if it was showing the
    /// deleted file. Returns which button the user pressed so a bulk caller
    /// can stop; a failed file delete reports [#CANCELLED].
    // [impl->dsn~task-delete~6]
    // [impl->dsn~claude-session-kill~6]
    private DeleteChoice confirmAndDeleteTask(Task task) {
        String fileName = task.id() + ".md";
        boolean liveContext = hasLiveContext(task);
        boolean working = liveContext && "working".equals(runningStatusFor(task));
        Task.ClaudeConfig claude = task.claude();
        boolean hasTranscript = task.remote() != null && claude != null && claude.sessionId() != null;
        String workspace = task.remote() != null && claude != null ? claude.workspace() : null;
        List<String> browserUrls = task.browser() == null ? List.of() : task.browser().urls();
        int queued = QueueFile.load(QueueFile.file(queuesDir, task.id())).size();

        // A window that meanwhile hosts another task's Claude session must not
        // be ended along with this task: the box is unticked, disabled and says
        // why, instead of quietly carrying the remembered "yes" into a kill of
        // someone else's session. (`TmuxKillAction` refuses it as well — this
        // is so the dialog does not promise something that will not happen.)
        // Closing browser tabs is unrelated to tmux ownership, so it keeps its
        // own tick regardless. [impl->dsn~tmux-window-ownership~4]
        String foreignOwner = foreignWindowOwner(task);
        String foreignWindow = task.tmux() == null ? "" : String.valueOf(task.tmux().window());
        CheckBox endWindow = new CheckBox(foreignOwner == null
                ? "End the tmux window"
                : "End the tmux window — not available: " + foreignWindow
                        + " now hosts another Claude session (" + foreignOwner + ")");
        endWindow.setWrapText(true);
        endWindow.setSelected(foreignOwner == null
                && PREFERENCES.getBoolean(KILL_END_WINDOW, true));
        endWindow.setDisable(foreignOwner != null);
        // How many tabs are really open is a round-trip to the browser, so the
        // tick starts off and says it is asking rather than blocking the
        // dialog on it; the answer enables it (or explains why it stays off).
        // [impl->dsn~claude-session-kill~6]
        CheckBox closeBrowserTabs = new CheckBox(BROWSER_TABS_CHECKING);
        closeBrowserTabs.setWrapText(true);
        closeBrowserTabs.setSelected(false);
        closeBrowserTabs.setDisable(true);
        if (!browserUrls.isEmpty()) {
            onCountOpenTabs.accept(browserUrls, openTabs -> {
                closeBrowserTabs.setText(browserTabsLabel(openTabs));
                closeBrowserTabs.setDisable(openTabs <= 0);
                closeBrowserTabs.setSelected(
                        openTabs > 0 && PREFERENCES.getBoolean(KILL_CLOSE_BROWSER_TABS, true));
            });
        } else {
            closeBrowserTabs.setText(browserTabsLabel(0));
        }
        CheckBox transcript = new CheckBox("Remove the Claude transcript on " + task.remote());
        transcript.setSelected(PREFERENCES.getBoolean(KILL_TRANSCRIPT, false));
        // A workspace that is one of the category's own directories — the main
        // checkout, the shared workspaces root, a pinned workdir — is not this
        // task's worktree, and removing it would take the whole category with
        // it (field report 2026-09-12). The same `rejectWorkdir` the cleanup
        // itself runs decides that here, so the dialog never offers what the
        // `rm -rf` would refuse. [impl->dsn~claude-session-kill~6]
        String rejected = workspace == null ? null : ClaudeSessionCleanup.rejectWorkdir(
                workspace, groupDefaults(groupOf(task.id())).protectedDirs());
        CheckBox workdir = new CheckBox(rejected == null
                ? "Remove the working directory: " + workspace
                : "Remove the working directory — not available: " + rejected);
        workdir.setWrapText(true);
        workdir.setDisable(rejected != null);

        String message = "Delete task file " + fileName + "?\nThis cannot be undone."
                + (working
                        ? "\nClaude in this task's window is WORKING right now — "
                                + "ending the window kills it mid-task."
                        : "");
        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        VBox content = new VBox(8, messageLabel);
        if (queued > 0) {
            Label queueWarning = new Label(queued == 1
                    ? "1 queued message for this task will be lost."
                    : queued + " queued messages for this task will be lost.");
            queueWarning.getStyleClass().add(Styles.DANGER);
            queueWarning.setWrapText(true);
            content.getChildren().add(queueWarning);
        }
        // A synced task is a mirror: deleting it deletes it for the group.
        // [impl->dsn~task-sync-groups-ui~1]
        String taskContent = files.read(fileName);
        TaskSync sync = taskSync;
        List<String> configured = sync == null ? List.of()
                : sync.groups().stream().map(TaskSync.Group::name).toList();
        List<String> syncGroups = taskContent == null ? List.of()
                : TaskSync.groupsOf(taskContent).stream().filter(configured::contains).toList();
        if (!syncGroups.isEmpty()) {
            Label syncWarning = new Label("This task is shared with the sync group%s %s and will be deleted for every member. To keep it for them, leave the group first (right-click › Sync groups)."
                    .formatted(syncGroups.size() == 1 ? "" : "s", String.join(", ", syncGroups)));
            syncWarning.getStyleClass().add(Styles.DANGER);
            syncWarning.setWrapText(true);
            content.getChildren().add(syncWarning);
        }
        if (liveContext) {
            content.getChildren().add(endWindow);
        }
        if (task.browser() != null) {
            content.getChildren().add(closeBrowserTabs);
        }
        if (hasTranscript) {
            content.getChildren().add(transcript);
        }
        if (workspace != null) {
            content.getChildren().add(workdir);
        }

        ButtonType delete = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        ButtonType tidyFirst = new ButtonType("Ask Claude to tidy up first");
        Alert confirm = liveContext
                ? Alerts.withContent(Alert.AlertType.CONFIRMATION, content,
                        delete, tidyFirst, ButtonType.CANCEL)
                : Alerts.withContent(Alert.AlertType.CONFIRMATION, content,
                        delete, ButtonType.CANCEL);
        confirm.setTitle("Delete task");
        confirm.setHeaderText("Delete \"" + task.title() + "\"");
        ButtonType chosen = confirm.showAndWait().orElse(ButtonType.CANCEL);
        if (chosen == ButtonType.CANCEL) {
            return DeleteChoice.CANCELLED;
        }
        if (chosen == tidyFirst) {
            onAskClaudeCleanup.accept(task);
            return DeleteChoice.TIDY_FIRST;
        }
        // Not while the box was forced off: that "no" is the app's, not the
        // user's, and remembering it would silently stop ending windows for
        // every later delete. [impl->dsn~tmux-window-ownership~4]
        if (liveContext && foreignOwner == null) {
            PREFERENCES.putBoolean(KILL_END_WINDOW, endWindow.isSelected());
        }
        // Only the user's own answer is remembered: a tick the app forced off
        // (nothing open, no extension) would otherwise stop closing tabs on
        // every later delete. [impl->dsn~claude-session-kill~6]
        boolean canCloseTabs = !closeBrowserTabs.isDisabled();
        if (canCloseTabs) {
            PREFERENCES.putBoolean(KILL_CLOSE_BROWSER_TABS, closeBrowserTabs.isSelected());
        }
        if (hasTranscript) {
            PREFERENCES.putBoolean(KILL_TRANSCRIPT, transcript.isSelected());
        }
        ClaudeSessionCleanup.Choices choices = new ClaudeSessionCleanup.Choices(
                liveContext && endWindow.isSelected(),
                canCloseTabs && closeBrowserTabs.isSelected(),
                hasTranscript && transcript.isSelected(),
                workspace != null && workdir.isSelected());
        if (choices.endWindow() || choices.closeBrowserTabs() || choices.remoteCleanup()) {
            onKillSession.accept(task, choices);
        }
        String error = files.delete(fileName);
        if (error != null) {
            new Alert(Alert.AlertType.ERROR, "Cannot delete " + fileName + ": " + error).show();
            return DeleteChoice.CANCELLED;
        }
        if (fileName.equals(editedFileName)) {
            editedFileName = null;
            editorFileLabel.setText("");
            setEditorText("");
        }
        return DeleteChoice.DELETED;
    }

    /// Deletes a corrupt / unparseable task file straight from its red error
    /// row (the trash button / context menu on [TaskListCell#errorRow]). A
    /// [TaskEntry.Failed] row carries no [Task] — none of the kill-dialog
    /// options (window, transcript, workdir) apply — so this is a plain
    /// confirm-and-delete of the offending file. The editor lane is cleared
    /// when it was showing the deleted file.
    // [impl->dsn~corrupt-task-delete~2]
    private void deleteCorruptFile(String fileName) {
        Alert confirm = Alerts.wrapping(Alert.AlertType.CONFIRMATION,
                "Delete the corrupt file " + fileName + "?\nThis cannot be undone.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Delete corrupt file");
        confirm.setHeaderText("Delete " + fileName);
        confirm.showAndWait()
                .filter(button -> button == ButtonType.OK)
                .ifPresent(button -> {
            String error = files.delete(fileName);
            if (error != null) {
                new Alert(Alert.AlertType.ERROR, "Cannot delete " + fileName + ": " + error).show();
                return;
            }
            if (fileName.equals(editedFileName)) {
                editedFileName = null;
                editorFileLabel.setText("");
                setEditorText("");
            }
        });
    }

    /// After a delete, the selection moves to the neighbouring task (next,
    /// else previous) so the snapshot pane does not keep showing the deleted
    /// task's session; without any task left, the pane resets. The choice is
    /// made now (the deleted row is still in the list), selected now for the
    /// immediate preview, and remembered — the watcher's rebuild clears the
    /// ListView selection moments later, and [#rebuildRows] re-applies it.
    // [impl->dsn~task-delete~6]
    private void selectNeighborOf(String taskId) {
        ListView<Object> list = taskList;
        if (list == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < visibleRows.size(); i++) {
            if (visibleRows.get(i) instanceof TaskEntry.Loaded loaded && loaded.id().equals(taskId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        for (int i = index + 1; i < visibleRows.size(); i++) {
            if (visibleRows.get(i) instanceof TaskEntry.Loaded loaded) {
                pendingSelectionId = loaded.id();
                list.getSelectionModel().select(i);
                return;
            }
        }
        for (int i = index - 1; i >= 0; i--) {
            if (visibleRows.get(i) instanceof TaskEntry.Loaded loaded) {
                pendingSelectionId = loaded.id();
                list.getSelectionModel().select(i);
                return;
            }
        }
        previewedTask = null;
        onPreviewCleared.run();
        renderPrHeader();
    }

    /// Reloads the editor lane if it currently shows `fileName` and has **no**
    /// unsaved edits — for a **background** write to the open file (the sync's
    /// session-id backfill / status reconcile, `dsn~tmux-sync~7`): a stale clean
    /// editor would otherwise both hide the written value and overwrite it on
    /// its next auto-save. Nulling `editedFileName` first keeps [#openInEditor]'s
    /// `saveEditorIfDirty` from re-flushing the (identical) content before the
    /// reload. When the editor **is** dirty the reload is skipped — the user's
    /// in-progress edits must not be discarded; the on-disk write stands and a
    /// later sync re-applies it if the editor's save meanwhile dropped it. FX
    /// thread only; a no-op when another file is open.
    // [impl->dsn~tmux-sync~7]
    public void reloadEditorIfShowing(String fileName) {
        if (fileName.equals(editedFileName) && editorText().equals(editorLoadedContent)) {
            editedFileName = null;
            openInEditor(fileName);
        }
    }

    /// Loads a task file into the editor lane unless it is already open there
    /// (a save triggers the file watcher, which must not stomp the open
    /// editor). Unsaved edits of the previous file are saved first — the
    /// focus-loss auto-save covers mouse flows, this covers the rest.
    // [impl->dsn~richtext-markdown-editor~9]
    private void openInEditor(String fileName) {
        if (fileName.equals(editedFileName)) {
            return;
        }
        saveEditorIfDirty();
        editedFileName = fileName;
        boolean config = fileName.endsWith("/" + TaskRepository.GROUP_CONFIG_FILE_NAME);
        editorTypeLabel.setText(config ? "Category config file" : "Task file");
        // A category is opened to edit its keys, so its pane comes forward. A
        // task switch leaves the docks alone: whichever pane the user has in
        // front stays there and just shows the new file.
        if (config) {
            showConfigurationPane();
        }
        String content = files.load(fileName);
        setEditorFileStatus(fileName, content, null);
        setEditorText(content);
    }

    /// The editor lane's status line: the file name, the transient note of the
    /// last action (`saved`, `reverted to disk`, a save error) when there is
    /// one, and this content's duplicate frontmatter keys when it has any.
    ///
    /// The label already doubled as the lane's status line, and a duplicate key
    /// is exactly what one comes here to fix — so it is stated where the
    /// offending line is on screen, not only in the log. It is appended *after*
    /// the note rather than replaced by it: the note is about the last action,
    /// the warning about the file, and a save must not make it disappear.
    // [impl->dsn~frontmatter-duplicate-keys~1]
    private void setEditorFileStatus(String fileName, String content, @Nullable String note) {
        List<String> duplicates = parser.duplicateFrontmatterKeys(content);
        StringBuilder text = new StringBuilder(fileName);
        if (note != null) {
            text.append(" — ").append(note);
        }
        if (!duplicates.isEmpty()) {
            text.append(" — duplicate ").append(duplicates.size() == 1 ? "key " : "keys ")
                    .append(String.join(", ", duplicates)).append("; the last of each wins");
        }
        editorFileLabel.setText(text.toString());
        editorFileLabel.getStyleClass().removeAll(Styles.TEXT_MUTED, Styles.WARNING);
        editorFileLabel.getStyleClass().add(duplicates.isEmpty() ? Styles.TEXT_MUTED : Styles.WARNING);
    }

    private void setEditorText(String content) {
        String[] parts = splitFrontmatter(content);
        setText(configEditor, parts[0]);
        setText(notesEditor, parts[1]);
        editorLoadedContent = editorText();
        // Every buffer replacement (file switch, revert, form apply) passes here.
        ConfigFormPane pane = configFormPane;
        if (pane != null) {
            pane.reload();
        }
    }

    private static void setText(RichTextArea area, String text) {
        area.getModel().replace(null, TextPos.ZERO, area.getModel().getDocumentEnd(), text);
        area.select(TextPos.ZERO);
    }

    private String editorText() {
        return joinFrontmatter(text(configModel), text(notesModel));
    }

    private static String text(CodeTextModel model) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < model.size(); i++) {
            if (i > 0) {
                text.append('\n');
            }
            text.append(model.getPlainText(i));
        }
        return text.toString();
    }

    /// The file content as the two tabs show it: `{frontmatter, notes}` —
    /// the YAML between the `---` fences without them, and the body after the
    /// closing fence line. Content without frontmatter (a `Cannot read …`
    /// placeholder, a plain note) is all notes. Fence detection is the
    /// parser's (`\r\n`→`\n`, BOM dropped, `\n---` closes).
    // [impl->dsn~richtext-markdown-editor~9]
    static String[] splitFrontmatter(String content) {
        String normalized = content.replace("\r\n", "\n");
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }
        int close = normalized.startsWith("---\n") ? normalized.indexOf("\n---", 3) : -1;
        if (close < 0) {
            return new String[] {"", normalized};
        }
        String frontmatter = close < 4 ? "" : normalized.substring(4, close);
        int bodyStart = normalized.indexOf('\n', close + 1);
        String notes = bodyStart < 0 ? "" : normalized.substring(bodyStart + 1);
        // Every task file has a blank line between the closing fence and its
        // `# Notes` heading - the template writes it - and keeping it made the
        // notes editor open on an empty first line (field report 2026-09-13:
        // "the empty line on top of the notes is strange"). It is structure,
        // not content, so the editor does not show it and `joinFrontmatter`
        // puts it back. Exactly one newline: a second blank line is the user's.
        // [impl->dsn~richtext-markdown-editor~9]
        if (notes.startsWith("\n")) {
            notes = notes.substring(1);
        }
        return new String[] {frontmatter, notes};
    }

    /// Inverse of [#splitFrontmatter]: the fences come back around a non-blank
    /// frontmatter; a blank one yields the bare notes — which the fence guard
    /// then refuses to save over a file that had frontmatter.
    // [impl->dsn~richtext-markdown-editor~9]
    static String joinFrontmatter(String frontmatter, String notes) {
        // The blank line `splitFrontmatter` hides goes back in, so a load and
        // a save leave the file as the template writes it.
        // [impl->dsn~richtext-markdown-editor~9]
        return frontmatter.isBlank() ? notes : "---\n" + frontmatter + "\n---\n\n" + notes;
    }

    private void saveEditor() {
        String fileName = editedFileName;
        if (fileName == null) {
            return;
        }
        String text = editorText();
        // Never let the editor lane persist content that dropped the opening
        // `---` frontmatter fence while the loaded content had one: that write
        // turns the file into an unparseable "must start with a '---'
        // frontmatter fence" row (the only unguarded writer — every textual
        // mutator is fence-guarded). A background rename/adoption race, or a
        // stray model round-trip, must not corrupt a valid task file this way.
        // [impl->dsn~richtext-markdown-editor~9]
        if (droppedFrontmatterFence(editorLoadedContent, text)) {
            Logger.warn("Refusing to save {}: the edit dropped the '---' frontmatter fence", fileName);
            editorFileLabel.setText(fileName + " — not saved (would drop '---' fence)");
            return;
        }
        String error = files.save(fileName, text);
        if (error == null) {
            editorLoadedContent = text;
        }
        setEditorFileStatus(fileName, text, error == null ? "saved" : error);
    }

    /// Whether `updated` would strip the leading `---` frontmatter fence off a
    /// `loaded` content that had one — the corruption guard for [#saveEditor].
    /// Both are normalized (`\r\n`→`\n`, BOM dropped) the way [TaskFileParser]
    /// parses them, so a fence-only difference is caught regardless of line
    /// endings.
    // [impl->dsn~richtext-markdown-editor~9]
    static boolean droppedFrontmatterFence(String loaded, String updated) {
        return startsWithFence(loaded) && !startsWithFence(updated);
    }

    private static boolean startsWithFence(String content) {
        String normalized = content.replace("\r\n", "\n");
        if (normalized.startsWith("﻿")) {
            normalized = normalized.substring(1);
        }
        return normalized.startsWith("---\n") || normalized.equals("---");
    }

    /// The auto-save half: writes only when the editor actually changed, so
    /// mere focus round-trips do not churn the file watcher.
    // [impl->dsn~richtext-markdown-editor~9]
    private void saveEditorIfDirty() {
        if (editedFileName != null && !editorText().equals(editorLoadedContent)) {
            saveEditor();
        }
    }

    /// Discards the editor content in favour of what is on disk. Fine-grained
    /// undo/redo within the editing session is the RichTextArea built-in
    /// (Ctrl+Z / Ctrl+Y).
    // [impl->dsn~richtext-markdown-editor~9]
    private void revertEditor() {
        String fileName = editedFileName;
        if (fileName == null) {
            return;
        }
        String content = files.load(fileName);
        setEditorText(content);
        setEditorFileStatus(fileName, content, "reverted to disk");
    }

    // [impl->dsn~richtext-markdown-editor~9]
    private BorderPane editorPane() {
        configModel.setDecorator(new MarkdownSyntaxDecorator(true));
        notesModel.setDecorator(new MarkdownSyntaxDecorator(false));
        configEditor.setId("config-editor");
        notesEditor.setId("notes-editor");
        for (RichTextArea editor : List.of(configEditor, notesEditor)) {
            wireEditor(editor);
        }

        Button revert = new Button("Revert");
        revert.getStyleClass().add(Styles.SMALL);
        revert.setTooltip(new Tooltip(
                "Reload the file from disk, discarding unsaved edits.\n"
                + "Undo/redo within the session: Ctrl+Z / Ctrl+Y."));
        // Not focus-traversable: clicking the button must not move focus out
        // of the editor — the focus-loss auto-save would write the edits to
        // disk first and turn the revert into a no-op.
        revert.setFocusTraversable(false);
        revert.setOnAction(event -> revertEditor());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        editorTypeLabel.setTooltip(new Tooltip("Press F1 for the field reference "
                + "(copy-and-pastable templates)."));
        Button addField = iconButton(MDIInterface.TUNE, "Configure fields — the frontmatter "
                + "as a form in the Configuration pane (raw YAML is the fallback)");
        addField.setId("add-field-button");
        // Not focus-traversable, for the reason the Revert button is not.
        addField.setFocusTraversable(false);
        addField.setOnAction(event -> showConfigForm());
        HBox header = new HBox(8, editorTypeLabel, editorFileLabel, spacer, addField, revert);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 6, 4, 6));
        header.getStyleClass().add("panel-header");
        editorFileLabel.getStyleClass().add(Styles.TEXT_MUTED);

        BorderPane pane = new BorderPane(notesEditor);
        pane.setTop(header);
        return pane;
    }

    /// Auto-save, Ctrl+S, F1 and the attachment hover — the same on both tabs.
    // [impl->dsn~richtext-markdown-editor~9]
    private void wireEditor(RichTextArea editor) {
        editor.setWrapText(true);
        // Auto-save: leaving the editor (clicking a row, another pane, the
        // other tab, …) writes the file when it changed; Ctrl+S stays as the
        // manual trigger.
        editor.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                saveEditorIfDirty();
            }
        });
        editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            hideImagePreview();
            if (new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN).match(event)) {
                saveEditor();
                event.consume();
            } else if (event.getCode() == KeyCode.F1) {
                showFieldHelp();
                event.consume();
            }
        });
        editor.setOnMouseMoved(event -> previewImageUnderPointer(editor, event));
        editor.setOnMouseExited(event -> hideImagePreview());
        editor.setOnScroll(event -> hideImagePreview());
        // [impl->dsn~attachment-copy-path~1]
        AttachmentPreview.installCopyPath(editor,
                event -> markerPathAt(editor, event.getScreenX(), event.getScreenY()), this::hideImagePreview);
    }

    /// Hovering an attachment marker pops its picture up next to the pointer,
    /// so a `[image: …\img-20260722-135316-762.png]` line can be recognised
    /// without opening the file. Re-entering the same marker leaves the popup
    /// where it is (no flicker while the pointer travels along the text);
    /// anything that is not a loadable image shows nothing.
    // [impl->dsn~attachment-image-hover~2]
    private void previewImageUnderPointer(RichTextArea editor, MouseEvent event) {
        imagePreview.show(editor, event, markerPathAt(editor, event.getScreenX(), event.getScreenY()));
    }

    /// The attachment marker path at a screen point of the editor, or null.
    private static @Nullable Path markerPathAt(RichTextArea editor, double screenX, double screenY) {
        TextPos pos = editor.getTextPosition(screenX, screenY);
        return pos == null
                ? null
                : Attachments.pathAt(editor.getModel().getPlainText(pos.index()), pos.offset());
    }

    private void hideImagePreview() {
        imagePreview.hide();
    }

    /// F1 in the editor: a non-blocking popup listing every field the open file
    /// accepts, as a commented, copy-and-pastable template with placeholders.
    /// The content is the same reference the app seeds/generates — `TEMPLATE.md`
    /// for a task file, the group-config skeleton for a `CONTEXTSWITCHER.md` —
    /// so the help never drifts from what the parser actually reads.
    // [impl->dsn~task-field-help~1]
    private void showFieldHelp() {
        boolean configFile = editedFileName != null && isGroupConfig(editedFileName);
        String reference;
        String title;
        if (configFile) {
            String group = editedFileName.substring(
                    0, editedFileName.length() - TaskRepository.GROUP_CONFIG_FILE_NAME.length() - 1);
            reference = TaskFileParser.groupConfigSkeleton(group, null, true);
            title = "Category config fields — CONTEXTSWITCHER.md";
        } else {
            reference = TaskRepository.templateReference();
            title = "Task file fields — copy & paste the parts you need";
        }
        showReferenceDialog(title, reference);
    }

    /// The notes header's configure button: puts the Configuration pane — the
    /// open file's frontmatter as a generated key/value form ([ConfigFormPane]),
    /// raw YAML its fallback — in front, reopening it if it was closed.
    // [impl->dsn~task-field-form~4]
    private void showConfigForm() {
        showConfigurationPane();
    }

    /// Brings Configuration forward — reopened if closed, its window raised if
    /// popped out. Nothing before [#show] has built the shell.
    // [impl->dsn~shell-layout~2]
    private void showConfigurationPane() {
        ShellFxHost.Hosted hosted = shell;
        if (hosted != null) {
            hosted.showConfiguration().run();
        }
    }

    /// Whether `fileName` is a category's `CONTEXTSWITCHER.md` rather than a
    /// task file — the two have different frontmatter keys.
    private static boolean isGroupConfig(String fileName) {
        return fileName.endsWith("/" + TaskRepository.GROUP_CONFIG_FILE_NAME)
                || fileName.equals(TaskRepository.GROUP_CONFIG_FILE_NAME);
    }

    /// The shared field-reference popup behind F1 — non-modal, read-only
    /// monospace text, `Copy all` without dismissing. Used by the editor lane
    /// ([#showFieldHelp()]) and the settings dialog.
    // [impl->dsn~task-field-help~1]
    private void showReferenceDialog(String title, String reference) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Every field is optional. Select what you need, Ctrl+C, "
                + "paste into the editor — or use Copy all.");
        dialog.setResizable(true);
        dialog.initModality(Modality.NONE);

        TextArea area = new TextArea(reference);
        area.setEditable(false);
        area.getStyleClass().add("monospace");
        area.setPrefColumnCount(72);
        area.setPrefRowCount(28);
        dialog.getDialogPane().setContent(area);

        ButtonType copyAll = new ButtonType("Copy all", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(copyAll, ButtonType.CLOSE);
        // Copy must not dismiss the popup — consume the button's action so the
        // reference stays open next to the editor while pasting field by field.
        dialog.getDialogPane().lookupButton(copyAll).addEventFilter(ActionEvent.ACTION, event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(reference);
            Clipboard.getSystemClipboard().setContent(content);
            event.consume();
        });
        dialog.show();
    }

    /// The project group the toolbar "Add task…" and `Ctrl+T` target: the
    /// category in view. That is the selected row's group while the selection
    /// is on screen, else the group of the list's topmost visible row — so a
    /// list scrolled away from the selection (or with none) does not fall back
    /// to whatever sorts first.
    // [impl->dsn~task-create-ui~15]
    private String selectedGroup() {
        ListView<Object> list = taskList;
        if (list == null) {
            return "";
        }
        int first = -1;
        int last = -1;
        if (list.lookup(".virtual-flow") instanceof VirtualFlow<?> flow
                && flow.getFirstVisibleCell() instanceof IndexedCell<?> top
                && flow.getLastVisibleCell() instanceof IndexedCell<?> bottom) {
            first = top.getIndex();
            last = bottom.getIndex();
        }
        return groupInView(visibleRows, list.getSelectionModel().getSelectedIndex(), first, last);
    }

    /// [#selectedGroup] over plain indices (-1: none): the selected row's group
    /// when it lies within `first..last`, else the first group found from the
    /// topmost visible row down, else the root.
    // [impl->dsn~task-create-ui~15]
    static String groupInView(List<Object> rows, int selected, int first, int last) {
        if (selected >= 0 && selected < rows.size()
                && (first < 0 || (selected >= first && selected <= last))) {
            String group = groupOfRow(rows.get(selected));
            return group == null ? "" : group;
        }
        for (int i = Math.max(first, 0); first >= 0 && i < rows.size(); i++) {
            String group = groupOfRow(rows.get(i));
            if (group != null) {
                return group;
            }
        }
        return "";
    }

    /// The group a list row belongs to, or null for a row outside any group
    /// (a label or done section header).
    private static @Nullable String groupOfRow(Object row) {
        return switch (row) {
            case GroupHeader header -> header.name();
            case TaskListCell.AddTaskRow add -> add.group();
            case TaskEntry.Loaded loaded -> groupOf(loaded.id());
            case TaskEntry.Failed failed -> groupOf(failed.fileName().replaceFirst("\\.md$", ""));
            default -> null;
        };
    }

    /// The folder part of a task id / relative path (before the last `/`), or
    /// the empty string for a root-level task.
    private static String groupOf(String id) {
        int slash = id.lastIndexOf('/');
        return slash < 0 ? "" : id.substring(0, slash);
    }

    /// The task list's toolbar, and the app-wide controls built with it that
    /// belong in the shell's main toolbar instead (energy saver, browser
    /// status, update, settings).
    // [impl->dsn~shell-layout~2]
    private record ListToolbar(ToolBar bar, List<Node> appControls) {
    }

    /// The task list's icon toolbar, sitting above the find field at the top of
    /// the left pane: the back/forward history arrows lead the bar, followed by
    /// an add menu (task / category), a filter menu (the three narrowing
    /// filters plus the tag filter as a submenu), a sort menu, a
    /// group-by-labels toggle, and single-shot icon buttons for
    /// open-tasks-directory, restart-running, and sync-tmux. Every control is
    /// icon-only
    /// (`Styles.SMALL`, `BUTTON_ICON`, `FLAT`) so the whole bar stays compact
    /// over the list. (Settings is the window's own top-right gear, not part of
    /// this list toolbar.)
    // [impl->dsn~task-list-toolbar~3]
    // [impl->dsn~tmux-task-import~12]
    private ListToolbar toolBar() {
        MenuButton filter = filterButton();
        MenuButton sort = sortButton();
        MenuButton groupBy = groupingButton();

        // [impl->dsn~task-history-navigation~2]
        Button back = iconButton(MDIInterface.ARROW_LEFT,
                "Back to the previously selected task (Alt+\u2190)");
        back.setOnAction(event -> navigateHistory(-1));
        Button forward = iconButton(MDIInterface.ARROW_RIGHT,
                "Forward again (Alt+\u2192)");
        forward.setOnAction(event -> navigateHistory(1));
        this.backButton = back;
        this.forwardButton = forward;
        updateHistoryButtons();

        // The occasional list-wide actions share one "more" menu, so the bar
        // keeps to what is read or clicked all day.
        MenuItem openTasksDir = tooltipMenuItem("Open tasks directory",
                "Open the folder holding the task files in the file manager.");
        openTasksDir.setOnAction(event -> onOpenTasksDir.run());

        // [impl->dsn~tmux-restart-running~1]
        MenuItem restartTmux = tooltipMenuItem("Restart running tasks…",
                "Recreate the tmux windows of the tasks marked running, e.g. after a host reboot.");
        restartTmux.setOnAction(event -> {
            if (!confirmRestartRunning()) {
                return;
            }
            restartTmux.setDisable(true);
            onRestartTmux.accept(() -> restartTmux.setDisable(false));
        });

        // [impl->dsn~tmux-sync~7]
        MenuItem syncTmux = tooltipMenuItem("Sync tmux windows…",
                "Mark tasks running or suspended as their tmux windows exist, and import windows without a task.");
        syncTmux.setOnAction(event -> {
            syncTmux.setDisable(true);
            onSyncTmux.accept(() -> syncTmux.setDisable(false));
        });

        // [impl->dsn~energy-saver~1]
        MenuItem refreshNow = tooltipMenuItem("Refresh now",
                "Run every poller once now (task statuses, titles, PRs, reviews), also while the energy saver is on.");
        refreshNow.setOnAction(event -> EnergySaver.refreshAll());
        this.refreshNowItem = refreshNow;
        ToggleButton energySaver = energySaverButton();

        // [impl->dsn~restart-to-update~11]
        Button update = updateNews.button();

        MenuButton add = addButton();

        Button browser = iconButton(MDITechnology.WEB, "");
        browser.setOnAction(event -> browserSwitch());
        // Right-click picks the browser to drive when both extensions are
        // connected. [impl->dsn~prefer-chosen-browser~1]
        ContextMenu browserMenu = new ContextMenu();
        fillBrowserMenu(browserMenu);
        browserMenu.setOnShowing(event -> fillBrowserMenu(browserMenu));
        browser.setContextMenu(browserMenu);
        this.browserStatus = browser;
        showBrowserExtension(null);

        MenuButton more = iconMenuButton(MDIInterface.DOTS_VERTICAL, "More list actions");
        more.getItems().addAll(openTasksDir, syncTmux, restartTmux, refreshNow);

        // The ⋮ menu's occasional actions sit at the right end, apart from the
        // controls in constant use. [impl->dsn~task-list-toolbar~3]
        Region moreSpacer = new Region();
        HBox.setHgrow(moreSpacer, Priority.ALWAYS);
        ToolBar bar = new ToolBar(back, forward, add, filter, sort, groupBy, moreSpacer, more);
        // Shares one fixed height (main.css) with every pane toolbar, so the
        // bands line up across the panes.
        bar.getStyleClass().add("list-toolbar");
        // The app-wide controls go to the shell's main toolbar; the list toolbar
        // keeps what acts on the task list. [impl->dsn~shell-layout~2]
        return new ListToolbar(bar, List.of(energySaver, browser, syncGroupsButton(), update, settingsButton()));
    }

    /// Reports a manual refresh in the info center: one notification naming the
    /// polls still running while they finish, replaced by the outcome — how
    /// long it took, or which polls failed — once the last one is done. The
    /// refresh item stays disabled meanwhile. FX thread.
    // [impl->dsn~refresh-progress~2]
    public void showRefreshProgress(Map<String, CompletableFuture<?>> polls) {
        ShellFxHost.Hosted hosted = shell;
        if (hosted == null || polls.isEmpty()) {
            return;
        }
        if (!hosted.infoCenter().getGroups().contains(backgroundWork)) {
            hosted.infoCenter().getGroups().add(backgroundWork);
        }
        backgroundWork.getNotifications().clear();
        Set<String> pending = new LinkedHashSet<>(polls.keySet());
        List<String> failed = new ArrayList<>();
        Notification<Void> progress = new Notification<>("Refreshing…", waitingFor(pending));
        progress.setOnClick(notification -> Notification.OnClickBehaviour.HIDE);
        backgroundWork.getNotifications().add(progress);
        MenuItem item = refreshNowItem;
        if (item != null) {
            item.setDisable(true);
        }
        long started = System.nanoTime();
        polls.forEach((name, poll) -> poll.whenComplete((ignored, error) -> Platform.runLater(() -> {
            pending.remove(name);
            if (error != null) {
                failed.add(name);
                Logger.warn(error, "Refresh of {} failed", name);
            }
            if (!pending.isEmpty()) {
                progress.setSummary(waitingFor(pending));
                return;
            }
            if (item != null) {
                item.setDisable(false);
            }
            progress.remove();
            Notification<Void> outcome;
            if (failed.isEmpty()) {
                outcome = new Notification<>("Refreshed", "%d polls in %.1f s".formatted(
                        polls.size(), (System.nanoTime() - started) / 1e9));
            } else {
                outcome = new Notification<>("Refresh incomplete", "Failed: " + String.join(", ", failed));
                outcome.setType(Notification.Type.WARNING);
            }
            backgroundWork.getNotifications().add(outcome);
        })));
    }

    private static String waitingFor(Set<String> pending) {
        return "Waiting for " + String.join(", ", pending);
    }

    /// Shows whether a browser extension is connected, and which browser's:
    /// the globe glyph left of the settings gear, struck through while nothing
    /// is connected. Clicking it runs the selected task's browser action alone
    /// — a play narrowed to the browser section. FX thread.
    // [impl->dsn~extension-connection-indicator~2]
    public void showBrowserExtension(@Nullable Browser connected) {
        Button status = browserStatus;
        if (status == null) {
            return;
        }
        SvgNode svg = new SvgNode(
                (connected == null ? MDITechnology.WEB_OFF : MDITechnology.WEB).path(), 16);
        status.setGraphic(svg);
        status.setTooltip(new Tooltip((connected == null
                ? "No browser extension connected — tab focus and tab-selects-task do nothing."
                : connected.displayName() + " extension connected.")
                + "\nClick: open the selected task's browser tabs."
                + "\nRight-click: choose the browser to use."));
    }

    /// One radio item per browser, the chosen one selected, the ones without a
    /// connected extension marked.
    // [impl->dsn~prefer-chosen-browser~1]
    private void fillBrowserMenu(ContextMenu menu) {
        ToggleGroup group = new ToggleGroup();
        Set<Browser> connected = connectedBrowsers.get();
        Browser chosen = chosenBrowser.get();
        menu.getItems().setAll(java.util.Arrays.stream(Browser.values()).map(candidate -> {
            RadioMenuItem item = new RadioMenuItem(candidate.displayName()
                    + (connected.contains(candidate) ? "" : " (not connected)"));
            item.setToggleGroup(group);
            item.setSelected(candidate == chosen);
            item.setOnAction(event -> onBrowserChoice.accept(candidate));
            return item;
        }).toList());
    }

    /// The globe's click: the selected task's browser action, nothing else.
    /// Runs on the task as it stands on disk (like play), and says so in the
    /// status bar when there is no task selected.
    // [impl->dsn~extension-connection-indicator~2]
    private void browserSwitch() {
        Task task = previewedTask;
        if (task == null) {
            statusBar().message("Select a task to open its browser tabs.");
            return;
        }
        onBrowserSwitch.accept(freshTask(task));
    }

    /// The sync-groups control: a people-sync glyph whose badge counts the
    /// shared tasks waiting to be sorted in; a click opens [SyncGroupsWindow].
    /// Always present — it is also where the first group gets configured.
    // [impl->dsn~task-sync-groups-ui~1]
    private Button syncGroupsButton() {
        Button button = iconButton(MDIInterface.ACCOUNT_SYNC, "Sync groups");
        Badge badge = new Badge(button);
        badge.show((int) syncIncoming.stream().filter(task -> !task.ignored()).count());
        this.syncBadge = badge;
        button.setOnAction(event -> openSyncGroups());
        this.syncButton = button;
        return button;
    }

    /// Installs the sync groups: `round` syncs every group off the FX thread
    /// and hands the result to [#showSyncIncoming].
    // [impl->dsn~task-sync-groups-ui~1]
    public void setTaskSync(TaskSync sync, Runnable round) {
        this.taskSync = sync;
        this.syncRound = round;
    }

    /// A sync round's shared tasks waiting to be sorted in: the badge counts
    /// the ones not ignored, an open window re-renders. FX thread.
    // [impl->dsn~task-sync-groups-ui~1]
    public void showSyncIncoming(List<TaskSync.Incoming> incoming) {
        syncIncoming = incoming;
        long waiting = incoming.stream().filter(task -> !task.ignored()).count();
        Badge badge = syncBadge;
        if (badge != null) {
            badge.show((int) waiting);
        }
        Button button = syncButton;
        if (button != null) {
            button.setTooltip(new Tooltip(waiting == 0 ? "Sync groups"
                    : "Sync groups — %d new shared task%s to sort in".formatted(waiting, waiting == 1 ? "" : "s")));
        }
        SyncGroupsWindow window = syncWindow;
        if (window != null) {
            window.update(incoming);
        }
    }

    private void openSyncGroups() {
        TaskSync sync = taskSync;
        if (sync == null) {
            return;
        }
        SyncGroupsWindow window = syncWindow;
        if (window == null) {
            Stage owner = stage;
            if (owner == null) {
                return;
            }
            window = new SyncGroupsWindow(owner, sync, () -> folders.stream().sorted().toList(),
                    this::sortInSharedTask, task -> {
                        sync.ignore(task);
                        showSyncIncoming(syncIncoming.stream().map(other -> other.equals(task)
                                ? new TaskSync.Incoming(task.group(), task.syncId(), task.title(),
                                        task.content(), true, task.localFile())
                                : other).toList());
                    }, syncRound);
            syncWindow = window;
        }
        window.update(syncIncoming);
        window.show();
    }

    /// The row menu's "Sync groups" submenu: one check item per configured
    /// group. Joining shares the task with the group; leaving keeps the local
    /// task and sets the shared one aside. Null while no group is configured.
    // [impl->dsn~task-sync-groups-ui~1]
    private @Nullable Menu syncGroupsMenu(Task task) {
        TaskSync sync = taskSync;
        List<TaskSync.Group> groups = sync == null ? List.of() : sync.groups();
        if (sync == null || groups.isEmpty()) {
            return null;
        }
        String fileName = task.id() + ".md";
        String content = files.read(fileName);
        if (content == null) {
            return null;
        }
        List<String> member = TaskSync.groupsOf(content);
        Menu menu = new Menu("Sync groups");
        for (TaskSync.Group group : groups) {
            CheckMenuItem item = new CheckMenuItem(group.name());
            item.setSelected(member.contains(group.name()));
            item.setOnAction(event -> {
                String current = files.read(fileName);
                if (current == null) {
                    return;
                }
                String base = task.id().substring(task.id().lastIndexOf('/') + 1);
                String error = files.save(fileName, TaskSync.withGroup(current, group.name(),
                        item.isSelected(), sync.syncIdFor(current, group.name(), base)));
                if (error != null) {
                    new Alert(Alert.AlertType.ERROR, "Cannot save %s: %s".formatted(fileName, error)).show();
                    return;
                }
                syncRound.run();
            });
            menu.getItems().add(item);
        }
        return menu;
    }

    /// Sorts a shared task into `category` (empty = the root): a task that
    /// left the group earlier and still exists rejoins and moves there,
    /// otherwise a new local task file is created from the shared content.
    // [impl->dsn~task-sync-groups-ui~1]
    private void sortInSharedTask(TaskSync.Incoming incoming, String category) {
        String localFile = incoming.localFile();
        if (localFile != null && files.exists(localFile)) {
            String current = files.read(localFile);
            if (current == null) {
                return;
            }
            String error = files.save(localFile,
                    TaskSync.withGroup(current, incoming.group(), true, incoming.syncId()));
            if (error != null) {
                new Alert(Alert.AlertType.ERROR, "Cannot save %s: %s".formatted(localFile, error)).show();
                return;
            }
            moveTask(localFile.substring(0, localFile.length() - ".md".length()), category);
        } else {
            String fileName = uniqueFileName(category.isEmpty()
                    ? incoming.syncId() : category + "/" + incoming.syncId());
            String error = files.save(fileName, TaskSync.sortInContent(incoming));
            if (error != null) {
                new Alert(Alert.AlertType.ERROR, "Cannot create %s: %s".formatted(fileName, error)).show();
                return;
            }
            String id = fileName.substring(0, fileName.length() - ".md".length());
            collapsedGroups.remove(category);
            selectTask(id);
        }
        showSyncIncoming(syncIncoming.stream().filter(other -> !other.equals(incoming)).toList());
        syncRound.run();
    }

    /// The settings control: the gear icon at the right end of the list
    /// toolbar (COG — the one place a plain cog appears, now that Sync tmux
    /// uses SYNC).
    // [impl->dsn~settings-editor~4]
    private Button settingsButton() {
        Button editSettings = iconButton(MDIInterface.COG, "Settings");
        editSettings.setOnAction(event -> settingsDialog.show());
        return editSettings;
    }

    /// The window hosting this pane, for parenting modal dialogs to it. An
    /// ownerless `Dialog` is application-modal (it grays the whole UI) but
    /// appears as its own top-level window — with the main window pinned to
    /// every virtual desktop (`showOnAllDesktops`), that orphan opened on the
    /// primary desktop while the user was on another, so the dialog was
    /// invisible and the app just looked frozen. Parenting it puts it over the
    /// main window on the desktop the user is actually on.
    private @Nullable Window dialogOwner() {
        ListView<Object> list = taskList;
        return list == null || list.getScene() == null ? null : list.getScene().getWindow();
    }

    /// The toolbar filter menu (funnel icon): the three narrowing filters as
    /// check items — "Awaits input only" (carrying the live count), "Show active
    /// desktop only" (carrying the active desktop's name), and "Show running
    /// tasks" — plus the tag filter as a "Tags" submenu. All restored from the
    /// last run (`dsn~filter-persistence~1`); the button's badge shows how many
    /// are active.
    // [impl->dsn~task-list-toolbar~3]
    // [impl->dsn~awaits-input-filter~1]
    // [impl->dsn~active-desktop-filter~6]
    // [impl->dsn~running-tasks-filter~1]
    // [impl->dsn~task-tag-filter~2]
    private MenuButton filterButton() {
        MenuButton button = iconMenuButton(MDIInterface.FILTER, "Filter the task list");
        this.filterButton = button;
        CheckMenuItem desktopItem = activeDesktopItem();
        button.getItems().addAll(awaitsInputItem(), runningTasksItem(), desktopItem,
                noDesktopCategoriesItem(desktopItem), new SeparatorMenuItem(), tagFilterMenu());
        updateFilterButton();
        return button;
    }

    /// Labels the filter button with the count of active narrowing dimensions —
    /// each of the three toggles plus "any tag selected" ("" when none, so the
    /// bare funnel shows).
    // [impl->dsn~task-list-toolbar~3]
    private void updateFilterButton() {
        MenuButton button = filterButton;
        if (button == null) {
            return;
        }
        int active = (awaitsInputOnly ? 1 : 0) + (activeDesktopOnly ? 1 : 0)
                + (runningTasksOnly ? 1 : 0) + (activeTags.isEmpty() ? 0 : 1);
        // A digit-wide figure space rather than "" keeps the button's width: a
        // width change re-flows an overflowing toolbar, and the re-flow
        // detaches the button and closes its open menu mid-tick.
        button.setText(active == 0 ? "\u2007" : String.valueOf(active));
    }

    /// Confirmation for the bulk restart: it recreates a window per running
    /// task, so it must not be a stray click. Names the count, and warns that
    /// a window that is still alive is left behind rather than killed — the
    /// button targets the after-a-reboot case where nothing is alive.
    // [impl->dsn~tmux-restart-running~1]
    private boolean confirmRestartRunning() {
        long count = entries.stream()
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(entry -> ((TaskEntry.Loaded) entry).task())
                .filter(task -> task.status() == TaskStatus.ACTIVE
                        && task.tmux() != null && task.remote() != null)
                .count();
        if (count == 0) {
            new Alert(Alert.AlertType.INFORMATION,
                    "No running task with a remote tmux window to restart.").show();
            return false;
        }
        ButtonType restartButton = new ButtonType("Restart");
        ButtonType returnButton = new ButtonType("Return", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                ("Recreate the tmux window of %d running task(s) and resume Claude in it?\n"
                        + "Use this after the remote machine rebooted — a window that is still"
                        + " alive is not killed, it is left behind as an orphan.").formatted(count),
                restartButton, returnButton);
        confirm.setTitle("Restart running tasks");
        confirm.setHeaderText("Restart running tasks");
        return confirm.showAndWait().filter(button -> button == restartButton).isPresent();
    }

    /// The toolbar "Sort" menu (icon-only, `SORT_VARIANT`): one radio item per
    /// [TaskOrder] mode. The pick is persisted (`Preferences`) — a sort order is
    /// how the user reads the list — like the filters, which are kept too.
    /// (Grouping is its own menu, [#groupingButton].)
    // [impl->dsn~task-sort-modes~2]
    // [impl->dsn~task-list-toolbar~3]
    private MenuButton sortButton() {
        MenuButton button = iconMenuButton(MDIInterface.SORT_VARIANT,
                "Sort order of the tasks within their groups");
        ToggleGroup modes = new ToggleGroup();
        for (TaskOrder mode : TaskOrder.values()) {
            RadioMenuItem item = new RadioMenuItem(mode.label());
            item.setToggleGroup(modes);
            item.setSelected(mode == sortMode);
            item.setOnAction(event -> {
                sortMode = mode;
                PREFERENCES.put(SORT_MODE, mode.name());
                rebuildRows();
            });
            button.getItems().add(item);
        }
        return button;
    }

    /// The toolbar "Group by" menu (icon-only, `FORMAT_LIST_GROUP`): one check
    /// item per [Grouping]; ticking several nests them in menu order. The last
    /// ticked one cannot be unticked — the list always groups by something.
    /// Persisted like the sort mode (a reading choice, not a transient filter).
    // [impl->dsn~task-label-grouping~3]
    // [impl->dsn~pr-status-grouping~1]
    // [impl->dsn~nested-grouping~1]
    // [impl->dsn~task-list-toolbar~3]
    private MenuButton groupingButton() {
        MenuButton button = iconMenuButton(MDIInterface.FORMAT_LIST_GROUP, "Group the task list by");
        for (Grouping mode : Grouping.values()) {
            CheckMenuItem item = new CheckMenuItem(mode.label);
            item.setSelected(groupings.contains(mode));
            item.setOnAction(event -> {
                if (item.isSelected()) {
                    groupings.add(mode);
                } else if (groupings.size() > 1) {
                    groupings.remove(mode);
                } else {
                    item.setSelected(true);
                    return;
                }
                PREFERENCES.put(GROUPING, groupings.stream().map(Enum::name).collect(Collectors.joining(",")));
                rebuildRows();
            });
            button.getItems().add(item);
        }
        return button;
    }

    /// The toolbar energy-saver toggle (icon-only, `LEAF`): while on, every
    /// background poller skips its periodic tick, so a laptop on battery pays
    /// only for the mirrored terminal of the selected task. Persisted like the
    /// grouping toggle — a reading choice, not a transient filter.
    // [impl->dsn~energy-saver~1]
    // [impl->dsn~task-list-toolbar~3]
    private ToggleButton energySaverButton() {
        ToggleButton button = iconToggle(MDIWorld.LEAF,
                "Energy saver — stop all background polling; the selected task's terminal keeps"
                        + " streaming and its state refreshes when you select it or press refresh.");
        button.selectedProperty().addListener((obs, was, selected) -> {
            EnergySaver.setActive(selected);
            PREFERENCES.putBoolean(ENERGY_SAVER, selected);
        });
        // Restored after the listener, so the stored state runs the very code
        // path a user tick runs — the gate is set, not just the button.
        button.setSelected(PREFERENCES.getBoolean(ENERGY_SAVER, false));
        return button;
    }

    /// The filter menu's "Show active desktop only" check item: while ticked,
    /// [#rebuildRows] narrows the list to categories whose `CONTEXTSWITCHER.md`
    /// `desktop:` matches the active Windows virtual desktop. Turning it on tells
    /// `Main` to start watching the active desktop (and read it once immediately);
    /// off stops the watch. The item text carries the active desktop's name once
    /// known. Restored from the last run ([#activeDesktopOnly]).
    // [impl->dsn~active-desktop-filter~6]
    private CheckMenuItem activeDesktopItem() {
        CheckMenuItem item = new CheckMenuItem("Show active desktop only");
        this.activeDesktopButton = item;
        item.selectedProperty().addListener((obs, was, selected) -> {
            activeDesktopOnly = selected;
            PREFERENCES.putBoolean(FILTER_ACTIVE_DESKTOP, selected);
            rebuildRows();
            // Drop/restore the desktop-name suffix: off means the poller stops,
            // so a lingering name would go stale — hide it until it is live again.
            updateActiveDesktopButton();
            updateFilterButton();
            // Start/stop the Windows poll in Main; on start it reads the current
            // desktop once so the filter applies at once (not after the next tick).
            onActiveDesktopFilter.accept(selected);
        });
        // Restored after the listener, so the stored state runs the very code
        // path a user tick runs — including the start of the desktop watch.
        // [impl->dsn~filter-persistence~1]
        item.setSelected(activeDesktopOnly);
        updateActiveDesktopButton();
        return item;
    }

    /// The filter menu's "Also show categories without a desktop" check item,
    /// directly under the desktop filter it modifies: while ticked, categories
    /// whose `CONTEXTSWITCHER.md` names no `desktop:` (and the ungrouped tasks)
    /// stay visible under that filter instead of dropping out. Disabled while
    /// the desktop filter is off — on its own it would change nothing, and a
    /// tickable item that does nothing invites exactly the wrong conclusion.
    /// Restored from the last run, like the filters themselves.
    // [impl->dsn~active-desktop-filter~6]
    private CheckMenuItem noDesktopCategoriesItem(CheckMenuItem desktopItem) {
        CheckMenuItem item = new CheckMenuItem("Also show categories without a desktop");
        item.disableProperty().bind(desktopItem.selectedProperty().not());
        item.selectedProperty().addListener((obs, was, selected) -> {
            noDesktopCategoriesToo = selected;
            PREFERENCES.putBoolean(FILTER_NO_DESKTOP_CATEGORIES, selected);
            rebuildRows();
        });
        // [impl->dsn~filter-persistence~1]
        item.setSelected(noDesktopCategoriesToo);
        return item;
    }

    /// The filter menu's "Show running tasks only" check item: while ticked,
    /// [#rebuildRows] narrows the list to active tasks — suspended and done ones
    /// are hidden. Restored from the last run ([#runningTasksOnly]).
    // [impl->dsn~running-tasks-filter~1]
    private CheckMenuItem runningTasksItem() {
        CheckMenuItem item = new CheckMenuItem("Show running tasks only");
        item.selectedProperty().addListener((obs, was, selected) -> {
            runningTasksOnly = selected;
            PREFERENCES.putBoolean(FILTER_RUNNING_TASKS, selected);
            updateFilterButton();
            rebuildRows();
        });
        // [impl->dsn~filter-persistence~1]
        item.setSelected(runningTasksOnly);
        return item;
    }

    /// Refreshes the desktop check item's text with the active desktop's name
    /// once known, so it shows which desktop it is filtering to. While the
    /// filter is on but the desktop cannot be determined it says "(unknown)":
    /// the filter narrows nothing in that state, and a bare checked item would
    /// silently promise a narrowing that is not happening. The suffix shows
    /// only while the filter is on: off, the poller stops pushing updates, so
    /// a shown name would be stale.
    // [impl->dsn~active-desktop-filter~6]
    private void updateActiveDesktopButton() {
        CheckMenuItem button = activeDesktopButton;
        if (button == null) {
            return;
        }
        button.setText(activeDesktopOnly
                ? "Show active desktop only (" + (activeDesktop != null ? activeDesktop : "unknown") + ")"
                : "Show active desktop only");
    }

    /// Records the active virtual desktop's name (pushed by `Main`'s poller) and,
    /// when the filter is on and the desktop actually changed, re-narrows the
    /// list so it follows the desktop the user switched to and re-selects that
    /// desktop's last task. With the filter off the list does not follow the
    /// desktop, so neither does the selection.
    // [impl->dsn~active-desktop-filter~6]
    public void updateActiveDesktop(@Nullable String desktop) {
        if (Objects.equals(desktop, activeDesktop)) {
            return;
        }
        activeDesktop = desktop;
        updateActiveDesktopButton();
        if (!activeDesktopOnly) {
            return;
        }
        // Read before the rebuild: the rebuild's own selection changes run
        // through the list's listener, which would record them under the
        // desktop just switched to — overwriting what it remembered.
        // [impl->dsn~desktop-last-task-selection~4]
        String last = desktop == null ? null : LAST_TASK_BY_DESKTOP.get(desktop, null);
        rebuildRows();
        // Re-select the task last selected on the now-active desktop, so the
        // panes follow the desktop switch. Only a row the narrowed list
        // actually shows is handed to `selectTask` (a remembered task that was
        // deleted, or one another filter hides, would sit in
        // pendingSelectionId forever and block later rebuilds' selection
        // preservation) — a desktop with nothing remembered, or whose
        // remembered task is gone, falls back to its first task, since the
        // previous desktop's task is exactly the foreign view to avoid.
        // [impl->dsn~desktop-last-task-selection~4]
        TaskEntry.@Nullable Loaded target = visibleRows.stream()
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(TaskEntry.Loaded.class::cast)
                .filter(loaded -> loaded.id().equals(last))
                .findFirst()
                .or(() -> visibleRows.stream()
                        .filter(TaskEntry.Loaded.class::isInstance)
                        .map(TaskEntry.Loaded.class::cast)
                        .findFirst())
                .orElse(null);
        // Never out from under the keyboard. A selection change swaps the
        // queue pane to the new task, and `QueuePane.showTask` commits the
        // half-typed message as the *previous* task's draft before clearing
        // the box — so a desktop switch arriving mid-sentence reads as "my
        // letters were lost" and moves the focus to the list (field report
        // 2026-09-12). The list still follows the desktop; only the selection
        // waits, and the next switch (or a click) applies it.
        // `selectCreatedTask` has kept this guard for the same reason since
        // `dsn~task-from-pr~6`; the desktop switch is the other caller that
        // needed it.
        // [impl->dsn~desktop-last-task-selection~4]
        if (target != null && !queueFieldFocused()) {
            selectTask(target.id());
        }
        // The title follows the desktop even when the selection waits or
        // there is nothing to select: the target's category, else the
        // desktop's own name — never the previous desktop's category.
        // [impl->dsn~window-title-category~3]
        if (target != null) {
            showCategoryInTitle(target.group());
        } else if (desktop != null) {
            showCategoryInTitle(desktop);
        }
    }

    /// Stores the task the user selected as the active desktop's last one.
    /// A desktop that is not known (non-Windows, unnamed, not yet read) has no
    /// key to store under, and an over-long name would blow the preferences
    /// key limit — both simply record nothing.
    // [impl->dsn~desktop-last-task-selection~4]
    private void rememberLastTask(@Nullable String desktop, String taskId) {
        if (desktop != null && desktop.length() <= Preferences.MAX_KEY_LENGTH
                && taskId.length() <= Preferences.MAX_VALUE_LENGTH) {
            LAST_TASK_BY_DESKTOP.put(desktop, taskId);
        }
    }

    /// The filter menu's "Awaits input only" check item: while ticked, [#rebuildRows]
    /// narrows the list to the rows the user must act on next — active tasks
    /// whose live `@cs_status` is `waiting` or `attention`. Its text carries the
    /// current count, so it doubles as an at-a-glance overview even without
    /// ticking it. Restored from the last run ([#awaitsInputOnly]).
    // [impl->dsn~awaits-input-filter~1]
    private CheckMenuItem awaitsInputItem() {
        CheckMenuItem item = new CheckMenuItem("Awaits input only");
        this.awaitsInputButton = item;
        item.selectedProperty().addListener((obs, was, selected) -> {
            awaitsInputOnly = selected;
            PREFERENCES.putBoolean(FILTER_AWAITS_INPUT, selected);
            updateFilterButton();
            rebuildRows();
        });
        // [impl->dsn~filter-persistence~1]
        item.setSelected(awaitsInputOnly);
        updateAwaitsInputButton();
        return item;
    }

    /// Refreshes the "Awaits input only" check item's count badge from the live
    /// statuses. Called from [#rebuildRows] and whenever the poller delivers
    /// new statuses.
    // [impl->dsn~awaits-input-filter~1]
    private void updateAwaitsInputButton() {
        CheckMenuItem button = awaitsInputButton;
        if (button == null) {
            return;
        }
        long count = entries.stream().filter(this::awaitsInput).count();
        button.setText(count == 0 ? "Awaits input only" : "Awaits input only (" + count + ")");
    }

    /// Whether an entry is an active task currently awaiting the user: its live
    /// `@cs_status` is `waiting` (end of turn), `attention` (Claude asked a
    /// question / needs a permission) or `limit` (the session hit its usage
    /// limit, `dsn~claude-limit-detection~2`). `working`, done, and suspended
    /// rows never qualify — they are not what the user must act on next.
    // [impl->dsn~awaits-input-filter~1]
    // [impl->dsn~claude-limit-detection~2]
    private boolean awaitsInput(TaskEntry entry) {
        if (!(entry instanceof TaskEntry.Loaded loaded)
                || loaded.task().status() != TaskStatus.ACTIVE) {
            return false;
        }
        String status = runningStatusFor(loaded.task());
        return "waiting".equals(status) || "attention".equals(status) || "limit".equals(status);
    }

    /// The tag-filter control: a "Tags" `Menu` (submenu of the filter menu) of
    /// `CheckMenuItem`s (one per selectable tag — configured or already in use,
    /// rendered as its colored chip) plus a "Clear filter" item. Toggling a tag
    /// updates [#activeTags] (AND-combined) and rebuilds the rows; the filter
    /// button's badge counts a non-empty tag selection. Disabled when no tag
    /// exists anywhere.
    // [impl->dsn~task-tag-filter~2]
    // [impl->dsn~tag-selection-union~2]
    private Menu tagFilterMenu() {
        SvgNode tagGlyph = new SvgNode(MDIInterface.TAG_MULTIPLE.path(), 14);
        Menu menu = new Menu(null, tagGlyph);
        menu.setText("Tags");
        this.tagFilterMenu = menu;
        populateTagFilterMenu();
        return menu;
    }

    /// The toolbar "add" menu (plus icon): create a task in the category in view
    /// (`selectedGroup`) or a new category. Given an id so a UI test can target
    /// it among the toolbar's several menu buttons.
    // [impl->dsn~task-create-ui~15]
    // [impl->dsn~category-create-ui~2]
    // [impl->dsn~task-list-toolbar~3]
    private MenuButton addButton() {
        MenuButton button = iconMenuButton(MDIInterface.PLUS, "Add…");
        button.setId("add-menu");
        // Target the category the user is looking at (the selection's group
        // while on screen, else the topmost visible one), not always the root —
        // a task is usually added to the context in view. (dsn~task-create-ui~15)
        MenuItem addTask = new MenuItem("Add task…");
        addTask.setOnAction(event -> addTask(selectedGroup()));
        MenuItem addCategory = new MenuItem("Add category…");
        addCategory.setOnAction(event -> addCategory());
        MenuItem addCategoryFromUrl = new MenuItem("Add category from URL…");
        addCategoryFromUrl.setOnAction(event -> addCategoryFromUrl());
        button.getItems().addAll(addTask, addCategory, addCategoryFromUrl,
                new SeparatorMenuItem(), setupWizardItem());
        return button;
    }

    /// The wizard's re-entry, next to the other creation actions in the add
    /// menu and the list's context menu: a second remote, the tools on a new
    /// host, another first project — the same pages as on the first start.
    // [impl->dsn~setup-wizard~8]
    private MenuItem setupWizardItem() {
        MenuItem item = new MenuItem("Setup wizard…");
        item.setId("setup-wizard");
        item.setOnAction(event -> onSetupWizard.run());
        return item;
    }

    /// (Re)builds the tag-filter menu items from the selectable tags (configured
    /// palette plus tags already in use). Called at startup, after a settings
    /// edit, and on task changes, so the menu reflects both settings.yaml and
    /// the task files without a restart. Selections for tags that no longer
    /// exist anywhere are dropped. Disabled with a hint when no tag exists.
    // [impl->dsn~task-tag-filter~2]
    // [impl->dsn~tag-selection-union~2]
    private void populateTagFilterMenu() {
        Menu menu = tagFilterMenu;
        if (menu == null) {
            return;
        }
        List<String> names = selectableTagNames();
        // Re-apply the stored selection instead of only dropping what is gone:
        // the selectable names grow as the palette and the task files load, so
        // a restored tag is simply not selectable yet at the first build — and
        // every toggle below writes the store, so it is the newer truth.
        // [impl->dsn~filter-persistence~1]
        // [impl->dsn~tag-selection-union~2]
        List<String> stored = storedFilterTags();
        activeTags.clear();
        names.stream().filter(name -> stored.stream().anyMatch(name::equalsIgnoreCase))
                .forEach(activeTags::add);
        menu.getItems().clear();
        if (names.isEmpty()) {
            menu.setDisable(true);
            updateTagFilterButton();
            return;
        }
        menu.setDisable(false);
        // The recently selected tags lead, separated from the rest, so a
        // long palette does not bury the few tags actually filtered by.
        // [impl->dsn~tag-filter-recent~1]
        List<String> recent = storedRecentFilterTags().stream()
                .flatMap(tag -> names.stream().filter(tag::equalsIgnoreCase).limit(1))
                .toList();
        List<String> ordered = new ArrayList<>(recent);
        names.stream().filter(name -> recent.stream().noneMatch(name::equalsIgnoreCase))
                .forEach(ordered::add);
        for (String name : ordered) {
            // The item shows the tag as its colored chip (the same rendering
            // as the row chips), not as plain text.
            CheckMenuItem item = new CheckMenuItem(null,
                    TagChips.chip(name, tagColors.get(name.toLowerCase(Locale.ROOT))));
            item.setSelected(activeTags.stream().anyMatch(name::equalsIgnoreCase));
            item.setOnAction(event -> {
                if (item.isSelected()) {
                    activeTags.add(name);
                    PREFERENCES.put(FILTER_RECENT_TAGS,
                            String.join("\n", TaskTags.withRecent(storedRecentFilterTags(), name)));
                } else {
                    activeTags.removeIf(name::equalsIgnoreCase);
                }
                storeFilterTags();
                updateTagFilterButton();
                rebuildRows();
            });
            menu.getItems().add(item);
            if (!recent.isEmpty() && name.equals(recent.getLast()) &&ordered.size() > recent.size()) {
                menu.getItems().add(new SeparatorMenuItem());
            }
        }
        MenuItem clear = new MenuItem("Clear filter");
        clear.setOnAction(event -> {
            activeTags.clear();
            for (MenuItem item : menu.getItems()) {
                if (item instanceof CheckMenuItem check) {
                    check.setSelected(false);
                }
            }
            storeFilterTags();
            updateTagFilterButton();
            rebuildRows();
        });
        menu.getItems().addAll(new SeparatorMenuItem(), clear);
        updateTagFilterButton();
    }

    /// The tag selection the last run left behind, newline-joined in the
    /// preferences (a tag name may carry spaces, never a newline).
    // [impl->dsn~filter-persistence~1]
    private static List<String> storedFilterTags() {
        String stored = PREFERENCES.get(FILTER_TAGS, "");
        return stored.isEmpty() ? List.of() : List.of(stored.split("\n"));
    }

    /// The tags most recently ticked in the tag filter, newest first.
    // [impl->dsn~tag-filter-recent~1]
    private static List<String> storedRecentFilterTags() {
        String stored = PREFERENCES.get(FILTER_RECENT_TAGS, "");
        return stored.isEmpty() ? List.of() : List.of(stored.split("\n"));
    }

    /// Stores the current tag selection for the next run.
    // [impl->dsn~filter-persistence~1]
    private void storeFilterTags() {
        PREFERENCES.put(FILTER_TAGS, String.join("\n", activeTags));
    }

    /// Re-reads the tag palette from settings.yaml and rebuilds the filter menu,
    /// chip colors, and rows — so editing the palette in the settings popup
    /// takes effect without a restart. Selections for tags that no longer exist
    /// anywhere are dropped by [#populateTagFilterMenu].
    // [impl->dsn~task-tag-filter~2]
    private void refreshTagPalette() {
        palette = configuredTags.get();
        rebuildTagColors();
        populateTagFilterMenu();
        rebuildRows();
        if (taskList != null) {
            taskList.refresh();
        }
    }

    /// Rebuilds the tag-name → color lookup from the current palette (a
    /// color-less tag is left out, so it falls back to the muted gray chip).
    private void rebuildTagColors() {
        tagColors.clear();
        for (AppSettings.TagDef tag : palette) {
            if (tag.color() != null) {
                tagColors.put(tag.name().toLowerCase(Locale.ROOT), tag.color());
            }
        }
    }

    /// The tag selection feeds the filter button's badge (a non-empty selection
    /// counts as one active filter dimension).
    // [impl->dsn~task-tag-filter~2]
    private void updateTagFilterButton() {
        updateFilterButton();
    }

    /// The tags offered for selection — in the toolbar filter menu and the
    /// row/group "Tags" submenus: the union of the configured palette and the
    /// tags already carried by tasks or their groups ([TaskTags#selectable]),
    /// so a tag only present in a task file is selectable without configuring it.
    // [impl->dsn~tag-selection-union~2]
    private List<String> selectableTagNames() {
        if (selectableTagsCache != null) {
            return selectableTagsCache;
        }
        List<String> inUse = new ArrayList<>();
        for (TaskEntry entry : entries) {
            if (entry instanceof TaskEntry.Loaded loaded) {
                inUse.addAll(effectiveTags(loaded.task()));
            }
        }
        selectableTagsCache = TaskTags.selectable(
                palette.stream().map(AppSettings.TagDef::name).toList(), inUse);
        return selectableTagsCache;
    }

    /// Toggles a tag on a task (adds if absent, removes if present), written
    /// through the fresh-from-disk rewrite path so a just-edited file is not
    /// clobbered. The watcher refreshes the row (and its chips).
    // [impl->dsn~task-tag-filter~2]
    private void toggleTag(Task staleTask, String tag) {
        Task task = freshTask(staleTask);
        List<String> tags = new ArrayList<>(task.tags());
        boolean removed = tags.removeIf(existing -> existing.equalsIgnoreCase(tag));
        if (!removed) {
            tags.add(tag);
        }
        rewriteFrontmatter(task, content -> TaskFileParser.withTags(content, tags));
    }

    /// Opens the newest `~/.contextswitcher/logs/*.log` in whatever the
    /// desktop uses for it. The log directory is fixed (tinylog writes it
    /// under `user.home`, not under the config dir). Anything that goes wrong
    /// — no log yet, no registered application — puts the path in the status
    /// bar, so the user can still find the file by hand.
    // [impl->dsn~open-log-button~1]
    private void openLog() {
        Path logs = Path.of(System.getProperty("user.home"), ".contextswitcher", "logs");
        Optional<Path> newest;
        try (var files = Files.list(logs)) {
            newest = files.filter(file -> file.getFileName().toString().endsWith(".log"))
                    .max(Comparator.comparingLong(file -> file.toFile().lastModified()));
        } catch (IOException e) {
            statusBar.message("No log directory yet: " + logs);
            return;
        }
        if (newest.isEmpty()) {
            statusBar.message("No log file yet in " + logs);
            return;
        }
        Path file = newest.get();
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            statusBar.message("The log is at " + file);
            return;
        }
        try {
            Desktop.getDesktop().open(file.toFile());
            statusBar.message("Opened " + file);
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            Logger.warn("Cannot open {}: {}", file, e.getMessage());
            statusBar.message("Cannot open the log, it is at " + file);
        }
    }

    /// A flat Material-Design icon button for the toolbar (SvgNode, MADR 0010),
    /// small-sized and icon-only per the app's control conventions. The fill
    /// is `.svg-node` in main.css, the same muted foreground as the row icons.
    static Button iconButton(SVG icon, String tooltip) {
        SvgNode svg = new SvgNode(icon.path(), 16);
        Button button = new Button(null, svg);
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    /// A menu item with a tooltip: a plain `MenuItem` cannot carry one, so its
    /// text is a `Label` inside a `CustomMenuItem`, stretched over the row.
    // [impl->dsn~task-list-toolbar~3]
    private static MenuItem tooltipMenuItem(String text, String tooltip) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        Tooltip.install(label, new Tooltip(tooltip));
        return new CustomMenuItem(label);
    }

    /// A flat icon-only `MenuButton` for the toolbar — the [#iconButton] shape
    /// for a drop-down control (filter, sort, tags, add). The `.small`
    /// menu-button sizing AtlantaFX lacks is patched in `main.css`.
    // [impl->dsn~task-list-toolbar~3]
    private static MenuButton iconMenuButton(SVG icon, String tooltip) {
        SvgNode svg = new SvgNode(icon.path(), 16);
        MenuButton button = new MenuButton(null, svg);
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    /// A flat icon-only `ToggleButton` for the toolbar — the [#iconButton] shape
    /// for a sticky on/off control (group-by-labels).
    // [impl->dsn~task-list-toolbar~3]
    private static ToggleButton iconToggle(SVG icon, String tooltip) {
        SvgNode svg = new SvgNode(icon.path(), 16);
        ToggleButton button = new ToggleButton(null, svg);
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    /// Selects the task row now and re-selects it after the next rebuild —
    /// for rows that may only appear once the watcher delivers them. The row
    /// is also scrolled into view (it may sit outside the viewport, e.g. a
    /// task just moved to a far-away category).
    // [impl->dsn~task-move-dnd~6]
    /// Selects the row of the task the panes currently show ([#previewedTask]).
    /// A no-op when no task is previewed (a category config or corrupt file in
    /// the editor), and — since the list only fires on a *changed* selection —
    /// when that row is already selected, so a click in the terminal never
    /// re-attaches the mirror. Focus is deliberately left in the clicked pane.
    // [impl->dsn~pane-click-selects-task~1]
    private void selectPreviewedTask() {
        Task previewed = previewedTask;
        if (previewed != null) {
            selectTask(previewed.id());
        }
    }

    /// The id of the task the list is currently on, or `null` when the
    /// selection is a header, a corrupt file, or nothing. Survives the
    /// transient clear a row rebuild causes, so it answers "is this task
    /// already the one in view" even while a collapsed category hides its row.
    // [impl->dsn~browser-tab-selects-task~5]
    public @Nullable String selectedTaskId() {
        Task previewed = previewedTask;
        return previewed == null ? null : previewed.id();
    }

    /// Selects a task an **asynchronous** creation just finished (the PR
    /// flow) — unless the user is meanwhile typing into a queue box. The
    /// selection change swaps the queue pane over to the new task, which
    /// commits and clears the half-typed message; a creation landing
    /// mid-sentence would pull the text out from under the keyboard.
    // [impl->dsn~task-from-pr~6]
    public void selectCreatedTask(String taskId) {
        if (!queueFieldFocused()) {
            selectTask(taskId);
        }
    }

    /// Whether the keyboard focus sits in one of the queue pane's text boxes.
    // [impl->dsn~task-from-pr~6]
    private boolean queueFieldFocused() {
        Scene scene = queueNode.getScene();
        Node focused = scene == null ? null : scene.getFocusOwner();
        if (!(focused instanceof TextInputControl)) {
            return false;
        }
        for (Node node = focused; node != null; node = node.getParent()) {
            if (node == queueNode) {
                return true;
            }
        }
        return false;
    }

    /// Appends `taskId` to the navigation trail, the way a browser records a
    /// visit: re-selecting the task already at the cursor changes nothing (a
    /// row rebuild re-selects the same task), and any other task truncates the
    /// forward branch. Called from the selection listener, so a step taken by
    /// [#navigateHistory] lands on the cursor it just moved and is ignored.
    // [impl->dsn~task-history-navigation~2]
    private void recordHistory(String taskId) {
        if (historyIndex >= 0 && history.get(historyIndex).equals(taskId)) {
            return;
        }
        // A task that was on screen for less than a glance was no visit: a
        // desktop switch re-selects the desktop's last task and the browser
        // window it activates reports its tab a moment later, so Back landed
        // on the task that only flashed instead of the one worked on before
        // (field report 2026-09-13). Such an entry is the newest one — only
        // an append stamps the time, and a history step clears it — so the
        // new task takes its place.
        if (historyIndex >= 0 && System.nanoTime() - lastRecordedNanos < TRANSIENT_VISIT_NANOS) {
            Logger.debug("History: {} replaces the transient visit {}", taskId, history.get(historyIndex));
            history.remove(historyIndex--);
            if (historyIndex >= 0 && history.get(historyIndex).equals(taskId)) {
                lastRecordedNanos = System.nanoTime() - TRANSIENT_VISIT_NANOS;
                updateHistoryButtons();
                return;
            }
        }
        lastRecordedNanos = System.nanoTime();
        history.subList(historyIndex + 1, history.size()).clear();
        history.add(taskId);
        // ponytail: a fixed cap, no persistence across restarts — add when
        // "back to a task from yesterday" is actually asked for.
        if (history.size() > HISTORY_LIMIT) {
            history.removeFirst();
        }
        historyIndex = history.size() - 1;
        updateHistoryButtons();
    }

    /// Moves one step back (`-1`) or forward (`+1`) along the trail and selects
    /// the task found there. Entries whose task no longer exists (deleted or
    /// renamed since the visit) are dropped first, so Back never lands on
    /// nothing.
    // [impl->dsn~task-history-navigation~2]
    private void navigateHistory(int step) {
        for (int i = history.size() - 1; i >= 0; i--) {
            String id = history.get(i);
            if (entries.stream().noneMatch(entry -> entry instanceof TaskEntry.Loaded loaded
                    && loaded.id().equals(id))) {
                history.remove(i);
                if (i <= historyIndex) {
                    historyIndex--;
                }
            }
        }
        int target = historyIndex + step;
        if (target < 0 || target >= history.size()) {
            updateHistoryButtons();
            return;
        }
        historyIndex = target;
        lastRecordedNanos = System.nanoTime() - TRANSIENT_VISIT_NANOS;
        updateHistoryButtons();
        selectTask(history.get(target));
    }

    /// Greys out Back at the oldest visit and Forward at the newest — the
    /// trail's ends, which is the only feedback the two icons can give.
    // [impl->dsn~task-history-navigation~2]
    private void updateHistoryButtons() {
        if (backButton != null) {
            backButton.setDisable(historyIndex <= 0);
        }
        if (forwardButton != null) {
            forwardButton.setDisable(historyIndex < 0 || historyIndex >= history.size() - 1);
        }
    }

    public void selectTask(String taskId) {
        // Who moved the selection: the browser-tab reporter, a drag, a pane
        // click and the desktop switch all land here, and a selection that
        // jumps back on its own is otherwise untraceable.
        Logger.debug("selectTask {} from {}", taskId,
                StackWalker.getInstance().walk(frames -> frames.skip(1).limit(4)
                        .map(StackWalker.StackFrame::getMethodName).toList()));
        pendingSelectionId = taskId;
        scrollToPendingSelection = true;
        ListView<Object> list = taskList;
        if (list == null) {
            return;
        }
        int row = rowOf(taskId);
        if (row >= 0) {
            pendingSelectionId = null;
            scrollToPendingSelection = false;
            list.getSelectionModel().select(row);
            scrollRowIntoView(list, row);
            return;
        }
        // The row is not on screen: a collapsed category hides it, or a find
        // query it does not match. Both are undone — a selection nobody can
        // see is no selection, and the browser-tab reporter has no other way
        // to say "this task". `rebuildRows` applies the pending selection
        // itself, so each step only has to reveal.
        // [impl->dsn~browser-tab-selects-task~5]
        if (collapsedGroups.remove(groupOf(taskId))) {
            rebuildRows();
        }
        if (pendingSelectionId != null && !searchQuery.strip().isEmpty()) {
            closeFind();
        }
    }

    /// The import dialog's pre-fill: the destination of the last import
    /// (kept across app restarts in Java Preferences — UI state, not
    /// configuration), falling back to the first task's remote.
    // [impl->dsn~tmux-task-import~12]
    private String defaultHost() {
        String last = PREFERENCES.get(LAST_IMPORT_HOST, "");
        if (!last.isEmpty()) {
            return last;
        }
        return entries.stream()
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(entry -> ((TaskEntry.Loaded) entry).task().remote())
                .filter(remote -> remote != null)
                .findFirst()
                .orElse("");
    }

    private VBox emptyStatePlaceholder() {
        Hyperlink openDir = new Hyperlink(tasksDir.toString());
        openDir.setOnAction(event -> onOpenTasksDir.run());

        // The wizard from the empty state too: a newcomer who cancelled it
        // is looking right here. [impl->dsn~setup-wizard~8]
        Hyperlink wizard = new Hyperlink("Run the setup wizard…");
        wizard.setOnAction(event -> onSetupWizard.run());
        VBox placeholder = new VBox(4,
                new Label("No tasks yet — run the setup wizard, or copy TEMPLATE.md in the tasks "
                        + "directory to create one:"), wizard, openDir);
        placeholder.setAlignment(Pos.CENTER);
        return placeholder;
    }

    /// Placeholder while the active filters hide every row. A search whose hits
    /// all sit on other desktops (`offDesktop` > 0) says so; with the desktop
    /// filter as the sole narrowing, it names the desktop and offers creating a
    /// category for it (its config's `desktop:` pre-filled with the active
    /// desktop); any other combination gets a plain "nothing matches" line.
    // [impl->dsn~desktop-filter-empty-state~2]
    private VBox filteredEmptyPlaceholder(@Nullable String desktop, int offDesktop) {
        VBox placeholder;
        if (offDesktop > 0) {
            // The search matched, just not here — say where, instead of the
            // bare "nothing matches" that reads as "the task is gone".
            // [impl->dsn~search-hits-off-desktop~1]
            placeholder = new VBox(new Label(
                    "No matches on this desktop — %d on other desktops.".formatted(offDesktop)));
        } else if (desktop == null) {
            placeholder = new VBox(new Label("No tasks match the current filters."));
        } else {
            Hyperlink addCategory =
                    new Hyperlink("Add category for desktop \"%s\"…".formatted(desktop));
            addCategory.setOnAction(event -> addCategory(desktop));
            placeholder = new VBox(4,
                    new Label("No categories on desktop \"%s\".".formatted(desktop)), addCategory);
        }
        placeholder.setAlignment(Pos.CENTER);
        return placeholder;
    }
}
