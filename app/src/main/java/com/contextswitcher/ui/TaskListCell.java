package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import atlantafx.base.theme.Styles;
import com.contextswitcher.analysis.RefactoringSummary;
import com.contextswitcher.extension.DeepLink;
import com.contextswitcher.switching.AutoPrLookup;
import com.contextswitcher.switching.PrInfo;
import com.contextswitcher.switching.PrState;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskEntry;
import com.contextswitcher.tasks.TaskStatus;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;
import tools.maran.svg.SVG;
import tools.maran.svg.materialdesign.MDIInterface;
import tools.maran.svg.materialdesign.MDITechnology;
import tools.maran.svgnode.SvgNode;

/// Renders a task row (title, status, hover action icons), a red error row for an
/// unparseable task file, or a collapsible folder group header. A flat
/// `ListView` (not a `TreeView`) hosts these so every row — ungrouped task,
/// group header, grouped task — shares the same left and right edge; grouping
/// is expressed by the [GroupHeader] separator rows the list builds, not by
/// tree depth.
// [impl->dsn~main-window~2]
// [impl->dsn~task-folder-grouping-ui~8]
public class TaskListCell extends ListCell<Object> {

    /// One collapsible section header in the flat task list. `tags` are the
    /// group's own tags (from its `CONTEXTSWITCHER.md`), shown as chips and
    /// inherited by every task in the group; `remote` is the group's own
    /// configured ssh destination (null for a local group).
    // [impl->dsn~task-tag-filter~2]
    /// `warning` describes a problem in the group's `CONTEXTSWITCHER.md` that
    /// does not stop it from loading — today its duplicate frontmatter keys
    /// (`dsn~frontmatter-duplicate-keys~1`); null when there is none.
    /// `path` is the directory the category's task workspaces live under (its
    /// `workspacesRoot`, else its `workdir`), shown once on the header's second
    /// line; `running` counts the category's active tasks on screen.
    // [impl->dsn~category-header-path~1]
    // [impl->dsn~running-task-accent~1]
    public record GroupHeader(String name, boolean expanded, List<String> tags,
            @Nullable String remote, @Nullable String warning, List<String> folders,
            @Nullable String path, int running) {

        /// Defensive copy so the header's folder list stays immutable.
        // [impl->dsn~category-folders-button~1]
        public GroupHeader {
            folders = List.copyOf(folders);
        }

        /// Back-compat constructor for a header without path or running count.
        public GroupHeader(String name, boolean expanded, List<String> tags,
                @Nullable String remote, @Nullable String warning, List<String> folders) {
            this(name, expanded, tags, remote, warning, folders, null, 0);
        }

        /// Back-compat constructor for a category without local folders.
        public GroupHeader(String name, boolean expanded, List<String> tags,
                @Nullable String remote, @Nullable String warning) {
            this(name, expanded, tags, remote, warning, List.of());
        }

        /// Back-compat constructor for a category with nothing to warn about.
        public GroupHeader(String name, boolean expanded, List<String> tags,
                @Nullable String remote) {
            this(name, expanded, tags, remote, null);
        }
    }

    /// The slim "Add task…" row closing an expanded category's tasks (an auto
    /// category's synced-from placeholder instead).
    // [impl->dsn~task-create-ui~15]
    public record AddTaskRow(String group) {
    }

    /// A task whose remote half is still being set up: the file is already on
    /// disk (so the row is real, not a placeholder), but the tmux window and
    /// Claude session are not there yet. `step` names the round-trip currently
    /// running, `startedNanos` when the creation began.
    ///
    /// Both are **properties**, and the row binds its bar and label to them:
    /// the creation ticks twice a second, and repainting it by rebuilding the
    /// cells (`ListView.refresh`) makes the whole tree flicker for as long as a
    /// task is being created. A bound bar repaints itself alone.
    // [impl->dsn~task-create-progress~5]
    public static final class Creation {

        /// Worst case wall time of a remote creation: two ssh round-trips plus
        /// the launcher's server-side `sleep 5` and its 3 s paste verification.
        /// The bar is time-driven — the steps are too coarse and too unevenly
        /// long to divide it into equal parts — and stops just short of full so
        /// a slow remote never shows a finished bar on an unfinished task.
        private static final double EXPECTED_SECONDS = 20;

        private final long startedNanos;
        private final StringProperty step = new SimpleStringProperty();
        private final DoubleProperty progress = new SimpleDoubleProperty();

        public Creation(String step, long startedNanos) {
            this.startedNanos = startedNanos;
            this.step.set(step);
            this.progress.set(progressAt(startedNanos));
        }

        /// How full the bar of a creation started at `startedNanos` is now.
        public static double progressAt(long startedNanos) {
            return Math.min((System.nanoTime() - startedNanos) / 1e9 / EXPECTED_SECONDS, 0.99);
        }

        /// Advances the bar to the elapsed time; the ticker's per-frame call.
        public void tick() {
            progress.set(progressAt(startedNanos));
        }

        public void setStep(String running) {
            step.set(running);
        }

        public StringProperty stepProperty() {
            return step;
        }

        public DoubleProperty progressProperty() {
            return progress;
        }
    }

    /// One collapsible label-group header while the list groups by labels
    /// instead of folders; `count` is how many tasks carry the tag. `key` is
    /// what collapses it (the name at the top level, the path below) and
    /// `depth` how far a nested sub-header is indented.
    // [impl->dsn~task-label-grouping~3]
    // [impl->dsn~nested-grouping~1]
    public record LabelHeader(String name, String key, boolean expanded, int count, int depth) {

        /// A top-level header.
        public LabelHeader(String name, boolean expanded, int count) {
            this(name, name, expanded, count, 0);
        }
    }

    /// The header of the bottom "Done" section that gathers every completed
    /// task; `count` is how many it holds, `expanded` whether its rows show.
    // [impl->dsn~done-section~1]
    public record DoneHeader(int count, boolean expanded) {
    }

    /// A little right padding keeps the trailing icons off the list's vertical
    /// scrollbar; the scrollbar's own width is taken off in [#rowWidth].
    private static final Insets ROW_PADDING = new Insets(2, 4, 2, 6);

    /// A row's smaller secondary text (main.css).
    private static final String ROW_DETAIL = "row-detail";

    /// Set on a row while a dragged task hovers it (main.css).
    private static final PseudoClass DROP_TARGET = PseudoClass.getPseudoClass("drop-target");

    /// Cell style classes carrying the section backgrounds. They are set on the
    /// `ListCell` itself, not the inner row — the row's width is bound narrower
    /// than the cell (reserving room for the scrollbar, [#rowWidth]), so
    /// a background on it leaves gaps at both edges; on the cell it reaches border
    /// to border. `HEADER_CELL` (subtle) marks a category/label header;
    /// `DONE_CELL` (inset, grayer) the de-emphasised "Done" header and each
    /// completed task row.
    // [impl->dsn~done-section~1]
    private static final String HEADER_CELL = "group-header-cell";
    private static final String DONE_CELL = "done-cell";
    /// Marks the header of the category the selected task belongs to, so the
    /// row that is highlighted names the context it sits in.
    // [impl->dsn~current-category-highlight~1]
    private static final String CURRENT_HEADER_CELL = "group-header-current";
    /// Marks an active task's cell: its left edge carries the accent bar.
    // [impl->dsn~running-task-accent~1]
    private static final String RUNNING_CELL = "running-task-cell";
    /// Marks a task row's cell, the rows that highlight under the mouse.
    // [impl->dsn~task-row-hover-actions~7]
    private static final String TASK_CELL = "task-cell";
    /// Marks a category's "Add task…" row's cell.
    // [impl->dsn~task-create-ui~15]
    private static final String ADD_TASK_CELL = "add-task-cell";

    /// The width bound onto a row graphic, cached once the list's vertical
    /// scrollbar exists (see [#rowWidth]).
    private javafx.beans.binding.@Nullable DoubleBinding rowWidth;

    private final Consumer<Task> onSwitch;
    /// Adds a bare `intellij:` section to the task (when absent) then switches,
    /// so a task gains its IntelliJ target and opens it in one action.
    // [impl->dsn~open-in-intellij~3]
    private final Consumer<Task> onOpenInIntellij;
    private final Consumer<Task> onRename;
    private final Consumer<Task> onDelete;
    private final BiConsumer<Task, TaskStatus> onSetStatus;
    /// Adds a link (browser URL or note) to a task (dialog in the window).
    private final Consumer<Task> onAddUrl;
    /// Opens a task's note link (resolved fresh in the window).
    private final Consumer<Task> onOpenTaskNote;
    private final Consumer<String> onToggleGroup;
    /// Collapses/expands the bottom "Done" section.
    private final Runnable onToggleDone;
    private final Consumer<String> onAddTask;
    /// The group's `auto:` search query, null for a hand-filled category —
    /// an auto category takes no tasks by hand, so it shows the synced-from
    /// placeholder instead of the "Add task…" row.
    // [impl->dsn~auto-category-placeholder-row~2]
    private final Function<String, @Nullable String> groupAutoQuery;
    /// Opens the group's `auto:` search on github.com (resolved fresh, like
    /// [#onOpenGroupRepo]).
    // [impl->dsn~auto-category-placeholder-row~2]
    private final Consumer<String> onOpenAutoSearch;

    /// Scopes the find field to a category by name — a press on the header's
    /// name drops it into the field as a chip. [impl->dsn~category-search~3]
    private final Consumer<String> onScopeCategory;
    /// The directory a category's task workspaces live under, null when its
    /// config names none — shown on the header, and stripped from its tasks'
    /// workspace paths. [impl->dsn~category-header-path~1]
    private final Function<String, @Nullable String> groupPath;

