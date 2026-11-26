package com.contextswitcher.tasks;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/// The `contextswitcher://` links to a task and a category, as *Copy link*
/// hands them out and the OneNote backlinks carry them. Lives in `core` so the
/// pure backlink builder ([com.contextswitcher.config.OneNoteBacklinks]) can
/// build them; the app's `DeepLink` delegates here and parses them back.
// [impl->dsn~task-link-copy~1]
// [impl->dsn~category-link-copy~1]
public final class DeepLinkUrls {

    private DeepLinkUrls() {
    }

    /// `contextswitcher://category/<name>`, the name percent-encoded (a space
    /// as `%20`, not `+`).
    public static String categoryUrl(String category) {
        return "contextswitcher://category/" + encode(category);
    }

    /// `contextswitcher://task/<id>`, each `/`-separated segment of the id
    /// percent-encoded and the `/` of group folders kept.
    public static String taskUrl(String id) {
        StringBuilder url = new StringBuilder("contextswitcher://task");
        for (String segment : id.split("/")) {
            url.append('/').append(encode(segment));
        }
        return url.toString();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
