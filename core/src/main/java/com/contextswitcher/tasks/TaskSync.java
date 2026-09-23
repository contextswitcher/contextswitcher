package com.contextswitcher.tasks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.contextswitcher.config.YamlPatch;
import com.contextswitcher.local.LocalCommandRunner;

/// Sync groups: tasks shared with other people through a git repository per
/// group (`dsn~task-sync-groups~1`).
///
/// A group's repository is a flat folder of task files (`<syncId>.md`) holding
/// only the *shared* part of a task — [#SHARED_KEYS] and the Markdown notes.
/// Everything machine- or person-specific (status, remote, tmux, Claude,
/// IntelliJ, folders, pinned) stays in the local file, and so does the
/// category: each member sorts a shared task into a category of their own.
///
/// A local task takes part through two frontmatter keys: `sync:` lists its
/// groups and `syncId:` names its file in them. The clones live outside the
/// task directory (`clonesDir/<group>`), since that directory is a git
/// repository of its own; the group list and the ignored shared tasks live in
/// the task directory's `.sync/` so they ride its personal backup to the
/// user's other machines.
///
/// A round per group ([#syncAll]): write the local shared parts into the
/// clone, commit, pull (merge, with [TaskGitBackup]'s conflict resolution),
/// push, then write what arrived back into the local files. What the last
/// round had in sync is remembered in the clone's `.git` (per machine), which
/// tells a task deleted locally (removed for everyone) from one just joined,
/// and a task deleted remotely (the local copy leaves the group but is kept —
/// it carries the user's own sessions) from one not pushed yet.
// [impl->dsn~task-sync-groups~1]
public class TaskSync {

    /// The frontmatter keys a shared task file carries; every other key stays local.
    public static final List<String> SHARED_KEYS = List.of("title", "tags", "browser", "chat", "note");

    public static final String SYNC_KEY = "sync";
    public static final String SYNC_ID_KEY = "syncId";

    /// A configured group: its `name` (the clone's folder, the value in `sync:`)
    /// and the git `url` of its repository.
    public record Group(String name, String url) {
    }

    /// A shared task no local task takes part with in its group — new, left, or
    /// (when `ignored`) set aside by the user until it changes again.
    /// `localFile` is the task file (relative to the task directory) still
    /// carrying the `syncId` after leaving, null when there is none.
    public record Incoming(String group, String syncId, String title, String content,
            boolean ignored, @Nullable String localFile) {
    }

    private record Local(Path file, String syncId, List<String> groups, String content) {
    }

    private final Path tasksDir;
    private final Path clonesDir;
    private final Function<List<String>, LocalCommandRunner.LocalResult> git;

    public TaskSync(Path tasksDir, Path clonesDir) {
        this(tasksDir, clonesDir, new LocalCommandRunner(Duration.ofSeconds(60))::run);
    }

    TaskSync(Path tasksDir, Path clonesDir, Function<List<String>, LocalCommandRunner.LocalResult> git) {
        this.tasksDir = tasksDir;
        this.clonesDir = clonesDir;
        this.git = git;
    }

    // ---- configuration ----------------------------------------------------

    private Path groupsFile() {
        return tasksDir.resolve(".sync").resolve("groups.yaml");
    }

    private Path ignoredFile() {
        return tasksDir.resolve(".sync").resolve("ignored.yaml");
    }

