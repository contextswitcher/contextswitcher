package com.contextswitcher.discovery;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// The Claude Code sessions recorded on **this** machine for one directory —
/// the transcripts under `~/.claude/projects/<encoded-cwd>/` — so a local
/// category can list the conversations already held in its folder as tasks
/// (`dsn~local-category-session-import~1`).
///
/// A transcript can be over 100 MB, so it is never read whole: the title
/// comes from the file's **tail** (Claude appends a fresh `ai-title`, or the
/// `custom-title` of a `/rename`, as the conversation goes on — the last one
/// wins), the fallback from its **head** (the first prompt typed).
// [impl->dsn~local-category-session-import~1]
public final class LocalClaudeSessions {

    /// One recorded session: its id (the transcript's file name), a title
    /// to show for it and when it was last written to.
    public record Session(String id, String title, Instant lastModified) {
    }

    /// A session id is a UUID; Claude's side files (`<id>.orphaned-….jsonl`)
    /// are not sessions of their own.
    private static final Pattern SESSION_FILE =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.jsonl");

    private static final int HEAD_BYTES = 256 * 1024;
    private static final int TAIL_BYTES = 512 * 1024;
    private static final int MAX_TITLE = 80;
    private static final ObjectMapper JSON = new ObjectMapper();

    private LocalClaudeSessions() {
    }

    /// The sessions recorded for `cwd` in the current user's
    /// `~/.claude/projects`, newest first; empty when there are none.
    public static List<Session> of(String cwd) {
        return in(Path.of(System.getProperty("user.home"), ".claude", "projects",
                ClaudeSessionLookup.encodeProjectDir(cwd)));
    }

    /// The sessions in one project directory, newest first. A transcript
    /// without a single prompt (Claude leaves title-only stubs behind) has no
    /// conversation to resume and is left out.
    static List<Session> in(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Session> sessions = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> SESSION_FILE.matcher(f.getFileName().toString()).matches())
                    .filter(Files::isRegularFile).toList()) {
                Session session = read(file);
                if (session != null) {
                    sessions.add(session);
                }
            }
        } catch (IOException e) {
            Logger.debug("Cannot list Claude transcripts in {}: {}", dir, e.getMessage());
            return List.of();
        }
        sessions.sort(Comparator.comparing(Session::lastModified).reversed());
        return List.copyOf(sessions);
    }

    private static @Nullable Session read(Path file) {
        String name = file.getFileName().toString();
        String id = name.substring(0, name.length() - ".jsonl".length());
        try {
            long size = Files.size(file);
            String head = slice(file, 0, (int) Math.min(size, HEAD_BYTES));
            String prompt = firstPrompt(head);
            if (prompt == null && size <= HEAD_BYTES) {
                return null;
            }
            String tail = size <= HEAD_BYTES ? head
                    : slice(file, Math.max(0, size - TAIL_BYTES), (int) Math.min(size, TAIL_BYTES));
            String title = latestTitle(tail);
            if (title == null) {
                title = prompt;
            }
            FileTime modified = Files.getLastModifiedTime(file);
            return new Session(id, title == null ? "Claude session " + id.substring(0, 8) : shorten(title),
                    modified.toInstant());
        } catch (IOException e) {
            Logger.debug("Cannot read Claude transcript {}: {}", file, e.getMessage());
            return null;
        }
    }

    private static String slice(Path file, long offset, int length) throws IOException {
        byte[] bytes = new byte[length];
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            in.seek(offset);
            in.readFully(bytes);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /// The last `custom-title` (a `/rename`) of the chunk, else its last
    /// `ai-title`; null when it holds neither. Broken lines (the chunk's cut
    /// first and last line) are skipped.
    static @Nullable String latestTitle(String chunk) {
        String custom = null;
        String ai = null;
        for (String line : chunk.split("\n")) {
            if (!line.contains("\"custom-title\"") && !line.contains("\"ai-title\"")) {
                continue;
            }
            JsonNode node = parse(line);
            if (node == null) {
                continue;
            }
            String type = node.path("type").asText();
            if ("custom-title".equals(type) && !node.path("customTitle").asText().isBlank()) {
                custom = node.path("customTitle").asText().strip();
            } else if ("ai-title".equals(type) && !node.path("aiTitle").asText().isBlank()) {
                ai = node.path("aiTitle").asText().strip();
            }
        }
        return custom != null ? custom : ai;
    }

    /// The first line of the first prompt the user typed (a plain string
    /// content, or the first text block); tool results and meta lines are
    /// not prompts. Null when the chunk holds none.
    static @Nullable String firstPrompt(String chunk) {
        for (String line : chunk.split("\n")) {
            if (!line.contains("\"type\":\"user\"")) {
                continue;
            }
            JsonNode node = parse(line);
            if (node == null || !"user".equals(node.path("type").asText())
                    || node.path("isMeta").asBoolean(false)) {
                continue;
            }
            JsonNode content = node.path("message").path("content");
            String text = null;
            if (content.isTextual()) {
                text = content.asText();
            } else if (content.isArray()) {
                for (JsonNode block : content) {
                    if ("text".equals(block.path("type").asText())) {
                        text = block.path("text").asText();
                        break;
                    }
                }
            }
            if (text == null) {
                continue;
            }
            String stripped = text.strip();
            // Slash commands and their output come wrapped in XML-ish tags.
            if (stripped.isEmpty() || stripped.startsWith("<")) {
                continue;
            }
            return stripped.lines().findFirst().orElse(stripped);
        }
        return null;
    }

    private static @Nullable JsonNode parse(String line) {
        try {
            return JSON.readTree(line);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String shorten(String title) {
        String oneLine = title.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= MAX_TITLE ? oneLine : oneLine.substring(0, MAX_TITLE - 1).strip() + "…";
    }
}
