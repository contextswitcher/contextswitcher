package com.contextswitcher.extension;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

import com.contextswitcher.config.Browser;

/// WebSocket server on `127.0.0.1:<wsPort>` the Firefox extension connects
/// to. A connection becomes the active client only when its `Origin` is a
/// `moz-extension://` or `chrome-extension://` URL and its `hello` carries the
/// configured token;
/// non-conforming connections are closed. Tab commands correlate request and
/// `result` by uuid and fail after a 5 s timeout.
/// The server doubles as the single-instance IPC for `contextswitcher://`
/// deep links (https://github.com/contextswitcher/contextswitcher-private/issues/47): a local client (no `Origin`) sends one token-carrying
/// `deeplink` message; acceptance is a normal close (1000).
// [impl->dsn~extension-server-protocol~4]
// [impl->dsn~deep-link-forward~1]
public class ExtensionServer extends WebSocketServer implements TabCommands {

    static final long TIMEOUT_SECONDS = 5;

    private final String expectedToken;
    private final Map<String, CompletableFuture<ExtensionProtocol.Result>> pending = new ConcurrentHashMap<>();

    /// The authenticated extension connection; a later successful `hello`
    /// replaces an earlier one (e.g. after a browser restart the old socket
    /// may not have noticed the close yet).
    private volatile @Nullable WebSocket client;

    /// Every authenticated connection and the `client` name its `hello`
    /// carried (`firefox`, `chrome`). Two browsers may hold a socket at once,
    /// and the one that stops being [#client] must not be forgotten: when the
    /// client closes, the role goes back to a connection that is still open
    /// rather than leaving the app believing no extension is there.
    // [impl->dsn~drive-the-connected-browser~1]
    private final Map<WebSocket, String> clients = new ConcurrentHashMap<>();

    /// The browser the user chose (the toolbar globe's context menu, the
    /// `browser` setting): while its extension is connected it keeps the
    /// client role, whichever browser said `hello` last. Null — the last
    /// `hello` wins.
    // [impl->dsn~prefer-chosen-browser~1]
    private volatile @Nullable Browser preferred;

    /// Receives the URL of an authenticated `deeplink` message; set by the app
    /// once its window infrastructure exists.
    private volatile @Nullable Consumer<String> deepLinkHandler;

    /// Receives the URL of a tab activated in the browser (unsolicited).
    private volatile @Nullable Consumer<String> tabActivatedHandler;

    /// Told which browser holds the client role whenever that changes — null
    /// when nothing is connected any more. Set by the app for its toolbar
    /// indicator. Called on the WebSocket thread.
    // [impl->dsn~extension-connection-indicator~2]
    private volatile @Nullable Consumer<@Nullable Browser> connectionHandler;

