package com.contextswitcher.extension;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/// A parsed `contextswitcher://` deep link (https://github.com/contextswitcher/contextswitcher-private/issues/47): `contextswitcher://task/<id>`
/// selects the task and focuses the app, `contextswitcher://switch/<id>` runs
/// the full switch, `contextswitcher://category/<name>` selects the category's
/// header row. Task ids contain `/` (group folders) — they live in the URL's
/// path; other characters arrive percent-encoded and are decoded by the `URI`
/// path accessor.
// [impl->dsn~deep-link-url~1]
public record DeepLink(Kind kind, String target) {

    /// What the link points at — a task (by id) or a category (by name).
    public enum Kind { TASK, SWITCH, CATEGORY }

    /// Whether `arg` (e.g. a program argument) is a deep link at all.
    public static boolean isDeepLink(String arg) {
        return arg.startsWith("contextswitcher://");
    }

    /// The `contextswitcher://category/<name>` link to `category`, ready to be
    /// pasted into a note. Percent-encodes the name, so spaces and other
    /// characters survive the round trip through [#parse].
    // [impl->dsn~category-link-copy~1]
    public static String categoryUrl(String category) {
        return "contextswitcher://category/"
                + URLEncoder.encode(category, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /// The `contextswitcher://task/<id>` link to the task `id`, ready to be
    /// pasted into a note. Percent-encodes each path segment but keeps the
    /// `/` of group folders, so the id survives the round trip through [#parse].
    // [impl->dsn~task-link-copy~1]
    public static String taskUrl(String id) {
        StringBuilder url = new StringBuilder("contextswitcher://task");
        for (String segment : id.split("/")) {
            url.append('/').append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return url.toString();
    }

    /// Parses `url`.
    ///
    /// @throws IllegalArgumentException on a non-`contextswitcher` scheme, an
    ///     unknown kind, or a missing task id / category name
    public static DeepLink parse(String url) {
        URI uri = URI.create(url);
        if (!"contextswitcher".equals(uri.getScheme())) {
            throw new IllegalArgumentException("Not a contextswitcher:// URL: " + url);
        }
        // In `contextswitcher://task/jabref/fix-npe` the kind parses as the
        // URI host and the task id as the (percent-decoded) path.
        String host = uri.getHost();
        Kind kind = switch (host == null ? "" : host) {
            case "task" -> Kind.TASK;
            case "switch" -> Kind.SWITCH;
            case "category" -> Kind.CATEGORY;
            default -> throw new IllegalArgumentException("Unknown deep-link kind '" + host
                    + "' (expected task, switch or category): " + url);
        };
        String path = uri.getPath();
        String target = path == null ? "" : path.replaceAll("^/|/$", "");
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Missing task id: " + url);
        }
        return new DeepLink(kind, target);
    }
}
