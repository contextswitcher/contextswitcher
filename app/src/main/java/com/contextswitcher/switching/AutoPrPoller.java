package com.contextswitcher.switching;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

import com.contextswitcher.config.EnergySaver;
import com.contextswitcher.tasks.GroupConfig;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskFileParser;
import com.contextswitcher.tasks.TaskRepository;

/// Polls the query of every *auto category* — a task folder whose
/// `CONTEXTSWITCHER.md` carries an `auto:` section — and hands each round's
/// matches to the callback, which reconciles the category's task files on the
/// UI thread (`dsn~auto-pr-category~3`).
///
/// The category configs are read from disk on the poller's own thread rather
/// than taken from the window: they are two small files, and this keeps the
/// poller free of the FX-thread-only collections the repository publishes.
/// What the query matches decides what is **added**; what keeps a task is the
/// state of its pull request. A category task whose PR is not among this
/// round's hits — it grew past the size limit, its review request went to
/// somebody else, or the user dropped a PR of their own into the category —
/// therefore costs one batched [PrStateLookup] call, and only a `MERGED` or
/// `CLOSED` answer marks it done. Everything else, an unreadable answer
/// included, counts as live: a task is suspended for a fact, never for a
/// silence.
///
/// A round that failed ([AutoPrLookup#search] returned null) reports nothing
/// at all — an empty match list means "every PR of this category is done" and
/// must never be invented out of an offline `gh`. For the same reason nothing
/// runs before `ready` says the task directory has been scanned: reconciling
/// against a half-loaded list would add a second task for every pull request
/// whose task has not been parsed yet.
// [impl->dsn~auto-pr-lookup~2]
public class AutoPrPoller implements AutoCloseable {

    /// One category's finished round: the `matches` worth adding, and `live`
    /// — every pull request URL that must **not** be suspended (this round's
    /// hits plus every tracked PR not known to be merged or closed), each
    /// normalized through [Task#normalizeUrl].
    public record Round(String category, GroupConfig.AutoPr config,
            List<AutoPrLookup.Match> matches, Set<String> live) {
    }

    private final Path tasksDir;
    private final TaskFileParser parser;
    private final AutoPrLookup lookup;
    private final PrStateLookup states;
    private final Function<String, List<String>> categoryPrUrls;
    private final BooleanSupplier ready;
    private final Consumer<Round> onRound;
    private final long intervalSeconds;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "auto-pr-poller");
        thread.setDaemon(true);
        return thread;
    });

    public AutoPrPoller(Path tasksDir, TaskFileParser parser, AutoPrLookup lookup,
            PrStateLookup states, Function<String, List<String>> categoryPrUrls,
            BooleanSupplier ready, Consumer<Round> onRound, long intervalSeconds) {
        this.tasksDir = tasksDir;
        this.parser = parser;
        this.lookup = lookup;
        this.states = states;
        this.categoryPrUrls = categoryPrUrls;
        this.ready = ready;
        this.onRound = onRound;
        this.intervalSeconds = intervalSeconds;
    }

    /// Starts polling. Fixed-*delay*, so a slow `gh` search never lets ticks
    /// pile up on each other.
    public void start() {
        scheduler.scheduleWithFixedDelay(this::tick, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /// Runs one round right now, past the energy-saver gate — the manual
    /// refresh.
    // [impl->dsn~energy-saver~1]
    public CompletableFuture<Void> pollNow() {
        return CompletableFuture.runAsync(this::poll, scheduler);
    }

    // [impl->dsn~energy-saver~1]
    private void tick() {
        if (!EnergySaver.active()) {
            poll();
        }
    }

    private void poll() {
        if (!ready.getAsBoolean()) {
            return;
        }
        for (String category : autoCategories()) {
            GroupConfig.AutoPr config = configOf(category);
            if (config == null) {
                continue;
            }
            AutoPrLookup.Search search = lookup.search(config.query(), config.maxSloc());
            if (search == null) {
                continue;
            }
            Logger.debug("Auto category {}: {} of {} hit(s) within the size limit",
                    category, search.matches().size(), search.open().size());
            onRound.accept(new Round(category, config, search.matches(), live(category, search)));
        }
    }

    /// The pull requests of `category` that must survive this round: the
    /// search's own hits, plus every PR its tasks carry that the hits do not
    /// name and GitHub does not report as merged or closed. The state call is
    /// one batched request and is skipped entirely while every tracked PR is
    /// a hit — the normal state of a category nobody dropped anything into.
    private Set<String> live(String category, AutoPrLookup.Search search) {
        Set<String> live = new HashSet<>();
        search.open().forEach(url -> live.add(Task.normalizeUrl(url)));
        List<String> tracked = categoryPrUrls.apply(category).stream()
                .map(Task::normalizeUrl)
                .filter(url -> !live.contains(url))
                .distinct()
                .toList();
        if (tracked.isEmpty()) {
            return live;
        }
        Map<String, PrInfo> known = states.states(tracked);
        for (String url : tracked) {
            PrInfo info = known.get(url);
            if (info == null || (info.state() != PrState.MERGED && info.state() != PrState.CLOSED)) {
                live.add(url);
            }
        }
        return live;
    }

    /// The task subfolders carrying a category config, cheapest first: the
    /// directory listing costs nothing, and a machine with no auto category
    /// never reaches `gh` at all.
    private List<String> autoCategories() {
        List<String> categories = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(tasksDir)) {
            dirs.filter(Files::isDirectory)
                    .filter(dir -> Files.isRegularFile(dir.resolve(TaskRepository.GROUP_CONFIG_FILE_NAME)))
                    .map(dir -> dir.getFileName().toString())
                    .sorted()
                    .forEach(categories::add);
        } catch (IOException e) {
            Logger.debug("Cannot list the task directory for auto categories: {}", e.getMessage());
        }
        return categories;
    }

    private GroupConfig.@Nullable AutoPr configOf(String category) {
        Path config = tasksDir.resolve(category).resolve(TaskRepository.GROUP_CONFIG_FILE_NAME);
        try {
            return parser.parseGroupConfig(Files.readString(config)).autoPr();
        } catch (IOException e) {
            Logger.debug("Cannot read {}: {}", config, e.getMessage());
            return null;
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
