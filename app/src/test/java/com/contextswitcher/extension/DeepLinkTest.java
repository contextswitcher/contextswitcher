package com.contextswitcher.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// [utest->dsn~deep-link-url~1]
class DeepLinkTest {

    @Test
    void taskLinkParsesGroupedId() {
        assertThat(DeepLink.parse("contextswitcher://task/jabref/fix-npe"))
                .isEqualTo(new DeepLink(DeepLink.Kind.TASK, "jabref/fix-npe"));
    }

    @Test
    void switchLinkParses() {
        assertThat(DeepLink.parse("contextswitcher://switch/2026-07-21-cs-protocol"))
                .isEqualTo(new DeepLink(DeepLink.Kind.SWITCH, "2026-07-21-cs-protocol"));
    }

    @Test
    void percentEncodingIsDecoded() {
        assertThat(DeepLink.parse("contextswitcher://task/jabref/fix%20npe"))
                .isEqualTo(new DeepLink(DeepLink.Kind.TASK, "jabref/fix npe"));
    }

    @Test
    void trailingSlashIsIgnored() {
        assertThat(DeepLink.parse("contextswitcher://task/a/b/"))
                .isEqualTo(new DeepLink(DeepLink.Kind.TASK, "a/b"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.org/",
            "contextswitcher://focus/x",
            "contextswitcher://task",
            "contextswitcher://task/",
            "not a url at all"
    })
    void badLinksRejected(String url) {
        assertThatThrownBy(() -> DeepLink.parse(url)).isInstanceOf(IllegalArgumentException.class);
    }

    // [utest->dsn~category-link-copy~1]
    @Test
    void categoryLinkRoundTrips() {
        String url = DeepLink.categoryUrl("My Project");
        assertThat(url).isEqualTo("contextswitcher://category/My%20Project");
        assertThat(DeepLink.parse(url))
                .isEqualTo(new DeepLink(DeepLink.Kind.CATEGORY, "My Project"));
    }

    // [utest->dsn~task-link-copy~1]
    @Test
    void taskLinkRoundTrips() {
        String url = DeepLink.taskUrl("jabref/fix npe");
        assertThat(url).isEqualTo("contextswitcher://task/jabref/fix%20npe");
        assertThat(DeepLink.parse(url))
                .isEqualTo(new DeepLink(DeepLink.Kind.TASK, "jabref/fix npe"));
    }

    @Test
    void isDeepLinkChecksScheme() {
        assertThat(DeepLink.isDeepLink("contextswitcher://task/x")).isTrue();
        assertThat(DeepLink.isDeepLink("--some-flag")).isFalse();
    }
}
