package com.contextswitcher.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.contextswitcher.discovery.TmuxDiscovery.TmuxSession;
import com.contextswitcher.discovery.TmuxDiscovery.TmuxWindow;
import com.contextswitcher.tasks.TextFiles;
import org.tinylog.Logger;

import static com.contextswitcher.tasks.TaskFileParser.yamlScalar;

/// Writes one task-file skeleton per discovered tmux **window** (a window/tab
/// is a task): `tmux` filled with session + immutable window id (`@17`, which
/// survives renumbering after other windows close), `intellij` and `browser`
/// prepared as commented sections to complete later — in intro mode; with the
/// `hints` setting off, the skeleton is compact (real keys only, empty Notes
/// body). The title is the pane
/// title when it carries one (Claude Code publishes its task summary there),
/// else the plain window name — the `user@host target` context is derived
/// from the frontmatter and shown by the UI, not baked into the title.
/// Windows already
/// covered by a task — directly, or via a session-level task without a
/// `window` key — are skipped. A same-named existing file is NOT coverage
/// (it may track a long-dead context): it is never overwritten, the new
/// task gets a `-2`/`-3`… suffixed name instead.
// [impl->dsn~tmux-task-import~12]
public class TmuxTaskImporter {

    public record ImportResult(List<String> created, List<String> skipped) {
    }

    /// Key of a session-level task (covers all windows of the session).
    public static String sessionKey(String host, String session) {
        return host + " " + session;
    }

    /// Key of a window-level task; `window` may be a name or an index.
    public static String windowKey(String host, String session, String window) {
        return host + " " + session + " " + window;
    }

    public static ImportResult write(String host, List<TmuxSession> sessions,
            Set<String> knownKeys, Set<String> knownSessionIds, Path tasksDir, LocalDate date,
            boolean hints) throws IOException {
        List<String> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (TmuxSession session : sessions) {
            for (TmuxWindow window : session.windows()) {
                String label = "%s:%s (%s)".formatted(session.name(), window.index(), window.name());
                // The Claude session id is the strongest identity: a window
                // whose session some task already tracks is covered no
                // matter how the tmux coordinates shifted.
                if (window.sessionId() != null && knownSessionIds.contains(window.sessionId())) {
                    skipped.add(label);
                    continue;
                }
                if (knownKeys.contains(sessionKey(host, session.name()))
                        || knownKeys.contains(windowKey(host, session.name(), window.id()))
                        || knownKeys.contains(windowKey(host, session.name(), window.index()))
                        || knownKeys.contains(windowKey(host, session.name(), window.name()))) {
                    skipped.add(label);
                    continue;
                }
                // An existing file of the same name is NOT coverage — it may
                // track a long-dead context (renamed task, reused window
                // index). Never overwrite, but do import: pick a free name.
                Path file = freeFile(tasksDir, fileName(host, session.name(), window));
                TextFiles.write(file, skeleton(host, session.name(), window, date, hints));
                Logger.info("Imported tmux window {} on {} as {}", label, host, file.getFileName());
                created.add(label);
            }
        }
        Logger.trace("Import on {}: created {}, skipped as covered: {}", host, created.size(), skipped);
        return new ImportResult(List.copyOf(created), List.copyOf(skipped));
    }

    /// The given name, or with `-2`, `-3`, … appended until unused.
    private static Path freeFile(Path tasksDir, String fileName) {
        Path file = tasksDir.resolve(fileName);
        String base = fileName.substring(0, fileName.length() - ".md".length());
        for (int i = 2; Files.exists(file); i++) {
            file = tasksDir.resolve(base + "-" + i + ".md");
        }
        return file;
    }