    /// The configured groups, in file order; entries without a valid name or
    /// url are skipped (hand-edited file).
    public synchronized List<Group> groups() {
        List<Group> groups = new ArrayList<>();
        if (loadYaml(groupsFile()) instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && map.get("name") != null && map.get("url") != null
                        && validName(map.get("name").toString())) {
                    groups.add(new Group(map.get("name").toString(), map.get("url").toString()));
                }
            }
        }
        return groups;
    }

    public synchronized void saveGroups(List<Group> groups) {
        writeYaml(groupsFile(), groups.stream()
                .map(group -> Map.of("name", group.name(), "url", group.url()))
                .toList());
    }

    /// A group name doubles as a folder name and a YAML list value.
    public static boolean validName(String name) {
        return name.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
    }

    /// Sets the shared task aside until its content changes.
    public synchronized void ignore(Incoming incoming) {
        Map<String, Map<String, String>> ignored = ignored();
        ignored.computeIfAbsent(incoming.group(), key -> new LinkedHashMap<>())
                .put(incoming.syncId(), hash(incoming.content()));
        writeYaml(ignoredFile(), ignored);
    }

    private Map<String, Map<String, String>> ignored() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (loadYaml(ignoredFile()) instanceof Map<?, ?> groups) {
            groups.forEach((group, ids) -> {
                Map<String, String> byId = new LinkedHashMap<>();
                if (ids instanceof Map<?, ?> map) {
                    map.forEach((id, hash) -> byId.put(String.valueOf(id), String.valueOf(hash)));
                }
                result.put(String.valueOf(group), byId);
            });
        }
        return result;
    }

    // ---- task file transforms ---------------------------------------------

    /// The groups a task file's `sync:` names (a list or a single scalar).
    public static List<String> groupsOf(String content) {
        Object sync = Frontmatter.parse(content).get(SYNC_KEY);
        if (sync instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return sync == null ? List.of() : List.of(sync.toString());
    }

    /// `content` joined to (`member`) or leaving `group`. Joining keeps an
    /// existing `syncId:` and otherwise sets `syncId`; leaving keeps it, so
    /// re-joining from the incoming list finds the task again.
    public static String withGroup(String content, String group, boolean member, String syncId) {
        List<String> groups = new ArrayList<>(groupsOf(content));
        groups.remove(group);
        if (member) {
            groups.add(group);
        }
        String result = Frontmatter.set(content, SYNC_KEY, groups.isEmpty() ? null : groups);
        if (member && !(Frontmatter.parse(result).get(SYNC_ID_KEY) instanceof String)) {
            result = Frontmatter.set(result, SYNC_ID_KEY, syncId);
        }
        return result;
    }

    /// The `syncId` a task joining `group` gets: its own when it has one,
    /// else `base` — suffixed `-2`, `-3`… while the group already holds a
    /// shared task of that name.
    public String syncIdFor(String content, String group, String base) {
        if (Frontmatter.parse(content).get(SYNC_ID_KEY) instanceof String existing) {
            return existing;
        }
        Path clone = clonesDir.resolve(group);
        String id = base;
        for (int n = 2; Files.exists(clone.resolve(id + ".md")); n++) {
            id = base + "-" + n;
        }
        return id;
    }

    /// The content of a new local task sorted in from `incoming`: the shared
    /// part, suspended, taking part in the group.
    public static String sortInContent(Incoming incoming) {
        String content = Frontmatter.set(incoming.content(), "status", TaskStatus.SUSPENDED.yaml());
        return withGroup(content, incoming.group(), true, incoming.syncId());
    }

    /// The shared part of a task file: the frontmatter reduced to
    /// [#SHARED_KEYS] (their text kept verbatim), and the notes.
    static String shared(String content) {
        String block = Frontmatter.block(content);
        if (block == null) {
            return content;
        }
        for (String key : Frontmatter.parse(content).keySet()) {
            if (!SHARED_KEYS.contains(key)) {
                block = YamlPatch.remove(block, key);
            }
        }
        return Frontmatter.withBlock(content, block);
    }

    /// `local` with its shared keys and notes replaced by `shared`'s; a key
    /// whose value did not change is not rewritten (keeps its formatting).
    static String applyShared(String local, String shared) {
        Map<String, Object> theirs = Frontmatter.parse(shared);
        Map<String, Object> ours = Frontmatter.parse(local);
        String result = local;
        for (String key : SHARED_KEYS) {
            if (!Objects.equals(ours.get(key), theirs.get(key))) {
                result = Frontmatter.set(result, key, theirs.get(key));
            }
        }
        String block = Frontmatter.block(result);
        return block == null ? result : "---\n" + block + "---\n" + body(shared);
    }

    /// Whether two files agree on the shared keys and the notes.
    static boolean sameShared(String a, String b) {
        Map<String, Object> left = Frontmatter.parse(a);
        Map<String, Object> right = Frontmatter.parse(b);
        return SHARED_KEYS.stream().allMatch(key -> Objects.equals(left.get(key), right.get(key)))
                && body(a).equals(body(b));
    }

    /// The Markdown after the closing frontmatter fence ("" when there is none).
    private static String body(String content) {
        String normalized = content.replace("\r\n", "\n");
        if (Frontmatter.block(normalized) == null) {
            return normalized;
        }
        int close = normalized.indexOf("\n---", 3);
        int lineEnd = normalized.indexOf('\n', close + 1);
        return lineEnd < 0 ? "" : normalized.substring(lineEnd + 1);
    }

    // ---- the round --------------------------------------------------------

    /// Syncs every configured group (clone on first use), then returns the
    /// shared tasks waiting to be sorted in. Blocking, network — call off the
    /// UI thread. A group that fails (no network, bad url) only logs.
    public synchronized List<Incoming> syncAll() {
        for (Group group : groups()) {
            try {
                syncGroup(group);
            } catch (RuntimeException e) {
                Logger.warn("Sync group {} failed: {}", group.name(), e.toString());
            }
        }
        return incoming();
    }

    private void syncGroup(Group group) {
        Path clone = clonesDir.resolve(group.name());
        if (!Files.isDirectory(clone.resolve(".git"))) {
            try {
                Files.createDirectories(clonesDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            LocalCommandRunner.LocalResult cloned = git.apply(
                    List.of("git", "clone", group.url(), clone.toString()));
            if (!cloned.ok()) {
                Logger.warn("Cannot clone sync group {} from {}: {}", group.name(), group.url(),
                        cloned.stderr().strip());
                return;
            }
        }
        boolean[] broken = {false};
        List<Local> locals = scanLocal(broken);
        // A task file whose frontmatter does not parse hides its syncId: it
        // would read as deleted, and a deletion here is a deletion for everyone.
        boolean mayDelete = !broken[0];
        Map<String, Local> mine = new LinkedHashMap<>();
        Set<String> carried = new HashSet<>();
        for (Local local : locals) {
            carried.add(local.syncId());
            if (local.groups().contains(group.name())) {
                mine.put(local.syncId(), local);
            }
        }
        Set<String> mapped = readMapped(clone);

        // Gone from the group locally: deleted (remove it for everyone) or left
        // (set the shared task aside until it changes).
        for (String id : mapped) {
            Path shared = clone.resolve(id + ".md");
            if (mine.containsKey(id) || !Files.exists(shared)) {
                continue;
            }
            if (carried.contains(id)) {
                ignore(new Incoming(group.name(), id, id, read(shared), true, null));
            } else if (mayDelete) {
                Logger.info("Sync group {}: {} deleted locally, removing it from the group", group.name(), id);
                delete(shared);
            }
        }

        // Out: every local task's shared part into the clone.
        Map<String, String> exported = new LinkedHashMap<>();
        for (Local local : List.copyOf(mine.values())) {
            Path shared = clone.resolve(local.syncId() + ".md");
            if (!Files.exists(shared) && mapped.contains(local.syncId())) {
                // Synced before, gone since: deleted by another member.
                leaveDeleted(group, local);
                mine.remove(local.syncId());
                continue;
            }
            String part = shared(local.content());
            // A task (re)joining a group that already holds it adopts the
            // group's version: its own copy is stale, not an edit to push.
            boolean joining = !mapped.contains(local.syncId()) && Files.exists(shared);
            if (!joining && (!Files.exists(shared) || !sameShared(read(shared), part))) {
                write(shared, part);
            }
            exported.put(local.syncId(), part);
        }

        new TaskGitBackup(clone, git).syncNow("ContextSwitcher sync");

        // In: what the others changed, merged into the local files.
        Set<String> nowMapped = new HashSet<>();
        for (Local local : mine.values()) {
            Path shared = clone.resolve(local.syncId() + ".md");
            if (!Files.exists(shared)) {
                leaveDeleted(group, local);
                continue;
            }
            nowMapped.add(local.syncId());
            if (!Files.exists(local.file())) {
                continue; // deleted while the round ran; the next round removes it
            }
            // ponytail: a local edit landing between this read and the write
            // below is lost; the window is the length of one write.
            String current = read(local.file());
            String ours = shared(current);
            String base = exported.get(local.syncId());
            String theirs = read(shared);
            String merged = sameShared(ours, base) ? theirs
                    : TaskMerge.merge(base, ours, theirs, false).orElse(ours);
            if (!sameShared(merged, ours)) {
                write(local.file(), applyShared(current, merged));
            }
        }
        writeMapped(clone, nowMapped);
    }

    /// A task another member deleted: the local file leaves the group rather
    /// than being deleted — it holds this user's own sessions and paths.
    private void leaveDeleted(Group group, Local local) {
        Logger.info("Sync group {}: {} was deleted by another member; {} leaves the group",
                group.name(), local.syncId(), local.file());
        if (Files.exists(local.file())) {
            write(local.file(), withGroup(read(local.file()), group.name(), false, local.syncId()));
        }
    }

    /// The shared tasks of every group no local task takes part with there,
    /// from the clones as they are (no network).
    public synchronized List<Incoming> incoming() {
        List<Local> locals = scanLocal(new boolean[1]);
        Map<String, Map<String, String>> ignored = ignored();
        List<Incoming> result = new ArrayList<>();
        for (Group group : groups()) {
            Path clone = clonesDir.resolve(group.name());
            if (!Files.isDirectory(clone)) {
                continue;
            }
            for (Path file : taskFiles(clone)) {
                String id = file.getFileName().toString().replaceFirst("\\.md$", "");
                Local carrier = locals.stream().filter(local -> local.syncId().equals(id)).findFirst().orElse(null);
                if (carrier != null && carrier.groups().contains(group.name())) {
                    continue;
                }
                String content = read(file);
                String title = Frontmatter.parse(content).get("title") instanceof String t ? t : id;
                boolean isIgnored = hash(content).equals(ignored.getOrDefault(group.name(), Map.of()).get(id));
                result.add(new Incoming(group.name(), id, title, content, isIgnored,
                        carrier == null ? null : TaskFileParser.relativeId(tasksDir, carrier.file()) + ".md"));
            }
        }
        return result;
    }

    /// Every task file carrying a `syncId:`, root and one category level deep.
    private List<Local> scanLocal(boolean[] broken) {
        List<Path> files = new ArrayList<>(taskFiles(tasksDir));
        try (Stream<Path> dirs = Files.list(tasksDir)) {
            dirs.filter(dir -> Files.isDirectory(dir) && !dir.getFileName().toString().startsWith("."))
                    .forEach(dir -> files.addAll(taskFiles(dir)));
        } catch (IOException e) {
            broken[0] = true;
            return List.of();
        }
        if (files.isEmpty()) {
            broken[0] = true; // a missing or wiped task directory is not "everything deleted"
        }
        List<Local> locals = new ArrayList<>();
        for (Path file : files) {
            String content = read(file);
            Map<String, Object> data = Frontmatter.parse(content);
            String block = Frontmatter.block(content);
            if (data.isEmpty() && block != null && !block.isBlank()) {
                broken[0] = true;
            }
            if (data.get(SYNC_ID_KEY) instanceof String id) {
                locals.add(new Local(file, id, groupsOf(content), content));
            }
        }
        return locals;
    }

    private static List<Path> taskFiles(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(file -> {
                String name = file.getFileName().toString();
                return name.endsWith(".md") && Files.isRegularFile(file)
                        && !name.equals(TaskRepository.TEMPLATE_FILE_NAME)
                        && !name.equals(TaskRepository.GROUP_CONFIG_FILE_NAME)
                        && !name.equalsIgnoreCase("README.md");
            }).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static Set<String> readMapped(Path clone) {
        Path file = clone.resolve(".git").resolve("contextswitcher-synced");
        if (!Files.exists(file)) {
            return Set.of();
        }
        return new HashSet<>(read(file).lines().filter(line -> !line.isBlank()).toList());
    }

    private static void writeMapped(Path clone, Set<String> ids) {
        write(clone.resolve(".git").resolve("contextswitcher-synced"),
                String.join("\n", ids.stream().sorted().toList()) + "\n");
    }

    // ---- io ---------------------------------------------------------------

    private static @Nullable Object loadYaml(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(TextFiles.read(file));
        } catch (IOException | RuntimeException e) {
            Logger.warn("Cannot read {}: {}", file, e.getMessage());
            return null;
        }
    }

    private static void writeYaml(Path file, Object data) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        write(file, new Yaml(options).dump(data));
    }

    private static String read(Path file) {
        try {
            return TextFiles.read(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            TextFiles.write(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String hash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
