package com.contextswitcher.ui;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import atlantafx.base.theme.Styles;
import com.contextswitcher.discovery.ClaudeReplies;
import com.contextswitcher.queue.Attachments;
import com.contextswitcher.queue.ClaudeMode;
import com.contextswitcher.ssh.RemoteFiles;
import com.contextswitcher.ssh.ProcessSshRunner;
import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.terminal.FileHyperlinkFilter;
import com.contextswitcher.terminal.HoverLinkFilter;
import com.contextswitcher.terminal.IssueHyperlinkFilter;
import com.contextswitcher.queue.SendResult;
import com.contextswitcher.terminal.OptionalSequencesEmulator;
import com.contextswitcher.terminal.PtyTtyConnector;
import com.contextswitcher.terminal.StepwiseResizeTerminal;
import com.contextswitcher.terminal.StringTtyConnector;
import com.contextswitcher.terminal.TerminalSettings;
import com.contextswitcher.terminal.TerminalZoom;
import com.contextswitcher.terminal.TmuxMirrorCommands;
import com.techsenger.jeditermfx.core.Terminal;
import com.techsenger.jeditermfx.core.TerminalDataStream;
import com.techsenger.jeditermfx.core.TerminalDisplay;
import com.techsenger.jeditermfx.core.TerminalStarter;
import com.techsenger.jeditermfx.core.TtyBasedArrayDataStream;
import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.core.emulator.JediEmulator;
import com.techsenger.jeditermfx.core.typeahead.TerminalTypeAheadManager;
import com.techsenger.jeditermfx.core.model.JediTerminal;
import com.techsenger.jeditermfx.core.model.StyleState;
import com.techsenger.jeditermfx.core.model.TerminalTextBuffer;
import com.techsenger.jeditermfx.ui.DefaultHyperlinkFilter;
import com.techsenger.jeditermfx.ui.DefaultTerminalCopyPasteHandler;
import com.techsenger.jeditermfx.ui.TerminalCopyPasteHandler;
import com.techsenger.jeditermfx.ui.TerminalPanel;
import com.techsenger.jeditermfx.ui.settings.SettingsProvider;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// The middle lane: a live JediTermFX terminal mirroring the selected
/// task's tmux window (MADR 0007), replacing the static capture-pane
/// snapshot of v0.1.
///
/// One pty per (remote, base session): the pty attaches a **grouped**
/// mirror session ([TmuxMirrorCommands]), so the mirror's current window is
/// independent of the real session's. Selecting another task of the same
/// session only sends a `select-window` over a plain ssh side channel —
/// no reconnect. Selecting a task elsewhere closes the pty and attaches
/// anew. The mirror session stays behind on the remote when the app quits
/// (detached, no processes of its own) — harmless by design.
///
/// The header shows the terminal's application title (what the remote
/// publishes via OSC — with tmux `set-titles on`, the pane title, i.e.
/// Claude's task summary). An unexpected pty exit auto-reconnects with
/// backoff (https://github.com/contextswitcher/contextswitcher-private/issues/36); an intentional teardown (suspend, reselection) does not.
///
/// The live connection itself is a [TerminalSession] — a [TmuxSession] or
/// an [OwnedSession]; the pane holds the one in view and asks it.
// [impl->dsn~terminal-pane~14]
// [impl->dsn~terminal-reconnect~2]
public class TerminalPane {

    private final ExecutorService executor;

    /// The mirror's side channel, host-routed ([HostCommandRunner]): the same
    /// commands go over ssh for a remote and through a local shell for a
    /// locally mirrored session.
    private final SshCommandRunner ssh;

    /// The bar buttons that need a remote host — the diff window and the
    /// generated-file listing both work over ssh, so a locally mirrored
    /// session (`dsn~terminal-local-mirror~2`) has nothing for them to reach.
    /// Disabled while such a session is shown rather than left to fail
    /// against a host called "(local)".
    // [impl->dsn~terminal-local-mirror~2]
    private final List<Button> remoteOnlyButtons = new ArrayList<>();

    /// Every button in the bar below the terminal: disabled while a placeholder
    /// shows, enabled for a mirror and for an app-owned session alike — the
    /// routes they take differ ([#session]), not whether they work.
    // [impl->dsn~terminal-owned-session~3]
    private final List<Button> barButtons = new ArrayList<>();
    /// The bar holding [#barButtons], filled by [#jumpToBottomBar].
    private final HBox bar = new HBox(8);

    /// The app-owned Claude sessions, by task id. They outlive a selection
    /// change: the pane hosts the process, so tearing it down to look at
    /// another task would end the conversation — only the session in view
    /// changes. Concurrent: the queue's delivery looks a session up from its
    /// background executor while the FX thread starts and ends them.
    // [impl->dsn~terminal-owned-session~3]
    private final Map<String, OwnedSession> owned = new ConcurrentHashMap<>();

    /// Every widget this pane built and still holds, so a `Ctrl`+wheel zoom
    /// reaches the sessions kept alive off-screen too ([#refreshFonts]).
    /// Weak: a widget is dropped with the session that owned it, and nothing
    /// here should keep either alive.
    // [impl->dsn~terminal-zoom~4]
    private final Map<JediTermFxWidget, Boolean> zoomed = new WeakHashMap<>();

    /// What the pane shows live — a [TmuxSession] or an [OwnedSession] — or
    /// null while a placeholder or a suspend snapshot is up. Whatever differs
    /// by kind, the pane asks it rather than checking which kind it is.
    // [impl->dsn~terminal-pane~14]
    private @Nullable TerminalSession session;

    /// The read-only terminal of a suspend snapshot, closed by the next
    /// [#disconnect] like a live one.
    private @Nullable JediTermFxWidget snapshotView;

    /// The pane as its sessions see it.
    private final TerminalSession.View view = new SessionView();

    /// Lists and fetches the mirrored host's generated files. Its own runner:
    /// the side channel's 10 s timeout fits a `tmux` round-trip, not a
    /// megabyte of base64 over a slow link.
    // [impl->dsn~generated-file-download~2]
    private final RemoteFiles remoteFiles =
            new RemoteFiles(new ProcessSshRunner(Duration.ofMinutes(2)));

    /// The repository `#123` in the mirrored terminal links into
    /// (`dsn~terminal-issue-links~2`) — the selected task's category `repo:`,
    /// null while none is known. Read on every click, so it follows the
    /// selection without rebuilding the terminal's filters.
    private @Nullable String issueRepo;

    private final Label title = new Label("");
    private final Label message = new Label("Select a task to mirror its tmux window.");
    private final StackPane center = new StackPane(message);
    private final BorderPane root = new BorderPane(center);

    /// The pane's one toolbar band — the mirrored pane's title, then whatever
    /// the window adds at the right end ([#addToolbarControls]) — in the
    /// height and look of every pane toolbar (`.panel-header`).
    // [impl->dsn~shell-layout~2]
    private final HBox toolbar = new HBox(8);
    /// The band, plus the row the window puts below it ([#setSubHeader]).
    private final VBox header = new VBox(toolbar);

    /// An opaque cover shown over the live terminal while the mirror switches
    /// windows within the same session (the fast path), so the previous
    /// window's content cannot be mistaken for the newly selected task's.
    private final Label switchingLabel = new Label("Switching…");
    private final StackPane switchingOverlay = new StackPane(switchingLabel);

    /// The load [#blankThen] deferred, until its frame comes; a newer click
    /// replaces it. Non-null also means a focus request is for that load.
    private @Nullable AnimationTimer pendingLoad;

    /// The cover is [#blankThen]'s, not a switch's: the fast path lifts it
    /// when no window change follows.
    private boolean blanked;

    /// A [#focusTerminal] that arrived while no terminal was up: the attach
    /// in flight takes the keyboard when it completes. Cleared at the start of
    /// every attach, so a request that never got its terminal cannot hand the
    /// keyboard to some later, unasked-for reconnect.
    // [impl->dsn~terminal-pane~14]
    private boolean focusOnAttach;

    /// The mirrored session's reported model/effort, shown left of the
    /// "Jump to bottom" button — blank while it publishes nothing.
    // [impl->dsn~claude-mode-report~2]
    private final Label modeLabel = new Label();

    /// The "Show diff" button's action, wired by `Main` once the repository
    /// exists: receives the mirrored remote + tmux config to resolve the
    /// task and open its diff window. Null until wired (button no-ops).
    // [impl->dsn~terminal-diff-window~2]
    private @Nullable BiConsumer<String, Task.TmuxConfig> diffOpener;

    /// The "Show diff" button's action while an app-owned session is shown,
    /// wired by `Main`: receives the session's task id, since there is no
    /// remote or tmux config to find the task by. Null until wired (no-op).
    // [impl->dsn~terminal-owned-session~3]
    private @Nullable Consumer<String> ownedDiffOpener;

    /// The "Fork…" button's action, wired by `MainWindow` — it owns the
    /// previewed task and the dialog, this pane only shows the button
    /// (`dsn~task-fork~1`). No-op until wired.
    // [impl->dsn~task-fork~1]
    private Runnable onFork = () -> { };