    static String fileName(String host, String session, TmuxWindow window) {
        return "%s-%s-%s-%s.md".formatted(
                sanitize(host), sanitize(session), sanitize(window.index()),
                capLength(sanitize(window.name())));
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /// Longest name component kept in an imported file name: a window whose
    /// name is a pasted multi-sentence task description (Claude never
    /// published a short `@cs_title`) must still yield a Windows-safe path
    /// (MAX_PATH). Host/session/index are already short; only the name can
    /// blow up. Cut at a `_`/`-` boundary past the halfway mark, else hard.
    private static final int MAX_NAME_LENGTH = 60;

    private static String capLength(String name) {
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        int cut = Math.max(name.lastIndexOf('_', MAX_NAME_LENGTH),
                name.lastIndexOf('-', MAX_NAME_LENGTH));
        return name.substring(0, cut > MAX_NAME_LENGTH / 2 ? cut : MAX_NAME_LENGTH)
                .replaceAll("[_.-]+$", "");
    }

    /// The pane title when it carries information — Claude Code publishes
    /// its task summary there (e.g. `directory-as-library-feature`), which
    /// beats the window name — else the plain window name. The `remote
    /// target` context is derived and shown by the task list; it does not
    /// belong in the title.
    static String title(TmuxWindow window) {
        return window.paneTitle().isBlank() ? window.name() : window.paneTitle();
    }

    /// The skeleton in the flavor the `hints` setting selects: intro mode
    /// carries the commented fill-in hints plus a provenance note under
    /// `# Notes`; compact mode carries only the real keys and an empty body.
    // [impl->dsn~skeleton-hints~4]
    static String skeleton(String host, String session, TmuxWindow window, LocalDate date,
            boolean hints) {
        String head = """
                ---
                title: %s
                status: active
                remote: %s
                tmux:
                  session: %s
                  window: %s
                %s%s""".formatted(
                yamlScalar(title(window)), yamlScalar(host),
                yamlScalar(session), yamlScalar(window.id()),
                claudeSection(window), browserSection(window, hints));
        if (!hints) {
            return head + "---\n\n# Notes\n\n";
        }
        return head + """
                # Fill in when ready (remove the leading '# '):
                # intellij:   # bare section suffices with claude: present (path from workspace/cwd)
                #   projectPath: /home/user/repos/project   # only needed without claude
                ---

                # Notes

                Imported from tmux on %s (%s): session %s, window %s (%s).
                """.formatted(host, date, session, window.index(), window.name());
    }

    /// A real `browser:` section when the window's pull request is known
    /// (`@cs_pr` option or the footer scrape), else the commented example
    /// (intro mode) or nothing (compact mode).
    // [impl->dsn~claude-pr-capture~2]
    private static String browserSection(TmuxWindow window, boolean hints) {
        if (window.prUrl() == null) {
            return hints ? """
                    # browser:
                    #   urls:
                    #     - https://github.com/owner/repo/pull/1
                    """ : "";
        }
        return """
                browser:
                  urls:
                    - %s
                """.formatted(yamlScalar(window.prUrl()));
    }

    /// `claude:` section only for windows that actually host a Claude
    /// session — the pane runs `claude`, a session id was found, or the
    /// window published `@cs_workspace`. Every pane has a cwd; that alone
    /// says nothing about Claude. With the section: `cwd`, the session id
    /// when one was found (enabling resume after a host reboot), and the
    /// working subdirectory when published.
    // [impl->dsn~claude-session-capture~3]
    // [impl->dsn~claude-workspace-capture~2]
    private static String claudeSection(TmuxWindow window) {
        boolean hostsClaude = "claude".equals(window.command())
                || window.sessionId() != null
                || !window.workspace().isBlank();
        if (!hostsClaude || window.cwd().isBlank()) {
            return "";
        }
        StringBuilder section = new StringBuilder();
        section.append("claude:\n");
        section.append("  cwd: %s\n".formatted(yamlScalar(window.cwd())));
        if (window.sessionId() != null) {
            section.append("  sessionId: %s\n".formatted(yamlScalar(window.sessionId())));
        }
        if (!window.workspace().isBlank()) {
            section.append("  workspace: %s\n".formatted(yamlScalar(window.workspace())));
        }
        return section.toString();
    }
}