    public ExtensionServer(int port, String expectedToken) {
        super(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
        this.expectedToken = expectedToken;
        setReuseAddr(true);
    }

    /// Whether an authenticated extension is currently connected.
    boolean hasClient() {
        WebSocket current = client;
        return current != null && current.isOpen();
    }

    /// The browser whose extension is executing the tab commands right now —
    /// what its `hello` named itself — or null when nothing is connected or
    /// the name is not one we know.
    /// It is the answer to "which browser's window do I raise": the extension
    /// that acts on the tabs is by definition the browser holding them, and a
    /// `browser:` setting left on its default would otherwise send the raise
    /// after the wrong process.
    // [impl->dsn~drive-the-connected-browser~1]
    public @Nullable Browser connectedBrowser() {
        WebSocket current = client;
        if (current == null || !current.isOpen()) {
            return null;
        }
        return Browser.named(clients.get(current));
    }

    /// The browsers whose extension holds an open, authenticated socket.
    // [impl->dsn~prefer-chosen-browser~1]
    public Set<Browser> connectedBrowsers() {
        Set<Browser> connected = EnumSet.noneOf(Browser.class);
        clients.forEach((socket, name) -> {
            Browser browser = Browser.named(name);
            if (browser != null && socket.isOpen()) {
                connected.add(browser);
            }
        });
        return connected;
    }

    /// Makes `browser` the one driven while its extension is connected, and
    /// hands it the client role right away when it is.
    // [impl->dsn~prefer-chosen-browser~1]
    public void prefer(@Nullable Browser browser) {
        preferred = browser;
        if (browser == null || browser == connectedBrowser()) {
            return;
        }
        clients.entrySet().stream()
                .filter(entry -> entry.getKey().isOpen() && Browser.named(entry.getValue()) == browser)
                .findFirst()
                .ifPresent(entry -> {
                    client = entry.getKey();
                    Logger.info("Extension client is now the chosen: {}", entry.getValue());
                    reportConnection();
                });
    }

    public void setDeepLinkHandler(Consumer<String> handler) {
        this.deepLinkHandler = handler;
    }

    /// Receives the URL of a tab the user activated in the browser; set by the
    /// app once its window exists.
    // [impl->dsn~browser-tab-selects-task~5]
    public void setTabActivatedHandler(Consumer<String> handler) {
        this.tabActivatedHandler = handler;
    }

    /// Sets the listener for connect/disconnect, and reports the current state
    /// to it right away — the extension may already have dialled in before the
    /// window existed.
    // [impl->dsn~extension-connection-indicator~2]
    public void setConnectionHandler(Consumer<@Nullable Browser> handler) {
        this.connectionHandler = handler;
        handler.accept(connectedBrowser());
    }

    private void reportConnection() {
        Consumer<@Nullable Browser> handler = connectionHandler;
        if (handler != null) {
            handler.accept(connectedBrowser());
        }
    }

    /// Browser extensions (Firefox sends its internal `moz-extension://<uuid>`
    /// URL as `Origin`, Chrome `chrome-extension://<id>`) and plain local
    /// clients (no `Origin` header — the deep-link forwarder) may connect; a
    /// browser **page** always presents its page origin and is rejected. Every
    /// action still requires the token.
    // [impl->dsn~extension-origin-check~3]
    static boolean originAccepted(@Nullable String origin) {
        return origin == null || origin.isEmpty()
                || origin.startsWith("moz-extension://")
                || origin.startsWith("chrome-extension://");
    }

    boolean tokenAccepted(String token) {
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    /// Focus-or-open with the opening under the caller's control: `false` asks
    /// only whether a tab already exists (answered with
    /// [ExtensionProtocol#NO_TAB]), so the caller can prepare *where* a new tab
    /// is to land before asking for it.
    // [impl->dsn~pr-open-on-category-desktop~3]
    // [impl->dsn~browser-tab-group~2]
    @Override
    public ExtensionProtocol.Result focusUrl(String url, boolean openIfMissing, @Nullable String windowTitle,
            @Nullable String group, boolean background) {
        String id = UUID.randomUUID().toString();
        return request(id, ExtensionProtocol.encodeFocusUrl(id, url, openIfMissing, windowTitle, group, background));
    }

    @Override
    public ExtensionProtocol.Result closeUrl(String url) {
        String id = UUID.randomUUID().toString();
        return request(id, ExtensionProtocol.encodeCloseUrl(id, url));
    }

    // [impl->dsn~complete-control-desktop~1]
    @Override
    public ExtensionProtocol.Result listTabs() {
        String id = UUID.randomUUID().toString();
        return request(id, ExtensionProtocol.encodeListTabs(id));
    }

    // [impl->dsn~complete-control-desktop~1]
    @Override
    public ExtensionProtocol.Result closeWindow(int windowId) {
        String id = UUID.randomUUID().toString();
        return request(id, ExtensionProtocol.encodeCloseWindow(id, windowId));
    }

    /// The URL of the tab in view (`detail`, empty when there is none) — the
    /// re-sync after a teardown dropped the tab reports its closes caused.
    // [impl->dsn~browser-teardown-quiet-tabs~1]
    public ExtensionProtocol.Result activeTab() {
        String id = UUID.randomUUID().toString();
        return request(id, ExtensionProtocol.encodeActiveTab(id));
    }

    /// Tells the extension which tasks list `url`, the answer to its
    /// `tab-activated` report. Fire-and-forget: nothing correlates it and
    /// nothing comes back, so no connected extension is simply nothing to
    /// tell — the badge is cosmetic and must never fail a caller.
    // [impl->dsn~browser-tab-context-count~2]
    public void sendTabCount(String url, List<ExtensionProtocol.TabTask> tasks) {
        WebSocket target = client;
        if (target == null || !target.isOpen()) {
            return;
        }
        try {
            target.send(ExtensionProtocol.encodeTabCount(url, tasks));
        } catch (RuntimeException e) {
            Logger.debug("Could not send tab count for {}: {}", url, e.toString());
        }
    }

    private ExtensionProtocol.Result request(String id, String message) {
        WebSocket target = client;
        if (target == null || !target.isOpen()) {
            return new ExtensionProtocol.Result(id, false, "Browser extension not connected");
        }
        CompletableFuture<ExtensionProtocol.Result> future = new CompletableFuture<>();
        pending.put(id, future);
        try {
            target.send(message);
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return new ExtensionProtocol.Result(id, false,
                    "Extension did not answer within %d s".formatted(TIMEOUT_SECONDS));
        } catch (ExecutionException e) {
            return new ExtensionProtocol.Result(id, false, "Extension connection lost");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ExtensionProtocol.Result(id, false, "Interrupted");
        } catch (RuntimeException e) {
            return new ExtensionProtocol.Result(id, false, "Cannot send to extension: " + e.getMessage());
        } finally {
            pending.remove(id);
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String origin = handshake.getFieldValue("Origin");
        if (!originAccepted(origin)) {
            Logger.warn("Rejecting extension connection with origin '{}'", origin);
            conn.close(4003, "origin not allowed");
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        ExtensionProtocol.Incoming incoming;
        try {
            incoming = ExtensionProtocol.parse(message);
        } catch (IllegalArgumentException e) {
            Logger.warn("Dropping unparseable extension message: {}", e.getMessage());
            return;
        }
        switch (incoming) {
            case ExtensionProtocol.Hello hello -> {
                if (tokenAccepted(hello.token())) {
                    Logger.info("Extension connected: {}", hello.client());
                    clients.put(conn, hello.client());
                    // [impl->dsn~prefer-chosen-browser~1]
                    Browser chosen = preferred;
                    if (chosen == null || Browser.named(hello.client()) == chosen
                            || connectedBrowser() != chosen) {
                        client = conn;
                        reportConnection();
                    }
                } else {
                    Logger.warn("Rejecting extension connection: token mismatch");
                    conn.close(4001, "token mismatch");
                }
            }
            case ExtensionProtocol.Deeplink deeplink -> {
                if (!tokenAccepted(deeplink.token())) {
                    Logger.warn("Rejecting deep link: token mismatch");
                    conn.close(4001, "token mismatch");
                    return;
                }
                Consumer<String> handler = deepLinkHandler;
                if (handler == null) {
                    Logger.warn("Dropping deep link, app not ready yet: {}", deeplink.url());
                    conn.close(4004, "not ready");
                    return;
                }
                Logger.info("Deep link received: {}", deeplink.url());
                handler.accept(deeplink.url());
                conn.close(1000, "deep link accepted");
            }
            case ExtensionProtocol.TabActivated activated -> {
                // Unauthenticated connections never get here: only the socket
                // that passed `hello` is the client, and a report from any
                // other one is ignored rather than acted on.
                Consumer<String> handler = tabActivatedHandler;
                if (conn != client || handler == null) {
                    Logger.debug("Dropping tab-activated for {}", activated.url());
                    return;
                }
                handler.accept(activated.url());
            }
            case ExtensionProtocol.OpenLink open -> {
                // Same handler the `contextswitcher://` scheme uses, but on
                // the live extension socket: authenticated by the `hello` that
                // made it the client, and left open afterwards (unlike the
                // one-shot `deeplink` of a second app instance).
                Consumer<String> handler = deepLinkHandler;
                if (conn != client || handler == null) {
                    Logger.debug("Dropping open-link for {}", open.url());
                    return;
                }
                Logger.info("Extension asks to open {}", open.url());
                handler.accept(open.url());
            }
            case ExtensionProtocol.Result result -> {
                CompletableFuture<ExtensionProtocol.Result> future = pending.remove(result.id());
                if (future != null) {
                    future.complete(result);
                } else {
                    Logger.debug("Dropping result for unknown or timed-out request {}", result.id());
                }
            }
        }
    }

    // [impl->dsn~drive-the-connected-browser~1]
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clients.remove(conn);
        if (conn != client) {
            return;
        }
        Logger.info("Extension disconnected ({} {})", code, reason);
        // Another browser's extension may still be sitting there: it was
        // pushed out of the client role by this one's `hello` and is the
        // obvious successor now that this one is gone.
        // The chosen browser first, if it is among them.
        // [impl->dsn~prefer-chosen-browser~1]
        WebSocket successor = clients.entrySet().stream()
                .filter(entry -> entry.getKey().isOpen())
                .sorted(java.util.Comparator.comparing(entry -> Browser.named(entry.getValue()) != preferred))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        client = successor;
        if (successor != null) {
            Logger.info("Extension client is now: {}", clients.get(successor));
        }
        reportConnection();
        // The pending requests went to the socket that just closed, whatever
        // takes over: they cannot be answered.
        pending.values().forEach(future ->
                future.completeExceptionally(new IllegalStateException("connection closed")));
    }

    @Override
    public void onError(@Nullable WebSocket conn, Exception e) {
        Logger.warn("Extension server error: {}", e.toString());
    }

    @Override
    public void onStart() {
        Logger.info("Extension server listening on 127.0.0.1:{}", getPort());
    }
}