    /// The "Fork…" button itself, so [MainWindow] can enable/disable it and
    /// set its tooltip for the previewed task — unlike [#barButtons], its
    /// enabled-ness does not follow the session shown here, but the task the
    /// rest of the window is looking at.
    // [impl->dsn~task-fork~1]
    private final Button forkButton = new Button("Fork…");

    /// The "Select the mirrored task" action, wired by `Main` once the
    /// repository exists: receives the mirrored remote and the window the
    /// mirror says it is **really** showing, and selects that window's task.
    /// Null until wired (menu item no-ops).
    // [impl->dsn~terminal-mirrored-task~1]
    private @Nullable BiConsumer<String, String> mirroredTaskSelector;

    /// The Claude session of a mirrored window, wired by `Main` (which knows
    /// tasks): the transcript "Copy reply" and the Markdown selection copy
    /// read. Null until wired; the resolver answers null for a window whose
    /// task records no session.
    // [impl->dsn~terminal-markdown-copy~1]
    private @Nullable BiFunction<String, Task.TmuxConfig, Task.@Nullable ClaudeConfig> claudeResolver;

    /// One-line outcomes for the status bar, wired by `Main`.
    private Consumer<String> statusMessage = text -> { };

    // [impl->dsn~terminal-markdown-copy~1]
    private final ClaudeReplies replies;

    /// `autoCopyReplies`: copy each finished reply of the mirrored chat.
    // [impl->dsn~terminal-markdown-copy~1]
    private boolean autoCopyReplies;

    /// The mirrored window's status at the previous poll tick, keyed by that
    /// window — a reply finishes on a `working` → idle edge of one window, not
    /// on a switch to a window that happens to be idle.
    private @Nullable String lastStatusKey;
    private @Nullable String lastStatus;

