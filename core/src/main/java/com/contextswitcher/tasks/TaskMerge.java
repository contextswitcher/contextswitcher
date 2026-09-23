package com.contextswitcher.tasks;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// A pseudo-semantic three-way merge of one task file, for the conflicts git's
/// line-based merge raises on task files that two machines changed
/// independently.
///
/// The unit of merging is a **top-level frontmatter key** (its line, its
/// indented continuation lines, and the comment lines directly above it) — not
/// a text line. Two machines adding `tags:` and `status:` touch adjacent lines
/// and conflict textually, but they changed different keys, so **both changes
/// are kept**. Only when the *same* key changed on both sides is there a real
/// conflict, and then the **last writer wins** (the side whose commit touched
/// the file more recently, decided by the caller from git history).
///
/// Blocks are carried over verbatim, so comments, quoting and key order in the
/// untouched parts of the file survive — no YAML round-trip.
///
/// The Markdown notes below the frontmatter are merged the same way as a whole:
/// if only one side changed them, that side wins. Notes that **both** sides
/// rewrote are human prose, so no version is guessed at: the merge gives up
/// (empty result) and the caller falls back to manual resolution.
// [impl->dsn~task-merge-resolution~2]
public final class TaskMerge {

    private static final String FENCE = "---";

    /// A top-level frontmatter key line: `status: suspended`, `tmux:`. Indented
    /// lines (nested keys, list items) and comments are not keys — they belong
    /// to the block of the key above them.
    private static final Pattern KEY = Pattern.compile("^([A-Za-z0-9_.-]+):(\\s.*)?$");

    /// Key of the pseudo-block holding the comments and blank lines before the
    /// first real key — not a valid YAML key, so it cannot collide with one.
    private static final String PREAMBLE = "";

    private TaskMerge() {
    }

    /// Merges the three versions of one task file. `preferTheirs` picks the
    /// winner for a key both sides changed differently. Returns the merged file
    /// text, or empty when the merge cannot be resolved semantically (not a
    /// task file, duplicate keys, or notes rewritten on both sides) — the
    /// caller then leaves the conflict to the user.
    public static Optional<String> merge(String base, String ours, String theirs, boolean preferTheirs) {
        Parts baseParts = split(base);
        Parts ourParts = split(ours);
        Parts theirParts = split(theirs);
        if (ourParts == null || theirParts == null) {
            return Optional.empty();
        }
        Map<String, String> baseBlocks = baseParts == null ? Map.of() : blocks(baseParts.frontmatter());
        Map<String, String> ourBlocks = blocks(ourParts.frontmatter());
        Map<String, String> theirBlocks = blocks(theirParts.frontmatter());
        if (baseBlocks == null || ourBlocks == null || theirBlocks == null) {
            return Optional.empty(); // duplicate top-level key — not ours to guess at
        }

        StringBuilder frontmatter = new StringBuilder();
        LinkedHashSet<String> keys = new LinkedHashSet<>(ourBlocks.keySet());
        keys.addAll(theirBlocks.keySet()); // keys only the other machine added, in its order
        for (String key : keys) {
            String winner = pick(baseBlocks.get(key), ourBlocks.get(key), theirBlocks.get(key), preferTheirs);
            if (winner != null) {
                frontmatter.append(winner);
            }
        }

        String baseNotes = baseParts == null ? null : baseParts.notes();
        if (!ourParts.notes().equals(theirParts.notes())
                && !ourParts.notes().equals(baseNotes) && !theirParts.notes().equals(baseNotes)) {
            return Optional.empty(); // both sides rewrote the prose — a human decides
        }
        // Only one side changed them (or neither did): take the changed one.
        String notes = ourParts.notes().equals(baseNotes) ? theirParts.notes() : ourParts.notes();
        return Optional.of(FENCE + "\n" + frontmatter + FENCE + "\n" + notes);
    }

    /// Three-way choice for one block: unchanged on a side means the other
    /// side's version wins (including its deletion, a null), both sides equal
    /// needs no decision, and a genuine divergence falls back to the last
    /// writer. Null means "not present" on input and "drop it" on output —
    /// except for the notes, where both sides diverging is the give-up signal.
    private static @Nullable String pick(@Nullable String base, @Nullable String ours,
            @Nullable String theirs, boolean preferTheirs) {
        if (Objects.equals(ours, theirs)) {
            return ours;
        }
        if (Objects.equals(ours, base)) {
            return theirs;
        }
        if (Objects.equals(theirs, base)) {
            return ours;
        }
        return preferTheirs ? theirs : ours;
    }

    /// The frontmatter (between the fences, fences excluded) and the notes
    /// after it. Null when the text is not a frontmatter file at all.
    private static @Nullable Parts split(String text) {
        String normalized = text.replace("\r\n", "\n");
        if (!normalized.startsWith(FENCE + "\n")) {
            return null;
        }
        int close = normalized.indexOf("\n" + FENCE + "\n", FENCE.length());
        if (close < 0) {
            return normalized.endsWith("\n" + FENCE)
                    ? new Parts(normalized.substring(FENCE.length() + 1, normalized.length() - FENCE.length()), "")
                    : null;
        }
        return new Parts(normalized.substring(FENCE.length() + 1, close + 1),
                normalized.substring(close + 1 + FENCE.length() + 1));
    }

    /// Splits frontmatter into one verbatim text block per top-level key, in
    /// file order. Comments and blank lines are attached to the key **below**
    /// them (that is where they belong: `# machine-specific` documents the key
    /// it precedes); those before the first key become the [#PREAMBLE] block.
    /// Null when a key appears twice — the file is beyond a safe automatic
    /// merge.
    private static @Nullable Map<String, String> blocks(String frontmatter) {
        Map<String, String> blocks = new LinkedHashMap<>();
        StringBuilder current = new StringBuilder();
        StringBuilder pending = new StringBuilder(); // comments/blanks, owner still unknown
        String key = PREAMBLE;
        for (String line : frontmatter.lines().toList()) {
            Matcher matcher = KEY.matcher(line);
            if (matcher.matches()) {
                if (!(PREAMBLE.equals(key) && current.isEmpty()) && blocks.put(key, current.toString()) != null) {
                    return null; // duplicate top-level key
                }
                key = matcher.group(1);
                current = new StringBuilder(pending.toString());
            } else if (line.isBlank() || line.stripLeading().startsWith("#")) {
                pending.append(line).append('\n');
                continue;
            } else {
                current.append(pending);
            }
            pending.setLength(0);
            current.append(line).append('\n');
        }
        current.append(pending); // trailing comments belong to the last key
        if (!(PREAMBLE.equals(key) && current.isEmpty()) && blocks.put(key, current.toString()) != null) {
            return null;
        }
        return blocks;
    }

    private record Parts(String frontmatter, String notes) {
    }
}
