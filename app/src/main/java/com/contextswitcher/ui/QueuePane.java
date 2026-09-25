package com.contextswitcher.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.imageio.ImageIO;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.contextswitcher.queue.Attachments;
import com.contextswitcher.queue.QodoImported;
import com.contextswitcher.queue.QodoReconcile;
import com.contextswitcher.queue.QueueFile;
import com.contextswitcher.discovery.TmuxStatusPoller;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import com.contextswitcher.queue.SendResult;
import com.contextswitcher.terminal.ChatRoute;
import com.contextswitcher.terminal.TmuxHost;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;
import tools.maran.svg.materialdesign.MDIInterface;
import tools.maran.svgnode.SvgNode;

/// The per-task message queue below the task-file editor: messages typed
/// ahead for the task's Claude chat. Top-down: the last sent message, the
/// status line, the queued messages oldest-first, and the empty **add** box
/// directly under the last of them — **every** box carries a
/// send button that pastes exactly that message into the task's tmux
/// window (the top box is the oldest, the natural next one, but any
/// message can jump the queue). Boxes are edited in place (saved on focus loss, like the
/// editor lane, or by three Ctrl+Enters, which save and send that message),
/// deleted via the 🗑 shown on hover, and reordered by dragging their ≡
/// handle. In any box Shift+Enter inserts a newline (chat-app muscle
/// memory), never committing. The add box also submits on three plain Enters in a row (a
/// message followed by two empty lines), for people who never reach for
/// the chord. Ctrl+V with an image on the clipboard stores it locally and
/// inserts an `[image: …]` marker that the send rewrites to the uploaded
/// remote path; files dragged from the OS onto a box are stored the same
/// way and inserted as `[file: …]` markers.
///
/// Below the last-sent box sits the row of **quick message** buttons: one
/// click copies that text into the add box ("Go" and whatever else one keeps
/// retyping), ready to be extended before it is queued; `…` opens the editor that adds,
/// edits and removes them, and a button's right-click menu removes it.
// [impl->dsn~message-queue-ui~26]
// [impl->dsn~message-queue-store~2]
public class QueuePane {

    /// Delivers one message to a task's chat, however it is reached
    /// ([ChatRoute]); blocking, called on the background executor.
    /// `progress` takes status lines while it runs (attachment uploads take
    /// minutes); a sender that reports any ends with the outcome line.
    public interface Sender {
        SendResult send(Task task, String text, Consumer<String> progress);
    }

    /// Fetches a task's current **open** review-comment messages (qodo agent
    /// prompts and human reviewers' comments alike, each with its comment URL)
    /// for a manual "Review comments sync"; blocking, called on the background
    /// executor. Null = the fetch failed (GitHub unreachable), so the queue is
    /// left untouched — distinct from an empty map, which means the PR has no
    /// open review comment and any leftover review-sourced message in the queue
    /// is done.
    public interface QodoPrompts {
        @Nullable Map<String, String> current(Task task);
    }