    /// Every category's repository, for the mirror's qualified issue links
    /// (`dsn~terminal-issue-links~2`).
    private List<String> knownRepos = List.of();
    /// Last path typed into "Custom path…", pre-filled and pre-selected on the
    /// next open: the same file is usually wanted again, and a selected value
    /// is one keystroke from being replaced.
    // [impl->dsn~generated-file-download~2]
    private String lastCustomPath = "";
    public TerminalPane(ExecutorService executor, SshCommandRunner ssh) {
        this.executor = executor;
        this.ssh = ssh;
        this.replies = new ClaudeReplies(ssh);
        message.getStyleClass().add(Styles.TEXT_MUTED);
        StackPane.setAlignment(message, Pos.CENTER);
        switchingLabel.getStyleClass().add(Styles.TEXT_MUTED);
        StackPane.setAlignment(switchingLabel, Pos.CENTER);
        paintGround();
        title.getStyleClass().add(Styles.TEXT_MUTED);
        title.setMinWidth(0);
        // The title leads the band — no "Terminal" label: the dock tab already
        // says so. Right-click anywhere on the band for the mirrored task.
        // [impl->dsn~terminal-mirrored-task~1]
        MenuItem whose = new MenuItem("Select the mirrored task");
        whose.setId("select-mirrored-task");
        whose.setOnAction(event -> selectMirroredTask());
        ContextMenu bandMenu = new ContextMenu(whose);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolbar.getChildren().addAll(title, spacer);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(4, 6, 4, 6));
        toolbar.getStyleClass().add("panel-header");
        toolbar.setOnContextMenuRequested(event -> {
            bandMenu.show(toolbar, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        root.setTop(header);
        root.setBottom(jumpToBottomBar());
    }

    /// Adds `controls` at the right end of the toolbar band — the main
    /// window's Claude and IntelliJ buttons.
    // [impl->dsn~shell-layout~2]
    public void addToolbarControls(Node... controls) {
        toolbar.getChildren().addAll(controls);
    }

    /// Puts `node` below the toolbar band — the main window's PR rows, which
    /// vary in height and so stay out of the fixed-height band.
    public void setSubHeader(Node node) {
        header.getChildren().setAll(toolbar, node);
    }

    /// The bar below the terminal with the "Jump to bottom" button: leaves
    /// tmux copy-mode in the mirror, so the view returns to the live output
    /// after the user scrolled up. A no-op while nothing is connected.
    // [impl->dsn~terminal-jump-to-bottom~1]
    private Node jumpToBottomBar() {
        Button clear = new Button("Clear input");
        clear.getStyleClass().add(Styles.SMALL);
        clear.setOnAction(event -> withSession(TerminalSession::clearInput));
        // [impl->dsn~terminal-accept-suggestion~1]
        Button accept = new Button("Accept suggestion");
        accept.getStyleClass().add(Styles.SMALL);
        accept.setTooltip(new Tooltip("Sends Claude's greyed-out prompt suggestion: Tab, then Enter"));
        accept.setOnAction(event -> withSession(TerminalSession::acceptSuggestion));
        // [impl->dsn~terminal-interrupt~1]
        Button interrupt = new Button("Ctrl+C");
        interrupt.getStyleClass().add(Styles.SMALL);
        interrupt.setTooltip(new Tooltip("Sends Ctrl+C to the session: interrupts Claude or the command "
                + "it runs (a second Ctrl+C right after exits Claude)"));
        interrupt.setOnAction(event -> withSession(TerminalSession::interrupt));
        Button jump = new Button("Jump to bottom");
        jump.getStyleClass().add(Styles.SMALL);
        jump.setOnAction(event -> withSession(TerminalSession::jumpToBottom));
        // [impl->dsn~terminal-diff-window~2]
        Button diff = new Button("Show diff");
        diff.setId("show-diff-button");
        diff.getStyleClass().add(Styles.SMALL);
        diff.setTooltip(new Tooltip("The workspace's uncommitted changes — or its last commit "
                + "when clean — in a throwaway tmux window (q, then Enter, closes it)"));
        diff.setOnAction(event -> withSession(TerminalSession::showDiff));
        // [impl->dsn~generated-file-download~2]
        Button files = new Button("Files");
        files.setId("generated-files-button");
        files.getStyleClass().add(Styles.SMALL);
        files.setTooltip(new Tooltip("Files the Claude sessions on this host generated, newest "
                + "first — or any remote path via \"Custom path…\". The chosen file is "
                + "downloaded to " + RemoteFiles.downloadsDir() + " and opened"));
        files.setOnAction(event -> withSession(shown -> shown.showFiles(files)));
        // [impl->dsn~terminal-markdown-copy~1]
        Button copyReply = new Button("Copy reply");
        copyReply.setId("copy-reply-button");
        copyReply.getStyleClass().add(Styles.SMALL);
        copyReply.setTooltip(new Tooltip("Claude's last reply as the Markdown it wrote, read from "
                + "the session transcript — no terminal wrapping, no rendering"));
        copyReply.setOnAction(event -> copyLastReply(copyReply));
        modeLabel.getStyleClass().add(Styles.TEXT_MUTED);
        // [impl->dsn~task-fork~1]
        forkButton.setId("fork-task-button");
        forkButton.getStyleClass().add(Styles.SMALL);
        forkButton.setOnAction(event -> onFork.run());
        // Starts disabled: `MainWindow` sets the real state for the
        // previewed task the moment it wires this pane up.
        forkButton.setDisable(true);
        remoteOnlyButtons.addAll(List.of(diff, files));
        barButtons.addAll(List.of(accept, clear, interrupt, jump, copyReply, diff, files));
        // forkButton is not in barButtons/remoteOnlyButtons: `enableBar`
        // would force it on for any live session, but its enabled-ness
        // tracks the *previewed task*, decided by `MainWindow`, not the
        // session shown here (`dsn~task-fork~1`).
        bar.getChildren().setAll(modeLabel, accept, clear, interrupt, jump, copyReply, diff, files, forkButton);
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(4, 6, 4, 6));
        return bar;
    }

    /// Wires the "Fork…" button's click to `action` — `MainWindow`'s dialog
    /// opener for the previewed task (`dsn~task-fork~1`).
    // [impl->dsn~task-fork~1]
    public void setOnFork(Runnable action) {
        this.onFork = action;
    }

    /// Enables/disables the "Fork…" button for the task `MainWindow` is
    /// currently previewing, with `reason` as its tooltip either way —
    /// why it is disabled, or what it does (`dsn~task-fork~1`).
    // [impl->dsn~task-fork~1]
    public void setForkAvailability(boolean enabled, String reason) {
        forkButton.setDisable(!enabled);
        forkButton.setTooltip(new Tooltip(reason));
    }

    /// Greys the whole bar out while `busy` — `Main` passes "a task creation
    /// is running", when a key sent into the fresh session would break its
    /// start. On the bar, not the buttons, which [#enableBar] and the
    /// placeholder toggle themselves.
    // [impl->dsn~busy-while-creating~1]
    public void disableBarWhile(ObservableBooleanValue busy) {
        bar.disableProperty().bind(busy);
    }

    /// Runs a bar button's action on the session in view. With none (a
    /// placeholder) the buttons are disabled anyway.
    private void withSession(Consumer<TerminalSession> action) {
        TerminalSession shown = session;
        if (shown != null) {
            action.accept(shown);
        }
    }

    /// Enables the bar for `shown`: every button, except Show diff and Files
    /// where the session has nothing for them to reach.
    // [impl->dsn~terminal-local-mirror~2]
    private void enableBar(TerminalSession shown) {
        barButtons.forEach(button -> button.setDisable(false));
        remoteOnlyButtons.forEach(button -> button.setDisable(!shown.offersDiffAndFiles()));
    }

    /// Asks the mirrored host for the newest files its Claude sessions
    /// generated and offers them in a menu above the button; picking one
    /// downloads and opens it ([#downloadGeneratedFile]).
    ///
    /// The list comes from the remote, never from the terminal's text: Claude
    /// Code hard-wraps a long path across lines with its own gutter text
    /// between the halves, so neither a clickable-path filter nor a copy of
    /// the selection can recover it (see [RemoteFiles]).
    ///
    /// "Custom path…" heads the menu ([#askForCustomPath]) — the scratchpad
    /// scan is a shortcut, not the only way to name a file — so a host whose
    /// sessions generated nothing still opens a menu, with one disabled line
    /// saying so instead of an alert.
    ///
    /// The button is disabled for the ssh round-trip so a slow remote does
    /// not read as a dead button.
    // [impl->dsn~generated-file-download~2]
    private void showRemoteFiles(Button source, String remote) {
        source.setDisable(true);
        executor.execute(() -> {
            List<RemoteFiles.RemoteFile> found = remoteFiles.list(remote);
            long now = System.currentTimeMillis() / 1000;
            Platform.runLater(() -> {
                source.setDisable(false);
                ContextMenu menu = filesMenu(() -> askForCustomPath(remote), found.isEmpty(), remote);
                for (RemoteFiles.RemoteFile file : found) {
                    menu.getItems().add(fileItem(file.fileName(), file, now,
                            () -> downloadGeneratedFile(remote, file)));
                }
                // Above the button: the bar sits at the bottom of the window.
                menu.show(source, Side.TOP, 0, 0);
            });
        });
    }

    /// "Files" for an app-owned session: the scratchpads of this machine's own
    /// Claude sessions ([RemoteFiles#listLocal]), opened where they are —
    /// nothing to download. "Custom path…" asks for a local path. Disabled for
    /// the directory walk, like the remote listing.
    // [impl->dsn~terminal-owned-session~3]
    private void showLocalFiles(Button source) {
        source.setDisable(true);
        executor.execute(() -> {
            List<RemoteFiles.RemoteFile> found = RemoteFiles.listLocal(RemoteFiles.localScratchpads());
            long now = System.currentTimeMillis() / 1000;
            Platform.runLater(() -> {
                source.setDisable(false);
                ContextMenu menu = filesMenu(this::askForLocalPath, found.isEmpty(), "this machine");
                for (RemoteFiles.RemoteFile file : found) {
                    Path local = Path.of(file.path());
                    menu.getItems().add(fileItem(String.valueOf(local.getFileName()), file, now,
                            () -> openLocal(local)));
                }
                menu.show(source, Side.TOP, 0, 0);
            });
        });
    }

    /// A started terminal on `connector`, with the pane's link filters and
    /// input fixes — the one build a mirror and an owned session share.
    /// `mirror` puts [MarkdownCopyHandler] behind the clipboard, so a mirror's
    /// selection can be matched against the chat's transcript.
    private JediTermFxWidget liveWidget(PtyTtyConnector connector, boolean mirror) {
        TerminalSettings settings = new TerminalSettings(Themes.isDark(), Themes.isEverforest());
        // [impl->dsn~terminal-markdown-copy~1]
        JediTermFxWidget terminal = new JediTermFxWidget(120, 32, settings) {
            @Override
            protected TerminalPanel createTerminalPanel(SettingsProvider provider, StyleState style,
                    TerminalTextBuffer buffer) {
                return new PanePanel(provider, buffer, style, mirror);
            }

            // [impl->dsn~terminal-zoom~4]
            @Override
            protected JediTerminal createTerminal(TerminalDisplay display, TerminalTextBuffer buffer,
                    StyleState style) {
                return new StepwiseResizeTerminal(display, buffer, style);
            }

            // The parent's starter, with an emulator that knows the optional
            // sequences Claude Code sends. [impl->dsn~terminal-optional-sequences~1]
            @Override
            protected TerminalStarter createTerminalStarter(JediTerminal terminal, TtyConnector ttyConnector) {
                TerminalTypeAheadManager typeAhead = getTypeAheadManager();
                return new TerminalStarter(terminal, ttyConnector,
                        new TtyBasedArrayDataStream(ttyConnector, typeAhead::onTerminalStateChanged), typeAhead,
                        getExecutorServiceManager()) {
                    @Override
                    protected JediEmulator createEmulator(TerminalDataStream dataStream, Terminal emulated) {
                        return new OptionalSequencesEmulator(dataStream, emulated);
                    }
                };
            }
        };
        addHyperlinkFilters(terminal);
        terminal.setTtyConnector(connector);
        terminal.start();
        installControlKeyForwarding(terminal, connector);
        installLocalSelection(terminal);
        installZoom(terminal);
        installWheelDirectionFix(terminal);
        hideScrollbarWithoutScrollback(terminal);
        zoomed.put(terminal, Boolean.TRUE);
        return terminal;
    }

    /// The pane as its sessions see it: the view they may change and the
    /// wiring `Main` installed on the pane.
    private final class SessionView implements TerminalSession.View {

        @Override
        public void showStatus(String text) {
            // The status replaces the whole center, so no blank is left to lift.
            blanked = false;
            message.setText(text);
            title.setText("");
            center.getChildren().setAll(message);
        }

        @Override
        public void showTerminal(JediTermFxWidget terminal) {
            center.getChildren().setAll(terminal.getPane());
        }

        @Override
        public void end(String text) {
            showMessage(text);
        }

        @Override
        public void setTitle(String text) {
            title.setText(text);
        }

        @Override
        public void cover(@Nullable String window) {
            showSwitchingOverlay(window);
        }

        @Override
        public void uncover() {
            center.getChildren().remove(switchingOverlay);
        }

        @Override
        public void liftBlank() {
            if (blanked) {
                // A believed window change puts its own cover straight back.
                blanked = false;
                center.getChildren().remove(switchingOverlay);
            }
        }

        @Override
        public void clearFocusRequest() {
            focusOnAttach = false;
        }

        @Override
        public void focusNewTerminal(JediTermFxWidget terminal) {
            boolean asked = focusOnAttach;
            focusOnAttach = false;
            Platform.runLater(() -> {
                Node focused = center.getScene() == null ? null : center.getScene().getFocusOwner();
                if (focused instanceof TextInputControl) {
                    return;
                }
                if (asked || focused == null) {
                    terminal.getTerminalPanel().getCanvas().requestFocus();
                }
            });
        }

        @Override
        public void focusTerminal() {
            TerminalPane.this.focusTerminal();
        }

        @Override
        public JediTermFxWidget liveWidget(PtyTtyConnector connector, boolean mirror) {
            return TerminalPane.this.liveWidget(connector, mirror);
        }

        @Override
        public void openDiff(String remote, Task.TmuxConfig tmux) {
            BiConsumer<String, Task.TmuxConfig> opener = diffOpener;
            if (opener != null) {
                opener.accept(remote, tmux);
            }
        }

        @Override
        public void openOwnedDiff(String taskId) {
            Consumer<String> opener = ownedDiffOpener;
            if (opener != null) {
                opener.accept(taskId);
            }
        }

        @Override
        public void showRemoteFiles(Button source, String remote) {
            TerminalPane.this.showRemoteFiles(source, remote);
        }

        @Override
        public void showLocalFiles(Button source) {
            TerminalPane.this.showLocalFiles(source);
        }

        @Override
        public Task.@Nullable ClaudeConfig claudeOf(String remote, Task.TmuxConfig tmux) {
            var resolver = claudeResolver;
            return resolver == null ? null : resolver.apply(remote, tmux);
        }

        @Override
        public void selectTaskOfWindow(String remote, String window) {
            BiConsumer<String, String> selector = mirroredTaskSelector;
            if (selector != null) {
                selector.accept(remote, window);
            }
        }
    }

    /// The "Files" menu's head: "Custom path…", a separator and — when the
    /// scan found nothing — a disabled line naming where it looked.
    private static ContextMenu filesMenu(Runnable onCustomPath, boolean empty, String where) {
        ContextMenu menu = new ContextMenu();
        // Always first, even when the listing is empty: the scratchpad
        // scan is a shortcut, not the only way to name a file.
        MenuItem custom = new MenuItem("Custom path…");
        custom.setOnAction(event -> onCustomPath.run());
        menu.getItems().addAll(custom, new SeparatorMenuItem());
        if (empty) {
            MenuItem none = new MenuItem("No files in any scratchpad on " + where);
            none.setDisable(true);
            menu.getItems().add(none);
        }
        return menu;
    }

    /// One listed scratchpad file: its name, size and age.
    private static MenuItem fileItem(String name, RemoteFiles.RemoteFile file, long now, Runnable open) {
        MenuItem item = new MenuItem("%s — %s, %s ago".formatted(name,
                RemoteFiles.humanSize(file.size()), RemoteFiles.age(file.modified(), now)));
        item.setOnAction(event -> open.run());
        return item;
    }

    /// The local menu's "Custom path…": [#askForCustomPath] for a file on
    /// this machine, opened in place.
    // [impl->dsn~terminal-owned-session~3]
    private void askForLocalPath() {
        TextInputDialog dialog = new TextInputDialog(lastCustomPath);
        dialog.setTitle("Open a local file");
        dialog.setHeaderText("Path of the file on this machine.\n~/ starts at your home directory.");
        dialog.getEditor().setPrefColumnCount(60);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        dialog.setOnShown(event -> Platform.runLater(dialog.getEditor()::selectAll));
        String typed = dialog.showAndWait().orElse("").strip();
        if (typed.isEmpty()) {
            return;
        }
        lastCustomPath = typed;
        Path local = localPath(typed);
        if (local == null) {
            Alerts.wrapping(Alert.AlertType.ERROR, "No readable file " + typed + ".").show();
            return;
        }
        openLocal(local);
    }

    /// Hands a file on this machine to its application off the FX thread; an
    /// alert names it when the OS has none registered.
    private void openLocal(Path local) {
        executor.execute(() -> {
            if (!open(local)) {
                Platform.runLater(() -> Alerts.wrapping(Alert.AlertType.INFORMATION,
                        "%s — this system has no application registered for it."
                                .formatted(local)).show());
            }
        });
    }

    /// The menu's "Custom path…" entry: asks for a remote path and downloads
    /// it like any listed file.
    ///
    /// The scratchpad listing only covers what a Claude session generated for
    /// itself; a file written anywhere else — into the workspace, a `/tmp`
    /// path of the user's own choosing — has no entry, and this is how it is
    /// named. The path is resolved on the remote first ([RemoteFiles#stat]),
    /// so a typo is one alert rather than a failed transfer, and so the size
    /// limit applies to a typed path as well.
    // [impl->dsn~generated-file-download~2]
    private void askForCustomPath(String remote) {
        TextInputDialog dialog = new TextInputDialog(lastCustomPath);
        dialog.setTitle("Download from " + remote);
        dialog.setHeaderText("Path of the file on " + remote
                + ".\nRelative paths and ~/ start at the remote home.");
        dialog.getEditor().setPrefColumnCount(60);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        // After the dialog focused its editor, which otherwise puts the caret
        // at the end and leaves nothing selected.
        dialog.setOnShown(event -> Platform.runLater(dialog.getEditor()::selectAll));
        String typed = dialog.showAndWait().orElse("");
        if (!typed.isBlank()) {
            lastCustomPath = typed.strip();
        }
        String path = RemoteFiles.cleanPath(typed);
        if (path == null) {
            if (!typed.isBlank()) {
                Alerts.wrapping(Alert.AlertType.ERROR,
                        "Not a usable path: %s%nA path may not contain a single quote — it is "
                                .formatted(typed.strip())
                                + "the one character the remote command's quoting cannot carry.")
                        .show();
            }
            return;
        }
        executor.execute(() -> {
            RemoteFiles.RemoteFile file = remoteFiles.stat(remote, path);
            if (file == null) {
                Platform.runLater(() -> Alerts.wrapping(Alert.AlertType.ERROR,
                        "No readable file %s on %s.".formatted(path, remote)).show());
                return;
            }
            downloadGeneratedFile(remote, file);
        });
    }

    /// Downloads one listed file into the user's download folder and hands it
    /// to the OS. When the OS cannot open it, an alert names the local path —
    /// the file did arrive, and a silent nothing would suggest otherwise.
    // [impl->dsn~generated-file-download~2]
    private void downloadGeneratedFile(String remote, RemoteFiles.RemoteFile file) {
        executor.execute(() -> {
            Path local;
            try {
                local = remoteFiles.download(remote, file);
            } catch (IOException e) {
                Logger.warn("Cannot download {}: {}", file.path(), e.getMessage());
                Platform.runLater(() -> Alerts.wrapping(Alert.AlertType.ERROR,
                        "Cannot download %s:%n%s".formatted(file.fileName(), e.getMessage()))
                        .show());
                return;
            }
            if (!open(local)) {
                Platform.runLater(() -> Alerts.wrapping(Alert.AlertType.INFORMATION,
                        "Downloaded to %s — this system has no application registered for it."
                                .formatted(local)).show());
            }
        });
    }

    /// Opens a local file with its registered application (AWT `Desktop`, not
    /// `HostServices.showDocument`, whose `file://` URI lands in the browser —
    /// the `Main.openTasksDir` shape). False when the platform or the OS
    /// refused; call off the FX thread, the dispatch can block.
    // [impl->dsn~generated-file-download~2]
    private static boolean open(Path file) {
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Logger.warn("Opening a file is not supported on this platform: {}", file);
            return false;
        }
        try {
            Desktop.getDesktop().open(file.toFile());
            return true;
        } catch (Exception e) {
            Logger.warn("Cannot open {}: {}", file, e.getMessage());
            return false;
        }
    }