    /// Whether this category holds the selected task (or is selected itself) —
    /// the header then carries the selection's own colours.
    // [impl->dsn~current-category-highlight~1]
    private final Predicate<String> groupCurrent;
    /// The row menu's "Sync groups" submenu, null while no group is configured.
    // [impl->dsn~task-sync-groups-ui~1]
    private final Function<Task, javafx.scene.control.@Nullable Menu> syncGroupsMenu;
    private final Predicate<String> hasGroupConfig;
    private final Consumer<String> onOpenGroupConfig;
    private final Function<Task, @Nullable String> runningStatus;
    /// Invoked with (task id, target group — empty for the root) when a task
    /// row is dropped onto another row or a target is picked from the row
    /// menu's "Move to category" submenu.
    private final BiConsumer<String, String> onMoveToGroup;
    /// The category names (sorted), for that submenu — an off-screen drop
    /// target is unreachable by drag, the menu always lists it.
    private final java.util.function.Supplier<List<String>> groupNames;
    /// The group's `note:` URL from its config, null when it has none —
    /// only decides whether the note affordances show.
    private final Function<String, @Nullable String> groupNoteUrl;
    /// Opens the group's note by group name; the URL is resolved fresh at
    /// click time (the editor may hold an unsaved config edit).
    private final Consumer<String> onOpenGroupNote;
    /// The group's `repo:` URL from its config, null when it has none — only
    /// decides whether the repository affordances show.
    // [impl->dsn~group-repo-open~2]
    private final Function<String, @Nullable String> groupRepoUrl;
    /// Opens the group's repository by group name; resolved fresh at click
    /// time, like the note.
    private final Consumer<String> onOpenGroupRepo;
    /// Renames the project-group folder.
    private final Consumer<String> onRenameGroup;
    /// Deletes the project-group folder and everything inside it.
    private final Consumer<String> onDeleteGroup;
    /// The live state, title and labels of a PR URL, or null (not yet
    /// resolved). https://github.com/contextswitcher/contextswitcher-private/issues/49.
    private final Function<String, @Nullable PrInfo> prInfo;
    /// Focuses (or opens) a tab — the click handler of a PR icon and of a
    /// plain link icon alike. Takes the whole entry, so the status bar can name
    /// the address's title next to its URL, plus the owning task, whose
    /// category decides on which virtual desktop a *new* tab is opened.
    // [impl->dsn~pr-open-on-category-desktop~3]
    private final BiConsumer<Task, Task.UrlEntry> onFocusUrl;
    /// Reports the address under the pointer to the status bar, null on leave —
    /// the row itself has no room to show it. Every URL icon uses it: the PR
    /// icons, the note icons, the category's repository link.
    private final Consumer<@Nullable String> onHoverUrl;
    /// The tags to render on a row: all of the task's tags, or — while a filter
    /// is active — only the selected ones ([TaskTags#visible]).
    // [impl->dsn~task-tag-filter~2]
    private final Function<Task, List<String>> rowTags;
    /// The palette color (CSS hex) for a tag name, null when the tag is not in
    /// the configured palette (rendered as a muted chip).
    private final Function<String, @Nullable String> tagColor;
    /// The selectable tag names (configured palette plus tags already in use),
    /// for the row menu's "Tags" toggle submenu.
    private final java.util.function.Supplier<List<String>> selectableTagNames;
    /// Toggles a tag on a task (adds it if absent, removes it if present).
    private final BiConsumer<Task, String> onToggleTag;
    /// Pins or unpins a task, from the row menu's "Pinned" checkmark.
    // [impl->dsn~pinned-tasks~2]
    private final Consumer<Task> onTogglePinned;
    /// A group's own tags (from its `CONTEXTSWITCHER.md`), for the group menu's
    /// "Tags" toggle checkmarks.
    private final Function<String, List<String>> groupTagsOf;
    /// Toggles a tag on a project group (creating its config when missing).
    private final BiConsumer<String, String> onToggleGroupTag;
    /// Whether a category is pinned (its `CONTEXTSWITCHER.md` `pinned: true`),
    /// for the group menu's "Pinned" checkmark.
    // [impl->dsn~pinned-categories~1]
    private final java.util.function.Predicate<String> groupPinned;
    /// Pins or unpins a category (creating its config when missing).
    // [impl->dsn~pinned-categories~1]
    private final Consumer<String> onToggleGroupPinned;
    /// Focuses the terminal mirror, so a click on a task row lets the user
    /// type into the task's tmux window straight away.
    // [impl->dsn~terminal-pane~14]
    private final Runnable onFocusTerminal;
    /// How many messages are queued for a task, so the row can show a small
    /// count badge after the title. Read fresh per render — the queue files
    /// are tiny and local ([QueueFile]).
    // [impl->dsn~message-queue-count-badge~1]
    private final ToIntFunction<Task> queueCount;
    /// Deletes a task file that failed to parse (a corrupt / hand-broken
    /// frontmatter row) straight by its file name — a [TaskEntry.Failed] row
    /// carries no [Task], so it cannot use the normal [#onDelete] kill dialog.
    // [impl->dsn~corrupt-task-delete~2]
    private final Consumer<String> onDeleteFile;
    /// The absolute path of a task file, by file name — the error row's
    /// "Copy file path" item, so a corrupt file can be opened in whatever
    /// editor the user prefers.
    // [impl->dsn~corrupt-task-delete~2]
    private final Function<String, String> taskFilePath;
    /// The category's assigned virtual-desktop name (its `CONTEXTSWITCHER.md`
    /// `desktop:`), null when it has none — only decides whether the header's
    /// desktop-focus button shows.
    // [impl->dsn~category-desktop-focus~4]
    private final Function<String, @Nullable String> groupDesktop;
    /// Focuses the category's virtual desktop by group name; resolved fresh at
    /// click time (the editor may hold an unsaved config edit), like the note.
    private final Consumer<String> onFocusDesktop;
    /// Opens the category's local `folders:` — the header's folder icon — by
    /// group name, the list resolved fresh at click time like the desktop.
    /// The `Runnable` is called (FX thread) once every folder settled, so the
    /// button can stop showing its busy state.
    // [impl->dsn~category-folders-button~1]
    private final BiConsumer<String, Runnable> onOpenGroupFolders;
    /// Whether the list currently groups by labels — task rows then lead their
    /// second line with the task's category name, which the label grouping no
    /// longer shows through the surrounding headers.
    // [impl->dsn~task-label-grouping~3]
    private final java.util.function.BooleanSupplier labelView;
    /// Opens a throwaway tmux window on the category's remote, by group name —
    /// the header's terminal icon. Nothing is written to disk; the window is
    /// adopted as a task only if the user later runs "Sync tmux windows…".
    /// The `Runnable` is called (FX thread) once the attempt settles, success
    /// or failure, so the button can stop showing its busy state.
    // [impl->dsn~category-scratch-window~2]
    private final BiConsumer<String, Runnable> onNewTmuxWindow;
    /// The task's background refactoring summary, or null while unresolved —
    /// decides whether the row shows the count badge (https://github.com/contextswitcher/contextswitcher-private/issues/50).
    // [impl->dsn~refactoring-analysis-poller~1]
    private final Function<Task, @Nullable RefactoringSummary> refactoringSummary;
    /// Opens the task's RefactoringMiner AST-diff web view — the hover
    /// button's and the count badge's click. The `Runnable` is called (FX
    /// thread) once the attempt settles, so the clicked control re-enables.
    // [impl->dsn~refactoring-web-view~1]
    private final BiConsumer<Task, Runnable> onShowRefactorings;
    /// The task's in-flight remote creation, or null when it is a settled
    /// task — decides whether the row shows the creation progress bar.
    // [impl->dsn~task-create-progress~5]
    private final Function<Task, @Nullable Creation> creationOf;

