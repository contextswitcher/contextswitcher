package com.contextswitcher.tasks;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/// Parses one task Markdown file: YAML frontmatter between `---` fences,
/// free-form Markdown notes after the closing fence.
// [impl->dsn~task-file-parsing~4]
public class TaskFileParser {

    private static final String FENCE = "---";

    /// Whether `line` is a frontmatter fence: `---` at **column 0**.
    /// The indentation matters — a `---` inside a multi-line block scalar
    /// (a pasted task description with a horizontal rule, indented into
    /// `title: |-`) is content, not the closing fence, and every textual
    /// writer that mistook it for one inserted its key into the middle of
    /// the scalar and left the file unparseable (field report 2026-09-07).
    private static boolean isFence(String line) {
        return FENCE.equals(line.stripTrailing());
    }

    /// A string serialized as a single-line YAML scalar — SnakeYAML decides
    /// quoting and escaping — for textual insertion into frontmatter
    /// templates (which keep their comments, so no document round-trip).
    public static String yamlScalar(String value) {
        return new Yaml().dump(value).stripTrailing();
    }

    public Task parse(Path file) throws TaskParseException {
        String stem = file.getFileName().toString().replaceFirst("\\.md$", "");
        return parse(stem, readContent(file));
    }

    /// Parses `file`, using its path relative to `tasksDir` (forward-slash
    /// separated, `.md` stripped) as id — e.g. `jabref/fix-npe` for a task
    /// grouped under the `jabref` subfolder.
    // [impl->dsn~task-repository-watching~6]
    public Task parse(Path tasksDir, Path file) throws TaskParseException {
        return parse(relativeId(tasksDir, file), readContent(file));
    }

    private String readContent(Path file) throws TaskParseException {
        try {
            return TextFiles.read(file);
        } catch (IOException e) {
            throw new TaskParseException("Cannot read %s: %s".formatted(file, e.getMessage()), e);
        }
    }

    /// The path of `file` relative to `tasksDir`, forward-slash separated
    /// regardless of platform, with the `.md` extension stripped.
    public static String relativeId(Path tasksDir, Path file) {
        String relative = tasksDir.relativize(file).toString().replace(File.separatorChar, '/');
        return relative.replaceFirst("\\.md$", "");
    }

    public Task parse(String id, String content) throws TaskParseException {
        String normalized = content.replace("\r\n", "\n");
        if (normalized.startsWith("﻿")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.startsWith(FENCE + "\n")) {
            throw new TaskParseException("Task file must start with a '---' frontmatter fence");
        }
        int close = normalized.indexOf("\n" + FENCE, FENCE.length());
        if (close < 0) {
            throw new TaskParseException("Closing '---' frontmatter fence not found");
        }
        String yamlPart = normalized.substring(FENCE.length() + 1, close + 1);
        int bodyStart = normalized.indexOf('\n', close + 1);
        String notes = bodyStart < 0 ? "" : normalized.substring(bodyStart + 1);

        Map<String, Object> data = loadYaml(yamlPart);
        String title = optionalString(data, "title", id);
        TaskStatus status = parseStatus(optionalString(data, "status", "active"));
        String remote = optionalString(data, "remote", null);

        return new Task(id, title, status, remote,
                parseTmux(section(data, "tmux")),
                parseTerminal(section(data, "terminal")),
                parseIntellij(data),
                parseBrowser(section(data, "browser")),
                parseClaude(section(data, "claude")),
                optionalString(data, "note", null),
                parseFolders(data),
                TaskTags.parse(data.get("tags")),
                parseStoredTabs(data),
                optionalString(data, "suspended", null),
                optionalString(data, "chat", null),
                notes,
                // [impl->dsn~pinned-tasks~2]
                Boolean.parseBoolean(String.valueOf(data.get("pinned"))),
                // [impl->dsn~auto-pr-category~3]
                Boolean.parseBoolean(String.valueOf(data.get(AUTO_PR_CLOSED))));
    }