    private static final String SYNC_TOOLTIP =
            "Pull the PR's open review comments — qodo's suggestions and what "
                    + "reviewers wrote — into the queue and drop the ones that are gone";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    /// Ctrl+Enter, three in a row, acts on the box under the caret without
    /// leaving the keyboard.
    private static final KeyCombination COMMIT_KEYS =
            new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN);

    /// Shift+Enter inserts a newline (chat-app muscle memory); a plain
    /// TextArea does not bind it, so we do it ourselves.
    private static final KeyCombination NEWLINE_KEYS =
            new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHIFT_DOWN);

    /// The style classes a box wears while a Ctrl+Enter chord runs, one per
    /// press *before* the last: the box greens up a shade at a time, so the
    /// chord that ends in a send is visible before it fires. The shades are
    /// theme colours (`-color-success-*` in `main.css`), so Nord and
    /// Everforest each land on their own green. The firing press wears none —
    /// it empties the box, and green on a box waiting for the next message
    /// reads as the box itself being coloured.
    private static final List<String> CHORD_STEPS =
            List.of("queue-chord-1", "queue-chord-2");

    /// Presses that make a chord: the shades above plus the one that acts.
    private static final int CHORD_PRESSES = CHORD_STEPS.size() + 1;

    private final Path queuesDir;
    private final Path qodoDir;
    private final Path attachmentsDir;
    private final Sender sender;
    private final QodoPrompts qodoFetcher;
    /// Opens (or focuses) a URL in the browser via the extension — the qodo
    /// link button's back half, the same path the PR icon uses. Carries the
    /// shown task so a tab that must be *opened* lands on the task's category
    /// desktop, exactly as the PR icon does.
    // [impl->dsn~pr-open-on-category-desktop~3]
    private final BiConsumer<@Nullable Task, String> onFocusUrlInBrowser;
    /// Notified with the task and text whenever a message is queued (the add
    /// box commits), so a URL mentioned in it also becomes a browser tab —
    /// the same treatment `TaskFileParser.addUrlsFrom` gives a task
    /// description. FX thread, like every queue write.
    // [impl->dsn~task-url-collect~1]
    private final BiConsumer<Task, String> onMessageQueued;
    /// Resumes a suspended task (status back to active, window recreated) —
    /// a send into a suspended chat first brings the chat back.
    // [impl->dsn~message-queue-resume-send~2]
    private final Consumer<Task> onResume;
    /// The current file state of a task by id, or null when gone: an armed
    /// message carries the task as it was armed, but the resurrect writes a
    /// new window id the delivery must target.
    // [impl->dsn~message-queue-resume-send~2]
    private final Function<String, @Nullable Task> taskById;
    private final Executor executor;
    /// Notified after every queue write, so the task list can refresh its
    /// per-row queued-count badge. Runs on the FX thread (all saves do).
    // [impl->dsn~message-queue-count-badge~1]
    private final Runnable onQueueChanged;

    private final VBox cards = new VBox(6);
    /// The add row's send button of the moment — replaced on every rebuild
    /// (its enabled state follows the shown task), so the Ctrl+Enter chord
    /// fires the current one rather than a stale button.
    private Button addSend = new Button();
    private final Label status = new Label();
    private final TextArea addBox = new TextArea();
    /// The message most recently sent to this task's chat, read-only below
    /// the queue: switching back to a task recalls what its chat was last
    /// asked to do. Collapsed until the task has a recorded send.
    // [impl->dsn~last-sent-message~5]
    private final TextArea lastSent = new TextArea();
    /// The sent messages shown above [#lastSent], oldest at the top: the
    /// task's earlier sends, out of sight until the user scrolls up in
    /// [#sentScroll].
    // [impl->dsn~last-sent-message~5]
    private final VBox sentStack = new VBox(6);
    /// Scrolls [#sentStack]; its viewport is exactly as tall as [#lastSent],
    /// so the last sent message is what one sees and the older ones are one
    /// scroll up.
    // [impl->dsn~last-sent-message~5]
    private final ScrollPane sentScroll = new ScrollPane(sentStack);
    private final Label lastSentTitle = new Label("Last sent");
    /// The one-click quick messages, rebuilt from [#quick] whenever the shown
    /// task changes what the buttons may do.
    // [impl->dsn~quick-message-buttons~7]
    private final HBox quickRow = new HBox(4);
    /// The quick messages themselves, shared by all tasks and persisted in
    /// [QueueFile#quickFile].
    // [impl->dsn~quick-message-buttons~7]
    private final List<String> quick;
    private final Button reviewSyncButton = new Button("Review comments sync");
    private final BorderPane root = new BorderPane();

    private @Nullable Task task;
    /// The id the shown task's file was just renamed to ([#taskRenamed]),
    /// consumed by the very next [#showTask] so the rename does not read as a
    /// task switch. Null whenever no rename is in flight.
    // [impl->dsn~message-queue-ui~26]
    private @Nullable String renamedTo;
    /// Index 0 = oldest = top box = next to send.
    private List<String> messages = new ArrayList<>();
    /// The shown task's qodo-sourced prompts and their review-comment URLs
    /// ([QodoImported]): a queued message found here gets the link button.
    private Map<String, String> qodoUrls = Map.of();
    /// The messages the user ticked as **read**: they move out of the stack
    /// into the collapsed "Read" section, still editable and sendable from
    /// there. Persisted per task beside the queue ([QueueFile#readFile]).
    // [impl->dsn~message-queue-read-mark~1]
    private Set<String> read = new LinkedHashSet<>();
    /// Whether the "Read" section is open — kept across rebuilds, so ticking a
    /// box inside it does not fold it shut under the user's hands.
    private boolean readExpanded;
    /// The text areas currently shown, in [#messages] order (index-aligned).
    private final List<TextArea> areas = new ArrayList<>();
    /// The messages armed for a **delayed** send, per task id, in the order
    /// they go out (queue order, ➊ first): one per idle turn, so an idle chat
    /// still never gets a burst. Keyed by id alone: the send may fire while
    /// the pane shows another task, and [#taskById] supplies it fresh.
    /// Mirrored to [#armedFile] on every change, so a restart keeps them.
    // [impl->dsn~message-queue-delayed-send~4]
    private final Map<String, List<String>> armed = new HashMap<>();
    private final Path armedFile;

    /// Task ids that got a delayed message and have not been reported busy
    /// since: the status poll may still be carrying the pre-paste `waiting`,
    /// and without this the next armed message would go out on top of it.
    // [impl->dsn~message-queue-delayed-send~4]
    private final Set<String> justSent = new HashSet<>();

    /// A task's armed messages, empty when it has none.
    private List<String> armedFor(String taskId) {
        return armed.getOrDefault(taskId, List.of());
    }

    /// The 1-based place of the message at `index` in the shown task's armed
    /// queue, or 0 when that message is not armed.
    // [impl->dsn~message-queue-delayed-send~4]
    private int armedRank(int index) {
        if (task == null || index < 0 || index >= messages.size()) {
            return 0;
        }
        String text = messages.get(index);
        return armedFor(task.id()).indexOf(text) + 1;
    }

    /// The statuses that mean "Claude is not working right now", as reported
    /// by the status poll — a `limit` or `working` window keeps waiting.
    // [impl->dsn~message-queue-delayed-send~4]
    private static final Set<String> IDLE_STATUSES = Set.of("waiting", "done");

    public QueuePane(Path queuesDir, Path qodoDir, Path attachmentsDir, Path armedFile, Sender sender,
            QodoPrompts qodoFetcher, BiConsumer<@Nullable Task, String> onFocusUrlInBrowser,
            BiConsumer<Task, String> onMessageQueued, Executor executor,
            Runnable onQueueChanged, Consumer<Task> onResume, Function<String, @Nullable Task> taskById) {
        this.queuesDir = queuesDir;
        this.armedFile = armedFile;
        QueueFile.loadArmed(armedFile).forEach((id, texts) -> armed.put(id, new ArrayList<>(texts)));
        this.onResume = onResume;
        this.taskById = taskById;
        this.qodoDir = qodoDir;
        this.attachmentsDir = attachmentsDir;
        this.sender = sender;
        this.qodoFetcher = qodoFetcher;
        this.onMessageQueued = onMessageQueued;
        this.onFocusUrlInBrowser = onFocusUrlInBrowser;
        this.executor = executor;
        this.onQueueChanged = onQueueChanged;
        // An empty list means "no file yet" — saving the last button away
        // deletes it — and "Go" is the message everybody retypes, so that is
        // what a fresh install (and an emptied row) offers.
        // [impl->dsn~quick-message-buttons~7]
        List<String> stored = QueueFile.load(QueueFile.quickFile(queuesDir));
        this.quick = new ArrayList<>(stored.isEmpty() ? List.of("Go") : stored);

        addBox.setPromptText("Queue a message for this chat… (stored when you click elsewhere)");
        addBox.setWrapText(true);
        addBox.setPrefRowCount(2);
        installAttachments(addBox);
        addBox.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) {
                resumeIfSuspended();
            } else {
                commitAddBox();
            }
        });
        // Three plain Enters in a row (message + two blank lines) store the
        // message and keep the caret in the emptied add box, ready for the
        // next one; three Ctrl+Enters store it and send it right away — the
        // add row's send button without leaving the keyboard.
        installComposeKeys(addBox, () -> {
            commitAddBox();
            addBox.requestFocus();
        }, () -> {
            // Queue first, send second: the send button is disabled on a task
            // without remote+tmux, and a chord that cannot send must still not
            // swallow the message — but it has to say so, or the chord looks
            // like it only ever added a card.
            commitAddBox();
            if (addSend.isDisable()) {
                status.setText("Queued — this task has no tmux window to send to.");
            } else {
                addSend.fire();
            }
            addBox.requestFocus();
        });

        cards.setPadding(new Insets(6));
        ScrollPane scroll = new ScrollPane(cards);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        status.getStyleClass().add(Styles.TEXT_MUTED);
        status.setPadding(new Insets(2, 6, 2, 6));
        // Empty most of the time, and an empty Label still claims a line's
        // height — which pushed the picker row up off the terminal pane's
        // bottom bar. Collapsed when it has nothing to say, so the picker row
        // is the pane's real bottom row and the two bars line up.
        status.managedProperty().bind(status.textProperty().isNotEmpty());
        status.visibleProperty().bind(status.managedProperty());

        reviewSyncButton.getStyleClass().add(Styles.SMALL);
        reviewSyncButton.setFocusTraversable(false);
        reviewSyncButton.setTooltip(new Tooltip(SYNC_TOOLTIP));
        reviewSyncButton.setOnAction(event -> syncReviewComments());
        HBox topBar = new HBox(reviewSyncButton);
        topBar.setAlignment(Pos.CENTER_RIGHT);
        topBar.setPadding(new Insets(4, 6, 4, 6));
        // The Queue pane's toolbar: the height and band of every pane toolbar
        // (main.css). [impl->dsn~shell-layout~2]
        topBar.getStyleClass().add("panel-header");

        // [impl->dsn~last-sent-message~5]
        lastSent.setEditable(false);
        lastSent.setWrapText(true);
        lastSent.setPrefRowCount(3);
        lastSent.setFocusTraversable(false);
        // The different background separates the sent record from the
        // editable queue boxes above (rule in main.css — AtlantaFX paints
        // the text area's .content, so an inline style would not take).
        lastSent.getStyleClass().add("queue-last-sent");
        // The recorded text keeps its `[image: …]` markers — hovering the box
        // shows the pictures the message went out with.
        // [impl->dsn~attachment-image-hover~2]
        AttachmentPreview.install(lastSent, attachmentsDir);
        // [impl->dsn~last-sent-message~5]
        installSentMenu(lastSent);
        lastSentTitle.getStyleClass().add(Styles.TEXT_MUTED);
        // The older sends live above the last one in a viewport exactly one
        // last-sent box tall: nothing changes visually while a task has only
        // one recorded send, and scrolling up walks back through the history.
        // [impl->dsn~last-sent-message~5]
        sentStack.getChildren().add(lastSent);
        sentScroll.setFitToWidth(true);
        sentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sentScroll.prefViewportHeightProperty().bind(lastSent.heightProperty());
        VBox lastSentBox = new VBox(2, lastSentTitle, sentScroll);
        lastSentBox.setPadding(new Insets(4, 6, 0, 6));
        lastSentBox.managedProperty().bind(lastSent.textProperty().isNotEmpty());
        lastSentBox.visibleProperty().bind(lastSentBox.managedProperty());

        // Top-down by how urgently it wants the eye: what was last sent, the
        // status line, the queue, and the add box last — right above the
        // keyboard hand, chat-app style.
        quickRow.setPadding(new Insets(4, 6, 0, 6));
        quickRow.setAlignment(Pos.CENTER_LEFT);
        root.setTop(new VBox(topBar, lastSentBox, quickRow, status));
        root.setCenter(scroll);
        root.setBottom(null);
        root.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) {
                addBox.requestFocus();
            }
        });
        showTask(null);
    }

    /// The pane's node. Focusing it focuses the add box — how the ShellFX
    /// host's queue tab hands over the focus it gets.
    public Node getRoot() {
        return root;
    }

    /// The shown task's file was renamed (`fromId` -> `toId`), so the id it is
    /// keyed by changes under it. Called by [MainWindow#taskRenamed] **before**
    /// the renamed task is selected; the next [#showTask] then takes the
    /// "same task" path instead of reading the new id as a switch.
    ///
    /// Without this, an adopted published title — which renames a task's file
    /// seconds after it was created, while the user is typing the first
    /// message for it — committed the half-typed add box to the **old** id and
    /// reloaded the new id's empty queue: the box went empty, no card
    /// appeared, and the message ended up in a queue file no task points at.
    // [impl->dsn~message-queue-ui~26]
    public void taskRenamed(String fromId, String toId) {
        // The armed queue is keyed by id too: it follows, or it waits forever
        // for a task that no longer exists under the old id.
        // [impl->dsn~message-queue-delayed-send~4]
        List<String> pending = armed.remove(fromId);
        if (pending != null) {
            armed.put(toId, pending);
            saveArmed();
        }
        Task shown = this.task;
        if (shown != null && shown.id().equals(fromId)) {
            renamedTo = toId;
        }
    }

    /// Switches the pane to `task` (null = no selection): pending edits of
    /// the previous task are committed to its queue file first.
    ///
    /// Re-showing the task already shown (a background list rebuild
    /// re-applying the selection, `refreshPreview` after a switch/suspend)
    /// must not disturb typing: committing here would turn a half-typed add
    /// box into a queued message, and rebuilding the cards steals the
    /// keyboard focus. So it only takes over the fresh task data — this pane
    /// is the sole writer of the queue files, its in-memory state stays
    /// authoritative — and rebuilds only when the fresh data changes what the
    /// buttons may do (a resumed task regained its tmux window).
    ///
    /// A task whose file was just renamed ([#taskRenamed]) is the same task
    /// too, under its new id: the in-memory queue is authoritative and
    /// `save()` writes it under the id `this.task` now carries, which is the
    /// new one — and `QueueFile.rename` has already moved the file there.
    public void showTask(@Nullable Task newTask) {
        Task shown = this.task;
        // Consumed by this call whatever it decides, so a rename can never
        // make a *later*, genuine switch look like the same task.
        String renamed = renamedTo;
        renamedTo = null;
        if (newTask != null && shown != null
                && (shown.id().equals(newTask.id()) || newTask.id().equals(renamed))) {
            this.task = newTask;
            reviewSyncButton.setDisable(newTask.prUrl() == null);
            boolean wasSendable = ChatRoute.of(shown).canSend();
            boolean sendable = ChatRoute.of(newTask).canSend();
            if (wasSendable != sendable) {
                commitEdits();
                rebuild();
            }
            return;
        }
        commitEdits();
        commitAddBox();
        this.task = newTask;
        messages = newTask == null
                ? new ArrayList<>()
                : new ArrayList<>(QueueFile.load(QueueFile.file(queuesDir, newTask.id())));
        qodoUrls = newTask == null
                ? Map.of()
                : QodoImported.load(QodoImported.file(qodoDir, newTask.id()));
        // [impl->dsn~message-queue-read-mark~1]
        read = newTask == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(QueueFile.load(QueueFile.readFile(queuesDir, newTask.id())));
        showSent();
        status.setText("");
        boolean canSync = newTask != null && newTask.prUrl() != null;
        reviewSyncButton.setDisable(!canSync);
        reviewSyncButton.setTooltip(new Tooltip(canSync
                ? SYNC_TOOLTIP
                : "This task has no PR to sync review comments from"));
        rebuild();
    }

    /// Fills the sent area from disk: the last sent message in [#lastSent]
    /// and the earlier ones above it, then parks the viewport at the bottom so
    /// the last send is what the pane shows. Re-read after every send — the
    /// files are tiny and local, like the queue itself.
    // [impl->dsn~last-sent-message~5]
    private void showSent() {
        String sent = task == null
                ? null
                : QueueFile.loadSent(QueueFile.sentFile(queuesDir, task.id()));
        lastSent.setText(sent == null ? "" : sent);
        List<String> history = task == null
                ? List.of()
                : QueueFile.load(QueueFile.sentHistoryFile(queuesDir, task.id()));
        // The history's last entry is the last sent message itself (unless a
        // send predates the history file), and that one already has its box.
        if (!history.isEmpty() && history.getLast().equals(sent)) {
            history = history.subList(0, history.size() - 1);
        }
        sentStack.getChildren().setAll(history.stream().map(this::sentBox).toList());
        sentStack.getChildren().add(lastSent);
        lastSentTitle.setText(history.isEmpty() ? "Last sent" : "Last sent (scroll up for older)");
        // After layout, or the viewport is not yet tall enough to scroll.
        Platform.runLater(() -> sentScroll.setVvalue(1));
    }

    /// One earlier sent message: like [#lastSent], read-only, with the same
    /// background and the same Resend/Copy menu.
    // [impl->dsn~last-sent-message~5]
    private TextArea sentBox(String text) {
        TextArea box = new TextArea(text);
        box.setEditable(false);
        box.setWrapText(true);
        box.setPrefRowCount(3);
        box.setFocusTraversable(false);
        box.getStyleClass().add("queue-last-sent");
        // [impl->dsn~attachment-image-hover~2]
        AttachmentPreview.install(box, attachmentsDir);
        installSentMenu(box);
        return box;
    }

    /// Right-click on a sent box to send its text again without retyping it.
    /// The menu replaces the TextArea's default one, so it keeps a Copy entry.
    // [impl->dsn~last-sent-message~5]
    private void installSentMenu(TextArea box) {
        MenuItem resendItem = new MenuItem("Resend");
        resendItem.setOnAction(event -> resend(box.getText()));
        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(box.getText());
            Clipboard.getSystemClipboard().setContent(content);
            status.setText("Copied sent message");
        });
        box.setContextMenu(new ContextMenu(resendItem, copyItem));
    }

    /// Fills [#quickRow] for the add box; disabled while no task is shown.
    // [impl->dsn~quick-message-buttons~7]
    private void rebuildQuick() {
        fillQuick(quickRow, addBox, task == null);
    }

    /// A quick message row that copies into `target` instead of the add box —
    /// the Add-task dialog's description field. Editing or removing a message
    /// there refreshes the queue pane's row too, the list being one.
    // [impl->dsn~quick-message-buttons~7]
    public HBox quickRow(TextArea target) {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        fillQuick(row, target, false);
        return row;
    }

    /// Fills `row`: one small button per quick message that copies it into
    /// `target` on a single click (nothing is sent), each removable from its
    /// right-click menu, and a trailing `…` that opens the editor.
    /// The message buttons sit in a strip that `‹` and `›` page through when
    /// they do not all fit.
    // [impl->dsn~quick-message-buttons~7]
    private void fillQuick(HBox row, TextArea target, boolean disabled) {
        Runnable refresh = () -> {
            rebuildQuick();
            if (row != quickRow) {
                fillQuick(row, target, disabled);
            }
        };
        row.getChildren().clear();
        HBox strip = new HBox(4);
        strip.setAlignment(Pos.CENTER_LEFT);
        ScrollPane scroll = new ScrollPane(strip);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add(Tweaks.EDGE_TO_EDGE);
        // As wide as its buttons when they fit, shrinking to what is left when not.
        scroll.prefViewportWidthProperty().bind(strip.widthProperty());
        scroll.setMinViewportWidth(0);
        Button prev = pageButton("‹", "Earlier quick messages", () -> page(scroll, strip, -1));
        Button next = pageButton("›", "Later quick messages", () -> page(scroll, strip, 1));
        Runnable ends = () -> {
            boolean fits = strip.getWidth() <= scroll.getViewportBounds().getWidth() + 1;
            prev.setDisable(fits || scroll.getHvalue() <= scroll.getHmin());
            next.setDisable(fits || scroll.getHvalue() >= scroll.getHmax());
        };
        scroll.hvalueProperty().addListener((obs, was, now) -> ends.run());
        scroll.viewportBoundsProperty().addListener((obs, was, now) -> ends.run());
        strip.widthProperty().addListener((obs, was, now) -> ends.run());
        ends.run();
        row.getChildren().addAll(prev, scroll, next);
        for (String text : quick) {
            Button button = new Button(label(text));
            button.getStyleClass().add(Styles.SMALL);
            // Not focus-traversable, so the click leaves the caret where the
            // target box had it.
            button.setFocusTraversable(false);
            button.setTooltip(new Tooltip(text));
            button.setDisable(disabled);
            button.setOnAction(event -> appendTo(target, text));
            MenuItem remove = new MenuItem("Remove");
            remove.setOnAction(event -> {
                quick.remove(text);
                saveQuick();
                refresh.run();
            });
            button.setContextMenu(new ContextMenu(remove));
            strip.getChildren().add(button);
        }
        Button more = new Button("…");
        more.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        more.setFocusTraversable(false);
        more.setTooltip(new Tooltip("Add, edit and remove quick messages"));
        more.setOnAction(event -> {
            editQuick(row);
            refresh.run();
        });
        row.getChildren().add(more);
    }

    // [impl->dsn~quick-message-buttons~7]
    private static Button pageButton(String label, String tooltip, Runnable action) {
        Button button = new Button(label);
        button.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(event -> action.run());
        return button;
    }

    /// Pages the quick message strip by one viewport — `direction` -1 back,
    /// 1 forward — aligned so the left-most shown button is shown whole.
    // [impl->dsn~quick-message-buttons~7]
    private static void page(ScrollPane scroll, HBox strip, int direction) {
        double viewport = scroll.getViewportBounds().getWidth();
        double range = strip.getWidth() - viewport;
        if (range <= 0) {
            return;
        }
        double left = scroll.getHvalue() * range;
        double wanted = left + direction * viewport;
        double target = direction > 0 ? range : 0;
        for (Node child : strip.getChildren()) {
            Bounds bounds = child.getBoundsInParent();
            if (direction > 0
                    ? bounds.getMinX() > left && bounds.getMaxX() > left + viewport
                    : bounds.getMinX() >= wanted) {
                target = bounds.getMinX();
                break;
            }
        }
        scroll.setHvalue(Math.clamp(target / range, 0, 1));
    }

    /// Appends `text` to `target` — after a blank line when it already holds
    /// something — scrolls it into view, focuses it and puts the caret at its
    /// end, so one can add a URL or more context before queueing it.
    // [impl->dsn~quick-message-buttons~7]
    private static void appendTo(TextArea target, String text) {
        String current = target.getText();
        target.setText(current == null || current.isBlank()
                ? text : current.stripTrailing() + "\n\n" + text);
        target.requestFocus();
        target.end();
        // After layout: the appended text may have grown the box.
        Platform.runLater(() -> scrollIntoView(target));
    }

    /// Scrolls every [ScrollPane] around `node` just far enough to show it —
    /// the add box sits under the last queued card, below the fold of a long
    /// queue.
    static void scrollIntoView(Node node) {
        for (Parent parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (!(parent instanceof ScrollPane scroll) || scroll.getContent() == null) {
                continue;
            }
            Node content = scroll.getContent();
            Bounds box = content.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
            double viewport = scroll.getViewportBounds().getHeight();
            double range = content.getBoundsInLocal().getHeight() - viewport;
            if (range <= 0) {
                continue;
            }
            double top = scroll.getVvalue() * range;
            if (box.getMinY() < top) {
                scroll.setVvalue(box.getMinY() / range);
            } else if (box.getMaxY() > top + viewport) {
                scroll.setVvalue(Math.min(1, (box.getMaxY() - viewport) / range));
            }
        }
    }

    /// A quick message's button and list label: its first line, ellipsized
    /// past 24 characters — the whole text is in the tooltip and in the
    /// editor.
    // [impl->dsn~quick-message-buttons~7]
    private static String label(String text) {
        String line = text.lines().findFirst().orElse("").strip();
        return line.length() > 24 ? line.substring(0, 23) + "…" : line;
    }

    /// The quick message editor behind `…`: the messages as a list on the
    /// left (drag a row to reorder), the selected one's full text on the right,
    /// **Add** and **Remove** below the list. **Save** replaces the row with
    /// what the list holds.
    // [impl->dsn~quick-message-buttons~7]
    private void editQuick(Node owner) {
        ObservableList<String> items = FXCollections.observableArrayList(quick);
        ListView<String> list = new ListView<>(items);
        list.setPrefWidth(180);
        TextArea editor = new TextArea();
        editor.setWrapText(true);
        editor.setPrefColumnCount(40);
        editor.setDisable(true);

        // The editor writes back when the selection leaves the entry it was
        // showing; a live write-back would re-fire the list's own change
        // events under the typing hand.
        int[] shown = {-1};
        Runnable commit = () -> {
            if (shown[0] >= 0 && shown[0] < items.size()
                    && !editor.getText().equals(items.get(shown[0]))) {
                items.set(shown[0], editor.getText());
            }
        };
        list.setCellFactory(view -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(@Nullable String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(item == null || empty ? null : label(item));
                }
            };
            // Reorder by dragging a row onto another (or below the last one);
            // the payload is the row index, like the queue cards' handle.
            cell.setOnDragDetected(event -> {
                if (!cell.isEmpty()) {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(Integer.toString(cell.getIndex()));
                    cell.startDragAndDrop(TransferMode.MOVE).setContent(content);
                }
                event.consume();
            });
            cell.setOnDragOver(event -> {
                if (event.getGestureSource() instanceof ListCell<?> source
                        && source.getListView() == list) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });
            cell.setOnDragDropped(event -> {
                int from = Integer.parseInt(event.getDragboard().getString());
                int to = cell.isEmpty() ? items.size() - 1 : cell.getIndex();
                // Deselect first: the listener below would otherwise write the
                // editor's text back to an index the move just shifted.
                commit.run();
                list.getSelectionModel().clearSelection();
                items.add(to, items.remove(from));
                list.getSelectionModel().select(to);
                event.setDropCompleted(true);
                event.consume();
            });
            return cell;
        });
        list.getSelectionModel().selectedIndexProperty().addListener((obs, was, now) -> {
            commit.run();
            shown[0] = now.intValue();
            editor.setDisable(shown[0] < 0);
            editor.setText(shown[0] < 0 ? "" : items.get(shown[0]));
        });

        Button add = new Button("Add");
        add.getStyleClass().add(Styles.SMALL);
        add.setOnAction(event -> {
            items.add("");
            list.getSelectionModel().selectLast();
            editor.requestFocus();
        });
        Button remove = new Button("Remove");
        remove.getStyleClass().add(Styles.SMALL);
        remove.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(event -> {
            int index = list.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                shown[0] = -1;
                items.remove(index);
            }
        });
        VBox left = new VBox(4, list, new HBox(4, add, remove));
        VBox.setVgrow(list, Priority.ALWAYS);
        // Otherwise the growing editor squeezes the column below its width
        // and the button labels collapse to "…".
        left.setMinWidth(Region.USE_PREF_SIZE);
        HBox content = new HBox(8, left, editor);
        HBox.setHgrow(editor, Priority.ALWAYS);
        content.setPrefHeight(260);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner.getScene() == null ? null : owner.getScene().getWindow());
        dialog.setTitle("Quick messages");
        dialog.setHeaderText("One click copies a message into the queue box or the Add-task description.");
        dialog.setResizable(true);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        list.getSelectionModel().selectFirst();
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != save) {
            return;
        }
        commit.run();
        quick.clear();
        items.stream().map(String::strip).filter(text -> !text.isEmpty()).forEach(quick::add);
        saveQuick();
    }

    /// Persists [#quick]; a failure only reports in the status line — the
    /// buttons themselves keep working for this session.
    // [impl->dsn~quick-message-buttons~7]
    private void saveQuick() {
        try {
            QueueFile.save(QueueFile.quickFile(queuesDir), quick);
        } catch (IOException e) {
            status.setText("Cannot save quick messages: " + e.getMessage());
        }
    }

    /// The cards' text areas are **reused** across rebuilds (see [#card]):
    /// a node that leaves the scene for good loses the keyboard at the next
    /// pulse — Tab then started from the toolbar (field report 2026-09-16) —
    /// while one taken out and put back within the same pulse keeps focus,
    /// caret, selection, height and a half-typed edit.
    // [impl->dsn~message-queue-ui~26]
    private void rebuild() {
        spare.clear();
        spare.addAll(areas);
        rebuildCards();
        spare.clear();
    }

    /// Last rebuild's card areas, handed out again by [#card] for the
    /// message they show; empty outside a rebuild.
    private final List<TextArea> spare = new ArrayList<>();

    /// Property key under which a card area carries its current send button,
    /// so the compose chord installed once reaches the button of the rebuild.
    private static final String SEND_BUTTON = "queue.send";

    private void rebuildCards() {
        rebuildQuick();
        cards.getChildren().clear();
        areas.clear();
        if (task == null) {
            cards.getChildren().add(new Label("Select a task to queue messages."));
            return;
        }
        // Read messages leave the stack for a collapsed section at the bottom —
        // out of the way, but one click from being edited and sent again.
        // [impl->dsn~message-queue-read-mark~1]
        VBox readCards = new VBox(6);
        // Oldest first, so the send order reads top-down like the list it is:
        // ➊ at the top goes out next, the newest message sits closest to the
        // add box that just produced it.
        for (int i = 0; i < messages.size(); i++) {
            (read.contains(messages.get(i)) ? readCards : cards).getChildren().add(card(i));
        }
        // Directly under the last queued card, not pinned to the pane's
        // bottom edge: with a short queue the box would sit alone at the far
        // end of an empty pane, miles from what one just read.
        addSend = addSendButton();
        cards.getChildren().add(row(addBox, null, addSend, addDelayButton(), null, null, null));
        if (!readCards.getChildren().isEmpty()) {
            TitledPane readPane =
                    new TitledPane("Read (" + readCards.getChildren().size() + ")", readCards);
            readPane.getStyleClass().add("queue-read");
            readPane.setExpanded(readExpanded);
            readPane.expandedProperty().addListener((obs, was, now) -> readExpanded = now);
            cards.getChildren().add(readPane);
        }
    }

    /// The shared row skeleton: left column (≡ handle above the send
    /// button, below it the delayed-send toggle and the qodo link), the text area, and right of it 🗑
    /// delete above the "read" tick box. Every row carries all of them — where
    /// an element does not apply it stays in the layout invisibly, so all text
    /// boxes start (and end) at the same x.
    private HBox row(TextArea area, @Nullable Label handle, Button send, ToggleButton delay,
            @Nullable String qodoUrl, @Nullable CheckBox readBox, @Nullable Runnable onDelete) {
        Label handleSlot = handle != null ? handle : invisible(new Label("≡"));
        VBox left = new VBox(4, handleSlot, send, delay, qodoLinkButton(qodoUrl));
        left.setAlignment(Pos.TOP_CENTER);
        VBox areaBox = new VBox(area, resizeGrip(area));
        HBox.setHgrow(areaBox, Priority.ALWAYS);
        Button delete = trashButton();
        delete.setFocusTraversable(false);
        VBox right = new VBox(4, delete, readBox != null ? readBox : invisible(new CheckBox()));
        right.setAlignment(Pos.TOP_CENTER);
        HBox row = new HBox(4, left, areaBox, right);
        row.setAlignment(Pos.TOP_LEFT);
        if (onDelete == null) {
            invisible(delete);
        } else {
            delete.setTooltip(new Tooltip("Delete this message"));
            delete.setOnAction(event -> onDelete.run());
            delete.visibleProperty().bind(row.hoverProperty());
        }
        return row;
    }

    /// The grip along a box's bottom edge: dragging it down makes that box
    /// taller (and up shorter, never below one row), what a browser gives
    /// every `<textarea>`. Per box and not persisted — a taller box is for
    /// the message being written right now.
    // [impl->dsn~message-queue-ui~26]
    private static Region resizeGrip(TextArea area) {
        Region grip = new Region();
        grip.setPrefHeight(5);
        grip.setCursor(Cursor.S_RESIZE);
        grip.getStyleClass().add("resize-grip");
        // Screen coordinates: the grip itself moves with the drag, so its
        // local y would fight the resize it causes.
        double[] start = new double[2];
        grip.setOnMousePressed(event -> {
            start[0] = event.getScreenY();
            start[1] = area.getHeight();
        });
        grip.setOnMouseDragged(event ->
                area.setPrefHeight(Math.max(24, start[1] + event.getScreenY() - start[0])));
        return grip;
    }

    /// Hides `node` but keeps its layout slot, so rows stay aligned.
    private static <T extends Node> T invisible(T node) {
        node.setVisible(false);
        return node;
    }

    /// A card's text area, built once per message and reused by every later
    /// rebuild ([#rebuild]); its handlers reach the current rebuild's send
    /// button through [#SEND_BUTTON].
    private TextArea newCardArea(String message) {
        TextArea area = new TextArea(message);
        area.setWrapText(true);
        area.setPrefRowCount(5);
        installAttachments(area);
        // Focus-loss auto-save, like the editor lane: edits persist without
        // an explicit save action.
        area.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) {
                resumeIfSuspended();
            } else {
                commitEdits();
            }
        });
        // Three Ctrl+Enters save the edit and send this card — the same chord
        // the add box carries, so the muscle memory holds while editing an
        // existing message too. No triple-Enter commit here: blank lines are
        // content while editing.
        installComposeKeys(area, null, () -> {
            commitEdits();
            Button send = (Button) area.getProperties().get(SEND_BUTTON);
            // Same order as the add box's chord: save first, so a chord that
            // cannot send still keeps the edit — and says why.
            if (send == null || send.isDisable()) {
                status.setText("Saved — this task has no tmux window to send to.");
            } else {
                send.fire();
            }
        });
        return area;
    }

    /// One queued message: ≡ drag handle and its send button left of the
    /// text, 🗑 delete shown while hovered.
    private Node card(int index) {
        String message = messages.get(index);
        Button send = sendButton(index);
        // The area shown for this message last time, when there is one: its
        // text is the message (or an uncommitted edit of it — the message it
        // was created for is what commitEdits compares against).
        TextArea area = spare.stream()
                .filter(shown -> message.equals(shown.getText())
                        || message.equals(shown.getProperties().get("queue.message")))
                .findFirst().orElse(null);
        if (area != null) {
            spare.remove(area);
        } else {
            area = newCardArea(message);
        }
        area.getProperties().put("queue.message", message);
        area.getProperties().put(SEND_BUTTON, send);
        while (areas.size() <= index) {
            areas.add(area);
        }
        areas.set(index, area);

        // Only the armed cards have a send order, so only they carry a number:
        // an unarmed card is never sent on its own, and numbering it promised a
        // turn it would never get. The rank is the card's place among the armed
        // ones (➊ goes first), not its place in the whole stack. Same glyphs as
        // the task row's queue count badge.
        int rank = armedRank(index);
        Label handle = new Label(rank > 0 ? "≡ " + CircledCount.glyph(rank) : "≡");
        handle.setTooltip(new Tooltip(rank > 0
                ? "Number " + rank + " in the delayed send order (➊ goes first) — drag to reorder"
                : "Drag to reorder — tick the clock to send this message once the chat is idle"));
        handle.getStyleClass().add(Styles.TEXT_MUTED);
        handle.setCursor(Cursor.MOVE);

        // The read tick sits below the trash can: same column, and both are
        // "I am done with this message", one reversibly.
        // [impl->dsn~message-queue-read-mark~1]
        CheckBox readBox = new CheckBox();
        readBox.setSelected(read.contains(messages.get(index)));
        readBox.setFocusTraversable(false);
        readBox.setTooltip(new Tooltip("Mark as read — moves the message into the \"Read\" section"));
        readBox.setOnAction(event -> markRead(index, readBox.isSelected()));

        HBox card = row(area, handle, send, delayButton(index),
                qodoUrls.get(messages.get(index)), readBox, () -> {
            commitEdits();
            if (index < messages.size()) {
                messages.remove(index);
            }
            save();
            rebuild();
        });

        // Reorder by dragging the handle onto another card; the payload is
        // the message index (same string-payload pattern as the task-list
        // row move).
        handle.setOnDragDetected(event -> {
            Dragboard dragboard = handle.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(Integer.toString(index));
            dragboard.setContent(content);
            event.consume();
        });
        card.setOnDragOver(event -> {
            if (event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        card.setOnDragDropped(event -> {
            boolean moved = event.getDragboard().hasString()
                    && move(event.getDragboard().getString(), index);
            event.setDropCompleted(moved);
            event.consume();
        });
        return card;
    }

    /// The hover delete button: the same Material trash-can SVG used for
    /// deleting a task ([TaskListCell]), so "delete" reads the same
    /// everywhere. Unicode glyphs render as empty boxes in JavaFX on
    /// Windows (MADR 0010), so it must be the SVG, not a ✕.
    private static Button trashButton() {
        SvgNode svg = new SvgNode(MDIInterface.TRASH_CAN_OUTLINE.path(), 14);
        Button delete = new Button(null, svg);
        delete.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        return delete;
    }

    /// The open-in-browser button of a qodo-sourced message: it focuses (or
    /// opens) the review comment the prompt came from, so one can see what
    /// qodo actually flagged and whether the suggestion is still there — the
    /// "is this message outdated?" check the sync itself cannot answer. A
    /// message with no known comment URL (a hand-typed one, or a sidecar
    /// written before the URLs were recorded) gets no button and no slot for
    /// it — an icon button is no wider than the send button above it, so the
    /// rows stay aligned either way, while a kept slot would stretch the
    /// two-row add box. Right-clicking it offers **Copy URL** — the fallback
    /// when no browser extension is connected.
    // [impl->dsn~qodo-agent-prompt-queue~11]
    private Button qodoLinkButton(@Nullable String url) {
        SvgNode svg = new SvgNode(MDIInterface.OPEN_IN_NEW.path(), 14);
        Button link = new Button(null, svg);
        link.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        link.setFocusTraversable(false);
        if (url == null || url.isBlank()) {
            link.setManaged(false);
            return invisible(link);
        }
        link.setTooltip(new Tooltip(
                "Show qodo's review comment in the browser (right-click to copy its URL)"));
        // The fallback when no browser extension is connected: the URL
        // itself, to paste into whatever browser is at hand. Same wording and
        // bare-URL payload as the PR icon's menu (`dsn~pr-state-indicator~3`).
        MenuItem copyUrl = new MenuItem("Copy URL");
        copyUrl.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(url);
            Clipboard.getSystemClipboard().setContent(content);
            status.setText("Copied " + url);
        });
        link.setContextMenu(new ContextMenu(copyUrl));
        link.setOnAction(event -> {
            status.setText("Opening " + url + " …");
            onFocusUrlInBrowser.accept(this.task, url);
        });
        return link;
    }

    /// The delay toggle's tooltip: what it does, or why this task cannot use it.
    private static String delayTooltip(@Nullable Task task) {
        ChatRoute route = ChatRoute.of(task);
        if (route.canDelay()) {
            return "Delayed next — send this message as soon as the chat is done working."
                    + " Tick several: they go out one per turn, in the order the numbers show";
        }
        return route.canSend()
                ? "Delayed send needs a tmux window — this chat runs in the app's own terminal"
                : "This task has no tmux window to send to";
    }

    private Button sendButton(int index) {
        Button send = new Button("➤");
        // Mirrored: the message leaves the queue towards the terminal on
        // the left, not into the text box (Unicode has no left twin of ➤).
        send.setScaleX(-1);
        send.getStyleClass().addAll(Styles.SMALL, Styles.ACCENT);
        boolean sendable = ChatRoute.of(task).canSend();
        send.setTooltip(new Tooltip(sendable
                ? "Send this message to the task's chat"
                : "This task has no tmux window to send to"));
        send.setDisable(!sendable);
        // Not focus-traversable: clicking must not pull focus out of a
        // half-typed add box and commit it prematurely.
        send.setFocusTraversable(false);
        send.setOnAction(event -> sendAt(send, index));
        return send;
    }

    /// The add box's own send button: queue what is typed, then send it
    /// right away. On failure the message stays queued below, with its own
    /// send button to retry from.
    private Button addSendButton() {
        Button send = sendButton(0);
        send.setOnAction(event -> {
            commitAddBox();
            sendAt(send, messages.size() - 1);
        });
        return send;
    }

    /// The "delayed next" toggle below a card's send button: instead of
    /// pasting the message now — into a chat that is mid-run, where it queues
    /// up behind whatever Claude is doing and is easily missed — it arms the
    /// message, and the status poll delivers it as soon as that task's window
    /// reports `waiting`/`done`. Untick to disarm. Arming another message
    /// queues it behind the armed ones; one goes out per idle turn.
    // [impl->dsn~message-queue-delayed-send~4]
    private ToggleButton delayButton(int index) {
        SvgNode svg = new SvgNode(MDIInterface.CLOCK_OUTLINE.path(), 14);
        ToggleButton delay = new ToggleButton(null, svg);
        delay.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        // Like the send button: reaching it must not commit a half-typed box.
        delay.setFocusTraversable(false);
        delay.setDisable(!ChatRoute.of(task).canDelay());
        delay.setTooltip(new Tooltip(delayTooltip(task)));
        String text = index >= 0 && index < messages.size() ? messages.get(index) : null;
        delay.setSelected(task != null && text != null
                && armedFor(task.id()).contains(text));
        delay.setOnAction(event -> {
            if (delay.isSelected()) {
                arm(index);
            } else if (task != null && text != null) {
                List<String> rest = new ArrayList<>(armedFor(task.id()));
                rest.remove(text);
                putArmed(task.id(), rest);
                // The cards behind it move up a rank, so their numbers change.
                rebuild();
                status.setText("");
            }
        });
        return delay;
    }

    /// The add box's delayed-send toggle: queue what is typed, then arm it —
    /// the delayed twin of [#addSendButton].
    // [impl->dsn~message-queue-delayed-send~4]
    private ToggleButton addDelayButton() {
        ToggleButton delay = delayButton(-1);
        delay.setOnAction(event -> {
            commitAddBox();
            arm(messages.size() - 1);
            addBox.requestFocus();
        });
        return delay;
    }

    /// Clicking into any message field of a suspended task brings its chat
    /// back right away, so the window is up by the time the message is
    /// written instead of only when it is sent. `MainWindow.resumeTask`
    /// re-reads the file, so a repeated click on an already-resumed task
    /// resurrects nothing.
    // [impl->dsn~message-queue-resume-send~2]
    private void resumeIfSuspended() {
        Task focused = this.task;
        if (focused != null && focused.status() == TaskStatus.SUSPENDED) {
            status.setText("Resuming the task …");
            onResume.accept(focused);
        }
    }

    /// Arms the message at `index` for a delayed send, moves its card right
    /// below the last armed one (so it gets the next number) and redraws.
    // [impl->dsn~message-queue-delayed-send~4]
    private void arm(int index) {
        Task armTask = this.task;
        if (!ChatRoute.of(armTask).canDelay()) {
            return;
        }
        commitEdits();
        if (index < 0 || index >= messages.size() || messages.get(index).isBlank()) {
            return;
        }
        String text = messages.get(index);
        List<String> queue = new ArrayList<>(armedFor(armTask.id()));
        if (!queue.contains(text)) {
            // The card moves directly below the last armed one, so it takes
            // the next number and the armed cards stay one contiguous block
            // in the order the clocks were ticked; a drag afterwards reorders
            // and renumbers as usual. Nothing armed yet: it stays where it is.
            int last = queue.stream().mapToInt(messages::indexOf).max().orElse(-1);
            if (last >= 0) {
                messages.remove(index);
                messages.add(last < index ? last + 1 : last, text);
                save();
            }
            queue.add(text);
        }
        putArmed(armTask.id(), queue);
        rebuild();
        int count = armedFor(armTask.id()).size();
        if (armTask.status() == TaskStatus.SUSPENDED) {
            // [impl->dsn~message-queue-resume-send~2]
            status.setText("Resuming the task — the message goes out once its chat is back …");
            onResume.accept(armTask);
        } else {
            status.setText(count == 1
                    ? "Waiting for the chat to finish …"
                    : count + " messages armed — one goes out per turn, ➊ first.");
        }
    }

    /// Stores a task's armed queue, dropping the entry entirely when nothing
    /// is armed any more — `armed` holds only tasks with a pending send.
    // [impl->dsn~message-queue-delayed-send~4]
    private void putArmed(String taskId, List<String> queue) {
        if (queue.isEmpty()) {
            armed.remove(taskId);
            justSent.remove(taskId);
        } else {
            armed.put(taskId, inQueueOrder(queue));
        }
        saveArmed();
    }

    /// Mirrors [#armed] to disk — best effort, a failure only logs: the
    /// armed queue still works until the app exits.
    // [impl->dsn~message-queue-delayed-send~4]
    private void saveArmed() {
        try {
            QueueFile.saveArmed(armedFile, armed);
        } catch (IOException e) {
            Logger.warn("Cannot save armed messages: {}", e.getMessage());
        }
    }

    /// Sorts an armed queue into the shown task's card order (index 0 = bottom
    /// card = ➊), so the delayed sends go out in the order the cards' numbers
    /// promise rather than the order the clocks were ticked.
    // [impl->dsn~message-queue-delayed-send~4]
    private List<String> inQueueOrder(List<String> queue) {
        List<String> sorted = new ArrayList<>(queue);
        sorted.sort(Comparator.comparingInt(messages::indexOf));
        return sorted;
    }

    /// Delivers every armed message whose task's chat has fallen idle, fed the
    /// status poll's `host windowId -> status` map on the FX thread (the same
    /// tick the running indicator uses, so "done" reaches the user and the
    /// message at once). A message that goes out leaves its task's queue —
    /// also when that task is not the one shown.
    // [impl->dsn~message-queue-delayed-send~4]
    public void sendDelayed(Map<String, String> byKey) {
        // Blocked tasks with nothing armed are visited too: their block must
        // lift when the chat goes busy, or a message armed later on the idle
        // chat never goes out.
        Set<String> taskIds = new HashSet<>(armed.keySet());
        taskIds.addAll(justSent);
        for (String taskId : taskIds) {
            List<String> pending = armedFor(taskId);
            // Fresh: a resumed task's window id is only in the file. Unknown
            // task (not loaded yet, or deleted): its messages stay armed.
            // [impl->dsn~message-queue-resume-send~2]
            Task sendTask = taskById.apply(taskId);
            String host = sendTask == null ? null : TmuxHost.of(sendTask);
            Task.TmuxConfig tmux = sendTask == null ? null : sendTask.tmux();
            if (host == null || tmux == null || tmux.window() == null) {
                if (pending.isEmpty()) {
                    // Gone or suspended: its window will not report busy.
                    justSent.remove(taskId);
                }
                continue;
            }
            String reported = byKey.get(TmuxStatusPoller.key(host, tmux.window()));
            if (reported == null || !IDLE_STATUSES.contains(reported)) {
                // The chat picked the last one up: the next may follow once it
                // falls idle again.
                justSent.remove(taskId);
                continue;
            }
            if (pending.isEmpty() || !justSent.add(taskId)) {
                // Idle, but this is still the status from before our paste.
                continue;
            }
            String text = pending.get(0);
            List<String> rest = new ArrayList<>(pending.subList(1, pending.size()));
            if (rest.isEmpty()) {
                armed.remove(taskId);
            } else {
                armed.put(taskId, rest);
            }
            saveArmed();
            deliver(sendTask, text, result -> {
                if (result instanceof SendResult.Sent) {
                    dropFromQueue(sendTask, text);
                } else {
                    // Nothing was pasted, so the chat stays idle and the block
                    // above would never lift: let the rest of the queue try on
                    // the next tick, the failed message stays queued, disarmed.
                    justSent.remove(taskId);
                    if (task != null && task.id().equals(taskId)) {
                        rebuild();
                    }
                }
            });
        }
    }

    /// Removes a delivered message from its task's queue: through the pane's
    /// own state when that task is shown, else straight in the file (the pane
    /// holds no other task's queue). The message is located by **content**,
    /// so an in-flight edit or reorder cannot drop the wrong one; false when
    /// it is no longer there (an edited message stays queued).
    // [impl->dsn~message-queue-delayed-send~4]
    // [impl->dsn~message-queue-ui~26]
    private boolean dropFromQueue(Task sent, String text) {
        if (task != null && task.id().equals(sent.id())) {
            int at = messages.indexOf(text);
            if (at < 0) {
                return false;
            }
            messages.remove(at);
            save();
            rebuild();
            return true;
        }
        Path file = QueueFile.file(queuesDir, sent.id());
        List<String> queued = new ArrayList<>(QueueFile.load(file));
        if (!queued.remove(text)) {
            return false;
        }
        try {
            QueueFile.save(file, queued);
            onQueueChanged.run();
        } catch (IOException e) {
            Logger.warn("Cannot save queue for {}: {}", sent.id(), e.getMessage());
        }
        return true;
    }

    /// Sends the message at `index` on the background executor; on success
    /// it leaves the queue, on failure it stays and the error shows below.
    /// Its box is read-only for the round-trip, so the text cannot drift away
    /// from what is being pasted.
    private void sendAt(Button send, int index) {
        Task sendTask = this.task;
        if (!ChatRoute.of(sendTask).canSend()) {
            return;
        }
        commitEdits();
        if (index < 0 || index >= messages.size() || messages.get(index).isBlank()) {
            return;
        }
        String text = messages.get(index);
        // A suspended chat has no window to paste into: resume it and let the
        // message go out on the tick the recreated session reports idle.
        // [impl->dsn~message-queue-resume-send~2]
        if (sendTask.status() == TaskStatus.SUSPENDED) {
            arm(index);
            return;
        }
        send.setDisable(true);
        // The box goes read-only with the button: the text is already on its
        // way, and an edit landing while the paste runs would be silently
        // lost — the chat gets what was sent, the queue file the newer text.
        // A success takes the card away anyway; a failure hands the box back
        // for a fix and a retry.
        TextArea area = index < areas.size() ? areas.get(index) : null;
        if (area != null) {
            area.setEditable(false);
        }
        deliver(sendTask, text, result -> {
            // Through the same drop as the delayed send: a selection change
            // while the send was in flight must not leave the delivered
            // message sitting in the queue of the task it went to.
            boolean dropped = result instanceof SendResult.Sent && dropFromQueue(sendTask, text);
            if (!dropped && this.task != null && this.task.id().equals(sendTask.id())) {
                send.setDisable(false);
                if (area != null) {
                    area.setEditable(true);
                }
            }
        });
    }

    /// Re-sends a recorded sent message unchanged, applying the currently
    /// picked mode. Unlike a queued send it touches no queue entry — the
    /// record simply gets delivered again. [impl->dsn~last-sent-message~5]
    private void resend(String text) {
        Task sendTask = this.task;
        if (!ChatRoute.of(sendTask).canSend()) {
            return;
        }
        if (text.isBlank()) {
            return;
        }
        deliver(sendTask, text, result -> {});
    }

    /// Delivers `text` to `sendTask`'s chat on the background executor,
    /// recording it as the task's last sent on success;
    /// `onDone` then runs on the FX thread with the result so the caller can
    /// reconcile its own view.
    private void deliver(Task sendTask, String text, Consumer<SendResult> onDone) {
        status.setText("Sending…");
        AtomicBoolean reported = new AtomicBoolean();
        executor.execute(() -> {
            SendResult result = sender.send(sendTask, text, line -> {
                reported.set(true);
                Platform.runLater(() -> status.setText(line));
            });
            Platform.runLater(() -> {
                switch (result) {
                    case SendResult.Sent() -> {
                        // Record the delivered text as the task's last sent
                        // message — keyed by the task the send was for, so a
                        // selection change mid-send records the right task. The
                        // watcher skips `.queues/`, so ping the backup hook too.
                        // [impl->dsn~last-sent-message~5]
                        QueueFile.saveSent(queuesDir, sendTask.id(), text);
                        onQueueChanged.run();
                        if (this.task != null && this.task.id().equals(sendTask.id())) {
                            showSent();
                        }
                        // A sender that reported progress ended on its outcome
                        // line (which attachments did not arrive); keep it standing.
                        if (!reported.get()) {
                            status.setText("Sent.");
                        }
                    }
                    case SendResult.Failed(String reason) -> status.setText(reason);
                }
                onDone.accept(result);
            });
        });
    }

    /// Syncs every shown text area back into [#messages] and persists —
    /// called before any operation that reads or reshuffles the list, so
    /// half-finished edits are never lost or sent stale.
    private void commitEdits() {
        boolean changed = false;
        for (int i = 0; i < areas.size() && i < messages.size(); i++) {
            String text = areas.get(i).getText();
            if (!messages.get(i).equals(text)) {
                // An armed message that is edited stays armed — with its new
                // text, or the stale one would go out.
                // [impl->dsn~message-queue-delayed-send~4]
                List<String> queue = task == null ? null : armed.get(task.id());
                if (queue != null) {
                    String stale = messages.get(i);
                    queue.replaceAll(a -> a.equals(stale) ? text : a);
                    saveArmed();
                }
                messages.set(i, text);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    /// A model or effort picker: `asIs` is the first entry and means "leave
    /// the session as it is" (nothing sent), every other entry is a
    /// `/model`/`/effort` argument. Used by the `Add task…` and the
    /// category-from-URL dialog, so both offer the same choices.
    // [impl->dsn~claude-mode-select~3]
    public static ComboBox<String> modeBox(String asIs, List<String> values) {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().add(asIs);
        box.getItems().addAll(values);
        box.getSelectionModel().selectFirst();
        box.getStyleClass().add(Styles.SMALL);
        // Not focus-traversable, for the same reason as the send button:
        // reaching the picker must not commit a half-typed box.
        box.setFocusTraversable(false);
        return box;
    }

    /// Pre-selects `value` in a [#modeBox] when it is one of the offered
    /// entries; an unknown or null value leaves the picker on "as is".
    // [impl->dsn~claude-mode-select~3]
    public static void selectMode(ComboBox<String> box, @Nullable String value) {
        if (value != null && box.getItems().indexOf(value) > 0) {
            box.getSelectionModel().select(value);
        }
    }

    /// The argument picked in a [#modeBox], or null for its "as is" entry.
    // [impl->dsn~claude-mode-select~3]
    public static @Nullable String modeValue(ComboBox<String> box) {
        return box.getSelectionModel().getSelectedIndex() <= 0 ? null : box.getValue();
    }

    /// Installs the compose-box key conventions on `area`. Both ways forward
    /// take **three** presses, so nothing leaves the box by accident:
    /// three plain Enters in a row at the end of the box run `commit` (the two
    /// blank lines marking the submit are deleted first — they are not part of
    /// the text), and three `Ctrl+Enter` in a row run `chord`. `Shift+Enter`
    /// inserts a newline. Each Ctrl+Enter but the last greens the box a shade
    /// stronger ([#CHORD_STEPS]) so the run is visible before it fires; the
    /// firing press clears the shade again, since it empties the box and green
    /// left standing colours the next message's box. Anything else — a key, a
    /// click into the box — drops the run, so a Ctrl+Enter minutes later never
    /// completes a chord started before it.
    ///
    /// One implementation, shared by the queue's add box, its card edit boxes
    /// and the Add-task dialog's description field, so the compose chords
    /// cannot drift apart between them (`req~compose-key-conventions~5`).
    /// A null `commit` leaves the triple-Enter half out — that is what a card
    /// edit box passes, where blank lines are content and must not submit.
    // [impl->dsn~message-queue-ui~26]
    // [impl->dsn~task-create-ui~15]
    public static void installComposeKeys(
            TextArea area, @Nullable Runnable commit, Runnable chord) {
        int[] run = {0};
        Runnable dropChord = () -> {
            run[0] = 0;
            chordShade(area, 0);
        };
        // Not the focus: committing rebuilds the add row, which takes the box
        // out of the scene and back in — the chord must survive its own first
        // press.
        area.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> dropChord.run());
        area.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            // The modifier's own key press comes first in every Ctrl+Enter —
            // holding Ctrl down must not drop the run it is about to advance.
            if (!COMMIT_KEYS.match(event) && !event.getCode().isModifierKey()) {
                dropChord.run();
            }
            if (NEWLINE_KEYS.match(event)) {
                area.replaceSelection("\n");
                event.consume();
            } else if (COMMIT_KEYS.match(event)) {
                int step = Math.min(run[0] + 1, CHORD_PRESSES);
                if (step == CHORD_PRESSES) {
                    // The shade goes before the action: the chord empties the
                    // add box (or replaces the card it edited), and a green
                    // box left behind colours the *next* message's box.
                    run[0] = 0;
                    chordShade(area, 0);
                    chord.run();
                } else {
                    run[0] = step;
                    chordShade(area, step);
                }
                event.consume();
            } else if (commit != null && submitsOnThirdEnter(area, event)) {
                // Drop the two blank lines the two prior Enters put in front
                // of the caret — they mark the submit, they are not the message.
                int caret = area.getCaretPosition();
                area.deleteText(caret - 2, caret);
                commit.run();
                event.consume();
            }
        });
    }

    /// Puts `area` into the Ctrl+Enter chord's `step`-th shade (0 = none).
    private static void chordShade(TextArea area, int step) {
        area.getStyleClass().removeAll(CHORD_STEPS);
        if (step > 0) {
            area.getStyleClass().add(CHORD_STEPS.get(step - 1));
        }
    }

    /// True when this key press is the third Enter of a run and so should
    /// submit the box. `KEY_PRESSED` fires before this Enter's own
    /// newline is inserted; the two Enters before it left two newlines
    /// immediately in front of the caret, **and the caret must sit at the
    /// end of the box**: two newlines alone can also come from a caret
    /// moved (e.g. cursor Up) right behind an existing blank line, where
    /// two Enters must not submit a message mid-edit. A box with no real
    /// content (three Enters into an empty box) does not submit.
    private static boolean submitsOnThirdEnter(TextArea area, KeyEvent event) {
        if (event.getCode() != KeyCode.ENTER || event.isShortcutDown()) {
            return false;
        }
        String text = area.getText();
        int caret = area.getCaretPosition();
        return caret == text.length() && caret >= 2
                && text.charAt(caret - 1) == '\n' && text.charAt(caret - 2) == '\n'
                && !text.isBlank();
    }

    private void commitAddBox() {
        Task queuedTask = this.task;
        String text = addBox.getText();
        if (queuedTask == null || text == null || text.isBlank()) {
            return;
        }
        commitEdits();
        // Trim surrounding whitespace: a message queued via three Enters
        // carries the blank lines that marked the submit, and stray leading/
        // trailing space is never meant to reach the chat either way.
        String stripped = text.strip();
        messages.add(stripped);
        addBox.clear();
        save();
        rebuild();
        // A URL mentioned in the message also becomes a browser tab, same as
        // one in the task description.
        // [impl->dsn~task-url-collect~1]
        onMessageQueued.accept(queuedTask, stripped);
    }

    /// The background poller's entry point: add `taskId`'s new active qodo
    /// prompts to its queue (add-only — the poller never removes). Deduped and
    /// recorded as qodo-sourced by the shared reconcile. FX thread only.
    // [impl->dsn~qodo-agent-prompt-queue~11]
    public void addQodoPrompts(String taskId, Map<String, String> activePrompts) {
        reconcileQodo(taskId, activePrompts, false);
    }

    /// The "Review comments sync" button: fetch the current task's open review
    /// comments off the FX thread, then reconcile — add new ones **and** remove
    /// queued review-sourced messages the PR no longer carries. A failed fetch
    /// (null) leaves the queue untouched, so a network blip can never be read
    /// as "all resolved".
    // [impl->dsn~qodo-agent-prompt-queue~11]
    private void syncReviewComments() {
        Task syncTask = this.task;
        if (syncTask == null || syncTask.prUrl() == null) {
            return;
        }
        reviewSyncButton.setDisable(true);
        status.setText("Review comments: syncing…");
        executor.execute(() -> {
            Map<String, String> current = qodoFetcher.current(syncTask);
            Platform.runLater(() -> {
                // Only mutate if the pane still shows the task the sync was for.
                if (this.task != null && this.task.id().equals(syncTask.id())) {
                    if (current == null) {
                        status.setText("Review comments: couldn't reach GitHub — queue left as is.");
                    } else {
                        QodoReconcile.Result result = reconcileQodo(syncTask.id(), current, true);
                        status.setText(String.format("Review comments: +%d added, −%d removed.",
                                result.added(), result.removed()));
                    }
                }
                reviewSyncButton.setDisable(this.task == null || this.task.prUrl() == null);
            });
        });
    }

    /// Reconciles `taskId`'s queue with qodo's current active `prompts`
    /// ([QodoReconcile]) and persists both the queue and the qodo-sourced
    /// sidecar. `removeResolved` drops the "done" qodo prompts (button) versus
    /// add-only (poller). FX thread — QueuePane is the sole writer of both
    /// files, so this never races the poller or the editing paths.
    // [impl->dsn~qodo-agent-prompt-queue~11]
    private QodoReconcile.Result reconcileQodo(String taskId, Map<String, String> prompts,
            boolean removeResolved) {
        Path storedFile = QodoImported.file(qodoDir, taskId);
        Map<String, String> storedUrls = QodoImported.load(storedFile);
        Set<String> sourced = new LinkedHashSet<>(storedUrls.keySet());
        boolean shown = task != null && task.id().equals(taskId);
        List<String> queue;
        if (shown) {
            commitEdits();   // capture in-flight box edits before reading the list
            queue = new ArrayList<>(messages);
        } else {
            queue = new ArrayList<>(QueueFile.load(QueueFile.file(queuesDir, taskId)));
        }

        QodoReconcile.Result result =
                QodoReconcile.apply(queue, sourced, new ArrayList<>(prompts.keySet()), removeResolved);
        // The sidecar keeps a URL for exactly the prompts the reconcile still
        // tracks; a re-fetched prompt takes the fresh URL (qodo re-posts a
        // suggestion under a new comment id after a force-push).
        Map<String, String> urls = new LinkedHashMap<>(storedUrls);
        urls.putAll(prompts);
        urls.keySet().retainAll(result.qodoSourced());

        if (shown) {
            messages = result.queue();
            qodoUrls = urls;
            save();          // writes the queue file + refreshes the badge
            rebuild();
        } else if (result.added() > 0 || result.removed() > 0) {
            try {
                QueueFile.save(QueueFile.file(queuesDir, taskId), result.queue());
                onQueueChanged.run();
            } catch (IOException e) {
                Logger.warn("Cannot write queue for {}: {}", taskId, e.getMessage());
            }
        }
        try {
            QodoImported.save(storedFile, urls);
        } catch (IOException e) {
            Logger.warn("Cannot record qodo-sourced prompts for {}: {}", taskId, e.getMessage());
        }
        return result;
    }

    /// Ticks (or unticks) the message at `index` as read and re-lays the
    /// stack, so it moves into — or back out of — the "Read" section.
    // [impl->dsn~message-queue-read-mark~1]
    private void markRead(int index, boolean isRead) {
        commitEdits();
        if (index < 0 || index >= messages.size()) {
            return;
        }
        if (isRead) {
            read.add(messages.get(index));
            readExpanded = true;   // show where the message just went
        } else {
            read.remove(messages.get(index));
        }
        saveRead();
        rebuild();
    }

    /// Persists the read marks, dropping those of messages that left the queue
    /// (sent, deleted, or edited into a different text) so the file cannot grow
    /// past its queue. An empty set deletes the file.
    // [impl->dsn~message-queue-read-mark~1]
    private void saveRead() {
        if (task == null) {
            return;
        }
        read.retainAll(messages);
        try {
            QueueFile.save(QueueFile.readFile(queuesDir, task.id()), new ArrayList<>(read));
        } catch (IOException e) {
            Logger.warn("Cannot save read marks for {}: {}", task.id(), e.getMessage());
        }
    }

    private void save() {
        if (task == null) {
            return;
        }
        if (!read.isEmpty()) {
            saveRead();
        }
        // Every queue mutation lands here, so this is the one place that keeps
        // the armed queue honest: a message that was sent, deleted or edited
        // away must not still go out, and a reorder moves its send with it.
        // [impl->dsn~message-queue-delayed-send~4]
        List<String> pending = new ArrayList<>(armedFor(task.id()));
        if (!pending.isEmpty()) {
            pending.removeIf(a -> !messages.contains(a));
            putArmed(task.id(), pending);
        }
        try {
            QueueFile.save(QueueFile.file(queuesDir, task.id()), messages);
            onQueueChanged.run();
        } catch (IOException e) {
            Logger.warn("Cannot save queue for {}: {}", task.id(), e.getMessage());
            status.setText("Cannot save queue: " + e.getMessage());
        }
    }

    private boolean move(String draggedIndex, int targetIndex) {
        int from;
        try {
            from = Integer.parseInt(draggedIndex);
        } catch (NumberFormatException e) {
            return false;
        }
        commitEdits();
        if (from < 0 || from >= messages.size() || targetIndex >= messages.size() || from == targetIndex) {
            return false;
        }
        messages.add(targetIndex, messages.remove(from));
        save();
        rebuild();
        return true;
    }

    /// Installs the queue boxes' attachment affordances on any text area:
    /// file drag'n'drop and image paste, both stored under the attachments
    /// dir and referenced as markers the send rewrites. Public because the
    /// Add-task dialog's description field takes them too
    /// (`dsn~task-create-ui~15`).
    public void installAttachments(TextArea area) {
        installImagePaste(area);
        installFileDrop(area);
        // A marker is a path, not a picture: hovering the box shows what it
        // actually attached, the way the editor lane does for its markers.
        // [impl->dsn~attachment-image-hover~2]
        AttachmentPreview.install(area, attachmentsDir);
    }

    /// Ctrl+V with an image on the clipboard (a screenshot) stores it under
    /// the attachments dir and inserts its marker at the caret — text
    /// pastes fall through to the TextArea's own handler.
    private void installImagePaste(TextArea area) {
        KeyCombination paste = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);
        area.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!paste.match(event) || !Clipboard.getSystemClipboard().hasImage()) {
                return;
            }
            try {
                Path stored = storeImage(Clipboard.getSystemClipboard().getImage());
                area.insertText(area.getCaretPosition(), Attachments.marker(stored));
            } catch (IOException e) {
                Logger.warn("Cannot store the pasted image: {}", e.getMessage());
                status.setText("Cannot store the pasted image: " + e.getMessage());
            }
            event.consume();
        });
    }

    /// Files dragged from the OS onto a text box are copied under the
    /// attachments dir and referenced with `[file: …]` markers at the
    /// caret — the send uploads them like pasted images. Event **filters**,
    /// so the card-reorder drag (string payload, no files) and the
    /// TextArea's own text-drag handling stay untouched.
    private void installFileDrop(TextArea area) {
        area.addEventFilter(DragEvent.DRAG_OVER, event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            }
        });
        area.addEventFilter(DragEvent.DRAG_DROPPED, event -> {
            if (!event.getDragboard().hasFiles()) {
                return;
            }
            for (File file : event.getDragboard().getFiles()) {
                if (!file.isFile()) {
                    continue;
                }
                try {
                    Path stored = storeFile(file.toPath());
                    area.insertText(area.getCaretPosition(), Attachments.fileMarker(stored));
                } catch (IOException e) {
                    Logger.warn("Cannot store the dropped file {}: {}", file, e.getMessage());
                    status.setText("Cannot store the dropped file: " + e.getMessage());
                }
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }

    /// Copies a dropped file under the attachments dir; the timestamp
    /// prefix keeps the original name visible while ruling out collisions
    /// (locally and in the flat remote drop directory).
    private Path storeFile(Path source) throws IOException {
        Files.createDirectories(attachmentsDir);
        // Sanitized: the remote path lands verbatim in the chat message
        // text, where a space (or quote) would split or mangle the path.
        String safeName = source.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        Path file = attachmentsDir.resolve(LocalDateTime.now().format(STAMP) + "-" + safeName);
        Files.copy(source, file);
        return file;
    }

    private Path storeImage(Image image) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();
        if (width <= 0 || height <= 0 || reader == null) {
            throw new IOException("clipboard image is empty");
        }
        // FX image -> PNG without the javafx.swing bridge: one bulk pixel
        // copy into an AWT image, encoded by ImageIO.
        int[] pixels = new int[width * height];
        reader.getPixels(0, 0, width, height,
                WritablePixelFormat.getIntArgbInstance(), pixels, 0, width);
        Attachments.opaqueIfFullyTransparent(pixels);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        Files.createDirectories(attachmentsDir);
        Path file = attachmentsDir.resolve("img-" + LocalDateTime.now().format(STAMP) + ".png");
        ImageIO.write(buffered, "png", file.toFile());
        return file;
    }
}