    public TaskListCell(Consumer<Task> onSwitch, Consumer<Task> onOpenInIntellij,
            Consumer<Task> onRename, Consumer<Task> onDelete,
            BiConsumer<Task, TaskStatus> onSetStatus, Consumer<Task> onAddUrl,
            Consumer<Task> onOpenTaskNote, Consumer<String> onToggleGroup,
            Runnable onToggleDone,
            Consumer<String> onAddTask, Predicate<String> hasGroupConfig,
            Consumer<String> onOpenGroupConfig, Function<Task, @Nullable String> runningStatus,
            BiConsumer<String, String> onMoveToGroup,
            java.util.function.Supplier<List<String>> groupNames,
            Function<String, @Nullable String> groupNoteUrl, Consumer<String> onOpenGroupNote,
            Function<String, @Nullable String> groupRepoUrl, Consumer<String> onOpenGroupRepo,
            Consumer<String> onRenameGroup, Consumer<String> onDeleteGroup,
            Function<String, @Nullable PrInfo> prInfo,
            BiConsumer<Task, Task.UrlEntry> onFocusUrl,
            Consumer<@Nullable String> onHoverUrl,
            Function<Task, List<String>> rowTags, Function<String, @Nullable String> tagColor,
            java.util.function.Supplier<List<String>> selectableTagNames,
            BiConsumer<Task, String> onToggleTag, Consumer<Task> onTogglePinned,
            Function<String, List<String>> groupTagsOf, BiConsumer<String, String> onToggleGroupTag,
            java.util.function.Predicate<String> groupPinned, Consumer<String> onToggleGroupPinned,
            Runnable onFocusTerminal, ToIntFunction<Task> queueCount,
            Consumer<String> onDeleteFile, Function<String, String> taskFilePath,
            Function<String, @Nullable String> groupDesktop, Consumer<String> onFocusDesktop,
            BiConsumer<String, Runnable> onOpenGroupFolders,
            java.util.function.BooleanSupplier labelView,
            BiConsumer<String, Runnable> onNewTmuxWindow,
            Function<Task, @Nullable RefactoringSummary> refactoringSummary,
            BiConsumer<Task, Runnable> onShowRefactorings,
            Function<Task, @Nullable Creation> creationOf,
            Function<String, @Nullable String> groupAutoQuery,
            Consumer<String> onOpenAutoSearch,
            Consumer<String> onScopeCategory, Function<String, @Nullable String> groupPath,
            Predicate<String> groupCurrent,
            Function<Task, javafx.scene.control.@Nullable Menu> syncGroupsMenu) {
        this.syncGroupsMenu = syncGroupsMenu;
        this.creationOf = creationOf;
        this.groupAutoQuery = groupAutoQuery;
        this.onOpenAutoSearch = onOpenAutoSearch;
        this.onScopeCategory = onScopeCategory;
        this.groupPath = groupPath;
        this.groupCurrent = groupCurrent;
        this.onSwitch = onSwitch;
        this.onOpenInIntellij = onOpenInIntellij;
        this.onRename = onRename;
        this.onDelete = onDelete;
        this.onSetStatus = onSetStatus;
        this.onAddUrl = onAddUrl;
        this.onOpenTaskNote = onOpenTaskNote;
        this.onToggleGroup = onToggleGroup;
        this.onToggleDone = onToggleDone;
        this.onAddTask = onAddTask;
        this.hasGroupConfig = hasGroupConfig;
        this.onOpenGroupConfig = onOpenGroupConfig;
        this.runningStatus = runningStatus;
        this.onMoveToGroup = onMoveToGroup;
        this.groupNames = groupNames;
        this.groupNoteUrl = groupNoteUrl;
        this.onOpenGroupNote = onOpenGroupNote;
        this.groupRepoUrl = groupRepoUrl;
        this.onOpenGroupRepo = onOpenGroupRepo;
        this.onRenameGroup = onRenameGroup;
        this.onDeleteGroup = onDeleteGroup;
        this.prInfo = prInfo;
        this.onFocusUrl = onFocusUrl;
        this.onHoverUrl = onHoverUrl;
        this.rowTags = rowTags;
        this.tagColor = tagColor;
        this.selectableTagNames = selectableTagNames;
        this.onToggleTag = onToggleTag;
        this.onTogglePinned = onTogglePinned;
        this.groupTagsOf = groupTagsOf;
        this.onToggleGroupTag = onToggleGroupTag;
        this.groupPinned = groupPinned;
        this.onToggleGroupPinned = onToggleGroupPinned;
        this.onFocusTerminal = onFocusTerminal;
        this.queueCount = queueCount;
        this.onDeleteFile = onDeleteFile;
        this.taskFilePath = taskFilePath;
        this.groupDesktop = groupDesktop;
        this.onFocusDesktop = onFocusDesktop;
        this.onOpenGroupFolders = onOpenGroupFolders;
        this.labelView = labelView;
        this.onNewTmuxWindow = onNewTmuxWindow;
        this.refactoringSummary = refactoringSummary;
        this.onShowRefactorings = onShowRefactorings;
        setOnMouseClicked(event -> {
            // Primary button only: a right-click opens the context menu and
            // must not also act on the row underneath it.
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            if (getItem() instanceof GroupHeader header) {
                // Collapse/expand is the arrow's job (which consumes its
                // clicks); a click on the rest of the header opens the
                // group's config in the editor.
                onOpenGroupConfig.accept(header.name());
            } else if (getItem() instanceof DoneHeader) {
                onToggleDone.run();
            } else if (getItem() instanceof LabelHeader label) {
                // No config file behind a label — a click anywhere on the
                // header just collapses/expands it, like the Done header.
                // [impl->dsn~task-label-grouping~3]
                onToggleGroup.accept(label.key());
            } else if (getItem() instanceof AddTaskRow add
                    && groupAutoQuery.apply(add.group()) != null) {
                // The placeholder is the row: a click anywhere on it opens the
                // search it is synced from.
                // [impl->dsn~auto-category-placeholder-row~2]
                onOpenAutoSearch.accept(add.group());
            } else if (getItem() instanceof AddTaskRow add) {
                // The "Add task…" row likewise: its button spans the row, and
                // the cell's own edges around it count too.
                // [impl->dsn~task-create-ui~15]
                onAddTask.accept(add.group());
            } else if (getItem() instanceof TaskEntry.Loaded loaded) {
                if (event.getClickCount() == 2) {
                    switchTask(loaded.task());
                }
                // A click (not keyboard selection — arrowing through the list
                // must keep the list focused) hands the keyboard to the
                // terminal mirror, so the user can type straight away.
                // [impl->dsn~terminal-pane~14]
                onFocusTerminal.run();
            }
        });
        // Drag source on the cell, not the row graphic: pressing an
        // unselected row selects it first, and the selection side effects
        // can replace the row graphic mid-gesture — a drag started from the
        // (stable) cell survives; one on the old graphic never fires.
        // [impl->dsn~task-move-dnd~6]
        setOnDragDetected(event -> {
            if (getItem() instanceof TaskEntry.Loaded loaded) {
                Dragboard dragboard = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(loaded.task().id());
                dragboard.setContent(content);
                event.consume();
            }
        });
    }

    /// The list's content width less its vertical scrollbar (while shown) and
    /// this cell's insets: the row then ends at the cell's inner edge, with no
    /// dead strip on the right, while a long title still ellipsizes instead of
    /// pushing the icons out of view. One pixel short, so rounding can never
    /// make the row wider than the viewport and bring up a horizontal bar.
    private javafx.beans.binding.DoubleBinding rowWidth(javafx.scene.control.ListView<Object> list) {
        javafx.beans.binding.DoubleBinding cached = rowWidth;
        if (cached != null) {
            return cached;
        }
        javafx.scene.control.ScrollBar bar = null;
        for (javafx.scene.Node node : list.lookupAll(".scroll-bar")) {
            if (node instanceof javafx.scene.control.ScrollBar scrollBar
                    && scrollBar.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                bar = scrollBar;
            }
        }
        javafx.scene.control.ScrollBar vertical = bar;
        List<javafx.beans.Observable> dependencies = new ArrayList<>(
                List.of(list.widthProperty(), list.insetsProperty(), insetsProperty()));
        if (vertical != null) {
            dependencies.add(vertical.visibleProperty());
            dependencies.add(vertical.widthProperty());
        }
        javafx.beans.binding.DoubleBinding width = javafx.beans.binding.Bindings.createDoubleBinding(() -> {
            double available = list.getWidth() - list.snappedLeftInset() - list.snappedRightInset()
                    - snappedLeftInset() - snappedRightInset() - 1;
            if (vertical != null && vertical.isVisible()) {
                available -= vertical.getWidth();
            }
            return Math.max(0, available);
        }, dependencies.toArray(javafx.beans.Observable[]::new));
        // Before the skin exists there is no scrollbar to follow yet; ask again
        // on the next render instead of keeping a binding blind to it.
        if (vertical != null) {
            rowWidth = width;
        }
        return width;
    }

    /// Switching always highlights the row it acts on — and puts the keyboard
    /// on the list: the icon buttons take no focus, so after pressing play the
    /// focus would sit wherever it happened to be (or be grabbed by the
    /// mirror's attach), and the arrow keys would not move from the row just
    /// switched to.
    private void switchTask(Task task) {
        getListView().getSelectionModel().select(getIndex());
        getListView().requestFocus();
        onSwitch.accept(task);
    }