    /// The `storedTabs:` list a complete-control suspend wrote — the URLs of
    /// the desktop's closed Firefox tabs, reopened on resume. Lenient like
    /// `folders`: not a list (or blank entries) reads as none.
    // [impl->dsn~complete-control-desktop~1]
    private static List<String> parseStoredTabs(Map<String, Object> data) {
        if (!(data.get("storedTabs") instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(java.util.Objects::nonNull).map(TaskFileParser::stringify)
                .filter(url -> !url.isBlank()).toList();
    }

    /// Replaces (or inserts) the `title:` line textually, preserving every
    /// other byte of the file — no YAML round-trip, comments survive.
    // [impl->dsn~task-rename-title~2]
    public static String withTitle(String content, String newTitle) {
        String titleLine = "title: " + yamlScalar(newTitle);
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        for (int i = 1; i < lines.length && !isFence(lines[i]); i++) {
            if (lines[i].startsWith("title:")) {
                List<String> out = new java.util.ArrayList<>(java.util.Arrays.asList(lines));
                out.subList(i, scalarEnd(lines, i) + 1).clear();
                out.add(i, titleLine);
                return String.join("\n", out);
            }
        }
        lines[0] = FENCE + "\n" + titleLine;
        return String.join("\n", lines);
    }

    /// Appends `text` as the body's last paragraph, under a `# Notes` heading
    /// added when the body has none — for text that must outlive its place in
    /// the frontmatter, such as a description replaced by a short title.
    // [impl->dsn~task-create-local-title~1]
    public static String withNotesAppended(String content, String text) {
        String paragraph = text.strip();
        if (paragraph.isEmpty()) {
            return content;
        }
        StringBuilder out = new StringBuilder(content.stripTrailing()).append("\n\n");
        if (content.lines().noneMatch(line -> line.strip().equals("# Notes"))) {
            out.append("# Notes\n\n");
        }
        return out.append(paragraph).append('\n').toString();
    }

    /// The last physical line (index into `lines`) of the scalar value whose
    /// key sits on `line`: a YAML scalar may continue over further indented
    /// lines, so a textual edit anchored on a key must skip the whole value —
    /// replacing only the key's line, or inserting right after it, lands
    /// inside the scalar and breaks the frontmatter.
    ///
    /// Two shapes, and the difference is what a blank line means. A **plain**
    /// scalar (no `|`/`>`, continuation lines merely indented; folded with
    /// spaces on parse) ends at the first blank line. A **block** scalar
    /// (`title: |-`, which is what the writer emits for any multi-line title —
    /// e.g. a description carrying an `[image: …]` marker after an empty line)
    /// keeps blank lines as content and runs until a line at the key's own
    /// indent. Ending a block scalar at its first blank line put the new key
    /// *inside* the title and orphaned everything below it.
    ///
    /// A block scalar's own trailing blank lines are not counted: the result
    /// is the last non-blank line, so an inserted key follows the value's
    /// content. Only `|+`/`>+` (keep) would read those as value, and nothing
    /// here writes them.
    // [impl->dsn~frontmatter-multiline-scalar~3]
    private static int scalarEnd(String[] lines, int line) {
        boolean block = isBlockScalarHeader(lines[line]);
        int end = line;
        for (int probe = line; probe + 1 < lines.length; probe++) {
            String next = lines[probe + 1];
            if (next.isBlank()) {
                if (!block) {
                    break;   // a blank line ends a plain scalar
                }
                continue;    // ...but is content inside a block scalar
            }
            if (!Character.isWhitespace(next.charAt(0))) {
                break;       // back at the key's indent: the value is over
            }
            end = probe + 1;
        }
        return end;
    }

    /// Whether `keyLine` opens a block scalar — its value is a `|`/`>`
    /// indicator (with any chomping/indent suffix: `|-`, `>-`, `|2`, `|+`).
    // [impl->dsn~frontmatter-multiline-scalar~3]
    private static boolean isBlockScalarHeader(String keyLine) {
        int colon = keyLine.indexOf(':');
        if (colon < 0) {
            return false;
        }
        String value = keyLine.substring(colon + 1).strip();
        return value.startsWith("|") || value.startsWith(">");
    }

    /// Content of a task file created for one pull request — the PR flow's
    /// skeleton and the auto category's (`dsn~auto-pr-category~3`): the PR
    /// title, `status: active`, the inherited `remote` when there is one, and
    /// the PR as the task's single `browser.urls` entry. `provenance` is the
    /// one line written under `# Notes` in intro mode; null in compact mode
    /// leaves the body empty (`dsn~skeleton-hints~4`).
    // [impl->dsn~task-from-pr~6]
    // [impl->dsn~auto-pr-category~3]
    // [impl->dsn~skeleton-hints~4]
    public static String prTaskContent(String title, @Nullable String remote, String url,
            @Nullable String provenance) {
        return """
                ---
                title: %s
                status: active
                %sbrowser:
                  urls:
                    - %s
                ---

                # Notes

                """.formatted(yamlScalar(title),
                        remote == null ? "" : "remote: " + yamlScalar(remote) + "\n",
                        yamlScalar(url))
                + (provenance == null ? "" : provenance + "\n");
    }

    /// Content of a freshly created task file: minimal frontmatter (title,
    /// status) plus a Notes body — the user completes the optional action
    /// sections in the editor lane; TEMPLATE.md documents them all.
    // [impl->dsn~task-create-ui~15]
    public static String newTaskContent(String title) {
        return newTaskContent(title, null, null, true);
    }

    /// As [#newTaskContent(String)], but pre-fills the inherited group
    /// defaults (https://github.com/contextswitcher/contextswitcher-private/issues/45). A non-null `remote` is written uncommented. The
    /// `workdir` is written uncommented too, but where it lands depends on the
    /// group's shape: a **remote** group treats it as the Claude session's
    /// `claude.cwd`; a **local** group (no `remote` — the lightweight, no-tmux
    /// category) treats it as `folder`, the local directory the switch action
    /// focuses/opens in Explorer. Absent keys keep their commented example
    /// line so the section stays optional.
    // [impl->dsn~group-config-apply~2]
    public static String newTaskContent(String title, @Nullable String remote, @Nullable String workdir) {
        return newTaskContent(title, remote, workdir, true);
    }

    /// As [#newTaskContent(String, String, String)], with an explicit hints
    /// switch: `hints` renders the onboarding skeleton with the commented
    /// example sections; without it (compact mode) the frontmatter carries
    /// only the real keys and the Notes body stays empty.
    // [impl->dsn~skeleton-hints~4]
    public static String newTaskContent(String title, @Nullable String remote,
            @Nullable String workdir, boolean hints) {
        boolean local = remote == null;
        if (!hints) {
            StringBuilder content = new StringBuilder("---\n")
                    .append("title: ").append(yamlScalar(title)).append('\n')
                    .append("status: active\n");
            if (remote != null) {
                content.append("remote: ").append(yamlScalar(remote)).append('\n');
            }
            if (workdir != null) {
                content.append(local
                        ? "folder: " + yamlScalar(workdir) + "\n"
                        : "claude:\n  cwd: " + yamlScalar(workdir) + "\n");
            }
            return content.append("---\n\n# Notes\n\n").toString();
        }
        String remoteLine = remote == null
                ? "# remote: devbox            # ssh alias or user@host"
                : "remote: " + yamlScalar(remote);
        // A local group's workdir is a local folder (Explorer focus/open); a
        // remote group's is the session cwd. Only the matching key is seeded.
        String folderLine = local && workdir != null
                ? "folder: " + yamlScalar(workdir)
                : "# folders: [C:\\Users\\me\\project]   # local folders to focus/open in Explorer";
        String claudeSection = !local && workdir != null
                ? "claude:\n  cwd: " + yamlScalar(workdir)
                        + "\n#   sessionId: ...          # enables claude --resume"
                : """
                # claude:
                #   cwd: /home/user/repos/project
                #   sessionId: ...          # enables claude --resume""";
        return """
                ---
                title: %1$s
                status: active
                # tags: [phone, jabref]     # filter the list by tag (colors set in settings.yaml)
                # Add the sections you need (remove the leading '# '):
                %2$s
                # tmux:
                #   session: "0"
                #   window: "@17"           # window id or name
                # terminal:
                #   tabTitle: my-wt-tab     # local Windows Terminal tab (pinned title)
                %4$s
                # intellij:                 # bare section is enough with a claude section
                #   projectPath: /home/user/repos/project
                # browser:                  # or use the row's Add-URL icon
                #   urls:
                #     - https://github.com/owner/repo/pull/1
                # chat: https://matrix.to/#/!room:matrix.org    # opens in the Element app
                %3$s
                ---

                # Notes

                """.formatted(yamlScalar(title), remoteLine, claudeSection, folderLine);
    }

    /// Content of a freshly created group `CONTEXTSWITCHER.md`. Both flavors
    /// carry every key: the `remote:` line real when a `remote` is passed,
    /// each other key a `# key: example` comment that is valid YAML once the
    /// `# ` is removed (one example tag). With `hints` the keys additionally
    /// carry their human-readable explanation lines; compact mode is the bare
    /// key lines. Neither flavor writes body prose — the file configures the
    /// group and is never listed as a task.
    // [impl->dsn~skeleton-hints~4]
    // [impl->dsn~group-config-create~9]
    public static String groupConfigSkeleton(String group, @Nullable String remote, boolean hints) {
        return groupConfigSkeleton(group, remote, null, hints);
    }

    /// As above, with the `desktop:` line real (uncommented, pre-filled with
    /// `desktop`) instead of the `# desktop: <group>` example — for a category
    /// created from the desktop-filter empty state, which already knows the
    /// desktop the category is for.
    // [impl->dsn~desktop-filter-empty-state~2]
    public static String groupConfigSkeleton(String group, @Nullable String remote,
            @Nullable String desktop, boolean hints) {
        String remoteLine = remote == null
                ? "# remote: koppor@devbox"
                : "remote: " + yamlScalar(remote);
        String desktopLine = desktop == null
                ? "# desktop: " + group
                : "desktop: " + yamlScalar(desktop);
        if (!hints) {
            return """
                    ---
                    # note: onenote:https://d.docs.live.net/…/Projects.one#Page&section-id={…}&page-id={…}&end
                    # chat: https://matrix.to/#/!room:matrix.org
                    %2$s
                    # workspacesRoot: /data/koppor/%1$s-workspaces
                    # mainCheckout: /data/koppor/%1$s-workspaces/%1$s
                    # repo: https://github.com/owner/%1$s
                    %3$s
                    # tags: [needs-review]
                    # auto: {query: "repo:owner/%1$s review-requested:@me", maxSloc: 50, deleteHours: 24}
                    # intellij: {projectPath: /data/koppor/%1$s}
                    # browser: {urls: [https://github.com/owner/%1$s]}
                    # folders: [/data/koppor/%1$s]
                    ---

                    # %1$s — group defaults
                    """.formatted(group, remoteLine, desktopLine);
        }
        return """
                ---
                # Link to the project's notes — group header right-click → "Open note".
                # For OneNote use the onenote:… link form (opens the desktop app);
                # the onedrive.live.com web URL would open the browser instead.
                # note: onenote:https://d.docs.live.net/…/Projects.one#Page&section-id={…}&page-id={…}&end
                # The project's Matrix room, opened on every switch into this category
                # (a task's own chat: wins). Paste a matrix.to permalink.
                # chat: https://matrix.to/#/!room:matrix.org
                # Defaults a new task in this folder inherits (its own frontmatter wins).
                %2$s
                # workspacesRoot: /data/koppor/%1$s-workspaces
                #   Where a task's Claude session starts; Claude creates and manages
                #   each task's own working directory under it. Set only this normally.
                # mainCheckout: /data/koppor/%1$s-workspaces/%1$s
                #   A permanent checkout of repo (not a per-task worktree).
                #   "Show diff" falls back to it when a task's own worktree is
                #   already deleted, showing the commit the session published.
                # repo: https://github.com/owner/%1$s
                #   The group's GitHub repository, opened by the header's repository
                #   icon and named in a live task's bootstrap prompt. If unset, the
                #   prompt omits it (the clone knows its origin).
                %4$s
                #   Name of the virtual desktop this category lives on (as shown in
                #   the Windows Task View). The header's ▶ button focuses it — nothing
                #   else. Omit for no desktop button. The nested form
                #   desktop: {name: %1$s, completeControl: true}
                #   additionally stores and closes every Firefox window on that
                #   desktop when a task of this category is suspended, and reopens
                #   the stored tabs on resume — a clean desktop in between.
                # tags: [needs-review]
                #   Tags inherited by every task in this group (as if each task
                #   carried them); colors are configured in settings.yaml.
                # autoDelete: true
                #   Lets the automatisms delete this category's tasks unattended: a
                #   task whose PRs are all merged, an auto category's expired task,
                #   a closed plain shell. Off by default — they are suspended and
                #   kept instead. A pinned task is never deleted automatically.
                # auto:
                #   query: "repo:owner/%1$s review-requested:@me"
                #   maxSloc: 50
                #   deleteHours: 24
                #   Makes this an auto category: every open pull request the
                #   GitHub search matches becomes a task here, and its task is
                #   suspended once the PR leaves the search and deleted
                #   deleteHours later (with autoDelete: true). maxSloc skips a PR changing more lines
                #   than that outside its tests (0 = no size limit); a task you
                #   started a session in is never suspended or deleted.
                # Switch-action defaults for every task in this category. A task
                # that carries the same section wins with it as a whole.
                # intellij:
                #   projectPath: /data/koppor/%1$s
                # browser:
                #   urls:
                #     - https://github.com/owner/%1$s
                # folders: [/data/koppor/%1$s]
                #   Local folders opened in the file manager on switch.
                # Any key can be scoped to one machine by suffixing it with this
                # computer's name (this machine: %3$s) or its OS — the most
                # specific suffix wins, an unsuffixed key is the fallback:
                # workspacesRoot-%3$s: /data/koppor/%1$s-workspaces
                # folders-windows: [C:\\git\\%1$s]
                ---

                # %1$s — group defaults
                """.formatted(group, remoteLine, exampleHost(), desktopLine);
    }

    /// The host name the machine-suffix example in the intro skeleton uses:
    /// this computer's own, so the example line is ready to uncomment instead
    /// of having to be looked up. A placeholder when the host is unknown.
    // [impl->dsn~skeleton-hints~4]
    private static String exampleHost() {
        String host = hostToken();
        return host.isEmpty() ? "mylaptop" : host;
    }

    /// Reads a single top-level frontmatter scalar (e.g. `workspacesRoot`)
    /// from a task file, or null when the file is not fenced frontmatter, the
    /// YAML does not parse, or the key is absent. Used to resolve a
    /// remote-only task's working directory when auto-creating its window.
    // [impl->dsn~remote-window-choice~6]
    public @Nullable String frontmatterString(String content, String key) {
        String normalized = content.replace("\r\n", "\n");
        if (normalized.startsWith("﻿")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.startsWith(FENCE + "\n")) {
            return null;
        }
        int close = normalized.indexOf("\n" + FENCE, FENCE.length());
        if (close < 0) {
            return null;
        }
        String yamlPart = normalized.substring(FENCE.length() + 1, close + 1);
        try {
            return optionalString(loadYaml(yamlPart), key, null);
        } catch (TaskParseException e) {
            return null;
        }
    }

    /// The frontmatter's duplicate keys, as `key (line N)` with the line
    /// counted in the **file**, outermost mapping first — empty when the file
    /// has none, no frontmatter, or YAML that does not compose at all.
    ///
    /// YAML says nothing about duplicate keys, so SnakeYAML takes the last one
    /// and warns through `java.util.logging` — a channel this app bridges
    /// nowhere, so the warning reaches neither the log file nor the user, and
    /// it names no file anyway (`loadYaml` only ever sees the YAML text). Two
    /// `desktop:` lines in a `CONTEXTSWITCHER.md` therefore silently resolved
    /// to whichever came last (field report 2026-07-30).
    ///
    /// The check composes the node tree instead of loading it, because a
    /// loaded `Map` has already lost the duplicates — and walks nested
    /// mappings and sequences too, so a repeated key inside `tmux:` or
    /// `intellij:` is found as well.
    // [impl->dsn~frontmatter-duplicate-keys~1]
    public List<String> duplicateFrontmatterKeys(String content) {
        String yamlPart = frontmatterBlock(content);
        if (yamlPart == null) {
            return List.of();
        }
        List<String> duplicates = new ArrayList<>();
        try {
            collectDuplicates(new Yaml(new SafeConstructor(new LoaderOptions()))
                    .compose(new StringReader(yamlPart)), duplicates);
        } catch (YAMLException e) {
            // Frontmatter that does not even compose is somebody else's
            // report: a task file fails to parse with its own message, a
            // category config stays EMPTY on purpose.
            return List.of();
        }
        return List.copyOf(duplicates);
    }

    /// Why a `CONTEXTSWITCHER.md` frontmatter does not load, with the line
    /// counted in the **file** — or null when it loads, is empty, or there is
    /// no complete frontmatter to load. [#parseGroupConfig] answers
    /// [GroupConfig#EMPTY] for such a file on purpose, so this is the only
    /// place the reason survives (field report 2026-09-13: a category whose
    /// config broke vanished from the list without a word).
    // [impl->dsn~group-config-parse-error~1]
    public @Nullable String groupConfigError(String content) {
        String yamlPart = frontmatterBlock(content);
        if (yamlPart == null) {
            return null;
        }
        Object loaded;
        try {
            loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlPart);
        } catch (MarkedYAMLException e) {
            Mark mark = e.getProblemMark();
            return mark == null ? e.getMessage()
                    : "%s (line %d)".formatted(e.getProblem(), mark.getLine() + 2);
        } catch (YAMLException e) {
            return e.getMessage();
        }
        return loaded == null || loaded instanceof Map ? null : "Frontmatter must be a YAML mapping";
    }

    /// Repairs a OneNote "Copy Link to Page" clipboard pasted straight into a
    /// frontmatter: its two keyless lines become keys in place — the
    /// `onenote:` desktop URL `note:`, the `https://onedrive.live.com/…` web
    /// URL (bare, or pasted behind `note: `) `note-linux:`, since Linux has no
    /// OneNote app to take the desktop link (`dsn~machine-key-variants~1`). Only a frontmatter that does not load is touched, and the
    /// repair is returned only when the result loads — otherwise `content`
    /// comes back unchanged, so nothing is ever rewritten on a guess.
    // [impl->dsn~onenote-paste-repair~1]
    public String repairPastedOneNoteLink(String content) {
        if (groupConfigError(content) == null) {
            return content;
        }
        String[] lines = content.replace("\r\n", "\n").split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        boolean changed = false;
        for (int i = 1; i < lines.length && !isFence(lines[i]); i++) {
            if (!lines[i].startsWith("onenote:") || OneNoteLink.extract(lines[i]) == null) {
                continue;
            }
            lines[i] = "note: " + yamlScalar(lines[i].strip());
            changed = true;
            for (int j : new int[] {i - 1, i + 1}) {
                if (j > 0 && j < lines.length && PASTED_WEB_URL.matcher(lines[j]).matches()) {
                    lines[j] = "note-linux: " + yamlScalar(lines[j].replaceFirst("^note:\\s*", ""));
                }
            }
        }
        String repaired = String.join("\n", lines);
        return changed && groupConfigError(repaired) == null ? repaired : content;
    }

    /// The web half of a OneNote clipboard: a keyless `http(s)` line, or one
    /// pasted behind an existing `note: `.
    private static final Pattern PASTED_WEB_URL = Pattern.compile("(note:\\s*)?https?://\\S*");

    /// Adds every repeated mapping key below `node`, depth-first. The `+ 2`
    /// turns SnakeYAML's 0-based line inside the YAML block into the file's
    /// 1-based line: the block starts on the line after the opening fence.
    private static void collectDuplicates(@Nullable Node node, List<String> out) {
        switch (node) {
            case MappingNode mapping -> {
                Set<String> seen = new HashSet<>();
                for (NodeTuple tuple : mapping.getValue()) {
                    if (tuple.getKeyNode() instanceof ScalarNode key && !seen.add(key.getValue())) {
                        out.add("%s (line %d)".formatted(
                                key.getValue(), key.getStartMark().getLine() + 2));
                    }
                    collectDuplicates(tuple.getValueNode(), out);
                }
            }
            case SequenceNode sequence -> sequence.getValue()
                    .forEach(child -> collectDuplicates(child, out));
            case null, default -> { }
        }
    }

    /// The YAML text between the `---` fences, or null when the content has no
    /// complete frontmatter block.
    private static @Nullable String frontmatterBlock(String content) {
        String normalized = content.replace("\r\n", "\n");
        if (normalized.startsWith("﻿")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.startsWith(FENCE + "\n")) {
            return null;
        }
        int close = normalized.indexOf("\n" + FENCE, FENCE.length());
        if (close < 0) {
            return null;
        }
        return normalized.substring(FENCE.length() + 1, close + 1);
    }

    /// Reads a group's `CONTEXTSWITCHER.md` defaults (https://github.com/contextswitcher/contextswitcher-private/issues/45). Tolerant:
    /// a missing, empty, or invalid frontmatter yields [GroupConfig#EMPTY]
    /// rather than an error — the file is user-edited configuration whose
    /// half-finished states must not surface as a task parse failure.
    // [impl->dsn~group-config-apply~2]
    public GroupConfig parseGroupConfig(String content) {
        String normalized = content.replace("\r\n", "\n");
        if (normalized.startsWith("﻿")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.startsWith(FENCE + "\n")) {
            return GroupConfig.EMPTY;
        }
        int close = normalized.indexOf("\n" + FENCE, FENCE.length());
        if (close < 0) {
            return GroupConfig.EMPTY;
        }
        String yamlPart = normalized.substring(FENCE.length() + 1, close + 1);
        Map<String, Object> data;
        try {
            data = loadYaml(yamlPart);
        } catch (TaskParseException e) {
            return GroupConfig.EMPTY;
        }
        // The action defaults are parsed leniently like the rest of the file:
        // a half-edited `browser`/`intellij` section yields no default rather
        // than turning the whole category config into an error.
        // [impl->dsn~category-action-defaults~1]
        Task.@Nullable IntellijConfig intellij;
        Task.@Nullable BrowserConfig browser;
        try {
            intellij = parseIntellij(data);
            browser = parseBrowser(section(data, "browser"));
        } catch (TaskParseException e) {
            intellij = null;
            browser = null;
        }
        // `desktop:` comes in two forms: the plain scalar (the name), and the
        // nested mapping `desktop: {name: …, completeControl: true}`.
        // [impl->dsn~complete-control-desktop~1]
        @Nullable String desktop;
        boolean completeControl = false;
        if (data.get("desktop") instanceof Map<?, ?> nested) {
            Object name = nested.get("name");
            desktop = name == null || stringify(name).isBlank() ? null : stringify(name);
            completeControl = Boolean.parseBoolean(String.valueOf(nested.get("completeControl")));
        } else {
            desktop = optionalString(data, "desktop", null);
        }
        return new GroupConfig(
                optionalString(data, "remote", null),
                optionalString(data, "workspacesRoot", null),
                optionalString(data, "workdir", null),
                optionalString(data, "repo", null),
                desktop,
                optionalString(data, "mainCheckout", null),
                optionalString(data, "baseBranch", null),
                intellij,
                browser,
                parseFolders(data),
                TaskTags.parse(data.get("tags")),
                completeControl,
                // [impl->dsn~pinned-categories~1]
                Boolean.parseBoolean(String.valueOf(data.get("pinned"))),
                // [impl->dsn~auto-pr-category~3]
                parseAutoPr(data.get("auto")),
                // [impl->dsn~auto-delete-opt-in~1]
                Boolean.parseBoolean(String.valueOf(data.get("autoDelete"))));
    }

    /// The `auto:` section of an auto category, or null when the category
    /// names no query — the one key that turns the reconcile on, so a
    /// half-written section (a `maxSloc` and no `query`) leaves the category
    /// hand-filled rather than reconciled against an empty search.
    /// The numbers are read leniently: the configuration form writes them as
    /// strings, and an unreadable one falls back to its default rather than
    /// failing the whole category config.
    // [impl->dsn~auto-pr-category~3]
    private GroupConfig.@Nullable AutoPr parseAutoPr(@Nullable Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> auto = (Map<String, Object>) raw;
        String query = optionalString(auto, "query", null);
        if (query == null || query.isBlank()) {
            return null;
        }
        return new GroupConfig.AutoPr(query.strip(), optionalInt(auto, "maxSloc", 0),
                optionalInt(auto, "deleteHours", GroupConfig.AutoPr.DEFAULT_DELETE_HOURS));
    }

    /// A frontmatter number, whether YAML typed it as an int or the
    /// configuration form wrote it as a string; `fallback` for a missing,
    /// blank or unreadable value.
    // [impl->dsn~auto-pr-category~3]
    private static int optionalInt(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(stringify(value).strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /// The local folders to open on switch: `folders` (a list) or the
    /// single-directory `folder` scalar the task files predating it use — the
    /// same key pair in a task file and in a category's `CONTEXTSWITCHER.md`.
    /// Blank entries are dropped so a half-typed list item opens nothing.
    // [impl->dsn~explorer-folder-focus~3]
    private static List<String> parseFolders(Map<String, Object> data) {
        Object folders = data.get("folders");
        if (folders instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull).map(TaskFileParser::stringify)
                    .filter(folder -> !folder.isBlank()).toList();
        }
        Object folder = folders != null ? folders : data.get("folder");
        if (folder == null || stringify(folder).isBlank()) {
            return List.of();
        }
        return List.of(stringify(folder));
    }

    /// Derives the relative task file path (without `.md`) from the Add-task
    /// dialog input: an optional `folder/` prefix becomes the project group
    /// (one level, matching the repository's scan depth), each part slugged —
    /// lowercased, whitespace to `-`, anything but `a-z0-9._-` dropped.
    // [impl->dsn~task-create-ui~15]
    public static String newTaskFileName(String input) {
        int slash = input.indexOf('/');
        String folder = slash < 0 ? "" : slug(input.substring(0, slash));
        String name = slug(slash < 0 ? input : input.substring(slash + 1));
        if (name.isEmpty()) {
            name = "task";
        }
        return folder.isEmpty() ? name : folder + "/" + name;
    }

    /// The task file base name (relative, no `.md`) adopting a published
    /// title: the group folder and the leading ISO date of `taskId` survive
    /// (a live task starts dated, `dsn~task-create-live~8`; `fallbackDate`
    /// fills in when the old name has none), the rest becomes the slugged
    /// title. Null when the name would not change.
    // [impl->dsn~claude-title-sync~3]
    public static @Nullable String adoptedFileName(String taskId, String title,
            LocalDate fallbackDate) {
        int slash = taskId.lastIndexOf('/');
        String group = slash < 0 ? "" : taskId.substring(0, slash + 1);
        String old = slash < 0 ? taskId : taskId.substring(slash + 1);
        String date = old.matches("\\d{4}-\\d{2}-\\d{2}(-.*)?") ? old.substring(0, 10)
                : fallbackDate.toString();
        String name = slug(title);
        String adopted = group + (name.isEmpty() ? date : date + "-" + name);
        return adopted.equals(taskId) ? null : adopted;
    }

    /// File/folder-name slug: lowercased, whitespace to `-`, anything but
    /// `a-z0-9._-` dropped, capped at [#MAX_SLUG_LENGTH] chars (cut at a `-`
    /// boundary) — a pasted multi-sentence task description must still yield
    /// a Windows-safe path (MAX_PATH). Also used for new category (group
    /// folder) names.
    // [impl->dsn~category-create-ui~2]
    // [impl->dsn~task-create-ui~15]
    public static String slug(String value) {
        String slug = value.strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9._-]", "")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[-.]+|[-.]+$", "");
        if (slug.length() <= MAX_SLUG_LENGTH) {
            return slug;
        }
        int cut = slug.lastIndexOf('-', MAX_SLUG_LENGTH);
        return slug.substring(0, cut > MAX_SLUG_LENGTH / 2 ? cut : MAX_SLUG_LENGTH)
                .replaceAll("[-.]+$", "");
    }

