package com.contextswitcher.discovery;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.contextswitcher.ssh.SshCommandRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// A Claude chat as who said what: the user's messages and Claude's replies,
/// read from the session transcript
/// (`~/.claude/projects/<encoded-cwd>/<session>.jsonl`). The terminal shows
/// both in one stream of rendered text; the transcript names the speaker.
// [impl->dsn~conversation-pane~1]
public class ClaudeConversation {

    /// One message; `text` is the Markdown as written.
    public record Message(boolean fromUser, String text, Instant at) {
    }

    /// How many transcript lines the remote hands back.
    // ponytail: only the recent end of a long chat; a "load earlier" when someone scrolls for more
    static final int TAIL_LINES = 80;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SshCommandRunner ssh;

    public ClaudeConversation(SshCommandRunner ssh) {
        this.ssh = ssh;
    }

    /// The transcript's user and assistant lines without the bulk — tool
    /// calls and their results, thinking, the logged API requests — newest
    /// last. `.` stands for the JSON quotes (no double quotes survive Windows
    /// `ssh.exe`); a message quoting such a line has them escaped (`\"`), so
    /// it stays. `~` is left for the remote shell.
    static List<String> command(String cwd, String sessionId) {
        return List.of("grep", "-a", "-e", "'role.:.assistant'", "-e", "'role.:.user'",
                "~/.claude/projects/" + ClaudeSessionLookup.encodeProjectDir(cwd) + "/" + sessionId + ".jsonl",
                "|", "grep", "-a", "-v", "-e", "'{.tool_use_id.:'", "-e", "'{.type.:.tool_'",
                "-e", "'{.type.:.thinking.'", "-e", "'.type.:.api-request'",
                "|", "tail", "-n", String.valueOf(TAIL_LINES));
    }

    /// The recent messages of session `sessionId` on `host`, oldest first;
    /// empty when the transcript cannot be read. Blocking.
    public List<Message> fetch(String host, String cwd, String sessionId) {
        SshCommandRunner.SshResult result = ssh.run(host, command(cwd, sessionId));
        if (!result.ok()) {
            Logger.debug("Cannot read transcript {} on {}: {}", sessionId, host, result.stderr().strip());
        }
        return parse(result.stdout());
    }

    /// One [Message] per user prompt and per assistant message: Claude Code
    /// writes each content block of a reply as its own line, so consecutive
    /// text lines sharing a message id are joined. Skipped: subagent chatter,
    /// what Claude Code itself puts in the user's mouth (meta lines, slash
    /// command echoes), and unparseable lines (a `tail` cut).
    static List<Message> parse(String jsonl) {
        List<Message> messages = new ArrayList<>();
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
            String type = entry.path("type").asText();
            boolean fromUser = "user".equals(type);
            if (!fromUser && !"assistant".equals(type)
                    || entry.path("isSidechain").asBoolean(false) || entry.path("isMeta").asBoolean(false)) {
                continue;
            }
            JsonNode content = entry.path("message").path("content");
            StringBuilder text = new StringBuilder();
            if (content.isTextual()) {
                text.append(content.asText());
            }
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(text.isEmpty() ? "" : "\n\n").append(block.path("text").asText());
                }
            }
            String body = text.toString().strip();
            if (body.isEmpty() || fromUser && (body.startsWith("<local-command") || body.startsWith("<command-"))) {
                continue;
            }
            String id = fromUser ? null : entry.path("message").path("id").asText(null);
            if (id != null && Objects.equals(id, lastId) && !messages.isEmpty()) {
                Message last = messages.removeLast();
                messages.add(new Message(false, last.text() + "\n\n" + body, last.at()));
            } else {
                messages.add(new Message(fromUser, body, timestamp(entry)));
            }
            lastId = id;
        }
        return messages;
    }

    private static Instant timestamp(JsonNode entry) {
        try {
            return Instant.parse(entry.path("timestamp").asText());
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }
}
