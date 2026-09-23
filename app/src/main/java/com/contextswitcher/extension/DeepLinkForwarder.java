package com.contextswitcher.extension;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.tinylog.Logger;

/// Forwards a `contextswitcher://` URL to an already-running instance over the
/// loopback extension server (https://github.com/contextswitcher/contextswitcher-private/issues/47 single instance): a second app launch sends
/// one `deeplink` message and exits instead of opening a second window. The
/// running instance acknowledges by closing the connection normally (1000).
// [impl->dsn~deep-link-forward~1]
public final class DeepLinkForwarder {

    private DeepLinkForwarder() {
    }

    /// Tries to hand `url` to a running instance on `port`. Returns true when
    /// the instance accepted it (the caller should exit); false when no
    /// instance is reachable or the handshake failed (the caller should start
    /// normally and handle the URL itself).
    public static boolean forward(int port, String token, String url) {
        CountDownLatch closed = new CountDownLatch(1);
        AtomicBoolean accepted = new AtomicBoolean();
        WebSocketClient client = new WebSocketClient(URI.create("ws://127.0.0.1:" + port)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                send(ExtensionProtocol.encodeDeeplink(token, url));
            }

            @Override
            public void onMessage(String message) {
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                accepted.set(code == 1000);
                closed.countDown();
            }

            @Override
            public void onError(Exception e) {
                Logger.debug("Deep-link forward failed: {}", e.toString());
                closed.countDown();
            }
        };
        try {
            if (!client.connectBlocking(2, TimeUnit.SECONDS)) {
                return false;
            }
            return closed.await(3, TimeUnit.SECONDS) && accepted.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            client.close();
        }
    }
}