    /// The repository name of a GitHub (or any `https://host/owner/name`)
    /// URL, slugged as a category name: the last path segment, a `.git`
    /// suffix dropped. Null when the text is no http(s) URL or has no such
    /// segment — the "Add category from URL…" dialog refuses those.
    // [impl->dsn~category-from-url~4]
    public static @Nullable String repoName(String url) {
        Matcher m = REPO_URL.matcher(url.strip());
        if (!m.matches()) {
            return null;
        }
        String name = slug(m.group(1));
        return name.isEmpty() ? null : name;
    }

    private static final Pattern REPO_URL =
            Pattern.compile("^https?://[^/]+/(?:[^/]+/)*([^/?#]+?)(?:\\.git)?/?(?:[?#].*)?$");

    /// A `CONTEXTSWITCHER.md` for a category created from a repository URL:
    /// the skeleton's example lines made real — `remote`, the
    /// `workspacesRoot` the setup task creates on the remote, the
    /// `mainCheckout` (the primary clone inside it, named like the group)
    /// and `repo`, plus a `desktop:` line when a `desktop` is
    /// passed (the active-desktop filter's, `dsn~category-create-ui~2`). A
    /// null `remote` writes no `remote:` line — a local category
    /// (`dsn~task-create-local~4`), the setup wizard's local choice.
    // [impl->dsn~category-from-url~4]
    // [impl->dsn~setup-wizard~8]
    public static String groupConfigForRepo(String group, String url, @Nullable String remote,
            String workspacesRoot, @Nullable String desktop) {
        return """
                ---
                %sworkspacesRoot: %s
                mainCheckout: %s
                repo: %s
                %s---

                # %s — group defaults
                """.formatted(remote == null ? "" : "remote: " + yamlScalar(remote) + "\n",
                yamlScalar(workspacesRoot),
                yamlScalar(workspacesRoot + "/" + group), yamlScalar(url.strip()),
                desktop == null ? "" : "desktop: " + yamlScalar(desktop) + "\n", group);
    }

