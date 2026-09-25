package com.contextswitcher;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.contextswitcher.analysis.RefactoringLookup;
import com.contextswitcher.analysis.RefactoringMinerCommands;
import com.contextswitcher.analysis.RefactoringMinerPoller;
import com.contextswitcher.analysis.RefactoringMinerView;
import com.contextswitcher.config.AppSettings;
import com.contextswitcher.config.EnergySaver;
import com.contextswitcher.config.WindowPositions;
import com.contextswitcher.config.YamlPatch;
import com.contextswitcher.extension.DeepLink;
import com.contextswitcher.extension.DeepLinkForwarder;
import com.contextswitcher.extension.ExtensionProtocol;
import com.contextswitcher.extension.ExtensionServer;
import com.contextswitcher.discovery.ClaudePrLookup;
import com.contextswitcher.discovery.ClaudeSessionCleanup;
import com.contextswitcher.discovery.ClaudeSessionLookup;
import com.contextswitcher.discovery.TmuxDiscovery;
import com.contextswitcher.discovery.TmuxPrPoller;
import com.contextswitcher.discovery.WorkspacePrLookup;
import com.contextswitcher.discovery.TmuxStatusPoller;
import com.contextswitcher.discovery.TmuxTitlePoller;
import com.contextswitcher.discovery.TmuxSync;
import com.contextswitcher.discovery.TmuxTaskImporter;
import com.contextswitcher.local.AppUpdate;
import com.contextswitcher.config.Browser;
import com.contextswitcher.local.BrowserDesktopWindows;
import com.contextswitcher.local.BrowserWindowFocus;
import com.contextswitcher.local.JetBrainsClientFocus;
import com.contextswitcher.local.WindowsDesktopPin;
import com.contextswitcher.local.LocalFolderFocus;
import com.contextswitcher.local.WindowsVirtualDesktopFocus;
import com.contextswitcher.local.ClaudeCliSummarizer;
import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.local.RequiredTools;
import com.contextswitcher.tasks.TextFiles;
import com.contextswitcher.tasks.title.FallbackSummarizer;
import com.contextswitcher.tasks.title.FirstWordsSummarizer;
import com.contextswitcher.tasks.title.InitialTitleAdopter;
import com.contextswitcher.provenance.ProvenanceConfig;
import com.contextswitcher.provenance.ProvenanceRecorder;
import com.contextswitcher.queue.ClaudeMode;
import com.contextswitcher.queue.MessageSender;
import com.contextswitcher.queue.QodoImported;
import com.contextswitcher.queue.QueueFile;
import com.contextswitcher.ssh.ProcessSshRunner;
import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.switching.ActionResult;
import com.contextswitcher.switching.ActionStatus;
import com.contextswitcher.switching.BrowserCloseAction;
import com.contextswitcher.switching.ChatFocusAction;
import com.contextswitcher.switching.ClaudeWindowLauncher;
import com.contextswitcher.switching.BrowserFocusAction;
import com.contextswitcher.switching.ExplorerFolderAction;
import com.contextswitcher.switching.IntellijGatewayAction;
import com.contextswitcher.switching.LocalDiff;
import com.contextswitcher.switching.LocalTerminalAction;
import com.contextswitcher.switching.AutoPrLookup;
import com.contextswitcher.switching.AutoPrPoller;
import com.contextswitcher.switching.NoteFocusAction;
import com.contextswitcher.switching.PrStateLookup;
import com.contextswitcher.switching.PrStatePoller;
import com.contextswitcher.switching.PrTitleLookup;
import com.contextswitcher.switching.QodoReviewLookup;
import com.contextswitcher.switching.QodoReviewPoller;
import com.contextswitcher.switching.RemoteIdeLookup;
import com.contextswitcher.switching.SwitchOrchestrator;
import com.contextswitcher.switching.TaskDiffWindow;
import com.contextswitcher.switching.TmuxFocusAction;
import com.contextswitcher.switching.TmuxKillAction;
import com.contextswitcher.switching.TmuxResurrect;
import com.contextswitcher.switching.TmuxWindowOwnership;
import com.contextswitcher.tasks.Gitkeep;
import com.contextswitcher.tasks.GitRemote;
import com.contextswitcher.tasks.GroupConfig;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.terminal.PaneSnapshots;
import com.contextswitcher.terminal.WindowsTerminalFocus;
import com.contextswitcher.queue.SendResult;
import com.contextswitcher.terminal.ChatRoute;
import com.contextswitcher.terminal.LocalClaudeLauncher;
import com.contextswitcher.terminal.HostCommandRunner;
import com.contextswitcher.terminal.LocalTmuxRunner;
import com.contextswitcher.terminal.WslTmuxRunner;
import com.contextswitcher.terminal.TmuxHost;
import com.contextswitcher.tasks.MergedCleanup;
import com.contextswitcher.tasks.TaskEntry;
import com.contextswitcher.tasks.TaskFileParser;
import com.contextswitcher.tasks.TaskGitBackup;
import com.contextswitcher.tasks.TaskSync;
import com.contextswitcher.tasks.TaskRepository;
import com.contextswitcher.tasks.TaskStatus;
import com.contextswitcher.ui.Alerts;
import com.contextswitcher.ui.AnsiTextWindow;
import com.contextswitcher.ui.MainWindow;
import com.contextswitcher.ui.SetupWizard;
import com.contextswitcher.ui.WhatsNew;
import com.contextswitcher.ui.QueuePane;
import com.contextswitcher.ui.TerminalPane;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

public class Main extends Application {

    /// Seconds between `@cs_status` polls for the running indicator.
    private static final long STATUS_POLL_SECONDS = 5;

    /// Seconds between reads of the active virtual desktop, so the "show active
    /// desktop only" filter follows desktop switches. Only ticks (spawns
    /// `powershell`) while the filter is on or the window is pinned to all
    /// desktops (the per-desktop position rides the same read).
    /// [impl->dsn~active-desktop-filter~6]
    private static final long ACTIVE_DESKTOP_POLL_SECONDS = 2;

    /// Seconds between `gh` PR-state polls (https://github.com/contextswitcher/contextswitcher-private/issues/49); long — PR state changes
    /// slowly and each tick hits the GitHub API once per PR.
    private static final long PR_STATE_POLL_SECONDS = 120;

    /// Seconds between `@cs_pr` / footer PR-capture polls (https://github.com/contextswitcher/contextswitcher-private/issues/46); short, so a
    /// PR Claude just opened shows up quickly. Cheap `@cs_pr` read per host,
    /// plus a footer `capture-pane` only for still-PR-less live Claude tasks.
    private static final long PR_REFRESH_POLL_SECONDS = 15;

    /// PR-refresh ticks between two `gh pr view` rounds for still-PR-less
    /// workspaces; that lookup hits the GitHub API once per such task, so it
    /// rides the cheap poller at a much slower cadence (~2 min).
    private static final int WORKSPACE_PR_EVERY_NTH_TICK = 8;

    /// The workspace PR lookup runs one `gh pr view` per workspace inside a
    /// single ssh call, sequentially on the remote — a GitHub API round-trip
    /// each — so its runner gets a timeout sized for the whole batch rather
    /// than the default one call's.
    private static final Duration WORKSPACE_PR_SSH_TIMEOUT = Duration.ofSeconds(90);

    /// One attachment upload: `MessageSender.MAX_UPLOAD_BYTES` of file is a
    /// third more of base64 through one ssh stdin, which the side channel's
    /// 10 s — sized for a tmux round-trip — never fitted (field report
    /// 2026-09-13). Five minutes carries the largest allowed file at ~300 KB/s.
    private static final Duration UPLOAD_SSH_TIMEOUT = Duration.ofMinutes(5);

    /// Seconds between `gh` qodo-review polls; long — a review lands minutes
    /// after a push and each tick hits the GitHub API once per PR.
    private static final long QODO_POLL_SECONDS = 120;

    /// Seconds between the PR searches of the auto categories
    /// (`dsn~auto-pr-lookup~2`); long — one GitHub search per auto category
    /// and tick, and a PR worth reviewing is not minutes-critical.
    private static final long AUTO_PR_POLL_SECONDS = 600;

    /// Seconds between refactoring-count polls (https://github.com/contextswitcher/contextswitcher-private/issues/50); long — a tick is one
    /// cheap `rev-parse` per idle task, but a moved HEAD runs a full
    /// RefactoringMiner analysis on the remote.
    private static final long REFACTORING_POLL_SECONDS = 120;

    /// Timeout for the refactoring poller's ssh runs: a RefactoringMiner
    /// analysis of a long commit range takes minutes, not the ssh default 10 s.
    private static final Duration REFACTORING_SSH_TIMEOUT = Duration.ofMinutes(5);