    /// Wires the transcript lookup behind "Copy reply" and the Markdown
    /// selection copy (called by `Main`).
    // [impl->dsn~terminal-markdown-copy~1]
    public void setClaudeResolver(BiFunction<String, Task.TmuxConfig, Task.@Nullable ClaudeConfig> resolver,
            Consumer<String> status) {
        this.claudeResolver = resolver;
        this.statusMessage = status;
    }

    /// Switches the reply auto-copy (`autoCopyReplies`), live from a settings save.
    // [impl->dsn~terminal-markdown-copy~1]
    public void setAutoCopyReplies(boolean enabled) {
        this.autoCopyReplies = enabled;
    }

    /// The poll tick's statuses: a `working` → `waiting`/`done` edge of the
    /// shown session's window is a finished reply, copied when
    /// `autoCopyReplies` is on. FX thread.
    // [impl->dsn~terminal-markdown-copy~1]
    public void showStatuses(Map<String, String> statuses) {
        TerminalSession shown = session;
        String key = shown == null ? null : shown.statusKey();
        String status = key == null ? null : statuses.get(key);
        boolean finished = key != null && key.equals(lastStatusKey) && "working".equals(lastStatus)
                && ("waiting".equals(status) || "done".equals(status));
        lastStatusKey = key;
        lastStatus = status;
        if (finished && autoCopyReplies) {
            copyLastReply(null);
        }
    }

    /// The transcript behind the session in view; null when nothing is shown
    /// or no Claude session is recorded for it.
    private TerminalSession.@Nullable ReplySource replySource() {
        TerminalSession shown = session;
        return shown == null ? null : shown.replySource();
    }

    /// Puts the shown chat's last reply on the clipboard as Markdown.
    /// `button` is disabled for the round-trip; null for the auto-copy.
    // [impl->dsn~terminal-markdown-copy~1]
    private void copyLastReply(@Nullable Button button) {
        TerminalSession.ReplySource source = replySource();
        if (source == null) {
            statusMessage.accept("No Claude session recorded for this window — nothing to copy.");
            return;
        }
        if (button != null) {
            button.setDisable(true);
        }
        statusMessage.accept("Copying Claude's last reply …");
        executor.execute(() -> {
            List<String> found = replies.fetch(source.host(), source.claude().cwd(), source.sessionId());
            Platform.runLater(() -> {
                if (button != null) {
                    button.setDisable(false);
                }
                if (found.isEmpty()) {
                    statusMessage.accept("No reply found in the transcript of " + source.sessionId() + ".");
                    return;
                }
                String reply = found.getLast();
                ClipboardContent content = new ClipboardContent();
                content.putString(reply);
                Clipboard.getSystemClipboard().setContent(content);
                statusMessage.accept("Copied Claude's last reply as Markdown (%d lines)."
                        .formatted(reply.lines().count()));
            });
        });
    }

    /// JediTermFX's clipboard handler with a Markdown second pass: the
    /// selection is copied as rendered right away, then — when the transcript
    /// holds it — replaced by its Markdown source, unless the clipboard has
    /// changed in the meantime. Covers copy-on-select and `Ctrl+Shift+C` alike.
    // [impl->dsn~terminal-markdown-copy~1]
    private final class MarkdownCopyHandler extends DefaultTerminalCopyPasteHandler {

