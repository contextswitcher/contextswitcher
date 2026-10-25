package com.contextswitcher.discovery;

import java.util.List;
import java.util.StringJoiner;

import com.contextswitcher.ssh.SshCommandRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Claude's last answer in a session, read from its transcript on the remote
/// host (`~/.claude/projects/<encoded-cwd>/<session-id>.jsonl`): plain text,
/// unlike a terminal snapshot, so it can be read aloud or shown as prose.
// [impl->dsn~android-voice-tour~1]
public class ClaudeTranscript {

    private static final ObjectMapper JSON = new ObjectMapper();
    /// A turn is one assistant line with text, usually behind tool-use-only
    /// lines; five covers Claude's trailing tool calls without a wide read.
    private static final int TAIL_LINES = 5;

    private final SshCommandRunner ssh;

    public ClaudeTranscript(SshCommandRunner ssh) {
        this.ssh = ssh;
    }

    /// Unquoted pipe: expanded by the remote shell (single ssh call). The
    /// transcript holds API-request blobs of hundreds of kilobytes per line,
    /// so only the assistant lines travel, and only the last few.
    static List<String> remoteCommand(String cwd, String sessionId) {
        return List.of("grep", "-F", "'\"type\":\"assistant\"'",
                "~/.claude/projects/" + ClaudeSessionLookup.encodeProjectDir(cwd) + "/" + sessionId + ".jsonl",
                "|", "tail", "-" + TAIL_LINES);
    }

    /// The text blocks of the newest assistant line that has any, joined by
    /// blank lines; null when the lines hold none (or do not parse).
    static @Nullable String lastAnswer(String assistantLines) {
        String[] lines = assistantLines.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].isBlank()) {
                continue;
            }
            try {
                StringJoiner text = new StringJoiner("\n\n");
                for (JsonNode block : JSON.readTree(lines[i]).path("message").path("content")) {
                    if ("text".equals(block.path("type").asText())) {
                        text.add(block.path("text").asText().strip());
                    }
                }
                if (text.length() > 0) {
                    return text.toString();
                }
            } catch (java.io.IOException e) {
                Logger.debug("Unparseable transcript line: {}", e.getMessage());
            }
        }
        return null;
    }

    /// Claude's last answer in the session, or null when the host, the
    /// transcript or an answer is not there.
    public @Nullable String lastAnswer(String host, String cwd, String sessionId) {
        SshCommandRunner.SshResult result = ssh.run(host, remoteCommand(cwd, sessionId));
        return result.ok() ? lastAnswer(result.stdout()) : null;
    }
}
