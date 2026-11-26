package com.contextswitcher.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.contextswitcher.tasks.DeepLinkUrls;
import com.contextswitcher.tasks.OneNoteLink;
import com.contextswitcher.tasks.TaskEntry;
import com.contextswitcher.tasks.TaskStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/// The backlinks AnchorHub writes at the top of each OneNote page that a task
/// or category links to with `note: onenote:…`: the request
/// `ankd onenote-backlinks-sync` reads from stdin, and its answer.
///
/// The link list is always the **complete** set for the source
/// `contextswitcher`: ankd drops this source's backlinks from every page no
/// longer listed, which is how a deleted task or category loses its link.
// [impl->dsn~onenote-backlinks~1]
public final class OneNoteBacklinks {

    /// The `settings.yaml` key that turns the sync off (`false`); absent = on.
    public static final String SETTING = "oneNoteBacklinks";

    /// The ankd command the request goes to.
    public static final String COMMAND = "onenote-backlinks-sync";

    private static final ObjectMapper JSON = new ObjectMapper();

    private OneNoteBacklinks() {
    }

    /// One backlink: the OneNote page (`note`, an `onenote:` link) shows
    /// `label` linking to `url`, struck through when `done`.
    public record Link(String note, String label, String url, boolean done) {
    }

    /// What ankd answered: how many pages it changed, cleaned, left alone,
    /// and the pages it could not write.
    public record Result(int updated, int removed, int unchanged, List<Failure> failed) {
    }

    /// A page ankd could not write, with its reason.
    public record Failure(String note, String error) {
    }

    /// The backlinks of the loaded `entries` and of the categories, keyed by
    /// category (folder) name to the `note:` of its `CONTEXTSWITCHER.md`.
    /// A task links back with its title to `contextswitcher://task/<id>`,
    /// done when its status is `done`; a category with its name to
    /// `contextswitcher://category/<name>`. Only a task's **own** `note:`
    /// counts (one inheriting the category's note is the category's
    /// backlink), notes that are no OneNote link are skipped, and so are
    /// task files that do not parse. Sorted by URL, so two lists of the same
    /// links compare equal.
    public static List<Link> links(Collection<? extends TaskEntry> entries,
            Map<String, ? extends @Nullable String> categoryNotes) {
        List<Link> links = new ArrayList<>();
        for (TaskEntry entry : entries) {
            if (entry instanceof TaskEntry.Loaded loaded) {
                var task = loaded.task();
                String note = oneNote(task.note());
                if (note != null) {
                    links.add(new Link(note, task.title(), DeepLinkUrls.taskUrl(task.id()),
                            task.status() == TaskStatus.DONE));
                }
            }
        }
        categoryNotes.forEach((category, raw) -> {
            String note = oneNote(raw);
            if (note != null) {
                links.add(new Link(note, "Category " + category, DeepLinkUrls.categoryUrl(category), false));
            }
        });
        links.sort(Comparator.comparing(Link::url).thenComparing(Link::note));
        return List.copyOf(links);
    }

    /// The `onenote:` link a `note:` value stands for, or null for none.
    static @Nullable String oneNote(@Nullable String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String link = OneNoteLink.noteLink(OneNoteLink.preferDesktop(note.strip()));
        return link == null || !link.startsWith("onenote:") ? null : link;
    }

    /// The JSON object ankd reads from stdin.
    public static String request(List<Link> links) {
        ObjectNode root = JSON.createObjectNode();
        root.put("source", "contextswitcher");
        root.put("name", "ContextSwitcher");
        ArrayNode array = root.putArray("links");
        for (Link link : links) {
            array.addObject()
                    .put("note", link.note())
                    .put("label", link.label())
                    .put("url", link.url())
                    .put("done", link.done());
        }
        return root.toString();
    }

    /// ankd's answer, or null when it printed nothing — an ankd that does
    /// not know the command yet.
    ///
    /// @throws IllegalArgumentException when the output is not the expected
    ///     JSON object
    public static @Nullable Result parseResult(String output) {
        String trimmed = output.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        // The answer is the last line; anything ankd logged before it is not.
        String line = trimmed.substring(trimmed.lastIndexOf('\n') + 1).strip();
        JsonNode node;
        try {
            node = JSON.readTree(line);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Not JSON: " + line, e);
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Not a JSON object: " + line);
        }
        List<Failure> failed = new ArrayList<>();
        for (JsonNode failure : node.path("failed")) {
            failed.add(new Failure(failure.path("note").asText(""), failure.path("error").asText("")));
        }
        return new Result(node.path("updated").asInt(0), node.path("removed").asInt(0),
                node.path("unchanged").asInt(0), List.copyOf(failed));
    }

    /// Where AnchorHub's installer puts `ankd.exe`
    /// (`%LOCALAPPDATA%\Programs\AnchorHub\ankd.exe`), or null off Windows,
    /// without `LOCALAPPDATA`, or when it is not installed.
    public static @Nullable Path installedAnkd(String osName, @Nullable String localAppData) {
        if (!osName.startsWith("Windows") || localAppData == null || localAppData.isBlank()) {
            return null;
        }
        Path ankd = Path.of(localAppData, "Programs", "AnchorHub", "ankd.exe");
        return Files.isRegularFile(ankd) ? ankd : null;
    }

    /// Whether `settings.yaml` leaves the sync on: everything but
    /// `oneNoteBacklinks: false`.
    public static boolean enabled(String settingsYaml) {
        Object root;
        try {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(settingsYaml);
        } catch (RuntimeException e) {
            return true;
        }
        return !(root instanceof Map<?, ?> map && Boolean.FALSE.equals(map.get(SETTING)));
    }
}