    /// Longest slug emitted by [#slug]: keeps the full task file path well
    /// under the Windows 260-char MAX_PATH even with a group folder prefix.
    private static final int MAX_SLUG_LENGTH = 60;

    /// Replaces (or inserts) the frontmatter `status:` line textually — no
    /// YAML round-trip, so comments and formatting survive. A missing line is
    /// inserted right after `title:` (or at the top of the frontmatter when
    /// there is no title either). Suspending also records **when** on a
    /// `suspended:` line right below the status (`dsn~suspended-timestamp~1`);
    /// every other status removes that line again.
    // [impl->dsn~task-status-cycle~2]
    // [impl->dsn~suspended-timestamp~1]
    public static String withStatus(String content, TaskStatus status) {
        return withStatus(content, status, LocalDateTime.now());
    }

    /// As [#withStatus(String, TaskStatus)] with an explicit clock.
    // [impl->dsn~suspended-timestamp~1]
    public static String withStatus(String content, TaskStatus status, LocalDateTime now) {
        // `autoPrClosed:` belongs to the suspend that wrote it, so any status
        // write drops it — including a re-suspend, which is then the user's
        // (a dismissal) until the reconcile marks its own again.
        // [impl->dsn~auto-pr-category~3]
        String statusLine = "status: " + status.yaml();
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        List<String> out = new ArrayList<>(Arrays.asList(lines));
        int statusLineAt = -1;
        int titleLine = -1;
        // `isFence`, not `FENCE.equals(line.strip())`: stripping the *leading*
        // space too makes an indented `---` inside a block scalar look like the
        // closing fence, and a task description pasted from a chat is full of
        // them. The scan then stopped at the first one, never saw the real
        // `status:` below it, and *inserted* a second one — YAML keeps the
        // last of duplicate keys, so the status the user set was the one that
        // lost. Field report 2026-09-12: "Duplicate frontmatter keys in
        // …-do-we-have-noted-down-our-decision-drivers-for.md: status (line
        // 28)", on a task whose suspend had silently not taken.
        for (int i = 1; i < out.size() && !isFence(out.get(i)); i++) {
            if (out.get(i).startsWith("suspended:") || out.get(i).startsWith(AUTO_PR_CLOSED + ":")) {
                out.remove(i--);
            } else if (out.get(i).startsWith("status:")) {
                out.set(i, statusLine);
                statusLineAt = i;
            } else if (out.get(i).startsWith("title:") && titleLine < 0) {
                titleLine = i;
            }
        }
        if (statusLineAt < 0) {
            statusLineAt = titleLine >= 0 ? scalarEnd(out.toArray(String[]::new), titleLine) + 1 : 1;
            out.add(statusLineAt, statusLine);
        }
        if (status == TaskStatus.SUSPENDED) {
            out.add(statusLineAt + 1, "suspended: \"" + SUSPENDED_AT.format(now) + "\"");
        }
        return String.join("\n", out);
    }

