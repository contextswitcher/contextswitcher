package com.contextswitcher.extension;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.contextswitcher.config.Browser;

import static org.assertj.core.api.Assertions.assertThat;

/// Real loopback round-trips against `ExtensionServer` with a Java-WebSocket
/// client standing in for the Firefox extension (port 0 = ephemeral).
// [utest->dsn~extension-server-protocol~4]
// [utest->dsn~extension-origin-check~3]
// [utest->dsn~deep-link-forward~1]
// [utest->dsn~drive-the-connected-browser~1]
class ExtensionServerIntegrationTest {

    private static final String TOKEN = "secret";

    private ExtensionServer server;

    @BeforeEach
    void startServer() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        server = new ExtensionServer(0, TOKEN) {
            @Override
            public void onStart() {
                super.onStart();
                started.countDown();
            }
        };
        server.start();
        assertThat(started.await(5, TimeUnit.SECONDS)).as("server started").isTrue();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.stop(1000);
    }

    /// Extension stand-in: sends `hello` on open and answers every command
    /// with an ok `result`.
    private final class FakeExtension extends WebSocketClient {
        private final String token;
        private final String client;

        FakeExtension(String origin, String token) {
            this(origin, token, "test");
        }

        /// `client` is what the `hello` names itself — `firefox`/`chrome` is
        /// how the app knows whose windows to raise.
        FakeExtension(String origin, String token, String client) {
            super(URI.create("ws://127.0.0.1:" + server.getPort()), Map.of("Origin", origin));
            this.token = token;
            this.client = client;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            send("{\"type\":\"hello\",\"token\":\"" + token + "\",\"client\":\"" + client + "\"}");
        }

        @Override
        public void onMessage(String message) {
            String id = message.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
            send("{\"type\":\"result\",\"id\":\"" + id + "\",\"ok\":true,\"detail\":\"focused existing tab\"}");
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
        }

        @Override
        public void onError(Exception e) {
        }
    }

    private void awaitAuthenticated() throws InterruptedException {
        for (int i = 0; i < 500 && !server.hasClient(); i++) {
            Thread.sleep(10);
        }
        assertThat(server.hasClient()).as("extension authenticated").isTrue();
    }

    private void awaitClosed(FakeExtension extension) throws InterruptedException {
        for (int i = 0; i < 500 && !extension.isClosed(); i++) {
            Thread.sleep(10);
        }
        assertThat(extension.isClosed()).as("connection closed by server").isTrue();
    }

    @Test
    void focusRoundTripCorrelatesRequestAndResult() throws Exception {
        FakeExtension extension = new FakeExtension("moz-extension://test", TOKEN);
        assertThat(extension.connectBlocking(5, TimeUnit.SECONDS)).isTrue();
        awaitAuthenticated();

        ExtensionProtocol.Result result = server.focusUrl("https://example.org/");

        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).isEqualTo("focused existing tab");
        extension.closeBlocking();
    }

    @Test
    void wrongTokenIsRejected() throws Exception {
        FakeExtension extension = new FakeExtension("moz-extension://test", "wrong");
        extension.connectBlocking(5, TimeUnit.SECONDS);
        awaitClosed(extension);
        assertThat(server.hasClient()).isFalse();
    }

    @Test
    void nonExtensionOriginIsRejected() throws Exception {
        FakeExtension extension = new FakeExtension("https://evil.example", TOKEN);
        extension.connectBlocking(5, TimeUnit.SECONDS);
        awaitClosed(extension);
        assertThat(server.hasClient()).isFalse();
    }

    /// The extension that acts on the tabs is the browser holding them, so it
    /// is the one whose windows are raised — and a `hello` from a second
    /// browser moves that answer along with the client role.
    // [utest->dsn~drive-the-connected-browser~1]
    @Test
    void connectedBrowserIsWhateverTheCurrentClientCalledItself() throws Exception {
        assertThat(server.connectedBrowser()).as("nothing connected").isNull();

        FakeExtension firefox = new FakeExtension("moz-extension://test", TOKEN, "firefox");
        assertThat(firefox.connectBlocking(5, TimeUnit.SECONDS)).isTrue();
        awaitAuthenticated();
        assertThat(server.connectedBrowser()).isEqualTo(Browser.FIREFOX);

        FakeExtension chrome = new FakeExtension("chrome-extension://test", TOKEN, "chrome");
        assertThat(chrome.connectBlocking(5, TimeUnit.SECONDS)).isTrue();
        awaitBrowser(Browser.CHROME);

        firefox.closeBlocking();
        chrome.closeBlocking();
    }

    /// A second extension leaving must not take the first one — still
    /// connected, still able to execute — down with it: the client role goes
    /// back rather than to nobody.
    // [utest->dsn~drive-the-connected-browser~1]
    @Test
    void closingTheClientHandsTheRoleBackToAConnectionStillOpen() throws Exception {
        FakeExtension firefox = new FakeExtension("moz-extension://test", TOKEN, "firefox");
        assertThat(firefox.connectBlocking(5, TimeUnit.SECONDS)).isTrue();
        awaitAuthenticated();
        FakeExtension chrome = new FakeExtension("chrome-extension://test", TOKEN, "chrome");
        assertThat(chrome.connectBlocking(5, TimeUnit.SECONDS)).isTrue();
        awaitBrowser(Browser.CHROME);

        chrome.closeBlocking();

        awaitBrowser(Browser.FIREFOX);
        assertThat(server.hasClient()).as("Firefox is still there").isTrue();
        firefox.closeBlocking();
    }

    /// The chosen browser keeps the client role against a later `hello`, takes
    /// it back when chosen, and gets it back when the other one leaves.
    // [utest->dsn~prefer-chosen-browser~1]
    @Test
    void theChosenBrowserDrivesWhileConnected() throws Exception {
        server.prefer(Browser.FIREFOX);
        FakeExtension firefox = new FakeExtension("moz-extension://test", TOKEN, "firefox");
        assertThat(firefox.connectBlocking(5, TimeUnit.SECONDS)).isTrue();
        awaitAuthenticated();
        FakeExtension chrome = new FakeExtension("chrome-extension://test", TOKEN, "chrome");
        assertThat(chrome.connectBlocking(5, TimeUnit.SECONDS)).isTrue();
        for (int i = 0; i < 500 && server.connectedBrowsers().size() < 2; i++) {
            Thread.sleep(10);
        }
        assertThat(server.connectedBrowsers()).containsExactlyInAnyOrder(Browser.FIREFOX, Browser.CHROME);
        assertThat(server.connectedBrowser()).as("Chrome's later hello").isEqualTo(Browser.FIREFOX);

        server.prefer(Browser.CHROME);
        assertThat(server.connectedBrowser()).isEqualTo(Browser.CHROME);

        server.prefer(Browser.FIREFOX);
        assertThat(server.connectedBrowser()).isEqualTo(Browser.FIREFOX);
        firefox.closeBlocking();
        awaitBrowser(Browser.CHROME);
        chrome.closeBlocking();
    }

    /// An unknown `client` name is no answer at all, so the configured browser
    /// keeps deciding — the pre-`browser:` extensions named themselves `test`
    /// in this very file.
    // [utest->dsn~drive-the-connected-browser~1]
    @Test
    void anUnnamedClientLeavesTheBrowserUnknown() throws Exception {
        FakeExtension extension = new FakeExtension("moz-extension://test", TOKEN);
        assertThat(extension.connectBlocking(5, TimeUnit.SECONDS)).isTrue();
        awaitAuthenticated();

        assertThat(server.connectedBrowser()).isNull();
        extension.closeBlocking();
    }

    private void awaitBrowser(Browser expected) throws InterruptedException {
        for (int i = 0; i < 500 && server.connectedBrowser() != expected; i++) {
            Thread.sleep(10);
        }
        assertThat(server.connectedBrowser()).isEqualTo(expected);
    }

    @Test
    void deepLinkForwardReachesHandler() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> received = new java.util.concurrent.atomic.AtomicReference<>();
        server.setDeepLinkHandler(received::set);

        boolean forwarded = DeepLinkForwarder.forward(
                server.getPort(), TOKEN, "contextswitcher://task/jabref/fix-npe");

        assertThat(forwarded).isTrue();
        assertThat(received.get()).isEqualTo("contextswitcher://task/jabref/fix-npe");
    }

    @Test
    void deepLinkWithWrongTokenIsRejected() {
        java.util.concurrent.atomic.AtomicReference<String> received = new java.util.concurrent.atomic.AtomicReference<>();
        server.setDeepLinkHandler(received::set);

        boolean forwarded = DeepLinkForwarder.forward(
                server.getPort(), "wrong", "contextswitcher://task/x");

        assertThat(forwarded).isFalse();
        assertThat(received.get()).isNull();
    }

    @Test
    void deepLinkForwardWithoutRunningInstanceReturnsFalse() throws Exception {
        int port = server.getPort();
        server.stop(1000);
        assertThat(DeepLinkForwarder.forward(port, TOKEN, "contextswitcher://task/x")).isFalse();
    }

    @Test
    void requestWithoutConnectedExtensionFailsImmediately() {
        ExtensionProtocol.Result result = server.closeUrl("https://example.org/");
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("not connected");
    }
}
