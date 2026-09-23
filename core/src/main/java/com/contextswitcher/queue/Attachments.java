package com.contextswitcher.queue;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.SequencedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// Attachments referenced from queued messages. A pasted clipboard image
/// (or a file dragged onto a box) is stored locally and referenced in the
/// message text as `[image: <absolute local path>]` (`[file: …]` for
/// dropped files); on send the file is uploaded and the marker is replaced
/// by its remote path, so the message Claude receives points at a file
/// that exists on its machine — mirroring what pasting an image or
/// dropping a file into Claude Code itself does.
// [impl->dsn~message-queue-send~5]
public final class Attachments {

    private static final Pattern MARKER = Pattern.compile("\\[(?:image|file): ([^\\]]+)\\]");

    /// An uploaded attachment's remote path, as the send rewrote its marker
    /// to and the chat echoes it: `…/.contextswitcher/attachments/<name>`.
    /// The upload keeps the (sanitized) file name, so the name alone leads
    /// back to the local copy.
    private static final Pattern UPLOADED = Pattern.compile(
            "[^\\s`'\"()\\[\\]]*/" + Pattern.quote(QueueSendCommands.REMOTE_DIR) + "/([A-Za-z0-9._-]+)");

    private Attachments() {
    }

    /// The marker text inserted at the caret for a stored image.
    public static String marker(Path localImage) {
        return "[image: " + localImage + "]";
    }

    /// The marker text inserted at the caret for a stored dropped file.
    public static String fileMarker(Path localFile) {
        return "[file: " + localFile + "]";
    }

    /// Makes every ARGB pixel opaque when not a single one is. The Windows
    /// clipboard hands JavaFX 32-bit screenshots whose alpha byte is 0
    /// throughout; stored as-is, such a PNG shows blank in every viewer and
    /// to Claude. An image with any visible pixel is really translucent and
    /// is left alone.
    // [impl->dsn~message-queue-ui~26]
    public static void opaqueIfFullyTransparent(int[] argb) {
        for (int pixel : argb) {
            if (pixel >>> 24 != 0) {
                return;
            }
        }
        for (int i = 0; i < argb.length; i++) {
            argb[i] |= 0xFF000000;
        }
    }

    /// The distinct paths every marker in `text` points at, in text order —
    /// what the hover preview offers as thumbnails. Unparseable paths
    /// (arbitrary hand-typed text between the brackets) are skipped.
    // [impl->dsn~attachment-image-hover~2]
    public static SequencedSet<Path> paths(String text) {
        SequencedSet<Path> refs = new LinkedHashSet<>();
        Matcher matcher = MARKER.matcher(text);
        while (matcher.find()) {
            try {
                refs.add(Path.of(matcher.group(1)));
            } catch (InvalidPathException e) {
                // Not a path — no attachment, nothing to collect.
            }
        }
        return refs;
    }

    /// The distinct local attachment paths a message references — only
    /// markers pointing into `attachmentsDir` count, so a hand-written
    /// `[image: …]` about some other file is left alone.
    public static SequencedSet<Path> localRefs(String text, Path attachmentsDir) {
        SequencedSet<Path> refs = new LinkedHashSet<>();
        Path dir = attachmentsDir.normalize();
        for (Path path : paths(text)) {
            if (path.normalize().startsWith(dir)) {
                refs.add(path);
            }
        }
        return refs;
    }

    /// The attachment path of the marker covering `offset` in `line`, or
    /// `null` when the offset sits outside every marker — what the editor
    /// lane's hover preview asks for the text position under the pointer.
    /// Unparseable paths (arbitrary hand-typed text between the brackets)
    /// count as no marker.
    // [impl->dsn~attachment-image-hover~2]
    public static @Nullable Path pathAt(String line, int offset) {
        Matcher matcher = MARKER.matcher(line);
        while (matcher.find()) {
            if (offset >= matcher.start() && offset < matcher.end()) {
                try {
                    return Path.of(matcher.group(1));
                } catch (InvalidPathException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /// Like [#pathAt(String, int)], but an uploaded attachment's remote path
    /// (a sent message, the chat's echo of it) counts too and resolves to its
    /// local copy in `attachmentsDir` — whether or not that copy still exists.
    // [impl->dsn~attachment-image-hover~2]
    public static @Nullable Path pathAt(String line, int offset, Path attachmentsDir) {
        Path marker = pathAt(line, offset);
        if (marker != null) {
            return marker;
        }
        Matcher matcher = UPLOADED.matcher(line);
        while (matcher.find()) {
            if (offset >= matcher.start() && offset < matcher.end()) {
                return attachmentsDir.resolve(matcher.group(1));
            }
        }
        return null;
    }

    /// The local copy in `attachmentsDir` of an uploaded attachment's remote
    /// `path`, or null when `path` is no such path.
    // [impl->dsn~terminal-file-links~1]
    public static @Nullable Path localCopy(String path, Path attachmentsDir) {
        Matcher matcher = UPLOADED.matcher(path);
        return matcher.matches() ? attachmentsDir.resolve(matcher.group(1)) : null;
    }

    /// Replaces the markers of `local` with `replacement` verbatim — for an
    /// attachment that did not reach the remote, whose local path would mean
    /// nothing to the chat there.
    public static String drop(String text, Path local, String replacement) {
        return text.replace(marker(local), replacement).replace(fileMarker(local), replacement);
    }

    /// Replaces each uploaded marker with its remote path in backticks —
    /// a Markdown code span, so the path stays one token for the receiving
    /// chat even when the file name carries spaces (pre-sanitization
    /// markers, hand-placed files).
    public static String rewrite(String text, Map<Path, String> remoteByLocal) {
        String result = text;
        for (Map.Entry<Path, String> entry : remoteByLocal.entrySet()) {
            String remote = "`" + entry.getValue() + "`";
            result = result.replace(marker(entry.getKey()), remote);
            result = result.replace(fileMarker(entry.getKey()), remote);
        }
        return result;
    }
}