    /// The frontmatter key marking a suspend an auto category made because the
    /// task's pull request ended — the only suspend it may undo
    /// (`dsn~auto-pr-category~3`). Written by the reconcile, cleared by every
    /// [#withStatus]; not offered by the configuration form, like the other
    /// keys the app writes for itself.
    // [impl->dsn~auto-pr-category~3]
    public static final String AUTO_PR_CLOSED = "autoPrClosed";

    /// The `suspended:` timestamp form — minute precision, local time, quoted
    /// so YAML reads it as a plain string (an unquoted date would load as a
    /// `Date` and print back in a different shape).
    // [impl->dsn~suspended-timestamp~1]
    static final DateTimeFormatter SUSPENDED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /// The `note:` URL from a group's `CONTEXTSWITCHER.md` frontmatter, or
    /// null when absent, blank, or unparseable. For OneNote this should be
    /// the `onenote:…` link form, which opens the desktop app.
    // [impl->dsn~group-note-open~5]
    public static @Nullable String groupNote(String content) {
        return groupString(content, "note");
    }

    /// The `chat:` URL from a group's `CONTEXTSWITCHER.md` frontmatter — the
    /// project's Matrix room, used by a task that configures none of its own.
    // [impl->dsn~chat-focus-action~2]
    public static @Nullable String groupChat(String content) {
        return groupString(content, "chat");
    }

    /// A top-level string key of a group's `CONTEXTSWITCHER.md` frontmatter,
    /// or null when the file has no frontmatter, no such key, a blank one, or
    /// does not parse — a half-edited config must not break the group menu.
    private static @Nullable String groupString(String content, String key) {
        String normalized = content.replace("\r\n", "\n");
        if (!normalized.startsWith(FENCE + "\n")) {
            return null;
        }
        int close = normalized.indexOf("\n" + FENCE, FENCE.length());
        if (close < 0) {
            return null;
        }
        try {
            Object data = new Yaml(new SafeConstructor(new LoaderOptions()))
                    .load(normalized.substring(FENCE.length() + 1, close + 1));
            // Folded like every other reader, so `note-linux:` wins on Linux.
            // [impl->dsn~machine-key-variants~1]
            if (data instanceof Map<?, ?> map
                    && applyMachineVariants(map, MACHINE_TOKENS).get(key) instanceof String value
                    && !value.isBlank()) {
                return value.strip();
            }
        } catch (YAMLException e) {
            // A half-edited config file must not break the group menu.
        }
        return null;
    }

    /// Replaces the frontmatter `window:` line textually (same comment-
    /// preserving approach as [#withTitle]) after a window was resurrected.
    /// When the section has no `window:` line — the state a suspended task is
    /// left in ([#withoutTmuxWindow]) — the line is inserted after `session:`.
    // [impl->dsn~tmux-resurrect~7]
    public static String withTmuxWindow(String content, String newWindowId, String note) {
        String windowLine = "window: \"%s\"   # %s".formatted(newWindowId, note);
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        int sessionLine = -1;
        for (int i = 1; i < lines.length && !isFence(lines[i]); i++) {
            if (lines[i].stripLeading().startsWith("window:")) {
                int indent = lines[i].length() - lines[i].stripLeading().length();
                lines[i] = " ".repeat(indent) + windowLine;
                return String.join("\n", lines);
            }
            if (lines[i].stripLeading().startsWith("session:")) {
                sessionLine = i;
            }
        }
        if (sessionLine >= 0) {
            int indent = lines[sessionLine].length() - lines[sessionLine].stripLeading().length();
            lines[sessionLine] = lines[sessionLine] + "\n" + " ".repeat(indent) + windowLine;
            return String.join("\n", lines);
        }
        return content;
    }

    /// Inserts a fresh, uncommented `tmux:` section (`session` + `window`)
    /// just before the closing frontmatter fence, for a remote-only task that
    /// had no live tmux config (the auto-create-on-play path). A commented
    /// template block (all `#`) is left in place — [#withTmuxWindow] cannot
    /// touch it, so we add real keys instead. No-op when the content is not
    /// fenced frontmatter.
    // [impl->dsn~remote-window-choice~6]
    public static String withTmuxSection(String content, String session, String windowId, String note) {
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        int closing = -1;
        for (int i = 1; i < lines.length; i++) {
            if (isFence(lines[i])) {
                closing = i;
                break;
            }
        }
        if (closing < 0) {
            return content;
        }
        String block = "tmux:\n  session: \"%s\"\n  window: \"%s\"   # %s"
                .formatted(session, windowId, note);
        List<String> out = new ArrayList<>(Arrays.asList(lines));
        out.add(closing, block);
        return String.join("\n", out);
    }

    /// Inserts a `claude:` section carrying `cwd` just before the closing
    /// frontmatter fence, for the Claude choice of the remote-only
    /// auto-create popup. No-op when a live `claude:` section already exists
    /// (a commented template block does not count) or the content is not
    /// fenced frontmatter — so a real session's recorded context is never
    /// overwritten.
    // [impl->dsn~remote-window-choice~6]
    public static String withClaudeSection(String content, String cwd) {
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        int closing = -1;
        for (int i = 1; i < lines.length; i++) {
            if (isFence(lines[i])) {
                closing = i;
                break;
            }
            if (lines[i].stripLeading().startsWith("claude:")) {
                return content;
            }
        }
        if (closing < 0) {
            return content;
        }
        String block = "claude:\n  cwd: " + yamlScalar(cwd);
        List<String> out = new ArrayList<>(Arrays.asList(lines));
        out.add(closing, block);
        return String.join("\n", out);
    }

    /// Removes the frontmatter `window:` line textually when a task is
    /// suspended — the killed window's id is dead and must not linger in the
    /// file; resume's resurrect inserts the fresh id via [#withTmuxWindow].
    // [impl->dsn~task-suspend~6]
    public static String withoutTmuxWindow(String content) {
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        for (int i = 1; i < lines.length && !isFence(lines[i]); i++) {
            if (lines[i].stripLeading().startsWith("window:")) {
                StringBuilder joined = new StringBuilder();
                for (int j = 0; j < lines.length; j++) {
                    if (j == i) {
                        continue;
                    }
                    if (!joined.isEmpty()) {
                        joined.append('\n');
                    }
                    joined.append(lines[j]);
                }
                return joined.toString();
            }
        }
        return content;
    }

    /// Sets (or replaces) the top-level `storedTabs:` list textually — the
    /// URLs of the Firefox tabs a complete-control suspend closed, written
    /// right before their windows go so resume can reopen them. Inserted just
    /// before the closing fence; an empty list removes the key. No-op when the
    /// content is not fenced frontmatter.
    // [impl->dsn~complete-control-desktop~1]
    public static String withStoredTabs(String content, List<String> urls) {
        String cleared = withoutStoredTabs(content);
        if (urls.isEmpty()) {
            return cleared;
        }
        String normalized = cleared.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        int closing = -1;
        for (int i = 1; i < lines.length; i++) {
            if (isFence(lines[i])) {
                closing = i;
                break;
            }
        }
        if (closing < 0) {
            return content;
        }
        StringBuilder block = new StringBuilder("storedTabs:");
        for (String url : urls) {
            block.append("\n  - ").append(yamlScalar(url));
        }
        List<String> out = new ArrayList<>(Arrays.asList(lines));
        out.add(closing, block.toString());
        return String.join("\n", out);
    }

