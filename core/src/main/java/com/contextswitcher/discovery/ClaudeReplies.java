package com.contextswitcher.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.contextswitcher.ssh.SshCommandRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Claude's replies as the Markdown it wrote, read from the session transcript
/// (`~/.claude/projects/<encoded-cwd>/<session>.jsonl`) rather than from the
/// terminal: Claude Code renders the Markdown — no `**`, no backticks, no
/// fences — and hard-wraps it with its own gutter, so the screen cannot give
/// the source back.
// [impl->dsn~terminal-markdown-copy~1]
public class ClaudeReplies {

    /// How many transcript lines the remote hands back: enough recent replies
    /// for a selection scrolled a little way up, bounded for a long session.
    static final int TAIL_LINES = 40;

    /// Selections shorter than this many letters and digits stay plain text —
    /// a double-clicked word would match too many places to mean one.
    static final int MIN_MATCH = 20;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SshCommandRunner ssh;

    public ClaudeReplies(SshCommandRunner ssh) {
        this.ssh = ssh;
    }

    /// The assistant text lines of the transcript, newest last. The patterns
    /// use `.` for the JSON quotes (no double quotes survive Windows
    /// `ssh.exe`), and a tool result quoting `"role":"assistant"` is escaped
    /// (`\"`) so it does not match. `~` is left for the remote shell.
    static List<String> command(String cwd, String sessionId) {
        return List.of("grep", "-a", "'role.:.assistant'", "~/.claude/projects/"
                        + ClaudeSessionLookup.encodeProjectDir(cwd) + "/" + sessionId + ".jsonl",
                "|", "grep", "-a", "'type.:.text'", "|", "tail", "-n", String.valueOf(TAIL_LINES));
    }

    /// The recent replies of session `sessionId` on `host`, newest last; empty
    /// when the transcript cannot be read. Blocking.
    public List<String> fetch(String host, String cwd, String sessionId) {
        SshCommandRunner.SshResult result = ssh.run(host, command(cwd, sessionId));
        if (!result.ok()) {
            Logger.debug("Cannot read transcript {} on {}: {}", sessionId, host, result.stderr().strip());
        }
        return parse(result.stdout());
    }

    /// One reply per assistant message: Claude Code writes each content block
    /// of a message as its own line, so consecutive text lines sharing a
    /// message id are joined. Unparseable lines (a `tail` cut) are skipped.
    static List<String> parse(String jsonl) {
        List<String> replies = new ArrayList<>();
        @Nullable String lastId = null;
        for (String line : jsonl.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode entry;
            try {
                entry = JSON.readTree(line);
            } catch (Exception e) {
                continue;
            }
            JsonNode message = entry.path("message");
            if (entry.path("isSidechain").asBoolean(false)
                    || !"assistant".equals(message.path("role").asText())) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode block : message.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
            if (text.isEmpty()) {
                continue;
            }
            String id = message.path("id").asText(null);
            if (id != null && Objects.equals(id, lastId) && !replies.isEmpty()) {
                replies.set(replies.size() - 1, replies.getLast() + "\n\n" + text);
            } else {
                replies.add(text.toString());
            }
            lastId = id;
        }
        return replies;
    }

    /// Whether `selection` is long enough to be looked up at all — checked
    /// before the transcript is fetched.
    public static boolean matchable(String selection) {
        return letters(selection, null).length() >= MIN_MATCH;
    }

    /// The Markdown source of `selection`, copied off the rendered terminal,
    /// or null when no reply contains it.
    ///
    /// Only letters and digits are compared: everything the rendering changes
    /// — `**`, backticks, `#`, list bullets, table borders, wrapping, the
    /// gutter — is punctuation or whitespace, so the selection's letters are a
    /// run of the source's. The matched span is then widened over the syntax
    /// hugging it (`**bold**`) and, when only syntax precedes or follows it on
    /// its line, to the whole line (`- item`, `## Heading`).
    // ponytail: a rendering that drops letters (a link shown without its URL) breaks the match; the plain text is copied then
    public static @Nullable String markdownFor(String selection, List<String> replies) {
        if (!matchable(selection)) {
            return null;
        }
        String needle = letters(selection, null);
        for (String reply : replies.reversed()) {
            int[] offsets = new int[reply.length()];
            int at = letters(reply, offsets).lastIndexOf(needle);
            if (at < 0) {
                continue;
            }
            int from = offsets[at];
            int to = offsets[at + needle.length() - 1] + 1;
            while (from > 0 && isSyntax(reply.charAt(from - 1))) {
                from--;
            }
            int lineStart = reply.lastIndexOf('\n', from - 1) + 1;
            if (reply.substring(lineStart, from).chars().noneMatch(Character::isLetterOrDigit)) {
                from = lineStart;
            }
            while (to < reply.length() && isSyntax(reply.charAt(to))) {
                to++;
            }
            int lineEnd = reply.indexOf('\n', to);
            lineEnd = lineEnd < 0 ? reply.length() : lineEnd;
            if (reply.substring(to, lineEnd).chars().noneMatch(Character::isLetterOrDigit)) {
                to = lineEnd;
            }
            return reply.substring(from, to);
        }
        return null;
    }

    /// The inline emphasis and code marks — not sentence punctuation, which a
    /// mid-line selection must not grow by.
    private static boolean isSyntax(char c) {
        return "*_`~".indexOf(c) >= 0;
    }

    /// `text`'s letters and digits, lower-cased; `offsets` (when given)
    /// receives each kept character's index in `text`.
    private static String letters(String text, int @Nullable [] offsets) {
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (offsets != null) {
                    offsets[kept.length()] = i;
                }
                kept.append(Character.toLowerCase(c));
            }
        }
        return kept.toString();
    }
}
