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
    void switchCategoryLinkParses() {
        assertThat(DeepLink.parse("contextswitcher://switch-category/My%20Project"))
                .isEqualTo(new DeepLink(DeepLink.Kind.SWITCH_CATEGORY, "My Project"));
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

    // [utest->dsn~single-instance~1]
    @Test
    void showLinkNeedsNoTarget() {
        assertThat(DeepLink.isDeepLink(DeepLink.SHOW_URL)).isTrue();
        assertThat(DeepLink.parse(DeepLink.SHOW_URL)).isEqualTo(new DeepLink(DeepLink.Kind.SHOW, ""));
    }

    // [utest->dsn~koppter-sync~1]
    @Test
    void filterLinkKeepsTheRawQuery() {
        String url = "contextswitcher://filter?project=papers%3Esurvey&context=desk";
        assertThat(DeepLink.isFilterLink(url)).isTrue();
        assertThat(DeepLink.parse(url)).isEqualTo(new DeepLink(DeepLink.Kind.FILTER, "project=papers%3Esurvey&context=desk"));
        // No query clears the filter.
        assertThat(DeepLink.parse("contextswitcher://filter")).isEqualTo(new DeepLink(DeepLink.Kind.FILTER, ""));
        assertThat(DeepLink.isFilterLink("contextswitcher://task/x")).isFalse();
    }

    @Test
    void isDeepLinkChecksScheme() {
        assertThat(DeepLink.isDeepLink("contextswitcher://task/x")).isTrue();
        assertThat(DeepLink.isDeepLink("--some-flag")).isFalse();
    }
}