    /// Removes the top-level `storedTabs:` key and its indented value lines —
    /// what a resume does once the stored tabs are reopened. No-op when the
    /// key is absent or the content is not fenced frontmatter.
    // [impl->dsn~complete-control-desktop~1]
    public static String withoutStoredTabs(String content) {
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        for (int i = 1; i < lines.length && !isFence(lines[i]); i++) {
            if (!lines[i].startsWith("storedTabs:")) {
                continue;
            }
            int end = i + 1;
            while (end < lines.length && !isFence(lines[end])
                    && (lines[end].isBlank() || lines[end].startsWith(" ") || lines[end].startsWith("\t"))) {
                end++;
            }
            List<String> out = new ArrayList<>(Arrays.asList(lines));
            out.subList(i, end).clear();
            return String.join("\n", out);
        }
        return content;
    }

    /// Sets (or inserts) the frontmatter's top-level `note:` line textually
    /// (comment-preserving) — the task's note link, opened via a row icon.
    /// A missing line is inserted after `title:` (else at the fence top).
    // [impl->dsn~task-add-link~3]
    public static String withNote(String content, String url) {
        return withScalar(content, "note", url);
    }

    /// Sets (or inserts) a top-level scalar `key: value` line in the
    /// frontmatter textually (comment-preserving, no YAML round-trip) — the
    /// generic form of [#withNote], also used to record a category's guessed
    /// `repo`. A missing line is inserted after `title:` (else at the fence
    /// top); content without an opening fence is returned unchanged.
    // [impl->dsn~task-add-link~3]
    public static String withScalar(String content, String key, String value) {
        String keyLine = key + ": " + yamlScalar(value);
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        int titleLine = -1;
        for (int i = 1; i < lines.length && !isFence(lines[i]); i++) {
            if (lines[i].startsWith(key + ":")) {
                lines[i] = keyLine;
                return String.join("\n", lines);
            }
            if (lines[i].startsWith("title:")) {
                titleLine = i;
            }
        }
        if (titleLine >= 0) {
            int end = scalarEnd(lines, titleLine);
            lines[end] = lines[end] + "\n" + keyLine;
        } else {
            lines[0] = FENCE + "\n" + keyLine;
        }
        return String.join("\n", lines);
    }

    /// Inserts a bare top-level `intellij:` line into the frontmatter when the
    /// task has no (uncommented) `intellij:` section yet — enough to enable the
    /// IntelliJ switch action, whose `projectPath` falls back to the Claude
    /// workspace/cwd (`Task.intellijProjectPath()`). A no-op when the section
    /// already exists (an explicit config is never clobbered) or when there is
    /// no frontmatter fence. The line is added just before the closing fence;
    /// comments and formatting survive (no YAML round-trip). A commented
    /// `# intellij:` example does not count as existing. Mirrors [#addBrowserUrl].
    // [impl->dsn~open-in-intellij~3]
    public static String withIntellij(String content) {
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        int close = -1;
        for (int i = 1; i < lines.length; i++) {
            if (isFence(lines[i])) {
                close = i;
                break;
            }
            if (lines[i].startsWith("intellij:")) {
                return content;
            }
        }
        if (close < 0) {
            return content;
        }
        List<String> out = new java.util.ArrayList<>(java.util.Arrays.asList(lines));
        out.add(close, "intellij:");
        return String.join("\n", out);
    }

    /// Sets `intellij.projectPath` to `projectPath` textually — for a task with
    /// no Claude session to derive the path from. Creates the whole `intellij:`
    /// section (with the child) when absent; adds the `projectPath:` child under
    /// an existing bare `intellij:`; replaces an existing `projectPath:` child.
    /// Comment-preserving (no YAML round-trip); a no-op when there is no
    /// frontmatter fence. Companion to the bare [#withIntellij].
    // [impl->dsn~open-in-intellij~3]
    public static String withIntellijProjectPath(String content, String projectPath) {
        String childLine = "  projectPath: " + yamlScalar(projectPath);
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        int intellijLine = -1;
        int close = -1;
        for (int i = 1; i < lines.length; i++) {
            if (isFence(lines[i])) {
                close = i;
                break;
            }
            if (lines[i].startsWith("intellij:")) {
                intellijLine = i;
            }
        }
        if (close < 0) {
            return content;
        }
        List<String> out = new java.util.ArrayList<>(java.util.Arrays.asList(lines));
        if (intellijLine < 0) {
            out.add(close, "intellij:\n" + childLine);
            return String.join("\n", out);
        }
        // Existing section: replace a `projectPath:` child within it (up to the
        // next top-level key or the closing fence), else insert one right after.
        for (int i = intellijLine + 1; i < close; i++) {
            String stripped = lines[i].stripLeading();
            int indent = lines[i].length() - stripped.length();
            if (indent == 0 && !stripped.isBlank()) {
                break;
            }
            if (stripped.startsWith("projectPath:")) {
                out.set(i, childLine);
                return String.join("\n", out);
            }
        }
        out.add(intellijLine + 1, childLine);
        return String.join("\n", out);
    }

    /// Sets `claude.sessionId` to `sessionId` textually — the sync's backfill
    /// for a task whose window published `@cs_session_id` after the task file
    /// was created. Replaces an existing `sessionId:` child, else inserts one
    /// right after the `claude:` line. Comment-preserving (no YAML
    /// round-trip); a no-op without a `claude:` section (a `sessionId` without
    /// `cwd` would not parse) or without a frontmatter fence. Mirrors
    /// [#withIntellijProjectPath].
    // [impl->dsn~tmux-sync~7]
    public static String withClaudeSessionId(String content, String sessionId) {
        return withClaudeChild(content, "sessionId", sessionId);
    }

    /// Sets `claude.workspace` to `workspace` textually — the sync's refresh
    /// for a window whose `@cs_workspace` differs from what the task records
    /// (a live task publishes it only after its file was written). Same rules
    /// as [#withClaudeSessionId]; the start `cwd` is left alone.
    // [impl->dsn~claude-workspace-capture~2]
    public static String withClaudeWorkspace(String content, String workspace) {
        return withClaudeChild(content, "workspace", workspace);
    }

    /// Sets `claude.commit` to `commit` textually — the sync's refresh for a
    /// window whose `@cs_commit` differs from what the task records. Same rules
    /// as [#withClaudeSessionId]. This is the only trace of the work that
    /// survives Claude deleting its own worktree, so "Show diff" can still
    /// reach the change afterwards.
    // [impl->dsn~diff-after-worktree-removal~1]
    public static String withClaudeCommit(String content, String commit) {
        return withClaudeChild(content, "commit", commit);
    }

    private static String withClaudeChild(String content, String key, String value) {
        String childLine = "  " + key + ": " + yamlScalar(value);
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        int claudeLine = -1;
        int close = -1;
        for (int i = 1; i < lines.length; i++) {
            if (isFence(lines[i])) {
                close = i;
                break;
            }
            if (lines[i].startsWith("claude:")) {
                claudeLine = i;
            }
        }
        if (close < 0 || claudeLine < 0) {
            return content;
        }
        List<String> out = new java.util.ArrayList<>(java.util.Arrays.asList(lines));
        // Replace the child within the section (up to the next top-level key
        // or the closing fence), else insert one right after.
        for (int i = claudeLine + 1; i < close; i++) {
            String stripped = lines[i].stripLeading();
            int indent = lines[i].length() - stripped.length();
            if (indent == 0 && !stripped.isBlank()) {
                break;
            }
            if (stripped.startsWith(key + ":")) {
                out.set(i, childLine);
                return String.join("\n", out);
            }
        }
        out.add(claudeLine + 1, childLine);
        return String.join("\n", out);
    }

    /// Sets (or inserts) the frontmatter's top-level `tags:` line textually
    /// (comment-preserving), as a flow list (`tags: [phone, jabref]`, each name
    /// serialized by SnakeYAML). An empty list removes the line — an untagged
    /// task carries no `tags:` key. A missing line is inserted after `title:`
    /// (else at the fence top). Mirrors [#withNote].
    // [impl->dsn~task-tag-model~2]
    public static String withTags(String content, List<String> tags) {
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        // Locate an existing top-level `tags:` line (and, for insertion, title).
        int tagsLine = -1;
        int titleLine = -1;
        for (int i = 1; i < lines.length && !isFence(lines[i]); i++) {
            if (lines[i].startsWith("tags:")) {
                tagsLine = i;
            } else if (lines[i].startsWith("title:")) {
                titleLine = i;
            }
        }
        if (tags.isEmpty()) {
            if (tagsLine < 0) {
                return content;
            }
            List<String> out = new java.util.ArrayList<>(java.util.Arrays.asList(lines));
            out.remove(tagsLine);
            return String.join("\n", out);
        }
        String tagsLineText = "tags: [" + tags.stream().map(TaskFileParser::yamlScalar)
                .collect(java.util.stream.Collectors.joining(", ")) + "]";
        if (tagsLine >= 0) {
            lines[tagsLine] = tagsLineText;
        } else if (titleLine >= 0) {
            int end = scalarEnd(lines, titleLine);
            lines[end] = lines[end] + "\n" + tagsLineText;
        } else {
            lines[0] = FENCE + "\n" + tagsLineText;
        }
        return String.join("\n", lines);
    }