        @Override
        public void setContents(String text, boolean useSystemSelection) {
            super.setContents(text, useSystemSelection);
            TerminalSession.ReplySource source = ClaudeReplies.matchable(text) ? replySource() : null;
            if (source == null) {
                return;
            }
            executor.execute(() -> {
                String markdown = ClaudeReplies.markdownFor(text,
                        replies.fetch(source.host(), source.claude().cwd(), source.sessionId()));
                if (markdown == null || markdown.equals(text)
                        || !text.equals(getContents(useSystemSelection))) {
                    return;
                }
                super.setContents(markdown, useSystemSelection);
                Platform.runLater(() -> statusMessage.accept("Selection copied as Markdown."));
            });
        }
    }

    /// Wires the "Show diff" button (called by `Main` once the task
    /// repository exists — the pane itself knows windows, not tasks).
    // [impl->dsn~terminal-diff-window~2]
    public void setDiffOpener(BiConsumer<String, Task.TmuxConfig> opener) {
        this.diffOpener = opener;
    }

    /// Wires "Show diff" for an app-owned session (called by `Main`, which
    /// resolves the task id to its workspace).
    // [impl->dsn~terminal-owned-session~3]
    public void setOwnedDiffOpener(Consumer<String> opener) {
        this.ownedDiffOpener = opener;
    }

    /// Wires the header's "Select the mirrored task" item (called by `Main`,
    /// which knows tasks where the pane knows only windows).
    // [impl->dsn~terminal-mirrored-task~1]
    public void setMirroredTaskSelector(BiConsumer<String, String> selector) {
        this.mirroredTaskSelector = selector;
    }

    /// **Select the mirrored task**: the session in view answers it — a mirror
    /// asks the remote which window it really shows
    /// ([TmuxSession#selectShownTask]). Nothing live to ask means nothing to
    /// answer: a placeholder or a suspend snapshot is rendered from the
    /// selected task's own id, so the list already matches and the item says
    /// so instead of guessing.
    // [impl->dsn~terminal-mirrored-task~1]
    private void selectMirroredTask() {
        TerminalSession shown = session;
        if (shown == null) {
            noLiveMirror();
            return;
        }
        shown.selectShownTask();
    }

    /// What **Select the mirrored task** says where nothing live is shown.
    // [impl->dsn~terminal-mirrored-task~1]
    static void noLiveMirror() {
        Alerts.wrapping(Alert.AlertType.INFORMATION,
                "No live mirror here — what is shown belongs to the selected task.").show();
    }

    public Node getRoot() {
        return root;
    }