    private @Nullable TaskRepository repository;
    private @Nullable TaskGitBackup taskBackup;
    /// Sync groups and the single thread their rounds run on, so two rounds
    /// never share a clone (`dsn~task-sync-groups-ui~1`).
    private @Nullable TaskSync taskSync;
    private Supplier<CompletableFuture<Void>> syncRound = () -> CompletableFuture.completedFuture(null);
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "task-sync");
        thread.setDaemon(true);
        return thread;
    });
    private @Nullable ExecutorService actionExecutor;
    /// Shortens a local Claude task's description-as-title once it is created.
    // [impl->dsn~task-create-local-title~1]
    private @Nullable InitialTitleAdopter initialTitles;
    /// Fires the one-shot post-create sync (`dsn~tmux-sync~7`) — a daemon so a
    /// pending 30 s wait cannot hold the JVM open after the window closes.
    private @Nullable ScheduledExecutorService scheduler;
    private @Nullable Thread fxWatchdog;
    private @Nullable MainWindow window;
    /// Number of async task creations (live, PR) currently running, so the
    /// status bar can report the parallel ones instead of the last one
    /// silently overwriting the others. FX thread only.
    /// Observable, so the terminal bar and *Restart to update* can stay
    /// disabled while any creation runs (`dsn~busy-while-creating~2`).
    // [impl->dsn~task-create-progress~5]
    // [impl->dsn~busy-while-creating~2]
    private final IntegerProperty creationsInFlight = new SimpleIntegerProperty();
    /// The step each still-windowless creation is on, so the terminal lane of
    /// a just-created (and now selected) task shows the launch coming up
    /// instead of "No tmux configured for this task.". An entry is dropped as
    /// soon as the window exists — the mirror itself takes over from there.
    /// FX thread only.
    // [impl->dsn~task-create-progress~5]
    private final Map<String, String> creationSteps = new HashMap<>();
    /// Creations whose tmux window already exists: from there on their steps
    /// go to the row's progress bar only, never into the terminal lane, which
    /// is by then attached to the fresh window.
    /// FX thread only.
    // [impl->dsn~task-create-progress~5]
    private final Set<String> windowedCreations = new HashSet<>();
    /// True while the "show active desktop only" filter is on; gates the active-
    /// desktop poll so it spawns `powershell` only when the filter is in use.
    // [impl->dsn~active-desktop-filter~6]
    private volatile boolean watchActiveDesktop;
    /// The persistent active-desktop watcher process — one start attempt ever
    /// (`desktopWatchAttempted`); while it is live the periodic poll skips its
    /// one-shot `powershell` reads, and if it dies the poll takes over again.
    // [impl->dsn~active-desktop-filter~6]
    private final AtomicBoolean desktopWatchAttempted = new AtomicBoolean();
    private volatile WindowsVirtualDesktopFocus.@Nullable Watch desktopWatch;
    private volatile boolean desktopWatchLive;
    /// True while the window is pinned to all desktops on Windows
    /// (`showOnAllDesktops`); the same poll then also drives the per-desktop
    /// window position. Refreshed on a settings save, like the pin itself.
    // [impl->dsn~window-position-per-desktop~2]
    private volatile boolean trackWindowPosition;
    /// The virtual desktop a category with no (or an unknown) `desktop:` is
    /// focused on — `fallbackDesktop` in `settings.yaml`, blank = no fallback.
    /// Refreshed on a settings save, like the pin above.
    // [impl->dsn~fallback-desktop~1]
    private volatile String fallbackDesktop = AppSettings.DEFAULT_FALLBACK_DESKTOP;

    /// `autoSuspendMinutes` / `mergedCleanupDays` in `settings.yaml`, refreshed
    /// on a settings save like the fallback above, so a lowered threshold
    /// starts suspending at the next status poll instead of the next start.
    // [impl->dsn~auto-suspend-idle~2]
    private volatile int autoSuspendMinutes;
    private volatile int mergedCleanupDays;

    /// The configured browser — `browser` in `settings.yaml`, refreshed on a
    /// settings save like the fallback above. It is the *fallback* for
    /// [#drivenBrowser()], which prefers whichever extension is connected.
    // [impl->dsn~browser-choice~2]
    private volatile Browser browser = Browser.DEFAULT;
    /// The desktop the window geometry currently belongs to — the last desktop
    /// the poll reported. FX thread only.
    // [impl->dsn~window-position-per-desktop~2]
    private @Nullable String positionDesktop;
    private @Nullable Stage stage;
    private @Nullable WindowPositions windowPositions;
    private @Nullable TmuxStatusPoller statusPoller;
    private @Nullable PrStatePoller prStatePoller;
    private @Nullable TmuxPrPoller tmuxPrPoller;

    /// PR-refresh ticks so far, for the slower `gh` workspace lookup's cadence.
    /// FX thread only.
    private int prTicks;

    /// Workspaces whose branch PR the `gh` lookup has already found — the
    /// bound that keeps it from asking GitHub about the same branch forever.
    /// Not "tasks that have a PR": a task whose `browser.urls` came from the
    /// prompt (the PR it references) would then never get its own.
    /// Written from the lookup's executor threads.
    // [impl->dsn~claude-pr-refresh~5]
    private final Set<String> workspacePrsFound = ConcurrentHashMap.newKeySet();
    private @Nullable TmuxTitlePoller tmuxTitlePoller;
    private @Nullable QodoReviewPoller qodoReviewPoller;
    private @Nullable AutoPrPoller autoPrPoller;

    /// Whether the initial task-directory scan has finished — the auto
    /// categories' gate (see the scan below).
    private volatile boolean tasksScanned;
    private @Nullable RefactoringMinerPoller refactoringPoller;
    private @Nullable RefactoringMinerView refactoringView;
    private @Nullable ExtensionServer extensionServer;
    private @Nullable TerminalPane terminalPane;
    private @Nullable PaneSnapshots paneSnapshots;

    /// Hosts to poll for `@cs_status`; recomputed on the FX thread whenever the
    /// task list changes, read (volatile) from the poller thread.
    private volatile Set<String> statusHosts = Set.of();

    /// PR URLs to poll for state (https://github.com/contextswitcher/contextswitcher-private/issues/49); recomputed on the FX thread on any task
    /// list change, read (volatile) from the poller thread.
    private volatile Set<String> prUrls = Set.of();

    /// Task id → PR URL, for the qodo-review poll (which must know *which*
    /// task's queue to append prompts to); recomputed alongside [#prUrls].
    private volatile Map<String, List<String>> prUrlsByTask = Map.of();

    /// Every PR URL a category's tasks carry, by category — the auto
    /// categories' retention input, and deliberately *not* narrowed to the
    /// rows on screen: a collapsed category's tasks are reconciled too.
    // [impl->dsn~auto-pr-category~3]
    private volatile Map<String, List<String>> categoryPrUrls = Map.of();

    /// Status-poll key (`host windowId`) → the PR URLs of the tasks in that
    /// window, for the stop-triggered PR-state refresh; recomputed alongside
    /// [#prUrls].
    // [impl->dsn~pr-state-refresh-on-stop~2]
    private volatile Map<String, List<String>> prUrlsByWindow = Map.of();

    /// Tasks the refactoring poller may analyze (https://github.com/contextswitcher/contextswitcher-private/issues/50); recomputed on the FX
    /// thread on any task list change, read (volatile) from the poller thread.
    private volatile List<RefactoringMinerPoller.Target> refactoringTargets = List.of();

    /// The latest raw `@cs_status` map (`host windowId -> status`), stashed
    /// off the status poller for the refactoring poller's idle gate — the
    /// authoritative copy lives in [MainWindow], which the poller thread
    /// cannot ask.
    private volatile Map<String, String> liveStatuses = Map.of();

    /// The latest published `@cs_session_id` per window (`host windowId ->
    /// Claude session id`), from the same status poll. Windows that publish
    /// nothing are absent, so a lookup miss means "no evidence", never "wrong
    /// session" ([TmuxWindowOwnership]).
    // [impl->dsn~tmux-window-ownership~4]
    private volatile Map<String, String> liveSessionIds = Map.of();

    /// JavaFX's CSS engine cannot convert `-fx-background-color` in the
    /// svgnode library's own `svg.css`, and warns about it on every start.
    /// The stylesheet ships inside that jar, so there is nothing to fix here —
    /// drop just those records. Held in a field because `java.util.logging`
    /// collects loggers nobody references.
    private static final java.util.logging.Logger CSS_LOGGER = java.util.logging.Logger.getLogger("javafx.css");

    static {
        CSS_LOGGER.setFilter(record -> record.getMessage() == null || !record.getMessage().contains("svgnode"));
        // Everything else JavaFX's CSS engine says goes into the log file, not
        // only onto the console of the run loop: a stylesheet it could not
        // load or a variable it could not resolve — the records that tell an
        // unthemed start's cause apart (dsn~theme-select~8, 2026-09-16). At
        // debug: a themed start produces about ninety of them (value
        // conversions in library stylesheets and Modena's), which belong in
        // the file, not on the console.
        CSS_LOGGER.setUseParentHandlers(false);
        CSS_LOGGER.addHandler(new java.util.logging.Handler() {
            private final java.util.logging.Formatter format = new java.util.logging.SimpleFormatter();

            @Override
            public void publish(java.util.logging.LogRecord record) {
                Logger.debug("JavaFX CSS: {}", format.formatMessage(record));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    /// SnakeYAML warns about duplicate keys through `java.util.logging` too —
    /// unbridged here, so it bypasses tinylog and lands raw on the console
    /// naming the key but no file (`WARNING: duplicate keys found : desktop`),
    /// because the constructor only ever sees YAML text. The same duplicates
    /// are reported *with* their file and line by `TaskRepository`, the editor
    /// lane and the category header, so this unattributable copy is dropped
    /// rather than left to be the one message the user actually sees.
    /// Held in a field for the same reason as [#CSS_LOGGER].
    // [impl->dsn~frontmatter-duplicate-keys~1]
    private static final java.util.logging.Logger SNAKEYAML_LOGGER =
            java.util.logging.Logger.getLogger("org.yaml.snakeyaml");

    static {
        SNAKEYAML_LOGGER.setLevel(java.util.logging.Level.OFF);
    }

    public static void main(String[] args) {
        // A deep-link launch (`contextswitcher://…` from the OS protocol
        // handler, https://github.com/contextswitcher/contextswitcher-private/issues/47) first tries to hand the URL to an already-running
        // instance; only when none answers does this process start normally
        // and handle the URL itself after startup.
        // [impl->dsn~deep-link-forward~1]
        String deepLinkUrl = Arrays.stream(args).filter(DeepLink::isDeepLink).findFirst().orElse(null);
        if (deepLinkUrl != null) {
            try {
                AppSettings settings = AppSettings.loadOrCreate(configDir());
                if (DeepLinkForwarder.forward(settings.wsPort(), settings.wsToken(), deepLinkUrl)) {
                    return;
                }
            } catch (IOException e) {
                Logger.warn("Cannot read settings for deep-link forwarding: {}", e.getMessage());
            }
        }
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        // An exception on the FX thread or on one of the virtual action
        // threads otherwise only reaches stderr, which the packaged app drops
        // — a blank row, a dead button or a lookup that quietly stopped
        // running would leave no trace in ~/.contextswitcher/logs, and none at
        // all for the user.
        // [impl->dsn~uncaught-exception-report~1]
        Thread.setDefaultUncaughtExceptionHandler(this::reportUncaught);
        // A stalled FX thread logs nothing by itself; the watchdog writes
        // its stack trace when events stop being serviced for a second.
        // [impl->dsn~fx-stall-log~1]
        this.fxWatchdog = FxStallWatchdog.start(Thread.currentThread(), Platform::runLater);
        // The heavyweight UI classes cost over a second of classpath scanning
        // on a cold Windows drive, logged by the watchdog as an FX stall inside
        // the TerminalPane constructor. Loading them on a daemon thread
        // overlaps that disk IO with the rest of startup; the FX thread later
        // finds them loaded (or waits only for the one still in flight).
        // [impl->dsn~startup-background~1]
        Thread classWarmup = new Thread(() -> {
            for (String name : new String[] {
                    "com.techsenger.jeditermfx.ui.JediTermFxWidget",
                    "jfx.incubator.scene.control.richtext.RichTextArea",
                    "com.contextswitcher.ui.TerminalPane",
                    "com.contextswitcher.ui.MainWindow"}) {
                try {
                    Class.forName(name);
                } catch (ClassNotFoundException | LinkageError e) {
                    Logger.debug("Class warm-up skipped {}: {}", name, e.toString());
                }
            }
        }, "class-warmup");
        classWarmup.setDaemon(true);
        classWarmup.start();
        AppSettings startupSettings = AppSettings.loadOrCreate(configDir());
        // The color theme from settings (light/dark/system) — sets the AtlantaFX
        // user-agent stylesheet the whole UI is drawn with.
        // [impl->dsn~theme-select~8]
        com.contextswitcher.ui.Themes.apply(startupSettings.theme());
        // The bash cursor chords in every text input, main window and dialogs
        // alike — installed unconditionally, switched by the setting.
        // [impl->dsn~readline-keys~1]
        com.contextswitcher.ui.ReadlineKeys.setEnabled(startupSettings.readlineKeys());
        com.contextswitcher.ui.ReadlineKeys.installEverywhere();
        // Before seeding an empty task directory: the first-start wizard —
        // where sessions run, the remote (checked), the task repo to clone
        // (moving to a new machine, MADR 0011), the first project. It writes
        // the remote into settings.yaml, so the settings are re-read after it.
        // [impl->dsn~setup-wizard~8]
        // [impl->dsn~task-git-clone-setup~2]
        AppSettings settings = runSetupWizard(startupSettings);
        // Same fresh-machine moment: say which command-line tools are missing
        // while the user is still setting the machine up.
        // [impl->dsn~startup-tool-check~1]
        warnAboutMissingTools(settings.tasksDir());
        ObservableList<TaskEntry> taskEntries = FXCollections.observableArrayList();
        ObservableSet<String> taskFolders = FXCollections.observableSet();
        TaskRepository repository = new TaskRepository(
                settings.tasksDir(), new TaskFileParser(), Platform::runLater, taskEntries, taskFolders);
        this.repository = repository;

        ExecutorService actionExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.actionExecutor = actionExecutor;
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cs-post-create-sync");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler = scheduler;
        // Memory growth curve in the log, for post-mortem after a field OOM.
        // Fixed *delay*, like every periodic job here — see
        // `dsn~poll-no-wake-backlog~1`.
        // [impl->dsn~memory-log~1]
        // [impl->dsn~poll-no-wake-backlog~1]
        scheduler.scheduleWithFixedDelay(() -> Logger.info("memory: {}", MemoryLog.line()),
                0, 10, TimeUnit.MINUTES);
        // Host-routed: everything tmux-facing addresses a host, and a local
        // Claude session's is the pseudo-host `TmuxHost.LOCAL` — its commands
        // run through a local shell, every real host's over ssh.
        // [impl->dsn~terminal-local-mirror~2]
        SshCommandRunner ssh = new HostCommandRunner(new ProcessSshRunner());
        // [impl->dsn~extension-server-protocol~4]
        ExtensionServer extensionServer = new ExtensionServer(settings.wsPort(), settings.wsToken());
        this.extensionServer = extensionServer;
        extensionServer.start();
        // Per-task message queues (+ the qodo-sourced sidecar) live inside the
        // task directory under a dot-prefixed `.queues/` the repository scanner
        // ignores like `.git`, so the task-dir git backup carries queued
        // messages across machines too. Migrate the old configDir locations in
        // once. [impl->dsn~message-queue-store~2]
        Path queueDir = settings.tasksDir().resolve(".queues");
        migrateQueuesIntoTaskDir(configDir(), queueDir);
        // Best-effort git sync of the task directory (pull at startup, commit
        // + push at close), active only when the user has made it a git repo.
        // [impl->dsn~task-git-backup~5]
        TaskGitBackup taskBackup = new TaskGitBackup(settings.tasksDir());
        this.taskBackup = taskBackup;
        // Scan + watch off the FX thread: the scan reads and parses every task
        // file — seconds of disk IO on a slow drive — and entries stream into
        // the list as they parse. The startup pull (what other machines pushed
        // while this one was closed, dsn~task-git-backup~5) stays behind the
        // watcher start, so the files it merges in are seen.
        // [impl->dsn~startup-background~1]
        actionExecutor.execute(() -> {
            try {
                repository.scan();
                // Only now may the auto categories reconcile: against a
                // half-loaded list every already-tracked PR would look new.
                // [impl->dsn~auto-pr-category~3]
                tasksScanned = true;
                repository.startWatching();
            } catch (IOException e) {
                Logger.error(e, "Task directory scan failed");
            }
            taskBackup.pullOnStartup();
            syncProvenance(settings.tasksDir());
        });
        // A round every few minutes, so the phone sees a recreated window or a
        // queued message soon, and this machine what the phone pushed.
        // [impl->dsn~task-git-backup~5]
        // [impl->dsn~energy-saver~1]
        scheduler.scheduleWithFixedDelay(() -> {
            if (!EnergySaver.active()) {
                taskBackup.syncWhileRunning();
                syncProvenance(settings.tasksDir());
            }
        }, TASK_BACKUP_MINUTES, TASK_BACKUP_MINUTES, TimeUnit.MINUTES);
        MainWindow.TaskFileAccess files = taskFileAccess(settings.tasksDir(), queueDir);
        // [impl->dsn~task-create-local-title~1]
        initialTitles = new InitialTitleAdopter(
                new FallbackSummarizer(new ClaudeCliSummarizer(), new FirstWordsSummarizer()),
                files, actionExecutor, Platform::runLater, fileName -> {
                    MainWindow shown = this.window;
                    if (shown != null) {
                        shown.reloadEditorIfShowing(fileName);
                    }
                });
        // Held on its own too: the category header's folder button opens the
        // same folders the switch action does, without a task in between.
        // [impl->dsn~category-folders-button~1]
        ExplorerFolderAction explorerFolders =
                new ExplorerFolderAction(new LocalFolderFocus(new LocalCommandRunner()));
        SwitchOrchestrator orchestrator = new SwitchOrchestrator(
                List.of(new TmuxFocusAction(ssh, new TmuxResurrect(ssh, settings.claudeAuto()),
                                (task, newWindowId) -> {
                                    String fileName = task.id() + ".md";
                                    String content = files.read(fileName);
                                    if (content == null) {
                                        Logger.warn("Skipping tmux-window rewrite of {}: file unreadable", fileName);
                                        return;
                                    }
                                    files.save(fileName, TaskFileParser.withTmuxWindow(
                                            content, newWindowId,
                                            "resurrected " + LocalDate.now()));
                                }),
                        new LocalTerminalAction(new WindowsTerminalFocus(new LocalCommandRunner())),
                        explorerFolders,
                        // Wait for the client window to appear (up to ~3 min at
                        // a 3 s cadence) so the chip stays a hourglass until the
                        // remote IDE is really up, not just launched.
                        new IntellijGatewayAction(this::openUrl,
                                new RemoteIdeLookup(ssh)::newestIdeDist,
                                new JetBrainsClientFocus(new LocalCommandRunner())::focus,
                                60, 3000L),
                        // Reopens a complete-control suspend's stored tabs on
                        // resume, then clears the storedTabs: key.
                        // [impl->dsn~complete-control-desktop~1]
                        new BrowserFocusAction(extensionServer,
                                task -> completeControlDesktop(files, task),
                                (desktop, url) -> openOnDesktop(extensionServer, desktop, url),
                                task -> rewriteTaskFile(files, task,
                                        TaskFileParser::withoutStoredTabs)),
                        new NoteFocusAction(this::openUrl,
                                group -> groupLink(files, group, TaskFileParser::groupNote)),
                        new ChatFocusAction(this::openUrl,
                                group -> groupLink(files, group, TaskFileParser::groupChat))),
                actionExecutor, Platform::runLater);
        // Suspend teardown reuses the orchestrator machinery (concurrent
        // actions + chips) with the ending counterpart of each action.
        // [impl->dsn~task-suspend~6]
        // [impl->dsn~terminal-suspend-snapshot~3]
        PaneSnapshots snapshots = new PaneSnapshots(ssh, configDir().resolve("snapshots"));
        this.paneSnapshots = snapshots;
        // On a complete-control category the browser close also stores and
        // closes every Firefox window on the category's desktop.
        // [impl->dsn~complete-control-desktop~1]
        SwitchOrchestrator suspendOrchestrator = new SwitchOrchestrator(
                List.of(new TmuxKillAction(ssh, snapshots),
                        new BrowserCloseAction(extensionServer,
                                task -> completeControlDesktop(files, task),
                                desktop -> new BrowserDesktopWindows(
                                        new LocalCommandRunner(), drivenBrowser())
                                        .captionsOn(desktop),
                                (task, urls) -> rewriteTaskFile(files, task,
                                        content -> TaskFileParser.withStoredTabs(content, urls)))),
                actionExecutor, Platform::runLater);

        // [impl->dsn~terminal-pane~14]
        TerminalPane terminalPane = new TerminalPane(actionExecutor, ssh);
        this.terminalPane = terminalPane;
        terminalPane.setAttachmentsDir(configDir().resolve("attachments"));
        // [impl->dsn~terminal-diff-window~2]
        terminalPane.setDiffOpener((remote, tmux) ->
                showDiffWindow(repository, files, ssh, terminalPane, remote, tmux));
        // [impl->dsn~terminal-owned-session~3]
        terminalPane.setOwnedDiffOpener(taskId -> showOwnedDiff(repository, taskId));
        // [impl->dsn~terminal-mirrored-task~1]
        terminalPane.setMirroredTaskSelector((remote, window) ->
                selectWindowTask(repository, remote, window));
        // [impl->dsn~terminal-markdown-copy~1]
        terminalPane.setAutoCopyReplies(settings.autoCopyReplies());
        terminalPane.setClaudeResolver((remote, tmux) -> {
            Task task = tmux.window() == null ? null : windowTask(repository, remote, tmux.window());
            return task == null ? null : task.claude();
        }, text -> {
            MainWindow shown = this.window;
            if (shown != null) {
                shown.statusBar().message(text);
            }
        });
        // The one-at-a-time refactoring web view (https://github.com/contextswitcher/contextswitcher-private/issues/50).
        // [impl->dsn~refactoring-web-view~1]
        RefactoringMinerView refactoringView = new RefactoringMinerView(actionExecutor);
        this.refactoringView = refactoringView;
        // [impl->dsn~message-queue-send~5]
        MessageSender messageSender = new MessageSender(ssh,
                new HostCommandRunner(new ProcessSshRunner(UPLOAD_SSH_TIMEOUT),
                        new LocalTmuxRunner(new LocalCommandRunner(UPLOAD_SSH_TIMEOUT)),
                        new WslTmuxRunner(new LocalCommandRunner(UPLOAD_SSH_TIMEOUT))),
                configDir().resolve("attachments"));
        // Every delivered message becomes a provenance record once the task repo
        // names a provenance repository and its copy exists (MADR 0037).
        // [impl->dsn~provenance-record~1]
        messageSender.setDeliveryListener(new ProvenanceRecorder(
                () -> this.provenanceBackup == null ? null : provenanceDir(),
                "desktop-" + TaskFileParser.hostName(), ssh,
                (host, target) -> ProvenanceRecorder.taskIn(repository.entries().stream()
                        .filter(TaskEntry.Loaded.class::isInstance)
                        .map(entry -> ((TaskEntry.Loaded) entry).task()).toList(), host, target),
                category -> categoryRepo(settings.tasksDir(), category),
                java.time.Clock.systemUTC(), () -> { }));
        // [impl->dsn~claude-session-kill~6]
        ClaudeSessionCleanup sessionCleanup = new ClaudeSessionCleanup(ssh);
        // [impl->dsn~qodo-agent-prompt-queue~11]
        QodoReviewLookup qodoLookup = new QodoReviewLookup(new LocalCommandRunner());
        QueuePane queuePane = new QueuePane(queueDir,
                queueDir.resolve("qodo"),
                configDir().resolve("attachments"),
                // [impl->dsn~message-queue-delayed-send~4]
                configDir().resolve("armed-messages.yaml"),
                (task, text, progress) -> switch (ChatRoute.of(task)) {
                    // Remote or local: the host decides the transport, not
                    // the caller. [impl->dsn~terminal-local-mirror~2]
                    case ChatRoute.Tmux(String host, Task.TmuxConfig tmux) -> SendResult.of(
                            messageSender.send(host, tmux.target(), text, ClaudeMode.DEFAULT, progress));
                    // Windows: no tmux, the chat runs in the pane's own ConPTY
                    // and is typed into. [impl->dsn~terminal-owned-session~3]
                    case ChatRoute.Owned(String taskId) ->
                            terminalPane.sendToOwned(taskId, messageSender.localText(text));
                    case ChatRoute.None() -> new SendResult.Failed("No tmux window configured for this task.");
                },
                // "Qodo sync": current active prompts for the task's PRs, or
                // null when it has no PR / every fetch failed (queue then left
                // as is). A task can carry several PRs; they are tried top to
                // bottom and the first one with prompts wins — merging reviews
                // from several PRs into one queue is deliberately not
                // supported, the queue reconcile is per-PR.
                task -> {
                    Map<String, String> prompts = null;
                    for (String url : task.prUrls()) {
                        Map<String, String> found = qodoLookup.promptsFor(url);
                        if (found != null && !found.isEmpty()) {
                            return found;
                        }
                        if (found != null) {
                            prompts = found;   // reachable but silent: "no active prompts"
                        }
                    }
                    return prompts;
                },
                // The qodo link buttons reuse the PR icon's browser path,
                // desktop and all: a review comment that must be opened lands
                // on the task's category desktop just like a PR link does.
                // [impl->dsn~qodo-agent-prompt-queue~11]
                // [impl->dsn~pr-open-on-category-desktop~3]
                (task, url) -> {
                    String desktop = task == null ? null : groupDesktop(files, taskGroup(task));
                    actionExecutor.execute(() -> reportFocusUrl(extensionServer, url, desktop));
                },
                // Any URL in a queued message also becomes a browser tab —
                // the same textual rewrite the task-creation dialogs use.
                // [impl->dsn~task-url-collect~1]
                (task, text) -> rewriteTaskFile(files, task,
                        content -> TaskFileParser.addUrlsFrom(content, text)),
                actionExecutor,
                // Refresh the task rows so their queued-count badges follow a
                // queue edit; the window is created just below, so resolve it
                // lazily. save() runs on the FX thread, so this is FX-safe.
                () -> {
                    MainWindow w = this.window;
                    if (w != null) {
                        w.refreshTaskRows();
                    }
                },
                // [impl->dsn~message-queue-resume-send~2]
                task -> {
                    MainWindow w = this.window;
                    if (w != null) {
                        w.resumeTask(task);
                    }
                },
                id -> repository.entries().stream()
                        .filter(entry -> entry instanceof TaskEntry.Loaded loaded && loaded.id().equals(id))
                        .map(entry -> ((TaskEntry.Loaded) entry).task())
                        .findFirst().orElse(null));
        MainWindow window = new MainWindow(taskEntries, taskFolders,
                task -> switchTo(orchestrator, files, task),
                task -> suspendTo(suspendOrchestrator, task),
                (task, done) -> refreshClaudeFromWindow(ssh, files, task, done),
                (task, newTitle) -> renameTmuxWindow(ssh, task, newTitle),
                this::openUrl,
                (url, desktop) -> actionExecutor.execute(
                        () -> reportFocusUrl(extensionServer, url, desktop)),
                (urls, answer) -> actionExecutor.execute(() -> {
                    int open = countOpenTabs(extensionServer, urls);
                    Platform.runLater(() -> answer.accept(open));
                }),
                name -> actionExecutor.execute(() -> reportFocusDesktop(name)),
                (folders, onSettled) -> actionExecutor.execute(
                        () -> reportOpenFolders(explorerFolders, folders, onSettled)),
                this::watchActiveDesktop,
                settings.tasksDir(),
                queueDir,
                () -> openTasksDir(settings.tasksDir()),
                done -> syncTmuxSessions(repository, settings, files, done, true, true),
                done -> restartRunningTasks(repository, files, ssh, settings, done),
                task -> {
                    // [impl->dsn~terminal-pane~14]
                    if (task.id().equals(mirroredTaskId)) {
                        previewTask(files, terminalPane, task);
                    } else {
                        mirroredTaskId = task.id();
                        terminalPane.blankThen(() -> previewTask(files, terminalPane, task));
                    }
                    applyIssueRepo(files, ssh, terminalPane, task);
                    queuePane.showTask(task);
                    refreshPrStatesOf(task);
                },
                files,
                terminalPane,
                queuePane.getRoot(),
                () -> {
                    mirroredTaskId = null;
                    terminalPane.blankThen(() ->
                            terminalPane.showMessage("Select a task to mirror its tmux window."));
                    queuePane.showTask(null);
                },
                terminalPane::focusTerminal,
                terminalPane::retheme,
                (task, choices) ->
                        killSession(files, suspendOrchestrator, sessionCleanup, task, choices),
                task -> askClaudeCleanup(messageSender, task),
                (url, remote, group, mode) ->
                        createTaskFromPr(repository, files, ssh, messageSender, url, remote, group,
                                settings, mode),
                (title, remote, workdir, repo, bootstrap, group, mode, appendix) ->
                        createLiveTask(repository, files, ssh, messageSender, title, remote,
                                workdir, repo, bootstrap, group, settings, mode, appendix),
                (source, instruction, mode) ->
                        forkTask(repository, files, ssh, messageSender, settings, source, instruction, mode),
                (taskId, workdir, description, mode) ->
                        startLocalClaude(files, taskId, workdir, description, mode),
                (task, withClaude, mode) -> startRemoteWindow(orchestrator, repository, files, ssh,
                        messageSender, settings, task, withClaude, mode),
                (remote, cwd, onSettled) -> startScratchWindow(repository, ssh, terminalPane, remote, cwd, onSettled),
                (task, onSettled) ->
                        openRefactoringView(files, settings, refactoringView, task, onSettled),
                this::setupRefactoringMiner,
                queuePane::installAttachments,
                queuePane::quickRow,
                () -> readSettingsFile(configDir()),
                content -> {
                    String error = writeSettingsFile(configDir(), content);
                    // A save that turned the all-desktops pin on applies it
                    // right away — no restart round-trip just to see the pin.
                    // [impl->dsn~window-desktop-pin~2]
                    if (error == null) {
                        try {
                            AppSettings saved = AppSettings.parse(content);
                            applyDesktopPin(saved);
                            // [impl->dsn~window-position-per-desktop~2]
                            trackWindowPosition = positionTrackingEnabled(saved);
                            // [impl->dsn~fallback-desktop~1]
                            fallbackDesktop = saved.fallbackDesktop();
                            // [impl->dsn~auto-suspend-idle~2]
                            autoSuspendMinutes = saved.autoSuspendMinutes();
                            mergedCleanupDays = saved.mergedCleanupDays();
                            // [impl->dsn~browser-choice~2]
                            browser = saved.browser();
                            // [impl->dsn~prefer-chosen-browser~1]
                            extensionServer.prefer(browser);
                            // The chords are a filter flag, so they switch live.
                            // [impl->dsn~readline-keys~1]
                            com.contextswitcher.ui.ReadlineKeys.setEnabled(saved.readlineKeys());
                            // [impl->dsn~terminal-markdown-copy~1]
                            if (this.terminalPane != null) {
                                this.terminalPane.setAutoCopyReplies(saved.autoCopyReplies());
                            }
                        } catch (IOException e) {
                            // Raw-mode saves may be unparseable; the pin then
                            // simply waits for the next startup.
                        }
                    }
                    return error;
                },
                settings::remotes,
                () -> currentTagPalette(settings),
                settings.hints(),
                settings.fallbackDesktop());
        // The toolbar globe: the browser action alone, otherwise a plain play.
        // [impl->dsn~extension-connection-indicator~2]
        window.setOnBrowserSwitch(task -> switchTo(orchestrator, files, task, Set.of("browser")));
        // The globe's right-click: the chosen browser drives while connected
        // and is the one launched while not; saved as `browser`, so it lasts.
        // [impl->dsn~prefer-chosen-browser~1]
        window.setBrowserChoice(extensionServer::connectedBrowsers, () -> browser, choice -> {
            browser = choice;
            extensionServer.prefer(choice);
            String error = writeSettingsFile(configDir(),
                    YamlPatch.set(readSettingsFile(configDir()), "browser", choice.key()));
            window.statusBar().message(error == null
                    ? "Using " + choice.displayName() + "."
                    : "Cannot save the browser choice: " + error);
        });
        // A rename swaps the entry in place instead of letting the watcher's
        // delete+create blink the row out of the list.
        // [impl->dsn~claude-title-sync~3]
        window.setTaskRenamer(repository::renamed);
        // …and the queue pane keeps the message being typed instead of filing
        // it under the id the task just left.
        // [impl->dsn~message-queue-ui~26]
        window.setOnTaskRenamed(queuePane::taskRenamed);
        // [impl->dsn~start-claude-button~2]
        // [impl->dsn~setup-wizard~8]
        window.setOnSetupWizard(this::rerunSetupWizard);
        window.setOnStartClaude((task, onSettled) ->
                startClaude(ssh, settings.claudeAuto(), task, onSettled));
        // The same window choice play makes, from the terminal placeholder.
        // The `tmux` button fires straight away — a plain shell has no model to
        // pick; `claude` opens play's dialog for the model and effort instead of
        // taking the remote CLI's defaults — but without its `tmux` button, which
        // would only undo the choice just made, and with the *resolved* host in
        // the text (`taskRemote`), since a task that borrows its category's
        // `remote:` read "Open one on null?". A cancelled dialog re-draws the
        // placeholder, whose buttons went dead for the round-trip that never
        // started.
        // [impl->dsn~remote-window-choice~6]
        // [impl->dsn~claude-mode-select~3]
        terminalPane.setWindowStarter((task, withClaude) -> {
            MainWindow.RemoteWindowChoice choice = withClaude
                    ? MainWindow.askRemoteWindow(task, taskRemote(files, task), false)
                    : new MainWindow.RemoteWindowChoice(false, ClaudeMode.DEFAULT);
            if (choice == null) {
                window.refreshPreview(task.id());
                return;
            }
            startRemoteWindow(orchestrator, repository, files, ssh, messageSender, settings,
                    task, choice.withClaude(), choice.mode());
        });
        // A key sent into, or a restart during, a session that is still being
        // started breaks that start. [impl->dsn~busy-while-creating~2]
        terminalPane.disableBarWhile(creationsInFlight.greaterThan(0));
        window.disableRestartWhile(creationsInFlight.greaterThan(0));
        this.window = window;
        this.stage = stage;
        this.windowPositions = new WindowPositions(configDir());
        this.trackWindowPosition = positionTrackingEnabled(settings);
        this.fallbackDesktop = settings.fallbackDesktop();
        this.autoSuspendMinutes = settings.autoSuspendMinutes();
        this.mergedCleanupDays = settings.mergedCleanupDays();
        this.browser = settings.browser();
        extensionServer.prefer(browser);
        // The geometry saved at the last exit, applied before the stage shows
        // so the window opens in place instead of jumping — on every OS, pin
        // on or off; the per-desktop entries take over once the desktop poll
        // reports a name. [impl->dsn~window-position-per-desktop~2]
        applyStoredGeometry(stage, windowPositions, WindowPositions.STARTUP);
        // [impl->dsn~task-repository-watching~6]
        repository.setOnGroupConfigChange(window::groupConfigChanged);
        // The shell needs the Application for its host services (MADR 0032).
        window.show(stage, this);
        // The wizard's first project, created once the window is up: the
        // category and its setup session need the repository and the live-task
        // machinery that did not exist while the wizard ran.
        // [impl->dsn~setup-wizard~8]
        SetupWizard.Result firstProject = this.firstProject;
        if (firstProject != null && firstProject.repoUrl() != null
                && firstProject.workspacesRoot() != null) {
            Platform.runLater(() -> window.createCategoryFromRepo(firstProject.repoUrl(),
                    firstProject.workspacesRoot(), firstProject.remote(), firstProject.repoType(),
                    MainWindow.lastMode(), firstProject.firstMessage()));
        }

        // Re-pin the window to all virtual desktops — Windows drops the Task
        // View pin with the window handle, so it cannot survive a restart on
        // its own. Off the FX thread; the script waits for the window itself.
        // [impl->dsn~window-desktop-pin~2]
        applyDesktopPin(settings);
        // One immediate desktop read, so the window jumps to this desktop's
        // remembered position right after showing instead of on the next tick.
        // [impl->dsn~window-position-per-desktop~2]
        if (trackWindowPosition) {
            actionExecutor.execute(this::refreshActiveDesktop);
        }

        // Deep links (https://github.com/contextswitcher/contextswitcher-private/issues/47): forwarded from a second instance over the
        // extension server, or passed as program argument when this launch
        // was itself the protocol handler and no instance was running yet.
        // [impl->dsn~deep-link-url~1]
        extensionServer.setDeepLinkHandler(url -> Platform.runLater(
                () -> handleDeepLink(orchestrator, repository, files, stage, url)));
        // Clicking a task's tab in the browser selects that task: the
        // extension reports the activated tab, the app follows.
        // [impl->dsn~browser-tab-selects-task~5]
        extensionServer.setTabActivatedHandler(url -> Platform.runLater(() -> {
            // Closing a tab activates its neighbour: during a teardown that is
            // the app's own doing, not the user's, and would resume the task
            // being suspended. [impl->dsn~browser-teardown-quiet-tabs~1]
            if (teardowns > 0) {
                Logger.debug("Tab activated {} during a teardown, ignored", url);
                return;
            }
            selectTaskForTab(repository, url, true);
        }));
        // The toolbar indicator follows connect/disconnect — the extension
        // dials in on its own, whenever the browser is started.
        // [impl->dsn~extension-connection-indicator~2]
        extensionServer.setConnectionHandler(
                connected -> Platform.runLater(() -> window.showBrowserExtension(connected)));
        // `getParameters()` is null when the app was not started through
        // `Application.launch` — the TestFX harness instantiates and starts it
        // directly, and a null there must not take the whole startup down.
        Parameters parameters = getParameters();
        if (parameters != null) {
            parameters.getRaw().stream().filter(DeepLink::isDeepLink).findFirst()
                    .ifPresent(url -> Platform.runLater(
                            () -> handleDeepLink(orchestrator, repository, files, stage, url)));
        }

        // [impl->dsn~task-running-indicator~7]
        recomputeStatusHosts(repository);
        taskEntries.addListener(
                (javafx.collections.ListChangeListener<TaskEntry>) change -> recomputeStatusHosts(repository));
        TmuxStatusPoller poller = new TmuxStatusPoller(ssh, () -> statusHosts,
                statuses -> {
                    // Stashed (volatile) before the FX hop: the refactoring
                    // poller's idle gate reads it from its own thread.
                    // [impl->dsn~refactoring-analysis-poller~1]
                    Map<String, String> previous = liveStatuses;
                    liveStatuses = statuses;
                    refreshPrStatesOfStoppedSessions(previous, statuses);
                    Platform.runLater(() -> {
                        window.updateRunningStatuses(statuses);
                        // A message armed for "delayed next" goes out on the
                        // tick its chat reports idle.
                        // [impl->dsn~message-queue-delayed-send~4]
                        queuePane.sendDelayed(statuses);
                        // [impl->dsn~terminal-markdown-copy~1]
                        terminalPane.showStatuses(statuses);
                    });
                },
                // [impl->dsn~claude-mode-report~2]
                modes -> Platform.runLater(() -> terminalPane.showSessionModes(modes)),
                // [impl->dsn~tmux-window-ownership~4]
                sessionIds -> {
                    Map<String, String> owned = uniqueSessionIds(sessionIds);
                    liveSessionIds = owned;
                    Platform.runLater(() -> window.updateSessionIds(owned));
                },
                // [impl->dsn~start-claude-button~2]
                commands -> Platform.runLater(() -> window.updateWindowCommands(commands)),
                // Last consumer of the tick, so the statuses it judges by are
                // already in the window. [impl->dsn~auto-suspend-idle~2]
                idle -> Platform.runLater(() -> window.autoSuspendIdle(idle,
                        autoSuspendMinutes * 60L)),
                // The flag a restarted session has to be given back.
                // [impl->dsn~claude-update-restart~6]
                settings.claudeAuto(),
                STATUS_POLL_SECONDS);
        this.statusPoller = poller;
        poller.start();

        // A hovered terminal link spells its URL out in the status bar.
        // [impl->dsn~terminal-link-hover~1]
        terminalPane.setLinkHover(window.statusBar()::hover);

        // [impl->dsn~pr-state-indicator~3]
        recomputePrUrls(repository);
        taskEntries.addListener(
                (javafx.collections.ListChangeListener<TaskEntry>) change -> recomputePrUrls(repository));
        // The same round also feeds the merged-task janitor.
        // [impl->dsn~merged-task-cleanup~2]
        window.setOnMergedCleanup(task -> cleanupMerged(files, snapshots, taskBackup,
                suspendOrchestrator, sessionCleanup, task, mergedCleanupDays));
        PrStatePoller prPoller = new PrStatePoller(new PrStateLookup(new LocalCommandRunner()),
                () -> prUrls,
                states -> Platform.runLater(() -> {
                    window.updatePrStates(states);
                    window.autoCleanupMerged(states, mergedCleanupDays);
                }),
                PR_STATE_POLL_SECONDS);
        this.prStatePoller = prPoller;
        prPoller.start();
        // Rows appearing (expanded group, cleared filter, new task) re-scope
        // the polled URL set and resolve the newly visible PRs right away —
        // URLs already resolved in this run keep their cached icon and cost
        // nothing. [impl->dsn~pr-poll-economy~2]
        window.visibleRowsObservable().addListener(
                (javafx.collections.ListChangeListener<Object>) change -> {
                    recomputePrUrls(repository);
                    prPoller.refreshMissing();
                });

        // The refactoring badge (https://github.com/contextswitcher/contextswitcher-private/issues/50), only when a RefactoringMiner install is
        // configured; a home added later needs a restart, like `remotes`.
        // [impl->dsn~refactoring-analysis-poller~1]
        String rmHome = settings.refactoringMinerHome();
        if (rmHome != null) {
            recomputeRefactoringTargets(repository, files);
            taskEntries.addListener(
                    (javafx.collections.ListChangeListener<TaskEntry>) change ->
                            recomputeRefactoringTargets(repository, files));
            RefactoringMinerPoller rmPoller = new RefactoringMinerPoller(
                    new RefactoringLookup(new ProcessSshRunner(REFACTORING_SSH_TIMEOUT)),
                    () -> refactoringTargets,
                    key -> liveStatuses.get(key),
                    rmHome,
                    summaries -> Platform.runLater(() -> window.updateRefactorings(summaries)),
                    REFACTORING_POLL_SECONDS);
            this.refactoringPoller = rmPoller;
            rmPoller.start();
        }

        // [impl->dsn~qodo-agent-prompt-queue~11]
        QodoReviewPoller qodoPoller = new QodoReviewPoller(qodoLookup,
                () -> prUrlsByTask,
                (taskId, prompts) -> Platform.runLater(() -> queuePane.addQodoPrompts(taskId, prompts)),
                QODO_POLL_SECONDS);
        this.qodoReviewPoller = qodoPoller;
        qodoPoller.start();

        // Auto categories: their PR search runs whether or not any of their
        // rows is on screen — the point is a list that fills itself.
        // [impl->dsn~auto-pr-category~3]
        AutoPrPoller autoPoller = new AutoPrPoller(settings.tasksDir(), new TaskFileParser(),
                new AutoPrLookup(new LocalCommandRunner()),
                new PrStateLookup(new LocalCommandRunner()),
                category -> categoryPrUrls.getOrDefault(category, List.of()),
                () -> tasksScanned,
                round -> Platform.runLater(() -> window.applyAutoPrs(round)),
                AUTO_PR_POLL_SECONDS);
        this.autoPrPoller = autoPoller;
        autoPoller.start();

        // [impl->dsn~claude-pr-refresh~5]
        SshCommandRunner workspacePrSsh = new ProcessSshRunner(WORKSPACE_PR_SSH_TIMEOUT);
        TmuxPrPoller tmuxPrPoller = new TmuxPrPoller(ssh, () -> statusHosts,
                found -> Platform.runLater(() -> {
                    applyDiscoveredPrs(repository, files, found);
                    scrapeFooterPrs(repository, files, ssh);
                    if (prTicks++ % WORKSPACE_PR_EVERY_NTH_TICK == 0) {
                        lookupWorkspacePrs(repository, files, workspacePrSsh);
                    }
                }),
                PR_REFRESH_POLL_SECONDS);
        this.tmuxPrPoller = tmuxPrPoller;
        tmuxPrPoller.start();

        // [impl->dsn~claude-title-sync~3]
        TmuxTitlePoller titlePoller = new TmuxTitlePoller(ssh, () -> statusHosts,
                found -> Platform.runLater(() -> adoptPublishedTitles(repository, files, ssh, found)),
                PR_REFRESH_POLL_SECONDS);
        this.tmuxTitlePoller = titlePoller;
        titlePoller.start();

        // The energy saver's two refresh actions: on a task change only the
        // cheap status poll (the selected task's live state), on the toolbar's
        // refresh button every poller. [impl->dsn~energy-saver~1]
        EnergySaver.setRefreshers(poller::pollNow, this::pollEverythingNow);
        // Every poller's first tick fires immediately on start — with the
        // saver already on from the last run that tick is skipped, and the app
        // would come up showing nothing. One startup poll instead: the user
        // opened the window to look at it.
        if (EnergySaver.active()) {
            pollEverythingNow();
        }

        schedulePeriodicReconcile(repository, settings, files);
        // [impl->dsn~active-desktop-filter~6]
        scheduleActiveDesktopWatch();
        // [impl->dsn~restart-to-update~11]
        scheduleUpdateCheck(window);
        // [impl->dsn~task-sync-groups-ui~1]
        scheduleTaskSync(window, settings);
    }

    /// Asks git every five minutes whether the checkout the app runs out of is
    /// behind its upstream, offers the restart once it is, and feeds the
    /// fetched changelog to the pending-news projection
    /// (`dsn~whats-new-upstream~7`); the local `CHANGELOG.md` is watched for
    /// the same projection, so a parallel session's edit shows up seconds
    /// after the write, committed or not. Started from a zip rather than a
    /// checkout there is no repository above the working directory and none
    /// of this runs.
    ///
    /// The scheduler thread only dispatches: `git fetch` waits on the network,
    /// and this scheduler is the single thread the memory log, the reconcile
    /// and the desktop watch tick on.
    // [impl->dsn~restart-to-update~11]
    // [impl->dsn~whats-new-upstream~7]
    private void scheduleUpdateCheck(MainWindow window) {
        ScheduledExecutorService scheduler = this.scheduler;
        Path repo = AppUpdate.repositoryRoot(Path.of(""));
        if (scheduler == null || repo == null) {
            Logger.debug("No git checkout above {} — update check off", Path.of("").toAbsolutePath());
            return;
        }
        // The commit in the status bar comes from the same checkout, asked
        // once at startup — a local `git show`, no network.
        // [impl->dsn~running-commit~3]
        scheduler.execute(() -> {
            // The build that runs, recorded before any check can ask: the
            // checkout's HEAD may move under it later. [impl->dsn~restart-to-update~11]
            AppUpdate.rememberRunningCommit(repo);
            String commit = AppUpdate.headCommit(repo);
            if (commit != null) {
                Platform.runLater(() -> window.showCommit(commit));
            }
        });
        window.setOnCheckRemote(() -> checkRemoteNow(window, repo));
        scheduler.execute(() -> {
            refreshNews(window, repo, true, WhatsNew.blame(repo, null, WhatsNew.ME));
            watchChangelog(repo, window);
        });
        // [impl->dsn~poll-no-wake-backlog~1]
        scheduler.scheduleWithFixedDelay(() -> {
            ExecutorService executor = this.actionExecutor;
            // [impl->dsn~energy-saver~1]
            if (executor == null || EnergySaver.active()) {
                return;
            }
            executor.execute(() -> {
                int behind = AppUpdate.commitsBehind(repo);
                if (behind > 0) {
                    Platform.runLater(() -> window.showUpdateAvailable(behind));
                    refreshNews(window, repo, false, WhatsNew.blame(repo, "@{u}", WhatsNew.ME_REMOTELY));
                }
            });
        }, UPDATE_CHECK_MINUTES, UPDATE_CHECK_MINUTES, TimeUnit.MINUTES);
    }

    /// The last announced copy of `CHANGELOG.md`: the pending news is what the
    /// current changelog holds beyond it. On disk next to the launch script's
    /// `whats-new-last-commit`, so news that lands while the app is closed
    /// waits for the next start — nothing is missed, nothing shown twice.
    private static Path announcedChangelog() {
        return configDir().resolve("whats-new-announced.md");
    }

    /// The blamed changelogs seen last, by trigger: `true` the local working
    /// tree, `false` the fetched upstream.
    /// Both stay in the projection, so upstream bullets arriving while a local
    /// edit is still pending neither hide it nor get hidden by it.
    private final Map<Boolean, WhatsNew.Source> newsSources = new ConcurrentHashMap<>();

    /// Records `text` as the latest changelog of its trigger and shows the
    /// bullets no announced copy holds in the update button's tooltip. Without a copy
    /// yet (first run) the current local changelog becomes it silently. Any
    /// thread but FX.
    // [impl->dsn~whats-new-upstream~7]
    private void refreshNews(MainWindow window, Path repo, boolean local, WhatsNew.@Nullable Source text) {
        if (text == null) {
            return;
        }
        newsSources.put(local, text);
        Path copy = announcedChangelog();
        if (!Files.exists(copy)) {
            announceNews(copy);
            return;
        }
        List<WhatsNew.Item> items = WhatsNew.pending(copy, newsSources);
        String upstream = newsUpstream(repo);
        // Announcing is one small file write, done right away: it now runs from
        // the *Restart to update* button too, just before the app leaves, and
        // stop() cuts the action pool short. [impl->dsn~whats-new-upstream~7]
        Platform.runLater(() -> window.showPendingNews(items, upstream, () -> announceNews(copy)));
    }

    /// The update button's click: fetches, re-projects the pending news and
    /// hands the result to the window it just opened, which drops its
    /// "Checking remote \u2026" bar and arms *Restart to update*. The count that
    /// revealed the button is up to five minutes old, so this is the look that
    /// decides what the restart will actually pull. Answers even when the
    /// fetch found nothing \u2014 an unattended bar is a hang. FX thread; the git
    /// work runs on the action pool.
    // [impl->dsn~restart-to-update~11]
    private void checkRemoteNow(MainWindow window, Path repo) {
        ExecutorService executor = this.actionExecutor;
        if (executor == null) {
            window.newsChecked(pendingNews(), newsUpstream(repo), true);
            return;
        }
        executor.execute(() -> {
            int behind = AppUpdate.commitsBehind(repo);
            if (behind > 0) {
                refreshNews(window, repo, false, WhatsNew.blame(repo, "@{u}", WhatsNew.ME_REMOTELY));
            }
            List<WhatsNew.Item> items = pendingNews();
            String upstream = newsUpstream(repo);
            Platform.runLater(() -> window.newsChecked(items, upstream, behind > 0));
        });
    }

    /// The bullets the current sources hold beyond the announced copy, empty
    /// while there is no copy yet (first run).
    // [impl->dsn~whats-new-upstream~7]
    private List<WhatsNew.Item> pendingNews() {
        Path copy = announcedChangelog();
        return Files.exists(copy) ? WhatsNew.pending(copy, newsSources) : List.of();
    }

    /// `<upstream commit>`, what a restart would bring, or null when it is the
    /// running commit (the news is a local edit nobody pushed) or git cannot
    /// say. Not a start for the pending count: that is measured against the
    /// announced copy, which has no commit. Any thread but FX \u2014 two
    /// `git show` calls.
    // [impl->dsn~whats-new-upstream~7]
    private static @Nullable String newsUpstream(Path repo) {
        String head = AppUpdate.describe(repo, AppUpdate.runningCommitOrHead());
        String upstream = AppUpdate.describe(repo, "@{u}");
        if (head == null || upstream == null || head.equals(upstream)) {
            return null;
        }
        return upstream;
    }

    private void announceNews(Path copy) {
        try {
            WhatsNew.announce(copy, newsSources);
        } catch (IOException e) {
            Logger.warn("Cannot write {}: {}", copy, e.toString());
        }
    }

    /// Seconds a changelog write has to settle before the projection runs: a
    /// session writes the file several times in a row, an editor through a
    /// temp file and a rename.
    private static final long CHANGELOG_SETTLE_SECONDS = 2;

    /// Watches `CHANGELOG.md` in the checkout — its parent directory, as a
    /// `WatchService` does (`TaskRepository`'s pattern), filtered to the one
    /// file; a rename lands as CREATE, a plain save as MODIFY. Only this
    /// checkout: a worktree writes another file, and a remote session's
    /// bullet arrives through the fetch tick once pushed.
    // [impl->dsn~whats-new-upstream~7]
    private void watchChangelog(Path repo, MainWindow window) {
        WatchService service;
        try {
            service = FileSystems.getDefault().newWatchService();
            repo.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException e) {
            Logger.warn("Cannot watch {}: {}", repo, e.toString());
            return;
        }
        Thread watcher = new Thread(() -> {
            try {
                while (true) {
                    WatchKey key = service.take();
                    boolean changed = false;
                    do {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            changed |= event.context() instanceof Path p && p.toString().equals("CHANGELOG.md");
                        }
                        if (!key.reset()) {
                            return;
                        }
                        key = service.poll(CHANGELOG_SETTLE_SECONDS, TimeUnit.SECONDS);
                    } while (key != null);
                    if (changed) {
                        refreshNews(window, repo, true, WhatsNew.blame(repo, null, WhatsNew.ME));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ClosedWatchServiceException e) {
                // shutdown
            }
        }, "changelog-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    /// Runs every poller once, right now, past the energy-saver gate — the
    /// toolbar's refresh item. Each poller does its work on its own thread, so
    /// this returns immediately; the window shows the polls' progress as they
    /// finish. A poller that was never created (no RefactoringMiner home) is
    /// simply skipped.
    // [impl->dsn~energy-saver~1]
    // [impl->dsn~refresh-progress~2]
    private void pollEverythingNow() {
        Map<String, CompletableFuture<?>> polls = new LinkedHashMap<>();
        TmuxStatusPoller status = this.statusPoller;
        if (status != null) {
            polls.put("Task statuses", status.pollNow());
        }
        PrStatePoller prState = this.prStatePoller;
        if (prState != null) {
            polls.put("PR states", prState.pollNow());
        }
        TmuxPrPoller tmuxPr = this.tmuxPrPoller;
        if (tmuxPr != null) {
            polls.put("Published PRs", tmuxPr.pollNow());
        }
        TmuxTitlePoller title = this.tmuxTitlePoller;
        if (title != null) {
            polls.put("Task titles", title.pollNow());
        }
        QodoReviewPoller qodo = this.qodoReviewPoller;
        if (qodo != null) {
            polls.put("Qodo reviews", qodo.pollNow());
        }
        RefactoringMinerPoller refactoring = this.refactoringPoller;
        if (refactoring != null) {
            polls.put("Refactorings", refactoring.pollNow());
        }
        AutoPrPoller autoPr = this.autoPrPoller;
        if (autoPr != null) {
            polls.put("Auto PRs", autoPr.pollNow());
        }
        // [impl->dsn~task-sync-groups-ui~1]
        polls.put("Sync groups", syncRound.get());
        MainWindow shown = this.window;
        if (shown != null) {
            shown.showRefreshProgress(polls);
        }
    }

    /// Minutes between two rounds of the task directory's own git sync while
    /// the app runs (`dsn~task-git-backup~5`) — also the delay before the
    /// first, so the startup pull has landed.
    private static final int TASK_BACKUP_MINUTES = 5;

    /// Minutes between two sync-group rounds — also the delay before the
    /// first one, so the startup pull of the personal task backup has landed.
    private static final int SYNC_GROUP_MINUTES = 5;

    /// Wires the sync groups: a round every [#SYNC_GROUP_MINUTES] (skipped
    /// under the energy saver), one on demand (join, sort-in, "Sync now",
    /// refresh), and the badge filled from the clones right away, without
    /// network.
    // [impl->dsn~task-sync-groups-ui~1]
    private void scheduleTaskSync(MainWindow window, AppSettings settings) {
        TaskSync sync = new TaskSync(settings.tasksDir(), configDir().resolve("sync"));
        this.taskSync = sync;
        Supplier<CompletableFuture<Void>> syncNow = () -> CompletableFuture.runAsync(() -> {
            List<TaskSync.Incoming> incoming = sync.syncAll();
            Platform.runLater(() -> window.showSyncIncoming(incoming));
        }, syncExecutor);
        Runnable round = syncNow::get;
        this.syncRound = syncNow;
        window.setTaskSync(sync, round);
        syncExecutor.execute(() -> {
            List<TaskSync.Incoming> incoming = sync.incoming();
            Platform.runLater(() -> window.showSyncIncoming(incoming));
        });
        ScheduledExecutorService scheduler = this.scheduler;
        if (scheduler != null) {
            // [impl->dsn~energy-saver~1]
            scheduler.scheduleWithFixedDelay(() -> {
                if (!EnergySaver.active()) {
                    round.run();
                }
            }, SYNC_GROUP_MINUTES, SYNC_GROUP_MINUTES, TimeUnit.MINUTES);
        }
    }

    /// Seconds between periodic auto-reconcile passes: a closed tmux window is
    /// detected on its own (task suspended, or a disposable shell deleted)
    /// without the user pressing "Sync tmux windows…". Long-ish — a window
    /// closing is not time-critical and each tick runs tmux discovery per host.
    private static final int AUTO_RECONCILE_SECONDS = 60;

    /// How often the app asks git for a new version — and the delay before
    /// the first ask, so a launch never competes with a `fetch`.
    private static final int UPDATE_CHECK_MINUTES = 5;

    /// Schedules the recurring silent reconcile (`dsn~tmux-sync~7`). Unlike the
    /// manual sync it does **not** import new windows — it only reconciles
    /// statuses (suspend a gone window, reactivate a returned one, delete a
    /// disposable shell) and backfills session ids, so it never auto-spawns a
    /// task for every tmux window. Daemon-scheduled off the FX thread; the sync
    /// re-enters on the FX thread and offloads its ssh work to the action pool.
    ///
    /// Fixed **delay**, never a fixed rate: a sleeping laptop misses hundreds
    /// of ticks and a fixed rate replays every one of them back to back on
    /// wake — each reconcile costing an ssh round-trip plus one pane capture
    /// per window, which held the six ssh slots for minutes
    /// (`dsn~poll-no-wake-backlog~1`).
    // [impl->dsn~tmux-sync~7]
    // [impl->dsn~poll-no-wake-backlog~1]
    private void schedulePeriodicReconcile(TaskRepository repository, AppSettings settings,
            MainWindow.TaskFileAccess files) {
        ScheduledExecutorService scheduler = this.scheduler;
        if (scheduler == null) {
            return;
        }
        // [impl->dsn~energy-saver~1]
        scheduler.scheduleWithFixedDelay(
                () -> {
                    if (EnergySaver.active()) {
                        return;
                    }
                    Platform.runLater(
                            () -> syncTmuxSessions(repository, settings, files, () -> { }, false, false));
                },
                AUTO_RECONCILE_SECONDS, AUTO_RECONCILE_SECONDS, TimeUnit.SECONDS);
    }

    /// Adopts a task title Claude published as `@cs_title` for its window —
    /// the live-creation prompt asks for one, derived from the task
    /// description: the matching task's frontmatter `title:` is replaced
    /// textually (the original description stays in the task body, written
    /// there at creation), the tmux window is renamed like a manual rename,
    /// and the task **file** follows too — the initial name is only a
    /// placeholder slug of the description, so it becomes
    /// `<date>-<slugged title>` (`TaskFileParser.adoptedFileName`, deduped;
    /// queue file, selection, and editor lane follow the new id). The option
    /// is unset on the remote afterwards — a one-shot mailbox, so a stale
    /// option never fights a manual rename.
    // [impl->dsn~claude-title-sync~3]
    private void adoptPublishedTitles(TaskRepository repository, MainWindow.TaskFileAccess files,
            SshCommandRunner ssh, Map<String, String> byKey) {
        for (TaskEntry entry : repository.entries()) {
            if (!(entry instanceof TaskEntry.Loaded loaded)) {
                continue;
            }
            Task task = loaded.task();
            // A suspended task's window id may already name a stranger's
            // window on a restarted server (dsn~tmux-sync~7); the poller keys
            // by id alone, so never adopt for a suspended task.
            String host = TmuxHost.of(task);
            if (host == null || task.tmux().window() == null
                    || task.status() == TaskStatus.SUSPENDED) {
                continue;
            }
            String published = byKey.get(TmuxStatusPoller.key(host, task.tmux().window()));
            if (published == null || published.isBlank()) {
                continue;
            }
            String title = published.strip();
            if (!title.equals(task.title())) {
                String fileName = task.id() + ".md";
                String content = files.read(fileName);
                if (content == null) {
                    Logger.warn("Skipping title sync of {}: file unreadable", fileName);
                    continue;
                }
                String error = files.save(fileName, TaskFileParser.withTitle(content, title));
                if (error != null) {
                    Logger.warn("Cannot adopt published title for {}: {}", task.id(), error);
                    continue;
                }
                Logger.info("Adopted published title for {}: {}", task.id(), title);
                renameTmuxWindow(ssh, task, title);
                String adopted = TaskFileParser.adoptedFileName(task.id(), title, LocalDate.now());
                if (adopted != null) {
                    String toFile = adopted + ".md";
                    for (int i = 2; files.exists(toFile); i++) {
                        toFile = adopted + "-" + i + ".md";
                    }
                    MainWindow mainWindow = this.window;
                    if (files.move(fileName, toFile) == null && mainWindow != null) {
                        mainWindow.taskRenamed(task.id(),
                                toFile.substring(0, toFile.length() - ".md".length()));
                    }
                }
            }
            ExecutorService executor = this.actionExecutor;
            if (executor != null) {
                String window = task.tmux().window();
                executor.execute(() -> ssh.run(host, TmuxTitlePoller.unsetCommand(window)));
            }
        }
    }

    /// Writes a freshly discovered `@cs_pr` URL into its task's `browser.urls`
    /// (https://github.com/contextswitcher/contextswitcher-private/issues/46): matches each `host windowId -> prUrl` entry to a live task
    /// by remote + tmux window, and appends the URL when the task does not
    /// already carry it (textual, comment-preserving). Runs on the FX thread;
    /// the watcher re-parses the file and the PR-state poller then shows it.
    // [impl->dsn~claude-pr-refresh~5]
    private void applyDiscoveredPrs(TaskRepository repository, MainWindow.TaskFileAccess files,
            Map<String, String> byKey) {
        for (TaskEntry entry : repository.entries()) {
            if (!(entry instanceof TaskEntry.Loaded loaded)) {
                continue;
            }
            Task task = loaded.task();
            // A suspended task's window id may already name a stranger's
            // window on a restarted server (dsn~tmux-sync~7); the poller keys
            // by id alone, so never adopt for a suspended task.
            String host = TmuxHost.of(task);
            if (host == null || task.tmux().window() == null
                    || task.status() == TaskStatus.SUSPENDED) {
                continue;
            }
            String prUrl = byKey.get(TmuxStatusPoller.key(host, task.tmux().window()));
            if (prUrl != null && !prUrl.isBlank()) {
                writePrToTask(files, task.id(), prUrl);
            }
        }
    }

    /// Fallback for tasks whose session prints the PR only in its footer (not
    /// as `@cs_pr`, https://github.com/contextswitcher/contextswitcher-private/issues/46): for each still-PR-less live Claude task, scrape
    /// the pane off the FX thread (`ClaudePrLookup`) and, if found, record it.
    /// Bounded — a task drops out once it has a PR URL.
    // [impl->dsn~claude-pr-refresh~5]
    private void scrapeFooterPrs(TaskRepository repository, MainWindow.TaskFileAccess files,
            SshCommandRunner ssh) {
        ExecutorService executor = this.actionExecutor;
        if (executor == null) {
            return;
        }
        // host -> window id -> task ids: one ssh call per host captures every
        // window; a per-task call each was a burst of ~40 ssh processes per
        // tick that sshd refused and that starved the FX thread.
        Map<String, Map<String, List<String>>> byHost = new LinkedHashMap<>();
        for (TaskEntry entry : repository.entries()) {
            if (!(entry instanceof TaskEntry.Loaded loaded)) {
                continue;
            }
            Task task = loaded.task();
            // Scraped on every tick even once the task has a PR: a session that
            // opened a code PR often opens its documentation PR minutes later,
            // and stopping at the first one would never pick that up.
            // `writePrToTask` skips URLs the file already has.
            String taskHost = TmuxHost.of(task);
            if (taskHost == null || task.tmux().window() == null
                    || task.claude() == null || task.status() == TaskStatus.SUSPENDED) {
                continue;
            }
            byHost.computeIfAbsent(taskHost, host -> new LinkedHashMap<>())
                    .computeIfAbsent(task.tmux().window(), window -> new ArrayList<>())
                    .add(task.id());
        }
        byHost.forEach((remote, tasksByWindow) -> executor.execute(() -> {
            Map<String, List<String>> found =
                    new ClaudePrLookup(ssh).findPrUrls(remote, tasksByWindow.keySet());
            found.forEach((windowId, prUrls) -> {
                for (String taskId : tasksByWindow.getOrDefault(windowId, List.of())) {
                    for (String prUrl : prUrls) {
                        Platform.runLater(() -> writePrToTask(files, taskId, prUrl));
                    }
                }
            });
        }));
    }

    /// Fallback for tasks whose session works on a PR it neither opened nor
    /// published (a review, a fix-up, a body edit): for each live Claude task
    /// whose workspace has not answered yet, ask `gh` on the remote which PR
    /// the workspace's branch belongs to ([WorkspacePrLookup], off the FX
    /// thread). Bounded twice over — a workspace drops out for good once its
    /// PR is found ([#workspacePrsFound]), and the lookup runs only every
    /// [#WORKSPACE_PR_EVERY_NTH_TICK]th tick, since each one costs a GitHub
    /// API call.
    // [impl->dsn~claude-pr-refresh~5]
    private void lookupWorkspacePrs(TaskRepository repository, MainWindow.TaskFileAccess files,
            SshCommandRunner ssh) {
        ExecutorService executor = this.actionExecutor;
        if (executor == null) {
            return;
        }
        // host -> workspace -> task ids: several tasks share a workspace (the
        // main checkout), so one `gh` call per distinct workspace, all of a
        // host's in one ssh call.
        Map<String, Map<String, List<String>>> byHost = new LinkedHashMap<>();
        for (TaskEntry entry : repository.entries()) {
            if (!(entry instanceof TaskEntry.Loaded loaded)) {
                continue;
            }
            Task task = loaded.task();
            Task.ClaudeConfig claude = task.claude();
            if (task.remote() == null || claude == null || task.status() == TaskStatus.SUSPENDED) {
                continue;
            }
            String workspace = claude.workspace() != null ? claude.workspace() : claude.cwd();
            if (workspacePrsFound.contains(workspace)) {
                continue;
            }
            byHost.computeIfAbsent(task.remote(), host -> new LinkedHashMap<>())
                    .computeIfAbsent(workspace, ws -> new ArrayList<>())
                    .add(task.id());
        }
        byHost.forEach((remote, tasksByWorkspace) -> executor.execute(() -> {
            Map<String, String> found =
                    new WorkspacePrLookup(ssh).findPrUrls(remote, tasksByWorkspace.keySet());
            found.forEach((workspace, prUrl) -> {
                workspacePrsFound.add(workspace);
                for (String taskId : tasksByWorkspace.getOrDefault(workspace, List.of())) {
                    Platform.runLater(() -> writePrToTask(files, taskId, prUrl));
                }
            });
        }));
    }

    /// Adds `prUrl` **first** in a task file's `browser.urls`, skipping when
    /// the list already has it (`addClaudePrUrl` returns the content unchanged).
    /// Re-reads the file (not the possibly-stale in-memory task) so the
    /// `@cs_pr` and footer-scrape writers — both marshalled to the FX thread —
    /// never double-append the same URL. Must run on the FX thread.
    /// An editor lane showing the file is reloaded, else its stale clean buffer
    /// hides the URL and drops it again on its next auto-save.
    // [impl->dsn~claude-pr-refresh~5]
    private void writePrToTask(MainWindow.TaskFileAccess files, String taskId, String prUrl) {
        String fileName = taskId + ".md";
        String loaded = files.read(fileName);
        if (loaded == null) {
            Logger.warn("Skipping PR write of {}: file unreadable", fileName);
            return;
        }
        String updated = TaskFileParser.addClaudePrUrl(loaded, prUrl);
        if (updated.equals(loaded)) {
            return;
        }
        String error = files.save(fileName, updated);
        if (error != null) {
            Logger.warn("Cannot record PR {} for {}: {}", prUrl, taskId, error);
        } else {
            Logger.info("Recorded PR {} for task {}", prUrl, taskId);
            MainWindow window = this.window;
            if (window != null) {
                window.reloadEditorIfShowing(fileName);
            }
        }
    }

    /// Every PR URL of every task, for the PR-state poller (https://github.com/contextswitcher/contextswitcher-private/issues/49) and the Qodo
    /// review poller — *all* of a task's PRs, so a second PR gets its own
    /// resolved icon and its own qodo reviews. The per-category map rides
    /// along for the auto categories (`dsn~auto-pr-category~3`).
    // [impl->dsn~pr-state-indicator~3]
    // [impl->dsn~auto-pr-category~3]
    private void recomputePrUrls(TaskRepository repository) {
        Map<String, List<String>> byTask = new HashMap<>();
        Map<String, List<String>> byWindow = new HashMap<>();
        Map<String, List<String>> byCategory = new HashMap<>();
        Set<String> allUrls = new HashSet<>();
        // Only tasks with a row on screen are polled against the GitHub API
        // (states already fetched stay cached in MainWindow, so a collapsed
        // group keeps its icons) — per-PR polling of the whole task list is
        // what got the gh token rate-limited. [impl->dsn~pr-poll-economy~2]
        MainWindow window = this.window;
        Set<String> visibleIds = window == null ? null : visibleTaskIds(window.visibleRowsObservable());
        for (TaskEntry entry : repository.entries()) {
            if (entry instanceof TaskEntry.Loaded loaded) {
                Task task = loaded.task();
                // Before the visibility gate: an auto category off screen must
                // still be reconciled. [impl->dsn~auto-pr-category~3]
                int slash = task.id().indexOf('/');
                if (slash > 0 && !task.prUrls().isEmpty()) {
                    byCategory.computeIfAbsent(task.id().substring(0, slash),
                            category -> new ArrayList<>()).addAll(task.prUrls());
                }
                if (visibleIds != null && !visibleIds.contains(task.id())) {
                    continue;
                }
                List<String> urls = task.prUrls();
                if (!urls.isEmpty()) {
                    allUrls.addAll(urls);
                    byTask.put(task.id(), urls);
                    Task.TmuxConfig tmux = task.tmux();
                    String host = TmuxHost.of(task);
                    if (host != null && tmux != null && tmux.window() != null) {
                        byWindow.computeIfAbsent(TmuxStatusPoller.key(host, tmux.window()),
                                key -> new ArrayList<>()).addAll(urls);
                    }
                }
            }
        }
        prUrlsByTask = Map.copyOf(byTask);
        prUrlsByWindow = Map.copyOf(byWindow);
        categoryPrUrls = Map.copyOf(byCategory);
        prUrls = Set.copyOf(allUrls);
    }

    /// Claude changes PR state itself (`gh pr ready --undo`, merges), and the
    /// 120 s PR-state tick would show the old icon for up to two minutes
    /// after it said so. A window whose `@cs_status` just left `working` has
    /// a Claude that finished a turn, so that task's PRs are re-read now.
    /// Runs on the status-poller thread; the `gh` calls go to the PR-state
    /// poller's own thread.
    // [impl->dsn~pr-state-refresh-on-stop~2]
    private void refreshPrStatesOfStoppedSessions(Map<String, String> previous,
            Map<String, String> current) {
        PrStatePoller poller = prStatePoller;
        if (poller == null) {
            return;
        }
        Set<String> urls = stoppedWindows(previous, current).stream()
                .flatMap(key -> prUrlsByWindow.getOrDefault(key, List.of()).stream())
                .collect(java.util.stream.Collectors.toSet());
        poller.refresh(urls);
    }

    /// Re-reads the PR states of the task the user just selected, ahead of the
    /// regular tick and past the energy-saver gate — the task on screen is the
    /// one whose PR state is being looked at, so it is the one worth a request.
    // [impl->dsn~pr-state-refresh-on-stop~2]
    private void refreshPrStatesOf(Task task) {
        PrStatePoller poller = prStatePoller;
        if (poller != null) {
            poller.refresh(Set.copyOf(task.prUrls()));
        }
    }

    /// The ids of the loaded tasks among the visible rows.
    // [impl->dsn~pr-poll-economy~2]
    static Set<String> visibleTaskIds(List<Object> rows) {
        Set<String> ids = new HashSet<>();
        for (Object row : rows) {
            if (row instanceof TaskEntry.Loaded loaded) {
                ids.add(loaded.id());
            }
        }
        return ids;
    }

    /// The status keys that were `working` before and are not any more.
    // [impl->dsn~pr-state-refresh-on-stop~2]
    static Set<String> stoppedWindows(Map<String, String> previous, Map<String, String> current) {
        Set<String> stopped = new HashSet<>();
        previous.forEach((key, status) -> {
            if ("working".equals(status) && !"working".equals(current.get(key))) {
                stopped.add(key);
            }
        });
        return stopped;
    }

    /// Every task the refactoring poller may analyze (https://github.com/contextswitcher/contextswitcher-private/issues/50): live, with a
    /// remote, a status-publishing tmux window (the idle gate needs the
    /// window's `@cs_status`), and a known worktree (`claude.workspace`, else
    /// `claude.cwd`). The group's `baseBranch` is resolved here — a config
    /// read per recompute, not per tick.
    // [impl->dsn~refactoring-analysis-poller~1]
    private void recomputeRefactoringTargets(TaskRepository repository,
            MainWindow.TaskFileAccess files) {
        List<RefactoringMinerPoller.Target> targets = new ArrayList<>();
        for (TaskEntry entry : repository.entries()) {
            if (!(entry instanceof TaskEntry.Loaded loaded)) {
                continue;
            }
            Task task = loaded.task();
            Task.ClaudeConfig claude = task.claude();
            Task.TmuxConfig tmux = task.tmux();
            String remote = task.remote();
            if (claude == null || remote == null || tmux == null || tmux.window() == null
                    || task.status() == TaskStatus.SUSPENDED) {
                continue;
            }
            String worktree = claude.workspace() != null ? claude.workspace() : claude.cwd();
            targets.add(new RefactoringMinerPoller.Target(task.id(), remote, worktree,
                    groupBaseBranch(files, taskGroup(task)),
                    TmuxStatusPoller.key(remote, tmux.window())));
        }
        refactoringTargets = List.copyOf(targets);
    }

    /// The distinct remotes of tasks that have a tmux window, for status
    /// polling. Suspended tasks never show a dot, so a host whose tmux tasks
    /// are all suspended is not polled at all — no idle ssh round-trips.
    // [impl->dsn~task-running-indicator~7]
    private void recomputeStatusHosts(TaskRepository repository) {
        statusHosts = repository.entries().stream()
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(entry -> ((TaskEntry.Loaded) entry).task())
                .filter(task -> task.tmux() != null && task.status() != TaskStatus.SUSPENDED)
                // A local session's host is the pseudo-host, polled through a
                // local shell like any remote — same dots, same @cs_title
                // sync, no ssh. [impl->dsn~terminal-local-mirror~2]
                .map(TmuxHost::of)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    /// Keeps the tmux window name in sync with a renamed task title (fire and
    /// forget on the action executor; a failure only logs — the rename itself
    /// already happened in the file).
    // [impl->dsn~task-rename-title~2]
    private void renameTmuxWindow(SshCommandRunner ssh, Task task, String newTitle) {
        Task.TmuxConfig tmux = task.tmux();
        String remote = TmuxHost.of(task);
        ExecutorService executor = this.actionExecutor;
        if (tmux == null || remote == null || executor == null) {
            return;
        }
        executor.execute(() -> {
            SshCommandRunner.SshResult result =
                    ssh.run(remote, TmuxResurrect.renameWindowCommand(tmux, newTitle));
            if (!result.ok()) {
                Logger.warn("Cannot rename tmux window {} on {}: {}",
                        tmux.target(), remote, result.stderr().strip());
            }
        });
    }

    @Override
    public void stop() {
        // First: a blocked FX thread is expected from here on (the git sync
        // below runs synchronously), and the watchdog has nothing useful to
        // say about a closing app.
        if (fxWatchdog != null) {
            fxWatchdog.interrupt();
        }
        // Persist the closing geometry: always under the startup key (the
        // next launch opens in place), and under the closing desktop's name
        // too — that one would otherwise only be saved on a desktop switch.
        // [impl->dsn~window-position-per-desktop~2]
        Stage currentStage = this.stage;
        WindowPositions positions = this.windowPositions;
        if (currentStage != null && positions != null && !currentStage.isMaximized()) {
            WindowPositions.Geometry closing = new WindowPositions.Geometry(currentStage.getX(),
                    currentStage.getY(), currentStage.getWidth(), currentStage.getHeight());
            positions.put(WindowPositions.STARTUP, closing);
            if (trackWindowPosition && positionDesktop != null) {
                positions.put(positionDesktop, closing);
            }
        }
        if (terminalPane != null) {
            terminalPane.disconnect();
            terminalPane.closeOwned();
        }
        WindowsVirtualDesktopFocus.Watch watch = this.desktopWatch;
        if (watch != null) {
            watch.close();
        }
        if (statusPoller != null) {
            statusPoller.close();
        }
        if (prStatePoller != null) {
            prStatePoller.close();
        }
        if (tmuxPrPoller != null) {
            tmuxPrPoller.close();
        }
        if (tmuxTitlePoller != null) {
            tmuxTitlePoller.close();
        }
        if (autoPrPoller != null) {
            autoPrPoller.close();
        }
        if (qodoReviewPoller != null) {
            qodoReviewPoller.close();
        }
        if (refactoringPoller != null) {
            refactoringPoller.close();
        }
        if (refactoringView != null) {
            refactoringView.close();
        }
        if (repository != null) {
            repository.close();
        }
        // Closing the app is the commit point for the task-dir git sync: after
        // the watcher stopped (no more file churn), commit everything this run
        // changed and push it. [impl->dsn~task-git-backup~5]
        // A last sync-group round first, so what it merges in rides the backup.
        // [impl->dsn~task-sync-groups-ui~1]
        TaskSync sync = this.taskSync;
        if (sync != null && !sync.groups().isEmpty()) {
            try {
                syncExecutor.submit(sync::syncAll).get(2, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
                Logger.warn("Sync-group close round did not finish: {}", e.toString());
            }
        }
        if (taskBackup != null) {
            taskBackup.syncOnClose();
        }
        TaskGitBackup provenance = this.provenanceBackup;
        if (provenance != null) {
            provenance.syncOnClose();
        }
        if (extensionServer != null) {
            try {
                extensionServer.stop(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (actionExecutor != null) {
            actionExecutor.shutdownNow();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /// Opens the tasks directory in the OS file manager (Explorer on Windows,
    /// the desktop's default elsewhere). Uses AWT `Desktop`, not
    /// `HostServices.showDocument`, whose `file://` URI otherwise opens in the
    /// default browser. Runs off the FX thread because the call can block.
    private void openTasksDir(Path dir) {
        Runnable open = () -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(dir.toFile());
                } else {
                    Logger.warn("Opening a directory is not supported on this platform: {}", dir);
                }
            } catch (Exception e) {
                Logger.warn("Cannot open tasks directory {}: {}", dir, e.getMessage());
            }
        };
        ExecutorService executor = this.actionExecutor;
        if (executor != null) {
            executor.execute(open);
        } else {
            new Thread(open, "open-tasks-dir").start();
        }
    }

    /// Opens a URL via the OS protocol handler (`Desktop.browse` →
    /// ShellExecute / xdg-open): a `jetbrains-gateway:` or `onenote:` URL
    /// goes straight to its registered application.
    /// `HostServices.showDocument` handed such URLs to the default browser,
    /// which asked for confirmation on every switch (Firefox, with no
    /// "always allow" option). Falls back to `showDocument` where AWT cannot
    /// browse. Shell dispatch is quick — safe on the FX thread; the intellij
    /// action calls it on the background executor anyway.
    // [impl->dsn~gateway-url-action~11]
    // [impl->dsn~group-note-open~5]
    private void openUrl(String url) {
        if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            } catch (IOException | IllegalArgumentException e) {
                // onenote: page links carry spaces and braces — not a valid
                // java.net.URI; showDocument refuses them too.
                Logger.warn("Desktop.browse cannot open {} ({})", url, e.getMessage());
            }
        }
        // Raw-string protocol dispatch: ShellExecute takes the URL verbatim,
        // exactly like `start "<url>"` in a shell. Windows-only command; on
        // other platforms showDocument remains the last resort.
        if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows")) {
            ExecutorService executor = this.actionExecutor;
            Runnable dispatch = () -> {
                LocalCommandRunner.LocalResult result = new LocalCommandRunner()
                        .run(List.of("rundll32", "url.dll,FileProtocolHandler", url));
                if (result.exitCode() != 0) {
                    Logger.warn("FileProtocolHandler exit {} for {}", result.exitCode(), url);
                }
            };
            if (executor != null) {
                executor.execute(dispatch);
            } else {
                dispatch.run();
            }
            return;
        }
        getHostServices().showDocument(url);
    }

    /// A category link from a group's `CONTEXTSWITCHER.md` — its `note:` or
    /// its `chat:`, read fresh through `extract` — or null when the group has
    /// no config file or no such key. Backs the [NoteFocusAction] and
    /// [ChatFocusAction] fallbacks, so a task without its own link opens its
    /// category's on switch.
    // [impl->dsn~note-focus-action~1]
    // [impl->dsn~chat-focus-action~2]
    private static @Nullable String groupLink(MainWindow.TaskFileAccess files, String group,
            java.util.function.Function<String, @Nullable String> extract) {
        if (group.isEmpty()) {
            return null;
        }
        String fileName = group + "/" + TaskRepository.GROUP_CONFIG_FILE_NAME;
        return files.exists(fileName) ? extract.apply(files.load(fileName)) : null;
    }

    /// The host a task's window belongs on: its own `remote:`, else its
    /// category's (`CONTEXTSWITCHER.md`). The task's own always wins — a task
    /// deliberately moved to another host must not be pulled back by the
    /// category it sits in — and the category's is what makes "claude" mean
    /// "tmux + claude" for a task that never recorded a host of its own
    /// (`dsn~remote-window-choice~6`).
    // [impl->dsn~remote-window-choice~6]
    static @Nullable String taskRemote(MainWindow.TaskFileAccess files, Task task) {
        return task.remote() != null ? task.remote()
                : groupLink(files, taskGroup(task),
                        content -> new TaskFileParser().parseGroupConfig(content).remote());
    }

    /// A category's virtual desktop: the `desktop:` from its
    /// `CONTEXTSWITCHER.md`, the configured fallback when it has no config or
    /// names no desktop, null for the root group (no category, nothing to fall
    /// back for) or with the fallback turned off. Read fresh so an edited
    /// `desktop:` takes effect without a restart, like [MainWindow]'s header
    /// desktop button.
    // [impl->dsn~pr-open-on-category-desktop~3]
    // [impl->dsn~fallback-desktop~1]
    private @Nullable String groupDesktop(MainWindow.TaskFileAccess files, @Nullable String group) {
        if (group == null || group.isEmpty()) {
            return null;
        }
        String fileName = group + "/" + TaskRepository.GROUP_CONFIG_FILE_NAME;
        @Nullable String desktop = files.exists(fileName)
                ? new TaskFileParser().parseGroupConfig(files.load(fileName)).desktop()
                : null;
        if (desktop != null) {
            return desktop;
        }
        String fallback = fallbackDesktop;
        return fallback.isBlank() ? null : fallback;
    }

    /// The desktop a task's category runs under **complete control**: its
    /// name when the category's `desktop:` is the nested form with
    /// `completeControl: true`, null otherwise (scalar form, no config, root
    /// task, or no name). Read fresh (like [#groupDesktop]) so an edited flag
    /// takes effect without a restart.
    // [impl->dsn~complete-control-desktop~1]
    private static @Nullable String completeControlDesktop(MainWindow.TaskFileAccess files,
            Task task) {
        String group = taskGroup(task);
        if (group.isEmpty()) {
            return null;
        }
        String fileName = group + "/" + TaskRepository.GROUP_CONFIG_FILE_NAME;
        if (!files.exists(fileName)) {
            return null;
        }
        GroupConfig config = new TaskFileParser().parseGroupConfig(files.load(fileName));
        return config.completeControl() ? config.desktop() : null;
    }

    /// The task's category config, read fresh; [GroupConfig#EMPTY] for a
    /// root-level task or a category without `CONTEXTSWITCHER.md`.
    // [impl->dsn~auto-delete-opt-in~1]
    private static GroupConfig groupConfig(MainWindow.TaskFileAccess files, Task task) {
        String group = taskGroup(task);
        String fileName = group + "/" + TaskRepository.GROUP_CONFIG_FILE_NAME;
        return group.isEmpty() || !files.exists(fileName)
                ? GroupConfig.EMPTY
                : new TaskFileParser().parseGroupConfig(files.load(fileName));
    }

    /// Rewrites a task's file through `transform` for the complete-control
    /// tab store/clear — the same read-transform-save the resurrect write-back
    /// uses, safe to call from an action thread. Returns an error text for the
    /// chip, or null on success.
    // [impl->dsn~complete-control-desktop~1]
    private static @Nullable String rewriteTaskFile(MainWindow.TaskFileAccess files, Task task,
            java.util.function.UnaryOperator<String> transform) {
        String fileName = task.id() + ".md";
        String content = files.read(fileName);
        if (content == null) {
            return "task file " + fileName + " unreadable";
        }
        return files.save(fileName, transform.apply(content));
    }

    /// A category's permanent checkout: the `mainCheckout:` from its
    /// `CONTEXTSWITCHER.md`, or null when the group is the root, has no config,
    /// or names none. Read fresh (like [#groupDesktop]) so an edited value
    /// takes effect without a restart. "Show diff" falls back to it when the
    /// task's own worktree is already deleted.
    // [impl->dsn~diff-after-worktree-removal~1]
    private static @Nullable String groupMainCheckout(MainWindow.TaskFileAccess files,
            @Nullable String group) {
        if (group == null || group.isEmpty()) {
            return null;
        }
        String fileName = group + "/" + TaskRepository.GROUP_CONFIG_FILE_NAME;
        return files.exists(fileName)
                ? new TaskFileParser().parseGroupConfig(files.load(fileName)).mainCheckout()
                : null;
    }

    /// The directories a category configures for itself — never a per-task
    /// worktree, so never something the delete cleanup may `rm -rf`. Read
    /// fresh like [#groupMainCheckout].
    // [impl->dsn~claude-session-kill~6]
    private static List<String> groupProtectedDirs(MainWindow.TaskFileAccess files, String group) {
        if (group.isEmpty()) {
            return List.of();
        }
        String fileName = group + "/" + TaskRepository.GROUP_CONFIG_FILE_NAME;
        return files.exists(fileName)
                ? new TaskFileParser().parseGroupConfig(files.load(fileName)).protectedDirs()
                : List.of();
    }

    /// The category a task lives in — the id's directory part ("" for a root
    /// task). Not [Task#folder], which is the *local* Explorer directory.
    private static String taskGroup(Task task) {
        int slash = task.id().lastIndexOf('/');
        return slash < 0 ? "" : task.id().substring(0, slash);
    }

    /// A category's diff base: the `baseBranch:` from its `CONTEXTSWITCHER.md`,
    /// defaulted to `origin/main` — for the refactoring view and badge (https://github.com/contextswitcher/contextswitcher-private/issues/50).
    /// Read fresh like the siblings above.
    // [impl->dsn~refactoring-miner-commands~1]
    private static String groupBaseBranch(MainWindow.TaskFileAccess files, String group) {
        if (group.isEmpty()) {
            return GroupConfig.DEFAULT_BASE_BRANCH;
        }
        String fileName = group + "/" + TaskRepository.GROUP_CONFIG_FILE_NAME;
        return files.exists(fileName)
                ? new TaskFileParser().parseGroupConfig(files.load(fileName)).resolveBaseBranch()
                : GroupConfig.DEFAULT_BASE_BRANCH;
    }

    /// Raw `settings.yaml` text for the settings editor (empty when absent).
    // [impl->dsn~settings-editor~4]
    private static String readSettingsFile(Path configDir) {
        Path file = configDir.resolve("settings.yaml");
        try {
            return Files.exists(file) ? Files.readString(file) : "";
        } catch (IOException e) {
            return "# Cannot read settings.yaml: " + e.getMessage();
        }
    }

    /// Writes `settings.yaml`; returns null on success, else the error.
    // [impl->dsn~settings-editor~4]
    private static @Nullable String writeSettingsFile(Path configDir, String content) {
        try {
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve("settings.yaml"), content);
            return null;
        } catch (IOException e) {
            Logger.warn("Cannot write settings.yaml: {}", e.getMessage());
            return e.getMessage();
        }
    }

    /// Pins the main window to all Windows virtual desktops when `settings`
    /// says so — at startup and again right after a settings save, since the
    /// Task View pin dies with the window handle. Off the FX thread; the
    /// outcome lands in the status bar, so a failing pin (an undocumented COM
    /// interface a Windows build moved) is visible, not just a log line.
    // [impl->dsn~window-desktop-pin~2]
    private void applyDesktopPin(AppSettings settings) {
        if (!settings.showOnAllDesktops()
                || !System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows")) {
            return;
        }
        actionExecutor.execute(() -> {
            WindowsDesktopPin.PinResult pin = new WindowsDesktopPin(new LocalCommandRunner())
                    .pin(ProcessHandle.current().pid());
            if (pin.ok()) {
                Logger.info("Show on all desktops: {}", pin.detail());
            } else {
                Logger.warn("Cannot pin the window to all desktops: {}", pin.detail());
            }
            Platform.runLater(() -> window.statusBar().message(pin.ok()
                    ? "Window " + pin.detail()
                    : "Cannot pin to all desktops: " + pin.detail()));
        });
    }

    /// Last resort for an exception no action handler caught: the log keeps
    /// the stack trace, the status bar tells the user that something just
    /// failed — a stderr line is invisible in the packaged app, and the
    /// virtual action threads are unnamed, so even the stderr line reads
    /// `Exception in thread ""`. The status-bar write is itself guarded: an
    /// exception escaping it would land back here and post another one.
    // [impl->dsn~uncaught-exception-report~1]
    private void reportUncaught(Thread thread, Throwable e) {
        Logger.error(e, "Uncaught exception on {}", thread);
        MainWindow current = this.window;
        if (current == null) {
            return;
        }
        Platform.runLater(() -> {
            try {
                current.statusBar().message("Internal error: " + e);
            } catch (RuntimeException ignored) {
                // Nothing left to report it with.
            }
        });
    }

    /// Whether the per-desktop window position memory is active
    /// (`dsn~window-position-per-desktop~2`): the window shows on all desktops
    /// (otherwise it only ever exists on one and there is nothing to
    /// position) and this is Windows (the desktop read is Windows-only).
    private static boolean positionTrackingEnabled(AppSettings settings) {
        return settings.showOnAllDesktops()
                && System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows");
    }

    /// The tag palette as currently written in settings.yaml, re-read from disk
    /// so a settings edit takes effect without a restart; falls back to the
    /// startup palette when the file cannot be reloaded.
    // [impl->dsn~task-tag-filter~2]
    private static List<AppSettings.TagDef> currentTagPalette(AppSettings startup) {
        try {
            return AppSettings.loadOrCreate(configDir()).tags();
        } catch (IOException e) {
            Logger.warn("Cannot reload settings.yaml for the tag palette: {}", e.getMessage());
            return startup.tags();
        }
    }

    /// Acts on a received `contextswitcher://` URL (FX thread): select the
    /// task (or, for a `category` link, the category's header row) and raise
    /// the app; a `switch` link additionally runs the full switch. Unknown
    /// links and unknown targets surface in the status bar — the click
    /// happened in another application, so silence would read as a dead link.
    // [impl->dsn~deep-link-url~1]
    // [impl->dsn~category-link-copy~1]
    private void handleDeepLink(SwitchOrchestrator orchestrator, TaskRepository repository,
            MainWindow.TaskFileAccess files, Stage stage, String url) {
        MainWindow window = this.window;
        if (window == null) {
            return;
        }
        DeepLink link;
        try {
            link = DeepLink.parse(url);
        } catch (IllegalArgumentException e) {
            Logger.warn("Ignoring bad deep link: {}", e.getMessage());
            window.statusBar().message("Cannot open link: " + e.getMessage());
            return;
        }
        TaskEntry.@Nullable Loaded loaded = null;
        if (link.kind() == DeepLink.Kind.CATEGORY) {
            if (!window.selectCategory(link.target())) {
                window.statusBar().message("No category '" + link.target() + "' for link " + url);
                return;
            }
        } else {
            loaded = repository.entries().stream()
                    .filter(entry -> entry instanceof TaskEntry.Loaded l
                            && l.id().equals(link.target()))
                    .map(entry -> (TaskEntry.Loaded) entry)
                    .findFirst().orElse(null);
            if (loaded == null) {
                window.statusBar().message("No task '" + link.target() + "' for link " + url);
                return;
            }
            window.selectTask(link.target());
        }
        stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
        if (link.kind() == DeepLink.Kind.SWITCH && loaded != null) {
            switchTo(orchestrator, files, loaded.task());
        }
    }

    /// Selects the task owning the browser tab the user just activated (FX
    /// thread). An exact `browser.urls` hit wins over a sub-page hit; no match
    /// (or no window yet) does nothing — the user is working in the browser,
    /// and neither a status message nor a raise of the app is wanted there.
    /// A **suspended** match is resumed on top of the selection
    /// (`dsn~browser-tab-selects-task~5`): its row alone shows nothing to work
    /// with, so the tab opens the same live context a running task's row
    /// already has.
    // [impl->dsn~browser-tab-selects-task~5]
    // [impl->dsn~browser-tab-context-count~2]
    private void selectTaskForTab(TaskRepository repository, String url, boolean resume) {
        MainWindow window = this.window;
        if (window == null) {
            return;
        }
        java.util.List<Task> matches = repository.entries().stream()
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(entry -> ((TaskEntry.Loaded) entry).task())
                .filter(task -> task.tabUrlMatch(url) > 0)
                .toList();
        // Best match first — the extension badges the tab's toolbar icon with
        // the count and lists the tasks themselves in its popup.
        java.util.List<Task> ranked = rankTabMatches(matches, url, window.selectedTaskId());
        ExtensionServer server = this.extensionServer;
        if (server != null) {
            server.sendTabCount(url, ranked.stream()
                    .map(task -> new ExtensionProtocol.TabTask(
                            task.id(), task.title(), task.status().yaml()))
                    .toList());
        }
        java.util.Optional<Task> match = ranked.stream().findFirst();
        // The one log line of this path: without it "why did my tab not select
        // the task" is undiagnosable — nothing else on it logs at all.
        Logger.debug("Tab activated {} -> {} ({} task(s) list it)",
                url, match.map(Task::id).orElse("no task"), matches.size());
        match.ifPresent(task -> {
            // A task's tabs share one tab group, so moving between them
            // reports each tab and they all match the same task. Re-selecting
            // the task already in view only scrolls, repaints and re-reveals
            // its row — visible flicker for a selection that never changed.
            if (!task.id().equals(window.selectedTaskId())) {
                window.selectTask(task.id());
            }
            // The regular resume (status back to `active`, then the switch
            // that resurrects the tmux window and resumes Claude) — the very
            // path the row's play button runs. A second activation of the same
            // tab while that is in flight sees the task already `active` and
            // does nothing.
            if (resume && task.status() == TaskStatus.SUSPENDED) {
                Logger.info("Tab activated a suspended task, resuming {}", task.id());
                window.resumeTask(task);
            }
        });
    }

    /// Config dir override for testing: `-Dcontextswitcher.configDir=<path>`.
    /// The provenance repository's copy on this machine (`dsn~provenance-repository~1`).
    private static Path provenanceDir() {
        return configDir().resolve("provenance");
    }

    /// Set once the provenance copy exists; its rounds follow the task directory's.
    private volatile @Nullable TaskGitBackup provenanceBackup;

    /// Clones the provenance repository `provenance.yaml` names when this machine
    /// has no copy yet, then runs one sync round on it. Blocking; off the FX
    /// thread. Without the file nothing happens — records stay off.
    // [impl->dsn~provenance-repository~1]
    private void syncProvenance(Path tasksDir) {
        String repo = ProvenanceConfig.repository(tasksDir);
        if (repo == null) {
            return;
        }
        Path dir = provenanceDir();
        if (!Files.exists(dir.resolve(".git"))) {
            LocalCommandRunner.LocalResult cloned = new LocalCommandRunner(Duration.ofSeconds(120))
                    .run(List.of("git", "clone", repo, dir.toString()));
            if (!cloned.ok()) {
                Logger.warn("Cannot clone the provenance repository {}: {}", repo, cloned.stderr().strip());
                return;
            }
            Logger.info("Cloned the provenance repository into {}", dir);
        }
        TaskGitBackup backup = this.provenanceBackup;
        if (backup == null) {
            backup = new TaskGitBackup(dir);
            this.provenanceBackup = backup;
        }
        backup.syncWhileRunning("ContextSwitcher provenance");
    }

    /// The `repo` a category's `CONTEXTSWITCHER.md` names, for provenance records.
    private static @Nullable String categoryRepo(Path tasksDir, String category) {
        if (category.isEmpty()) {
            return null;
        }
        Path config = tasksDir.resolve(category).resolve(TaskRepository.GROUP_CONFIG_FILE_NAME);
        try {
            return Files.isRegularFile(config) ? new TaskFileParser().parseGroupConfig(TextFiles.read(config)).repo() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static Path configDir() {
        String override = System.getProperty("contextswitcher.configDir");
        return override == null ? AppSettings.defaultConfigDir() : Path.of(override);
    }

    /// Toolbar "Restart running tasks…": after the remote machine rebooted,
    /// every active task points at a tmux window that no longer exists (and
    /// whose id a fresh tmux server hands out again, so focusing it can even
    /// land on a stranger's window). Doing it by hand means suspend + resume
    /// per task — this does the same for all running tasks at once: drop the
    /// recorded `window:` line and resurrect ([TmuxResurrect] — new window in
    /// the recorded cwd, `claude --resume`), writing the fresh id back.
    /// No kill: the stale id may already denote someone else's window.
    /// `done` runs on the FX thread once every task was tried.
    // [impl->dsn~tmux-restart-running~1]
    private void restartRunningTasks(TaskRepository repository, MainWindow.TaskFileAccess files,
            SshCommandRunner ssh, AppSettings settings, Runnable done) {
        List<Task> tasks = repository.entries().stream()
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(entry -> ((TaskEntry.Loaded) entry).task())
                .filter(task -> task.status() == TaskStatus.ACTIVE
                        && task.tmux() != null && task.remote() != null)
                .toList();
        ExecutorService executor = this.actionExecutor;
        if (executor == null) {
            done.run();
            return;
        }
        TmuxResurrect resurrect = new TmuxResurrect(ssh, settings.claudeAuto());
        executor.execute(() -> {
            int restarted = 0;
            StringBuilder errors = new StringBuilder();
            for (Task task : tasks) {
                String fileName = task.id() + ".md";
                Task.TmuxConfig tmux = task.tmux();
                String remote = task.remote();
                if (tmux == null || remote == null) {
                    continue;
                }
                String content = files.read(fileName);
                if (content == null) {
                    errors.append("\n").append(task.title()).append(": file unreadable");
                    continue;
                }
                TmuxResurrect.Resurrected result =
                        resurrect.resurrect(remote, tmux, task.claude(), task.title());
                if (result == null) {
                    // Leave the old window: line in place — nothing replaced it.
                    errors.append("\n").append(task.title()).append(": resurrect failed (see log)");
                    continue;
                }
                files.save(fileName, TaskFileParser.withTmuxWindow(
                        TaskFileParser.withoutTmuxWindow(content), result.windowId(),
                        "restarted " + LocalDate.now()));
                restarted++;
            }
            String summary = "Restarted %d of %d running task(s)%s."
                    .formatted(restarted, tasks.size(),
                            errors.length() == 0 ? "" : "\nErrors:" + errors);
            Logger.info("Restart running tasks: {}", summary);
            Alert.AlertType type = errors.length() == 0
                    ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING;
            Platform.runLater(() -> {
                Alerts.wrapping(type, summary).show();
                done.run();
            });
        });
    }

    /// Sync = for every configured remote (settings `remotes`, else the
    /// distinct remotes of existing tasks): reconcile each task's status
    /// against the live windows (window gone → suspended, back → active; no
    /// kill/resurrect side effects — [TmuxSync]) and import the windows not
    /// yet covered by a task; the directory watcher shows the new rows.
    /// A covered window that published `@cs_session_id` backfills the id into
    /// a task still lacking `claude.sessionId` (enables resume for
    /// live-created tasks); a file the sync rewrites (backfill or status change)
    /// that is currently open in the editor is reloaded, so a just-created,
    /// still-selected task shows its new `sessionId` at once instead of the
    /// stale content overwriting it on the editor's next auto-save.
    /// `announce` shows the summary alert (manual sync); the silent post-create
    /// sync (`dsn~tmux-sync~7`) passes false and only logs.
    /// `done` runs on the FX thread once the sync ended (any outcome) — the
    /// toolbar button stays disabled with an hourglass until then.
    // [impl->dsn~tmux-sync~7]
    // [impl->dsn~tmux-task-import~12]
    private void syncTmuxSessions(TaskRepository repository, AppSettings settings,
            MainWindow.TaskFileAccess files, Runnable done, boolean announce, boolean importNew) {
        var tasks = repository.entries().stream()
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(entry -> ((TaskEntry.Loaded) entry).task())
                .toList();
        List<String> remotes = settings.remotes().isEmpty()
                ? tasks.stream().map(Task::remote).filter(java.util.Objects::nonNull).distinct().toList()
                : settings.remotes();
        if (remotes.isEmpty()) {
            // Only the manual sync explains this; the periodic/auto passes stay
            // silent so an alert does not pop up on every tick.
            if (announce) {
                new Alert(Alert.AlertType.INFORMATION,
                        "No remotes to sync. Add hosts to the remotes: list in settings.yaml,"
                        + " or create a task with a remote: first.").show();
            }
            done.run();
            return;
        }
        var knownSessionIds = tasks.stream()
                .map(task -> task.claude() == null ? null : task.claude().sessionId())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        ExecutorService executor = this.actionExecutor;
        if (executor == null) {
            done.run();
            return;
        }
        executor.execute(() -> {
            try {
                int created = 0;
                int reactivated = 0;
                int suspended = 0;
                int deleted = 0;
                int backfilled = 0;
                int rehomed = 0;
                int committed = 0;
                // Files the sync rewrote, so an editor showing one can reload
                // (feature: the open, just-created task must reflect its
                // backfilled sessionId immediately, not stomp it on auto-save).
                List<String> changedFiles = new ArrayList<>();
                StringBuilder errors = new StringBuilder();
                // Host-routed: everything tmux-facing addresses a host, and a local
        // Claude session's is the pseudo-host `TmuxHost.LOCAL` — its commands
        // run through a local shell, every real host's over ssh.
        // [impl->dsn~terminal-local-mirror~2]
        SshCommandRunner ssh = new HostCommandRunner(new ProcessSshRunner());
                for (String remote : remotes) {
                    try {
                        var discovered = new TmuxDiscovery(ssh).listSessions(remote);
                        var sessions = enrichClaudeSessions(remote, discovered,
                                new ClaudeSessionLookup(ssh), new ClaudePrLookup(ssh));
                        // Reconcile against the raw discovery output: the
                        // match compares Claude session ids too, and the
                        // enrichment's guessed id must never suspend a task.
                        for (TmuxSync.Reconciliation change : TmuxSync.reconcile(tasks, remote, discovered,
                                task -> groupConfig(files, task).mayAutoDelete(task))) {
                            String fileName = change.taskId() + ".md";
                            switch (change) {
                                case TmuxSync.Reconciliation.Delete ignored -> {
                                    // A disposable shell (closed plain window,
                                    // nothing recorded): remove it instead of
                                    // leaving a suspended husk (dsn~tmux-sync~7).
                                    // Not added to changedFiles — reloading a
                                    // just-deleted file would show error text.
                                    String error = files.delete(fileName);
                                    if (error != null) {
                                        Logger.warn("Skipping delete of disposable {}: {}", fileName, error);
                                    } else {
                                        deleted++;
                                    }
                                }
                                case TmuxSync.Reconciliation.SetStatus set -> {
                                    // read (not load): a load() failure returns
                                    // error text that, transformed and saved,
                                    // would clobber the file (dsn~richtext-markdown-editor~9).
                                    String content = files.read(fileName);
                                    if (content == null) {
                                        Logger.warn("Skipping status sync of {}: file unreadable", fileName);
                                        continue;
                                    }
                                    files.save(fileName, TaskFileParser.withStatus(content, set.status()));
                                    changedFiles.add(fileName);
                                    if (set.status() == TaskStatus.ACTIVE) {
                                        reactivated++;
                                    } else {
                                        suspended++;
                                        // Name the task and why: a taken-over
                                        // window looks exactly like a dead one
                                        // from the outside (dsn~tmux-sync~7).
                                        String note = "Suspended %s — %s. Press play to resume."
                                                .formatted(change.taskId(), set.reason());
                                        MainWindow shown = this.window;
                                        if (shown != null) {
                                            Platform.runLater(() -> shown.statusBar().message(note));
                                        }
                                    }
                                    Logger.info("Tmux sync: {} {} — {}", set.status(), change.taskId(), set.reason());
                                }
                            }
                        }
                        // Backfill from the raw discovery output: only the
                        // exact published @cs_session_id, never the
                        // enrichment's newest-transcript guess.
                        for (TmuxSync.SessionIdBackfill fill : TmuxSync.sessionIdBackfills(
                                tasks, remote, discovered)) {
                            String fileName = fill.taskId() + ".md";
                            String content = files.read(fileName);
                            if (content == null) {
                                Logger.warn("Skipping session-id backfill of {}: file unreadable", fileName);
                                continue;
                            }
                            // withClaudeSection is a no-op when the task already
                            // has one; it only matters for the adopted case —
                            // a window imported as a plain shell that the user
                            // later started Claude in.
                            files.save(fileName, TaskFileParser.withClaudeSessionId(
                                    TaskFileParser.withClaudeSection(content, fill.cwd()),
                                    fill.sessionId()));
                            changedFiles.add(fileName);
                            backfilled++;
                        }
                        // Follow the live @cs_workspace: the import snapshots
                        // it once, but a live task's window publishes it only
                        // after Claude bootstrapped its worktree — and a
                        // session moving to another worktree publishes again.
                        // [impl->dsn~claude-workspace-capture~2]
                        for (TmuxSync.WorkspaceRefresh refresh : TmuxSync.workspaceRefreshes(
                                tasks, remote, discovered)) {
                            String fileName = refresh.taskId() + ".md";
                            String content = files.read(fileName);
                            if (content == null) {
                                Logger.warn("Skipping workspace refresh of {}: file unreadable", fileName);
                                continue;
                            }
                            files.save(fileName, TaskFileParser.withClaudeWorkspace(
                                    content, refresh.workspace()));
                            changedFiles.add(fileName);
                            rehomed++;
                        }
                        // Follow the live @cs_commit: the sha outlives the
                        // worktree Claude deletes after committing, and is all
                        // "Show diff" has left to show afterwards.
                        // [impl->dsn~diff-after-worktree-removal~1]
                        for (TmuxSync.CommitRefresh refresh : TmuxSync.commitRefreshes(
                                tasks, remote, discovered)) {
                            String fileName = refresh.taskId() + ".md";
                            String content = files.read(fileName);
                            if (content == null) {
                                Logger.warn("Skipping commit refresh of {}: file unreadable", fileName);
                                continue;
                            }
                            files.save(fileName, TaskFileParser.withClaudeCommit(
                                    content, refresh.commit()));
                            changedFiles.add(fileName);
                            committed++;
                        }
                        // Import (create tasks for not-yet-covered windows) only
                        // on the manual sync. The periodic/auto passes reconcile
                        // and backfill but must not auto-spawn a task for every
                        // tmux window (dsn~tmux-sync~7).
                        if (importNew) {
                            var result = TmuxTaskImporter.write(remote, sessions,
                                    TmuxSync.coverageKeys(tasks, remote), knownSessionIds,
                                    settings.tasksDir(), LocalDate.now(), settings.hints());
                            created += result.created().size();
                        }
                    } catch (Exception e) {
                        errors.append("\n").append(remote).append(": ").append(e.getMessage());
                    }
                }
                String summary = "Synced %d remote(s): imported %d, reactivated %d, suspended %d, deleted %d%s."
                        .formatted(remotes.size(), created, reactivated, suspended, deleted,
                                (backfilled == 0 ? "" : ", session ids backfilled " + backfilled)
                                + (rehomed == 0 ? "" : ", workspaces refreshed " + rehomed)
                                + (committed == 0 ? "" : ", commits recorded " + committed))
                        + (errors.length() == 0 ? "" : "\nErrors:" + errors);
                Logger.info("Tmux sync{}: {}", announce ? "" : " (auto)", summary);
                MainWindow window = this.window;
                if (window != null && !changedFiles.isEmpty()) {
                    Platform.runLater(() -> changedFiles.forEach(window::reloadEditorIfShowing));
                }
                if (announce) {
                    Alert.AlertType type = errors.length() == 0
                            ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING;
                    Platform.runLater(() -> Alerts.wrapping(type, summary).show());
                }
            } finally {
                Platform.runLater(done);
            }
        });
    }

    /// Re-reads what **one** task's own tmux window publishes about its Claude
    /// session and writes it into the task file, then runs `done` on the FX
    /// thread. The teardown path calls this before it judges what suspending
    /// would lose: Claude may have been started **by hand** in a window that
    /// was imported as a plain shell, so the task file can be a reconcile
    /// interval behind the live session — or carry no `claude:` section at all
    /// while a resumable session runs in the window.
    ///
    /// Only the incomplete case pays the round-trip: a task whose section
    /// already carries a `sessionId` (or which has no window to ask) continues
    /// straight away, so the normal silent suspend stays instant. `done` runs
    /// on **every** outcome — an unreachable host must not swallow the suspend.
    // [impl->dsn~teardown-claude-resync~1]
    private void refreshClaudeFromWindow(SshCommandRunner ssh, MainWindow.TaskFileAccess files,
            Task task, Runnable done) {
        Task.ClaudeConfig claude = task.claude();
        String remote = task.remote();
        String windowId = task.tmux() == null ? null : task.tmux().window();
        ExecutorService executor = this.actionExecutor;
        if (remote == null || windowId == null || executor == null
                || (claude != null && claude.sessionId() != null)) {
            done.run();
            return;
        }
        MainWindow window = this.window;
        if (window != null) {
            window.statusBar().message("Checking " + windowId + " on " + remote + " …");
        }
        executor.execute(() -> {
            String outcome;
            try {
                // The plain discovery listing, not the enrichment: only the id
                // the session published itself may be written (dsn~tmux-sync~7).
                var found = new TmuxDiscovery(ssh).listSessions(remote).stream()
                        .flatMap(session -> session.windows().stream())
                        .filter(live -> live.id().equals(windowId))
                        .findFirst();
                outcome = found.isEmpty()
                        ? "Window " + windowId + " is gone on " + remote + "."
                        : adoptClaudeFacts(files, task.id() + ".md", found.get())
                                ? "Adopted the Claude session running in " + windowId + "."
                                : "No Claude session published by " + windowId + ".";
            } catch (Exception e) {
                outcome = "Cannot check " + windowId + " on " + remote + ": " + e.getMessage();
                Logger.debug("Claude re-sync of {} failed: {}", task.id(), e.getMessage());
            }
            // The teardown continues on any outcome — a host that cannot be
            // reached must not swallow the suspend, it only means the dialog
            // decides on what the file already knew.
            String message = outcome;
            Platform.runLater(() -> {
                if (window != null) {
                    window.statusBar().message(message);
                }
                done.run();
            });
        });
    }

    /// Writes everything `window` publishes about its Claude session into the
    /// task file: the `claude:` section itself (created from the window's cwd
    /// when the task has none), then the session id, workspace, and commit.
    /// Unlike the sync's per-fact loops this runs on one window at a time and
    /// therefore writes all four in one pass — the teardown decision happens
    /// seconds later, not a tick later. Options the window never set are left
    /// alone, and an editor showing the file is reloaded so it does not save
    /// the stale content back over the adoption. Returns whether the file
    /// changed — the status bar reports the round-trip either way.
    // [impl->dsn~teardown-claude-resync~1]
    private boolean adoptClaudeFacts(MainWindow.TaskFileAccess files, String fileName,
            TmuxDiscovery.TmuxWindow live) {
        String content = files.read(fileName);
        if (content == null) {
            Logger.warn("Skipping Claude re-sync of {}: file unreadable", fileName);
            return false;
        }
        String updated = content;
        if (live.sessionId() != null) {
            updated = TaskFileParser.withClaudeSessionId(
                    TaskFileParser.withClaudeSection(updated, live.cwd()), live.sessionId());
        }
        if (!live.workspace().strip().isEmpty()) {
            updated = TaskFileParser.withClaudeWorkspace(updated, live.workspace().strip());
        }
        if (!live.commit().strip().isEmpty()) {
            updated = TaskFileParser.withClaudeCommit(updated, live.commit().strip());
        }
        if (updated.equals(content)) {
            return false;
        }
        files.save(fileName, updated);
        Logger.info("Re-synced Claude session of {} from window {}", fileName, live.id());
        MainWindow window = this.window;
        if (window != null) {
            Platform.runLater(() -> window.reloadEditorIfShowing(fileName));
        }
        return true;
    }

    /// Seconds between silent post-create syncs that backfill a live task's
    /// `@cs_session_id` — the first delay is enough for Claude Code to boot and
    /// its `SessionStart` hook to publish the id onto the window.
    private static final int POST_CREATE_SYNC_SECONDS = 30;

    /// How many times the post-create backfill retries before giving up. Retries
    /// (not one-shot) so the id still lands when Claude booted slowly **or** when
    /// the user's editor save dropped a just-written id back out (`dsn~tmux-sync~7`).
    private static final int POST_CREATE_SYNC_ATTEMPTS = 5;

    /// Starts the retrying post-create backfill for a freshly created live task,
    /// so the session id Claude publishes on startup is recorded without the
    /// user pressing "Sync tmux windows…".
    // [impl->dsn~tmux-sync~7]
    private void schedulePostCreateSync(TaskRepository repository, AppSettings settings,
            MainWindow.TaskFileAccess files, String taskId) {
        scheduleSessionIdBackfill(repository, settings, files, taskId, POST_CREATE_SYNC_ATTEMPTS);
    }

    /// Schedules one silent [#syncTmuxSessions] `POST_CREATE_SYNC_SECONDS` out,
    /// then chains the next attempt — stopping as soon as `taskId` carries a
    /// `claude.sessionId` (`dsn~tmux-sync~7`). Each tick first checks the
    /// repository (updated by the file watcher within ~1 s of the previous
    /// sync's write), so a captured id ends the chain and a dropped one (an
    /// editor save between attempts) is written again. Daemon-scheduled off the
    /// FX thread; the sync is re-entered on the FX thread.
    // [impl->dsn~tmux-sync~7]
    private void scheduleSessionIdBackfill(TaskRepository repository, AppSettings settings,
            MainWindow.TaskFileAccess files, String taskId, int attemptsLeft) {
        ScheduledExecutorService scheduler = this.scheduler;
        if (scheduler == null || attemptsLeft <= 0) {
            return;
        }
        scheduler.schedule(() -> Platform.runLater(() -> {
            if (sessionIdRecorded(repository, taskId)) {
                return;
            }
            syncTmuxSessions(repository, settings, files, () -> { }, false, false);
            scheduleSessionIdBackfill(repository, settings, files, taskId, attemptsLeft - 1);
        }), POST_CREATE_SYNC_SECONDS, TimeUnit.SECONDS);
    }

    /// Whether the loaded task `taskId` already carries a Claude session id —
    /// the stop condition for the post-create backfill retries.
    private static boolean sessionIdRecorded(TaskRepository repository, String taskId) {
        return repository.entries().stream()
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(entry -> ((TaskEntry.Loaded) entry).task())
                .filter(task -> task.id().equals(taskId))
                .anyMatch(task -> task.claude() != null && task.claude().sessionId() != null);
    }

    /// The wizard's answers kept for after the window is up — its first
    /// project is created there. [impl->dsn~setup-wizard~8]
    private SetupWizard.@Nullable Result firstProject;

    /// The first-start wizard, on a task directory that is missing or empty
    /// and not a git checkout — the same moment the clone offer used to pick
    /// (`isEmptyOrMissing`: where a clone is safe and the fresh-machine
    /// recovery applies; an existing directory is never re-prompted). A
    /// remote the wizard names is patched into `settings.yaml`'s `remotes`
    /// (the settings are re-read, so the sync and the add-task dialog see
    /// it right away); a task repository URL is cloned into the task
    /// directory; the first project waits in [#firstProject]. Cancel leaves
    /// everything as it was: an empty directory, seeded with `TEMPLATE.md`.
    // [impl->dsn~setup-wizard~8]
    // [impl->dsn~task-git-clone-setup~2]
    private AppSettings runSetupWizard(AppSettings settings) throws IOException {
        Path tasksDir = settings.tasksDir();
        if (!isEmptyOrMissing(tasksDir) || Files.exists(tasksDir.resolve(".git"))) {
            return settings;
        }
        SetupWizard.Result result = showSetupWizard(true, SetupWizard.Prefill.EMPTY, settings).orElse(null);
        if (result == null) {
            return settings;
        }
        this.firstProject = result;
        AppSettings current = applyWizardSettings(settings, result);
        if (result.taskRepoUrl() != null) {
            cloneTaskRepo(result.taskRepoUrl(), tasksDir);
        }
        return current;
    }

    /// The wizard with its runners: the connection check, this machine's
    /// tool check, and the installers' runner — minutes rather than the
    /// ten-second default, since they download release zips; on the remote
    /// through ssh, locally through the same `sh -c` the local tmux runner
    /// uses. `offerClone` is the first-start flag (`SetupWizard.show`).
    // [impl->dsn~setup-wizard~8]
    private Optional<SetupWizard.Result> showSetupWizard(boolean offerClone,
            SetupWizard.Prefill prefill, AppSettings settings) {
        String home = System.getProperty("user.home");
        boolean onWindows = LocalCommandRunner.onWindows();
        Duration installTimeout = Duration.ofMinutes(5);
        SshCommandRunner tools = new HostCommandRunner(new ProcessSshRunner(installTimeout),
                new LocalTmuxRunner(new LocalCommandRunner(installTimeout)),
                new WslTmuxRunner(new LocalCommandRunner(installTimeout)));
        // The connection check goes through the routing runner too: a WSL
        // choice is checked through wsl.exe, not through ssh.
        // [impl->dsn~wsl-sessions~1]
        return SetupWizard.show(new HostCommandRunner(new ProcessSshRunner()),
                () -> SetupWizard.localVerdict(new LocalCommandRunner(), home, onWindows),
                tools, home, onWindows, offerClone, prefill,
                new SetupWizard.Extension(settings.wsPort(), settings.wsToken(), settings.browser(),
                        this::openUrl, () -> {
                            ExtensionServer server = this.extensionServer;
                            return server == null ? null : server.connectedBrowser();
                        }));
    }

    /// Patches the wizard's settings answers into `settings.yaml` — a remote
    /// not yet listed, and the RefactoringMiner directory the tools page
    /// found or installed (the setting the badge and the view are gated on,
    /// `dsn~recommended-tools~1`) — and returns the re-read settings.
    // [impl->dsn~setup-wizard~8]
    // [impl->dsn~recommended-tools~1]
    private static AppSettings applyWizardSettings(AppSettings settings, SetupWizard.Result result)
            throws IOException {
        AppSettings current = settings;
        if (result.remote() != null && !settings.remotes().contains(result.remote())) {
            List<String> remotes = new java.util.ArrayList<>(settings.remotes());
            remotes.add(result.remote());
            String error = writeSettingsFile(configDir(),
                    YamlPatch.set(readSettingsFile(configDir()), "remotes", remotes));
            if (error != null) {
                new Alert(Alert.AlertType.ERROR, "Cannot write the remote to settings.yaml: "
                        + error).showAndWait();
            }
            current = AppSettings.loadOrCreate(configDir());
        }
        if (result.refactoringMinerHome() != null) {
            String error = writeSettingsFile(configDir(), YamlPatch.set(readSettingsFile(configDir()),
                    "refactoringMinerHome", result.refactoringMinerHome()));
            if (error != null) {
                new Alert(Alert.AlertType.ERROR,
                        "Cannot write refactoringMinerHome to settings.yaml: " + error).showAndWait();
            }
            current = AppSettings.loadOrCreate(configDir());
        }
        return current;
    }

    /// The wizard from the menu, with the window up: the same pages minus
    /// the clone one, the settings patched the same way, and the first
    /// project created right away instead of after the window shows. The
    /// remotes are read fresh by every consumer, so a new one works at once;
    /// the refactoring badge's poller starts at startup only, so a newly
    /// installed RefactoringMiner is announced as needing a restart.
    // [impl->dsn~setup-wizard~8]
    private void rerunSetupWizard() {
        MainWindow window = this.window;
        if (window == null) {
            return;
        }
        try {
            AppSettings before = AppSettings.loadOrCreate(configDir());
            // The fields start from what is configured: the first remote,
            // the existing roots' parent, the RefactoringMiner directory.
            SetupWizard.Prefill prefill = new SetupWizard.Prefill(
                    before.remotes().isEmpty() ? null : before.remotes().getFirst(),
                    window.configuredWorkspacesParent(), before.refactoringMinerHome());
            SetupWizard.Result result = showSetupWizard(false, prefill, before).orElse(null);
            if (result == null) {
                return;
            }
            AppSettings after = applyWizardSettings(before, result);
            String message = "Setup saved.";
            if (after.refactoringMinerHome() != null && this.refactoringPoller == null) {
                message = "Setup saved — restart for the refactoring badge.";
            }
            window.statusBar().message(message);
            if (result.repoUrl() != null && result.workspacesRoot() != null) {
                window.createCategoryFromRepo(result.repoUrl(), result.workspacesRoot(),
                        result.remote(), result.repoType(), MainWindow.lastMode(), result.firstMessage());
            }
        } catch (IOException e) {
            Logger.warn("Setup wizard: cannot read settings.yaml: {}", e.getMessage());
            new Alert(Alert.AlertType.ERROR, "Cannot read settings.yaml: " + e.getMessage()).show();
        }
    }

    /// `git clone <url> <tasksDir>` via the system git (MADR 0011; a
    /// network-tolerant timeout); a failure is logged and shown, and startup
    /// continues with an empty directory.
    // [impl->dsn~task-git-clone-setup~2]
    private static void cloneTaskRepo(String url, Path tasksDir) {
        LocalCommandRunner.LocalResult result = new LocalCommandRunner(Duration.ofSeconds(120))
                .run(List.of("git", "clone", url, tasksDir.toString()));
        if (!result.ok()) {
            Logger.warn("Task repo clone failed: {}", result.stderr().strip());
            new Alert(Alert.AlertType.ERROR,
                    "Could not clone the task repository:\n" + result.stderr().strip()
                            + "\n\nStarting with an empty task directory.").showAndWait();
        } else {
            Logger.info("Cloned task repository into {}", tasksDir);
        }
    }

    /// On a fresh install — the same empty/missing task directory the clone
    /// offer keys on — name the command-line tools that are not on `PATH`.
    /// Once, not on every start: they are installed once, and a check on each
    /// launch would be a permanent nag for a one-time problem.
    /// Windows is skipped: `ssh` ships with it and the local sessions there
    /// are hosted by the app itself, so `tmux` is not part of that install.
    /// A missing `tmux` is the one that bites — without it a local Claude
    /// session has nowhere to run, and the failure would only surface at the
    /// first click.
    // [impl->dsn~startup-tool-check~1]
    private void warnAboutMissingTools(Path tasksDir) {
        if (LocalCommandRunner.onWindows() || !isEmptyOrMissing(tasksDir)) {
            return;
        }
        List<String> missing = RequiredTools.missing();
        if (missing.isEmpty()) {
            return;
        }
        Logger.warn("Not on PATH: {}", String.join(", ", missing));
        new Alert(Alert.AlertType.WARNING,
                "These tools are not on your PATH:\n\n    " + String.join(", ", missing)
                        + "\n\ntmux runs local and remote Claude sessions, ssh reaches every"
                        + " remote, git syncs the task files. Install what you need and restart;"
                        + " the rest of the app works meanwhile.").showAndWait();
    }

    private static boolean isEmptyOrMissing(Path dir) {
        if (!Files.exists(dir)) {
            return true;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            return false; // cannot tell — do not risk cloning over it
        }
    }

    /// The clone dialog: a URL field with **Clone** / **Skip git sync**.
    /// Returns the entered URL when Clone is pressed with a non-blank URL,
    /// else null (skip).
    private static @Nullable String promptCloneUrl() {
        javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("ContextSwitcher — task sync");
        dialog.setHeaderText("No tasks found here yet.\n"
                + "Clone your task repository to sync from another machine, or skip to start fresh.");
        javafx.scene.control.ButtonType clone =
                new javafx.scene.control.ButtonType("Clone", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType skip = new javafx.scene.control.ButtonType(
                "Skip git sync", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(clone, skip);

        javafx.scene.control.TextField urlField = new javafx.scene.control.TextField();
        urlField.setPromptText("git@github.com:me/my-tasks.git  or  https://…");
        urlField.setPrefColumnCount(40);
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(6,
                new javafx.scene.control.Label("Clone URL"), urlField);
        box.setPadding(new javafx.geometry.Insets(8));
        dialog.getDialogPane().setContent(box);
        Platform.runLater(urlField::requestFocus);
        dialog.setResultConverter(button -> button == clone ? urlField.getText().strip() : null);

        String url = dialog.showAndWait().orElse(null);
        return url == null || url.isEmpty() ? null : url;
    }

    /// One-time move of the queue state from its old `<configDir>` home into
    /// the task directory's `.queues/` (and `.queues/qodo/`), so it rides the
    /// task-dir git backup. Runs before the backup's startup pull; a no-op once
    /// moved (the new location exists) and on a fresh install (nothing to move).
    /// ponytail: last-writer-wins across two machines upgrading independently —
    /// whichever `.queues/` reaches the remote first is the one both keep; the
    /// other machine's not-yet-migrated old queues stay in `<configDir>` and are
    /// abandoned. Fine for a single user's transient queue state.
    // [impl->dsn~message-queue-store~2]
    private static void migrateQueuesIntoTaskDir(Path configDir, Path queueDir) {
        moveIfAbsent(configDir.resolve("queues"), queueDir);
        moveIfAbsent(configDir.resolve("qodo"), queueDir.resolve("qodo"));
    }

    private static void moveIfAbsent(Path from, Path to) {
        try {
            if (Files.isDirectory(from) && !Files.exists(to)) {
                Files.createDirectories(to.getParent());
                Files.move(from, to);
                Logger.info("Migrated queue state {} -> {}", from, to);
            }
        } catch (IOException e) {
            Logger.warn("Could not migrate queue state {} to {}: {}", from, to, e.getMessage());
        }
    }

    /// Writes `content` to `file` without ever leaving it half-written: the
    /// bytes go to a sibling `.tmp` first and only then replace the target in
    /// one move. A plain `Files.writeString` truncates before it writes, so a
    /// shutdown (or crash) between the two turns a task file into 0 bytes —
    /// which is exactly how a task file lost its content on 2026-09-07.
    /// Falls back to the non-atomic move on filesystems without `ATOMIC_MOVE`.
    // [impl->dsn~task-file-atomic-write~1]
    static void writeAtomically(Path file, String content) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /// File names come from TaskEntry (watcher-derived), i.e. from within the
    /// tasks directory; resolution stays inside it.
    // [impl->dsn~richtext-markdown-editor~9]
    private static MainWindow.TaskFileAccess taskFileAccess(Path tasksDir, Path queuesDir) {
        return new MainWindow.TaskFileAccess() {
            @Override
            public String load(String fileName) {
                try {
                    return Files.readString(tasksDir.resolve(fileName));
                } catch (IOException e) {
                    return "Cannot read %s: %s".formatted(fileName, e.getMessage());
                }
            }

            @Override
            public @Nullable String read(String fileName) {
                try {
                    return Files.readString(tasksDir.resolve(fileName));
                } catch (IOException e) {
                    return null;
                }
            }

            @Override
            public @Nullable String save(String fileName, String content) {
                try {
                    Path file = tasksDir.resolve(fileName);
                    // A task created into a fresh project group needs its
                    // subfolder first.
                    // [impl->dsn~task-create-ui~15]
                    if (file.getParent() != null) {
                        Files.createDirectories(file.getParent());
                    }
                    writeAtomically(file, content);
                    // Folder now holds a real file — drop its .gitkeep.
                    // [impl->dsn~empty-category-gitkeep~1]
                    Gitkeep.drop(tasksDir, file.getParent());
                    return null;
                } catch (IOException e) {
                    Logger.warn("Cannot save {}: {}", fileName, e.getMessage());
                    return e.getMessage();
                }
            }

            // [impl->dsn~task-create-ui~15]
            @Override
            public boolean exists(String fileName) {
                return Files.exists(tasksDir.resolve(fileName));
            }

            // [impl->dsn~category-create-ui~2]
            // [impl->dsn~empty-category-gitkeep~1]
            @Override
            public @Nullable String createFolder(String folder) {
                try {
                    Path dir = tasksDir.resolve(folder);
                    Files.createDirectories(dir);
                    // git cannot track an empty directory: a .gitkeep holds the
                    // freshly created (empty) category so it commits and syncs.
                    Gitkeep.ensure(tasksDir, dir);
                    return null;
                } catch (IOException e) {
                    Logger.warn("Cannot create folder {}: {}", folder, e.getMessage());
                    return e.getMessage();
                }
            }

            // [impl->dsn~group-rename~1]
            @Override
            public @Nullable String renameFolder(String folder, String newFolder) {
                try {
                    Files.move(tasksDir.resolve(folder), tasksDir.resolve(newFolder));
                    return null;
                } catch (IOException e) {
                    Logger.warn("Cannot rename folder {} to {}: {}", folder, newFolder, e.getMessage());
                    return e.getMessage();
                }
            }

            // [impl->dsn~category-delete~1]
            @Override
            public @Nullable String deleteFolder(String folder) {
                Path dir = tasksDir.resolve(folder);
                try (Stream<Path> paths = Files.walk(dir)) {
                    // Reverse path order = children before their parent directory.
                    for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                        Files.deleteIfExists(p);
                    }
                    return null;
                } catch (IOException e) {
                    Logger.warn("Cannot delete folder {}: {}", folder, e.getMessage());
                    return e.getMessage();
                }
            }

            // [impl->dsn~task-move-dnd~6]
            @Override
            public @Nullable String move(String fromFileName, String toFileName) {
                try {
                    Path to = tasksDir.resolve(toFileName);
                    if (to.getParent() != null) {
                        Files.createDirectories(to.getParent());
                    }
                    Path from = tasksDir.resolve(fromFileName);
                    Files.move(from, to);
                    // The queue is keyed by task id — it follows the rename.
                    QueueFile.rename(queuesDir,
                            fromFileName.replaceFirst("\\.md$", ""),
                            toFileName.replaceFirst("\\.md$", ""));
                    // The qodo-harvested memory is keyed by task id too, so
                    // it must follow or every prompt re-queues under the new id.
                    // (It nests under `.queues/qodo`, so resolve, not sibling.)
                    QodoImported.rename(queuesDir.resolve("qodo"),
                            fromFileName.replaceFirst("\\.md$", ""),
                            toFileName.replaceFirst("\\.md$", ""));
                    // Target gained a file; source may have gone empty.
                    // [impl->dsn~empty-category-gitkeep~1]
                    Gitkeep.drop(tasksDir, to.getParent());
                    Gitkeep.ensure(tasksDir, from.getParent());
                    return null;
                } catch (IOException e) {
                    Logger.warn("Cannot move {} to {}: {}", fromFileName, toFileName, e.getMessage());
                    return e.getMessage();
                }
            }

            // [impl->dsn~task-delete~6]
            @Override
            public @Nullable String delete(String fileName) {
                try {
                    Path file = tasksDir.resolve(fileName);
                    Files.deleteIfExists(file);
                    // Deleting the last task in a category leaves it empty —
                    // keep it representable in git with a .gitkeep.
                    // [impl->dsn~empty-category-gitkeep~1]
                    Gitkeep.ensure(tasksDir, file.getParent());
                    return null;
                } catch (IOException e) {
                    Logger.warn("Cannot delete {}: {}", fileName, e.getMessage());
                    return e.getMessage();
                }
            }
        };
    }

    /// Creates a task for a GitHub PR URL (the caller already checked no
    /// task carries it): PR title via the local `gh` CLI (fallback derived
    /// from the URL), a fresh tmux window in the remote's usual session
    /// (that of its first task, else `0`), Claude started inside with a
    /// context prompt, and a task file carrying the PR as `browser.urls`
    /// entry — written before the remote work, so the row is in the list
    /// showing its creation progress while the window comes up, and the tmux
    /// target is written back once it is there. The new row is selected when
    /// the watcher delivers it.
    // [impl->dsn~task-from-pr~6]
    private void createTaskFromPr(TaskRepository repository, MainWindow.TaskFileAccess files,
            SshCommandRunner ssh, MessageSender sender, String url, String remote, String group,
            AppSettings settings, ClaudeMode mode) {
        boolean hints = settings.hints();
        boolean claudeAuto = settings.claudeAuto();
        ExecutorService executor = this.actionExecutor;
        MainWindow window = this.window;
        if (executor == null || window == null) {
            return;
        }
        String session = usualSession(repository, remote);
        creationStarted(window);
        executor.execute(() -> {
            String title = new PrTitleLookup(new LocalCommandRunner()).title(url);
            if (title == null) {
                Platform.runLater(() -> {
                    creationFinished(window, "Task creation failed.");
                    window.noteTaskCreationFailed();
                    new Alert(Alert.AlertType.ERROR, "Not a GitHub PR or GitLab MR URL: " + url).show();
                });
                return;
            }
            String prompt = "Context: " + url + ". Task description will follow in next message.";
            // The file is written before the remote work, without the `tmux:`
            // section the launcher has yet to produce: the row is in the list
            // from the first second, showing its creation progress, instead of
            // appearing only once the remote is done.
            // [impl->dsn~task-create-progress~5]
            // Provenance under # Notes only in intro mode.
            // [impl->dsn~skeleton-hints~4]
            String content = TaskFileParser.prTaskContent(title, remote, url,
                    hints ? "Created from %s on %s.".formatted(url, LocalDate.now()) : null);
            Platform.runLater(() -> {
                String base = TaskFileParser.newTaskFileName(group.isEmpty() ? title : group + "/" + title);
                String taskId = saveNewTask(files, window, base, content);
                if (taskId == null) {
                    return;
                }
                // Same as a live task: the launch prompt is the task's last
                // sent message, so a paste that silently missed the not-yet-up
                // TUI is still there to re-send.
                // [impl->dsn~last-sent-message~5]
                QueueFile.saveSent(settings.tasksDir().resolve(".queues"), taskId, prompt);
                window.selectCreatedTask(taskId);
                creationStep(window, taskId, "Creating the tmux window …");
                executor.execute(() -> {
                    String windowId = new ClaudeWindowLauncher(ssh, sender, claudeAuto)
                            .launch(remote, session, title, prompt, null, mode,
                                    step -> Platform.runLater(() -> creationStep(window, taskId, step)),
                                    fresh -> Platform.runLater(() ->
                                            recordCreatedWindow(files, window, taskId, session, fresh)));
                    Platform.runLater(() -> finishCreation(window, taskId, windowId, remote));
                });
            });
        });
    }

    /// Reports a creation step on the task's row and, while that task is the
    /// selected one *and the window does not exist yet*, in the terminal lane
    /// too — a freshly created task is selected right away, and its mirror has
    /// no window to attach to yet. Once the window is recorded the remaining
    /// steps ("Starting Claude …", "Sending the prompt …") stay on the row
    /// alone: [TerminalPane#showMessage] ends the connection, so standing in
    /// again would tear the just-attached live mirror down.
    /// FX thread.
    // [impl->dsn~task-create-progress~5]
    private void creationStep(MainWindow window, String taskId, String step) {
        window.creationProgress(taskId, step);
        if (windowedCreations.contains(taskId)) {
            return;
        }
        creationSteps.put(taskId, step);
        TerminalPane terminal = this.terminalPane;
        if (terminal != null && taskId.equals(window.selectedTaskId())) {
            terminal.showMessage(step);
        }
    }

    /// Saves a freshly created task file under `base`, de-duplicated with
    /// `-2`/`-3`… like the plain flow, and returns its task id — or null when
    /// the save failed, having reported it. FX thread.
    // [impl->dsn~task-create-progress~5]
    private @Nullable String saveNewTask(MainWindow.TaskFileAccess files, MainWindow window,
            String base, String content) {
        String fileName = base + ".md";
        for (int i = 2; files.exists(fileName); i++) {
            fileName = base + "-" + i + ".md";
        }
        String error = files.save(fileName, content);
        if (error != null) {
            creationFinished(window, "Task creation failed.");
            window.noteTaskCreationFailed();
            new Alert(Alert.AlertType.ERROR, "Cannot create " + fileName + ": " + error).show();
            return null;
        }
        return fileName.substring(0, fileName.length() - ".md".length());
    }

    /// Writes the fresh tmux window into the already-existing task file, as
    /// soon as the launcher reports it — before Claude is even started. The
    /// mirror can then attach to the window and the session is watchable while
    /// it comes up, instead of the task staying "no tmux configured" for the
    /// whole launch. FX thread.
    // [impl->dsn~task-create-progress~5]
    private void recordCreatedWindow(MainWindow.TaskFileAccess files, MainWindow window,
            String taskId, String session, String windowId) {
        // From here the mirror shows the real session; stop standing in for it.
        creationSteps.remove(taskId);
        windowedCreations.add(taskId);
        String fileName = taskId + ".md";
        String content = files.read(fileName);
        String error = content == null
                ? "unreadable"
                : files.save(fileName, TaskFileParser.withTmuxSection(
                        content, session, windowId, "created " + LocalDate.now()));
        if (error != null) {
            new Alert(Alert.AlertType.ERROR, "Cannot update " + fileName + ": " + error).show();
            return;
        }
        // The file may be open in the editor lane; refresh it so its stale
        // (pre-tmux) buffer cannot auto-save over the just-written section.
        window.reloadEditorIfShowing(fileName);
    }

    /// Ends a live/PR creation: clears the row's progress bar and reports the
    /// outcome. A failed launch leaves the task file behind, so the description
    /// the user typed is never lost to a dead remote. FX thread.
    // [impl->dsn~task-create-progress~5]
    private void finishCreation(MainWindow window, String taskId, @Nullable String windowId,
            String remote) {
        creationSteps.remove(taskId);
        windowedCreations.remove(taskId);
        window.creationDone(taskId);
        if (windowId == null) {
            creationFinished(window, "Task creation failed.");
            window.noteTaskCreationFailed();
            new Alert(Alert.AlertType.ERROR,
                    "Cannot create a tmux window on " + remote + " (see log).").show();
            return;
        }
        creationFinished(window, "Task created.");
    }

    /// Announces a started async task creation in the status bar. Creations
    /// may overlap (the executor runs each on its own virtual thread), so the
    /// message counts them rather than pretending there is only one.
    // [impl->dsn~task-create-progress~5]
    private void creationStarted(MainWindow window) {
        int running = creationsInFlight.get() + 1;
        creationsInFlight.set(running);
        window.statusBar().message(running == 1
                ? "Creating task …"
                : "Creating " + running + " tasks …");
    }

    /// Reports a finished async task creation (`outcome` = success or failure
    /// line), naming how many are still running so a fast one's outcome does
    /// not read as "all done".
    // [impl->dsn~task-create-progress~5]
    private void creationFinished(MainWindow window, String outcome) {
        int running = creationsInFlight.get() - 1;
        creationsInFlight.set(running);
        window.statusBar().message(running <= 0
                ? outcome
                : outcome + " (" + running + " still creating …)");
    }

    /// The tmux session new windows on `remote` join: that of the remote's
    /// first task with a tmux section, else `0` (a fresh server's default).
    // [impl->dsn~task-from-pr~6]
    private static String usualSession(TaskRepository repository, String remote) {
        return repository.entries().stream()
                .filter(TaskEntry.Loaded.class::isInstance)
                .map(entry -> ((TaskEntry.Loaded) entry).task())
                .filter(task -> remote.equals(task.remote()) && task.tmux() != null)
                .map(task -> task.tmux().session())
                .findFirst()
                .orElse("0");
    }

    /// Creates a live task (tmux window + Claude session) for a task
    /// description in a group whose `CONTEXTSWITCHER.md` configures a remote
    /// (https://github.com/contextswitcher/contextswitcher-private/issues/45): a fresh window in the remote's usual session, started in
    /// the task's workspace (`workdir`, when the group sets one — `mkdir -p`ed
    /// first so a fresh `workspacesRoot` exists), Claude launched with a
    /// context prompt carrying the description between fence lines (a
    /// worktree-bootstrap clause when the group uses `workspacesRoot`) and
    /// asking for a short `@cs_title` back, and a task file carrying
    /// `claude.cwd` and the description under `# Notes` — written before the
    /// remote work (the row shows its creation progress meanwhile, and the
    /// tmux target is written back once the window is up) — the title
    /// starts as the description and is replaced once Claude publishes one
    /// (`dsn~claude-title-sync~3`). The new row **is** selected right away, so
    /// the creation — which runs for a quarter minute — is visible while it
    /// runs.
    // [impl->dsn~task-create-live~8]
    private void createLiveTask(TaskRepository repository, MainWindow.TaskFileAccess files,
            SshCommandRunner ssh, MessageSender sender, String title, String remote,
            @Nullable String workdir, @Nullable String repo, boolean bootstrapWorktree,
            String group, AppSettings settings, ClaudeMode mode, @Nullable String appendix) {
        boolean hints = settings.hints();
        boolean claudeAuto = settings.claudeAuto();
        ExecutorService executor = this.actionExecutor;
        MainWindow window = this.window;
        if (executor == null || window == null) {
            return;
        }
        String session = usualSession(repository, remote);
        creationStarted(window);
        // The description travels verbatim: the prompt goes over ssh
        // stdin into a tmux buffer (no argv quoting limits).
        String prompt = ClaudeWindowLauncher.liveTaskPrompt(title, repo, bootstrapWorktree, group, appendix);
        // The file is written first, without the `tmux:` section the launcher
        // has yet to produce, so the row is in the list from the first second —
        // showing its creation progress — instead of appearing only when the
        // remote is done. A failed remote then leaves a plain task carrying the
        // description rather than nothing at all.
        // [impl->dsn~task-create-progress~5]
        // The description lives under # Notes from the start, so nothing
        // is lost when the title is later replaced by Claude's @cs_title.
        String content = ClaudeWindowLauncher.liveTaskContent(title, remote, workdir)
                + (hints ? "\nCreated on %s.\n".formatted(LocalDate.now()) : "");
        // Any URL inside the description also becomes a browser tab.
        // [impl->dsn~task-url-collect~1]
        String withPrs = TaskFileParser.addUrlsFrom(content, title);
        // Dated like the per-task worktrees; the description slug is
        // only a placeholder until the adopted title renames the file.
        String dated = LocalDate.now() + "-" + title;
        String base = TaskFileParser.newTaskFileName(group.isEmpty() ? dated : group + "/" + dated);
        String taskId = saveNewTask(files, window, base, withPrs);
        if (taskId == null) {
            return;
        }
        // The launch prompt is recorded as the task's last sent
        // message: the paste into the fresh session can fail silently
        // (Claude's TUI not up yet), and the queue pane's "Last sent"
        // box is then the one place the text — attachment markers
        // included — survives to re-send from.
        // [impl->dsn~last-sent-message~5]
        QueueFile.saveSent(settings.tasksDir().resolve(".queues"), taskId, prompt);
        // The new task takes the selection, so its launch is visible while it
        // runs — the row's progress and the same step in the terminal lane,
        // then the mirror the moment the window exists.
        // Unconditionally, not through `selectCreatedTask`: this creation is
        // the user's own just-submitted dialog, not the PR flow's late
        // arrival, so the queue-typing guard has nothing to protect — and
        // whenever the closing dialog handed the focus back to a queue box
        // (where it had been before), that guard silently dropped the
        // selection and the new row stayed unselected (field report
        // 2026-09-10).
        // [impl->dsn~task-create-live~8]
        window.selectTask(taskId);
        creationStep(window, taskId, workdir == null
                ? "Creating the tmux window …" : "Preparing the workspace …");
        executor.execute(() -> {
            // Ensure the workspace root exists so `tmux new-window -c` succeeds
            // (Claude then creates the per-task worktree under it).
            if (workdir != null) {
                ssh.run(remote, List.of("mkdir", "-p", "'" + workdir + "'"));
            }
            String windowId = new ClaudeWindowLauncher(ssh, sender, claudeAuto)
                    .launch(remote, session, title, prompt, workdir, mode,
                            step -> Platform.runLater(() -> creationStep(window, taskId, step)),
                            fresh -> Platform.runLater(() ->
                                    recordCreatedWindow(files, window, taskId, session, fresh)));
            Platform.runLater(() -> {
                finishCreation(window, taskId, windowId, remote);
                if (windowId != null) {
                    // Record the session id Claude publishes on startup without
                    // the user pressing "Sync tmux windows…" (dsn~tmux-sync~7).
                    schedulePostCreateSync(repository, settings, files, taskId);
                }
            });
        });
    }

    /// The context prompt pasted into a forked task's fresh Claude session
    /// (`dsn~task-fork~1`), started with `claude --resume <sessionId>
    /// --fork-session` (`ClaudeWindowLauncher`) so it continues the source
    /// task's conversation without touching the original session. Tells
    /// Claude it is a fork of `sourceTitle`'s conversation and to first check
    /// `workspace` (the source's `@cs_workspace`, which may have moved on
    /// since the remembered conversation) — or, when none was recorded, the
    /// working directory (`cwd`) the conversation used — before doing
    /// `instruction`, which travels verbatim between `-----` fence lines like
    /// a live task's description. `repo`/`bootstrapWorktree`/`group` add the
    /// same worktree-bootstrap clause [ClaudeWindowLauncher#liveTaskPrompt] does.
    // [impl->dsn~task-fork~1]
    static String forkPrompt(String sourceTitle, String instruction, @Nullable String workspace, String cwd,
            @Nullable String repo, boolean bootstrapWorktree, String group) {
        String checkTarget = workspace == null || workspace.isBlank() ? cwd : workspace;
        return "You are a fork of the conversation of task \"" + sourceTitle.strip() + "\". " + ClaudeWindowLauncher.TITLE_CLAUSE
                + " Then check the workspace " + checkTarget.strip()
                + " — its state may have moved on since the conversation you remember"
                + " — and do what is between the ----- lines below."
                + ClaudeWindowLauncher.worktreeClause(bootstrapWorktree, repo, group)
                + "\n-----\n" + instruction.strip() + "\n-----";
    }

    /// Forks a task's Claude conversation into a new task (`dsn~task-fork~1`):
    /// a fresh tmux window in `source`'s remote and usual session, started in
    /// `source.claude().cwd()` — Claude stores sessions per directory, so
    /// `--resume <id>` must run where the source session ran — with
    /// `claude --resume <sourceSessionId> --fork-session` in place of a plain
    /// `claude` ([ClaudeWindowLauncher]). Same category as `source` (its id's
    /// group folder); `repo`/`bootstrapWorktree` come from the group's
    /// `CONTEXTSWITCHER.md`, like [#startRemoteWindow]. The task file is
    /// written first, like [#createLiveTask], but never selected — the fork
    /// runs in the background while the user stays on the task they forked
    /// from — and its progress and outcome are reported through the status
    /// bar rather than the row-selected terminal lane.
    // [impl->dsn~task-fork~1]
    private void forkTask(TaskRepository repository, MainWindow.TaskFileAccess files,
            SshCommandRunner ssh, MessageSender sender, AppSettings settings, Task source,
            String instruction, ClaudeMode mode) {
        Task.ClaudeConfig claude = source.claude();
        String remote = source.remote();
        if (claude == null || remote == null) {
            return;
        }
        ExecutorService executor = this.actionExecutor;
        MainWindow window = this.window;
        if (executor == null || window == null) {
            return;
        }
        String group = taskGroup(source);
        String cfgName = group.isEmpty() ? null : group + "/" + TaskRepository.GROUP_CONFIG_FILE_NAME;
        GroupConfig groupConfig = cfgName != null && files.exists(cfgName)
                ? new TaskFileParser().parseGroupConfig(files.load(cfgName))
                : GroupConfig.EMPTY;
        String repo = groupConfig.repo();
        boolean bootstrapWorktree = groupConfig.bootstrapWorktree();
        String cwd = claude.cwd();
        String session = usualSession(repository, remote);
        boolean claudeAuto = settings.claudeAuto();
        String prompt = forkPrompt(source.title(), instruction, claude.workspace(), cwd,
                repo, bootstrapWorktree, group);
        String content = """
                ---
                title: %s
                status: active
                remote: %s
                claude:
                  cwd: %s
                ---

                # Notes

                %s

                Forked from %s (`%s`).
                """.formatted(
                TaskFileParser.yamlScalar(instruction), TaskFileParser.yamlScalar(remote),
                TaskFileParser.yamlScalar(cwd), instruction.strip(), source.title(), source.id())
                + (settings.hints() ? "\nCreated on %s.\n".formatted(LocalDate.now()) : "");
        String withPrs = TaskFileParser.addUrlsFrom(content, instruction);
        String dated = LocalDate.now() + "-" + instruction;
        String base = TaskFileParser.newTaskFileName(group.isEmpty() ? dated : group + "/" + dated);
        String taskId = saveNewTask(files, window, base, withPrs);
        if (taskId == null) {
            return;
        }
        QueueFile.saveSent(settings.tasksDir().resolve(".queues"), taskId, prompt);
        // The status line names the fork, not the whole (possibly
        // multi-sentence) instruction — the first line is enough to tell
        // creations apart, like a task row's title before it is adopted.
        String statusName = instruction.strip().lines().findFirst().orElse(instruction.strip());
        window.statusBar().message("Forking " + statusName + " …");
        window.creationProgress(taskId, "Creating the tmux window …");
        executor.execute(() -> {
            String windowId = new ClaudeWindowLauncher(ssh, sender, claudeAuto)
                    .launch(remote, session, instruction, prompt, cwd, mode,
                            step -> Platform.runLater(() -> window.creationProgress(taskId, step)),
                            fresh -> Platform.runLater(() ->
                                    recordCreatedWindow(files, window, taskId, session, fresh)),
                            claude.sessionId());
            Platform.runLater(() -> {
                window.creationDone(taskId);
                if (windowId == null) {
                    window.statusBar().message("Forking " + statusName + " failed.");
                    new Alert(Alert.AlertType.ERROR,
                            "Cannot create a tmux window on " + remote + " (see log).").show();
                    return;
                }
                window.statusBar().message("Forked " + statusName + ".");
                schedulePostCreateSync(repository, settings, files, taskId);
            });
        });
    }

    /// Creates a tmux window on play for a task that carries a `remote:` but no
    /// `tmux:` section yet — the choice popup's outcome (issue: remote-only
    /// play). `withClaude` starts a Claude session (context prompt, like a live
    /// task) rather than a plain shell; both start in the task's workspace on
    /// the remote. The working directory resolves from the task's own
    /// `workspacesRoot:`/`workdir:` frontmatter, else the group's
    /// `CONTEXTSWITCHER.md` defaults (`mkdir -p`ed so a fresh root exists). The
    /// `mode` is the model and effort the Claude session starts with, asked for
    /// by the choice dialog (`dsn~claude-mode-select~3`); the plain shell
    /// ignores it. The
    /// new window id is written back as a real `tmux:` section (and, for Claude,
    /// a `claude:` cwd section) so the next play focuses it; then the task is
    /// selected and switched to, focusing the window and attaching the mirror.
    // [impl->dsn~remote-window-choice~6]
    private void startRemoteWindow(SwitchOrchestrator orchestrator, TaskRepository repository,
            MainWindow.TaskFileAccess files, SshCommandRunner ssh, MessageSender sender,
            AppSettings settings, Task task, boolean withClaude, ClaudeMode mode) {
        // The category's host when the task names none (`dsn~remote-window-choice~6`).
        String remote = taskRemote(files, task);
        ExecutorService executor = this.actionExecutor;
        MainWindow window = this.window;
        if (remote == null || executor == null || window == null) {
            return;
        }
        // Only worth writing back when it was not the task's to begin with.
        String adoptedRemote = task.remote() == null ? remote : null;
        String fileName = task.id() + ".md";
        String group = taskGroup(task);
        String cfgName = group.isEmpty() ? null : group + "/" + TaskRepository.GROUP_CONFIG_FILE_NAME;
        GroupConfig groupConfig = cfgName != null && files.exists(cfgName)
                ? new TaskFileParser().parseGroupConfig(files.load(cfgName))
                : GroupConfig.EMPTY;
        // Working directory: the task's own workspacesRoot/workdir wins over the
        // group defaults. A fixed workdir keeps the plain prompt; a workspacesRoot
        // means Claude bootstraps its own worktree under it.
        String content = files.read(fileName);
        String taskWorkdir = content == null ? null : new TaskFileParser().frontmatterString(content, "workdir");
        String taskWsRoot = content == null ? null
                : new TaskFileParser().frontmatterString(content, "workspacesRoot");
        String cwd;
        boolean bootstrapWorktree;
        if (taskWorkdir != null) {
            cwd = taskWorkdir;
            bootstrapWorktree = false;
        } else if (taskWsRoot != null) {
            cwd = taskWsRoot;
            bootstrapWorktree = true;
        } else {
            cwd = groupConfig.resolveWorkdir();
            bootstrapWorktree = groupConfig.bootstrapWorktree();
        }
        String repo = groupConfig.repo();
        String session = usualSession(repository, remote);
        boolean claudeAuto = settings.claudeAuto();
        // The progress bar the Add-task flow puts on the row
        // (`dsn~task-create-progress~5`). This path does the very work that
        // one does — an ssh round-trip per step, ten seconds and more — and
        // showed nothing for it but two buttons going grey. Field report
        // 2026-09-12: "I clicked 'Claude' on this task and it just stalled. I
        // was expecting the 'usual' progress bar" — on a creation that had in
        // fact succeeded while the screenshot was being taken.
        // [impl->dsn~remote-window-choice~6]
        // Counted in, because the shared `finishCreation` below counts out:
        // without this the in-flight counter went negative and the *next*
        // Add-task creation announced "Creating 0 tasks …".
        // [impl->dsn~task-create-progress~5]
        creationStarted(window);
        creationStep(window, task.id(), "Creating the tmux window …");
        executor.execute(() -> {
            if (cwd != null) {
                ssh.run(remote, List.of("mkdir", "-p", "'" + cwd + "'"));
            }
            String windowId;
            if (withClaude) {
                String description = task.notes().isBlank() ? task.title() : task.notes().strip();
                String prompt = ClaudeWindowLauncher.liveTaskPrompt(description, repo, bootstrapWorktree && cwd != null,
                        group, null);
                windowId = new ClaudeWindowLauncher(ssh, sender, claudeAuto)
                        .launch(remote, session, task.title(), prompt, cwd, mode,
                                step -> Platform.runLater(() -> creationStep(window, task.id(), step)),
                                id -> { });
            } else {
                windowId = new TmuxResurrect(ssh, claudeAuto)
                        .createWindow(remote, session, cwd, task.title());
            }
            String created = windowId;
            Platform.runLater(() -> {
                // Clears the bar and reports the outcome exactly as the
                // Add-task flow does, the failure alert included.
                // [impl->dsn~remote-window-choice~6]
                finishCreation(window, task.id(), created, remote);
                if (created == null) {
                    // Re-draws the placeholder, so its buttons — dead for the
                    // round-trip — can be pressed again after a failed attempt.
                    // [impl->dsn~remote-window-choice~6]
                    window.refreshPreview(task.id());
                    return;
                }
                writeRemoteWindowBack(orchestrator, repository, settings, files,
                        task.id(), session, created, withClaude ? cwd : null, adoptedRemote);
            });
        });
    }

    /// Types `claude --resume <id> || claude` (a plain `claude` without a
    /// recorded session id) into the task's existing tmux window — the repair
    /// for a window whose Claude never came up, offered by the `Claude` button
    /// next to `IDEA`. Runs off the FX thread; `onSettled` re-enables the
    /// button on the FX thread either way.
    // [impl->dsn~start-claude-button~2]
    private void startClaude(SshCommandRunner ssh, boolean claudeAuto, Task task, Runnable onSettled) {
        ExecutorService executor = this.actionExecutor;
        String remote = task.remote();
        Task.TmuxConfig tmux = task.tmux();
        if (executor == null || remote == null || tmux == null || tmux.window() == null) {
            onSettled.run();
            return;
        }
        String windowId = tmux.window();
        String sessionId = task.claude() == null ? null : task.claude().sessionId();
        MainWindow currentWindow = this.window;
        executor.execute(() -> {
            boolean sent = new TmuxResurrect(ssh, claudeAuto)
                    .startClaude(remote, windowId, sessionId);
            Platform.runLater(() -> {
                if (currentWindow != null) {
                    currentWindow.statusBar().message(sent
                            ? "Claude started in " + windowId + " on " + remote + "."
                            : "Cannot start Claude in " + windowId + " on " + remote + ".");
                }
                onSettled.run();
            });
        });
    }

    /// Opens a throwaway tmux window on `remote` — the category header's
    /// terminal icon. Deliberately minimal: a plain shell in the remote's usual
    /// session (`cwd` when the category configures one, `mkdir -p`ed first),
    /// named `scratch`, then focused like any task window. Nothing is written
    /// locally — no task file, no selection, no mirror; a window that turns out
    /// to be worth keeping is adopted by "Sync tmux windows…" afterwards.
    /// `onSettled` runs on the FX thread once the ssh round-trip succeeds or
    /// fails, so the calling button can stop showing its busy state — the
    /// status bar gets the same "Opening …"/outcome shape as `focusPr`.
    // [impl->dsn~category-scratch-window~2]
    private void startScratchWindow(TaskRepository repository, SshCommandRunner ssh,
            TerminalPane terminal, String remote, @Nullable String cwd, Runnable onSettled) {
        ExecutorService executor = this.actionExecutor;
        if (executor == null) {
            onSettled.run();
            return;
        }
        MainWindow currentWindow = this.window;
        if (currentWindow != null) {
            currentWindow.statusBar().message("Opening terminal on " + remote + " …");
        }
        String session = usualSession(repository, remote);
        executor.execute(() -> {
            if (cwd != null) {
                ssh.run(remote, List.of("mkdir", "-p", "'" + cwd + "'"));
            }
            String windowId = new TmuxResurrect(ssh).createWindow(remote, session, cwd, "scratch");
            if (windowId == null) {
                Platform.runLater(() -> {
                    if (currentWindow != null) {
                        currentWindow.statusBar().message("Cannot open a terminal on " + remote + ".");
                    }
                    new Alert(Alert.AlertType.ERROR,
                            "Cannot open a window on " + remote + " (see log).").show();
                    onSettled.run();
                });
                return;
            }
            Task.TmuxConfig tmux = new Task.TmuxConfig(session, windowId);
            ssh.run(remote, TmuxFocusAction.remoteCommand(tmux));
            // Mirror it in the app's terminal, keyboard focus included: the
            // remote focus above only reaches plain tmux clients (mirror
            // clients are deliberately skipped), so with the app as the only
            // client the window would otherwise be created out of sight — the
            // whole point is to type in it right away.
            Platform.runLater(() -> {
                terminal.show(remote, tmux);
                terminal.focusTerminal();
                if (currentWindow != null) {
                    currentWindow.statusBar().message("Terminal opened on " + remote + ".");
                }
                onSettled.run();
            });
        });
    }

    /// The Add-task dialog's `Add local Claude` button: a local Claude session
    /// in the local category's directory — the app's own ConPTY on Windows,
    /// a window in the local tmux server elsewhere — with the typed
    /// description as its initial prompt. The task file is already written by
    /// `MainWindow` (a plain file-only task); the tmux window the session runs
    /// in is written back into it, so the pane can mirror the session like a
    /// remote one (`dsn~terminal-local-mirror~2`). There is no `@cs_title` to
    /// sync — a local session is not polled.
    /// Off the FX thread with the status-bar "Opening …"/outcome shape the
    /// other single-shot buttons use.
    // [impl->dsn~task-create-local~4]
    private void startLocalClaude(MainWindow.TaskFileAccess files, String taskId, String workdir,
            String description, ClaudeMode mode) {
        ExecutorService executor = this.actionExecutor;
        if (executor == null) {
            return;
        }
        // Independent of the launch: the title is the task file's, and a
        // failed start leaves a task that still deserves a readable row.
        // [impl->dsn~task-create-local-title~1]
        InitialTitleAdopter titles = this.initialTitles;
        if (titles != null) {
            titles.adopt(taskId);
        }
        MainWindow currentWindow = this.window;
        if (currentWindow != null) {
            currentWindow.statusBar().message("Starting Claude in " + workdir + " …");
        }
        TerminalPane terminal = this.terminalPane;
        if (LocalClaudeLauncher.onWindows() && terminal != null) {
            // Windows has no tmux to run the session in, so the **app** hosts
            // it in the terminal pane's own ConPTY — a Windows Terminal tab
            // could not be typed into from here (`dsn~terminal-owned-session~3`).
            terminal.startOwned(taskId,
                    LocalClaudeLauncher.ownedCommand(description, mode.model(), null), workdir);
            if (currentWindow != null) {
                currentWindow.statusBar().message("Claude started in " + workdir + ".");
            }
            // No tmux window to record — only the cwd, which is what finds the
            // transcript again when the app is restarted.
            writeLocalWindowBack(files, taskId, null, workdir);
            return;
        }
        executor.execute(() -> {
            LocalClaudeLauncher.Launch launch = new LocalClaudeLauncher(new LocalCommandRunner())
                    .launch(workdir, description, mode.model());
            Platform.runLater(() -> {
                if (currentWindow != null) {
                    currentWindow.statusBar().message(launch.error() == null
                            ? "Claude started in " + workdir + " (tmux attach -t 0)."
                            : "Cannot start Claude in " + workdir + ": " + launch.error());
                }
                if (launch.window() != null) {
                    writeLocalWindowBack(files, taskId, launch.window(), workdir);
                }
            });
        });
    }

    /// Records the local tmux window a just-started local Claude session runs
    /// in — `tmux:` (session `0` plus the window id) and `claude.cwd`, the same
    /// sections the remote flow writes, minus a `remote:`: that absence is
    /// what marks the session as local, and the mirror addresses it as
    /// `TmuxHost.LOCAL` instead of over ssh. FX thread.
    // [impl->dsn~terminal-local-mirror~2]
    private void writeLocalWindowBack(MainWindow.TaskFileAccess files, String taskId,
            @Nullable String windowId, String workdir) {
        MainWindow window = this.window;
        if (window == null) {
            return;
        }
        String fileName = taskId + ".md";
        String content = files.read(fileName);
        if (content == null) {
            Logger.warn("Local window created but {} is unreadable; not updated", fileName);
            return;
        }
        // No window id on Windows: the session is the app's own ConPTY, not a
        // tmux window, and `claude.cwd` alone is what a later resume needs.
        String updated = TaskFileParser.withClaudeSection(
                windowId == null ? content
                        : TaskFileParser.withTmuxSection(content, LocalClaudeLauncher.SESSION,
                                windowId, "created " + LocalDate.now()),
                workdir);
        String error = files.save(fileName, updated);
        if (error != null) {
            Logger.warn("Cannot record the local tmux window in {}: {}", fileName, error);
            return;
        }
        // The file is open in the editor lane (the plain flow opens it), so a
        // stale clean buffer would auto-save over the just-written section.
        window.reloadEditorIfShowing(fileName);
        window.selectTask(taskId);
    }

    /// Opens the task's RefactoringMiner AST-diff web view (https://github.com/contextswitcher/contextswitcher-private/issues/50): resolves
    /// config and workspace fresh, starts the tunnelled view process, and —
    /// once `/list` answers — opens the browser on it. Status-bar messaging
    /// and the settled callback follow the async single-shot button
    /// convention. FX thread (button click).
    // [impl->dsn~refactoring-web-view~1]
    private void openRefactoringView(MainWindow.TaskFileAccess files, AppSettings startup,
            RefactoringMinerView view, Task task, Runnable onSettled) {
        MainWindow window = this.window;
        if (window == null) {
            onSettled.run();
            return;
        }
        // Settings read fresh, so pointing `refactoringMinerHome` at a new
        // unzip location needs no restart (the *poller* does, like `remotes`).
        AppSettings settings = startup;
        try {
            settings = AppSettings.loadOrCreate(configDir());
        } catch (IOException e) {
            Logger.warn("Cannot re-read settings, using startup values: {}", e.getMessage());
        }
        String rmHome = settings.refactoringMinerHome();
        if (rmHome == null) {
            window.statusBar().message(
                    "Set refactoringMinerHome in the settings to enable the refactoring view.");
            onSettled.run();
            return;
        }
        Task.ClaudeConfig claude = task.claude();
        String remote = task.remote();
        if (claude == null || remote == null) {
            window.statusBar().message("No remote workspace known for this task.");
            onSettled.run();
            return;
        }
        String worktree = claude.workspace() != null ? claude.workspace() : claude.cwd();
        String baseBranch = groupBaseBranch(files, taskGroup(task));
        int port = settings.refactoringMinerPort();
        String listUrl = RefactoringMinerCommands.listUrl(port);
        window.statusBar().message("Opening refactoring view for “%s” …"
                .formatted(task.title()));
        view.open(RefactoringMinerCommands.viewCommand(remote, rmHome, port, worktree, baseBranch),
                listUrl,
                failure -> Platform.runLater(() -> {
                    onSettled.run();
                    if (failure == null) {
                        window.statusBar().message("Refactoring view opened.");
                        openUrl(listUrl);
                    } else {
                        window.statusBar().message("Cannot open the refactoring view: " + failure);
                    }
                }));
    }

    /// The settings dialog's **Set up** button: installs the RefactoringMiner
    /// release on every configured remote (idempotent — an existing install is
    /// left alone) and reports back the directory it landed in, which the form
    /// puts into `refactoringMinerHome`. One shared setting names the directory
    /// on all remotes, so remotes resolving it to different absolute paths is
    /// an error rather than a silently half-working install. FX thread (button
    /// click); the ssh work runs on the action executor.
    // [impl->dsn~refactoring-miner-setup~1]
    private void setupRefactoringMiner(List<String> remotes,
            java.util.function.Consumer<@Nullable String> onDone) {
        MainWindow window = this.window;
        ExecutorService executor = this.actionExecutor;
        if (window == null || executor == null) {
            onDone.accept(null);
            return;
        }
        window.statusBar().message("Installing RefactoringMiner on %s …"
                .formatted(String.join(", ", remotes)));
        executor.execute(() -> {
            SshCommandRunner ssh = new ProcessSshRunner(REFACTORING_SSH_TIMEOUT);
            String home = null;
            String failure = null;
            for (String remote : remotes) {
                SshCommandRunner.SshResult result =
                        ssh.run(remote, RefactoringMinerCommands.setupCommand());
                // The installed directory is the script's last stdout line.
                String installed = result.stdout().strip().lines()
                        .reduce((first, last) -> last).orElse("");
                if (!result.ok() || installed.isEmpty()) {
                    String stderr = result.stderr().strip();
                    failure = remote + ": "
                            + (stderr.isEmpty() ? "exit " + result.exitCode() : stderr);
                    break;
                }
                if (home == null) {
                    home = installed;
                } else if (!home.equals(installed)) {
                    failure = "installed at %s on %s but at %s — one setting cannot name both"
                            .formatted(home, remote, installed);
                    break;
                }
            }
            String installedHome = failure == null ? home : null;
            String message = failure == null
                    ? "RefactoringMiner ready at %s — Save to keep it.".formatted(installedHome)
                    : "Cannot install RefactoringMiner: " + failure;
            Platform.runLater(() -> {
                window.statusBar().message(message);
                onDone.accept(installedHome);
            });
        });
    }

    /// The Claude task running in `window` on `remote` (a pseudo-host for a
    /// local session, [TmuxHost#of]), or null.
    private static @Nullable Task windowTask(TaskRepository repository, String remote, String window) {
        for (TaskEntry entry : repository.entries()) {
            if (entry instanceof TaskEntry.Loaded loaded
                    && remote.equals(TmuxHost.of(loaded.task()))
                    && loaded.task().tmux() != null
                    && window.equals(loaded.task().tmux().window())
                    && loaded.task().claude() != null) {
                return loaded.task();
            }
        }
        return null;
    }

    /// The terminal pane's "Show diff" button: resolves the mirrored window
    /// back to its task (the pane knows windows, not tasks), takes the task's
    /// workspace (`claude.workspace`, falling back to `claude.cwd` — the same
    /// resolution the IntelliJ action uses), and opens a throwaway `diff`
    /// window there ([TaskDiffWindow]) in the task's own session, mirrored in
    /// the app's terminal like the scratch window — with keyboard focus, so
    /// the pager's keys work right away. FX thread (button click).
    // [impl->dsn~terminal-diff-window~2]
    // [impl->dsn~diff-after-worktree-removal~1]
    private void showDiffWindow(TaskRepository repository, MainWindow.TaskFileAccess files,
            SshCommandRunner ssh, TerminalPane terminal, String remote, Task.TmuxConfig mirrored) {
        ExecutorService executor = this.actionExecutor;
        String window = mirrored.window();
        if (executor == null || window == null) {
            return;
        }
        String cwd = null;
        String commit = null;
        String checkout = null;
        Task task = windowTask(repository, remote, window);
        if (task != null) {
            Task.ClaudeConfig claude = task.claude();
            cwd = claude.workspace() != null ? claude.workspace() : claude.cwd();
            commit = claude.commit();
            checkout = groupMainCheckout(files, taskGroup(task));
        }
        if (cwd == null) {
            new Alert(Alert.AlertType.INFORMATION,
                    "No workspace recorded for this window — the task needs a claude: "
                            + "section with a cwd or workspace to diff in.").show();
            return;
        }
        String workspace = cwd;
        String recordedCommit = commit;
        String mainCheckout = checkout;
        executor.execute(() -> {
            String windowId = TaskDiffWindow.open(ssh, remote, mirrored.session(), workspace,
                    mainCheckout, recordedCommit);
            if (windowId == null) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR,
                        "Cannot open a diff window on " + remote + " (see log).").show());
                return;
            }
            Task.TmuxConfig tmux = new Task.TmuxConfig(mirrored.session(), windowId);
            ssh.run(remote, TmuxFocusAction.remoteCommand(tmux));
            Platform.runLater(() -> {
                terminal.show(remote, tmux);
                terminal.focusTerminal();
            });
        });
    }

    /// "Show diff" for an app-owned session (`dsn~terminal-owned-session~3`):
    /// git runs on this machine in the task's workspace — the recorded
    /// `claude.workspace`, else the directory the session runs in — and the
    /// colored output opens in a read-only terminal window.
    // [impl->dsn~terminal-owned-session~3]
    private void showOwnedDiff(TaskRepository repository, String taskId) {
        ExecutorService executor = this.actionExecutor;
        if (executor == null) {
            return;
        }
        String cwd = null;
        String title = taskId;
        for (TaskEntry entry : repository.entries()) {
            if (entry instanceof TaskEntry.Loaded loaded && loaded.task().id().equals(taskId)) {
                Task task = loaded.task();
                title = task.title();
                cwd = task.claude() != null && task.claude().workspace() != null
                        ? task.claude().workspace()
                        : ownedCwd(task);
                break;
            }
        }
        if (cwd == null) {
            new Alert(Alert.AlertType.INFORMATION,
                    "No working directory recorded for this task — nothing to diff.").show();
            return;
        }
        String workspace = cwd;
        String windowTitle = "Diff — " + title;
        executor.execute(() -> {
            String diff = LocalDiff.render(workspace);
            Platform.runLater(() -> AnsiTextWindow.show(windowTitle, diff));
        });
    }

    /// Writes the freshly created window's `tmux:` (and, for Claude, `claude:`
    /// cwd) section back into the task file, then selects and switches to the
    /// task so the window is focused and the mirror attaches. FX thread.
    // [impl->dsn~remote-window-choice~6]
    private void writeRemoteWindowBack(SwitchOrchestrator orchestrator, TaskRepository repository,
            AppSettings settings, MainWindow.TaskFileAccess files, String taskId, String session,
            String windowId, @Nullable String claudeCwd, @Nullable String adoptedRemote) {
        MainWindow window = this.window;
        if (window == null) {
            return;
        }
        String fileName = taskId + ".md";
        String content = files.read(fileName);
        if (content == null) {
            Logger.warn("Window created but {} is unreadable; not updated", fileName);
            return;
        }
        String note = "created " + LocalDate.now();
        String tmuxSection = TaskFileParser.withTmuxWindow(content, windowId, note);
        String updated = tmuxSection.equals(content)
                ? TaskFileParser.withTmuxSection(content, session, windowId, note)
                : tmuxSection;
        if (claudeCwd != null) {
            updated = TaskFileParser.withClaudeSection(updated, claudeCwd);
        }
        // A host borrowed from the category becomes the task's own: the window
        // now really is there, and every later action (mirror, focus, queue)
        // addresses a task by its `remote:`.
        // [impl->dsn~remote-window-choice~6]
        if (adoptedRemote != null) {
            updated = TaskFileParser.withScalar(updated, "remote", adoptedRemote);
        }
        String error = files.save(fileName, updated);
        if (error != null) {
            new Alert(Alert.AlertType.ERROR, "Cannot update " + fileName + ": " + error).show();
            return;
        }
        // The file may be open in the editor lane; refresh it so its stale
        // (pre-tmux) clean buffer cannot auto-save over the just-written
        // section — which would leave the new window unmatched and a later
        // "Sync tmux windows" would import it as a duplicate task.
        window.reloadEditorIfShowing(fileName);
        window.selectTask(taskId);
        try {
            switchTo(orchestrator, files, new TaskFileParser().parse(taskId, updated));
        } catch (com.contextswitcher.tasks.TaskParseException e) {
            Logger.warn("Cannot re-parse {} after window creation: {}", fileName, e.getMessage());
        }
        if (claudeCwd != null) {
            schedulePostCreateSync(repository, settings, files, taskId);
        }
    }

    /// Routes the selected task to the terminal mirror: window-less
    /// placeholders for unconfigured or suspended tasks — a suspended task's
    /// window was ended by the suspend, so the mirror must not silently
    /// drift to another window of the session.
    // [impl->dsn~terminal-pane~14]
    /// Focuses (or opens) `url` in Firefox via the extension and reports the
    /// outcome in the status bar — the PR-icon click's back half. Runs on the
    /// action executor (`focusUrl` blocks on the extension round-trip); the
    /// result was previously discarded, so a not-connected extension, a 5 s
    /// timeout, or an opened-but-unraised background tab all looked like
    /// nothing happened. Now the detail (`focused existing tab` / `opened new
    /// tab` / `Browser extension not connected` / timeout) surfaces instead.
    // [impl->dsn~pr-state-indicator~3]
    /// `desktop` is the virtual desktop of the task's category (null when it has
    /// none): the whole click belongs on that desktop, so it goes through
    /// [#openOnDesktop] — which switches there *first* and then asks the
    /// extension, naming the window to land in. Raising an existing tab where
    /// it happens to live pulled Windows to whatever desktop that Firefox
    /// window sat on and left the user off their category (field report
    /// 2026-09-10); the tab moves to them instead.
    // [impl->dsn~pr-open-on-category-desktop~3]
    private void reportFocusUrl(ExtensionServer extensionServer, String url, @Nullable String desktop) {
        ExtensionProtocol.Result result = desktop == null
                ? extensionServer.focusUrl(url)
                : openOnDesktop(extensionServer, desktop, url);
        // The extension activated the tab but cannot raise Firefox past the
        // foreground-stealing lock while this app is in front; bring the exact
        // window forward ourselves, matched by the focused tab's title so the
        // right one rises among many (Windows; a silent no-op elsewhere). No
        // title means a freshly opened tab whose window we cannot title-match.
        String windowTitle = result.title();
        if (result.ok() && windowTitle != null && !windowTitle.isBlank()) {
            new BrowserWindowFocus(new LocalCommandRunner(), drivenBrowser()).focus(windowTitle);
        }
        MainWindow currentWindow = this.window;
        if (currentWindow == null) {
            return;
        }
        String name = drivenBrowser().displayName();
        String text = result.ok()
                ? name + ": " + result.detail() + " — " + url
                : "Cannot show in " + name + ": " + result.detail();
        Platform.runLater(() -> currentWindow.statusBar().message(text));
    }

    /// Shows `url` on `desktop`: switches to it, raises a Firefox window living
    /// there and has the extension put the tab **into that window** (named by
    /// its caption — Firefox's own "most recent window" may still be the one
    /// on the desktop just left), so the tab lands in front of the user. That
    /// holds for a tab that already exists too: the extension moves it here
    /// rather than raising it where it is.
    /// A desktop with no browser window at all gets a new one carrying the
    /// URL, which is both the window and the tab. Windows-only in effect:
    /// without `powershell` every step is a no-op and the extension focuses or
    /// opens the tab wherever it would have anyway.
    // [impl->dsn~pr-open-on-category-desktop~3]
    private ExtensionProtocol.Result openOnDesktop(ExtensionServer extensionServer, String desktop, String url) {
        LocalCommandRunner runner = new LocalCommandRunner();
        new WindowsVirtualDesktopFocus(runner, fallbackDesktop).focus(desktop);
        BrowserWindowFocus windows = new BrowserWindowFocus(runner, drivenBrowser());
        String caption = windows.focusOnCurrentDesktop();
        if (caption != null) {
            return extensionServer.focusUrl(url, true, caption);
        }
        // No browser window here to put the tab into. Ask focus-only first: a
        // URL that is already open is focused where it lives — the one case
        // left in which the click still follows the tab to another desktop,
        // and better than the second copy a blind launch would make. Only a
        // URL with no tab anywhere gets a fresh window on this desktop, which
        // is both the window and the tab.
        ExtensionProtocol.Result probe = extensionServer.focusUrl(url, false);
        if (!ExtensionProtocol.NO_TAB.equals(probe.detail())) {
            return probe;
        }
        if (windows.launch(url)) {
            return new ExtensionProtocol.Result("", true,
                    "opened a window on desktop \"%s\"".formatted(desktop));
        }
        return extensionServer.focusUrl(url, true);
    }

    /// The browser whose windows are raised, launched, and captured: the one
    /// whose extension is connected, since the extension that just acted on a
    /// tab *is* the browser holding it, and only then the configured
    /// `browser:`.
    /// Preferring the setting would send the raise after the wrong process
    /// whenever the two disagree — the common case being a Chrome extension
    /// connected while `browser:` still says `firefox`, where the raise found
    /// a leftover Firefox window instead of the Chrome one holding the tab.
    /// The setting still decides when nothing is connected, which is the case
    /// that has to *launch* a browser.
    // [impl->dsn~drive-the-connected-browser~1]
    private Browser drivenBrowser() {
        ExtensionServer server = this.extensionServer;
        Browser connected = server == null ? null : server.connectedBrowser();
        return connected != null ? connected : browser;
    }

    /// How many of `urls` have a browser tab open right now, or -1 when the
    /// browser cannot be asked (no extension connected, or the request timed
    /// out) — the delete dialog says which of the two it is rather than
    /// counting an unanswerable question as zero.
    /// One `list-tabs` answers for every URL at once, and the match is the
    /// **exact** one `close-url` uses (`dsn~browser-close-action~2`): the tick
    /// promises to close what it counted, so counting a sub-page tab that the
    /// close would then leave behind would be a lie.
    /// Runs off the FX thread — it is a round-trip with a 5 s timeout.
    // [impl->dsn~claude-session-kill~6]
    private static int countOpenTabs(ExtensionServer extensionServer, List<String> urls) {
        ExtensionProtocol.Result listed = extensionServer.listTabs();
        List<ExtensionProtocol.TabWindow> windows = listed.windows();
        if (!listed.ok() || windows == null) {
            return -1;
        }
        Set<String> open = windows.stream()
                .flatMap(window -> window.urls().stream())
                .collect(java.util.stream.Collectors.toSet());
        return (int) urls.stream().filter(open::contains).count();
    }

    /// Switches Windows to the named virtual desktop and reports the outcome in
    /// the status bar — the category header's desktop-focus button, run on the
    /// action executor (off the FX thread) like the other local-focus actions.
    // [impl->dsn~category-desktop-focus~4]
    private void reportFocusDesktop(String desktop) {
        WindowsVirtualDesktopFocus.FocusResult result =
                new WindowsVirtualDesktopFocus(new LocalCommandRunner(), fallbackDesktop)
                        .focus(desktop);
        MainWindow currentWindow = this.window;
        if (currentWindow == null) {
            return;
        }
        String text = result.ok() ? "Desktop: " + result.detail()
                : "Cannot focus desktop: " + result.detail();
        Platform.runLater(() -> currentWindow.statusBar().message(text));
    }

    /// Opens the category's local folders and reports the outcome in the status
    /// bar — the header's folder button, run on the action executor (off the FX
    /// thread) like the desktop focus next to it. Reuses the switch action's own
    /// body, so the header button and a task switch open folders identically.
    /// `onSettled` re-enables the button on the FX thread, whatever happened.
    // [impl->dsn~category-folders-button~1]
    private void reportOpenFolders(ExplorerFolderAction action, List<String> folders,
            Runnable onSettled) {
        MainWindow currentWindow = this.window;
        if (currentWindow != null) {
            Platform.runLater(() -> currentWindow.statusBar()
                    .message("Opening %d folder(s) …".formatted(folders.size())));
        }
        ActionResult result;
        try {
            result = action.open(folders);
        } catch (RuntimeException e) {
            Logger.warn(e, "Cannot open category folders");
            result = ActionResult.failure(e.toString());
        }
        String text = result.ok() ? "Folders: " + result.detail()
                : "Cannot open folders: " + result.detail();
        Platform.runLater(() -> {
            if (currentWindow != null) {
                currentWindow.statusBar().message(text);
            }
            onSettled.run();
        });
    }

    /// Starts/stops watching the active virtual desktop for the "show active
    /// desktop only" filter. On start, reads the current desktop once at once
    /// (off the FX thread) so the filter applies without waiting for the next
    /// poll tick; the periodic poll ([#scheduleActiveDesktopWatch]) keeps it
    /// fresh as the user switches desktops.
    // [impl->dsn~active-desktop-filter~6]
    private void watchActiveDesktop(boolean on) {
        this.watchActiveDesktop = on;
        ExecutorService executor = this.actionExecutor;
        if (on && executor != null) {
            executor.execute(this::refreshActiveDesktop);
        }
    }

    /// One desktop refresh on the action pool: makes sure the persistent
    /// watcher is running (first call is the only start attempt) and, only
    /// while it is not, falls back to the one-shot `powershell` read — so a
    /// machine without the watcher keeps exactly the old poll behavior.
    // [impl->dsn~active-desktop-filter~6]
    private void refreshActiveDesktop() {
        startDesktopWatch();
        if (!desktopWatchLive) {
            pushDesktop(new WindowsVirtualDesktopFocus(new LocalCommandRunner()).current());
        }
    }

    /// Starts the persistent active-desktop watcher exactly once: a process
    /// that detects an externally triggered desktop switch (e.g. a
    /// WindowsVirtualDesktopHelper hotkey) within ~250 ms, instead of the up
    /// to poll-interval + `powershell`-start seconds the one-shot read needs —
    /// the window where the list silently still shows the *previous* desktop's
    /// tasks. If the process dies later, `desktopWatchLive` drops and the
    /// periodic poll takes over again; no restart attempts (a box without
    /// `powershell` would otherwise respawn-fail every tick).
    // [impl->dsn~active-desktop-filter~6]
    private void startDesktopWatch() {
        if (desktopWatchAttempted.getAndSet(true)) {
            return;
        }
        WindowsVirtualDesktopFocus.Watch watch =
                WindowsVirtualDesktopFocus.watch(this::pushDesktop, () -> desktopWatchLive = false);
        this.desktopWatch = watch;
        this.desktopWatchLive = watch != null;
    }

    /// Pushes one desktop read to the window on the FX thread. A read that
    /// never reached the mechanism (off Windows, no `powershell`, timeout)
    /// pushes nothing, so a transient failure keeps the last known desktop
    /// instead of silently un-narrowing the filtered list; only a successful
    /// read updates — to the name, or to unknown on an unrenamed desktop.
    // [impl->dsn~active-desktop-filter~6]
    private void pushDesktop(WindowsVirtualDesktopFocus.ActiveDesktop result) {
        MainWindow currentWindow = this.window;
        if (result.read() && currentWindow != null) {
            Platform.runLater(() -> {
                currentWindow.updateActiveDesktop(result.name());
                // [impl->dsn~window-position-per-desktop~2]
                if (trackWindowPosition) {
                    applyDesktopPosition(result.name());
                }
            });
        }
    }

    /// Moves the window to the position remembered for the now-active desktop
    /// (`dsn~window-position-per-desktop~2`). Called on the FX thread with each
    /// successful desktop read while the all-desktops pin is on: on a desktop
    /// change, the geometry the user left behind is saved under the *previous*
    /// desktop's name, then the new desktop's remembered geometry is applied.
    /// The very first read (previous unknown) only restores — that is the
    /// startup restore. A geometry that intersects no current screen is
    /// dropped instead of applied, so a changed monitor configuration resets
    /// the position rather than parking the window out of reach (the git gui
    /// failure mode). Unnamed desktops (null) have no key and keep whatever
    /// position the window has; a maximized window is neither saved nor moved.
    private void applyDesktopPosition(@Nullable String desktop) {
        Stage currentStage = this.stage;
        WindowPositions positions = this.windowPositions;
        if (currentStage == null || positions == null || currentStage.isMaximized()) {
            return;
        }
        String previous = this.positionDesktop;
        if (java.util.Objects.equals(previous, desktop)) {
            return;
        }
        if (previous != null) {
            positions.put(previous, new WindowPositions.Geometry(currentStage.getX(),
                    currentStage.getY(), currentStage.getWidth(), currentStage.getHeight()));
        }
        this.positionDesktop = desktop;
        if (desktop == null) {
            return;
        }
        applyStoredGeometry(currentStage, positions, desktop);
    }

    /// Applies the geometry stored under `key` to the stage — shared by the
    /// per-desktop follow and the startup restore. A geometry that intersects
    /// no current screen is removed instead of applied, so a changed monitor
    /// configuration resets that entry rather than parking the window out of
    /// reach (the git gui failure mode).
    // [impl->dsn~window-position-per-desktop~2]
    private static void applyStoredGeometry(Stage stage, WindowPositions positions, String key) {
        WindowPositions.Geometry stored = positions.get(key);
        if (stored == null) {
            return;
        }
        if (javafx.stage.Screen.getScreensForRectangle(
                stored.x(), stored.y(), stored.width(), stored.height()).isEmpty()) {
            positions.remove(key);
            return;
        }
        stage.setX(stored.x());
        stage.setY(stored.y());
        stage.setWidth(stored.width());
        stage.setHeight(stored.height());
    }

    /// Periodically reads the active virtual desktop while the filter is on, so
    /// the list follows desktop switches. The scheduler thread only checks the
    /// flag and dispatches the read to the action pool — it never blocks on
    /// `powershell`; the read is free (skipped) while the filter is off.
    // [impl->dsn~active-desktop-filter~6]
    private void scheduleActiveDesktopWatch() {
        ScheduledExecutorService scheduler = this.scheduler;
        if (scheduler == null) {
            return;
        }
        // [impl->dsn~poll-no-wake-backlog~1]
        scheduler.scheduleWithFixedDelay(() -> {
            ExecutorService executor = this.actionExecutor;
            // The per-desktop window position rides the same read
            // (`dsn~window-position-per-desktop~2`), so the tick also runs
            // while the all-desktops pin is on.
            // [impl->dsn~energy-saver~1]
            if ((watchActiveDesktop || trackWindowPosition) && executor != null
                    && !EnergySaver.active()) {
                executor.execute(this::refreshActiveDesktop);
            }
        }, ACTIVE_DESKTOP_POLL_SECONDS, ACTIVE_DESKTOP_POLL_SECONDS, TimeUnit.SECONDS);
    }

    /// Every category's `repo:` — what a foreign `JabRef#123` in the mirror is
    /// resolved against (`dsn~terminal-issue-links~2`). Read from the category
    /// files on each selection: there are a handful of them, and they are the
    /// same files the line above already reads.
    // [impl->dsn~terminal-issue-links~2]
    private List<String> knownRepos(MainWindow.TaskFileAccess files) {
        TaskRepository repository = this.repository;
        if (repository == null) {
            return List.of();
        }
        return repository.folders().stream()
                .map(group -> groupLink(files, group,
                        content -> new TaskFileParser().parseGroupConfig(content).repo()))
                .filter(Objects::nonNull)
                .toList();
    }

    /// The category whose repository the mirror's issue links currently point
    /// at — the guard for the async guess below, which must not apply its
    /// result once the selection moved to another category.
    private @Nullable String issueRepoGroup;

    /// The task the terminal pane was last pointed at: a preview of another
    /// one blanks the pane first, a list rebuild re-showing it does not.
    // [impl->dsn~terminal-pane~14]
    private @Nullable String mirroredTaskId;

    /// Points the mirror's `#123` links at the selected task's repository: the
    /// category's `repo:` when it has one, otherwise the origin of the task's
    /// own checkout, asked over the host's shell and written back to the
    /// category config so the next session needs no round-trip. A category
    /// without a `CONTEXTSWITCHER.md` keeps the guess for this session only —
    /// there is no file to persist it in, and creating one behind the user's
    /// back is not this feature's business.
    // [impl->dsn~terminal-issue-links~2]
    private void applyIssueRepo(MainWindow.TaskFileAccess files, SshCommandRunner ssh,
            TerminalPane terminal, Task task) {
        String group = taskGroup(task);
        issueRepoGroup = group;
        String configured = groupLink(files, group, content ->
                new TaskFileParser().parseGroupConfig(content).repo());
        terminal.setIssueRepo(configured);
        terminal.setKnownRepos(knownRepos(files));
        ExecutorService executor = this.actionExecutor;
        Task.ClaudeConfig claude = task.claude();
        String host = TmuxHost.of(task);
        if (configured != null || group.isEmpty() || executor == null || claude == null
                || host == null) {
            return;
        }
        String cwd = claude.workspace() != null ? claude.workspace() : claude.cwd();
        if (cwd == null) {
            return;
        }
        executor.execute(() -> {
            SshCommandRunner.SshResult result = ssh.run(host, List.of("git", "-C", "'" + cwd + "'",
                    "config", "--get", "remote.origin.url"));
            if (!result.ok()) {
                return;
            }
            String repo = GitRemote.repoUrl(result.stdout());
            if (repo == null) {
                return;
            }
            Platform.runLater(() -> {
                if (!group.equals(issueRepoGroup)) {
                    return;
                }
                terminal.setIssueRepo(repo);
                String fileName = group + "/" + TaskRepository.GROUP_CONFIG_FILE_NAME;
                if (!files.exists(fileName)) {
                    return;
                }
                String content = files.read(fileName);
                if (content == null) {
                    return;
                }
                String error = files.save(fileName, TaskFileParser.withScalar(content, "repo", repo));
                if (error != null) {
                    Logger.warn("Cannot record repo {} for {}: {}", repo, group, error);
                } else {
                    Logger.info("Recorded guessed repo {} for category {}", repo, group);
                }
            });
        });
    }

    /// Points the mirror at the selected task's window, or explains why there
    /// is none. A task whose `window:` is gone gets a placeholder rather than
    /// the session's current window: that window belongs to whichever task was
    /// mirrored last, so the pane would silently show *another* task's session.
    // [impl->dsn~terminal-pane~14]
    private void previewTask(MainWindow.TaskFileAccess files, TerminalPane terminal, Task task) {
        // Which task the mirror is being pointed at, next to the
        // `select-window` the pane then issues: without it a pane that shows
        // another task's session is undiagnosable from the log alone — the ssh
        // line names a window id, and nothing says whose window it is.
        Logger.debug("Mirror -> {} ({})", task.id(),
                task.tmux() == null ? "no tmux" : task.tmux().window());
        // On Windows a task with no host runs in the app's own ConPTY, so the
        // owned branch answers *before* the remote offers below — those two
        // buttons open a window on a remote, which such a task does not have.
        // [impl->dsn~terminal-owned-session~3]
        if (LocalClaudeLauncher.ownsSession(task) && showOwnedSession(terminal, task)) {
            return;
        }
        Task.TmuxConfig tmux = task.tmux();
        if (tmux == null) {
            // A creation still on its way to a window says what it is doing.
            // [impl->dsn~task-create-progress~5]
            String step = creationSteps.get(task.id());
            if (step != null) {
                terminal.showMessage(step);
            } else if (task.remote() != null || taskRemote(files, task) != null) {
                // A creation that died before it got a window leaves exactly
                // this placeholder, and the user is looking right at it — so
                // the way out is offered here, not only behind play.
                // The category's `remote:` counts: a task written by hand (or
                // one whose creation died before it recorded a host) has none
                // of its own, and asking the user to add a line to the file
                // first is the dead end this placeholder exists to remove.
                // [impl->dsn~remote-window-choice~6]
                terminal.showStartWindowMessage("No tmux configured for this task.", task);
            } else {
                terminal.showMessage("No tmux configured for this task.");
            }
            return;
        }
        // No `remote:` and a tmux window: a local Claude session
        // (`dsn~task-create-local~4`), mirrored from this machine's own tmux
        // server. Windows has no tmux at all, so a task that still carries a
        // `tmux:` section there points at a window that cannot exist.
        // [impl->dsn~terminal-local-mirror~2]
        String host = TmuxHost.of(task);
        if (host == null) {
            terminal.showMessage("No tmux configured for this task.");
            return;
        }
        if (TmuxHost.isLocal(host) && LocalClaudeLauncher.onWindows()) {
            terminal.showMessage("Windows has no tmux — this task's tmux: section cannot be mirrored.");
            return;
        }
        if (task.status() == TaskStatus.SUSPENDED) {
            showPlaceholder(terminal, task,
                    "Task is suspended — its tmux window was ended. Switch resumes it.");
            return;
        }
        if (TmuxFocusAction.windowGone(task)) {
            showPlaceholder(terminal, task,
                    "No tmux window recorded for this task — Switch resurrects it.");
            return;
        }
        String owner = windowOwner(task);
        if (owner != null) {
            showPlaceholder(terminal, task, ("Window %s now hosts another Claude session (%s) — "
                    + "mirroring it would show that task's terminal. Switch gives this task its "
                    + "own window back.").formatted(tmux.window(), owner));
            return;
        }
        terminal.show(host, tmux);
    }

    /// Where an owned session runs: the `claude.cwd` a started one recorded,
    /// else the task's own `folder:` — which the group defaults seed at
    /// creation (`dsn~group-config-apply~2`), so a local category's task can
    /// be started from the placeholder even when its creation died before the
    /// write-back. Null for a task with neither, the one case that still has
    /// nowhere to run and says so.
    // [impl->dsn~terminal-owned-session~3]
    private static @Nullable String ownedCwd(Task task) {
        if (task.claude() != null) {
            return task.claude().cwd();
        }
        return task.folders().isEmpty() ? null : task.folders().getFirst();
    }

    /// Shows the task's app-owned session, or — when no process of the app's
    /// is running for it — offers to start one. After an app restart the
    /// offer is a **resume**: the newest transcript for the task's `cwd`
    /// continues the same conversation, which is what replaces the detach a
    /// tmux mirror would have given (`dsn~terminal-owned-session~3`).
    /// False when the task names no directory to run in, the one case that
    /// falls through to the ordinary "No tmux configured" placeholder.
    // [impl->dsn~terminal-owned-session~3]
    private boolean showOwnedSession(TerminalPane terminal, Task task) {
        if (terminal.showOwnedIfRunning(task.id())) {
            return true;
        }
        String cwd = ownedCwd(task);
        if (cwd == null) {
            return false;
        }
        String session = ClaudeSessionLookup.findLatestLocalSession(cwd);
        terminal.showActionMessage(
                session == null
                        ? "No session running — start Claude in " + cwd + "."
                        : "No session running — resume the last one in " + cwd + ".",
                session == null ? "start" : "resume",
                () -> terminal.startOwned(task.id(),
                        LocalClaudeLauncher.ownedCommand("", null, session), cwd));
        return true;
    }

    /// Moves the list to the task owning the window the **mirror says it is
    /// showing** — the terminal's "Select the mirrored task"
    /// (`dsn~terminal-mirrored-task~1`). Landing on the task that is already
    /// selected is the answer "the terminal is not lying"; landing elsewhere
    /// names the task whose session is really on screen.
    // [impl->dsn~terminal-mirrored-task~1]
    private void selectWindowTask(TaskRepository repository, String remote, String window) {
        MainWindow main = this.window;
        if (main == null) {
            return;
        }
        for (TaskEntry entry : repository.entries()) {
            if (entry instanceof TaskEntry.Loaded loaded
                    && loaded.task().tmux() != null
                    && remote.equals(TmuxHost.of(loaded.task()))
                    && window.equals(loaded.task().tmux().window())) {
                main.statusBar().message("The terminal shows " + loaded.task().title() + ".");
                main.selectTask(loaded.task().id());
                return;
            }
        }
        main.statusBar().message("Window %s on %s belongs to no task — \"Sync tmux windows…\" "
                .formatted(window, remote) + "imports it.");
    }

    /// The tab's matching tasks, best first: by [Task#tabUrlMatch] score, and
    /// among equal scores the task **already selected** wins. Two tasks can
    /// name the same PR — one working on it, one reviewing why a check of it
    /// fails — and score alike; without this tie-break the ranking's first
    /// task won, so opening the PR tab of the task you just selected switched
    /// the app (and the terminal mirror) to the *other* one, which reads as
    /// the app switching tasks by itself (field report 2026-09-09).
    // [impl->dsn~browser-tab-selects-task~5]
    static java.util.List<Task> rankTabMatches(java.util.List<Task> matches, String url,
            @Nullable String selectedId) {
        return matches.stream()
                .sorted(java.util.Comparator.comparingInt((Task task) -> task.tabUrlMatch(url))
                        .thenComparingInt(task -> task.id().equals(selectedId) ? 1 : 0)
                        .reversed())
                .toList();
    }

    /// The poll's `host window -> session id` map with every id that more than
    /// one window publishes dropped.
    ///
    /// A published `@cs_session_id` is only evidence of ownership while it is
    /// unique: a Claude session whose `$TMUX_PANE` is empty stamps the option
    /// onto the session's *current* window instead of its own, so a second
    /// window ends up claiming an id that belongs elsewhere. The import path
    /// has refused duplicates from the start ("no id beats a wrong one" —
    /// `enrichClaudeSessions`); the status poll, which feeds the ownership
    /// check, trusted them.
    ///
    /// Field report 2026-09-12: `@687` (Review PR 17110, its task recording
    /// exactly that id) and `@693` both published `f2d88857-…`, so the mirror
    /// refused to show `@693` — "Window @693 now hosts another Claude session"
    /// — for a task whose own window it was, and the reconcile suspended it.
    /// With the id dropped, neither window is accused and the pane mirrors
    /// again; the stray stamp itself is the hook's bug to fix.
    // [impl->dsn~tmux-window-ownership~4]
    static Map<String, String> uniqueSessionIds(Map<String, String> published) {
        Set<String> twice = new java.util.HashSet<>();
        Set<String> seen = new java.util.HashSet<>();
        for (String id : published.values()) {
            if (!seen.add(id)) {
                twice.add(id);
            }
        }
        if (twice.isEmpty()) {
            return published;
        }
        Logger.warn("Session id(s) {} published by more than one window; ignoring for ownership",
                twice);
        return published.entrySet().stream()
                .filter(entry -> !twice.contains(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /// The Claude session id the task's recorded window really hosts, when the
    /// last status poll saw a *different* one than the task's — the mirror's
    /// half of [TmuxWindowOwnership]'s check ([TmuxFocusAction] asks the remote
    /// directly, since it can also repair). Free: the id rides the status poll
    /// that already runs, so the FX thread only does a map lookup here.
    /// A window the poll has not seen yet (just created) is absent from the
    /// map and therefore never called stolen.
    // [impl->dsn~tmux-window-ownership~4]
    private @Nullable String windowOwner(Task task) {
        Task.TmuxConfig tmux = task.tmux();
        Task.ClaudeConfig claude = task.claude();
        String host = TmuxHost.of(task);
        if (tmux == null || tmux.window() == null || host == null || claude == null) {
            return null;
        }
        String published = liveSessionIds.get(TmuxStatusPoller.key(host, tmux.window()));
        return TmuxWindowOwnership.hijacked(claude.sessionId(), published) ? published : null;
    }

    /// The placeholder for a task with no live window: with its last terminal
    /// screen when one was captured before the window was killed
    /// (suspend/complete), plain text otherwise.
    // [impl->dsn~terminal-suspend-snapshot~3]
    private void showPlaceholder(TerminalPane terminal, Task task, String text) {
        PaneSnapshots snapshots = this.paneSnapshots;
        String snapshot = snapshots == null ? null : snapshots.read(task.id());
        if (snapshot == null) {
            terminal.showMessage(text);
        } else {
            terminal.showSnapshot(text + " Last screen before it ended:", snapshot);
        }
    }

    /// Windows running `claude` get the newest session id of their cwd looked
    /// up, so the generated task file allows resuming after a host reboot.
    // [impl->dsn~claude-session-capture~3]
    static List<TmuxDiscovery.TmuxSession> enrichClaudeSessions(String host,
            List<TmuxDiscovery.TmuxSession> sessions, ClaudeSessionLookup lookup,
            ClaudePrLookup prLookup) {
        // Windows whose SessionStart hook published @cs_session_id carry the
        // exact id already; the newest-transcript heuristic only fills the
        // rest. Two sessions started in the same directory share a transcript
        // folder, so the heuristic can hand the SAME id to several windows —
        // such collisions (also with an exact id) are dropped: no id beats a
        // wrong one (resume would hijack another task's session).
        Set<String> takenIds = sessions.stream().flatMap(session -> session.windows().stream())
                .map(TmuxDiscovery.TmuxWindow::sessionId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.HashSet::new));
        // Every window that needs a footer scrape in ONE ssh round-trip, the
        // batch `dsn~claude-pr-capture~2` already built for the periodic
        // footer poll — not one connection per window, which is what made a
        // single reconcile cost eleven of them on a host with ten Claude
        // windows, over the six global slots of `dsn~ssh-command-runner~6`.
        // [impl->dsn~claude-pr-capture~2]
        Map<String, List<String>> footerPrs = prLookup.findPrUrls(host,
                sessions.stream().flatMap(session -> session.windows().stream())
                        .filter(window -> window.prUrl() == null
                                && "claude".equals(window.command()))
                        .map(TmuxDiscovery.TmuxWindow::id)
                        .toList());
        return sessions.stream().map(session -> new TmuxDiscovery.TmuxSession(session.name(),
                session.windows().stream().map(window -> {
                    // PR footer scrape for Claude windows without a
                    // published @cs_pr option.
                    // [impl->dsn~claude-pr-capture~2]
                    // Only the first PR is seeded into the imported file; the
                    // periodic footer scrape appends any further ones.
                    List<String> prUrls = footerPrs.getOrDefault(window.id(), List.of());
                    if (!prUrls.isEmpty()) {
                        window = window.withPrUrl(prUrls.getFirst());
                    }
                    if (window.sessionId() != null
                            || !"claude".equals(window.command()) || window.cwd().isBlank()) {
                        return window;
                    }
                    String sessionId = lookup.findLatestSession(host, window.cwd());
                    if (sessionId == null) {
                        return window;
                    }
                    if (!takenIds.add(sessionId)) {
                        Logger.warn("Session id {} resolved for more than one window; dropping it"
                                + " for {} — publish @cs_session_id for exact ids", sessionId, window.id());
                        return window;
                    }
                    return window.withSessionId(sessionId);
                }).toList())).toList();
    }

    private void switchTo(SwitchOrchestrator orchestrator, MainWindow.TaskFileAccess files, Task task) {
        switchTo(orchestrator, files, task, null);
    }

    /// A play, optionally narrowed to the actions named in `only` (`null`: all
    /// of them) — the toolbar globe runs the browser action this way. The
    /// "nothing configured" chip names what was looked for, so a narrowed run
    /// on a task without that section does not read as a task without any
    /// configuration at all.
    private void switchTo(SwitchOrchestrator orchestrator, MainWindow.TaskFileAccess files,
            Task task, @Nullable Set<String> only) {
        MainWindow window = this.window;
        if (window == null) {
            return;
        }
        Task effective = withCategoryDefaults(files, task);
        window.statusBar().beginSwitch(effective.title());
        List<String> configured = orchestrator.configuredActions(effective).stream()
                .filter(action -> only == null || only.contains(action))
                .toList();
        if (configured.isEmpty()) {
            window.statusBar().update("nothing configured", ActionStatus.FAILED,
                    only == null ? "Task has no tmux/intellij/browser configuration"
                            : "Task has no " + String.join("/", only) + " configuration");
            return;
        }
        // [impl->dsn~terminal-pane~14]
        orchestrator.switchTo(effective, only, window.statusBar()::update,
                () -> window.refreshPreview(effective.id()));
    }

    /// The task with its category's action defaults folded in — what a switch
    /// actually runs. The `CONTEXTSWITCHER.md` is read fresh (like
    /// [#groupDesktop]) so an edited default takes effect without a restart.
    /// Only the switch path merges: the suspend teardown keeps closing the
    /// task's *own* browser tabs, since a category's URLs are shared by every
    /// task in it and ending one task must not close the project's pages.
    // [impl->dsn~category-action-defaults~1]
    private static Task withCategoryDefaults(MainWindow.TaskFileAccess files, Task task) {
        String group = taskGroup(task);
        if (group.isEmpty()) {
            return task;
        }
        String fileName = group + "/" + TaskRepository.GROUP_CONFIG_FILE_NAME;
        return files.exists(fileName)
                ? task.withCategoryDefaults(new TaskFileParser().parseGroupConfig(files.load(fileName)))
                : task;
    }

    /// Runs the suspend teardown actions (tmux window kill, browser-tab
    /// close) with the same per-action chips as a switch.
    // [impl->dsn~task-suspend~6]
    private void suspendTo(SwitchOrchestrator orchestrator, Task task) {
        MainWindow window = this.window;
        if (window == null) {
            return;
        }
        window.statusBar().beginSwitch(task.title() + " (suspend)");
        if (orchestrator.configuredActions(task).isEmpty()) {
            window.statusBar().update("nothing to end", ActionStatus.OK,
                    "Task has no live context configured");
            return;
        }
        // [impl->dsn~terminal-pane~14]
        teardowns++;
        orchestrator.switchTo(task, window.statusBar()::update, () -> {
            window.refreshPreview(task.id());
            endTeardown();
        });
    }

    /// Suspend and delete teardowns in flight (FX thread). While non-zero,
    /// tab reports are dropped: closing a tab activates its neighbour.
    // [impl->dsn~browser-teardown-quiet-tabs~1]
    private int teardowns;

    /// Ends one teardown; the last one asks the extension which tab is in
    /// view now and selects its task — select only, never resume, since the
    /// user did not pick that tab. The count drops only once the answer is
    /// in, so a report the close triggered that is still on its way arrives
    /// while reports are dropped.
    // [impl->dsn~browser-teardown-quiet-tabs~1]
    private void endTeardown() {
        ExtensionServer server = this.extensionServer;
        ExecutorService executor = this.actionExecutor;
        TaskRepository repository = this.repository;
        if (teardowns > 1 || server == null || executor == null || repository == null) {
            teardowns--;
            return;
        }
        executor.execute(() -> {
            ExtensionProtocol.Result active = server.activeTab();
            Platform.runLater(() -> {
                teardowns--;
                if (teardowns == 0 && active.ok() && !active.detail().isBlank()) {
                    selectTaskForTab(repository, active.detail(), false);
                }
            });
        });
    }

    /// The delete dialog's "Ask Claude to tidy up first" prompt: generic on
    /// purpose — the session in the window knows its own worktree and branch.
    // [impl->dsn~claude-session-kill~6]
    private static final String CLEANUP_PROMPT = """
            This task is about to be deleted in ContextSwitcher. Please tidy up: \
            push or merge to main whatever is worth keeping, then remove this \
            task's git worktree and delete its branch (git worktree remove from \
            the primary clone, then git branch -d), plus any other temporary \
            state you created for this task. Reply with a one-line summary when \
            you are done.""";

    /// Sends [#CLEANUP_PROMPT] into the task's tmux window (same delivery as
    /// a queued message) and deletes nothing — the user deletes the task once
    /// Claude reports done. Outcome lands in the status line.
    // [impl->dsn~claude-session-kill~6]
    private void askClaudeCleanup(MessageSender sender, Task task) {
        MainWindow window = this.window;
        ExecutorService executor = this.actionExecutor;
        Task.TmuxConfig tmux = task.tmux();
        String remote = TmuxHost.of(task);
        if (window == null || executor == null) {
            return;
        }
        if (tmux == null || remote == null) {
            window.statusBar().message("No tmux window to send the cleanup prompt to.");
            return;
        }
        window.statusBar().message("Sending cleanup prompt to Claude…");
        executor.execute(() -> {
            String error = sender.send(remote, tmux.target(), CLEANUP_PROMPT);
            Platform.runLater(() -> window.statusBar().message(error == null
                    ? "Cleanup prompt sent — delete the task once Claude reports done."
                    : "Cannot send cleanup prompt: " + error));
        });
    }

    /// The merged-task janitor's acting half (`MainWindow.autoCleanupMerged`
    /// nominates, [MergedCleanup] decides): off the FX thread it captures the
    /// task's last screen — live from the window, else the snapshot the
    /// suspend stored — and, when that screen needs nobody (or the task has
    /// been paused past the grace period anyway), appends it to the task file,
    /// commits the task directory so git holds the archived version, and then
    /// ends the window, removes transcript and worktree, and deletes the file.
    ///
    /// Nothing here asks: that is the point of the feature. What protects the
    /// user is the decision (an interesting screen buys `graceDays` of pause)
    /// and the commit — a removed task is one `git log` away.
    // [impl->dsn~merged-task-cleanup~2]
    private void cleanupMerged(MainWindow.TaskFileAccess files, PaneSnapshots snapshots,
            TaskGitBackup backup, SwitchOrchestrator orchestrator,
            ClaudeSessionCleanup cleanup, Task task, int graceDays) {
        ExecutorService executor = this.actionExecutor;
        if (executor == null) {
            return;
        }
        executor.execute(() -> {
            String live = snapshots.captureNow(task);
            String screen = live != null ? live : snapshots.read(task.id());
            if (!MergedCleanup.cleanUpNow(screen, task.suspendedAt(), graceDays,
                    java.time.LocalDateTime.now())) {
                Logger.debug("Merged task {} kept: its last screen still wants a human.", task.id());
                return;
            }
            Logger.info("Merged task {}: archiving its last screen and removing it.", task.id());
            String error = rewriteTaskFile(files, task,
                    content -> MergedCleanup.withLastScreen(content, screen));
            if (error != null) {
                Logger.warn("Cannot archive the last screen of {}: {}", task.id(), error);
            }
            backup.commitNow("ContextSwitcher: archive " + task.id() + " (pull request merged)");
            Task.ClaudeConfig claude = task.claude();
            ClaudeSessionCleanup.Choices choices = new ClaudeSessionCleanup.Choices(
                    task.tmux() != null, false,
                    claude != null && claude.sessionId() != null,
                    claude != null && claude.workspace() != null);
            Platform.runLater(() -> {
                killSession(files, orchestrator, cleanup, task, choices);
                String deleteError = files.delete(task.id() + ".md");
                if (deleteError != null) {
                    Logger.warn("Cannot delete the merged task {}: {}", task.id(), deleteError);
                }
            });
        });
    }

    /// Runs the delete dialog's ticked cleanup: the window teardown through
    /// the regular suspend orchestrator (per-action chips) restricted to the
    /// ticked ones — "tmux", "browser", or both — and — after it finished, so
    /// Claude's exit cannot recreate the transcript or hold the directory —
    /// transcript/workdir removal over ssh.
    // [impl->dsn~claude-session-kill~6]
    private void killSession(MainWindow.TaskFileAccess files, SwitchOrchestrator orchestrator,
            ClaudeSessionCleanup cleanup, Task task, ClaudeSessionCleanup.Choices choices) {
        MainWindow window = this.window;
        ExecutorService executor = this.actionExecutor;
        if (window == null || executor == null) {
            return;
        }
        window.statusBar().beginSwitch(task.title() + " (delete)");
        Runnable remoteCleanup = () -> executor.execute(() -> {
            Task.ClaudeConfig claude = task.claude();
            String remote = task.remote();
            if (claude == null || remote == null) {
                return;
            }
            if (choices.removeTranscript() && claude.sessionId() != null) {
                String error = cleanup.removeTranscript(remote, claude.cwd(), claude.sessionId());
                Platform.runLater(() -> window.statusBar().update("transcript",
                        error == null ? ActionStatus.OK : ActionStatus.FAILED,
                        error == null ? "transcript removed" : error));
            }
            if (choices.removeWorkdir() && claude.workspace() != null) {
                String error = cleanup.removeWorkdir(remote, claude.workspace(),
                        groupProtectedDirs(files, taskGroup(task)));
                Platform.runLater(() -> window.statusBar().update("workdir",
                        error == null ? ActionStatus.OK : ActionStatus.FAILED,
                        error == null ? "working directory removed" : error));
            }
        });
        Set<String> selected = new HashSet<>();
        if (choices.endWindow()) {
            selected.add("tmux");
        }
        if (choices.closeBrowserTabs()) {
            selected.add("browser");
        }
        if (!selected.isEmpty() && !orchestrator.configuredActions(task).isEmpty()) {
            teardowns++;
            orchestrator.switchTo(task, selected, window.statusBar()::update, () -> {
                endTeardown();
                remoteCleanup.run();
            });
        } else {
            remoteCleanup.run();
        }
    }
}