    /// Sets (or removes) the frontmatter's top-level `pinned:` line textually
    /// (comment-preserving) — the "show me first" flag of a task or a category. Pinning off
    /// removes the line rather than writing `pinned: false`, so an unpinned
    /// task or category carries no key. A missing line is inserted after
    /// `title:` (a `CONTEXTSWITCHER.md` has none, so there it lands at the
    /// fence top). Mirrors [#withTags]; used for task files and category
    /// configs alike — the key means the same in each.
    // [impl->dsn~pinned-categories~1]
    // [impl->dsn~pinned-tasks~2]
    public static String withPinned(String content, boolean pinned) {
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        int pinnedLine = -1;
        int titleLine = -1;
        for (int i = 1; i < lines.length && !isFence(lines[i]); i++) {
            if (lines[i].startsWith("pinned:")) {
                pinnedLine = i;
            } else if (lines[i].startsWith("title:")) {
                titleLine = i;
            }
        }
        if (!pinned) {
            if (pinnedLine < 0) {
                return content;
            }
            List<String> out = new java.util.ArrayList<>(java.util.Arrays.asList(lines));
            out.remove(pinnedLine);
            return String.join("\n", out);
        }
        if (pinnedLine >= 0) {
            lines[pinnedLine] = "pinned: true";
        } else if (titleLine >= 0) {
            int end = scalarEnd(lines, titleLine);
            lines[end] = lines[end] + "\npinned: true";
        } else {
            lines[0] = FENCE + "\npinned: true";
        }
        return String.join("\n", lines);
    }

    /// Adds `url` (with an optional short `title`) to the frontmatter's
    /// `browser.urls` list textually (comment-preserving). A titled entry
    /// uses the `{url, title}` map form; an untitled one the terse scalar.
    /// When a real (uncommented) `browser:` section with a `urls:` list
    /// exists, the entry goes **before the first existing entry that sorts
    /// after it** (plain string order), falling back to after the last item;
    /// otherwise a fresh `browser:` block is inserted just before the closing
    /// fence. Scalars are serialized by SnakeYAML.
    ///
    /// Existing entries are never reordered — only the new one is placed. On
    /// an already-sorted list that keeps it sorted, which is what a task's PRs
    /// want (`.../pull/16287` before `.../pull/643`: string order, so a PR
    /// numbered like a prefix of another sorts oddly — accepted, sorting by
    /// parsed PR number would only order GitHub URLs and leave every other
    /// link arbitrary).
    // [impl->dsn~task-add-link~3]
    // [impl->dsn~browser-url-title~1]
    public static String addBrowserUrl(String content, String url) {
        return addBrowserUrl(content, url, null);
    }

    /// Appends every http(s) URL **mentioned in** `description` to the
    /// content's `browser.urls` (deduplicated, first occurrence wins) — a
    /// link inside a longer description or queued message gets the same
    /// treatment as one pasted alone into the Add-task dialog.
    // [impl->dsn~task-create-ui~15]
    // [impl->dsn~task-url-collect~1]
    public static String addUrlsFrom(String content, String description) {
        String updated = content;
        for (String url : new java.util.LinkedHashSet<>(Task.urlsIn(description))) {
            updated = addBrowserUrl(updated, url);
        }
        return updated;
    }

    public static String addBrowserUrl(String content, String url, @Nullable String title) {
        return addBrowserUrl(content, url, title, false);
    }

    /// Adds the pull request Claude is working on: like [#addBrowserUrl] but
    /// the entry goes **first** in an existing list instead of into its sorted
    /// position — the task's own PR is the one to open, and
    /// `BrowserFocusAction` focuses the first URL.
    // [impl->dsn~claude-pr-refresh~5]
    public static String addClaudePrUrl(String content, String url) {
        return addBrowserUrl(content, url, null, true);
    }

