package com.contextswitcher.extension;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

/// JSON messages between app and browser extension. The extension opens with
/// `hello` (token authentication); the app sends `focus-url` / `close-url` /
/// `list-tabs` / `close-window` requests carrying a correlation `id`, which
/// the extension answers with a matching `result` (for `list-tabs` carrying a
/// `windows` array). A second app instance (deep-link launch, https://github.com/contextswitcher/contextswitcher-private/issues/47) sends a
/// one-shot `deeplink` message instead of `hello`.
// [impl->dsn~extension-server-protocol~4]
public final class ExtensionProtocol {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /// The extension's `detail` when a `focus-url` with `openIfMissing: false`
    /// found no tab — the one failure that means "the extension answered, and
    /// the URL still needs opening" (every other failure is the app's own:
    /// not connected, timeout). Must match `background.js`.
    // [impl->dsn~pr-open-on-category-desktop~3]
    public static final String NO_TAB = "no tab with that URL";

    private ExtensionProtocol() {
    }

    /// A message a client sends to the app.
    public sealed interface Incoming permits Hello, Result, Deeplink, TabActivated, OpenLink {
    }

    /// First message after connecting; authenticates the extension.
    public record Hello(String token, String client) implements Incoming {
    }

    /// The user activated a browser tab; the app selects the task owning the
    /// URL. Unsolicited (no correlation id) and only honoured on the already
    /// authenticated extension connection.
    // [impl->dsn~browser-tab-selects-task~5]
    public record TabActivated(String url) implements Incoming {
    }

    /// One-shot hand-over of a `contextswitcher://` URL from a second app
    /// instance; the server answers by closing the connection (1000 = accepted).
    public record Deeplink(String token, String url) implements Incoming {
    }

    /// A `contextswitcher://` URL the extension's popup wants run — the same
    /// links a note carries, arriving on the already authenticated connection
    /// (so no token, and no close: the extension keeps its socket).
    // [impl->dsn~browser-tab-task-popup~1]
    public record OpenLink(String url) implements Incoming {
    }

    /// One Firefox window in a `list-tabs` result: the extension's window id
    /// (valid for a following `close-window`), its caption-forming title (the
    /// active tab's), and the URLs of all its tabs.
    // [impl->dsn~extension-server-protocol~4]
    public record TabWindow(int id, String title, List<String> urls) {
        public TabWindow {
            urls = List.copyOf(urls);
        }
    }

    /// Outcome of one tab command, correlated by `id`. `title` is the focused
    /// tab's window title when a `focus-url` hit an existing tab — the app uses
    /// it to raise the *right* Firefox window among many; null otherwise.
    /// `windows` is the open-window list a `list-tabs` answers with; null for
    /// every other command.
    public record Result(String id, boolean ok, String detail, @Nullable String title,
            @Nullable List<TabWindow> windows) implements Incoming {

        /// For callers/tests with no window title (close, failures).
        public Result(String id, boolean ok, String detail) {
            this(id, ok, detail, null, null);
        }

        /// For focus results carrying only the window title.
        public Result(String id, boolean ok, String detail, @Nullable String title) {
            this(id, ok, detail, title, null);
        }
    }

    public static String encodeFocusUrl(String id, String url, boolean openIfMissing) {
        return encodeFocusUrl(id, url, openIfMissing, null);
    }

    /// `windowTitle` (the OS caption of a window the app raised on the target
    /// desktop) tells the extension where a tab that must be opened goes; null
    /// leaves the choice to Firefox.
    // [impl->dsn~pr-open-on-category-desktop~3]
    public static String encodeFocusUrl(String id, String url, boolean openIfMissing,
            @Nullable String windowTitle) {
        return encodeFocusUrl(id, url, openIfMissing, windowTitle, null);
    }

    /// `group` is the name of the Firefox tab group the tab is collected in
    /// (null: no grouping).
    // [impl->dsn~browser-tab-group~3]
    public static String encodeFocusUrl(String id, String url, boolean openIfMissing,
            @Nullable String windowTitle, @Nullable String group) {
        return encodeFocusUrl(id, url, openIfMissing, windowTitle, group, false);
    }