    /// The status poll's `host windowId -> ClaudeMode` report: the **mirrored**
    /// window's entry is shown next to the jump button, so switching tasks also
    /// switches what the label claims. Blank for a session that publishes
    /// nothing (`dsn~claude-mode-report~2`) — an empty label, not a stale one.
    /// FX thread.
    // [impl->dsn~claude-mode-report~2]
    public void showSessionModes(Map<String, ClaudeMode> byKey) {
        TerminalSession shown = session;
        String key = shown == null ? null : shown.statusKey();
        ClaudeMode mode = key == null ? ClaudeMode.DEFAULT : byKey.getOrDefault(key, ClaudeMode.DEFAULT);
        modeLabel.setText(Stream.of(mode.model(), mode.effort())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" · ")));
    }

    /// Paints the regions framing the canvas — the placeholder area and the
    /// switching cover — in the terminal's own ground, so neither shows a seam
    /// against it under either theme.
    // [impl->dsn~terminal-theme~2]
    private void paintGround() {
        String ground = TerminalSettings.backgroundColor(Themes.isDark(), Themes.isEverforest());
        center.setStyle("-fx-background-color: " + ground + ";");
        switchingOverlay.setStyle("-fx-background-color: " + ground + ";");
    }

    /// Re-colors the pane after a live theme switch: re-grounds the regions
    /// framing the canvas, and the session in view re-colors itself as far as
    /// its kind allows (a mirror re-attaches, an owned session keeps its
    /// colors). A placeholder or a snapshot has nothing to re-attach — the
    /// snapshot's own re-render waits for the next selection.
    // [impl->dsn~terminal-theme~2]
    public void retheme() {
        paintGround();
        TerminalSession shown = session;
        if (shown != null) {
            shown.retheme();
        }
    }

    /// Shows a placeholder instead of a terminal (no tmux config, suspended
    /// task, …) and ends a running connection — e.g. after a suspend the
    /// mirror must not silently drift to another window of the session.
    public void showMessage(String text) {
        // Intentional teardown: with the session gone, nothing reconnects.
        disconnect();
        // Nothing connected, so every bar button would be a silent no-op —
        // grey them out instead; [#show] enables them again.
        barButtons.forEach(button -> button.setDisable(true));
        modeLabel.setText("");
        message.setText(text);
        title.setText("");
        center.getChildren().setAll(message);
    }

    /// Opens a tmux or Claude window for a task that has none — the recovery
    /// buttons of [#showMessage]. A no-op until [#setWindowStarter] wires
    /// `Main.startRemoteWindow` up.
    // [impl->dsn~remote-window-choice~6]
    private BiConsumer<Task, Boolean> windowStarter = (task, withClaude) -> { };

    /// Hands the pane the window starter its recovery buttons call.
    // [impl->dsn~remote-window-choice~6]
    public void setWindowStarter(BiConsumer<Task, Boolean> starter) {
        this.windowStarter = starter;
    }

    /// [#showMessage] with the **tmux** / **claude** buttons under the text:
    /// the choice play offers for a task that has a remote but no window
    /// (`dsn~remote-window-choice~6`), offered here as well because the
    /// placeholder is where a creation that died halfway is actually seen.
    /// Both buttons go dead for the round-trip; the window written back (or
    /// the failure's preview refresh) replaces the placeholder.
    // [impl->dsn~remote-window-choice~6]
    public void showStartWindowMessage(String text, Task task) {
        showMessage(text);
        HBox actions = new HBox(6,
                startWindowButton("tmux", task, false),
                startWindowButton("claude", task, true));
        actions.setAlignment(Pos.CENTER);
        VBox box = new VBox(8, message, actions);
        box.setAlignment(Pos.CENTER);
        center.getChildren().setAll(box);
    }

    // [impl->dsn~remote-window-choice~6]
    private Button startWindowButton(String label, Task task, boolean withClaude) {
        Button button = new Button(label);
        button.getStyleClass().add(Styles.SMALL);
        button.setOnAction(event -> {
            HBox row = (HBox) button.getParent();
            row.getChildren().forEach(node -> node.setDisable(true));
            windowStarter.accept(task, withClaude);
        });
        return button;
    }

    /// Where a hovered link's URL is reported (the status bar); a no-op until
    /// [#setLinkHover] wires one up.
    private Consumer<@Nullable String> linkHover = url -> { };

    /// Where uploaded attachments keep their local copies; null until
    /// [#setAttachmentsDir] wires it up — then a remote attachment path is
    /// just a remote path.
    private @Nullable Path attachmentsDir;

    /// The thumbnail of the hovered file link, and the pointer it pops up
    /// beside — a link's hover callback carries no mouse event.
    // [impl->dsn~attachment-image-hover~2]
    private final AttachmentPreview.Hover imageHover = new AttachmentPreview.Hover();
    private double pointerX;
    private double pointerY;

    /// Hands the pane the sink for hover texts — `MainWindow`'s status bar,
    /// the same one the task rows' PR icons report into.
    // [impl->dsn~terminal-link-hover~1]
    public void setLinkHover(Consumer<@Nullable String> hover) {
        this.linkHover = hover;
    }

    /// Hands the pane the local attachments dir, so a sent attachment's
    /// remote path opens and previews the local copy.
    public void setAttachmentsDir(Path attachmentsDir) {
        this.attachmentsDir = attachmentsDir;
    }

    /// The clickable text of both terminal widgets: plain URLs, `#123` /
    /// `PR 123` / `JabRef#123` resolved against a repository, and file paths.
    /// Hovering a link shows a hand cursor and underlines it, a primary click
    /// opens it (Windows-Terminal-style) — a URL and an issue in the default
    /// browser, a file in its registered application — and the status bar
    /// spells out the target while the pointer rests on it.
    // [impl->dsn~terminal-hyperlinks~1]
    // [impl->dsn~terminal-issue-links~2]
    // [impl->dsn~terminal-file-links~1]
    // [impl->dsn~terminal-link-hover~1]
    private void addHyperlinkFilters(JediTermFxWidget widget) {
        widget.addHyperlinkFilter(new HoverLinkFilter(
                new DefaultHyperlinkFilter(), url -> url, url -> linkHover.accept(url)));
        IssueHyperlinkFilter issues = new IssueHyperlinkFilter(() -> issueRepo, () -> knownRepos);
        widget.addHyperlinkFilter(new HoverLinkFilter(
                issues, issues::urlFor, url -> linkHover.accept(url)));
        widget.addHyperlinkFilter(new HoverLinkFilter(new FileHyperlinkFilter(this::openLinkedPath),
                FileHyperlinkFilter::pathFor, path -> {
                    linkHover.accept(path);
                    imageHover.show(widget.getPane(), pointerX, pointerY, path == null ? null : localPath(path));
                }));
        // A filter: it sees the move before the canvas turns it into a link hover.
        widget.getPane().addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            pointerX = event.getScreenX();
            pointerY = event.getScreenY();
        });
        widget.getPane().addEventFilter(ScrollEvent.SCROLL, event -> imageHover.hide());
    }

    /// The repository the pane's `#123` links resolve against — set from the
    /// selected task's category before every [#show] / [#showSnapshot]
    /// (`dsn~terminal-issue-links~2`); null turns the issue links off.
    // [impl->dsn~terminal-issue-links~2]
    public void setIssueRepo(@Nullable String repoUrl) {
        this.issueRepo = repoUrl;
    }

    /// The repositories a qualified `JabRef#123` may resolve against — every
    /// category's `repo:`, since Claude names a foreign repository exactly
    /// when it is *not* the one the session works in
    /// (`dsn~terminal-issue-links~2`).
    // [impl->dsn~terminal-issue-links~2]
    public void setKnownRepos(Collection<String> repos) {
        this.knownRepos = List.copyOf(repos);
    }

    /// Opens a path clicked in the mirror: one that exists on this machine
    /// goes straight to its application, anything else is fetched from the
    /// mirrored host first — the "Files" menu's route
    /// ([#downloadGeneratedFile]) without the menu.
    // [impl->dsn~terminal-file-links~1]
    private void openLinkedPath(String path) {
        Path local = localPath(path);
        if (local != null) {
            openLocal(local);
            return;
        }
        TerminalSession shown = session;
        String remote = shown == null ? null : shown.downloadHost();
        String cleaned = RemoteFiles.cleanPath(path);
        if (remote == null || cleaned == null) {
            Alerts.wrapping(Alert.AlertType.ERROR, "No readable file " + path + ".").show();
            return;
        }
        executor.execute(() -> {
            RemoteFiles.RemoteFile file = remoteFiles.stat(remote, cleaned);
            if (file == null) {
                Platform.runLater(() -> Alerts.wrapping(Alert.AlertType.ERROR,
                        "No readable file %s on %s.".formatted(cleaned, remote)).show());
                return;
            }
            downloadGeneratedFile(remote, file);
        });
    }

    /// The clicked path as a readable file **on this machine**, `~` expanded;
    /// an uploaded attachment's remote path counts as its local copy, however
    /// the remote home differs from this one. Null when it is none of these —
    /// then it is the remote's, and travels the download route.
    private @Nullable Path localPath(String path) {
        try {
            Path copy = attachmentsDir == null ? null : Attachments.localCopy(path, attachmentsDir);
            Path local = copy != null ? copy : path.startsWith("~/")
                    ? Path.of(System.getProperty("user.home"), path.substring(2))
                    : Path.of(path);
            return Files.isReadable(local) && Files.isRegularFile(local) ? local : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /// Like [#showMessage] but with the task's last screen below the
    /// explanation: the pane captured just before its tmux window was killed
    /// (`dsn~terminal-suspend-snapshot~3`). Read-only and selectable, so the
    /// user can re-read Claude's last output — and copy from it — without
    /// resuming the session.
    ///
    /// The snapshot terminal is frozen at the captured screen's size and put
    /// in a [ScrollPane]: a resized window scrolls it, it never re-wraps.
    // [impl->dsn~terminal-suspend-snapshot~3]
    public void showSnapshot(String text, String snapshot) {
        showMessage(text);
        // The same emulator the live mirror uses, fed a fixed string instead
        // of a pty — so the stored `capture-pane -e` escapes render in the
        // exact colors the mirror showed. Tracked as `snapshotView` so the next
        // selection's disconnect() closes it like any live terminal.
        JediTermFxWidget view = new JediTermFxWidget(
                Math.max(80, longestVisibleLine(snapshot)),
                Math.max(1, (int) snapshot.lines().count()),
                new TerminalSettings(Themes.isDark(), Themes.isEverforest()));
        // URLs in the captured screen stay clickable, like in the live mirror.
        addHyperlinkFilters(view);
        view.setTtyConnector(new StringTtyConnector(snapshot));
        view.start();
        this.snapshotView = view;
        freezeSize(view);
        ScrollPane scroller = new ScrollPane(view.getPane());
        // No fitToWidth/fitToHeight: the content keeps its own (frozen) size
        // and the viewport scrolls over it.
        scroller.setPannable(true);
        String ground = TerminalSettings.backgroundColor(Themes.isDark(), Themes.isEverforest());
        scroller.setStyle("-fx-background: %s; -fx-background-color: %s;".formatted(ground, ground));
        VBox box = new VBox(4, message, scroller);
        VBox.setVgrow(scroller, Priority.ALWAYS);
        box.setPadding(new Insets(6));
        box.setStyle("-fx-background-color: " + ground + ";");
        center.getChildren().setAll(box);
    }

    /// Pins the terminal's canvas pane to the size its constructed column and
    /// row count gave it, so no layout pass can resize the emulator.
    ///
    /// A snapshot is a picture of a screen already wrapped at the remote pane's
    /// width, and `capture-pane` writes each of those lines as its own line.
    /// A line that filled the last column leaves the emulator's wrap flag set,
    /// so widening the terminal — which a stretched-to-fill widget does on
    /// every window resize — merges it with the next one and re-splits
    /// elsewhere: the stored screen turns into ragged nonsense. Freezing the
    /// pane keeps `TerminalPanel`'s size listener (it watches this pane's
    /// width/height) from ever firing.
    ///
    /// The pane in question is the canvas's parent — jeditermfx 1.1.0's
    /// package-private `CanvasPane`, whose preferred size `init()` already set
    /// from the constructed column/row count; `TerminalPanel.getPane()` is the
    /// outer box around it and its scrollbar, which is not what the listener
    /// watches.
    // [impl->dsn~terminal-suspend-snapshot~3]
    private static void freezeSize(JediTermFxWidget view) {
        if (!(view.getTerminalPanel().getCanvas().getParent() instanceof Region canvasPane)) {
            return;
        }
        double width = canvasPane.prefWidth(-1);
        double height = canvasPane.prefHeight(-1);
        if (width <= 0 || height <= 0) {
            // No preferred size yet — leave the layout alone rather than
            // collapsing the terminal to nothing.
            Logger.debug("No preferred size for the snapshot terminal: {}x{}", width, height);
            return;
        }
        canvasPane.setMinSize(width, height);
        canvasPane.setMaxSize(width, height);
    }

    /// Longest line width ignoring SGR escapes, so the snapshot terminal is
    /// wide enough not to re-wrap the captured lines.
    private static int longestVisibleLine(String snapshot) {
        return snapshot.lines()
                .mapToInt(line -> line.replaceAll("\\u001b\\[[0-9;:]*[A-Za-z]", "").length())
                .max()
                .orElse(0);
    }

    /// Covers the live terminal with a "Switching…" placeholder during a
    /// fast-path window change, clearing the stale header title too.
    private void showSwitchingOverlay(@Nullable String window) {
        switchingLabel.setText(window == null ? "Switching…" : "Switching to window " + window + " …");
        title.setText("");
        if (!center.getChildren().contains(switchingOverlay)) {
            center.getChildren().add(switchingOverlay);
        }
    }

    /// Blanks the pane **now** and runs `load` once that blank is on screen.
    /// Everything a task click triggers runs in one FX event, so without the
    /// split the previous task's terminal stays visible until the whole
    /// selection (snapshot widget, pty teardown, …) has finished — which reads
    /// as the click doing nothing. `load` runs in the second animation frame:
    /// the first one renders the blank.
    // [impl->dsn~terminal-pane~14]
    public void blankThen(Runnable load) {
        if (pendingLoad != null) {
            pendingLoad.stop();
        }
        switchingLabel.setText("");
        title.setText("");
        if (!center.getChildren().contains(switchingOverlay)) {
            center.getChildren().add(switchingOverlay);
        }
        blanked = true;
        AnimationTimer timer = new AnimationTimer() {
            private int frames;

            @Override
            public void handle(long now) {
                if (++frames < 2) {
                    return;
                }
                stop();
                pendingLoad = null;
                boolean focus = focusOnAttach;
                load.run();
                if (focus) {
                    focusTerminal();
                }
            }
        };
        pendingLoad = timer;
        timer.start();
    }

    /// Mirrors the task's tmux window. The mirror in view takes it on its fast
    /// path when it already serves the task's host and session
    /// ([TmuxSession#select]); anything else is a fresh [TmuxSession].
    /// A user selection, so the reconnect budget starts over either way.
    public void show(String remote, Task.TmuxConfig tmux) {
        // Blank the mode label until the next poll tick reports this window's:
        // the previous task's model next to the new task's terminal would be a
        // lie for as long as a poll interval (`dsn~claude-mode-report~2`).
        modeLabel.setText("");
        TerminalSession shown = session;
        if (!(shown instanceof TmuxSession mirror && mirror.select(remote, tmux))) {
            disconnect();
            TmuxSession fresh = new TmuxSession(executor, ssh, view, remote, tmux);
            session = fresh;
            shown = fresh;
            fresh.attach();
        }
        enableBar(shown);
    }

    /// Shows the app-owned session `key` again when its process is still
    /// running — the pane keeps the session, so the scrollback and the running
    /// Claude come back untouched after a look at another task. False when
    /// there is no such session (or it has exited), which is the caller's cue
    /// to offer a start/resume instead.
    // [impl->dsn~terminal-owned-session~3]
    public boolean showOwnedIfRunning(String key) {
        OwnedSession running = owned.get(key);
        if (running == null) {
            return false;
        }
        if (!running.isAlive()) {
            owned.remove(key);
            running.close();
            return false;
        }
        showOwned(running);
        return true;
    }

    /// Starts a Claude session the **app** owns in a ConPTY rooted at `cwd`
    /// ([OwnedSession#start]) and shows it.
    // [impl->dsn~terminal-owned-session~3]
    public void startOwned(String key, List<String> command, String cwd) {
        disconnect();
        Logger.info("Starting an app-owned Claude session in {}: {}", cwd,
                String.join(" ", command));
        try {
            OwnedSession started = OwnedSession.start(key, command, cwd, view);
            owned.put(key, started);
            showOwned(started);
            focusTerminal();
        } catch (IOException | RuntimeException e) {
            Logger.warn("Cannot start an owned Claude session in {}: {}", cwd, e.getMessage());
            showMessage("Cannot start Claude in " + cwd + ": " + e.getMessage());
        }
    }

    /// Puts an owned session on screen in place of whatever was shown: the
    /// bar buttons take its local routes, no mode label (an owned session
    /// publishes no tmux options), no title until the session sets one.
    // [impl->dsn~terminal-owned-session~3]
    private void showOwned(OwnedSession shown) {
        if (session != shown) {
            disconnect();
        }
        session = shown;
        enableBar(shown);
        modeLabel.setText("");
        title.setText("");
        center.getChildren().setAll(shown.widget().getPane());
    }

    /// Types `text` into the app-owned session `key` and submits it
    /// ([OwnedSession#send]) — the message queue's delivery for a chat no tmux
    /// holds. Blocking, for the background executor.
    // [impl->dsn~terminal-owned-session~3]
    public SendResult sendToOwned(String key, String text) {
        OwnedSession target = owned.get(key);
        if (target == null || !target.isAlive()) {
            return new SendResult.Failed(
                    "No Claude session is running for this task — start or resume it in the terminal.");
        }
        return target.send(text);
    }

    /// Ends every owned session — application shutdown only. The app owns
    /// these processes; leaving them behind would orphan a Claude with no
    /// terminal attached to it.
    // [impl->dsn~terminal-owned-session~3]
    public void closeOwned() {
        TerminalSession shown = session;
        if (shown != null && owned.containsValue(shown)) {
            session = null;
        }
        owned.values().forEach(OwnedSession::close);
        owned.clear();
    }

    /// A placeholder with one button under it — the owned session's "start" /
    /// "resume" offer, the same shape [#showStartWindowMessage] uses for the
    /// remote window choice.
    // [impl->dsn~terminal-owned-session~3]
    public void showActionMessage(String text, String label, Runnable action) {
        showMessage(text);
        Button button = new Button(label);
        button.setId("owned-session-button");
        button.getStyleClass().add(Styles.SMALL);
        button.setOnAction(event -> {
            button.setDisable(true);
            action.run();
        });
        VBox box = new VBox(8, message, button);
        box.setAlignment(Pos.CENTER);
        center.getChildren().setAll(box);
    }

    /// Forwards keys JediTermFX drops to the tty: `Ctrl`+letter as the matching
    /// control byte (`Ctrl+A`=1 … `Ctrl+Z`=26, including `Ctrl+C`=3, which
    /// JediTermFX's Windows copy handling can otherwise swallow), and
    /// `Shift+Tab` as the reverse-tab escape `ESC [ Z` (CBT) that Claude Code's
    /// TUI uses to cycle modes, and `Escape` as `0x1b` (JediTermFX does not
    /// emit it, so `vi` never left insert mode), and `Shift`/`Alt+Enter` as
    /// `ESC CR` (meta+enter) so Claude Code inserts a newline instead of
    /// submitting.
    ///
    /// The filter is attached to the **outer pane**, not the canvas, on
    /// purpose: capture-phase runs an ancestor's filter before the canvas's
    /// own handlers, so it wins over JediTermFX (which consumes `Shift+Tab` and
    /// several `Ctrl` combos on the canvas) and over JavaFX focus traversal
    /// (which would otherwise steal `Shift+Tab` to move focus out of the
    /// terminal). Consuming keeps the key from reaching either. `Ctrl+Shift+…`
    /// (e.g. `Ctrl+Shift+C` copy) is left to JediTermFX — only unshifted
    /// `Ctrl`+letter is forwarded.
    // [impl->dsn~terminal-pane~14]
    private void installControlKeyForwarding(JediTermFxWidget terminal,
            com.techsenger.jeditermfx.core.TtyConnector connector) {
        terminal.getPane().addEventFilter(
                javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    javafx.scene.input.KeyCode code = event.getCode();
                    byte @Nullable [] bytes = null;
                    if (code == javafx.scene.input.KeyCode.ESCAPE && !event.isControlDown()
                            && !event.isAltDown() && !event.isMetaDown()) {
                        bytes = new byte[]{0x1b};
                    } else if (code == javafx.scene.input.KeyCode.TAB && event.isShiftDown()
                            && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown()) {
                        bytes = new byte[]{0x1b, '[', 'Z'};
                    } else if (code == javafx.scene.input.KeyCode.ENTER
                            && (event.isShiftDown() || event.isAltDown())
                            && !event.isControlDown() && !event.isMetaDown()) {
                        // ESC CR = meta+enter, which Claude Code (and most
                        // readline TUIs) insert as a newline instead of
                        // submitting; JediTermFX sends a bare CR for all
                        // Enter variants, so every one of them submitted.
                        bytes = new byte[]{0x1b, '\r'};
                    } else if (event.isControlDown() && !event.isAltDown()
                            && !event.isShiftDown() && !event.isMetaDown() && code.isLetterKey()) {
                        bytes = new byte[]{(byte) (code.getChar().toUpperCase().charAt(0) - 'A' + 1)};
                    }
                    if (bytes == null) {
                        return;
                    }
                    try {
                        connector.write(bytes);
                    } catch (java.io.IOException e) {
                        Logger.debug("Cannot forward key {}: {}", code, e.getMessage());
                    }
                    event.consume();
                });
    }

    /// Makes a plain (unmodified) mouse selection local, the way every other
    /// terminal behaves: drag highlights, double-click picks the word,
    /// triple-click the line, and `TerminalSettings.copyOnSelect` puts it on
    /// the system clipboard. Without this the mirror's tmux mouse mode
    /// swallowed the drag into tmux copy-mode — the highlight vanished on
    /// release and the text landed in tmux's *remote* buffer, so only
    /// `Shift`+drag (JediTermFX's mouse-reporting bypass) ever reached the
    /// clipboard.
    ///
    /// Same shape as [#installWheelDirectionFix]: a capture-phase filter on
    /// the outer pane consumes a button event JediTermFX would report to the
    /// tty and re-fires it at the canvas with `Shift` set, which is exactly
    /// the bypass JediTermFX already implements (`isLocalMouseAction`) — so
    /// its own selection, word/line and copy handling runs unchanged. The
    /// guard flag lets the re-fired event through (dispatch is synchronous).
    ///
    /// The trade-off: buttons no longer reach the remote at all, so a click
    /// cannot select a tmux pane or hit a widget in a TUI. The wheel is left
    /// alone and still drives tmux's scrollback, which is what the mirror's
    /// mouse mode is there for. Hyperlink clicks are unaffected — JediTermFX
    /// checks the hovered link before the selection path.
    // [impl->dsn~terminal-mouse-scroll~9]
    private void installLocalSelection(JediTermFxWidget terminal) {
        boolean[] refiring = {false};
        Node canvas = terminal.getTerminalPanel().getCanvas();
        terminal.getPane().addEventFilter(MouseEvent.ANY, event -> {
            var type = event.getEventType();
            if (refiring[0]
                    || (type != MouseEvent.MOUSE_PRESSED && type != MouseEvent.MOUSE_DRAGGED
                            && type != MouseEvent.MOUSE_RELEASED && type != MouseEvent.MOUSE_CLICKED)
                    || !terminal.getTerminalPanel().isRemoteMouseAction(event)
                    || !onCanvas(event, canvas)) {
                return;
            }
            var local = canvas.sceneToLocal(event.getSceneX(), event.getSceneY());
            event.consume();
            MouseEvent shifted = new MouseEvent(type, local.getX(), local.getY(),
                    event.getScreenX(), event.getScreenY(), event.getButton(), event.getClickCount(),
                    true, event.isControlDown(), event.isAltDown(), event.isMetaDown(),
                    event.isPrimaryButtonDown(), event.isMiddleButtonDown(),
                    event.isSecondaryButtonDown(), event.isSynthesized(), event.isPopupTrigger(),
                    event.isStillSincePress(), event.getPickResult());
            refiring[0] = true;
            try {
                javafx.event.Event.fireEvent(canvas, shifted);
            } finally {
                refiring[0] = false;
            }
        });
    }

    /// Shows the terminal's scrollbar only while JediTermFX holds a scrollback
    /// to scroll through.
    ///
    /// Claude Code runs in the alternate screen buffer, and so does a tmux
    /// client: that buffer has no scrollback, so JediTermFX pins the scrollbar
    /// to one full screen and the thumb fills the whole track. The wheel still
    /// scrolls — the program redraws its own transcript — but it never tells
    /// the terminal where it is, so there is nothing a scrollbar could show.
    /// JediTermFX marks a real scrollback with a negative minimum (the history
    /// lines above the screen), which is what the visibility follows; a plain
    /// shell with history, e.g. after Claude exits, gets its scrollbar back.
    ///
    /// Only hidden, not unmanaged: taking it out of the layout would widen the
    /// canvas by a column or two and resize the pty every time a program
    /// enters or leaves the alternate buffer.
    // [impl->dsn~terminal-mouse-scroll~9]
    private static void hideScrollbarWithoutScrollback(JediTermFxWidget terminal) {
        for (Node child : terminal.getTerminalPanel().getPane().getChildrenUnmodifiable()) {
            if (child instanceof ScrollBar scrollBar) {
                scrollBar.visibleProperty().bind(scrollBar.minProperty().lessThan(0));
            }
        }
    }

    /// Whether `event` happened on the terminal canvas itself. The filter above
    /// sits on the widget's pane, which also holds the scrollbar; without this
    /// a click or drag on the scrollbar was turned into a text selection on the
    /// canvas instead of scrolling. A drag that started on the canvas keeps the
    /// canvas as its target when it crosses the scrollbar, so a selection can
    /// still be dragged past the edge.
    // [impl->dsn~terminal-mouse-scroll~9]
    private static boolean onCanvas(MouseEvent event, Node canvas) {
        return event.getTarget() == canvas;
    }

    /// `Ctrl`+wheel zooms the terminal font, the gesture every browser and
    /// IDE has; `Ctrl`+`0` puts it back to JediTermFX's 14 points.
    ///
    /// The size lives in [TerminalZoom] (one size for the whole app, kept in
    /// `Preferences`) and is read back by `TerminalSettings.getTerminalFontSize`,
    /// so a zoom takes effect wherever a widget rebuilds its font — which is
    /// what [PanePanel#refreshFont] makes it do, for **every** live widget and
    /// not only the one under the pointer: the sessions of other tasks are
    /// kept alive off-screen ([#owned]) and must not come back in the old size.
    ///
    /// The wheel-direction filter leaves a `Ctrl`+wheel alone (a consumed
    /// event still reaches the filters registered next to it, so it guards
    /// itself). A mirror's resize goes on to tmux through the pty, which
    /// reflows the remote window — that is the same path a window resize
    /// already takes.
    // [impl->dsn~terminal-zoom~4]
    private void installZoom(JediTermFxWidget terminal) {
        terminal.getPane().addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown() || Math.abs(event.getDeltaY()) < 0.01) {
                return;
            }
            event.consume();
            if (TerminalZoom.zoom(event.getDeltaY() > 0 ? 1 : -1)) {
                refreshFonts();
            }
        });
        terminal.getPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && (event.getCode() == KeyCode.DIGIT0
                    || event.getCode() == KeyCode.NUMPAD0)) {
                event.consume();
                if (TerminalZoom.reset()) {
                    refreshFonts();
                }
            }
        });
    }

    /// Rebuilds the font of every widget this pane has built and not dropped.
    // [impl->dsn~terminal-zoom~4]
    private void refreshFonts() {
        for (JediTermFxWidget widget : List.copyOf(zoomed.keySet())) {
            if (widget.getTerminalPanel() instanceof PanePanel panel) {
                panel.refreshFont();
            }
        }
    }

    /// The pane's own `TerminalPanel`: it re-reads the font on demand (the
    /// library only does so on a resize) and, for a mirror, copies Claude's
    /// text as the Markdown it was written in.
    // [impl->dsn~terminal-zoom~4]
    // [impl->dsn~terminal-markdown-copy~1]
    private final class PanePanel extends TerminalPanel {

        private final boolean mirror;

        private PanePanel(SettingsProvider provider, TerminalTextBuffer buffer, StyleState style,
                boolean mirror) {
            super(provider, buffer, style);
            this.mirror = mirror;
        }

        /// Re-asks the settings for the font and resizes to it — `protected`
        /// in JediTermFX, which is why this class exists.
        private void refreshFont() {
            reinitFontAndResize();
        }

        @Override
        protected TerminalCopyPasteHandler createCopyPasteHandler() {
            return mirror ? new MarkdownCopyHandler() : super.createCopyPasteHandler();
        }
    }

    /// Corrects the mouse-wheel direction under tmux mouse reporting:
    /// JediTermFX 1.1.0 builds the xterm wheel report with Swing's sign
    /// convention (`FxMouseWheelEvent`: positive = towards the user), but a
    /// JavaFX `deltaY > 0` means wheel **up** — so a wheel-up reached tmux as
    /// button 5 (wheel-down) and the scrollback moved inverted.
    ///
    /// The capture-phase filter on the outer pane consumes a scroll that
    /// JediTermFX would report to the tty (`isRemoteMouseAction`: mouse
    /// reporting active and `Shift` not held) and re-fires it at the canvas
    /// with the Y deltas negated, so JediTermFX's own cell-coordinate and
    /// protocol handling runs with the correct sign; the guard flag lets the
    /// re-fired event pass (dispatch is synchronous). Local scrolling
    /// (`Shift`+wheel, or no mouse reporting) is left alone — that path
    /// already negates correctly. Remove once a jeditermfx release carries
    /// the upstream fix (techsenger/jeditermfx#24, PR #25).
    // [impl->dsn~terminal-mouse-scroll~9]
    private void installWheelDirectionFix(JediTermFxWidget terminal) {
        boolean[] refiring = {false};
        terminal.getPane().addEventFilter(ScrollEvent.SCROLL, event -> {
            if (refiring[0] || event.isControlDown() || Math.abs(event.getDeltaY()) < 0.01
                    || !terminal.getTerminalPanel().isRemoteMouseAction(event)) {
                return;
            }
            event.consume();
            ScrollEvent inverted = new ScrollEvent(ScrollEvent.SCROLL,
                    event.getSceneX(), event.getSceneY(), event.getScreenX(), event.getScreenY(),
                    event.isShiftDown(), event.isControlDown(), event.isAltDown(), event.isMetaDown(),
                    event.isDirect(), event.isInertia(),
                    event.getDeltaX(), -event.getDeltaY(),
                    event.getTotalDeltaX(), -event.getTotalDeltaY(),
                    event.getTextDeltaXUnits(), event.getTextDeltaX(),
                    event.getTextDeltaYUnits(), -event.getTextDeltaY(),
                    event.getTouchCount(), null);
            refiring[0] = true;
            try {
                javafx.event.Event.fireEvent(terminal.getTerminalPanel().getCanvas(), inverted);
            } finally {
                refiring[0] = false;
            }
        });
    }

    /// Puts the keyboard focus into the live terminal, so a task click lets
    /// the user type straight away. `runLater` so it runs after the ListView's
    /// own click-focus grab. With no terminal up yet (a placeholder, or an
    /// attach still in flight) the request is remembered and the attach
    /// honours it on completion ([TerminalSession.View#focusNewTerminal]).
    // [impl->dsn~terminal-pane~14]
    public void focusTerminal() {
        TerminalSession shown = session;
        JediTermFxWidget terminal = shown != null ? shown.widget() : snapshotView;
        if (terminal == null || pendingLoad != null) {
            focusOnAttach = true;
            return;
        }
        Platform.runLater(() -> terminal.getTerminalPanel().getCanvas().requestFocus());
    }

    /// Ends what the pane shows (idempotent): the session in view is detached
    /// — a mirror's pty ends, and the exit that causes is not taken for a lost
    /// connection ([TmuxSession#detach]); an owned session keeps running in
    /// [#owned] — and a snapshot's terminal closes. Called on placeholder
    /// switches and on application shutdown.
    public void disconnect() {
        TerminalSession shown = session;
        session = null;
        if (shown != null) {
            shown.detach();
        }
        JediTermFxWidget snapshot = snapshotView;
        snapshotView = null;
        if (snapshot != null) {
            snapshot.close();
        }
        blanked = false;
    }
}