    /// Drops repeated `browser.urls` entries, keeping the first occurrence
    /// (with its title, and the map form's continuation lines). Two writers
    /// racing on the same file — the `@cs_pr` and footer-scrape PR writers,
    /// an add-link over a stale editor buffer — can each append a URL the
    /// other has just written; this cleans that up on the next write.
    // [impl->dsn~browser-url-dedupe~3]
    public static String dedupeBrowserUrls(String content) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        return dropBrowserUrls(content, url -> !seen.add(url));
    }

    /// Removes one entry from `browser.urls` — the trash icon on the terminal's
    /// PR header line. Textual and comment-preserving like [#addBrowserUrl],
    /// URLs compared normalized, a URL the list does not have is a no-op.
    // [impl->dsn~task-remove-link~2]
    public static String removeBrowserUrl(String content, String url) {
        String target = Task.normalizeUrl(url);
        return dropBrowserUrls(content, target::equals);
    }

    /// The shared walk over the `browser.urls` items: every item line whose
    /// URL `drop` accepts goes, together with the map form's continuation
    /// lines. Removing the last item takes the whole `browser:` block with it
    /// — a bare `urls:` parses as "no URLs" but would make the next
    /// [#addBrowserUrl] write a second `urls:` key.
    private static String dropBrowserUrls(String content,
            java.util.function.Predicate<String> drop) {
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        int close = -1;
        int browser = -1;
        for (int i = 1; i < lines.length; i++) {
            if (isFence(lines[i])) {
                close = i;
                break;
            }
            if (lines[i].startsWith("browser:")) {
                browser = i;
            }
        }
        if (close < 0 || browser < 0) {
            return content;
        }
        boolean[] dropped = new boolean[lines.length];
        int droppedIndent = -1;
        int blockEnd = close;
        boolean any = false;
        boolean kept = false;
        for (int i = browser + 1; i < close; i++) {
            String stripped = lines[i].stripLeading();
            int indent = lines[i].length() - stripped.length();
            if (indent == 0 && !stripped.isBlank()) {
                blockEnd = i;
                break;
            }
            if (stripped.startsWith("- ")) {
                dropped[i] = drop.test(itemUrl(stripped));
                any |= dropped[i];
                kept |= !dropped[i];
                droppedIndent = dropped[i] ? indent : -1;
            } else if (droppedIndent >= 0 && indent > droppedIndent) {
                dropped[i] = true;   // the map form's `title:` line
            } else {
                droppedIndent = -1;
            }
        }
        if (any && !kept) {
            java.util.Arrays.fill(dropped, browser, blockEnd, true);
        }
        java.util.List<String> out = new java.util.ArrayList<>(lines.length);
        for (int i = 0; i < lines.length; i++) {
            if (!dropped[i]) {
                out.add(lines[i]);
            }
        }
        return out.size() == lines.length ? content : String.join("\n", out);
    }

    private static String addBrowserUrl(String content, String url, @Nullable String title,
            boolean first) {
        // Deduplicated first, so every return path — including the "already
        // there" no-op — hands back a list without repeats.
        content = dedupeBrowserUrls(content);
        url = Task.normalizeUrl(url);
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !isFence(lines[0])) {
            return content;
        }
        int close = -1;
        int browser = -1;
        for (int i = 1; i < lines.length; i++) {
            if (isFence(lines[i])) {
                close = i;
                break;
            }
            if (lines[i].stripLeading().startsWith("browser:")
                    && lines[i].length() - lines[i].stripLeading().length() == 0) {
                browser = i;
            }
        }
        if (close < 0) {
            return content;
        }
        String item = title == null || title.isBlank()
                ? "    - " + yamlScalar(url)
                : "    - url: " + yamlScalar(url) + "\n      title: " + yamlScalar(title);
        java.util.List<String> out = new java.util.ArrayList<>(java.util.Arrays.asList(lines));
        if (browser >= 0) {
            // Walk the `urls:` list items within the browser block (up to the
            // next top-level key or the closing fence), remembering the last
            // one and the first that sorts after the new URL.
            int firstUrl = -1;
            int lastUrl = -1;
            int sortedAt = -1;
            for (int i = browser + 1; i < close; i++) {
                String stripped = lines[i].stripLeading();
                int indent = lines[i].length() - stripped.length();
                if (indent == 0 && !stripped.isBlank()) {
                    break;
                }
                if (stripped.startsWith("- ")) {
                    if (itemUrl(stripped).equals(url)) {
                        return content;
                    }
                    if (firstUrl < 0) {
                        firstUrl = i;
                    }
                    lastUrl = i;
                    if (sortedAt < 0 && itemUrl(stripped).compareTo(url) > 0) {
                        sortedAt = i;   // insert before this item's first line
                    }
                }
            }
            int at = first && firstUrl >= 0 ? firstUrl : sortedAt;
            if (at >= 0) {
                out.add(at, item);
            } else {
                out.add(lastUrl >= 0 ? lastUrl + 1 : browser + 1, lastUrl >= 0 ? item : "  urls:\n" + item);
            }
        } else {
            out.add(close, "browser:\n  urls:\n" + item);
        }
        return String.join("\n", out);
    }

    /// The comparable URL of a `urls:` list item's first line, for the sorted
    /// insert: the scalar of `- <url>`, or the value of the map form's
    /// `- url: <url>` (whose `title:` sits on the following line and never
    /// starts an item). Quotes are stripped so a quoted and an unquoted entry
    /// compare on the same text.
    private static String itemUrl(String strippedItemLine) {
        String value = strippedItemLine.substring(2).strip();
        if (value.startsWith("url:")) {
            value = value.substring(4).strip();
        }
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return Task.normalizeUrl(value);
    }

    private Map<String, Object> loadYaml(String yamlPart) throws TaskParseException {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object loaded;
        try {
            loaded = yaml.load(yamlPart);
        } catch (YAMLException e) {
            throw new TaskParseException("Invalid YAML frontmatter: " + e.getMessage(), e);
        }
        if (loaded == null) {
            throw new TaskParseException("Frontmatter is empty");
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new TaskParseException("Frontmatter must be a YAML mapping");
        }
        return applyMachineVariants(map, MACHINE_TOKENS);
    }

    /// The suffix tokens a key may carry to scope its value to this machine,
    /// least specific first — the OS, then the host name, so the host wins.
    /// Resolved once: `os.name` never changes, and the host name costs a
    /// (possibly DNS-backed) lookup.
    // [impl->dsn~machine-key-variants~1]
    private static final List<String> MACHINE_TOKENS = List.of(osToken(), hostToken());

    /// Folds machine-scoped keys into their plain form: a `folders-linux`
    /// entry replaces `folders` when `linux` is one of this machine's tokens,
    /// and a later (more specific) token overrides an earlier one. Keys
    /// suffixed for *another* machine stay untouched — no parse rule knows
    /// them, so they are ignored like any other unknown key. Nested mappings
    /// are folded too (`intellij.projectPath-devbox`); lists are not, their
    /// items are values rather than configuration keys.
    // [impl->dsn~machine-key-variants~1]
    static Map<String, Object> applyMachineVariants(Map<?, ?> data, List<String> tokens) {
        Map<String, Object> base = new LinkedHashMap<>();
        List<Map<String, Object>> overrides = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            overrides.add(new LinkedHashMap<>());
        }
        for (Map.Entry<?, ?> entry : data.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue() instanceof Map<?, ?> nested
                    ? applyMachineVariants(nested, tokens)
                    : entry.getValue();
            int variant = variantToken(key, tokens);
            if (variant < 0) {
                base.put(key, value);
            } else {
                overrides.get(variant)
                        .put(key.substring(0, key.length() - tokens.get(variant).length() - 1), value);
            }
        }
        overrides.forEach(base::putAll);
        return base;
    }

    /// Index of the machine token `key` is suffixed with (`-<token>`,
    /// case-insensitive, non-empty base name), or -1 for a plain key.
    private static int variantToken(String key, List<String> tokens) {
        String lower = key.toLowerCase(Locale.ROOT);
        for (int i = 0; i < tokens.size(); i++) {
            String suffix = "-" + tokens.get(i);
            if (!tokens.get(i).isEmpty() && lower.endsWith(suffix) && lower.length() > suffix.length()) {
                return i;
            }
        }
        return -1;
    }

    /// This machine's OS token: `windows`, `mac`, or `linux`.
    private static String osToken() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "windows";
        }
        return os.contains("mac") || os.contains("darwin") ? "mac" : "linux";
    }

    /// This machine's short host name as [#hostToken] finds it — the desktop's
    /// sender name in provenance records (`dsn~provenance-record~1`).
    public static String hostName() {
        return hostToken();
    }

    /// This machine's short host name, lower-cased and without its domain.
    /// `COMPUTERNAME` (Windows) and `HOSTNAME` come first because they cost
    /// nothing; the DNS-backed lookup is the fallback. Empty when even that
    /// fails — then no key is host-scoped, and the OS token still applies.
    /// Android forbids the lookup on the main thread
    /// (`NetworkOnMainThreadException`, a runtime exception), where this class
    /// may first load; it has no meaningful host name anyway.
    private static String hostToken() {
        String host = System.getenv("COMPUTERNAME");
        if (host == null || host.isBlank()) {
            host = System.getenv("HOSTNAME");
        }
        if (host == null || host.isBlank()) {
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException | RuntimeException e) {
                host = "";
            }
        }
        int dot = host.indexOf('.');
        return (dot < 0 ? host : host.substring(0, dot)).toLowerCase(Locale.ROOT);
    }

    private TaskStatus parseStatus(String value) throws TaskParseException {
        try {
            return TaskStatus.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new TaskParseException(e.getMessage());
        }
    }

    private Task.@Nullable TmuxConfig parseTmux(@Nullable Map<String, Object> tmux) throws TaskParseException {
        if (tmux == null) {
            return null;
        }
        String session = requiredString(tmux, "session", "tmux");
        String window = optionalString(tmux, "window", null);
        return new Task.TmuxConfig(session, window);
    }

    // [impl->dsn~local-terminal-focus~5]
    private Task.@Nullable TerminalConfig parseTerminal(@Nullable Map<String, Object> terminal) throws TaskParseException {
        if (terminal == null) {
            return null;
        }
        return new Task.TerminalConfig(requiredString(terminal, "tabTitle", "terminal"));
    }

    /// Every `intellij` key is optional (`projectPath` falls back to the
    /// Claude workspace/cwd, see `Task.intellijProjectPath()`), so a bare
    /// `intellij:` line — present but without children — already enables the
    /// action. Hence the presence check on the key itself.
    private Task.@Nullable IntellijConfig parseIntellij(Map<String, Object> data) throws TaskParseException {
        if (!data.containsKey("intellij")) {
            return null;
        }
        Map<String, Object> intellij = section(data, "intellij");
        if (intellij == null) {
            return new Task.IntellijConfig(null, null, null);
        }
        return new Task.IntellijConfig(
                optionalString(intellij, "projectPath", null),
                optionalString(intellij, "remote", null),
                optionalString(intellij, "ide", null));
    }

    // [impl->dsn~claude-session-capture~3]
    private Task.@Nullable ClaudeConfig parseClaude(@Nullable Map<String, Object> claude) throws TaskParseException {
        if (claude == null) {
            return null;
        }
        return new Task.ClaudeConfig(
                requiredString(claude, "cwd", "claude"),
                optionalString(claude, "sessionId", null),
                optionalString(claude, "workspace", null),
                optionalString(claude, "commit", null));
    }

    private Task.@Nullable BrowserConfig parseBrowser(@Nullable Map<String, Object> browser) throws TaskParseException {
        if (browser == null) {
            return null;
        }
        Object urls = browser.get("urls");
        if (urls == null) {
            return null;
        }
        if (!(urls instanceof List<?> list)) {
            throw new TaskParseException("'browser.urls' must be a list");
        }
        // Each item is either a plain URL scalar or a `{url, title}` map —
        // the terse scalar form for the common untitled case.
        // [impl->dsn~browser-url-title~1]
        // Duplicates (a hand-edited file, or two writers that raced) are
        // dropped on load, first occurrence wins — the file itself is cleaned
        // on its next `addBrowserUrl`.
        // [impl->dsn~browser-url-dedupe~3]
        List<Task.UrlEntry> result = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object url = map.get("url");
                if (url == null) {
                    throw new TaskParseException("'browser.urls' map entry needs a 'url'");
                }
                Object title = map.get("title");
                String normalized = Task.normalizeUrl(url.toString());
                if (seen.add(normalized)) {
                    result.add(new Task.UrlEntry(normalized,
                            title == null ? null : title.toString()));
                }
            } else {
                String normalized = Task.normalizeUrl(stringify(item));
                if (seen.add(normalized)) {
                    result.add(new Task.UrlEntry(normalized, null));
                }
            }
        }
        return result.isEmpty() ? null : new Task.BrowserConfig(result);
    }

    private @Nullable Map<String, Object> section(Map<String, Object> data, String key) throws TaskParseException {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new TaskParseException("'%s' must be a YAML mapping".formatted(key));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) map;
        return result;
    }

    private String requiredString(Map<String, Object> map, String key, String sectionName)
            throws TaskParseException {
        Object value = map.get(key);
        if (value == null) {
            throw new TaskParseException("'%s.%s' is required".formatted(sectionName, key));
        }
        return stringify(value);
    }

    private @Nullable String optionalString(Map<String, Object> map, String key, @Nullable String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : stringify(value);
    }

    private static String stringify(Object value) {
        return String.valueOf(value);
    }
}