    /// `background` opens (or keeps) the tab without activating it and without
    /// raising its window — every URL of a task but its first.
    // [impl->dsn~browser-tab-group~3]
    public static String encodeFocusUrl(String id, String url, boolean openIfMissing,
            @Nullable String windowTitle, @Nullable String group, boolean background) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "focus-url");
        node.put("id", id);
        node.put("url", url);
        node.put("openIfMissing", openIfMissing);
        if (windowTitle != null) {
            node.put("windowTitle", windowTitle);
        }
        if (group != null) {
            node.put("group", group);
        }
        if (background) {
            node.put("background", true);
        }
        return node.toString();
    }

    public static String encodeDeeplink(String token, String url) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "deeplink");
        node.put("token", token);
        node.put("url", url);
        return node.toString();
    }

    /// One task in a `tab-count`: enough for the popup to show a row and to
    /// build the `contextswitcher://` link that acts on it.
    // [impl->dsn~browser-tab-task-popup~1]
    public record TabTask(String id, String title, String status) {
    }

    /// The app's answer to a `tab-activated`: the tasks listing that URL, in
    /// best-match-first order. Unsolicited — no correlation id, no reply; the
    /// extension badges the tab's icon with the count and lists the tasks in
    /// its popup.
    // [impl->dsn~browser-tab-context-count~2]
    public static String encodeTabCount(String url, List<TabTask> tasks) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "tab-count");
        node.put("url", url);
        var array = node.putArray("tasks");
        for (TabTask task : tasks) {
            ObjectNode entry = array.addObject();
            entry.put("id", task.id());
            entry.put("title", task.title());
            entry.put("status", task.status());
        }
        return node.toString();
    }

    public static String encodeCloseUrl(String id, String url) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "close-url");
        node.put("id", id);
        node.put("url", url);
        return node.toString();
    }

    // [impl->dsn~extension-server-protocol~4]
    public static String encodeListTabs(String id) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "list-tabs");
        node.put("id", id);
        return node.toString();
    }

    // [impl->dsn~browser-teardown-quiet-tabs~1]
    public static String encodeActiveTab(String id) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "active-tab");
        node.put("id", id);
        return node.toString();
    }

    // [impl->dsn~extension-server-protocol~4]
    public static String encodeCloseWindow(String id, int windowId) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "close-window");
        node.put("id", id);
        node.put("windowId", windowId);
        return node.toString();
    }

    /// Parses a message from the extension.
    ///
    /// @throws IllegalArgumentException on malformed JSON, a missing/unknown
    ///     `type`, or missing required fields
    public static Incoming parse(String json) {
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Malformed JSON: " + e.getMessage(), e);
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        String type = node.path("type").asText("");
        return switch (type) {
            case "hello" -> new Hello(
                    requiredText(node, "hello", "token"),
                    node.path("client").asText(""));
            case "deeplink" -> new Deeplink(
                    requiredText(node, "deeplink", "token"),
                    requiredText(node, "deeplink", "url"));
            case "tab-activated" -> new TabActivated(requiredText(node, "tab-activated", "url"));
            case "open-link" -> new OpenLink(requiredText(node, "open-link", "url"));
            case "result" -> new Result(
                    requiredText(node, "result", "id"),
                    node.path("ok").asBoolean(false),
                    node.path("detail").asText(""),
                    node.path("title").isTextual() ? node.path("title").asText() : null,
                    parseWindows(node.path("windows")));
            default -> throw new IllegalArgumentException("Unknown message type: '" + type + "'");
        };
    }

    /// The `windows` array of a `list-tabs` result, or null when the message
    /// carries none. Tolerant per entry: missing fields default rather than
    /// fail — the extension is the only writer and always sends all three.
    // [impl->dsn~extension-server-protocol~4]
    private static @Nullable List<TabWindow> parseWindows(JsonNode windows) {
        if (!windows.isArray()) {
            return null;
        }
        List<TabWindow> parsed = new java.util.ArrayList<>();
        for (JsonNode window : windows) {
            List<String> urls = new java.util.ArrayList<>();
            window.path("urls").forEach(url -> urls.add(url.asText("")));
            parsed.add(new TabWindow(
                    window.path("id").asInt(-1),
                    window.path("title").asText(""),
                    urls));
        }
        return List.copyOf(parsed);
    }

    private static String requiredText(JsonNode node, String type, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("'%s' message misses '%s'".formatted(type, field));
        }
        return value.asText();
    }
}