    @Override
    protected void updateItem(@Nullable Object item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setTooltip(null);
            setContextMenu(null);
            getStyleClass().removeAll(HEADER_CELL, DONE_CELL, CURRENT_HEADER_CELL, RUNNING_CELL, TASK_CELL, ADD_TASK_CELL);
            return;
        }
        // Section backgrounds go on the cell (full width), not the inner row
        // (bound narrower to reserve scrollbar room). [impl->dsn~done-section~1]
        getStyleClass().removeAll(HEADER_CELL, DONE_CELL, CURRENT_HEADER_CELL, RUNNING_CELL, TASK_CELL, ADD_TASK_CELL);
        if (item instanceof TaskEntry.Loaded) {
            getStyleClass().add(TASK_CELL);
        }
        // The "Add task…" row highlights as a whole cell, edge to edge; its
        // button's own hover fill stopped short of the cell's padding.
        if (item instanceof AddTaskRow add && groupAutoQuery.apply(add.group()) == null) {
            getStyleClass().add(ADD_TASK_CELL);
        }
        switch (item) {
            case GroupHeader header -> {
                getStyleClass().add(HEADER_CELL);
                // [impl->dsn~current-category-highlight~1]
                if (groupCurrent.test(header.name())) {
                    getStyleClass().add(CURRENT_HEADER_CELL);
                }
            }
            case LabelHeader ignored -> getStyleClass().add(HEADER_CELL);
            case DoneHeader ignored -> getStyleClass().add(DONE_CELL);
            case TaskEntry.Loaded loaded when loaded.task().status() == TaskStatus.DONE ->
                    getStyleClass().add(DONE_CELL);
            // [impl->dsn~running-task-accent~1]
            case TaskEntry.Loaded loaded when loaded.task().status() == TaskStatus.ACTIVE ->
                    getStyleClass().add(RUNNING_CELL);
            default -> { }
        }
        setContextMenu(switch (item) {
            case TaskEntry.Loaded loaded -> taskMenu(loaded.task());
            case GroupHeader header -> groupMenu(header.name());
            default -> null;
        });
        HBox graphic;
        try {
            graphic = switch (item) {
                case TaskEntry.Loaded loaded -> taskRow(loaded.task());
                case TaskEntry.Failed failed -> errorRow(failed);
                case GroupHeader header -> groupHeader(header);
                case DoneHeader header -> doneHeader(header);
                case LabelHeader header -> labelHeader(header);
                case AddTaskRow add -> addTaskRow(add);
                default -> null;
            };
        } catch (RuntimeException e) {
            // A throwing row builder used to leave the cell silently blank
            // (the FX thread's exception only reaches stderr, which the
            // packaged app drops): show what failed instead, and log it.
            String what = item instanceof TaskEntry.Loaded loaded ? loaded.id() : item.toString();
            Logger.error(e, "Cannot render row {}", what);
            Label text = new Label("\u26a0 " + what + ": cannot render (" + e + ")");
            text.getStyleClass().add(Styles.DANGER);
            text.setMinWidth(0);
            Tooltip.install(text, new Tooltip(text.getText()));
            graphic = new HBox(text);
            graphic.setPadding(ROW_PADDING);
        }
        // A ListView cell reports its graphic at the graphic's own preferred
        // width and scrolls horizontally on overflow rather than shrinking it;
        // binding to the list's width forces the row to actually shrink.
        if (graphic != null && getListView() != null) {
            javafx.beans.binding.DoubleBinding width = rowWidth(getListView());
            graphic.prefWidthProperty().bind(width);
            graphic.maxWidthProperty().bind(width);
        }
        setGraphic(graphic);
        setText(null);
    }

    /// Right-click menu for a task row: status changes (reaching `done`, which
    /// the status-label toggle does not), the pin toggle, rename, and delete.
    // [impl->dsn~task-status-cycle~2]
    // [impl->dsn~pinned-tasks~2]
    // [impl->dsn~task-delete~6]
    private ContextMenu taskMenu(Task task) {
        ContextMenu menu = new ContextMenu();
        // The keyboard-reachable counterpart of the hover play button.
        // [impl->dsn~task-row-hover-actions~7]
        MenuItem switchItem = new MenuItem("Switch");
        switchItem.setOnAction(event -> switchTask(task));
        // Adds a bare `intellij:` section if the task lacks one, then switches —
        // opening the project in IntelliJ just as pressing play would.
        // [impl->dsn~open-in-intellij~3]
        MenuItem intellijItem = new MenuItem("Open in IntelliJ");
        intellijItem.setOnAction(event -> onOpenInIntellij.accept(task));
        intellijItem.setDisable(task.lacksWorktree());
        menu.getItems().addAll(switchItem, intellijItem, new SeparatorMenuItem());
        switch (task.status()) {
            case ACTIVE -> menu.getItems().add(statusItem("Suspend…", task, TaskStatus.SUSPENDED));
            case SUSPENDED, DONE -> menu.getItems().add(statusItem("Resume (active)", task, TaskStatus.ACTIVE));
        }
        if (task.status() != TaskStatus.DONE) {
            menu.getItems().add(statusItem("Mark done", task, TaskStatus.DONE));
        }
        menu.getItems().add(new SeparatorMenuItem());

        // Pinned tasks head their status block.
        // [impl->dsn~pinned-tasks~2]
        javafx.scene.control.CheckMenuItem pinned = new javafx.scene.control.CheckMenuItem("Pinned");
        pinned.setSelected(task.pinned());
        pinned.setOnAction(event -> onTogglePinned.accept(task));
        menu.getItems().add(pinned);

        // The task's deep link, for pasting into a note (OneNote, README).
        // [impl->dsn~task-link-copy~1]
        MenuItem copyLink = new MenuItem("Copy link");
        copyLink.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(DeepLink.taskUrl(task.id()));
            Clipboard.getSystemClipboard().setContent(content);
        });
        menu.getItems().add(copyLink);

        MenuItem rename = new MenuItem("Rename task…");
        rename.setOnAction(event -> onRename.accept(task));
        MenuItem delete = new MenuItem("Delete task…");
        delete.setOnAction(event -> onDelete.accept(task));
        menu.getItems().addAll(rename, delete);

        // "Move to category" submenu — the drag'n'drop's menu counterpart: a
        // drop target scrolled out of view is unreachable by drag, the menu
        // always lists every category (plus the root; the current one omitted).
        // [impl->dsn~task-move-dnd~6]
        int slash = task.id().lastIndexOf('/');
        String currentGroup = slash < 0 ? "" : task.id().substring(0, slash);
        javafx.scene.control.Menu moveMenu = new javafx.scene.control.Menu("Move to category");
        if (!currentGroup.isEmpty()) {
            MenuItem root = new MenuItem("(no category)");
            root.setOnAction(event -> onMoveToGroup.accept(task.id(), ""));
            moveMenu.getItems().add(root);
        }
        for (String group : groupNames.get()) {
            if (group.equals(currentGroup)) {
                continue;
            }
            MenuItem item = new MenuItem(group);
            item.setOnAction(event -> onMoveToGroup.accept(task.id(), group));
            moveMenu.getItems().add(item);
        }
        if (!moveMenu.getItems().isEmpty()) {
            menu.getItems().add(moveMenu);
        }
        // [impl->dsn~task-sync-groups-ui~1]
        javafx.scene.control.@Nullable Menu syncMenu = syncGroupsMenu.apply(task);
        if (syncMenu != null) {
            menu.getItems().add(syncMenu);
        }

        // "Tags" submenu — toggle any selectable tag (configured or already in
        // use) on the task without hand-editing YAML. Shown only when at least
        // one tag exists anywhere.
        // [impl->dsn~task-tag-filter~2]
        javafx.scene.control.@Nullable Menu tagsMenu =
                tagsSubmenu(task.tags(), name -> onToggleTag.accept(task, name));
        if (tagsMenu != null) {
            menu.getItems().addAll(new SeparatorMenuItem(), tagsMenu);
        }
        return menu;
    }

    /// A "Tags" submenu of `CheckMenuItem`s — one per selectable tag (configured
    /// palette plus tags already in use), checked when `current` already carries
    /// it (case-insensitive), toggling via `onToggle`. Null when no tag exists
    /// anywhere (no submenu shown). Shared by the task row and the group header.
    // [impl->dsn~task-tag-filter~2]
    // [impl->dsn~tag-selection-union~2]
    private javafx.scene.control.@Nullable Menu tagsSubmenu(List<String> current,
            Consumer<String> onToggle) {
        List<String> names = selectableTagNames.get();
        if (names.isEmpty()) {
            return null;
        }
        java.util.Set<String> have = new java.util.HashSet<>();
        for (String tag : current) {
            have.add(tag.toLowerCase(Locale.ROOT));
        }
        javafx.scene.control.Menu tagsMenu = new javafx.scene.control.Menu("Tags");
        for (String name : names) {
            // The item shows the tag as its palette-colored chip (the same
            // rendering as the row chips), not as plain text.
            javafx.scene.control.CheckMenuItem item = new javafx.scene.control.CheckMenuItem(
                    null, TagChips.chip(name, tagColor.apply(name)));
            item.setSelected(have.contains(name.toLowerCase(Locale.ROOT)));
            item.setOnAction(event -> onToggle.accept(name));
            tagsMenu.getItems().add(item);
        }
        return tagsMenu;
    }

    /// Right-click menu for a group header: open the group's note and
    /// repository (when the config carries a `note:` / `repo:` URL), pin the
    /// category to the top of the list, and create or edit the group's
    /// `CONTEXTSWITCHER.md` defaults file.
    // [impl->dsn~group-config-create~9]
    // [impl->dsn~group-note-open~5]
    // [impl->dsn~group-repo-open~2]
    // [impl->dsn~pinned-categories~1]
    private ContextMenu groupMenu(String group) {
        ContextMenu menu = new ContextMenu();
        String note = groupNoteUrl.apply(group);
        if (note != null) {
            MenuItem openNote = new MenuItem("Open note");
            openNote.setOnAction(event -> onOpenGroupNote.accept(group));
            menu.getItems().add(openNote);
        }
        // [impl->dsn~group-repo-open~2]
        String repo = groupRepoUrl.apply(group);
        if (repo != null) {
            MenuItem openRepo = new MenuItem("Open repository");
            openRepo.setOnAction(event -> onOpenGroupRepo.accept(group));
            menu.getItems().add(openRepo);
        }
        // The category's own deep link, for pasting into a note (OneNote,
        // README) that should link back to this category.
        // [impl->dsn~category-link-copy~1]
        MenuItem copyLink = new MenuItem("Copy link");
        copyLink.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(DeepLink.categoryUrl(group));
            Clipboard.getSystemClipboard().setContent(content);
        });
        menu.getItems().add(copyLink);
        MenuItem config = new MenuItem(
                hasGroupConfig.test(group) ? "Edit CS config…" : "Add CS config…");
        config.setOnAction(event -> onOpenGroupConfig.accept(group));
        menu.getItems().add(config);
        // Pinned categories sort above the unpinned ones.
        // [impl->dsn~pinned-categories~1]
        javafx.scene.control.CheckMenuItem pinned = new javafx.scene.control.CheckMenuItem("Pinned");
        pinned.setSelected(groupPinned.test(group));
        pinned.setOnAction(event -> onToggleGroupPinned.accept(group));
        menu.getItems().add(pinned);
        // [impl->dsn~group-rename~1]
        MenuItem renameGroup = new MenuItem("Rename group…");
        renameGroup.setOnAction(event -> onRenameGroup.accept(group));
        menu.getItems().add(renameGroup);
        // [impl->dsn~category-delete~1]
        MenuItem deleteGroup = new MenuItem("Delete category…");
        deleteGroup.setOnAction(event -> onDeleteGroup.accept(group));
        menu.getItems().add(deleteGroup);

        // "Tags" submenu — toggle the group's own tags (inherited by every task
        // in it), written to its CONTEXTSWITCHER.md. Shown only with a palette.
        // [impl->dsn~task-tag-filter~2]
        javafx.scene.control.@Nullable Menu tagsMenu =
                tagsSubmenu(groupTagsOf.apply(group), name -> onToggleGroupTag.accept(group, name));
        if (tagsMenu != null) {
            menu.getItems().addAll(new SeparatorMenuItem(), tagsMenu);
        }
        return menu;
    }

    private MenuItem statusItem(String label, Task task, TaskStatus status) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(event -> onSetStatus.accept(task, status));
        return item;
    }

    private HBox groupHeader(GroupHeader header) {
        Label arrow = new Label(header.expanded() ? "▾" : "▸");
        arrow.getStyleClass().add(Styles.TEXT_MUTED);
        // The arrow alone collapses/expands (padding widens its hit area);
        // consumed so the cell's click handler doesn't also open the config.
        arrow.setPadding(new Insets(0, 6, 0, 2));
        // On **pressed**, not clicked: a click is only synthesized when press
        // and release land on the same node, and this Label lives in a cell
        // graphic that `ListView.refresh()` throws away and rebuilds — which
        // the running-status poll does on every tick that changes anything,
        // so with live Claude sessions it fires constantly. A press and
        // release a hundred milliseconds apart then straddle a rebuild, the
        // click never happens, and the arrow ignores the first attempt:
        // field report 2026-09-12, "I need to click it twice". A toggle is
        // press-shaped anyway.
        // [impl->dsn~collapse-on-press~1]
        arrow.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                onToggleGroup.accept(header.name());
                event.consume();
            }
        });
        // The cell's own handler opens the group config on a click; the press
        // above consumes the arrow's, but a release/click pair that still
        // reaches the cell would open it on top of the toggle.
        arrow.setOnMouseClicked(javafx.event.Event::consume);
        arrow.setOnMouseReleased(javafx.event.Event::consume);

        // The name scopes the find field to this category (a chip in the
        // field), and a second press lifts the scope again; the rest of the
        // header still opens the config. Pressed, not clicked, for the arrow's
        // reason above.
        // [impl->dsn~category-search~3]
        Label label = new Label(header.name());
        label.setId("group-name-label");
        label.setMinWidth(0);
        label.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_MUTED);
        label.setCursor(Cursor.HAND);
        Tooltip.install(label, new Tooltip("Search in " + header.name() + " (click again: all categories)"));
        label.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                onScopeCategory.accept(header.name());
                event.consume();
            }
        });
        label.setOnMouseClicked(javafx.event.Event::consume);
        label.setOnMouseReleased(javafx.event.Event::consume);

        HBox nameLine = new HBox(6, label);
        nameLine.setAlignment(Pos.CENTER_LEFT);
        nameLine.setMinWidth(0);

        // A remote category — its CS config sets `remote` — carries a terminal
        // icon right after the name (tooltip names the host), so local and
        // remote groups tell apart at a glance; "+" here defaults to a remote
        // Claude session. Clicking the icon opens a throwaway tmux window on
        // that host — no task file, no Claude ("Sync tmux windows…" adopts it
        // later if it turns out to be worth keeping).
        // [impl->dsn~task-folder-grouping-ui~8]
        // [impl->dsn~category-scratch-window~2]
        if (header.remote() != null) {
            Button scratch = iconButton(MDITechnology.CONSOLE,
                    "New tmux window on \"" + header.remote() + "\"");
            scratch.setId("scratch-window-button");
            // Grayed out for the ssh + tmux round-trip so a slow remote can't
            // look like a dead button (`req~async-action-feedback~1`) — the
            // callback fires on success or failure, never leaving it stuck.
            scratch.setOnAction(event -> {
                scratch.setDisable(true);
                onNewTmuxWindow.accept(header.name(), () -> scratch.setDisable(false));
            });
            nameLine.getChildren().add(scratch);
        }

        // A category naming local `folders:` opens them from the header —
        // reaching the project's directories without switching to one of its
        // tasks (and without a task that would carry the same list again).
        // Like the terminal icon, it states a property of the category.
        // [impl->dsn~category-folders-button~1]
        if (!header.folders().isEmpty()) {
            Button openFolders = iconButton(MDITechnology.FOLDER_OPEN_OUTLINE,
                    header.folders().size() == 1
                            ? "Open \"" + header.folders().getFirst() + "\""
                            : "Open %d folders: %s".formatted(header.folders().size(),
                                    String.join(", ", header.folders())));
            openFolders.setId("group-folders-button");
            // Explorer is a local round-trip per folder, so the button greys
            // out until they are all handled (`req~async-action-feedback~1`).
            openFolders.setOnAction(event -> {
                openFolders.setDisable(true);
                onOpenGroupFolders.accept(header.name(), () -> openFolders.setDisable(false));
            });
            nameLine.getChildren().add(openFolders);
        }

        // A category whose config still loads but has something wrong with it
        // says so on the header — the only place a `CONTEXTSWITCHER.md`
        // problem can be seen without opening the file, since a category is
        // not a task and can never become an error row.
        // [impl->dsn~frontmatter-duplicate-keys~1]
        if (header.warning() != null) {
            Label warning = new Label("⚠");
            warning.setId("group-warning-label");
            warning.getStyleClass().add(Styles.WARNING);
            Tooltip.install(warning, new Tooltip(header.warning() + "\nClick to open the file."));
            // The row is where the user is looking; opening the config is
            // otherwise only the header click. [impl->dsn~group-config-parse-error~1]
            warning.setCursor(Cursor.HAND);
            warning.setOnMouseClicked(event -> {
                onOpenGroupConfig.accept(header.name());
                event.consume();
            });
            nameLine.getChildren().add(warning);
        }

        // Second line: the directory the category's task workspaces live
        // under, once — its task rows then name only their own directory
        // below it. Cut at the front when narrow: the end of a path is the
        // part that tells categories apart.
        // [impl->dsn~category-header-path~1]
        VBox title = new VBox(0, nameLine);
        if (header.path() != null) {
            Label path = new Label(header.path());
            path.setId("group-path-label");
            path.setMinWidth(0);
            path.setTextOverrun(javafx.scene.control.OverrunStyle.LEADING_ELLIPSIS);
            path.getStyleClass().addAll(Styles.TEXT_MUTED, ROW_DETAIL);
            Tooltip.install(path, new Tooltip(header.path()));
            title.getChildren().add(path);
        }
        // Takes the width the trailing icons leave (see the task row's text box
        // for why prefWidth 0 is load-bearing).
        title.setMinWidth(0);
        title.setPrefWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);

        HBox row = new HBox(6, arrow, title);

        // The group's own tags, inherited by every task in the group.
        // [impl->dsn~task-tag-filter~2]
        for (String tag : header.tags()) {
            row.getChildren().add(tagChip(tag));
        }

        // A collapsed category counts its running tasks, as a bare number just
        // left of the header's buttons — expanded, the accent bars show them.
        // [impl->dsn~running-task-accent~1]
        if (!header.expanded() && header.running() > 0) {
            Label running = new Label(Integer.toString(header.running()));
            running.setId("group-running-count");
            running.setMinWidth(Region.USE_PREF_SIZE);
            running.getStyleClass().add("running-count");
            Tooltip.install(running, new Tooltip(header.running() == 1
                    ? "1 running task" : header.running() + " running tasks"));
            row.getChildren().add(running);
        }

        // A category pinned to a virtual desktop (its config's desktop:) carries
        // an always-visible "Jump to desktop" icon, right-aligned with the
        // note and repository icons: it leads away from the list like they do.
        // Only switches Windows to that desktop; the name is resolved fresh on
        // click. [impl->dsn~category-desktop-focus~4]
        String desktop = groupDesktop.apply(header.name());
        if (desktop != null) {
            Button focusDesktop = iconButton(MDITechnology.MONITOR_MULTIPLE,
                    "Jump to desktop \"" + desktop + "\"");
            focusDesktop.setId("group-desktop-button");
            focusDesktop.setOnAction(event -> onFocusDesktop.accept(header.name()));
            row.getChildren().add(focusDesktop);
        }

        // Always-visible note icon when the group config carries a note: URL.
        // [impl->dsn~group-note-open~5]
        String note = groupNoteUrl.apply(header.name());
        if (note != null) {
            row.getChildren().add(urlIconButton(MDITechnology.NOTE_TEXT_OUTLINE, "Open note",
                    note, () -> onOpenGroupNote.accept(header.name())));
        }

        // Always-visible repository icon when the group config carries a repo:
        // URL — the project's home page is what the category name stands for,
        // so it is reachable without opening the config. A plain chain link
        // (not MDI's source-repository, whose branch-and-node glyph reads as a
        // fork at 14px): the row's own "Add link…" icon already establishes the
        // chain link as this app's mark for "a URL".
        // [impl->dsn~group-repo-open~2]
        String repo = groupRepoUrl.apply(header.name());
        if (repo != null) {
            Button repoButton = urlIconButton(MDIInterface.LINK_VARIANT, "Open " + repo,
                    repo, () -> onOpenGroupRepo.accept(header.name()));
            repoButton.setId("group-repo-button");
            row.getChildren().add(repoButton);
        }

        // An always-visible plus at the header's right edge, pre-filling the
        // dialog with this group. An auto category is filled by its search,
        // never by hand — no plus.
        // [impl->dsn~task-create-ui~15]
        // [impl->dsn~auto-category-placeholder-row~2]
        if (groupAutoQuery.apply(header.name()) == null) {
            Button addInGroup = iconButton(MDIInterface.PLUS, "Add task in " + header.name() + "…");
            addInGroup.setId("group-header-add-button");
            addInGroup.setOnAction(event -> onAddTask.accept(header.name()));
            row.getChildren().add(addInGroup);
        }

        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(ROW_PADDING);
        // Background is on the cell (HEADER_CELL), so the row itself is
        // transparent; the drag-over highlight restores that.
        installDropTarget(row, header.name());
        return row;
    }

    /// A label-group header ("Group by labels" on): the tag's colored chip
    /// plus a task count. Deliberately not a drop target and without a
    /// context menu — there is no folder or config file behind a label, and
    /// dropping a task cannot mean "add this tag" while drops elsewhere
    /// mean "move".
    // [impl->dsn~task-label-grouping~3]
    private HBox labelHeader(LabelHeader header) {
        Label arrow = new Label(header.expanded() ? "▾" : "▸");
        arrow.getStyleClass().add(Styles.TEXT_MUTED);
        arrow.setPadding(new Insets(0, 6, 0, 2));

        Label count = new Label("(" + header.count() + ")");
        count.getStyleClass().add(Styles.TEXT_MUTED);

        HBox row = new HBox(6, arrow, tagChip(header.name()), count);
        row.setAlignment(Pos.CENTER_LEFT);
        // [impl->dsn~nested-grouping~1]
        row.setPadding(new Insets(ROW_PADDING.getTop(), ROW_PADDING.getRight(), ROW_PADDING.getBottom(),
                ROW_PADDING.getLeft() + 16 * header.depth()));
        // Background is on the cell (HEADER_CELL); the row stays transparent.
        return row;
    }

    /// The bottom "Done" section header: a grayer, collapsible bar counting
    /// every completed task, so finished work stays reachable but visually
    /// stepped back. Not a drop target — completed tasks are not re-filed by
    /// dragging onto it.
    // [impl->dsn~done-section~1]
    private HBox doneHeader(DoneHeader header) {
        Label arrow = new Label(header.expanded() ? "▾" : "▸");
        arrow.getStyleClass().add(Styles.TEXT_MUTED);

        Label label = new Label("Done (" + header.count() + ")");
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_MUTED);
        HBox.setHgrow(label, Priority.ALWAYS);

        HBox row = new HBox(6, arrow, label);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(ROW_PADDING);
        // Background is on the cell (DONE_CELL); the row stays transparent.
        return row;
    }

    /// Accepts a dragged task row: dropping moves the task into `targetGroup`
    /// (the row's own group — so dropping on a root-level task moves a task
    /// out of its folder). The row is `:drop-target` (main.css) while a drag
    /// hovers it.
    // [impl->dsn~task-move-dnd~6]
    private void installDropTarget(Region row, String targetGroup) {
        row.setOnDragOver(event -> {
            if (event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        // A pseudo-class, not a style class: setting it twice cannot leave a
        // stale copy behind that one removal misses.
        row.setOnDragEntered(event -> {
            if (event.getDragboard().hasString()) {
                row.pseudoClassStateChanged(DROP_TARGET, true);
            }
        });
        row.setOnDragExited(event -> row.pseudoClassStateChanged(DROP_TARGET, false));
        row.setOnDragDropped(event -> {
            String taskId = event.getDragboard().getString();
            boolean valid = taskId != null && !taskId.isBlank();
            if (valid) {
                onMoveToGroup.accept(taskId, targetGroup);
            }
            event.setDropCompleted(valid);
            event.consume();
        });
    }

    // [impl->dsn~task-create-ui~15]
    // [impl->dsn~group-config-create~9]
    private HBox addTaskRow(AddTaskRow add) {
        String autoQuery = groupAutoQuery.apply(add.group());
        if (autoQuery != null) {
            return autoSyncRow(add.group(), autoQuery);
        }
        // Slim and muted: the header's plus is the prominent way in; this row
        // is where the eye lands after reading a category's last task.
        // The button spans the whole row, so a click anywhere on it adds; its
        // text is indented to the task titles in main.css.
        Button addTask = new Button("Add task…", new SvgNode(MDIInterface.PLUS.path(), 12));
        addTask.setId("group-add-task-button");
        addTask.getStyleClass().addAll(Styles.SMALL, Styles.FLAT, "add-task-row-button");
        addTask.setFocusTraversable(false);
        addTask.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(addTask, Priority.ALWAYS);
        // Handled by the button; the cell's click handler would add a second time.
        addTask.setOnMouseClicked(javafx.event.Event::consume);
        addTask.setOnAction(event -> onAddTask.accept(add.group()));

        HBox row = new HBox(addTask);
        row.setAlignment(Pos.CENTER_LEFT);
        // A drop target like the header — in an empty group the only one below it.
        // [impl->dsn~task-move-dnd~6]
        installDropTarget(row, add.group());
        return row;
    }

    /// An auto category's last row, in place of the "Add task…" one: where its
    /// tasks come from. Shaped like a task row (icon slot, title line, muted
    /// second line) and clickable — it opens the same search on github.com,
    /// approximately: the web search cannot express the `maxSloc` filter.
    // [impl->dsn~auto-category-placeholder-row~2]
    private HBox autoSyncRow(String group, String query) {
        String url = AutoPrLookup.searchUrl(query);
        Button open = urlIconButton(MDIInterface.SYNC, "Open " + url, url,
                () -> onOpenAutoSearch.accept(group));
        Label title = new Label("Synced from a GitHub pull-request search");
        title.getStyleClass().add(Styles.TEXT_MUTED);
        Label sub = new Label(query);
        sub.getStyleClass().addAll(Styles.TEXT_MUTED, ROW_DETAIL);
        sub.setMinWidth(0);
        VBox text = new VBox(1, title, sub);
        text.setMinWidth(0);
        HBox row = new HBox(8, open, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(ROW_PADDING);
        row.setCursor(Cursor.HAND);
        return row;
    }

    /// The title as one row line: newlines (and the whitespace around them)
    /// collapsed to single spaces.
    static String singleLine(String title) {
        return title.strip().replaceAll("\\s*\\R\\s*", " ");
    }

    private HBox taskRow(Task task) {
        // Leading bubble carries the task status: a checkmark once done, a pause
        // glyph while suspended, otherwise the live running dot. This replaces
        // the old status text column — the glyph says it all.
        // [impl->dsn~task-running-indicator~7]
        javafx.scene.Node dot = switch (task.status()) {
            case DONE -> statusGlyph(MDIInterface.CHECK, "done");
            // [impl->dsn~suspended-timestamp~1]
            case SUSPENDED -> statusGlyph(MDIInterface.PAUSE, task.suspendedAt() == null
                    ? "suspended" : "suspended " + task.suspendedAt());
            case ACTIVE -> runningDot(runningStatus.apply(task));
        };

        // One line, always: a task created from a multi-line description keeps
        // that whole text as its `title:` until Claude publishes a short
        // `@cs_title`, and a Label renders the embedded newlines — the fresh
        // row would stand three lines tall among its one-line neighbours.
        // The full text stays reachable on hover.
        // [impl->dsn~task-row-single-line-title~1]
        Label title = new Label(singleLine(task.title()));
        title.setMinWidth(0);
        if (!title.getText().equals(task.title())) {
            Tooltip.install(title, new Tooltip(task.title()));
        }

        // A small negative-circled count of the task's queued messages sits
        // right after the title — the name matters more, so it comes first and
        // ellipsizes ahead of the (fixed-width) badge; nothing shows when the
        // queue is empty. The tooltip carries the exact number even past 20.
        // [impl->dsn~message-queue-count-badge~1]
        int queued = queueCount.applyAsInt(task);
        String queueGlyph = CircledCount.glyph(queued);
        // A pinned task leads its title with a small pin, so why it sits atop
        // its block is visible on the row itself. [impl->dsn~pinned-tasks~2]
        javafx.scene.Node pin = null;
        if (task.pinned()) {
            pin = statusGlyph(MDIInterface.PIN, "pinned");
            pin.setId("task-pin-icon");
        }
        javafx.scene.Node titleLine = title;
        if (!queueGlyph.isEmpty() || pin != null) {
            HBox line = new HBox(4);
            line.setAlignment(Pos.CENTER_LEFT);
            line.setMinWidth(0);
            if (pin != null) {
                line.getChildren().add(pin);
            }
            line.getChildren().add(title);
            if (!queueGlyph.isEmpty()) {
                Label badge = new Label(queueGlyph);
                badge.setMinWidth(Region.USE_PREF_SIZE);
                badge.getStyleClass().add(Styles.TEXT_MUTED);
                Tooltip.install(badge, new Tooltip(queued == 1
                        ? "1 queued message" : queued + " queued messages"));
                line.getChildren().add(badge);
            }
            titleLine = line;
        }

        VBox text = new VBox(1, titleLine);
        // While the remote half is still being set up, the row's second line is
        // the creation progress instead of the config subtitle: the task file
        // exists from the first moment (so the row is real and keeps the typed
        // description even if the remote fails), but its tmux window and Claude
        // session arrive seconds later. [impl->dsn~task-create-progress~5]
        Creation creating = creationOf.apply(task);
        if (creating != null) {
            ProgressBar bar = new ProgressBar();
            bar.progressProperty().bind(creating.progressProperty());
            bar.setPrefWidth(80);
            bar.setMinWidth(Region.USE_PREF_SIZE);
            Label step = new Label();
            step.textProperty().bind(creating.stepProperty());
            step.setMinWidth(0);
            step.getStyleClass().addAll(Styles.TEXT_MUTED, ROW_DETAIL);
            HBox progressLine = new HBox(6, bar, step);
            progressLine.setAlignment(Pos.CENTER_LEFT);
            progressLine.setMinWidth(0);
            text.getChildren().add(progressLine);
            text.setMinWidth(0);
            text.setPrefWidth(0);
            text.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(8, dot, text);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(ROW_PADDING);
            return row;
        }
        // Second line: the task's tag chips, then the config subtitle. Keeping
        // the chips off the title line means the title never has to compete
        // with the fixed-width chips for space in a narrow pane. While a filter
        // is active, rowTags yields only the selected tags (a filtered view
        // hides a task's other labels).
        // [impl->dsn~task-tag-filter~2]
        List<String> tags = rowTags.apply(task);
        int slash = task.id().lastIndexOf('/');
        String group = slash < 0 ? "" : task.id().substring(0, slash);
        String summary = configSummary(task, group.isEmpty() ? null : groupPath.apply(group));
        List<Task.UrlEntry> prs = task.prEntries();
        // In label view the surrounding headers are tags, so the row's folder
        // category is invisible — lead the second line with it (muted, with a
        // separator) to keep "where does this task live" answerable at a
        // glance. Root-level tasks have no category and show nothing.
        // [impl->dsn~task-label-grouping~3]
        String category = labelView.getAsBoolean() ? group : "";
        if (!prs.isEmpty() || !category.isEmpty() || !tags.isEmpty() || !summary.isEmpty()) {
            HBox secondLine = new HBox(6);
            secondLine.setAlignment(Pos.CENTER_LEFT);
            secondLine.setMinWidth(0);
            // The task's PRs lead the line directly under the title, each as
            // its number with the state icon, then their `status:` chips.
            // [impl->dsn~pr-state-indicator~3]
            // [impl->dsn~pr-header-line~4]
            int labelChips = 0;
            for (Task.UrlEntry pr : prs) {
                secondLine.getChildren().add(prLink(task, pr));
                PrInfo info = prInfo.apply(pr.url());
                if (info != null) {
                    for (String label : MainWindow.statusLabels(List.copyOf(info.labels().keySet()))) {
                        if (labelChips++ < MainWindow.MAX_PR_LABELS) {
                            Label chip = TagChips.chip(label, info.labels().get(label));
                            chip.setText(MainWindow.statusChipText(pr.url(), label));
                            secondLine.getChildren().add(chip);
                        }
                    }
                }
            }
            if (!category.isEmpty()) {
                Label categoryLabel = new Label(category + " ·");
                categoryLabel.setMinWidth(Region.USE_PREF_SIZE);
                categoryLabel.getStyleClass().addAll(Styles.TEXT_MUTED, ROW_DETAIL);
                secondLine.getChildren().add(categoryLabel);
            }
            for (String tag : tags) {
                secondLine.getChildren().add(tagChip(tag));
            }
            if (!summary.isEmpty()) {
                Label subtitle = new Label(summary);
                subtitle.setMinWidth(0);
                subtitle.getStyleClass().addAll(Styles.TEXT_MUTED, ROW_DETAIL);
                secondLine.getChildren().add(subtitle);
            }
            text.getChildren().add(secondLine);
        }
        // Lets the title/subtitle ellipsize instead of pushing the status
        // label and action icons out of view when the pane is narrowed.
        // prefWidth 0 is load-bearing: the two-line VBox has a height-dependent
        // (content-bias) preferred width that reports a bogus tiny value when the
        // HBox measures it at height -1, so the HBox would *grow* the title past
        // the leftover space and shove the trailing icons off the right edge.
        // Pinning pref to 0 makes the HGrow child take exactly the space left
        // after the fixed icons, so the icons stay right-aligned and in view.
        text.setMinWidth(0);
        text.setPrefWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);

        // The always-visible icons at the row's right edge: the refactoring
        // count, the task's links, its note.
        HBox trailing = new HBox(8);
        trailing.setAlignment(Pos.CENTER_RIGHT);
        trailing.setMinWidth(Region.USE_PREF_SIZE);

        // The background refactoring count (https://github.com/contextswitcher/contextswitcher-private/issues/50), left of the link icons: a
        // circled-count button (the queue badge's glyphs — MADR 0010 rules out
        // other symbol glyphs on Windows, and this one sits with the icons,
        // not after the title) that opens the same AST-diff view as the gutter
        // button. Nothing shows until the poller found at least one
        // refactoring — a permanent 0 on every row would be noise.
        // [impl->dsn~refactoring-analysis-poller~1]
        RefactoringSummary refactoringCount = refactoringSummary.apply(task);
        if (refactoringCount != null && refactoringCount.count() > 0) {
            Button badge = new Button(CircledCount.glyph(refactoringCount.count()));
            badge.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            badge.setMinWidth(Button.USE_PREF_SIZE);
            badge.setFocusTraversable(false);
            badge.setOnMouseClicked(javafx.event.Event::consume);
            badge.setCursor(Cursor.HAND);
            Tooltip.install(badge, new Tooltip(refactoringCount.count() == 1
                    ? "1 refactoring vs the base branch — open the AST-diff view"
                    : refactoringCount.count()
                            + " refactorings vs the base branch — open the AST-diff view"));
            badge.setOnAction(event -> {
                badge.setDisable(true);
                onShowRefactorings.accept(task, () -> badge.setDisable(false));
            });
            trailing.getChildren().add(badge);
        }

        // One always-visible chain link per non-PR `browser.urls` entry: a
        // task's repository or issue page is one click away like its PRs are,
        // instead of being reachable only by switching to the task. Same open
        // path as a PR (focus-or-open tab), same hover (hand cursor, address in
        // the status bar) as every other link icon. Several links collapse
        // into one counted button instead of a row of identical icons, which
        // ate the title's width.
        // [impl->dsn~task-link-icons~2]
        List<Task.UrlEntry> links = task.linkEntries();
        if (links.size() == 1) {
            Task.UrlEntry link = links.getFirst();
            trailing.getChildren().add(urlIconButton(MDIInterface.LINK_VARIANT,
                    "Open " + link.display(), link.display(), () -> onFocusUrl.accept(task, link)));
        } else if (links.size() > 1) {
            trailing.getChildren().add(linksButton(task, links));
        }
        // The task's note, when it carries one. [impl->dsn~task-add-link~3]
        String taskNote = task.note();
        if (taskNote != null) {
            trailing.getChildren().add(urlIconButton(MDITechnology.NOTE_TEXT_OUTLINE, "Open note",
                    taskNote, () -> onOpenTaskNote.accept(task)));
        }

        // The action gutter: shown only while the mouse is on the row, and laid
        // over the row (unmanaged) just left of the always-visible icons, so
        // hovering never reflows or re-truncates the title. One toggle — pause
        // on an active task, play (resume and switch) otherwise, in the accent
        // colour — then add link and the refactoring view, a divider, and
        // delete at the far right, away from the toggle a row is reached for.
        // [impl->dsn~task-row-hover-actions~7]
        boolean active = task.status() == TaskStatus.ACTIVE;
        Button toggle = iconButton(active ? MDIInterface.PAUSE : MDIInterface.PLAY,
                active ? "Suspend…" : "Resume and switch");
        toggle.setId("task-toggle-button");
        toggle.getGraphic().getStyleClass().add("task-toggle-glyph");
        toggle.setOnAction(event -> {
            if (active) {
                onSetStatus.accept(task, TaskStatus.SUSPENDED);
            } else {
                switchTask(task);
            }
        });
        HBox gutter = new HBox(2, toggle);
        // [impl->dsn~task-add-link~3]
        Button addUrl = iconButton(MDIInterface.LINK_PLUS, "Add link…");
        addUrl.setOnAction(event -> onAddUrl.accept(task));
        gutter.getChildren().add(addUrl);
        // The refactoring AST-diff view (https://github.com/contextswitcher/contextswitcher-private/issues/50) — only a task with a remote and
        // a known workspace has something to diff. Disabled for the round-trip
        // (starting the remote JVM takes seconds), the standard async
        // single-shot shape.
        // [impl->dsn~refactoring-web-view~1]
        if (task.remote() != null && task.claude() != null) {
            Button refactorings = iconButton(MDITechnology.FILE_COMPARE,
                    "Refactorings vs main — AST-diff web view");
            refactorings.setOnAction(event -> {
                refactorings.setDisable(true);
                onShowRefactorings.accept(task, () -> refactorings.setDisable(false));
            });
            gutter.getChildren().add(refactorings);
        }
        javafx.scene.control.Separator divider =
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL);
        Button delete = iconButton(MDIInterface.TRASH_CAN_OUTLINE, "Delete task…");
        delete.setId("task-delete-button");
        delete.setOnAction(event -> onDelete.accept(task));
        gutter.getChildren().addAll(divider, delete);
        gutter.setAlignment(Pos.CENTER);
        gutter.getStyleClass().add("task-action-gutter");
        gutter.setManaged(false);
        gutter.visibleProperty().bind(hoverProperty());

        HBox row = new HBox(8, dot, text, trailing) {
            @Override
            protected void layoutChildren() {
                super.layoutChildren();
                // Full row height: a shorter gutter left the tops and bottoms
                // of the title's and second line's glyphs peeking out around it.
                double width = snapSizeX(gutter.prefWidth(-1));
                // Flush with the row's right edge when nothing trails, so the
                // delete button lines up with the category header's last button.
                double right = trailing.getChildren().isEmpty()
                        ? getWidth() - snappedRightInset()
                        : trailing.getLayoutX() - getSpacing();
                gutter.resizeRelocate(snapPositionX(right - width), 0, width, getHeight());
            }
        };
        row.getChildren().add(gutter);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(ROW_PADDING);
        // A completed task lives in the bottom "Done" section; its grayer
        // background is painted on the cell (DONE_CELL, full width), so the row
        // itself stays transparent.
        // [impl->dsn~done-section~1]
        // Task rows are draggable onto group headers, "Add task…" rows, or
        // other task rows (drag source: the cell, see the constructor) — the
        // drop moves the task file into the target's folder (a root-level row
        // moves it out of its folder).
        // [impl->dsn~task-move-dnd~6]
        installDropTarget(row, slash < 0 ? "" : task.id().substring(0, slash));
        return row;
    }

    /// A flat icon button for a row. Keyboard focus skips it — the context
    /// menu stays the keyboard path — and a click does not bubble to the
    /// cell (group headers toggle on click). Icons are Material Design SVG
    /// paths rendered by SvgNode (MADR 0010) — unicode symbol glyphs (⏸/🗑)
    /// render as empty boxes in JavaFX on Windows.
    private Button iconButton(SVG icon, String tooltip) {
        // Its fill is `.svg-node` in main.css: the theme's muted foreground,
        // so the icon reads on a hovered row in every theme.
        SvgNode svg = new SvgNode(icon.path(), 14);
        Button button = new Button(null, svg);
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        button.setMinWidth(Button.USE_PREF_SIZE);
        button.setFocusTraversable(false);
        button.setOnMouseClicked(javafx.event.Event::consume);
        Tooltip.install(button, new Tooltip(tooltip));
        return button;
    }

    /// An [#iconButton] that stands for an address — the category header's note
    /// and repository icons, the task rows' note icon. It behaves like the PR
    /// icons: a hand cursor marks it as leading somewhere, and hovering names
    /// the address in the status bar, which the row has no room for and the
    /// tooltip only reveals after its delay.
    /// `url` is the address as last read from disk (what the hover shows);
    /// `onOpen` re-resolves it at click time, so an unsaved config edit cannot
    /// send the click to a stale address.
    // [impl->dsn~group-repo-open~2]
    // [impl->dsn~group-note-open~5]
    // [impl->dsn~task-add-link~3]
    /// The aggregated link button for a task carrying several non-PR URLs: one
    /// chain link with a circled count, whose click opens a menu with one
    /// submenu per link — each holding the same `Open`/`Copy URL` items a PR
    /// icon's context menu offers. A row of identical chain links took the
    /// whole width of the title, and told nothing apart until hovered.
    // [impl->dsn~task-link-icons~2]
    private Button linksButton(Task task, List<Task.UrlEntry> links) {
        Button button = iconButton(MDIInterface.LINK_VARIANT, links.size() + " links");
        button.setText(CircledCount.glyph(links.size()));
        button.setCursor(Cursor.HAND);
        ContextMenu menu = new ContextMenu();
        for (Task.UrlEntry link : links) {
            MenuItem open = new MenuItem("Open");
            open.setOnAction(event -> onFocusUrl.accept(task, link));
            // The bare URL, never the titled display form — this goes to the
            // clipboard to be pasted somewhere as a link.
            MenuItem copyUrl = new MenuItem("Copy URL");
            copyUrl.setOnAction(event -> {
                ClipboardContent content = new ClipboardContent();
                content.putString(link.url());
                Clipboard.getSystemClipboard().setContent(content);
            });
            menu.getItems().add(new Menu(link.display(), null, open, copyUrl));
        }
        button.setOnAction(event -> menu.show(button, Side.BOTTOM, 0, 0));
        // Right-click gets the same menu rather than the row's own.
        button.setContextMenu(menu);
        return button;
    }

    private Button urlIconButton(SVG icon, String tooltip, String url, Runnable onOpen) {
        Button button = iconButton(icon, tooltip);
        button.setCursor(Cursor.HAND);
        button.setOnMouseEntered(event -> onHoverUrl.accept(url));
        button.setOnMouseExited(event -> onHoverUrl.accept(null));
        button.setOnAction(event -> onOpen.run());
        return button;
    }

    /// [#iconButton], but visible only while the mouse hovers the row. The
    /// node stays managed, so the layout never shifts on hover; invisible
    /// also means unclickable (visibility requires the hover).
    // [impl->dsn~task-row-hover-actions~7]
    private Button hoverIconButton(SVG icon, String tooltip) {
        Button button = iconButton(icon, tooltip);
        button.visibleProperty().bind(hoverProperty());
        return button;
    }

    /// A small rounded colored chip for a tag: the palette color for the tag,
    /// or a muted gray when the tag is not in the configured palette
    /// ([TagChips]).
    // [impl->dsn~task-tag-filter~2]
    private Label tagChip(String name) {
        return TagChips.chip(name, tagColor.apply(name));
    }

    /// A small dot showing the window's live self-reported state (the
    /// `@cs_status` tmux option): orange while working, red whenever the
    /// session needs the user — waiting for input, asking a question or a
    /// permission (attention), or stopped at its usage limit
    /// (`dsn~claude-limit-detection~2`) — and muted when done. The dot keeps
    /// its slot (transparent) when the status is unknown so titles stay
    /// aligned.
    // [impl->dsn~task-running-indicator~7]
    // [impl->dsn~claude-limit-detection~2]
    private static Label runningDot(@Nullable String status) {
        Label dot = new Label("●");
        dot.setMinWidth(10);
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        // Traffic-light semantics, colours from claude-semaphore (MIT,
        // github.com/TaulantSela/claude-semaphore; named in main.css): anything
        // that needs the user is the one vivid red, work in progress is orange,
        // nothing else competes for attention. The tooltip tells the three reds
        // apart. No state class leaves the dot transparent.
        dot.getStyleClass().add("running-dot");
        @Nullable String state = switch (normalized) {
            case "working" -> "running-dot-working";
            case "waiting", "attention", "limit" -> "running-dot-needs-you";
            case "done" -> "running-dot-done";
            default -> null;
        };
        if (state != null) {
            dot.getStyleClass().add(state);
        }
        if (status != null && !status.isBlank()) {
            Tooltip.install(dot, new Tooltip("limit".equals(normalized)
                    ? "usage limit reached — pay attention" : status));
        }
        return dot;
    }

    /// A small muted glyph for a row: the leading status glyph of a suspended
    /// (pause) or done (check) task, in the same tone the old "done" dot used
    /// and sized to sit in the dot's slot so titles stay aligned with the
    /// running-dot rows — and the pin of a pinned task.
    // [impl->dsn~task-running-indicator~7]
    // [impl->dsn~pinned-tasks~2]
    private static javafx.scene.Node statusGlyph(SVG icon, String tooltip) {
        SvgNode svg = new SvgNode(icon.path(), 12);
        svg.getStyleClass().add("status-glyph");
        Tooltip.install(svg, new Tooltip(tooltip));
        return svg;
    }

    /// One PR on a task row's second line: its number (`#123`, GitLab's `!45`)
    /// after the state icon. Until the state is loaded it is the number alone,
    /// shaped like a tag chip without the chip's colour. The whole box is the
    /// hit target (an [SvgNode] picks on its thin strokes only): a click
    /// focuses or opens the PR's tab, and its context menu offers opening and
    /// copying *this* PR's URL, consuming the event so the row's own menu does
    /// not also pop up.
    // [impl->dsn~pr-state-indicator~3]
    private HBox prLink(Task task, Task.UrlEntry pr) {
        PrInfo info = prInfo.apply(pr.url());
        String number = MainWindow.prNumber(pr.url());
        Label text = new Label(number == null ? "PR" : number);
        text.getStyleClass().add("pr-chip");
        text.setMinWidth(Region.USE_PREF_SIZE);
        HBox link = new HBox(2);
        link.setId("task-pr-link");
        if (info == null) {
            Tooltip.install(text, new Tooltip("PR — state not loaded yet\n" + pr.display()));
        } else {
            text.getStyleClass().add("pr-chip-resolved");
            link.getChildren().add(prStateIcon(info.state(), pr.display()));
        }
        link.getChildren().add(text);
        link.setAlignment(Pos.CENTER_LEFT);
        link.setMinWidth(Region.USE_PREF_SIZE);
        link.setPickOnBounds(true);
        link.setCursor(Cursor.HAND);
        // The URL has no room on the row, so hovering names it in the status
        // bar — the tooltip carries it too, but only after its delay.
        link.setOnMouseEntered(event -> onHoverUrl.accept(pr.display()));
        link.setOnMouseExited(event -> onHoverUrl.accept(null));
        link.setOnMouseClicked(event -> {
            // Primary button only: a right-click just opens the context menu
            // and must not also focus/open the PR.
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            onFocusUrl.accept(task, pr);
            event.consume();
        });
        MenuItem openPr = new MenuItem("Open PR");
        openPr.setOnAction(event -> onFocusUrl.accept(task, pr));
        // The bare URL, never the titled display form — this goes to the
        // clipboard to be pasted somewhere as a link.
        MenuItem copyUrl = new MenuItem("Copy URL");
        copyUrl.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(pr.url());
            Clipboard.getSystemClipboard().setContent(content);
        });
        ContextMenu prMenu = new ContextMenu(openPr, copyUrl);
        link.setOnContextMenuRequested(event -> {
            prMenu.show(link, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        return link;
    }

    /// A small colored PR-state icon (https://github.com/contextswitcher/contextswitcher-private/issues/49): open = green pull, draft = muted
    /// pull, merged = accent merge, closed = red pull (same PR glyph, red — it
    /// was closed without merging, not an error). When the state is not (yet)
    /// resolved — `gh` missing, offline, or the first poll pending — a muted
    /// `?` glyph shows instead of nothing, so the PR is never invisible. The
    /// tooltip names the state and shows the PR (title and URL, as `display`).
    // [impl->dsn~pr-state-indicator~3]
    static javafx.scene.Node prStateIcon(@Nullable PrState state, String display) {
        SVG icon = state == null ? MDIInterface.HELP_CIRCLE_OUTLINE : switch (state) {
            case OPEN, DRAFT, CLOSED -> MDITechnology.SOURCE_PULL;
            case MERGED -> MDITechnology.SOURCE_MERGE;
        };
        // `pr-open`, `pr-draft`, `pr-merged`, `pr-closed`, `pr-unknown` (main.css).
        String stateClass = state == null ? "pr-unknown" : "pr-" + state.name().toLowerCase(Locale.ROOT);
        String label = state == null
                ? "PR — state unknown (is gh or glab installed and authenticated?)"
                : switch (state) {
                    case OPEN -> "PR open";
                    case DRAFT -> "PR open (draft)";
                    case MERGED -> "PR merged";
                    case CLOSED -> "PR closed (closed without merging)";
                };
        SvgNode svg = new SvgNode(icon.path(), 12);
        svg.getStyleClass().add(stateClass);
        Tooltip.install(svg, new Tooltip(label + "\n" + display));
        return svg;
    }

    /// One line telling what a switch will touch: host, tmux target, IntelliJ project, URL.
    /// Paths under `root` — the category's workspace directory, shown on its
    /// header — are shortened to the part below it.
    // [impl->dsn~category-header-path~1]
    static String configSummary(Task task, @Nullable String root) {
        List<String> parts = new ArrayList<>();
        // A suspended task leads with *when* — a pile of paused tasks is
        // sorted through by age. [impl->dsn~suspended-timestamp~1]
        if (task.status() == TaskStatus.SUSPENDED && task.suspendedAt() != null) {
            parts.add("⏸ " + task.suspendedAt());
        }
        if (task.remote() != null) {
            parts.add(task.remote());
        }
        if (task.tmux() != null) {
            parts.add("tmux " + task.tmux().session()
                    + (task.tmux().window() == null ? "" : ":" + task.tmux().window()));
        }
        if (task.intellij() != null) {
            // Resolved path (explicit, else claude workspace/cwd); a bare
            // intellij: section without any resolvable path shows plain.
            String projectPath = task.intellijProjectPath();
            parts.add(projectPath == null ? "IntelliJ" : "IntelliJ " + WorkspacePaths.belowRoot(projectPath, root));
        }
        if (task.browser() != null && !task.browser().entries().isEmpty()) {
            // Only a human title, never the bare URL: a URL is far too long for
            // the line and, depending on how much width the other parts take,
            // was either truncated to uselessness or pushed out of view
            // entirely. The PR icons carry the URLs now — hovering one names it
            // in the status bar, and its menu copies it.
            String title = task.browser().entries().getFirst().title();
            if (title != null) {
                parts.add(title);
            }
        }
        for (String folder : task.folders()) {
            parts.add("📁 " + folder);
        }
        if (task.claude() != null && task.claude().workspace() != null) {
            parts.add("wd " + WorkspacePaths.belowRoot(task.claude().workspace(), root));
        }
        return String.join("  ·  ", parts);
    }

    /// A corrupt / unparseable task file: the red error text plus a trash
    /// button (and matching context menu) that deletes the offending file
    /// outright — the row carries no [Task], so the normal kill dialog does
    /// not apply; a broken frontmatter file has nothing worth keeping.
    // [impl->dsn~corrupt-task-delete~2]
    private HBox errorRow(TaskEntry.Failed failed) {
        Label marker = new Label("⚠");
        Label text = new Label("%s: %s".formatted(failed.fileName(), failed.message()));
        text.getStyleClass().add(Styles.DANGER);
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        Tooltip.install(text, new Tooltip(failed.message()));

        Button delete = hoverIconButton(MDIInterface.TRASH_CAN_OUTLINE, "Delete file…");
        delete.setOnAction(event -> onDeleteFile.accept(failed.fileName()));
        // Hidden (un-hovered) button collapses to zero width, so the error text
        // uses the full row and no permanent gap sits at the right edge.
        delete.managedProperty().bind(delete.visibleProperty());

        MenuItem copyPath = new MenuItem("Copy file path");
        copyPath.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(taskFilePath.apply(failed.fileName()));
            Clipboard.getSystemClipboard().setContent(content);
        });
        MenuItem deleteItem = new MenuItem("Delete file…");
        deleteItem.setOnAction(event -> onDeleteFile.accept(failed.fileName()));
        setContextMenu(new ContextMenu(copyPath, deleteItem));

        HBox row = new HBox(8, marker, text, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(ROW_PADDING);
        return row;
    }
}
