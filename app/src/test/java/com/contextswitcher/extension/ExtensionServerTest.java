package com.contextswitcher.extension;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~extension-origin-check~3]
class ExtensionServerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "moz-extension://c9e0b6c2-3b0f-4b19-9c19-abcdef012345",
            "moz-extension://anything",
            "chrome-extension://abcdefghijklmnopabcdefghijklmnop",
            "chrome-extension://anything"
    })
    void extensionOriginsAccepted(String origin) {
        assertThat(ExtensionServer.originAccepted(origin)).isTrue();
    }

    /// A plain local client (the deep-link forwarder) sends no `Origin` at
    /// all; a browser page cannot omit it.
    @ParameterizedTest
    @NullAndEmptySource
    void absentOriginAccepted(String origin) {
        assertThat(ExtensionServer.originAccepted(origin)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:17872",
            "https://evil.example",
            "MOZ-EXTENSION://case-matters",
            "CHROME-EXTENSION://case-matters",
            "file:///c:/x.html"
    })
    void otherOriginsRejected(String origin) {
        assertThat(ExtensionServer.originAccepted(origin)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "secret, secret, true",
            "secret, wrong, false",
            "secret, '', false",
            "secret, Secret, false"
    })
    void tokenMustMatchExactly(String configured, String presented, boolean accepted) {
        assertThat(new ExtensionServer(0, configured).tokenAccepted(presented)).isEqualTo(accepted);
    }
}
